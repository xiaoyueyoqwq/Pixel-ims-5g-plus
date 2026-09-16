package io.github.xiaoyueyoqwq.ims.adb

import android.content.Context

/**
 * Last wireless-debugging connect port that accepted this app's kadb
 * identity. Used to prompt Apply on boot without waiting for mDNS.
 */
object AdbPortStore {
    private const val PREFS = "adb_ports"
    private const val KEY_CONNECT = "last_connect_port"

    fun lastConnectPort(context: Context): Int =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_CONNECT, -1)

    fun saveConnectPort(context: Context, port: Int) {
        if (port <= 0) return
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_CONNECT, port)
            .apply()
    }
}
