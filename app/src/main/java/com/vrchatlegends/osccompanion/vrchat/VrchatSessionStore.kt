package com.vrchatlegends.osccompanion.vrchat

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

interface VrchatCookieStore {
    fun load(): Map<String, String>
    fun save(cookies: Map<String, String>)
    fun clear()
}

/** Stores only VRChat's session cookies, encrypted by a key held in Android Keystore. */
class VrchatSessionStore(context: Context) : VrchatCookieStore {

    private val appContext = context.applicationContext

    /**
     * Null when the keystore is unusable. Losing the saved session only costs one extra
     * sign-in, so this must never be allowed to take the whole app down.
     */
    private val preferences: SharedPreferences? by lazy {
        runCatching { open() }
            .recoverCatching {
                // A rotated or half-written master key leaves the file permanently undecryptable.
                appContext.deleteSharedPreferences(FILE_NAME)
                open()
            }
            .getOrNull()
    }

    private fun open(): SharedPreferences = EncryptedSharedPreferences.create(
        appContext,
        FILE_NAME,
        MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override fun load(): Map<String, String> {
        val prefs = preferences ?: return emptyMap()
        return COOKIE_NAMES.mapNotNull { name ->
            runCatching { prefs.getString(name, null) }.getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { name to it }
        }.toMap()
    }

    override fun save(cookies: Map<String, String>) {
        val prefs = preferences ?: return
        runCatching {
            prefs.edit().apply {
                clear()
                COOKIE_NAMES.forEach { name ->
                    cookies[name]?.takeIf { it.isNotBlank() }?.let { putString(name, it) }
                }
            }.apply()
        }
    }

    override fun clear() {
        runCatching { preferences?.edit()?.clear()?.apply() }
    }

    private companion object {
        const val FILE_NAME = "vrchat_session"
        val COOKIE_NAMES = setOf("auth", "twoFactorAuth")
    }
}