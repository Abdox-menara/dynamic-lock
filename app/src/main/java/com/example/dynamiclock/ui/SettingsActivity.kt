package com.example.dynamiclock.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.widget.GridLayout
import androidx.activity.result.contract.ActivityResultContracts
import com.example.dynamiclock.R
import com.example.dynamiclock.databinding.ActivitySettingsBinding
import com.example.dynamiclock.pin.PinAddOn
import com.example.dynamiclock.pin.PinComponent
import com.example.dynamiclock.pin.PinConfig
import com.example.dynamiclock.pin.PinEngine
import com.example.dynamiclock.pin.PinRepository
import com.example.dynamiclock.recovery.RecoveryManager
import com.example.dynamiclock.util.Options

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var repo: PinRepository
    private lateinit var options: Options

    private val sequence = mutableListOf<PinComponent>()
    private val addOns = linkedSetOf<PinAddOn>()

    private val cameraPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) Toast.makeText(this, "Camera permission granted", Toast.LENGTH_SHORT).show()
        else Toast.makeText(this, "Camera permission required for intruder selfie", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repo = PinRepository(this)
        options = Options(this)

        // Load existing rule
        val current = repo.load()
        sequence.addAll(current.components)
        addOns.addAll(current.addOns)
        binding.etOffset.setText(current.offsetMinutes.toString())

        buildPresetButtons()
        buildComponentButtons()
        buildAddOnChecks()

        // v4 options
        binding.cbScramble.isChecked = options.scrambleKeypad
        binding.cbScramble.setOnCheckedChangeListener { _, v -> options.scrambleKeypad = v }
        binding.cbBiometric.isChecked = options.biometricEnabled
        binding.cbBiometric.setOnCheckedChangeListener { _, v -> options.biometricEnabled = v }

        // v5: Stealth Mode
        binding.cbStealth.isChecked = options.stealthMode
        binding.cbStealth.setOnCheckedChangeListener { _, v ->
            options.stealthMode = v
            updateStealthMode(v)
        }

        // v5: Intruder Selfie
        binding.cbIntruderSelfie.isChecked = options.intruderSelfieEnabled
        binding.cbIntruderSelfie.setOnCheckedChangeListener { _, v ->
            options.intruderSelfieEnabled = v
            if (v) {
                // Request camera permission
                if (checkSelfPermission(android.Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                    cameraPermLauncher.launch(android.Manifest.permission.CAMERA)
                }
            }
        }

        // v5: WiFi Trust checkbox
        binding.cbWiFiTrust.isChecked = options.trustedSsids.isNotEmpty()
        binding.cbWiFiTrust.setOnCheckedChangeListener { _, v ->
            if (!v) {
                options.trustedSsids = emptySet()
                refreshWiFiTrustSection()
            }
        }

        // v5: Recovery Code section
        refreshRecoverySection()

        // v5: WiFi Trust
        refreshWiFiTrustSection()

        // v4: vault auto-lock
        binding.sliderAutoLock.apply {
            value = options.vaultAutoLockSeconds.toFloat()
            setLabelFormatter { "${it.toInt()}s" }
            addOnChangeListener { _, v, _ ->
                options.vaultAutoLockSeconds = v.toInt()
                binding.tvAutoLockVal.text = formatAutoLock(v.toInt())
            }
        }
        binding.tvAutoLockVal.text = formatAutoLock(options.vaultAutoLockSeconds)

        binding.etOffset.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = refresh()
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        binding.btnClear.setOnClickListener { sequence.clear(); refresh() }

        // v5: Add SSID button
        binding.btnAddSsid.setOnClickListener {
            val ssid = binding.etSsid.text.toString().trim()
            if (ssid.isNotEmpty()) {
                val current = options.trustedSsids.toMutableSet()
                current.add(ssid)
                options.trustedSsids = current
                binding.etSsid.text.clear()
                refreshWiFiTrustSection()
            }
        }

        binding.btnSave.setOnClickListener { save() }
        refresh()
    }

    private fun refreshRecoverySection() {
        if (RecoveryManager.hasCode(this)) {
            binding.tvRecoveryCode.text = getString(R.string.recovery_code_label) + "\n" +
                RecoveryManager.maskedCode(this)
            binding.btnShowRecovery.text = "Copy Recovery Code"
            binding.btnShowRecovery.setOnClickListener {
                val code = RecoveryManager.maskedCode(this) ?: return@setOnClickListener
                // In production, you'd use ClipboardManager here
                Toast.makeText(this, getString(R.string.recovery_code_copied), Toast.LENGTH_SHORT).show()
            }
        } else {
            binding.tvRecoveryCode.text = getString(R.string.recovery_code_label) + "\n(not yet generated)"
            binding.btnShowRecovery.text = "Generate Recovery Code"
            binding.btnShowRecovery.setOnClickListener {
                val code = RecoveryManager.generate(this)
                binding.tvRecoveryCode.text = getString(R.string.recovery_code_label) + "\n$code"
                Toast.makeText(this, getString(R.string.recovery_code_copied), Toast.LENGTH_LONG).show()
                binding.btnShowRecovery.text = "Copy Recovery Code"
            }
        }
    }

    private fun refreshWiFiTrustSection() {
        val ssids = options.trustedSsids
        binding.tvTrustedNetworks.text = if (ssids.isEmpty()) {
            "No trusted networks"
        } else {
            ssids.joinToString("\n") { "• $it" }
        }
    }

    private fun updateStealthMode(enabled: Boolean) {
        // Toggle the activity-alias to show/hide launcher icon
        val pm = packageManager
        val componentName = android.content.ComponentName(
            this,
            "com.example.dynamiclock.ui.MainActivityAlias"
        )
        pm.setComponentEnabledSetting(
            componentName,
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            else PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
        Toast.makeText(
            this,
            if (enabled) "App icon hidden. Use dialer: *#*#1234#*#* or QS tile"
            else "App icon visible again",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun buildPresetButtons() {
        val presets = listOf(
            "Time 12h" to PinConfig.time12(),
            "Time 24h" to PinConfig.time24(),
            "Date · Intl" to PinConfig.dateIntl,
            "Date · USA" to PinConfig.dateUsa,
            "Date + Year" to PinConfig.dateIntlYear,
            "Battery" to PinConfig.battery
        )
        binding.gridPresets.columnCount = 2
        for ((label, cfg) in presets) {
            binding.gridPresets.addView(chip(label) {
                sequence.clear(); sequence.addAll(cfg.components)
                addOns.clear(); addOns.addAll(cfg.addOns)
                binding.etOffset.setText(cfg.offsetMinutes.toString())
                syncAddOnChecks()
                refresh()
            })
        }
    }

    private fun buildComponentButtons() {
        binding.gridComponents.columnCount = 2
        for (c in PinComponent.values()) {
            binding.gridComponents.addView(chip(c.labe
