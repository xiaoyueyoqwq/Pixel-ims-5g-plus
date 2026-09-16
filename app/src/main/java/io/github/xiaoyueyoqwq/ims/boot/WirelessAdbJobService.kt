package io.github.xiaoyueyoqwq.ims.boot

import android.app.job.JobParameters
import android.app.job.JobService
import android.util.Log
import io.github.xiaoyueyoqwq.ims.PatchCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WirelessAdbJobService : JobService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var running: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        running = scope.launch {
            try {
                PatchCoordinator.promptPairingIfNeeded(this@WirelessAdbJobService)
            } catch (e: Exception) {
                Log.e(TAG, "watch failed", e)
            } finally {
                WirelessAdbWatcher.schedule(this@WirelessAdbJobService)
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running?.cancel()
        WirelessAdbWatcher.schedule(this)
        return false
    }

    companion object {
        private const val TAG = "WirelessAdbJob"
    }
}
