package com.shilapi.xcertplay.hud

import android.content.Context
import android.util.Log
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * Microphone arbitration through the target firmware's GKUI Policy API.
 * The recovered SDK is retained separately and not used by the application yet.
 * Reflection keeps ordinary Android recording without arbitration when this API is absent.
 * `isNeedrequestMic` belongs to the HY application wrapper, not the vendor interface.
 */
internal object EcarxMicPolicy {
    private const val TAG = "DiPlay-Ecarx-Mic"

    const val REQUEST_TYPE_SYSTEM = 1
    const val REQUEST_TYPE_CALL = 2
    const val REQUEST_TYPE_RECORD = 3

    interface Listener {
        fun onResume()
        fun onAbort(reason: Int)
    }

    data class Result(val ok: Boolean, val reason: String) {
        companion object {
            fun ok(what: String) = Result(true, what)
            fun failed(what: String) = Result(false, what)
        }
    }

    /** The release closure retains the exact callback accepted by the platform. */
    class Held internal constructor(private val releaseMic: () -> Boolean) {
        val granted = true

        fun release(): Result = runCatching { releaseMic() }.fold(
            { released -> if (released) Result.ok("releaseMic") else Result.failed("releaseMic declined") },
            { Result.failed("releaseMic threw $it") },
        )
    }

    fun describe(): String = buildString {
        append("policyApi=").append(EcarxCapabilities.classPresent(EcarxCapabilities.POLICY_API))
        append(" audioPolicy=").append(EcarxCapabilities.classPresent(EcarxCapabilities.AUDIO_POLICY))
        append(" micCallback=").append(EcarxCapabilities.classPresent(EcarxCapabilities.MIC_CALLBACK))
    }

    /** A usable policy handle means arbitration can be attempted; refusal never blocks recording. */
    fun available(context: Context): Boolean {
        return audioPolicy(context) != null
    }

    /** Requests a grant before capture; callers release an accepted request when capture stops. */
    fun requestMic(context: Context, requestType: Int, listener: Listener): Held? {
        val policy = audioPolicy(context) ?: return null
        return runCatching {
            val type = Class.forName(EcarxCapabilities.AUDIO_POLICY)
            val callbackType = Class.forName(EcarxCapabilities.MIC_CALLBACK)
            val callback = Proxy.newProxyInstance(callbackType.classLoader, arrayOf(callbackType), MicCallbackHandler(listener))
            if (type.getMethod("requestMic", Int::class.javaPrimitiveType, callbackType)
                    .invoke(policy, requestType, callback) != true) {
                Log.i(TAG, "requestMic declined requestType=$requestType")
                return null
            }
            // Preserve grant-on-accept: the reference app starts recording before a later onResume.
            Held { type.getMethod("releaseMic", callbackType).invoke(policy, callback) == true }
        }.getOrElse {
            Log.i(TAG, "requestMic failed: $it")
            null
        }
    }

    private fun audioPolicy(context: Context): Any? = runCatching {
        val type = Class.forName(EcarxCapabilities.POLICY_API)
        val policy = type.getMethod("get", Context::class.java).invoke(null, context) ?: return null
        type.getMethod("getAudioPolicy").invoke(policy)
    }.getOrElse {
        Log.i(TAG, "PolicyAPI unavailable: $it")
        null
    }

    private class MicCallbackHandler(private val listener: Listener) : InvocationHandler {
        override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? = when (method.name) {
            "onResume" -> { listener.onResume(); null }
            "onAbort" -> { listener.onAbort((args?.firstOrNull() as? Int) ?: -1); null }
            "toString" -> "EcarxMicCallback"
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.firstOrNull()
            else -> null
        }
    }
}
