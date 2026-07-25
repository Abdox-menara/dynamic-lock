package com.example.dynamiclock.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.example.dynamiclock.R
import com.example.dynamiclock.databinding.ActivityUnlockBinding
import com.example.dynamiclock.locker.LockManager
import com.example.dynamiclock.locker.WiFiTrustManager
import com.example.dynamiclock.pin.PinConfig
import com.example.dynamiclock.pin.PinRepository
import com.example.dynamiclock.recovery.RecoveryManager
import com.example.dynamiclock.security.Crypto
import com.example.dynamiclock.security.IntruderCaptureActivity
import com.example.dynamiclock.util.Options
import com.example.dynamiclock.vault.VaultActivity

class UnlockActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUnlockBinding
    private lateinit var repo: PinRepository
    private lateinit var options: Options
    private val entered = StringBuilder()
    private var mode: String = MODE_VAULT
    private var targetPackage: String? = null

    private var failedCount = 0
    private var lockUntil = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val lockoutPrefs by lazy { Crypto.prefs(this, "lockout") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        binding = ActivityUnlockBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repo = PinRepository(this)
        options = Options(this)

        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_VAULT
        targetPackage = intent.getStringExtra(EXTRA_PACKAGE)

        // Restore persistent lockout state
        failedCount = lockoutPrefs.getInt("failed_count", 0)
        val savedLockUntil = lockoutPrefs.getLong("lock_until", 0L)
        if (savedLockUntil > SystemClock.elapsedRealtime()) {
            lockUntil = savedLockUntil
            showLockoutStatus()
            scheduleLockoutTick()
        }

        // v5: WiFi Trust — auto-dismiss if on trusted network
        if (mode == MODE_APP && options.trustedSsids.isNotEmpty()) {
            if (WiFiTrustManager.isOnTrustedNetwork(this, options.trustedSsids)) {
                onSuccess()
                return
            }
        }

        buildKeypad()
        render()
        maybeOfferBiometric()

        // v5: "Forgot PIN?" recovery button
        binding.btnForgotPin.setOnClickListener { showRecoveryDialog() }
        if (RecoveryManager.hasCode(this)) {
            binding.btnForgotPin.visibility = android.view.View.VISIBLE
        } else {
            binding.btnForgotPin.visibility = android.view.View.VISIBLE
            binding.btnForgotPin.text = "Generate Recovery Code"
            binding.btnForgotPin.setOnClickListener {
                val code = RecoveryManager.generate(this)
                options.recoveryShown = true
                AlertDialog.Builder(this)
                    .setTitle("Recovery Code Generated")
                    .setMessage("Your recovery code: $code\n\nSave this somewhere safe!")
                    .setPositiveButton("OK") { d, _ -> d.dismiss() }
                    .show()
                binding.btnForgotPin.text = getString(R.string.forgot_pin)
                binding.btnForgotPin.setOnClickListener { showRecoveryDialog() }
            }
        }
    }

    private fun showRecoveryDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.recovery_hint)
            setTextColor(resources.getColor(R.color.on_surface, theme))
            setHintTextColor(resources.getColor(R.color.muted, theme))
            textSize = 16f
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.recovery_title))
            .setView(input)
            .setPositiveButton("Recover") { _, _ ->
                val code = input.text.toString().trim()
                if (RecoveryManager.validate(this, code)) {
                    RecoveryManager.markUsed(this)
                    // Reset to default PIN config
                    repo.save(PinConfig.DEFAULT)
                    // Clear lockout
                    failedCount = 0
                    lockUntil = 0L
                    lockoutPrefs.edit().clear().apply()
                    Toast.makeText(this, getString(R.string.recovery_success), Toast.LENGTH_LONG).show()
                    // Re-render to show new PIN
                    handler.postDelayed({
                        entered.clear()
                        render()
                    }, 500)
                } else {
                    AlertDialog.Builder(this)
                        .setTitle("Invalid Code")
                        .setMessage(getString(R.string.recovery_wrong))
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun buildKeypad() {
        val digits = mutableListOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
        if (options.scrambleKeypad) digits.shuffle()
        val rows = listOf(
            listOf(digits[0], digits[1], digits[2]),
            listOf(digits[3], digits[4], digits[5]),
            listOf(digits[6], digits[7], digits[8]),
            listOf(DELETE, digits[9], ENTER)
        )
        binding.keypad.removeAllViews()
        for (row in rows) {
            val rowView = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
                gravity = Gravity.CENTER
            }
            for (label in row) rowView.addView(makeKey(label))
            binding.keypad.addView(rowView)
        }
    }

    private fun makeKey(label: String): Button {
        val size = resources.displayMetrics.density * 78
        return Button(this).apply {
            text = label
            textSize = 22f
            setTextColor(ContextCompat.getColor(this@UnlockActivity, R.color.on_surface))
            setBackgroundResource(R.drawable.bg_key)
            val lp = LinearLayout.LayoutParams(size.toInt(), size.toInt())
            val m = (resources.displayMetrics.density * 8).toInt()
            lp.setMargins(m, m, m, m)
            layoutParams = lp
            setOnClickListener { onKey(label) }
        }
    }

    private fun onKey(label: String) {
        if (isLockedOut()) { showLockoutStatus(); return }
        when (label) {
            DELETE -> if (entered.isNotEmpty()) entered.deleteCharAt(entered.length - 1)
            ENTER -> { validate(); return }
            else -> {
                binding.root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                entered.append(label)
            }
        }
        binding.tvStatus.text = ""
        render()
        if (entered.toString() == repo.currentPin()) onSuccess()
        else if (entered.length >= 12) fail()
    }

    private fun validate() {
        if (entered.toString() == repo.currentPin()) onSuccess() else fail()
    }

    private fun fail() {
        failedCount++
        // v5: Intruder selfie — capture photo on 3rd+ failed attempt
        if (failedCount >= 3 && options.intruderSelfieEnabled) {
            Intrud
