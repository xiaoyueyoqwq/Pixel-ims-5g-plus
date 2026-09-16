package io.github.xiaoyueyoqwq.ims.system

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import io.github.xiaoyueyoqwq.ims.BuildConfig
import io.github.xiaoyueyoqwq.ims.PatchPromptReceiver
import io.github.xiaoyueyoqwq.ims.R

class NotificationController(context: Context) {

    private val appContext = context.applicationContext
    private val notificationManager =
        appContext.getSystemService(NotificationManager::class.java)

    fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun ensureChannels() {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PROMPT,
                appContext.getString(R.string.notif_channel_prompt_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = appContext.getString(R.string.notif_channel_prompt_desc) },
        )
        // ims_status was IMPORTANCE_LOW; Android will not raise an existing
        // channel, so the success notice stayed silent. Drop it and use a
        // DEFAULT channel so the result is a normal notification.
        notificationManager.deleteNotificationChannel("ims_status")
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_STATUS,
                appContext.getString(R.string.notif_channel_status_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = appContext.getString(R.string.notif_channel_status_desc) },
        )
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_FGS,
                appContext.getString(R.string.notif_channel_fgs_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = appContext.getString(R.string.notif_channel_fgs_desc) },
        )
    }

    fun showApplyPrompt() {
        if (!canPost()) return
        ensureChannels()
        val yes = action(PatchPromptReceiver.ACTION_APPLY, R.string.notif_apply_yes)
        val no = action(PatchPromptReceiver.ACTION_DISMISS, R.string.notif_apply_no)
        val notification = NotificationCompat.Builder(appContext, CHANNEL_PROMPT)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(appContext.getString(R.string.notif_apply_title))
            .setContentText(appContext.getString(R.string.notif_apply_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(yes)
            .addAction(no)
            .build()
        notificationManager.notify(NOTIFICATION_PROMPT, notification)
    }

    fun buildWatchNotification(): Notification {
        ensureChannels()
        return NotificationCompat.Builder(appContext, CHANNEL_FGS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(appContext.getString(R.string.notif_watch_title))
            .setContentText(appContext.getString(R.string.notif_watch_text))
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun buildWorkingNotification(detail: String? = null): Notification {
        ensureChannels()
        val text = detail ?: appContext.getString(R.string.prompt_working)
        return NotificationCompat.Builder(appContext, CHANNEL_FGS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(appContext.getString(R.string.notif_working_title))
            .setContentText(text)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun showWorking(detail: String? = null) {
        notificationManager.notify(NOTIFICATION_FGS, buildWorkingNotification(detail))
    }

    fun cancelWorking() {
        notificationManager.cancel(NOTIFICATION_FGS)
    }

    fun buildPairingRemoteInput(pairingPort: Int = -1): Notification {
        ensureChannels()
        val remoteInput = RemoteInput.Builder(PatchPromptReceiver.KEY_PAIRING_CODE)
            .setLabel(appContext.getString(R.string.notif_pairing_reply_label))
            .build()
        val intent = Intent(PatchPromptReceiver.ACTION_PAIR)
            .setPackage(appContext.packageName)
            .putExtra(PatchPromptReceiver.KEY_PAIRING_PORT, pairingPort)
        val pending = PendingIntent.getBroadcast(
            appContext,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val action = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            appContext.getString(R.string.notif_pairing_action),
            pending,
        ).addRemoteInput(remoteInput).build()
        return NotificationCompat.Builder(appContext, CHANNEL_PROMPT)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(appContext.getString(R.string.notif_pairing_title))
            .setContentText(appContext.getString(R.string.notif_pairing_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setOngoing(true)
            .addAction(action)
            .build()
    }

    fun showPairingRemoteInput(pairingPort: Int = -1) {
        if (!canPost()) return
        notificationManager.notify(NOTIFICATION_PROMPT, buildPairingRemoteInput(pairingPort))
    }

    fun showResult(success: Boolean, detail: String? = null) {
        if (!canPost()) return
        ensureChannels()
        val title = appContext.getString(
            if (success) R.string.notif_result_ok_title else R.string.notif_result_fail_title,
        )
        val text = detail ?: appContext.getString(
            when {
                success && BuildConfig.DEBUG -> R.string.notif_result_ok_text_debug
                success -> R.string.notif_result_ok_text
                else -> R.string.notif_result_fail_text
            },
        )
        val notification = NotificationCompat.Builder(appContext, CHANNEL_STATUS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIFICATION_STATUS, notification)
    }

    fun cancelAll() {
        cancelPrompt()
        cancelWorking()
        notificationManager.cancelAll()
    }

    fun cancelPrompt() {
        // Direct-reply spinner wants an update, but a blank HIGH notify()
        // is what Pixel keeps. Cancel 302 only; the watch FGS is 301.
        notificationManager.cancel(NOTIFICATION_PROMPT)
    }

    /**
     * Replace the RemoteInput notification so Pixel drops
     * LIFETIME_EXTENDED_BY_DIRECT_REPLY, then cancel it.
     */
    fun consumeDirectReply() {
        ensureChannels()
        val closing = NotificationCompat.Builder(appContext, CHANNEL_FGS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(appContext.getString(R.string.notif_working_title))
            .setContentText(appContext.getString(R.string.prompt_working))
            .setSilent(true)
            .setOngoing(false)
            .setTimeoutAfter(1)
            .build()
        notificationManager.notify(NOTIFICATION_PROMPT, closing)
        notificationManager.cancel(NOTIFICATION_PROMPT)
    }

    private fun action(action: String, labelRes: Int): NotificationCompat.Action {
        val intent = Intent(action).setPackage(appContext.packageName)
        val pending = PendingIntent.getBroadcast(
            appContext,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            appContext.getString(labelRes),
            pending,
        ).build()
    }

    companion object {
        const val CHANNEL_FGS = "ims_fgs"
        const val NOTIFICATION_FGS = 301
        const val NOTIFICATION_PROMPT = 302

        private const val CHANNEL_PROMPT = "ims_prompt"
        private const val CHANNEL_STATUS = "ims_result"
        private const val NOTIFICATION_STATUS = 303
    }
}
