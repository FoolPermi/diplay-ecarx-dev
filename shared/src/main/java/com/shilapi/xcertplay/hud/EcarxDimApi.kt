package com.shilapi.xcertplay.hud

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.ecarx.xui.adaptapi.diminteraction.DimInteraction
import com.ecarx.xui.adaptapi.diminteraction.INaviInteraction
import java.lang.reflect.Modifier

/**
 * Compile-only AdaptAPI access. The head unit supplies every vendor class at runtime.
 * Calls retain diagnostic failures, including missing classes or methods on other firmware.
 * A successful void call means dispatch only; it does not prove the cluster changed.
 */
internal object EcarxDimApi {
    private const val TAG = "DiPlay-Ecarx-DIM"
    @Volatile private var reportSink: ((String) -> Unit)? = null

    fun attachReport(sink: (String) -> Unit) { reportSink = sink }

    private fun report(line: String) {
        Log.i(TAG, line)
        reportSink?.invoke(line)
    }

    const val DEFAULT_SCREEN_ID = 1

    data class EcarxResult(val ok: Boolean, val reason: String) {
        companion object {
            fun ok(what: String) = EcarxResult(true, what)
            fun failed(what: String) = EcarxResult(false, what)
        }
    }

    /** App-owned handles keep vendor types out of the always-loaded HUD bridge's fields. */
    class DimHandle internal constructor(internal val api: DimInteraction)
    class NaviHandle internal constructor(internal val api: INaviInteraction)

    /** One navigation frame in ECARX vocabulary. `-1` means "this field has no new value". */
    data class Guidance(
        val turnId: Int = 0,
        val distanceToNextMeters: Long = -1,
        val distanceToDestinationMeters: Long = -1,
        val etaEpochSeconds: Long = -1,
        val drivingDirection: Int = 0,
        val status: Int = 0,
        val muteState: Int = 0,
        val dayNightMode: Int = 0,
        val nextGuidancePointName: String = "",
    )

    data class Lane(val number: Int, val iconId: Int)

    fun createDimInteraction(context: Context): DimHandle? = runCatching {
        DimInteraction.create(context)?.let(::DimHandle)
    }.getOrElse {
        report("DimInteraction.create failed: ${describe(it)}")
        null
    }

    fun naviInteraction(dim: DimHandle): NaviHandle? = runCatching {
        dim.api.naviInteraction?.let(::NaviHandle)
    }.getOrElse {
        report("getNaviInteraction failed: ${describe(it)}")
        null
    }

    fun setPresentationToDimSwitch(
        dim: DimHandle,
        show: Boolean,
        screenId: Int = DEFAULT_SCREEN_ID,
        packageName: String = "",
    ): EcarxResult = dispatch("setPresentationToDimSwitch($screenId,\"$packageName\",\"$packageName\",$show)") {
        dim.api.setPresentationToDimSwitch(screenId, packageName, packageName, show)
    }

    fun registerInteractionCallback(dim: DimHandle, label: String, onOption: (Int) -> Unit): Any? = runCatching {
        val callback = DimInteraction.IInteractionCallback { option ->
            report("callback dim.$label onShowPresentationOptionChanged([$option])")
            onOption(option)
        }
        dim.api.registerInteractionCallback(callback)
        callback
    }.getOrElse {
        report("registerInteractionCallback failed: ${describe(it)}")
        null
    }

    fun registerNavigationInteractionCallback(navi: NaviHandle, label: String, onAction: ((Int) -> Unit)? = null): Any? = runCatching {
        val callback = object : INaviInteraction.INavigationInteractionCallback {
            override fun onDoInteractionAction(action: Int, address: INaviInteraction.IAddress?) {
                report("callback navi.$label onDoInteractionAction([$action, $address])")
                onAction?.invoke(action)
            }

            override fun onSearchAddress(query: String?) {
                if (NavigationOutputs.debugLogsEnabled()) report("callback navi.$label onSearchAddress([$query])")
            }
        }
        navi.api.registerNavigationInteractionCallback(callback)
        callback
    }.getOrElse {
        report("registerNavigationInteractionCallback failed: ${describe(it)}")
        null
    }

    fun updateNavigationInfo(navi: NaviHandle, guidance: Guidance): EcarxResult = dispatch("updateNavigationInfo") {
        navi.api.updateNavigationInfo(object : INaviInteraction.INavigationInfo {
            override fun getNavigationTurnId() = guidance.turnId
            override fun getDistanceToNextGuidancePoint() = guidance.distanceToNextMeters.coerceAtLeast(0L)
            override fun getDistanceToDestination() = guidance.distanceToDestinationMeters.coerceAtLeast(0L)
            override fun getETA() = guidance.etaEpochSeconds.coerceAtLeast(0L)
            override fun getDrivingDirection() = guidance.drivingDirection
            override fun getNavigationStatus() = guidance.status
            override fun getMuteState() = guidance.muteState
            override fun getDayNightMode() = guidance.dayNightMode
            override fun getNextGuidancePointName() = guidance.nextGuidancePointName
            // The OEM app sends drawings through updateTurnByTurnArrow, not this getter.
            override fun getNavigationTurnSVG() = ""
            override fun getLaneInfo(): Array<INaviInteraction.ILaneInfo>? = null
            override fun getExtensionInfo(): android.os.Bundle? = null
            override fun getHighwayExitInfo(): INaviInteraction.IHighwayExitInfo? = null
            override fun getRoadCameraInfo(): INaviInteraction.IRoadCamera? = null
            override fun getServiceAreaInfo(): INaviInteraction.IServiceArea? = null
        })
    }

    fun updateTurnByTurnArrow(navi: NaviHandle, arrow: Int): EcarxResult = dispatch("updateTurnByTurnArrow($arrow)") {
        navi.api.updateTurnByTurnArrow(arrow)
    }

    /** The String overload is the SVG channel used by the factory navigation app. */
    fun updateTurnByTurnArrow(navi: NaviHandle, svg: String): EcarxResult = dispatch("updateTurnByTurnArrow(svg ${svg.length}B)") {
        navi.api.updateTurnByTurnArrow(svg)
    }

    fun updateTurnByTurnArrow(navi: NaviHandle, arrow: Bitmap): EcarxResult = dispatch("updateTurnByTurnArrow(Bitmap)") {
        navi.api.updateTurnByTurnArrow(arrow)
    }

    /** Firmware capability diagnostics still inspect the actual runtime method surface. */
    fun arrowOverloads(target: NaviHandle?): String {
        if (target == null) return "none"
        val names = arrayOf("int", "string", "bitmap")
        val types = arrayOf<Class<*>>(Int::class.javaPrimitiveType!!, String::class.java, Bitmap::class.java)
        return types.indices.filter { index ->
            runCatching { target.api.javaClass.getMethod("updateTurnByTurnArrow", types[index]) }.isSuccess
        }.joinToString("/") { names[it] }.ifEmpty { "none" }
    }

    fun updateDistanceToNextGuidancePoint(navi: NaviHandle, meters: Long): EcarxResult = dispatch("updateDistanceToNextGuidancePoint(${meters.toInt()})") {
        navi.api.updateDistanceToNextGuidancePoint(meters.toInt())
    }

    fun updateDistanceToDestination(navi: NaviHandle, meters: Long): EcarxResult = dispatch("updateDistanceToDestination(${meters.toInt()})") {
        navi.api.updateDistanceToDestination(meters.toInt())
    }

    fun updateEta(navi: NaviHandle, epochSeconds: Long): EcarxResult = dispatch("updateETA(${epochSeconds.toInt()})") {
        navi.api.updateETA(epochSeconds.toInt())
    }

    fun updateNextGuidancePointName(navi: NaviHandle, name: String): EcarxResult = dispatch("updateNextGuidancePointName(\"$name\")") {
        navi.api.updateNextGuidancePointName(name)
    }

    fun updateLaneInfo(navi: NaviHandle, lanes: List<Lane>): EcarxResult = dispatch("updateLaneInfo(${lanes.size})") {
        navi.api.updateLaneInfo(lanes.map { lane ->
            object : INaviInteraction.ILaneInfo {
                override fun getLaneNumber() = lane.number
                override fun getLaneBackIconId() = lane.iconId
                override fun getLaneBackIcon(): Bitmap? = null
            }
        }.toTypedArray())
    }

    /** The 753 navigation plugin sends one null argument to clear the lane strip. */
    fun clearLaneInfo(navi: NaviHandle): EcarxResult = dispatch("updateLaneInfo(null)") { navi.api.updateLaneInfo(null) }

    fun notifyTurnByTurnStarted(navi: NaviHandle): EcarxResult = dispatch("notifyTurnByTurnStarted") { navi.api.notifyTurnByTurnStarted() }

    fun notifyTurnByTurnStopped(navi: NaviHandle): EcarxResult = dispatch("notifyTurnByTurnStopped") { navi.api.notifyTurnByTurnStopped() }

    /** Older firmware omits this from the interface even when an implementation exposes it. */
    fun notifyNavigationStatus(navi: NaviHandle, status: Int): EcarxResult {
        val method = runCatching { navi.api.javaClass.getMethod("notifyNavigationStatus", Int::class.javaPrimitiveType!!) }
            .getOrNull() ?: return EcarxResult.failed("notifyNavigationStatus($status): method not found")
        return dispatch("notifyNavigationStatus($status)") { method.invoke(navi.api, status) }
    }

    fun showPresentationOption(dim: DimHandle): Int? = runCatching { dim.api.showPresentationOption }.getOrNull()

    /** No compilation library has been recovered for this optional firmware extension. */
    fun dimProtocolManager(context: Context): Any? = runCatching {
        val cls = Class.forName(EcarxCapabilities.DIM_PROTOCOL_MANAGER)
        val getInstance = cls.getMethod("getInstance", Context::class.java)
        if (Modifier.isStatic(getInstance.modifiers)) {
            getInstance.invoke(null, context)
        } else {
            val ctor = cls.getDeclaredConstructor().also { it.isAccessible = true }
            getInstance.invoke(ctor.newInstance(), context)
        }
    }.getOrElse {
        report("DIMProtocolManager.getInstance failed: ${describe(it)}")
        null
    }

    private inline fun dispatch(label: String, action: () -> Unit): EcarxResult = runCatching { action() }
        .fold({ EcarxResult.ok(label) }, { EcarxResult.failed("$label threw ${describe(it)}") })

    private fun describe(error: Throwable): String {
        val cause = (error as? java.lang.reflect.InvocationTargetException)?.targetException ?: error
        return "${cause.javaClass.simpleName}: ${cause.message}"
    }
}
