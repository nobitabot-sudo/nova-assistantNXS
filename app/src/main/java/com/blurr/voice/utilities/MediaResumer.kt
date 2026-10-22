package com.blurr.voice.utilities

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent

/**
 * Nova: while Nova listens, Android's speech recognizer takes audio focus and the video you were
 * watching (YouTube etc.) pauses. This remembers that something was playing when the conversation
 * started and presses "play" again when Nova is done, so you do not have to touch the phone.
 *
 * It is skipped when you asked Nova to do a task ("pause the video", "open Spotify", ...), so it never
 * undoes what you asked for.
 */
object MediaResumer {
    private const val TAG = "MediaResumer"
    private const val PREFS = "nova_audio"
    private const val KEY_RESUME = "resume_media"

    @Volatile
    private var wasPlaying = false

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_RESUME, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_RESUME, enabled).apply()
    }

    /** Call when a conversation starts, before Nova listens. */
    fun remember(context: Context) {
        wasPlaying = try {
            isEnabled(context) &&
                (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).isMusicActive
        } catch (e: Exception) {
            false
        }
        Log.d(TAG, "Media was playing at conversation start: $wasPlaying")
    }

    /** Call when the user asked for a task, so we must not resume anything afterwards. */
    fun cancel() {
        wasPlaying = false
    }

    /** Call when the conversation ends. Waits a moment, then presses play if the media stayed paused. */
    fun resumeIfNeeded(context: Context) {
        if (!wasPlaying) return
        wasPlaying = false
        val appContext = context.applicationContext
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                val audio = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                if (!audio.isMusicActive) {
                    audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
                    audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY))
                    Log.d(TAG, "Pressed play to resume the media.")
                } else {
                    Log.d(TAG, "Media already playing again.")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not resume media: ${e.message}")
            }
        }, 900)
    }
}
