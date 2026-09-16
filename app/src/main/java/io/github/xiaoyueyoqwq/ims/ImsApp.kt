package io.github.xiaoyueyoqwq.ims

import android.app.Application
import io.github.xiaoyueyoqwq.ims.adb.AdbIdentity
import io.github.xiaoyueyoqwq.ims.boot.WirelessAdbWatcher
import io.github.xiaoyueyoqwq.ims.system.NotificationController

class ImsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AdbIdentity.ensure(this)
        NotificationController(this).ensureChannels()
        WirelessAdbWatcher.start(this)
        PatchCoordinator.promptPairingIfNeeded(this)
    }
}
