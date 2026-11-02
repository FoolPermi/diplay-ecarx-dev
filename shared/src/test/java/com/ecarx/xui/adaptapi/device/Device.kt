package com.ecarx.xui.adaptapi.device

import android.content.Context
import com.ecarx.xui.adaptapi.device.daynigntmode.IDayNightMode

/** Only the Device entry points used by day/night synchronization. */
class Device {
    fun getDayNightMode(): IDayNightMode? = mode

    companion object {
        var mode: IDayNightMode? = null
        @JvmStatic fun create(context: Context) = Device()
    }
}
