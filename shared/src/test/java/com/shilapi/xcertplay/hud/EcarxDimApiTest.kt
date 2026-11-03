package com.shilapi.xcertplay.hud

import android.cevt.dimsupportservice.DimSupportManager
import com.ecarx.xui.adaptapi.diminteraction.FixtureLog
import com.ecarx.xui.adaptapi.diminteraction.INaviInteraction
import com.ecarx.xui.adaptapi.diminteraction.NaviFixture
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class EcarxDimApiTest {
    class NaviWithStatus : NaviFixture() {
        fun notifyNavigationStatus(status: Int) = FixtureLog.capture("notifyNavigationStatus", "status", status)
    }

    @Before fun setup() { FixtureLog.reset(); DimSupportManager.reset() }
    @After fun cleanup() { FixtureLog.reset(); DimSupportManager.reset() }

    @Test fun managerRefreshReachesTheFirmwareEvenWhenSdkCachedAnInternallyFailedArrow() {
        val target = NaviFixture()
        val navi = EcarxDimApi.NaviHandle(target)
        DimSupportManager.failedIconWrites = 1
        repeat(2) { assertTrue(EcarxDimApi.updateTurnByTurnArrow(navi, 103).ok) }
        assertEquals(103, target.cachedArrowId)
        assertEquals(1, DimSupportManager.iconAttempts)
        assertTrue(DimSupportManager.writes.isEmpty())

        val snapshot = EcarxDimApi.Guidance(status = 1, turnId = 103, distanceToNextMeters = 350,
            nextGuidancePointName = "next-road", distanceToDestinationMeters = 9876, etaRemainingSeconds = 600)
        repeat(2) { assertTrue(EcarxDimApi.refreshNavigation(snapshot, "destination").ok) }
        assertEquals(3, DimSupportManager.iconAttempts)
        assertEquals(2, DimSupportManager.writes.count { it == "icon:0:103" })
        assertEquals(2, DimSupportManager.writes.count { it == "next:350:next-road" })
        assertEquals(2, DimSupportManager.writes.count { it == "distance:9876" })
        assertEquals(2, DimSupportManager.writes.count { it == "eta:600" })
        assertEquals(2, DimSupportManager.writes.count { it == "ongoing:true:destination:false" })
    }

    @Test fun managerRefreshOmitsUnknownTurnDataAndContinuesAfterAFieldFailure() {
        DimSupportManager.failNextTurn = true
        val failed = EcarxDimApi.refreshNavigation(EcarxDimApi.Guidance(status = 1, turnId = 103,
            distanceToNextMeters = 350, distanceToDestinationMeters = 9876, etaRemainingSeconds = 600), "")
        assertFalse(failed.ok)
        assertTrue(failed.reason.contains("next="))
        assertTrue(DimSupportManager.writes.containsAll(listOf("icon:0:103", "distance:9876", "eta:600")))

        DimSupportManager.reset()
        assertTrue(EcarxDimApi.refreshNavigation(EcarxDimApi.Guidance(status = 1,
            distanceToDestinationMeters = 9876, etaRemainingSeconds = 600), "").ok)
        assertEquals(listOf("ongoing:true::false", "distance:9876", "eta:600"), DimSupportManager.writes)
        DimSupportManager.available = false
        assertFalse(EcarxDimApi.refreshNavigation(EcarxDimApi.Guidance(status = 1), "").ok)
    }

    @Test fun destinationIsNameOnlyAndClearsWithoutReactivatingIdleNavigation() {
        val target = object : NaviFixture() {
            override fun updateAddresses(type: Int, addresses: MutableList<INaviInteraction.IAddress>) {
                assertEquals(INaviInteraction.TYPE_DESTINATION, type)
                addresses.singleOrNull()?.let {
                    assertTrue(it.latitude.isNaN())
                    assertTrue(it.longitude.isNaN())
                    assertEquals(-1L, it.distance)
                    assertEquals("", it.fullAddress)
                    assertEquals("", it.cityName)
                }
                super.updateAddresses(type, addresses)
            }
        }
        val navi = EcarxDimApi.NaviHandle(target)
        assertTrue(EcarxDimApi.updateNavigationInfo(navi,
            EcarxDimApi.Guidance(status = EcarxDimApi.NAVIGATION_SNAPSHOT_ACTIVE)).ok)
        assertTrue(EcarxDimApi.updateDestinationName(navi, "目的地").ok)
        assertEquals("目的地", target.destinationName)
        assertEquals(listOf("ongoingNavi", "ongoingNavi"), target.navigationEvents)
        assertTrue(EcarxDimApi.updateDestinationName(navi, "目的地").ok)
        assertEquals(2, target.navigationEvents.size)

        assertTrue(EcarxDimApi.updateNavigationInfo(navi,
            EcarxDimApi.Guidance(status = EcarxDimApi.NAVIGATION_SNAPSHOT_IDLE)).ok)
        assertTrue(EcarxDimApi.updateDestinationName(navi, "").ok)
        assertEquals("", target.destinationName)
        assertEquals(listOf("ongoingNavi", "ongoingNavi", "idle", "idle"), target.navigationEvents)
        val calls = FixtureLog.calls().filter { it.method == "updateAddresses" }
        assertEquals(listOf(1, 1, 0), calls.map { it.payload["count"] })
    }

    @Test fun destinationRejectionRetainsOldNameAndDoesNotBlockOtherDispatches() {
        val target = NaviFixture()
        val navi = EcarxDimApi.NaviHandle(target)
        assertTrue(EcarxDimApi.updateDestinationName(navi, "旧目的地").ok)
        FixtureLog.failOnce("updateAddresses")
        val rejected = EcarxDimApi.updateDestinationName(navi, "新目的地")
        assertFalse(rejected.ok)
        assertFalse(rejected.reason.contains("新目的地"))
        assertEquals("旧目的地", target.destinationName)
        assertTrue(EcarxDimApi.clearLaneInfo(navi).ok)
        assertTrue(EcarxDimApi.notifyTurnByTurnStopped(navi).ok)
        assertTrue(EcarxDimApi.updateDestinationName(navi, "新目的地").ok)
        FixtureLog.failOnce("updateAddresses")
        assertFalse(EcarxDimApi.updateDestinationName(navi, "").ok)
        assertEquals("新目的地", target.destinationName)
        assertTrue(EcarxDimApi.updateDestinationName(navi, "").ok)
        assertEquals("", target.destinationName)
    }

    @Test fun etaUsesRemainingSecondsInBothTheSnapshotAndSingleField() {
        val navi = EcarxDimApi.NaviHandle(NaviFixture())
        assertTrue(EcarxDimApi.updateNavigationInfo(navi,
            EcarxDimApi.Guidance(etaRemainingSeconds = 600)).ok)
        assertTrue(EcarxDimApi.updateEta(navi, 600).ok)
        assertEquals(600L, FixtureLog.calls().single { it.method == "updateNavigationInfo" }.payload["getETA"])
        assertEquals(600, FixtureLog.calls().single { it.method == "updateETA" }.payload["remainingSeconds"])
    }

    @Test fun lifecycleNotificationsUseSdkEventsRatherThanSnapshotValues() {
        val navi = EcarxDimApi.NaviHandle(NaviWithStatus())
        assertEquals(1, EcarxDimApi.NAVIGATION_SNAPSHOT_ACTIVE)
        assertEquals(0, EcarxDimApi.NAVIGATION_SNAPSHOT_IDLE)
        assertTrue(EcarxDimApi.notifyNavigationStarted(navi).ok)
        assertTrue(EcarxDimApi.notifyNavigationEnded(navi).ok)
        assertEquals(listOf(2, 3), FixtureLog.calls().map { it.payload["status"] })

        FixtureLog.failOnce("notifyNavigationStatus")
        assertFalse(EcarxDimApi.notifyNavigationEnded(navi).ok)
        assertTrue(EcarxDimApi.clearLaneInfo(navi).ok)
        assertFalse(EcarxDimApi.notifyNavigationStarted(EcarxDimApi.NaviHandle(NaviFixture())).ok)
    }
}
