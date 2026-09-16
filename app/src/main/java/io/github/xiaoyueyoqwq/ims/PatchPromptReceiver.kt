package io.github.xiaoyueyoqwq.ims

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.RemoteInput
import io.github.xiaoyueyoqwq.ims.system.NotificationController

class PatchPromptReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val notifications = NotificationController(context)
        when (intent.action) {
            ACTION_DISMISS -> {
                notifications.cancelPrompt()
            }
            ACTION_APPLY -> {
                notifications.consumeDirectReply()
                PromptService.stop(context)
                startWork(context) { PatchApplyService.startApply(context) }
            }
            ACTION_PAIR -> {
                val code = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(KEY_PAIRING_CODE)
                    ?.toString()
                    .orEmpty()
                if (code.isBlank()) {
                    notifications.showResult(
                        false,
                        context.getString(R.string.prompt_need_code),
                    )
                    return
                }
                val port = intent.getIntExtra(KEY_PAIRING_PORT, -1)
                notifications.consumeDirectReply()
                PromptService.stop(context)
                startWork(context) { PatchApplyService.startPair(context, code, port) }
            }
        }
    }

    private fun startWork(
        context: Context,
        start: () -> Unit,
    ) {
        try {
            start()
        } catch (e: Exception) {
            Log.e(TAG, "failed to start PatchApplyService", e)
            PatchCoordinator.notifyResult(context, Result.failure(e))
        }
    }

    companion object {
        private const val TAG = "PatchPromptReceiver"
        const val ACTION_APPLY = "io.github.xiaoyueyoqwq.ims.ACTION_APPLY"
        const val ACTION_DISMISS = "io.github.xiaoyueyoqwq.ims.ACTION_DISMISS"
        const val ACTION_PAIR = "io.github.xiaoyueyoqwq.ims.ACTION_PAIR"
        const val KEY_PAIRING_CODE = "extra_pairing_code"
        const val KEY_PAIRING_PORT = "extra_pairing_port"
    }
}
