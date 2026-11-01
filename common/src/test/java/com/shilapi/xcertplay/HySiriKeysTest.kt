package com.shilapi.xcertplay

import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class HySiriKeysTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val keys = HySiriKeys()
    private var requests = 0

    @Before fun reset() {
        app.getSharedPreferences("diplay_media_session", 0).edit().clear().commit()
    }

    @Test fun eitherCodeInTheSelectedPairTriggersOnceWithoutLearningDeviceOrScanCodes() {
        for (pair in HySiriKey.entries) {
            MediaSessionSettings.setHySiriKey(app, pair)
            for (code in pair.codes) {
                assertEquals(true, handle(code))
                assertEquals(true, handle(code, repeat = 1))
                assertEquals(true, handle(code, action = KeyEvent.ACTION_UP))
            }
        }
        assertEquals(4, requests)
    }

    @Test fun unselectedKeysAndMediaKeysDoNotTriggerSiri() {
        assertEquals(false, handle(312))
        assertNull(handle(85))
        assertNull(handle(87))
        assertNull(handle(88))
        assertNull(handle(200231))
        assertEquals(0, requests)
    }

    @Test fun disabledOrInCallPassesTheKeyAndConsumedReleaseSurvivesSwitchOff() {
        assertNull(handle(156, enabled = false))
        assertEquals(false, handle(156, calling = true))
        handle(156, action = KeyEvent.ACTION_UP)
        assertEquals(0, requests)
        assertEquals(true, handle(155))
        assertEquals(true, handle(155, repeat = 1, enabled = false))
        assertEquals(true, handle(155, action = KeyEvent.ACTION_UP, enabled = false))
        assertEquals(1, requests)
    }

    @Test fun changingTheSelectionTakesEffectOnTheNextDistinctPress() {
        assertEquals(true, handle(155))
        handle(155, action = KeyEvent.ACTION_UP)
        MediaSessionSettings.setHySiriKey(app, HySiriKey.TOGGLE_312_303)
        assertEquals(false, handle(156))
        assertEquals(true, handle(303))
        assertEquals(2, requests)
    }

    private fun handle(code: Int, action: Int = KeyEvent.ACTION_DOWN, repeat: Int = 0,
        enabled: Boolean = true, calling: Boolean = false): Boolean? =
        keys.handle(app, KeyEvent(1, 2, action, code, repeat, 0, -1, 99), enabled, calling) {
            requests++
            true
        }
}
