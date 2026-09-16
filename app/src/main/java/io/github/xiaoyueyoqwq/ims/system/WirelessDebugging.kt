package io.github.xiaoyueyoqwq.ims.system

import android.provider.Settings
import android.content.Context

object WirelessDebugging {
    const val ADB_WIFI_ENABLED = "adb_wifi_enabled"

    fun isEnabled(context: Context): Boolean =
        Settings.Global.getInt(context.contentResolver, ADB_WIFI_ENABLED, 0) == 1
}
