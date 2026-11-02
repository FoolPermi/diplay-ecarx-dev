package com.shilapi.xcertplay.hud

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import com.ecarx.xui.adaptapi.input.IKeyCallback
import com.ecarx.xui.adaptapi.input.Input

/**
 * ECARX steering-wheel key interception through the compile-only AdaptAPI, recovered from a third-party
 * app that runs on this head unit.
 *
 * On 领克 / 亿咖通 (820A) head units the steering wheel does NOT deliver standard Android key codes
 * through a normal `Activity.dispatchKeyEvent`: the wheel reports keys in the ECARX 200000-offset
 * space and only surfaces them through the ROM's key-interception API. A third-party app that only
 * listens to standard key codes therefore never sees a wheel press (the exact "方控学习后按键无反应"
 * symptom). The reference app fixes this by registering the nine wheel keys with
 * `com.ecarx.xui.adaptapi.input.Input.requestKeysInterception(int[], IKeyCallback)`.
 *
 * The runtime probe precedes construction of the SDK callback. Vendor classes are never packaged
 * in this APK; a head unit without the API keeps the normal key path.
 */
object EcarxSwKeyIntercept {
    private const val TAG = "DiPlay-Ecarx-SwKey"
    private const val READY = "com.ecarx.input.action.ACTION_INPUT_KEY_SERVICE_READY"
    private val activeLabels = linkedMapOf<String, Int>()
    private var recentLabel = "none"
    private var initialAttempts = 0L
    private var readyAttempts = 0L
    private var initialResult = "none"
    private var readyResult = "none"
    private var receiverResult = "none"
    private var acceptedKeys: IntArray? = null
    private var downCount = 0L
    private var upCount = 0L
    private var lastKey = "none"
    private var lastAction = "none"
    private var lastEpochMs = 0L

    /** The nine wheel keys the reference app intercepts (media.oneOsKey, media.smali:22626). */
    private val oneOsKey: IntArray = intArrayOf(
        0x30D98, // 200088 prev
        0x30D97, // 200087 next
        0x30D95, // 200085 play/pause
        0x30E27, // 200231 voice
        0x30DE4, // 200164 ?
        0x30ED0, // 200400 ?
        0x30D58, // 200024 volume+
        0x30D59, // 200025 volume-
        0x77,    // 119 ?
    )

    /**
     * [oneOsKey] by position. A bare key code says nothing in a log line, and the whole point of
     * printing the ROM's answer is to see *which* keys it kept for itself.
     */
    private val oneOsName: Array<String> = arrayOf(
        "prev", "next", "play", "voice", "k200164", "k200400", "vol+", "vol-", "k119",
    )

    /** A single wheel key press (or release) surfaced by the platform callback. */
    interface Listener {
        /** [keyCode] uses ECARX codes; the platform may ignore the callback's Boolean result. */
        fun onKeyPressed(keyCode: Int): Boolean
        fun onKeyReleased(keyCode: Int): Boolean
    }

    data class Result(val ok: Boolean, val reason: String)

    /**
     * Registers the nine wheel keys; the returned handle must be released on teardown.
     *
     * [label] only names the caller in diagnostics: the ROM's key-interception API is per-process,
     * so two live registrations (the settings screen and the CarPlay screen) can both answer one
     * press, and the log has to say which one did.
     */
    fun register(context: Context, listener: Listener, label: String = "app"): Handle? {
        if (!EcarxCapabilities.classPresent("com.ecarx.xui.adaptapi.input.Input") ||
            !EcarxCapabilities.classPresent("com.ecarx.xui.adaptapi.input.IKeyCallback")) {
            synchronized(this) {
                recentLabel = label
                initialAttempts++
                initialResult = "api-unavailable"
                acceptedKeys = null
            }
            return null
        }
        return Handle(context.applicationContext, listener, label).also { it.start() }
    }

    /** A held interception registration; [release] hands the keys back to the platform. */
    class Handle internal constructor(
        private val app: Context,
        private var listener: Listener?,
        val label: String,
    ) {
        private var live = true
        private var receiverRegistered = false
        private var input: Input? = null
        private var callback: IKeyCallback? = null
        private var callbackHandler: CallbackHandler? = null
        private var state = "waiting"
        /** SDK whitelist answer; it does not prove events are delivered or exclusively owned. */
        var registered: IntArray? = null
            private set
        private val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == READY) rebind(ready = true)
            }
        }

        internal fun start() {
            synchronized(EcarxSwKeyIntercept) {
                recentLabel = label
                activeLabels[label] = (activeLabels[label] ?: 0) + 1
            }
            receiverRegistered = runCatching {
                if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(receiver, IntentFilter(READY), Context.RECEIVER_EXPORTED)
                else app.registerReceiver(receiver, IntentFilter(READY))
            }.onFailure { Log.i(TAG, "READY receiver failed: ${describe(it)}") }.isSuccess
            synchronized(EcarxSwKeyIntercept) { receiverResult = if (receiverRegistered) "registered" else "register-failed" }
            rebind(ready = false)
        }

        @Synchronized private fun rebind(ready: Boolean) {
            if (!live) return
            val oldInput = input
            val oldCallback = callback
            callbackHandler?.deactivate()
            input = null; callback = null; callbackHandler = null; registered = null
            if (oldInput != null && oldCallback != null) abandon(oldInput, oldCallback)
            val fresh = runCatching { Input.create(app) }.getOrNull()
            val current = listener
            val result = when {
                fresh == null -> "create-failed"
                current == null -> "released"
                else -> {
                    val handler = CallbackHandler(current, label)
                    val answer = runCatching { fresh.requestKeysInterception(oneOsKey, handler) }
                    if (answer.isSuccess) {
                        input = fresh; callback = handler; callbackHandler = handler
                        registered = answer.getOrNull()?.copyOf()
                        "registered"
                    } else {
                        handler.deactivate()
                        abandon(fresh, handler)
                        "request-failed:${answer.exceptionOrNull()!!.javaClass.simpleName}"
                    }
                }
            }
            state = result
            synchronized(EcarxSwKeyIntercept) {
                recentLabel = label
                if (ready) { readyAttempts++; readyResult = result }
                else { initialAttempts++; initialResult = result }
                acceptedKeys = registered?.copyOf()
            }
            Log.i(TAG, "interception $label trigger=${if (ready) "READY" else "initial"} ${describe()} result=$result")
        }

        fun release() {
            synchronized(this) {
                if (!live) return
                live = false
                callbackHandler?.deactivate()
                listener = null
                val oldInput = input
                val oldCallback = callback
                input = null; callback = null; callbackHandler = null
                if (receiverRegistered) {
                    receiverRegistered = false
                    runCatching { app.unregisterReceiver(receiver) }
                        .onFailure { Log.i(TAG, "READY receiver unregister failed: ${describe(it)}") }
                }
                if (oldInput != null && oldCallback != null) abandon(oldInput, oldCallback)
                state = "released"
                synchronized(EcarxSwKeyIntercept) {
                    recentLabel = label
                    val remaining = (activeLabels[label] ?: 1) - 1
                    if (remaining > 0) activeLabels[label] = remaining else activeLabels.remove(label)
                    receiverResult = "released"
                }
            }
        }

        /** One log line: which key set went out and what the ROM answered, each key named. */
        fun describe(): String {
            val registeredKeys = registered ?: return "keys=${oneOsKey.size} registered=unknown state=$state"
            if (registeredKeys.isEmpty()) return "keys=${oneOsKey.size} registered=none state=$state"
            val answer = StringBuilder("registered=")
            var named = 0
            for (key in registeredKeys) {
                if (named > 0) answer.append('|')
                answer.append(nameFor(key))
                named += 1
            }
            return "keys=${oneOsKey.size} $answer state=$state"
        }

        private fun abandon(input: Input, callback: IKeyCallback) {
            runCatching { input.abandonKeysInterception(callback) }
                .onFailure { Log.i(TAG, "abandonKeysInterception failed: ${describe(it)}") }
        }
    }

    @Synchronized fun diagnosticReport(): String {
        val active = if (activeLabels.isEmpty()) "none" else activeLabels.entries.joinToString("|") { "${it.key}:${it.value}" }
        val accepted = acceptedKeys?.joinToString("|") { nameFor(it) } ?: "unknown"
        return "ECARX Input activeHandles=$active recent=$recentLabel lastReceiverResult=$receiverResult " +
            "initialAttempts=$initialAttempts initialResult=$initialResult READYAttempts=$readyAttempts READYResult=$readyResult " +
            "requested=${oneOsKey.joinToString("|") { nameFor(it) }} lastSDKAccepted=$accepted " +
            "received=${downCount + upCount} DOWN=$downCount UP=$upCount lastKey=$lastKey lastAction=$lastAction lastEpochMs=$lastEpochMs; " +
            "counts are process-lifetime; SDKAccepted is whitelist only, not exclusive delivery; received=0 cannot establish HY interception"
    }

    /** The name of [keyCode] when it is one of [oneOsKey]; the number itself otherwise. */
    private fun nameFor(keyCode: Int): String {
        var index = 0
        while (index < oneOsKey.size) {
            if (oneOsKey[index] == keyCode) return oneOsName[index]
            index += 1
        }
        return keyCode.toString()
    }

    // ---------------------------------------------------------------- plumbing

    private fun describe(error: Throwable): String {
        val cause = (error as? java.lang.reflect.InvocationTargetException)?.targetException ?: error
        return "${cause.javaClass.simpleName}: ${cause.message}"
    }

    @Synchronized private fun received(label: String, keyCode: Int, action: String) {
        recentLabel = label
        if (action == "DOWN") downCount++ else upCount++
        lastKey = nameFor(keyCode)
        lastAction = action
        lastEpochMs = System.currentTimeMillis()
    }

    private class CallbackHandler(@Volatile private var listener: Listener?, private val label: String) : IKeyCallback {
        @Volatile private var active = true
        fun deactivate() { active = false; listener = null }
        override fun onKeyPressed(keyCode: Int): Boolean {
            val target = listener
            if (!active || target == null) return false
            received(label, keyCode, "DOWN")
            return target.onKeyPressed(keyCode)
        }

        override fun onKeyReleased(keyCode: Int): Boolean {
            val target = listener
            if (!active || target == null) return false
            received(label, keyCode, "UP")
            return target.onKeyReleased(keyCode)
        }
    }
}
