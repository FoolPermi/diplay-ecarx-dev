package com.shilapi.xcertplay

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/** Read-only package grants and effective access, shared by the inspection screen and report. */
internal object PermissionDiagnosticSnapshot {
    enum class State { GRANTED, DENIED, UNAVAILABLE, UNKNOWN }
    data class Permission(val name: String, val state: State)
    data class Snapshot(
        val uid: Int,
        val signature: Int,
        val permissions: List<Permission>,
        val access: Map<UsbPermissionSetup.Permission, Boolean>,
        val wheelAllowed: Boolean,
        val wheelConnected: Boolean,
        val usageStatsAppOpMode: Int?,
    ) {
        fun report(): String = buildString {
            appendLine("Permission inspection: uid=$uid")
            appendLine("Platform signature check=$signature (0=match)")
            permissions.forEach { appendLine("Permission ${it.name.removePrefix("android.permission.")}=${it.state}") }
            appendLine("USB setup permissions: $access")
            appendLine("Usage access AppOps: GET_USAGE_STATS mode=${usageStatsAppOpMode ?: "unavailable"} " +
                "(0=allowed,1=ignored,2=errored,3=default)")
            appendLine("Wheel access: allowed=$wheelAllowed connected=$wheelConnected")
        }.trimEnd()
    }

    fun log(context: Context, write: (String) -> Unit) {
        runCatching {
            read(context).report().lineSequence().forEach { write("Permissions: $it") }
        }.onFailure { write("Permissions: inspection failed ${it.javaClass.simpleName}") }
    }

    fun read(context: Context): Snapshot {
        val manager = context.packageManager
        val requested = manager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions.orEmpty()
        val permissions = requested.distinct().map { name ->
            val definition = runCatching { manager.getPermissionInfo(name, 0) }
            val state = when {
                definition.exceptionOrNull() is PackageManager.NameNotFoundException -> State.UNAVAILABLE
                definition.isFailure -> State.UNKNOWN
                context.checkSelfPermission(name) == PackageManager.PERMISSION_GRANTED -> State.GRANTED
                else -> State.DENIED
            }
            Permission(name, state)
        }.sortedWith(compareBy({ when (it.state) {
            State.DENIED -> 0
            State.UNKNOWN -> 1
            State.GRANTED -> 2
            State.UNAVAILABLE -> 3
        } }, { it.name }))
        return Snapshot(
            context.applicationInfo.uid,
            manager.checkSignatures(context.packageName, "android"),
            permissions,
            UsbPermissionSetup.snapshot(context),
            WheelKeyService.enabledInSettings(context) &&
                Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1,
            WheelKeyService.connected(),
            HomeScreenMonitor.usageStatsAppOpMode(context),
        )
    }
}
