package com.shilapi.xcertplay.hud

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

/**
 * The ECARX private steering-wheel key broadcasts, recovered from the `media$EcarXVoiceKey` /
 * `media$EcarXCalleKey` receivers in the head unit's own key service.
 *
 * On 领克 / 亿咖通 head units the steering wheel's voice key and call key are delivered as system
 * broadcasts rather than as `KeyEvent`s, so a normal activity never sees them. The reference app subscribes to
 * these two actions and calls `abortBroadcast()` on a match to take ownership away from the factory
 * HMI. DiPlay mirrors that: each [register] returns a receiver that forwards the press to [onPress].
 */
object EcarxKeyBroadcasts {
    private const val ACTION_VOICE = "ecarx.intent.action.ECARX_KEY_RVOICEASSIST_EVENT"
    private const val ACTION_CALL = "ecarx.intent.action.ECARX_KEY_RCALL_EVENT"

    /**
     * Owns only the presses [takesPress] accepts while [shouldHandle] permits it; every other press
     * passes through to the factory assistant.
     *
     * The key service marks each press with `ecarx.extra.ECARX_KEY_ACTION_TYPE` — 0 for a short press,
     * 1 for a long one — so the caller picks which of the two DiPlay owns and leaves the other alone.
     */
    fun registerVoice(context: Context, takesPress: (Int) -> Boolean = { it == 0 },
        shouldHandle: () -> Boolean, onPress: (ordered: Boolean) -> Unit): BroadcastReceiver {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != ACTION_VOICE) return
                val press = intent.getIntExtra("ecarx.extra.ECARX_KEY_ACTION_TYPE", -1)
                if (intent.getIntExtra("ecarx.extra.HY", 0) == 1 || !takesPress(press) || !shouldHandle()) return
                if (isOrderedBroadcast) abortBroadcast()
                onPress(isOrderedBroadcast)
            }
        }
        val filter = IntentFilter(ACTION_VOICE).apply {
            priority = Int.MAX_VALUE
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        context.registerReceiver(receiver, filter)
        return receiver
    }

    /** Registers the call-key broadcast; [onPress] runs on the broadcast's thread. */
    fun registerCall(context: Context, onPress: () -> Unit): BroadcastReceiver {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action != ACTION_CALL) return
                abortBroadcast()
                onPress()
            }
        }
        val filter = IntentFilter(ACTION_CALL).apply { priority = IntentFilter.SYSTEM_HIGH_PRIORITY }
        context.registerReceiver(receiver, filter)
        return receiver
    }
}
