package com.eraherm.hermchat.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted prefs backed by Android Keystore.
 * Falls back to plain prefs only if crypto init fails (rare OEM issues).
 */
object SecurePrefs {
    fun open(
        context: Context,
        secureName: String,
        plainName: String? = null,
        migrateKeys: List<String> = emptyList(),
    ): SharedPreferences {
        val app = context.applicationContext
        val secure = runCatching {
            val masterKey = MasterKey.Builder(app)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                app,
                secureName,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }.getOrElse {
            // Last resort so the app still boots; secrets stay in plain prefs.
            return app.getSharedPreferences(secureName + "_fallback", Context.MODE_PRIVATE)
        }

        if (plainName != null && migrateKeys.isNotEmpty()) {
            migrateFromPlain(app, plainName, secure, migrateKeys)
        }
        return secure
    }

    private fun migrateFromPlain(
        context: Context,
        plainName: String,
        secure: SharedPreferences,
        keys: List<String>,
    ) {
        val plain = context.getSharedPreferences(plainName, Context.MODE_PRIVATE)
        val editor = secure.edit()
        var moved = false
        keys.forEach { key ->
            if (secure.contains(key)) return@forEach
            when {
                plain.contains(key) && plain.all[key] is String -> {
                    editor.putString(key, plain.getString(key, null))
                    moved = true
                }
                plain.contains(key) && plain.all[key] is Boolean -> {
                    editor.putBoolean(key, plain.getBoolean(key, false))
                    moved = true
                }
                plain.contains(key) && plain.all[key] is Int -> {
                    editor.putInt(key, plain.getInt(key, 0))
                    moved = true
                }
                plain.contains(key) && plain.all[key] is Long -> {
                    editor.putLong(key, plain.getLong(key, 0L))
                    moved = true
                }
            }
        }
        if (moved) {
            editor.apply()
            plain.edit().clear().apply()
        }
    }
}
