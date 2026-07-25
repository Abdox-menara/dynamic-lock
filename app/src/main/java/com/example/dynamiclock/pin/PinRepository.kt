package com.example.dynamiclock.pin

import android.content.Context
import android.os.BatteryManager
import com.example.dynamiclock.security.Crypto
import java.util.Calendar

/**
 * Loads/saves the [PinConfig] and evaluates the current PIN from the live clock and battery.
 * This is the only PIN class that touches Android APIs.
 */
class PinRepository(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = Crypto.prefs(appContext, PREFS)

    fun load(): PinConfig {
        val comps = prefs.getString(KEY_COMPONENTS, null)
            ?.split(",")?.filter { it.isNotBlank() }
            ?.mapNotNull { runCatching { PinComponent.valueOf(it) }.getOrNull() }
            ?: return PinConfig.DEFAULT
        if (comps.isEmpty()) return PinConfig.DEFAULT
        val adds = prefs.getString(KEY_ADDONS, "")
            ?.split(",")?.filter { it.isNotBlank() }
            ?.mapNotNull { runCatching { PinAddOn.valueOf(it) }.getOrNull() }
            ?: emptyList()
        val offset = prefs.getInt(KEY_OFFSET, 0)
        return PinConfig(comps, offset, adds)
    }

    fun save(config: PinConfig) {
        prefs.edit()
            .putString(KEY_COMPONENTS, config.components.joinToString(",") { it.name })
            .putString(KEY_ADDONS, config.addOns.joinToString(",") { it.name })
            .putInt(KEY_OFFSET, config.offsetMinutes)
            .apply()
    }

    /** Snapshot of the current clock + battery. */
    fun currentInput(): PinInput {
        val cal = Calendar.getInstance()
        val bm = appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val battery = (bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 0)
            .coerceIn(0, 100)
        return PinInput(
            year = cal.get(Calendar.YEAR),
            month = cal.get(Calendar.MONTH) + 1,
            day = cal.get(Calendar.DAY_OF_MONTH),
            hour24 = cal.get(Calendar.HOUR_OF_DAY),
            minute = cal.get(Calendar.MINUTE),
            batteryPct = battery,
            second = cal.get(Calendar.SECOND),
            weekday = cal.get(Calendar.DAY_OF_WEEK),
            dayOfYear = cal.get(Calendar.DAY_OF_YEAR),
            week = cal.get(Calendar.WEEK_OF_YEAR)
        )
    }

    /** The PIN that would unlock the device right now. */
    fun currentPin(): String = PinEngine.compute(load(), currentInput())

    companion object {
        private const val PREFS = "pin_rule"
        private const val KEY_COMPONENTS = "components"
        private const val KEY_ADDONS = "addons"
        private const val KEY_OFFSET = "offset"
    }
}
