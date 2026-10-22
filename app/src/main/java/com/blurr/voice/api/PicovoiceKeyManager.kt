package com.blurr.voice.api

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit

/**
 * Nova: holds the user's own free Picovoice AccessKey (https://console.picovoice.ai/).
 * The key is typed in the app and stored on the phone only. The old behaviour of fetching a shared
 * key from the Panda developer's server has been removed.
 */
class PicovoiceKeyManager(private val context: Context) {

    companion object {
        private const val TAG = "PicovoiceKeyManager"
        private const val PREFS_NAME = "PicovoicePrefs"
        private const val KEY_USER_PROVIDED_KEY = "user_provided_access_key"
    }

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Returns the user's Picovoice AccessKey, or null if they have not entered one yet. */
    suspend fun getAccessKey(): String? {
        val key = getUserProvidedKey()
        return if (key.isNullOrBlank()) {
            Log.w(TAG, "No Picovoice AccessKey saved yet.")
            null
        } else {
            key
        }
    }

    /** Saves the key typed by the user. */
    fun saveUserProvidedKey(accessKey: String) {
        sharedPreferences.edit {
            putString(KEY_USER_PROVIDED_KEY, accessKey.trim())
        }
    }

    /** Gets the key typed by the user, if any. */
    fun getUserProvidedKey(): String? {
        return sharedPreferences.getString(KEY_USER_PROVIDED_KEY, null)
    }

    /** Forgets the saved key. */
    fun clearCache() {
        sharedPreferences.edit {
            remove(KEY_USER_PROVIDED_KEY)
        }
        Log.d(TAG, "Cleared saved Picovoice access key")
    }
}
