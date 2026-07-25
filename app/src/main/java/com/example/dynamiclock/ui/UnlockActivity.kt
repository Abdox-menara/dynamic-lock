package com.example.dynamiclock.ui

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
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.example.dynamiclock.R
import com.example.dynamiclock.databinding.ActivityUnlockBinding
import com.example.dynamiclock.locker.LockManager
import com.example.dynamiclock.pin.PinRepository
import com.example.dynamiclock.security.Crypto
import com.example.dynamiclock.util.Options
import com.example.dynamiclock.vault.VaultActivity

/**
 * Full-screen gate that asks for the currently-valid dynamic PIN. Adds (v2): FLAG_SECURE,
 * attempt throttling, haptics, an optional scrambled keypad, and optional biometric unlock.
 */
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

        buildKeypad()
        render()
        maybeOfferBiometric()
    }

    private fun buildKeypad() {
        val digits = mutableListOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
        if (options.scrambleKeypad) digits.shuffle() // v4: reshuffles every time
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
        if (failedCount >= MAX_ATTEMPTS) {
            val backoff = minOf(30_000L * (1L shl (failedCount - MAX_ATTEMPTS)), 300_000L)
            lockUntil = SystemClock.elapsedRealtime() + backoff
            // v4: persist lockout across app restart
            lockoutPrefs.edit()
                .putInt("failed_count", failedCount)
                .putLong("lock_until", lockUntil)
                .apply()
            scheduleLockoutTick()
        } else {
            binding.tvStatus.text = getString(R.string.wrong_pin)
        }
        entered.clear()
        render()
        binding.tvEntry.startAnimation(AnimationUtils.loadAnimation(this, R.anim.shake))
    }

    private fun isLockedOut(): Boolean = SystemClock.elapsedRealtime() < lockUntil

    private fun showLockoutStatus() {
        val secs = ((lockUntil - SystemClock.elapsedRealtime()) / 1000) + 1
        binding.tvStatus.text = getString(R.string.locked_out, secs)
    }

    private fun scheduleLockoutTick() {
        handler.removeCallbacksAndMessages(null)
        val tick = object : Runnable {
            override fun run() {
                if (isLockedOut()) { showLockoutStatus(); handler.postDelayed(this, 500) }
                else binding.tvStatus.text = ""
            }
        }
        handler.post(tick)
    }

    private fun render() {
        binding.tvEntry.text = "•".repeat(entered.length)
    }

    private fun onSuccess() {
        failedCount = 0
        lockUntil = 0L
        // v4: clear persisted lockout
        lockoutPrefs.edit().clear().apply()
        // v4: brief haptic + visual success feedback
        binding.tvEntry.setTextColor(ContextCompat.getColor(this, R.color.white))
        binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.white))
        binding.tvStatus.text = "✓"
        handler.postDelayed({
            when (mode) {
                MODE_APP -> { targetPackage?.let { LockManager.markUnlocked(it) }; finish() }
                else -> { startActivity(Intent(this, VaultActivity::class.java)); finish() }
            }
        }, 400)
    }

    // ---- Biometric ----

    private fun biometricAvailable(): Boolean =
        BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS

    private fun maybeOfferBiometric() {
        if (options.biometricEnabled && biometricAvailable()) {
            binding.btnBiometric.visibility = android.view.View.VISIBLE
            binding.btnBiometric.setOnClickListener { showBiometric() }
        } else {
            binding.btnBiometric.visibility = android.view.View.GONE
        }
    }

    private fun showBiometric() {
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }
            })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.app_name))
            .setSubtitle(getString(R.string.biometric_subtitle))
            .setNegativeButtonText(getString(R.string.use_pin))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()
        prompt.authenticate(info)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onBackPressed() {
        if (mode == MODE_APP) {
            startActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        finish()
    }

    companion object {
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_PACKAGE = "package"
        const val MODE_VAULT = "vault"
        const val MODE_APP = "app"
        private const val DELETE = "⌫"
        private const val ENTER = "✓"
        private const val MAX_ATTEMPTS = 5

        fun forVault(context: Context): Intent =
            Intent(context, UnlockActivity::class.java).putExtra(EXTRA_MODE, MODE_VAULT)

        fun forApp(context: Context, pkg: String): Intent =
            Intent(context, UnlockActivity::class.java)
                .putExtra(EXTRA_MODE, MODE_APP)
                .putExtra(EXTRA_PACKAGE, pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
}
