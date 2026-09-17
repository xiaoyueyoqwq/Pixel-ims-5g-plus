package io.github.xiaoyueyoqwq.ims

import android.app.Application
import android.telephony.CarrierConfigManager
import android.util.Log
import io.github.xiaoyueyoqwq.ims.adb.AdbIdentity
import io.github.xiaoyueyoqwq.ims.boot.WirelessAdbWatcher
import io.github.xiaoyueyoqwq.ims.system.NotificationController

class ImsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AdbIdentity.ensure(this)
        NotificationController(this).ensureChannels()
        WirelessAdbWatcher.start(this)
        registerCarrierConfigWatch()
        PatchCoordinator.promptPairingIfNeeded(this)
    }

    private fun registerCarrierConfigWatch() {
        val cm = getSystemService(CarrierConfigManager::class.java) ?: return
        cm.registerCarrierConfigChangeListener(mainExecutor) { _, _, _, _ ->
            Log.i(TAG, "carrier config changed")
            PatchCoordinator.promptPairingIfNeeded(this)
        }
    }

    companion object {
        private const val TAG = "ImsApp"
    }
}
