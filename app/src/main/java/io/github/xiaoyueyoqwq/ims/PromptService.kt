package io.github.xiaoyueyoqwq.ims

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import io.github.xiaoyueyoqwq.ims.adb.AdbController
import io.github.xiaoyueyoqwq.ims.adb.AdbDiscovery
import io.github.xiaoyueyoqwq.ims.adb.AdbEndpoint
import io.github.xiaoyueyoqwq.ims.adb.AdbPortStore
import io.github.xiaoyueyoqwq.ims.system.NotificationController
import io.github.xiaoyueyoqwq.ims.system.WirelessDebugging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Silent FGS while 5G+ is not written. Keeps NSD alive even before wireless
 * debugging is turned on, so pairing mDNS is not missed. Dismissing the
 * HIGH pairing/apply prompt must not stop this service.
 */
class PromptService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var watch: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notifications = NotificationController(this)
        val watchNotification = notifications.buildWatchNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NotificationController.NOTIFICATION_FGS,
                watchNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NotificationController.NOTIFICATION_FGS, watchNotification)
        }
        if (!shouldWatch()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            notifications.cancelPrompt()
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (watch?.isActive != true) {
            watch = scope.launch { watchDiscovery() }
        }
        if (WirelessDebugging.isEnabled(this)) {
            scope.launch { probeLastAuthorizedPort() }
        }
        return START_STICKY
    }

    private fun shouldWatch(): Boolean = !FiveGStatus.isEffective(this)

    private suspend fun probeLastAuthorizedPort() {
        if (!shouldWatch() || !WirelessDebugging.isEnabled(this)) return
        val port = AdbPortStore.lastConnectPort(this)
        if (port <= 0) {
            Log.i(TAG, "no saved connect port")
            return
        }
        val authorized = AdbController(this).isAuthorized(port)
        Log.i(TAG, "saved connect port $port authorized=$authorized")
        if (authorized && shouldWatch()) {
            NotificationController(this).showApplyPrompt()
        }
    }

    private suspend fun watchDiscovery() {
        val notifications = NotificationController(this)
        Log.i(TAG, "watching pairing/connect mDNS")
        try {
            AdbDiscovery(this).discover().collect { endpoint ->
                if (!shouldWatch()) {
                    Log.i(TAG, "watch no longer needed")
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    notifications.cancelPrompt()
                    stopSelf()
                    return@collect
                }
                when (endpoint) {
                    is AdbEndpoint.Pairing -> {
                        Log.i(TAG, "pairing port ${endpoint.port}")
                        notifications.showPairingRemoteInput(endpoint.port)
                    }
                    is AdbEndpoint.Connect -> {
                        if (!WirelessDebugging.isEnabled(this)) return@collect
                        scope.launch {
                            val authorized = AdbController(this@PromptService)
                                .isAuthorized(endpoint.port)
                            Log.i(TAG, "connect port ${endpoint.port} authorized=$authorized")
                            if (authorized && shouldWatch()) {
                                notifications.showApplyPrompt()
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "NSD watch failed", e)
        }
    }

    override fun onDestroy() {
        watch?.cancel()
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PromptService"

        fun start(context: Context) {
            val app = context.applicationContext
            ContextCompat.startForegroundService(app, Intent(app, PromptService::class.java))
        }

        fun stop(context: Context) {
            val app = context.applicationContext
            try {
                app.stopService(Intent(app, PromptService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "stop PromptService failed", e)
            }
        }
    }
}
