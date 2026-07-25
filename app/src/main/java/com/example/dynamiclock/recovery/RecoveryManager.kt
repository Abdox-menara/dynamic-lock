package com.example.dynamiclock.recovery

import android.content.Context
import com.example.dynamiclock.security.Crypto
import java.security.SecureRandom

/**
 * v5: Manages PIN recovery — generates, stores, and validates recovery codes.
 * Recovery code is a 12-character alphanumeric string (base36, ~62 bits entropy).
 */
object RecoveryManager {

    private const val PREFS = "recovery"
    private const val KEY_CODE = "code"
    private const val KEY_USED = "used"

    private fun prefs(ctx: Context) = Crypto.prefs(ctx, PREFS)

    /** Generate a new recovery code and store it encrypted. Returns the code. */
    fun generate(ctx: Context): String {
        val random = SecureRandom()
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // no I,O,0,1 — avoids ambiguity
        val code = (1..12).map { chars[random.nextInt(chars.length)] }.joinToString("")
        prefs(ctx).edit().putString(KEY_CODE, code).putBoolean(KEY_USED, false).apply()
        return code
    }

    /** Check if a recovery code exists. */
    fun hasCode(ctx: Context): Boolean = prefs(ctx).contains(KEY_CODE)

    /** Validate a recovery code (constant-time comparison). */
    fun validate(ctx: Context, input: String): Boolean {
        val stored = prefs(ctx).getString(KEY_CODE, null) ?: return false
        if (prefs(ctx).getBoolean(KEY_USED, false)) return false // one-time use
        return constantTimeEquals(stored.uppercase(), input.uppercase())
    }

    /** Mark the recovery code as used (invalidate it). */
    fun markUsed(ctx: Context) {
        prefs(ctx).edit().putBoolean(KEY_USED, true).apply()
    }

    /** Get the masked recovery code (show last 4 chars). */
    fun maskedCode(ctx: Context): String? {
        val code = prefs(ctx).getString(KEY_CODE, null) ?: return null
        return "••••••••" + code.takeLast(4)
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) result = result or (a[i].code xor b[i].code)
        return result == 0
    }
}
