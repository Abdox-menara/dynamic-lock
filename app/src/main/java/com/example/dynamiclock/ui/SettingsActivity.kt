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

        // Load existing rule into the editor.
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

        binding.btnClear.setOnClickListener {
            sequence.clear(); refresh()
        }
        binding.btnSave.setOnClickListener { save() }

        refresh()
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
            if (sequence.isEmpty()) "(empty · defaults to Time 24h)"
            else sequence.joinToString(" · ") { it.label }
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
}
