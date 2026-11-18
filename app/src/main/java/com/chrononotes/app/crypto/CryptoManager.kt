package com.chrononotes.app.crypto

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Сейф: AES-256-GCM, ключ выводится из пароля через PBKDF2WithHmacSHA256.
 * Соль и верификатор хранятся в SharedPreferences, сам ключ — только в памяти.
 */
class CryptoManager(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var salt: ByteArray = ByteArray(0)
    private var verifier: ByteArray = ByteArray(0)
    private var key: SecretKeySpec? = null

    init {
        val storedSalt = prefs.getString(KEY_SALT, null)
        val storedVerifier = prefs.getString(KEY_VERIFIER, null)
        if (storedSalt != null && storedVerifier != null) {
            salt = Base64.decode(storedSalt, Base64.NO_WRAP)
            verifier = Base64.decode(storedVerifier, Base64.NO_WRAP)
        }
    }

    val isConfigured: Boolean get() = verifier.isNotEmpty()
    val isUnlocked: Boolean get() = key != null

    fun setPassword(password: CharArray) {
        require(password.isNotEmpty()) { "Пароль не может быть пустым" }
        val newSalt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val derived = derive(password, newSalt)
        val newVerifier = MessageDigest.getInstance("SHA-256").digest(derived.encoded)
        prefs.edit()
            .putString(KEY_SALT, Base64.encodeToString(newSalt, Base64.NO_WRAP))
            .putString(KEY_VERIFIER, Base64.encodeToString(newVerifier, Base64.NO_WRAP))
            .apply()
        salt = newSalt
        verifier = newVerifier
        key = derived
    }

    fun unlock(password: CharArray): Boolean {
        if (!isConfigured) return false
        val derived = derive(password, salt)
        val candidate = MessageDigest.getInstance("SHA-256").digest(derived.encoded)
        if (!MessageDigest.isEqual(candidate, verifier)) return false
        key = derived
        return true
    }

    fun lock() {
        key = null
    }

    fun clearPassword() {
        prefs.edit().remove(KEY_SALT).remove(KEY_VERIFIER).apply()
        salt = ByteArray(0)
        verifier = ByteArray(0)
        key = null
    }

    fun encrypt(plain: String): String {
        val secret = requireKey()
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, secret, GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        val cipherText = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + cipherText, Base64.NO_WRAP)
    }

    fun decrypt(payload: String): String {
        val secret = requireKey()
        val raw = Base64.decode(payload, Base64.NO_WRAP)
        require(raw.size > IV_LENGTH) { "Повреждённые данные сейфа" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(
                Cipher.DECRYPT_MODE,
                secret,
                GCMParameterSpec(GCM_TAG_BITS, raw.copyOfRange(0, IV_LENGTH)),
            )
        }
        val plain = cipher.doFinal(raw.copyOfRange(IV_LENGTH, raw.size))
        return String(plain, Charsets.UTF_8)
    }

    private fun requireKey(): SecretKeySpec = key ?: error("Сейф заблокирован")

    private fun derive(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    private companion object {
        const val PREFS_NAME = "chrononotes_vault"
        const val KEY_SALT = "salt"
        const val KEY_VERIFIER = "verifier"
        const val ITERATIONS = 210_000
        const val KEY_BITS = 256
        const val GCM_TAG_BITS = 128
        const val IV_LENGTH = 12
    }
}
