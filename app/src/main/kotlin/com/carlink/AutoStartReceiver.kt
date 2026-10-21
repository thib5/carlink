package com.carlink

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Opens the app when the car starts (boot / driver profile unlocked), so CarPlay is up without
 * tapping the icon.
 *
 * Android may refuse an activity start from the background. In that case a high-priority
 * notification is posted instead: its full-screen intent opens the app when the system allows
 * it, otherwise one tap on the notification does.
 */
class AutoStartReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_USER_UNLOCKED) return
        Log.i(TAG, "Auto-start on $action")

        val launch =
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        runCatching { context.startActivity(launch) }
            .onFailure { Log.w(TAG, "Direct start refused: ${it.javaClass.simpleName}") }

        // Always also post the notification: if the direct start was silently blocked, this is
        // the fallback; if it worked, MainActivity cancels it right away.
        runCatching { postLaunchNotification(context, launch) }
            .onFailure { Log.w(TAG, "Notification failed: ${it.javaClass.simpleName}") }
    }

    private fun postLaunchNotification(
        context: Context,
        launch: Intent,
    ) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Démarrage automatique", NotificationManager.IMPORTANCE_HIGH),
        )
        val pi =
            PendingIntent.getActivity(
                context,
                0,
                launch,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val n =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher_plain)
                .setContentTitle("CarPlay")
                .setContentText("Touche pour ouvrir CarPlay")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setContentIntent(pi)
                .setFullScreenIntent(pi, true)
                .setAutoCancel(true)
                .setTimeoutAfter(TIMEOUT_MS)
                .build()
        nm.notify(NOTIFICATION_ID, n)
    }

    companion object {
        private const val TAG = "CARLINK_AUTOSTART"
        private const val CHANNEL_ID = "carlink_autostart"
        const val NOTIFICATION_ID = 4711
        private const val TIMEOUT_MS = 2 * 60 * 1000L

        /** Called by MainActivity once it is visible: the prompt is no longer needed. */
        fun dismiss(context: Context) {
            runCatching { context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID) }
        }
    }
}
