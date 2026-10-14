package com.focusblock.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.focusblock.R
import com.focusblock.util.Permissions
import java.time.LocalDate

/** "5 minutes left on Instagram" notifications, at most once per app, threshold and day. */
class LowTimeAlerts(private val context: Context) {

    private val sent = mutableSetOf<String>()

    init {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_HIGH),
        )
    }

    /** Thresholds still ahead of us, soonest last, e.g. [10, 5, 1] with 12 min left. */
    fun pendingThresholds(pkg: String, remainingMs: Long): List<Int> =
        THRESHOLDS_MIN.filter { remainingMs > it * 60_000L && key(pkg, it) !in sent }

    /** Marks a threshold as handled; returns false if it was already handled. */
    fun markIfNew(pkg: String, minutes: Int): Boolean = sent.add(key(pkg, minutes))

    @SuppressLint("MissingPermission") // checked via Permissions.hasNotificationPermission
    fun send(pkg: String, appLabel: String, minutes: Int) {
        if (!Permissions.hasNotificationPermission(context)) return
        val text = if (minutes == 1) "1 minute left on $appLabel" else "$minutes minutes left on $appLabel"
        NotificationManagerCompat.from(context).notify(
            ALERT_NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(text)
                .setAutoCancel(true)
                .setTimeoutAfter(60_000)
                .build(),
        )
    }

    private fun key(pkg: String, minutes: Int) = "$pkg|${LocalDate.now()}|$minutes"

    private companion object {
        const val CHANNEL_ID = "alerts"
        const val ALERT_NOTIFICATION_ID = 2
        val THRESHOLDS_MIN = listOf(10, 5, 1)
    }
}
