package com.example.dynamiclock.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File

/**
 * Keystore-backed encryption for preferences and files, with a safe fallback to plaintext
 * if the device's keystore is unavailable (so the app never crashes on odd hardware).
 */
object Crypto {

    private fun masterKey(context: Context): MasterKey? = runCatching {
        MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }.getOrNull()

    /** Encrypted SharedPreferences, falling back to normal prefs on failure. */
    fun prefs(context: Context, name: String): SharedPreferences {
        val app = context.applicationContext
        val key = masterKey(app)
        if (key != null) {
            runCatching {
                return EncryptedSharedPreferences.create(
                    app,
                    name,
                    key,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            }
        }
        return app.getSharedPreferences(name + "_plain", Context.MODE_PRIVATE)
    }

    /** Writes bytes encrypted; falls back to a plain write if encryption is unavailable. */
    fun writeBytes(context: Context, file: File, bytes: ByteArray) {
        if (file.exists()) file.delete() // EncryptedFile refuses to overwrite
        val key = masterKey(context)
        if (key != null) {
            val ok = runCatching {
                val ef = EncryptedFile.Builder(
                    context.applicationContext, file, key,
                    EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
                ).build()
                ef.openFileOutput().use { it.write(bytes) }
            }.isSuccess
            if (ok) return
            if (file.exists()) file.delete()
        }
        file.outputStream().use { it.write(bytes) }
    }

    /** Reads bytes, trying the encrypted reader first then a plain read. */
    fun readBytes(context: Context, file: File): ByteArray? {
        if (!file.exists()) return null
        val key = masterKey(context)
        if (key != null) {
            val decrypted = runCatching {
                val ef = EncryptedFile.Builder(
                    context.applicationContext, file, key,
                    EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
                ).build()
                ef.openFileInput().use { it.readBytes() }
            }.getOrNull()
            if (decrypted != null) return decrypted
        }
        return runCatching { file.readBytes() }.getOrNull()
    }
}
