package com.example.dynamiclock.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * v5: Silent launcher that opens the main app from a dialer code.
 * This activity has Theme.NoDisplay — it never shows a UI.
 * Called by the system when the user types *#*#<code>#*#* in the dialer.
 */
class SecretDialerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        })
        finish()
    }
}
