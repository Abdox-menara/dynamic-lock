package com.example.dynamiclock.locker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts protection after a reboot if the user had it enabled. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && LockManager.isEnabled(context)) {
            AppLockService.start(context)
        }
    }
}
