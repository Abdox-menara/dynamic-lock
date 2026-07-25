package com.example.dynamiclock.pin

/** A category used by the strength meter. */
enum class PinCategory { TIME, DATE, BATTERY, RANDOM }

/**
 * A single building block of a dynamic PIN. Each renders to a run of digits
 * computed from the current time / date / battery.
 */
enum class PinComponent(
    val label: String,
    val category: PinCategory = PinCategory.TIME,
    val isVolatile: Boolean = true,
    val digitCount: Int = 2
) {
    TIME_12("Time (12h)", digitCount = 4),         // hh + mm, 12-hour clock  -> 01:23 => "0123"
    TIME_24("Time (24h)", digitCount = 4),         // HH + mm, 24-hour clock  -> 13:23 => "1323"
    HOUR_12("Hour (12h)"),
    HOUR_24("Hour (24h)"),
    MINUTE("Minute"),
    SECOND("Second", isVolatile = true),          // v2: highest-frequency component
    DAY("Day", category = PinCategory.DATE, isVolatile = false),
    MONTH("Month", category = PinCategory.DATE, isVolatile = false),
    WEEKDAY("Weekday", category = PinCategory.DATE, isVolatile = false),  // v2: 1..7
    DAY_OF_YEAR("Day of year", category = PinCategory.DATE, digitCount = 3, isVolatile = false),  // v3: 1..366
    ISO_WEEK("Week of year", category = PinCategory.DATE, isVolatile = false),  // v3: 1..53
    YEAR_2("Year (2-digit)", category = PinCategory.DATE, isVolatile = false),
    YEAR_4("Year (4-digit)", category = PinCategory.DATE, digitCount = 4, isVolatile = false),
    BATTERY("Battery %", category = PinCategory.BATTERY),
    RANDOM("Random", category = PinCategory.RANDOM)  // v4: deterministic pseudo-random within the minute
}

/** A transformation applied to the assembled PIN string. */
enum class PinAddOn(val label: String) {
    DOUBLE("Double"),    // 1234 -> 12341234
    MIRROR("Mirror"),    // 1234 -> 12344321
    SUM("Sum"),          // 1234 -> 1010  (sum of digits, normalised to 4 digits)
    REVERSE("Reverse")   // 1234 -> 4321
}

/**
 * The rule that turns "now" into a PIN.
 */
data class PinConfig(
    val components: List<PinComponent>,
    val offsetMinutes: Int = 0,
    val addOns: List<PinAddOn> = emptyList()
) {
    /** A short human-readable summary, e.g. "Time (24h) + Mirror". */
    fun describe(): String {
        val base = if (components.isEmpty()) "(none)" else components.joinToString(" · ") { it.label }
        val off = if (offsetMinutes != 0) "  offset ${if (offsetMinutes > 0) "+" else ""}$offsetMinutes min" else ""
        val add = if (addOns.isEmpty()) "" else "  +  " + addOns.joinToString(" + ") { it.label }
        return base + off + add
    }

    companion object {
        /** Sensible starting rule: current 24-hour time. */
        val DEFAULT = PinConfig(components = listOf(PinComponent.TIME_24))

        fun time12(offset: Int = 0) = PinConfig(listOf(PinComponent.TIME_12), offset)
        fun time24(offset: Int = 0) = PinConfig(listOf(PinComponent.TIME_24), offset)
        val dateIntl = PinConfig(listOf(PinComponent.DAY, PinComponent.MONTH))
        val dateUsa = PinConfig(listOf(PinComponent.MONTH, PinComponent.DAY))
        val dateIntlYear = PinConfig(listOf(PinComponent.DAY, PinComponent.MONTH, PinComponent.YEAR_4))
        val battery = PinConfig(listOf(PinComponent.BATTERY), addOns = listOf(PinAddOn.DOUBLE))
    }
}
