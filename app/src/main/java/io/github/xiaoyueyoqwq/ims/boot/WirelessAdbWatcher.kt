package io.github.xiaoyueyoqwq.ims.boot

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import io.github.xiaoyueyoqwq.ims.PatchCoordinator
import io.github.xiaoyueyoqwq.ims.system.NotificationController
import io.github.xiaoyueyoqwq.ims.system.WirelessDebugging

object WirelessAdbWatcher {
    const val JOB_ID_WATCH = 2060
    const val JOB_ID_IMMEDIATE = 2061
    const val JOB_ID_STATUS = 2062

    private const val TAG = "WirelessAdbWatch"
    // JobScheduler periodic floor. Cheap: read three CarrierConfig keys, no FGS.
    private const val STATUS_PERIOD_MS = 15 * 60 * 1000L

    @Volatile
    private var observing = false

    fun start(context: Context) {
        val app = context.applicationContext
        schedule(app)
        scheduleStatus(app)
        observe(app)
    }

    fun schedule(context: Context) {
        val app = context.applicationContext
        val scheduler = app.getSystemService(JobScheduler::class.java) ?: return
        scheduler.cancel(JOB_ID_IMMEDIATE)
        val uri = Settings.Global.getUriFor(WirelessDebugging.ADB_WIFI_ENABLED)
        val watch = JobInfo.Builder(
            JOB_ID_WATCH,
            ComponentName(app, WirelessAdbJobService::class.java),
        )
            .addTriggerContentUri(JobInfo.TriggerContentUri(uri, 0))
            .setTriggerContentMaxDelay(1_000)
            .setPriority(JobInfo.PRIORITY_HIGH)
            .build()
        scheduler.schedule(watch)
    }

    fun scheduleStatus(context: Context) {
        val app = context.applicationContext
        val scheduler = app.getSystemService(JobScheduler::class.java) ?: return
        if (scheduler.getPendingJob(JOB_ID_STATUS) != null) return
        val status = JobInfo.Builder(
            JOB_ID_STATUS,
            ComponentName(app, WirelessAdbJobService::class.java),
        )
            .setPeriodic(STATUS_PERIOD_MS)
            .setPersisted(true)
            .setPriority(JobInfo.PRIORITY_DEFAULT)
            .build()
        scheduler.schedule(status)
        Log.i(TAG, "scheduled 5G+ status job every ${STATUS_PERIOD_MS}ms")
    }

    private fun observe(app: Context) {
        if (observing) return
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                onChange(selfChange, null)
            }

            override fun onChange(selfChange: Boolean, uri: Uri?) {
                val enabled = WirelessDebugging.isEnabled(app)
                Log.i(TAG, "adb_wifi_enabled=$enabled")
                if (!enabled) {
                    NotificationController(app).cancelPrompt()
                }
                PatchCoordinator.promptPairingIfNeeded(app)
            }
        }
        app.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(WirelessDebugging.ADB_WIFI_ENABLED),
            false,
            observer,
        )
        observing = true
        Log.i(TAG, "observing adb_wifi_enabled")
    }
}
