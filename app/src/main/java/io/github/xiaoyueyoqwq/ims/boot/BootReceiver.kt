package io.github.xiaoyueyoqwq.ims.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.github.xiaoyueyoqwq.ims.PatchCoordinator

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            intent.action != Intent.ACTION_USER_UNLOCKED
        ) {
            return
        }
        val app = context.applicationContext
        val pending = goAsync()
        Log.i(TAG, "boot action=${intent.action}")
        try {
            WirelessAdbWatcher.start(app)
            PatchCoordinator.promptPairingIfNeeded(app)
        } catch (e: Exception) {
            Log.e(TAG, "boot watch failed", e)
        }
        // Keep the process alive until PromptService.startForeground runs.
        Handler(Looper.getMainLooper()).postDelayed({ pending.finish() }, KEEP_ALIVE_MS)
    }

    companion object {
        private const val TAG = "BootReceiver"
        private const val KEEP_ALIVE_MS = 2_500L
    }
}
