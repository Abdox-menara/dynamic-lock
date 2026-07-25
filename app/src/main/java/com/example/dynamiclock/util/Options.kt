package com.example.dynamiclock.util

import android.content.Context
import com.example.dynamiclock.security.Crypto

/** v2 user options, stored encrypted. */
class Options(context: Context) {
    private val p = Crypto.prefs(context, "options")

    var scrambleKeypad: Boolean
        get() = p.getBoolean("scramble", false)
        set(v) { p.edit().putBoolean("scramble", v).apply() }

    var biometricEnabled: Boolean
        get() = p.getBoolean("biometric", false)
        set(v) { p.edit().putBoolean("biometric", v).apply() }

    /** Auto-lock the vault after this many seconds in background (0 = immediate). */
    var vaultAutoLockSeconds: Int
        get() = p.getInt("vault_autolock", 30)
        set(v) { p.edit().putInt("vault_autolock", v).apply() }
}
