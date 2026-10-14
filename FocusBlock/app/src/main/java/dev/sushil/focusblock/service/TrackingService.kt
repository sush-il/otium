package dev.sushil.focusblock.service

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.sushil.focusblock.BuildConfig
import dev.sushil.focusblock.FocusBlockApp
import dev.sushil.focusblock.R
import dev.sushil.focusblock.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground service with a permanent notification. While the screen is on and
 * unlocked it polls the foreground app every 300 ms and reports it to [BlockEngine],
 * so blocking keeps working even if accessibility events are missed.
 */
class TrackingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var pollJob: Job? = null
    private lateinit var engine: BlockEngine
    private lateinit var poller: ForegroundPoller

    // Polling only makes sense while someone can see the screen
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> stopPolling()
                Intent.ACTION_USER_PRESENT -> startPolling()
                // No lock screen set up: there's no USER_PRESENT, so start on screen on
                Intent.ACTION_SCREEN_ON -> if (!isKeyguardLocked()) startPolling()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        engine = (application as FocusBlockApp).container.blockEngine
        poller = ForegroundPoller(getSystemService(UsageStatsManager::class.java))

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        if (getSystemService(PowerManager::class.java).isInteractive && !isKeyguardLocked()) startPolling()
        log("tracking service started")
    }

    // Restarted by the system with a null intent if it gets killed
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        unregisterReceiver(screenReceiver)
        scope.cancel()
        log("tracking service stopped")
        super.onDestroy()
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        log("polling on")
        pollJob = scope.launch {
            while (isActive) {
                val pkg = withContext(Dispatchers.Default) { poller.poll() }
                if (pkg != null) engine.onForeground(pkg)
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun stopPolling() {
        log("polling off")
        pollJob?.cancel()
        pollJob = null
    }

    private fun isKeyguardLocked() = getSystemService(KeyguardManager::class.java).isKeyguardLocked

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.channel_status), NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            },
        )
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.notification_active))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun log(msg: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, msg)
    }

    companion object {
        private const val TAG = "FocusBlock"
        private const val CHANNEL_ID = "status"
        private const val NOTIFICATION_ID = 1
        private const val POLL_INTERVAL_MS = 300L

        /** Safe to call often: starting a running service just calls onStartCommand again. */
        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, TrackingService::class.java))
            } catch (e: IllegalStateException) {
                // Android 12+ refuses to start foreground services from the background in some
                // cases (ForegroundServiceStartNotAllowedException). The next allowed start point
                // (opening the app, boot, accessibility connect) will try again.
                Log.w(TAG, "could not start tracking service", e)
            }
        }
    }
}
