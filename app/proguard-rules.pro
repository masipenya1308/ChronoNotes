# Room: сущности и DAO используются через рефлексию/генерацию кода
-keep class com.chrononotes.app.data.** { *; }
-keep class androidx.room.** { *; }

# Compose и KotlinxReflection — safest keep for generated metadata
-keep class androidx.compose.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlinx.coroutines.**
-dontwarn androidx.compose.**
