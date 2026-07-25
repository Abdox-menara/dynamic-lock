package com.example.dynamiclock.locker

import android.content.Context
import com.example.dynamiclock.security.Crypto
import java.util.Calendar

/**
 * v5: Time-based lock scheduling. Each app can have a schedule with start/end hour.
 * If the schedule is active and current time is outside the window, the app is
 * automatically locked regardless of user state.
 */
data class AppSchedule(
    val packageName: String,
    val enabled: Boolean = false,
    val startHour: Int = 9,   // 0-23
    val endHour: Int = 17,    // 0-23, end > start means within range, end < start means overnight
    val daysOfWeek: Int = 0b1111111 // bitmask: Mon=1<<0, Tue=1<<1, ..., Sun=1<<6
)

object LockScheduler {

    private const val PREFS = "lock_schedule"
    private const val KEY_PREFIX = "schedule_"

    private fun prefs(ctx: Context) = Crypto.prefs(ctx, PREFS)

    fun save(ctx: Context, schedule: AppSchedule) {
        val json = buildString {
            append("${schedule.enabled}|${schedule.startHour}|${schedule.endHour}|${schedule.daysOfWeek}")
        }
        prefs(ctx).edit().putString(KEY_PREFIX + schedule.packageName, json).apply()
    }

    fun load(ctx: Context, packageName: String): AppSchedule {
        val raw = prefs(ctx).getString(KEY_PREFIX + packageName, null) ?: return AppSchedule(packageName)
        val parts = raw.split("|")
        if (parts.size < 4) return AppSchedule(packageName)
        return AppSchedule(
            packageName = packageName,
            enabled = parts[0].toBooleanStrictOrNull() ?: false,
            startHour = parts[1].toIntOrNull() ?: 9,
            endHour = parts[2].toIntOrNull() ?: 17,
            daysOfWeek = parts[3].toIntOrNull() ?: 0b1111111
        )
    }

    fun delete(ctx: Context, packageName: String) {
        prefs(ctx).edit().remove(KEY_PREFIX + packageName).apply()
    }

    /** Check if a locked app should currently be unlocked based on schedule. */
    fun shouldAutoUnlock(ctx: Context, packageName: String): Boolean {
        val schedule = load(ctx, packageName)
        if (!schedule.enabled) return false
        val cal = Calendar.getInstance()
        val currentHour = cal.get(Calendar.HOUR_OF_DAY)
        val currentDay = cal.get(Calendar.DAY_OF_WEEK) - 2 // Monday=0, Sunday=6

        // Check day of week
        val dayBit = 1 shl currentDay
        if (schedule.daysOfWeek and dayBit == 0) return false

        // Check time range (supports overnight ranges like 22-6)
        return if (schedule.endHour > schedule.startHour) {
            currentHour in schedule.startHour until schedule.endHour
        } else {
            // Overnight: e.g., 22:00 - 06:00
            currentHour >= schedule.startHour || currentHour < schedule.endHour
        }
    }
}
