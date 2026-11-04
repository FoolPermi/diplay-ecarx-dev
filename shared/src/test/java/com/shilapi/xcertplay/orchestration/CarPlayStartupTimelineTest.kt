package com.shilapi.xcertplay.orchestration

import android.content.Context
import com.shilapi.xcertplay.orchestration.CarPlayStartupTimeline.Stage
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class CarPlayStartupTimelineTest {
    private val app get() = RuntimeEnvironment.getApplication()

    @Before fun reset() {
        app.getSharedPreferences("diplay_wireless_startup_timeline", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test fun onlyCarHotspotAndExistingWifiProduceBoundedSafeTimeline() {
        val modes = listOf(WirelessHotspotMode.MANUAL, WirelessHotspotMode.EXISTING_WIFI)
        for (i in 0 until 90) CarPlayStartupTimeline.record(app, Stage.NETWORK_BACKEND_READY,
            modes[i % modes.size], generation = i, owner = 21, attempt = 1)
        CarPlayStartupTimeline.record(app, Stage.VIDEO_FIRST_FRAME, WirelessHotspotMode.WIFI_P2P)
        val lines = CarPlayStartupTimeline.report(app).lineSequence().filter { it.startsWith("elapsedSinceBootMs=") }.toList()
        assertEquals(80, lines.size)
        assertTrue(lines.first().contains("generation=10"))
        assertTrue(lines.last().contains("generation=89"))
        assertTrue(lines.none { it.contains("WIFI_P2P") })
        assertTrue(lines.all { it.contains("owner=21") && it.contains("attempt=1") })
    }

    @Test fun formattingUsesOneClockAndOnlyEnumAndNumericFields() {
        val line = CarPlayStartupTimeline.format(1234, Stage.START_SESSION_SENT,
            WirelessHotspotMode.MANUAL, generation = 7, owner = 31, attempt = 2)
        assertEquals("elapsedSinceBootMs=1234 stage=START_SESSION_SENT mode=MANUAL generation=7 owner=31 attempt=2", line)
        assertFalse(line.contains("ssid", ignoreCase = true))
        assertFalse(line.contains("token", ignoreCase = true))
        assertTrue(CarPlayStartupTimeline.report(app).contains("No hotspot/same-LAN startup samples"))
    }
}
