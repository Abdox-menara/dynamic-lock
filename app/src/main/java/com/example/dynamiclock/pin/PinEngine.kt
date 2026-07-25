package com.example.dynamiclock.pin

/** Deterministic snapshot of the values a PIN can be built from. */
data class PinInput(
    val year: Int,
    val month: Int,    // 1..12
    val day: Int,      // 1..31
    val hour24: Int,   // 0..23
    val minute: Int,   // 0..59
    val batteryPct: Int, // 0..100
    val second: Int = 0,     // 0..59
    val weekday: Int = 1,    // 1..7
    val dayOfYear: Int = 1,  // 1..366
    val week: Int = 1        // 1..53
)

/**
 * Pure, side-effect-free dynamic-PIN calculator. Contains no Android APIs so it can be
 * unit tested on any JVM. See [PinConfig] for the rule model.
 */
object PinEngine {

    fun compute(config: PinConfig, input: PinInput): String {
        val effMinutes = (((input.hour24 * 60 + input.minute + config.offsetMinutes) % 1440) + 1440) % 1440
        val h24 = effMinutes / 60
        val m = effMinutes % 60
        val h12 = ((h24 + 11) % 12) + 1

        val base = StringBuilder()
        for (c in config.components) base.append(render(c, input, h24, m, h12))

        var pin = base.toString()
        for (a in ADDON_ORDER) if (config.addOns.contains(a)) pin = applyAddOn(a, pin)
        return pin
    }

    /** Rough structural strength label for a rule (does not depend on live values). */
    fun strength(config: PinConfig): String {
        val comps = config.components
        if (comps.isEmpty()) return "—"
        val categories = comps.map { it.category }
        val distinctCategories = categories.distinct().size
        val volatile = comps.count { it.isVolatile }
        val doubleContribution = if (config.addOns.contains(PinAddOn.DOUBLE)) comps.sumOf { it.digitCount } else 0
        val mirrorContribution = if (config.addOns.contains(PinAddOn.MIRROR)) comps.sumOf { it.digitCount } else 0
        val totalDigits = comps.sumOf { it.digitCount } + doubleContribution + mirrorContribution
        val base = distinctCategories + volatile + if (config.addOns.isNotEmpty()) 1 else 0 + (totalDigits / 2)
        return when {
            base <= 3 -> "Weak"
            base <= 6 -> "Medium"
            else -> "Strong"
        }
    }

    private val ADDON_ORDER = listOf(PinAddOn.DOUBLE, PinAddOn.MIRROR, PinAddOn.SUM, PinAddOn.REVERSE)

    private fun render(c: PinComponent, input: PinInput, h24: Int, m: Int, h12: Int): String = when (c) {
        PinComponent.TIME_12 -> pad2(h12) + pad2(m)
        PinComponent.TIME_24 -> pad2(h24) + pad2(m)
        PinComponent.HOUR_12 -> pad2(h12)
        PinComponent.HOUR_24 -> pad2(h24)
        PinComponent.MINUTE -> pad2(m)
        PinComponent.SECOND -> pad2(input.second)
        PinComponent.DAY -> pad2(input.day)
        PinComponent.MONTH -> pad2(input.month)
        PinComponent.WEEKDAY -> pad2(input.weekday)
        PinComponent.DAY_OF_YEAR -> pad3(input.dayOfYear)
        PinComponent.ISO_WEEK -> pad2(input.week)
        PinComponent.YEAR_2 -> pad2(((input.year % 100) + 100) % 100)
        PinComponent.YEAR_4 -> input.year.toString().padStart(4, '0')
        PinComponent.BATTERY -> if (input.batteryPct in 0..99) pad2(input.batteryPct) else input.batteryPct.toString()
        PinComponent.RANDOM -> {
            // Deterministic pseudo-random based on the minute, so the PIN
            // changes every minute but is stable within it.
            val seed = (h24 * 60 + m) * 31 + input.day * 7 + input.month
            val r = ((seed * 1103515245L + 12345) / 65536) % 100
            pad2(r.toInt())
        }
    }

    private fun applyAddOn(a: PinAddOn, s: String): String = when (a) {
        PinAddOn.DOUBLE -> s + s
        PinAddOn.MIRROR -> s + s.reversed()
        PinAddOn.REVERSE -> s.reversed()
        PinAddOn.SUM -> {
            val digitSum = s.filter { it.isDigit() }.sumOf { (it - '0') }
            val ds = digitSum.toString()
            val out = StringBuilder()
            while (out.length < 4) out.append(ds)
            out.substring(0, 4)
        }
    }

    private fun pad2(n: Int): String {
        val v = if (n < 0) 0 else n
        return if (v < 10) "0$v" else v.toString()
    }

    private fun pad3(n: Int): String {
        val v = if (n < 0) 0 else n
        return v.toString().padStart(3, '0')
    }
}
