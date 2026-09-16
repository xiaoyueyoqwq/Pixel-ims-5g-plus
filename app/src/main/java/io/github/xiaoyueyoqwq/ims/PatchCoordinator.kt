package io.github.xiaoyueyoqwq.ims

import android.content.Context
import android.util.Log
import io.github.xiaoyueyoqwq.ims.adb.AdbController
import io.github.xiaoyueyoqwq.ims.adb.AdbDiscovery
import io.github.xiaoyueyoqwq.ims.system.NotificationController
import io.github.xiaoyueyoqwq.ims.system.WirelessDebugging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PatchCoordinator {
    private const val TAG = "PatchCoordinator"

    suspend fun apply(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val adb = AdbController(app)
        val port = authorizedPort(app, adb)
            ?: return@withContext Result.failure(IllegalStateException("wireless ADB not authorized"))
        adb.grantPermissionsIfNeeded(port)
        adb.runApply(port).onFailure { Log.e(TAG, "apply failed", it) }
    }

    suspend fun pairAndApply(
        context: Context,
        code: String,
        portHint: Int = -1,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val adb = AdbController(app)
        val pairingPort = portHint.takeIf { it > 0 }
            ?: AdbDiscovery(app).awaitPairingPort()
            ?: return@withContext Result.failure(
                IllegalStateException("no pairing port; keep the system pairing dialog open"),
            )
        Log.i(TAG, "pairing on port $pairingPort")
        val paired = adb.pair(pairingPort, code.trim())
        if (paired.isSuccess) {
            return@withContext apply(app)
        }
        Log.w(TAG, "pair failed on port $pairingPort", paired.exceptionOrNull())
        paired
    }

    suspend fun isAuthorized(context: Context): Boolean = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        authorizedPort(app, AdbController(app)) != null
    }

    /**
     * Keep a silent watch FGS while 5G+ is not written, even if wireless
     * debugging is still off. Pairing RemoteInput is posted later, only when
     * NSD sees `_adb-tls-pairing._tcp`. An already-authorized connect port
     * posts Apply immediately. Do not wait for WirelessAdbJobService.
     */
    fun promptPairingIfNeeded(context: Context): Boolean {
        val app = context.applicationContext
        if (FiveGStatus.isEffective(app)) {
            Log.d(TAG, "5G+ already effective; staying silent")
            PromptService.stop(app)
            NotificationController(app).cancelPrompt()
            return false
        }
        val notifications = NotificationController(app)
        if (!notifications.canPost()) {
            Log.w(TAG, "POST_NOTIFICATIONS not granted; cannot prompt")
            return false
        }
        try {
            PromptService.start(app)
            Log.i(TAG, "started 5G+ watch (wireless=${WirelessDebugging.isEnabled(app)})")
        } catch (e: Exception) {
            Log.w(TAG, "watch FGS not allowed", e)
        }
        return true
    }

    private suspend fun authorizedPort(context: Context, adb: AdbController): Int? {
        val connect = AdbDiscovery(context).awaitConnectPort() ?: return null
        return connect.takeIf { adb.isAuthorized(it) }
    }

    fun notifyResult(context: Context, result: Result<Unit>) {
        val notifications = NotificationController(context)
        PromptService.stop(context)
        notifications.cancelWorking()
        notifications.cancelPrompt()
        result.fold(
            onSuccess = { notifications.showResult(true) },
            onFailure = { notifications.showResult(false, it.message) },
        )
    }
}
