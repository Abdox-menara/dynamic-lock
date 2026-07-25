package com.example.dynamiclock.util

import android.content.Context
import com.example.dynamiclock.security.Crypto

/** v5 user options, stored encrypted. */
class Options(context: Context) {
    private val p = Crypto.prefs(context, "options")

    var scrambleKeypad: Boolean
        get() = p.getBoolean("scramble", false)
        set(v) { p.edit().putBoolean("scramble", v).apply() }

    var biometricEnabled: Boolean
        get() = p.getBoolean("biometric", false)
        set(v) { p.edit().putBoolean("biometric", v).apply() }

    var vaultAutoLockSeconds: Int
        get() = p.getInt("vault_autolock", 30)
        set(v) { p.edit().putInt("vault_autolock", v).apply() }

    // v5: Stealth mode — hide launcher icon
    var stealthMode: Boolean
        get() = p.getBoolean("stealth", false)
        set(v) { p.edit().putBoolean("stealth", v).apply() }

    // v5: Intruder selfie — capture photo on failed PIN
    var intruderSelfieEnabled: Boolean
        get() = p.getBoolean("intruder_selfie", false)
        set(v) { p.edit().putBoolean("intruder_selfie", v).apply() }

    // v5: PIN recovery code (encrypted)
    var recoveryCode: String?
        get() = p.getString("recovery_code", null)
        set(v) { p.edit().putString("recovery_code", v).apply() }

    // v5: WiFi trust — list of trusted SSIDs (comma-separated)
    var trustedSsids: Set<String>
        get() = (p.getStringSet("trusted_ssids", emptySet()) ?: emptySet())
        set(v) { p.edit().putStringSet("trusted_ssids", v).apply() }

    // v5: Custom PIN min length
    var customPinMinLength: Int
        get() = p.getInt("pin_min_len", 4)
        set(v) { p.edit().putInt("pin_min_len", v.coerceIn(4, 8)).apply() }

    // v5: Custom PIN max length
    var customPinMaxLength: Int
        get() = p.getInt("pin_max_len", 8)
        set(v) { p.edit().putInt("pin_max_len", v.coerceIn(4, 12)).apply() }
}
