package com.shilapi.xcertplay.orchestration

import com.shilapi.xcertplay.transport.Iap2BootstrapPhase

/**
 * A progress watchdog for the wireless iAP2 bootstrap handshake.
 *
 * The bootstrap has no single meaningful budget: bringing up RFCOMM, negotiating the iAP2 CSM,
 * authenticating over MFi and the phone-driven transport exchange happen at very different speeds,
 * and a stall in each one means something different. Instead of one global timeout, every
 * checkpoint gets its own limit measured from the previous one, so the limit that fires names the
 * phase that actually stalled:
 *
 *  - [Iap2BootstrapPhase.RFCOMM_CONNECTED] without CSM  → the Bluetooth link never became iAP2.
 *  - [Iap2BootstrapPhase.CSM_ESTABLISHED] without MFi   → the phone never answered identification/MFi.
 *  - [Iap2BootstrapPhase.MFi_AUTHENTICATED] or a later checkpoint without progress → the phone
 *    authenticated but stopped driving the handshake.
 *
 * Once [Iap2BootstrapPhase.START_SESSION_SENT] is out, AirPlay TCP and tunnel readiness are watched
 * by [FirstTcpWatchdog] and the wireless handoff watchdog; this watchdog stops arming timers there
 * so it never races them, and [Iap2BootstrapPhase.TUNNEL_ESTABLISHED] simply ends it.
 *
 * [schedule] and [nowNanos] are injected so the timing logic is unit-testable without a thread.
 */
internal class Iap2BootstrapWatchdog(
    private val schedule: (Long, () -> Unit) -> (() -> Unit),
    private val onTimeout: (Iap2BootstrapStall) -> Unit,
    private val nowNanos: () -> Long = System::nanoTime,
    private val policy: Iap2BootstrapPolicy = Iap2BootstrapPolicy,
    private val log: (String) -> Unit = {},
) {
    @Volatile var finished = false
        private set

    private var highest: Iap2BootstrapPhase? = null
    private var lastProgressNanos = nowNanos()
    // A replaced timer that still runs finds a newer generation and does nothing.
    private var generation = 0
    private var cancelTimer: (() -> Unit)? = null

    /**
     * Records a checkpoint reached at [atNanos]. A later phase advances the limit and re-arms it; an
     * earlier phase still counts as progress for the log but cannot move the limit backwards.
     */
    @Synchronized fun mark(phase: Iap2BootstrapPhase, atNanos: Long = nowNanos()) {
        if (finished) return
        val current = highest
        if (current != null && phase.ordinal <= current.ordinal) {
            log("bootstrap phase=${phase.name} already at=${current.name} ignored")
            return
        }
        val elapsedMillis = (atNanos - lastProgressNanos).coerceAtLeast(0) / NANOS_PER_MILLISECOND
        highest = phase
        lastProgressNanos = atNanos
        log("bootstrap phase=${phase.name} sincePreviousMs=$elapsedMillis")
        if (phase == Iap2BootstrapPhase.TUNNEL_ESTABLISHED) {
            finished = true
            cancel()
            return
        }
        if (phase == Iap2BootstrapPhase.START_SESSION_SENT) {
            // Hand off to FirstTcpWatchdog + the handoff watchdog: they own tunnel readiness.
            // Bump the generation so a timer already running for the previous stage cannot report a
            // tunnel stall for a checkpoint this watchdog deliberately does not time.
            generation++
            cancel()
            return
        }
        arm(phase)
    }

    /** Stops the watchdog without a timeout, e.g. on success, failure or teardown. */
    @Synchronized fun terminate(): Boolean {
        if (finished) return false
        finished = true
        cancel()
        return true
    }

    private fun arm(reached: Iap2BootstrapPhase) {
        val missing = nextPhase(reached) ?: return
        val delay = policy.budgetToReach(missing)
        if (delay <= 0L) return
        val armedGeneration = ++generation
        cancel()
        cancelTimer = schedule(delay) { expire(armedGeneration) }
    }

    private fun expire(armedGeneration: Int) {
        val stall = synchronized(this) {
            if (finished || armedGeneration != generation) return
            val reached = highest ?: return
            val missing = nextPhase(reached) ?: return
            finished = true
            cancelTimer = null
            Iap2BootstrapStall(
                reached = reached,
                missing = missing,
                waitedMillis = (nowNanos() - lastProgressNanos).coerceAtLeast(0) / NANOS_PER_MILLISECOND,
            )
        }
        onTimeout(stall)
    }

    private fun cancel() {
        cancelTimer?.invoke()
        cancelTimer = null
    }

    private fun nextPhase(phase: Iap2BootstrapPhase): Iap2BootstrapPhase? =
        PHASES.getOrNull(phase.ordinal + 1)

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
        val PHASES = Iap2BootstrapPhase.entries
    }
}

/**
 * Per-checkpoint limits for the wireless iAP2 bootstrap, measured from the previous checkpoint.
 *
 * The budgets are deliberately generous: a healthy handshake usually reaches every checkpoint in
 * well under a second, but CSM bring-up has been measured as late as 34.6 s on the real car, so the
 * limits must cover the long tail and only fire on a genuinely stuck phone while still ending the
 * attempt long before the five-minute control-loop budget would.
 */
internal object Iap2BootstrapPolicy {
    /**
     * RFCOMM connect to a live iAP2 peer plus CSM negotiation. The 45 s budget covers the 34.6 s
     * long-tail sample with margin, so a slow but healthy link is not mistaken for a dead one.
     */
    const val RFCOMM_TO_CSM_MILLIS = 45_000L

    /** Identification exchange plus MFi authentication. */
    const val CSM_TO_MFI_MILLIS = 30_000L

    /**
     * Authenticated phone → any further bootstrap progress. Held just above
     * [Iap2WirelessControlClient]'s 60 s transport wait, which already names the "answered nothing"
     * case, so this only fires when that wait is bypassed by partial progress.
     */
    const val MFI_TO_PROGRESS_MILLIS = 65_000L

    /** Partial progress (availability / transport identifier) then silence before StartSession. */
    const val PARTIAL_PROGRESS_MILLIS = 45_000L

    fun budgetToReach(missing: Iap2BootstrapPhase): Long = when (missing) {
        Iap2BootstrapPhase.CSM_ESTABLISHED -> RFCOMM_TO_CSM_MILLIS
        Iap2BootstrapPhase.MFi_AUTHENTICATED -> CSM_TO_MFI_MILLIS
        Iap2BootstrapPhase.WIRELESS_CARPLAY_AVAILABLE -> MFI_TO_PROGRESS_MILLIS
        Iap2BootstrapPhase.TRANSPORT_IDENTIFIED,
        Iap2BootstrapPhase.START_SESSION_SENT -> PARTIAL_PROGRESS_MILLIS
        // Tunnel readiness is watched by FirstTcpWatchdog and the handoff watchdog.
        Iap2BootstrapPhase.RFCOMM_CONNECTED,
        Iap2BootstrapPhase.TUNNEL_ESTABLISHED -> 0L
    }
}

/** The checkpoint the bootstrap stalled at, and what the driver-facing cause should be. */
internal data class Iap2BootstrapStall(
    val reached: Iap2BootstrapPhase,
    val missing: Iap2BootstrapPhase,
    val waitedMillis: Long,
) {
    val cause: ConnectionFailureCause
        get() = when (reached) {
            Iap2BootstrapPhase.RFCOMM_CONNECTED -> ConnectionFailureCause.WIRELESS_BLUETOOTH_LINK_FAILED
            else -> ConnectionFailureCause.WIRELESS_PHONE_NOT_RESPONDING
        }

    fun message(): String = when (reached) {
        Iap2BootstrapPhase.RFCOMM_CONNECTED ->
            "The Bluetooth link connected but the iPhone never opened an iAP2 control session; " +
                "waited ${waitedMillis}ms for CSM after RFCOMM connected"
        else ->
            "The iPhone stopped responding during the wireless iAP2 handshake after " +
                "${reached.name}; waited ${waitedMillis}ms for ${missing.name}"
    }

    fun logLine(): String = "bootstrap stall reached=${reached.name} missing=${missing.name} waitedMs=$waitedMillis"
}
