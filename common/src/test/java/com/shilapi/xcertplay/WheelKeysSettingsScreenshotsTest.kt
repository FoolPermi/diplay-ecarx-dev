package com.shilapi.xcertplay

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.shilapi.xcertplay.host.R
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The steering-wheel card on the current 1920×1080 head unit: the one switch DiPlay needs (Siri on the
 * voice key) and none of the key learning it does not.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], qualifiers = "zh-rCN-w1920dp-h1080dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WheelKeysSettingsScreenshotsTest {
    @Test fun cardOffersTheSiriKeyAndNoLearningList() {
        val activity = Robolectric.buildActivity(DiPlayActivity::class.java).get()
        activity.setTheme(android.R.style.Theme_Material_NoActionBar)
        // Identify the head unit as ECARX, so the voice-key takeover row is part of the screenshot.
        org.robolectric.Shadows.shadowOf(activity.packageManager).installPackage(
            android.content.pm.PackageInfo().apply { packageName = "com.ecarx.xui.service" })
        WheelZoomSettings.setSiriKey(activity, true)
        try {
            val row = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            DiPlayActivity::class.java.getDeclaredMethod("wheelKeysSettings", LinearLayout::class.java)
                .apply { isAccessible = true }.invoke(activity, row)
            val texts = allViews(row).filterIsInstance<TextView>()
                .map { it.contentDescription?.toString() ?: it.text.toString() }
                .toList()
            assertTrue(texts.contains(activity.getString(R.string.wheel_siri_key)))
            assertTrue(texts.any { it.startsWith(activity.getString(R.string.settings_voice_key_press)) })
            assertFalse(texts.contains(activity.getString(R.string.wheel_media_play_pause)))
            assertFalse(texts.any { it.startsWith(activity.getString(R.string.settings_custom_wheel_keys)) })
            val width = 1920
            row.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY))
            row.layout(0, 0, width, row.measuredHeight)
            val bitmap = Bitmap.createBitmap(width, row.measuredHeight, Bitmap.Config.ARGB_8888)
            try {
                row.draw(Canvas(bitmap))
                val directory = File("build/reports/wheel-keys-ui").apply { mkdirs() }
                FileOutputStream(File(directory, "current-head-unit.png")).use { output ->
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                }
            } finally {
                bitmap.recycle()
            }
        } finally {
            activity.finish()
        }
    }

    private fun allViews(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(allViews(view.getChildAt(i)))
    }
}
