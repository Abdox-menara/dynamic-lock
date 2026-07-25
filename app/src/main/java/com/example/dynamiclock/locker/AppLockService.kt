package com.example.dynamiclock.locker

import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.dynamiclock.App
import com.example.dynamiclock.R
import com.example.dynamiclock.ui.MainActivity
import com.example.dynamiclock.ui.UnlockActivity

/**
 * Foreground service that samples the current foreground app and, when a locked app is
 * opened, shows the dynamic-PIN unlock screen over it.
 */
class AppLockService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastForeground: String? = null

    // Re-arm every lock when the screen turns off.
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            LockManager.clearAll()
            lastForeground = null
        }
    }

    private val poll = object : Runnable {
        override fun run() {
            check()
            handler.postDelayed(this, INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification())
        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handler.removeCallbacks(poll)
        handler.post(poll)
        return START_STICKY
    }

    private fun check() {
        val me = packageName
        val fg = currentForeground() ?: return
        if (fg == me) return // ignore our own unlock screen

        if (fg != lastForeground) {
            when {
                !LockManager.isLocked(this, fg) -> LockManager.clearAll()
                !LockManager.isUnlocked(fg) -> startActivity(UnlockActivity.forApp(this, fg))
            }
            lastForeground = fg
        }
    }

    private fun currentForeground(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 10_000, now)
        val event = UsageEvents.Event()
        var pkg: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) pkg = event.packageName
        }
        return pkg
    }

    private fun buildNotification() = NotificationCompat.Builder(this, App.CHANNEL_ID)
        .setContentTitle(getString(R.string.app_name))
        .setContentText(getString(R.string.service_running))
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setOngoing(true)
        .setContentIntent(
            PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()

    override fun onDestroy() {
        handler.removeCallbacks(poll)
        runCatching { unregisterReceiver(screenOffReceiver) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIF_ID = 42
        private const val INTERVAL_MS = 700L

        fun start(ctx: Context) {
            LockManager.setEnabled(ctx, true)
            ContextCompat.startForegroundService(ctx, Intent(ctx, AppLockService::class.java))
        }

        fun stop(ctx: Context) {
            LockManager.setEnabled(ctx, false)
            ctx.stopService(Intent(ctx, AppLockService::class.java))
        }
    }
}
