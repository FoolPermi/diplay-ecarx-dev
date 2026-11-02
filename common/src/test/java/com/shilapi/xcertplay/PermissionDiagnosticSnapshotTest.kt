package com.shilapi.xcertplay

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.content.pm.Signature
import android.provider.Settings
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class PermissionDiagnosticSnapshotTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val granted = "test.permission.GRANTED"
    private val denied = "test.permission.DENIED"
    private val unsupported = "test.permission.NEW_ANDROID"

    @Before fun setup() {
        val packages = shadowOf(app.packageManager)
        val signature = Signature("1234")
        packages.installPackage(PackageInfo().apply {
            packageName = app.packageName
            applicationInfo = app.applicationInfo
            requestedPermissions = arrayOf(granted, denied, unsupported, granted)
            signatures = arrayOf(signature)
        })
        packages.installPackage(PackageInfo().apply {
            packageName = "android"
            signatures = arrayOf(signature)
        })
        for (permission in listOf(granted, denied)) packages.addPermissionInfo(PermissionInfo().apply {
            name = permission
            packageName = "android"
        })
        shadowOf(app).grantPermissions(granted)
        shadowOf(app).denyPermissions(denied)
    }

    @Test fun grantsAreReadIndividuallyAndMissingDefinitionsAreNotDeniedGrants() {
        val snapshot = PermissionDiagnosticSnapshot.read(app)
        assertEquals(PackageManager.SIGNATURE_MATCH, snapshot.signature)
        assertEquals(app.applicationInfo.uid, snapshot.uid)
        assertEquals(listOf(denied, granted, unsupported), snapshot.permissions.map { it.name })
        assertEquals(listOf(PermissionDiagnosticSnapshot.State.DENIED, PermissionDiagnosticSnapshot.State.GRANTED,
            PermissionDiagnosticSnapshot.State.UNAVAILABLE), snapshot.permissions.map { it.state })
        assertTrue(snapshot.report().contains("Permission $granted=GRANTED"))
        assertTrue(snapshot.report().contains("Permission $denied=DENIED"))
    }

    @Test fun refreshReadsCurrentGrantsWithoutChangingOtherSettings() {
        val key = Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        Settings.Secure.putString(app.contentResolver, key, "other.app/.Service")
        assertEquals(PermissionDiagnosticSnapshot.State.DENIED,
            PermissionDiagnosticSnapshot.read(app).permissions.single { it.name == denied }.state)
        shadowOf(app).grantPermissions(denied)
        assertEquals(PermissionDiagnosticSnapshot.State.GRANTED,
            PermissionDiagnosticSnapshot.read(app).permissions.single { it.name == denied }.state)
        assertEquals("other.app/.Service", Settings.Secure.getString(app.contentResolver, key))
    }

    @Test fun platformSignatureIsCheckedRatherThanInferredFromPermissionGrants() {
        val packages = shadowOf(app.packageManager)
        packages.installPackage(PackageInfo().apply { packageName = "android"; signatures = arrayOf(Signature("5678")) })
        assertEquals(PackageManager.SIGNATURE_NO_MATCH, PermissionDiagnosticSnapshot.read(app).signature)
    }
}
