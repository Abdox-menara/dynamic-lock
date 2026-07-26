package com.example.dynamiclock.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import androidx.appcompat.app.AppCompatActivity
import android.widget.GridLayout
import com.example.dynamiclock.R
import com.example.dynamiclock.databinding.ActivitySettingsBinding
import com.example.dynamiclock.pin.PinAddOn
import com.example.dynamiclock.pin.PinComponent
import com.example.dynamiclock.pin.PinConfig
import com.example.dynamiclock.pin.PinEngine
import com.example.dynamiclock.pin.PinRepository
import com.example.dynamiclock.util.Options
import com.example.dynamiclock.locker.WiFiTrustManager
import com.example.dynamiclock.recovery.RecoveryManager
import android.app.AlertDialog
import android.text.InputType
import android.widget.EditText
import android.widget.TextView

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var repo: PinRepository
    private lateinit var options: Options

    private val sequence = mutableListOf<PinComponent>()
    private val addOns = linkedSetOf<PinAddOn>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repo = PinRepository(this)
        options = Options(this)

        val current = repo.load()
        sequence.addAll(current.components)
        addOns.addAll(current.addOns)
        binding.etOffset.setText(current.offsetMinutes.toString())

        buildPresetButtons()
        buildComponentButtons()
        buildAddOnChecks()

        binding.cbScramble.isChecked = options.scrambleKeypad
        binding.cbScramble.setOnCheckedChangeListener { _, v -> options.scrambleKeypad = v }
        binding.cbBiometric.isChecked = options.biometricEnabled
        binding.cbBiometric.setOnCheckedChangeListener { _, v -> options.biometricEnabled = v }

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

        binding.btnClear.setOnClickListener {
            sequence.clear(); refresh()
        }
        binding.btnSave.setOnClickListener { save() }

        // v5: programmatic UI sections
        buildV5Settings()

        refresh()
    }

    private fun buildPresetButtons() {
        val presets = listOf(
            "Time 12h" to PinConfig.time12(),
            "Time 24h" to PinConfig.time24(),
            "Date \u00b7 Intl" to PinConfig.dateIntl,
            "Date \u00b7 USA" to PinConfig.dateUsa,
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
            binding.gridComponents.addView(chip(c.label) {
                sequence.add(c); refresh()
            })
        }
    }

    private val addOnChecks = mutableListOf<CheckBox>()
    private fun buildAddOnChecks() {
        for (a in PinAddOn.values()) {
            val cb = CheckBox(this).apply {
                text = a.label
                setTextColor(resources.getColor(R.color.on_surface, theme))
                isChecked = addOns.contains(a)
                setOnCheckedChangeListener { _, checked ->
                    if (checked) addOns.add(a) else addOns.remove(a)
                    refresh()
                }
                val lp = GridLayout.LayoutParams()
                lp.width = 0
                lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                layoutParams = lp
            }
            addOnChecks.add(cb)
            binding.gridAddons.addView(cb)
        }
    }

    private fun syncAddOnChecks() {
        for (cb in addOnChecks) {
            val a = PinAddOn.values().firstOrNull { it.label == cb.text.toString() } ?: continue
            cb.isChecked = addOns.contains(a)
        }
    }

    private fun chip(label: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 14f
            setTextColor(resources.getColor(R.color.on_surface, theme))
            setBackgroundResource(R.drawable.bg_card)
            setOnClickListener { onClick() }
            val lp = GridLayout.LayoutParams()
            lp.width = 0
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            val m = (resources.displayMetrics.density * 5).toInt()
            lp.setMargins(m, m, m, m)
            layoutParams = lp
        }
    }

    private fun currentOffset(): Int =
        binding.etOffset.text.toString().trim().toIntOrNull() ?: 0

    private fun workingConfig(): PinConfig {
        val comps = if (sequence.isEmpty()) listOf(PinComponent.TIME_24) else sequence.toList()
        return PinConfig(comps, currentOffset(), addOns.toList())
    }

    private fun refresh() {
        binding.tvSequence.text =
            if (sequence.isEmpty()) "(empty \u00b7 defaults to Time 24h)"
            else sequence.joinToString(" \u00b7 ") { it.label }
        val pin = PinEngine.compute(workingConfig(), repo.currentInput())
        binding.tvPreview.text = pin
        binding.tvPinLength.text = "${pin.length} digits"
        binding.tvStrength.text = "Strength: " + PinEngine.strength(workingConfig())
    }

    private fun save() {
        repo.save(workingConfig())
        finish()
    }

    private fun formatAutoLock(secs: Int): String = when {
        secs <= 0 -> "Immediate (lock on screen off)"
        secs < 60 -> "$secs seconds"
        else -> "${secs / 60} min"
    }

    // ---- v5 features ----

    private fun buildV5Settings() {
        val root = binding.root as? ViewGroup ?: return
        val ctx = this

        val cbStealth = CheckBox(ctx).apply {
            text = "Stealth mode (hide PIN dots)"
            setTextColor(resources.getColor(R.color.on_surface, theme))
            isChecked = options.stealthMode
            setOnCheckedChangeListener { _, v -> options.stealthMode = v }
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        root.addView(cbStealth)

        val tvRecoveryLabel = TextView(ctx).apply {
            text = "PIN RECOVERY"
            setTextColor(resources.getColor(R.color.muted, theme))
            textSize = 14f
            setPadding(0, (resources.displayMetrics.density * 24).toInt(), 0, 0)
        }
        root.addView(tvRecoveryLabel)

        val btnRecovery = Button(ctx).apply {
            text = if (RecoveryManager.hasCode(ctx)) "Show Recovery Code" else "Generate Recovery Code"
            isAllCaps = false
            setOnClickListener {
                if (RecoveryManager.hasCode(ctx)) {
                    showRecoveryDialog()
                } else {
                    val code = RecoveryManager.generate(ctx)
                    AlertDialog.Builder(ctx)
                        .setTitle("Recovery Code")
                        .setMessage("Write this down and keep it safe!\n\n" + code)
                        .setPositiveButton("OK") { d, _ -> d.dismiss() }
                        .show()
                    text = "Show Recovery Code"
                }
            }
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        root.addView(btnRecovery)

        val tvWifiLabel = TextView(ctx).apply {
            text = "TRUSTED WI-FI (auto-unlock)"
            setTextColor(resources.getColor(R.color.muted, theme))
            textSize = 14f
            setPadding(0, (resources.displayMetrics.density * 24).toInt(), 0, 0)
        }
        root.addView(tvWifiLabel)

        val wifiRow = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val etSsid = EditText(ctx).apply {
            hint = "WiFi SSID (e.g. MyHomeWiFi)"
            setTextColor(resources.getColor(R.color.on_surface, theme))
            setHintTextColor(resources.getColor(R.color.muted, theme))
            inputType = InputType.TYPE_CLASS_TEXT
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            )
        }
        wifiRow.addView(etSsid)

        val btnAddSsid = Button(ctx).apply {
            text = "Add"
            isAllCaps = false
            setOnClickListener {
                val ssid = etSsid.text.toString().trim()
                if (ssid.isNotEmpty()) {
                    val ssids = options.trustedSsids.toMutableSet()
                    ssids.add(ssid)
                    options.trustedSsids = ssids
                    etSsid.text.clear()
                    refreshWiFiDisplay(tvWifiList)
                }
            }
        }
        wifiRow.addView(btnAddSsid)
        root.addView(wifiRow)

        val tvWifiList = TextView(ctx).apply {
            text = ""
            setTextColor(resources.getColor(R.color.on_surface, theme))
            textSize = 14f
            setPadding(0, (resources.displayMetrics.density * 8).toInt(), 0, 0)
        }
        root.addView(tvWifiList)
        refreshWiFiDisplay(tvWifiList)
    }

    private fun refreshWiFiDisplay(tvWifiList: TextView) {
        val ssids = options.trustedSsids
        tvWifiList.text = if (ssids.isEmpty()) "No trusted WiFi networks."
            else ssids.joinToString("\n") { "  \u2022  $it" }
    }

    private fun showRecoveryDialog() {
        val code = RecoveryManager.maskedCode(this) ?: return
        AlertDialog.Builder(this)
            .setTitle("Recovery Code")
            .setMessage("Your saved recovery code:\n\n${code}\n\nYou can use it to unlock if you forget your PIN.")
            .setPositiveButton("OK", null)
            .setNeutralButton("Regenerate") { _, _ ->
                val newCode = RecoveryManager.generate(this)
                AlertDialog.Builder(this)
                    .setTitle("New Recovery Code")
                    .setMessage("Write this down and keep it safe!\n\n$newCode")
                    .setPositiveButton("OK", null)
                    .show()
            }
            .show()
    }
}
