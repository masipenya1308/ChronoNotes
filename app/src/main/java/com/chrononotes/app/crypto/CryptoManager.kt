package com.chrononotes.app.crypto

import android.content.Context
import android.os.SystemClock
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Сейф: AES-256-GCM, ключ выводится из пароля через PBKDF2WithHmacSHA256.
 *
 * Производный ключ оборачивается неэкспортируемым ключом Android Keystore,
 * поэтому по данным SharedPreferences нельзя подобрать пароль офлайн.
 * Старый формат (SHA-256(key) как верификатор) поддерживается и мигрируется при первом входе.
 */
class CryptoManager(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val keyStore: KeyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }

    private var salt: ByteArray = ByteArray(0)
    private var wrappedKey: String? = null
    private var legacyVerifier: ByteArray = ByteArray(0)
    private var key: SecretKeySpec? = null

    private var failedAttempts = 0
    private var lockUntilMs = 0L

    init {
        val rawSalt = prefs.getString(KEY_SALT, null)?.let { runCatching { decode(it) }.getOrNull() }
        val storedWrapped = prefs.getString(KEY_WRAPPED, null)
        val storedVerifier = prefs.getString(KEY_VERIFIER, null)
            ?.let { runCatching { decode(it) }.getOrNull() }
        salt = rawSalt ?: ByteArray(0)
        wrappedKey = storedWrapped
        legacyVerifier = storedVerifier ?: ByteArray(0)
    }

    val isConfigured: Boolean get() = wrappedKey != null || legacyVerifier.isNotEmpty()
    val isUnlocked: Boolean get() = key != null

    /** Оставшееся время блокировки после неудачных попыток, мс. */
    val lockRemainingMs: Long
        get() = (lockUntilMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L)

    fun setPassword(password: CharArray) {
        require(password.size >= MIN_PASSWORD_LENGTH) {
            "Пароль должен быть не короче $MIN_PASSWORD_LENGTH символов"
        }
        val newSalt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val derived = derive(password, newSalt)
        val wrapped = wrapKey(derived)
        prefs.edit()
            .putString(KEY_SALT, encode(newSalt))
            .putString(KEY_WRAPPED, wrapped)
            .remove(KEY_VERIFIER)
            .apply()
        salt = newSalt
        wrappedKey = wrapped
        legacyVerifier = ByteArray(0)
        key = derived
        failedAttempts = 0
        lockUntilMs = 0L
    }

    fun unlock(password: CharArray): Boolean {
        if (lockRemainingMs > 0) return false
        if (!isConfigured) return false

        val derived = derive(password, salt)
        val valid = runCatching {
            val expected = if (wrappedKey != null) unwrapKey(wrappedKey!!).encoded else legacyVerifier
            MessageDigest.isEqual(derived.encoded, expected)
        }.getOrDefault(false)

        if (!valid) {
            failedAttempts++
            if (failedAttempts >= MAX_ATTEMPTS) {
                val backoff = (BASE_LOCK_MS shl (failedAttempts - MAX_ATTEMPTS))
                    .coerceAtMost(MAX_LOCK_MS)
                lockUntilMs = SystemClock.elapsedRealtime() + backoff
            }
            return false
        }

        failedAttempts = 0
        lockUntilMs = 0L
        key = derived

        // Миграция со старого формата: сохраняем обёрнутый ключ, удаляем верификатор.
        if (wrappedKey == null) {
            runCatching {
                val migrated = wrapKey(derived)
                prefs.edit()
                    .putString(KEY_WRAPPED, migrated)
                    .remove(KEY_VERIFIER)
                    .apply()
                wrappedKey = migrated
                legacyVerifier = ByteArray(0)
            }
        }
        return true
    }

    fun lock() {
        key = null
    }

    fun clearPassword() {
        prefs.edit()
            .remove(KEY_SALT)
            .remove(KEY_WRAPPED)
            .remove(KEY_VERIFIER)
            .apply()
        runCatching {
            if (keyStore.containsAlias(WRAP_ALIAS)) keyStore.deleteEntry(WRAP_ALIAS)
        }
        salt = ByteArray(0)
        wrappedKey = null
        legacyVerifier = ByteArray(0)
        key = null
        failedAttempts = 0
        lockUntilMs = 0L
    }

    fun encrypt(plain: String): String {
        val secret = requireKey()
        val iv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = contentCipher(Cipher.ENCRYPT_MODE, iv)
        cipher.updateAAD(CONTENT_AAD)
        val cipherText = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return encode(iv + cipherText)
    }

    fun decrypt(payload: String): String {
        val secret = requireKey()
        val raw = decode(payload)
        require(raw.size >= IV_LENGTH + TAG_LENGTH) { "Повреждённые данные сейфа" }
        val iv = raw.copyOfRange(0, IV_LENGTH)
        val cipherText = raw.copyOfRange(IV_LENGTH, raw.size)

        val plain = runCatching {
            val cipher = contentCipher(Cipher.DECRYPT_MODE, iv)
            cipher.updateAAD(CONTENT_AAD)
            cipher.doFinal(cipherText)
        }.recoverCatching { error ->
            // Совместимость со старыми заметками, зашифрованными без AAD.
            if (error !is AEADBadTagException) throw error
            val cipher = contentCipher(Cipher.DECRYPT_MODE, iv)
            cipher.doFinal(cipherText)
        }.getOrThrow()

        return String(plain, Charsets.UTF_8)
    }

    private fun requireKey(): SecretKeySpec = key ?: error("Сейф заблокирован")

    private fun contentCipher(mode: Int, iv: ByteArray): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, requireKey(), GCMParameterSpec(TAG_BITS, iv))
        }

    private fun derive(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun ensureWrapKey() {
        if (keyStore.containsAlias(WRAP_ALIAS)) return
        val spec = KeyGenParameterSpec.Builder(
            WRAP_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_BITS)
            .build()
        javax.crypto.KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
            .apply { init(spec) }
            .generateKey()
    }

    private fun wrapKey(secret: SecretKeySpec): String {
        ensureWrapKey()
        val cipher = keystoreCipher(Cipher.ENCRYPT_MODE, null)
        val wrapped = cipher.doFinal(secret.encoded)
        return encode(cipher.iv + wrapped)
    }

    private fun unwrapKey(payload: String): SecretKeySpec {
        val raw = decode(payload)
        require(raw.size > IV_LENGTH) { "Повреждённый ключ сейфа" }
        val cipher = keystoreCipher(Cipher.DECRYPT_MODE, raw.copyOfRange(0, IV_LENGTH))
        val encoded = cipher.doFinal(raw.copyOfRange(IV_LENGTH, raw.size))
        return SecretKeySpec(encoded, "AES")
    }

    private fun keystoreCipher(mode: Int, iv: ByteArray?): Cipher {
        val secret = keyStore.getKey(WRAP_ALIAS, null) as SecretKey
        return Cipher.getInstance("AES/GCM/NoPadding", KEYSTORE_PROVIDER).apply {
            if (iv == null) init(mode, secret) else init(mode, secret, GCMParameterSpec(TAG_BITS, iv))
        }
    }

    private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decode(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)

    private companion object {
        const val PREFS_NAME = "chrononotes_vault"
        const val KEY_SALT = "salt"
        const val KEY_WRAPPED = "wrapped_key"
        const val KEY_VERIFIER = "verifier" // только для миграции со старого формата
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val WRAP_ALIAS = "chrononotes_vault_wrap"
        const val MIN_PASSWORD_LENGTH = 8
        const val ITERATIONS = 600_000
        const val KEY_BITS = 256
        const val TAG_BITS = 128
        const val TAG_LENGTH = TAG_BITS / 8
        const val IV_LENGTH = 12
        const val MAX_ATTEMPTS = 3
        const val BASE_LOCK_MS = 2_000L
        const val MAX_LOCK_MS = 60_000L
        val CONTENT_AAD = "ChronoNotes-v1".toByteArray(Charsets.UTF_8)
    }
}
