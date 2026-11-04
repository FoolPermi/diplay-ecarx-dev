package com.shilapi.xcertplay.orchestration

import com.shilapi.xcertplay.mfi.Iap2MfiAuthenticationException
import com.shilapi.xcertplay.mfi.MfiException
import com.shilapi.xcertplay.network.WirelessStartupException
import com.shilapi.xcertplay.transport.Iap2IdentificationException
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.CancellationException

/** Budgets belong to one wireless run, not to each phone or socket. No Wi-Fi resources live here. */
internal class WirelessBootstrapRecovery(
    private val isCurrent: () -> Boolean,
    private val validateInfrastructure: () -> Unit,
    private val handoffStarted: () -> Boolean,
    private val pause: (Long) -> Unit = Thread::sleep,
    private val log: (String) -> Unit = {},
) {
    enum class Stage { CONNECT, CSM, BOOTSTRAP, CONTROL }

    class Attempt(private val current: () -> Boolean, val number: Int) {
        @Volatile private var valid = true
        var stage = Stage.CONNECT
        val isCurrent: Boolean get() = valid && current()
        fun checkCurrent() {
            if (!isCurrent) throw CancellationException("Stale wireless bootstrap attempt")
        }
        fun invalidate() { valid = false }
    }

    fun <T> run(
        target: T,
        validateTarget: (T) -> Unit,
        fallback: (T) -> T?,
        // Must release this attempt's CSM/stream/socket before propagating a failure.
        connectAndBootstrap: (T, Attempt) -> Unit,
    ) {
        var selected = target
        var connectRetryUsed = false
        var bootstrapRetryUsed = false
        var fallbackUsed = false
        var number = 0
        fun checkReady() {
            if (!isCurrent()) throw CancellationException("Stale wireless run")
            if (handoffStarted()) throw CancellationException("Wireless handoff already started")
            validateInfrastructure()
            validateTarget(selected)
            if (!isCurrent() || handoffStarted()) throw CancellationException("Wireless retry cancelled")
        }
        while (true) {
            checkReady()
            val attempt = Attempt(isCurrent, ++number)
            try {
                connectAndBootstrap(selected, attempt)
                return
            } catch (error: Exception) {
                attempt.invalidate()
                if (!isCurrent() || handoffStarted() || !retryable(error)) throw error
                // Recheck radio, permissions, pairing and retained Wi-Fi services *before* retrying.
                checkReady()
                val delay = when {
                    attempt.stage == Stage.CONNECT && !connectRetryUsed -> {
                        connectRetryUsed = true
                        CONNECT_BACKOFF_MILLIS
                    }
                    attempt.stage == Stage.CONNECT && !fallbackUsed -> {
                        val alternative = fallback(selected) ?: throw error
                        selected = alternative
                        fallbackUsed = true
                        0L
                    }
                    (attempt.stage == Stage.CSM || attempt.stage == Stage.BOOTSTRAP) && !bootstrapRetryUsed -> {
                        bootstrapRetryUsed = true
                        BOOTSTRAP_BACKOFF_MILLIS
                    }
                    else -> throw error
                }
                log("local recovery stage=${attempt.stage} attempt=$number delayMs=$delay fallback=$fallbackUsed")
                // Short cancellable slices, including an infrastructure check after the backoff.
                var remaining = delay
                while (remaining > 0) {
                    if (!isCurrent() || handoffStarted()) throw CancellationException("Wireless retry cancelled")
                    val slice = minOf(remaining, 100L)
                    pause(slice)
                    remaining -= slice
                }
            } finally {
                attempt.invalidate()
            }
        }
    }

    companion object {
        const val CSM_TIMEOUT_MILLIS = 5_000L
        const val HANDSHAKE_TIMEOUT_MILLIS = 15_000L
        const val CONNECT_BACKOFF_MILLIS = 400L
        const val BOOTSTRAP_BACKOFF_MILLIS = 800L

        /** Do not let the host turn a terminal prerequisite/rejection into a full-stack retry loop. */
        internal fun requiresUserAction(error: Throwable): Boolean =
            generateSequence(error) { it.cause }.take(8).any {
                it is SecurityException || it is com.shilapi.xcertplay.mfi.MfiAuthenticationFailedException ||
                    it.message?.contains(com.shilapi.xcertplay.transport.BluetoothRadioCheck.RADIO_OFF_MARK) == true ||
                    it is Iap2IdentificationException ||
                    it is Iap2MfiAuthenticationException && !it.retryable ||
                    it is ConnectionFailureException && it.reason in setOf(
                        ConnectionFailureCause.NO_PAIRED_IPHONE, ConnectionFailureCause.CHOSEN_IPHONE_NOT_PAIRED,
                        ConnectionFailureCause.IPHONE_NOT_CHOSEN, ConnectionFailureCause.WIRELESS_CARPLAY_UNAVAILABLE,
                    )
            }

        internal fun retryable(error: Throwable): Boolean {
            if (requiresUserAction(error)) return false
            val causes = generateSequence(error) { it.cause }.take(8).toList()
            if (causes.any { it is Error || it is SecurityException || it is IllegalArgumentException ||
                    it is IllegalStateException || it is InterruptedException || it is InterruptedIOException ||
                    it is CancellationException || it is WirelessStartupException ||
                    it is ConnectionFailureException || it is Iap2IdentificationException }) return false
            if (error is Iap2MfiAuthenticationException) return error.retryable
            if (causes.any { it is MfiException }) return false
            return error is IOException
        }
    }
}
