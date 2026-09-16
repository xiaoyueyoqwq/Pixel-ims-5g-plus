package io.github.xiaoyueyoqwq.ims

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import io.github.xiaoyueyoqwq.ims.system.NotificationController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Runs pair/apply off the notification broadcast. Background receivers are killed
 * after ~10s; loopback port scan plus kadb pairing exceeds that.
 */
class PatchApplyService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var work: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        PromptService.stop(this)
        val notifications = NotificationController(this)
        val working = notifications.buildWorkingNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NotificationController.NOTIFICATION_FGS,
                working,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NotificationController.NOTIFICATION_FGS, working)
        }

        val op = intent?.getStringExtra(EXTRA_OP)
        val code = intent?.getStringExtra(EXTRA_CODE).orEmpty()
        val portHint = intent?.getIntExtra(EXTRA_PORT, -1) ?: -1
        val token = startId
        work?.cancel()
        work = scope.launch {
            val result = runCatching {
                when (op) {
                    OP_PAIR -> PatchCoordinator.pairAndApply(
                        this@PatchApplyService,
                        code,
                        portHint,
                    ).getOrThrow()
                    OP_APPLY -> PatchCoordinator.apply(this@PatchApplyService).getOrThrow()
                    else -> error("unknown op")
                }
            }
            try {
                stopForeground(STOP_FOREGROUND_REMOVE)
                notifications.cancelWorking()
                PatchCoordinator.notifyResult(this@PatchApplyService, result)
            } catch (e: Exception) {
                Log.e(TAG, "notifyResult failed", e)
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(token)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        work?.cancel()
        scope.cancel()
        super.onDestroy()
        // 301 is free after this service stops. Resume NSD watch if the
        // patch is still missing (pair failed, user dismissed, etc.).
        PatchCoordinator.promptPairingIfNeeded(this)
    }

    companion object {
        private const val TAG = "PatchApplyService"
        const val EXTRA_OP = "extra_op"
        const val EXTRA_CODE = "extra_code"
        const val EXTRA_PORT = "extra_port"
        const val OP_PAIR = "pair"
        const val OP_APPLY = "apply"

        fun startPair(context: Context, code: String, port: Int = -1) {
            start(context, OP_PAIR, code, port)
        }

        fun startApply(context: Context) {
            start(context, OP_APPLY, null, -1)
        }

        private fun start(context: Context, op: String, code: String?, port: Int) {
            val intent = Intent(context, PatchApplyService::class.java)
                .putExtra(EXTRA_OP, op)
                .putExtra(EXTRA_PORT, port)
            if (code != null) {
                intent.putExtra(EXTRA_CODE, code)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
