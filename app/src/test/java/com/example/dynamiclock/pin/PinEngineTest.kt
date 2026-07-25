package com.example.dynamiclock.pin

import org.junit.Assert.assertEquals
import org.junit.Test

/** Verifies the engine against DroidLock's documented examples, on the real Kotlin code. */
class PinEngineTest {

    private val t0123 = PinInput(2016, 5, 4, 1, 23, 52, second = 7, weekday = 4, dayOfYear = 125, week = 19)
    private val t1323 = PinInput(2016, 5, 4, 13, 23, 52)
    private val t1234 = PinInput(2016, 5, 4, 12, 34, 52)

    private fun cfg(comps: List<PinComponent>, offset: Int = 0, adds: List<PinAddOn> = emptyList()) =
        PinConfig(comps, offset, adds)

    @Test fun time12() =
        assertEquals("0123", PinEngine.compute(cfg(listOf(PinComponent.TIME_12)), t0123))

    @Test fun time12WithOffset() =
        assertEquals("0133", PinEngine.compute(cfg(listOf(PinComponent.TIME_12), 10), t0123))

    @Test fun time24() =
        assertEquals("1323", PinEngine.compute(cfg(listOf(PinComponent.TIME_24)), t1323))

    @Test fun dateIntl() =
        assertEquals("0405", PinEngine.compute(cfg(listOf(PinComponent.DAY, PinComponent.MONTH)), t0123))

    @Test fun dateUsa() =
        assertEquals("0504", PinEngine.compute(cfg(listOf(PinComponent.MONTH, PinComponent.DAY)), t0123))

    @Test fun dateIntlYear2() =
        assertEquals("040516", PinEngine.compute(cfg(listOf(PinComponent.DAY, PinComponent.MONTH, PinComponent.YEAR_2)), t0123))

    @Test fun dateIntlYear4() =
        assertEquals("04052016", PinEngine.compute(cfg(listOf(PinComponent.DAY, PinComponent.MONTH, PinComponent.YEAR_4)), t0123))

    @Test fun geekCombo() =
        assertEquals("52010523", PinEngine.compute(cfg(listOf(PinComponent.BATTERY, PinComponent.HOUR_12, PinComponent.MONTH, PinComponent.MINUTE)), t0123))

    @Test fun batteryDouble() =
        assertEquals("5252", PinEngine.compute(cfg(listOf(PinComponent.BATTERY), 0, listOf(PinAddOn.DOUBLE)), t0123))

    @Test fun addonDouble() =
        assertEquals("12341234", PinEngine.compute(cfg(listOf(PinComponent.TIME_24), 0, listOf(PinAddOn.DOUBLE)), t1234))

    @Test fun addonMirror() =
        assertEquals("12344321", PinEngine.compute(cfg(listOf(PinComponent.TIME_24), 0, listOf(PinAddOn.MIRROR)), t1234))

    @Test fun addonSum() =
        assertEquals("1010", PinEngine.compute(cfg(listOf(PinComponent.TIME_24), 0, listOf(PinAddOn.SUM)), t1234))

    @Test fun addonReverse() =
        assertEquals("4321", PinEngine.compute(cfg(listOf(PinComponent.TIME_24), 0, listOf(PinAddOn.REVERSE)), t1234))

    @Test fun secondComponent() =
        assertEquals("07", PinEngine.compute(cfg(listOf(PinComponent.SECOND)), t0123))

    @Test fun weekdayComponent() =
        assertEquals("04", PinEngine.compute(cfg(listOf(PinComponent.WEEKDAY)), t0123))

    @Test fun dayOfYearComponent() =
        assertEquals("125", PinEngine.compute(cfg(listOf(PinComponent.DAY_OF_YEAR)), t0123))

    @Test fun isoWeekComponent() =
        assertEquals("19", PinEngine.compute(cfg(listOf(PinComponent.ISO_WEEK)), t0123))
}
