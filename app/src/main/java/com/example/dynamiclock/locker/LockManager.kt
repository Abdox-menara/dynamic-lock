package com.example.dynamiclock.locker

import android.content.Context
import com.example.dynamiclock.security.Crypto

/**
 * Persists which apps are locked and whether protection is enabled, and tracks the
 * single app the user has currently unlocked (in memory only).
 */
object LockManager {
    private const val PREFS = "locker"
    private const val KEY_APPS = "apps"
    private const val KEY_ENABLED = "enabled"

    @Volatile private var currentUnlocked: String? = null

    private fun prefs(ctx: Context) = Crypto.prefs(ctx, PREFS)

    fun lockedApps(ctx: Context): MutableSet<String> =
        HashSet(prefs(ctx).getStringSet(KEY_APPS, emptySet()) ?: emptySet())

    fun isLocked(ctx: Context, pkg: String) = lockedApps(ctx).contains(pkg)

    fun setLocked(ctx: Context, pkg: String, locked: Boolean) {
        val set = lockedApps(ctx)
        if (locked) set.add(pkg) else set.remove(pkg)
        prefs(ctx).edit().putStringSet(KEY_APPS, set).apply()
    }

    fun isEnabled(ctx: Context) = prefs(ctx).getBoolean(KEY_ENABLED, false)
    fun setEnabled(ctx: Context, value: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_ENABLED, value).apply()

    /** Called after a successful unlock so the app isn't asked again while it stays open. */
    fun markUnlocked(pkg: String) { currentUnlocked = pkg }
    fun isUnlocked(pkg: String) = currentUnlocked == pkg
    /** Called when the user leaves into a non-locked app, re-arming every lock. */
    fun clearAll() { currentUnlocked = null }
}
