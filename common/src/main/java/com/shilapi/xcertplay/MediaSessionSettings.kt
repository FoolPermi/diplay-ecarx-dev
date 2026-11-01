package com.shilapi.xcertplay

import android.content.Context

// Keep the stored boolean and enum order: ANDROID delegates vehicle media to HY; ECARX is direct.
// Both publish a standard Android session, and the choice takes effect at the next connection.
internal enum class MediaSessionMode { ANDROID, ECARX }

/**
 * Which press of the factory voice key DiPlay takes over on an ECARX head unit.
 *
 * The key service broadcasts both presses with `ecarx.extra.ECARX_KEY_ACTION_TYPE`: 0 for the short
 * press and 1 for the long one (the same two values HY's own receiver splits into key 6 and key 7).
 * DiPlay aborts only the chosen one, so the other still reaches the factory assistant. It takes effect
 * immediately: the receiver decides per press, so no reconnect is needed.
 */
internal enum class VoiceKeyPress(val type: Int) { SHORT(0), LONG(1) }

internal object MediaSessionSettings {
    private const val PREFS = "diplay_media_session"
    private const val KEY_VOICE_PRESS = "voice_key_press"

    fun mode(context: Context): MediaSessionMode =
        if (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("ecarx", true))
            MediaSessionMode.ECARX else MediaSessionMode.ANDROID

    fun setMode(context: Context, mode: MediaSessionMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean("ecarx", mode == MediaSessionMode.ECARX).apply()
    }

    fun voiceKeyPress(context: Context): VoiceKeyPress {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_VOICE_PRESS, VoiceKeyPress.SHORT.type)
        return VoiceKeyPress.entries.firstOrNull { it.type == stored } ?: VoiceKeyPress.SHORT
    }

    fun setVoiceKeyPress(context: Context, press: VoiceKeyPress) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_VOICE_PRESS, press.type).apply()
    }
}
