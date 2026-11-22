package com.odininputmirror.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BuiltInRefreshRateTest {
    private class RecordingShell(private val readResult: String = "") : MirrorShell {
        val commands = mutableListOf<String>()
        override val isAvailable = true
        override fun exec(command: String): Boolean {
            commands += command
            return true
        }
        override fun read(command: String): String {
            commands += command
            return readResult
        }
        override fun launchDaemon(command: String) = false
    }

    @Test
    fun panelRatesAreRoundedAndDeduplicated() {
        assertEquals(listOf(60, 120), BuiltInRefreshRate.rates(listOf(120.00001f, 60.000004f, 60.000004f)))
    }

    @Test
    fun tapsStepThroughTheSupportedRatesAndWrap() {
        assertEquals(120, BuiltInRefreshRate.next(listOf(60, 120), 60))
        assertEquals(60, BuiltInRefreshRate.next(listOf(60, 120), 120))
        assertEquals(90, BuiltInRefreshRate.next(listOf(60, 90, 120), 60))
    }

    @Test
    fun unpinnedOrUnknownStartsAtTheTop() {
        assertEquals(120, BuiltInRefreshRate.next(listOf(60, 120), null))
        assertEquals(120, BuiltInRefreshRate.next(listOf(60, 120), 90))
    }

    @Test
    fun aSingleRatePanelHasNothingToToggle() {
        assertNull(BuiltInRefreshRate.next(listOf(60), 60))
    }

    @Test
    fun readsThePinnedPeakAndFallsBackToTheShell() {
        assertEquals(120, BuiltInRefreshRate({ emptyList() }, { "120.0" }, RecordingShell()).current())
        val shell = RecordingShell(readResult = "60.0\n")
        assertEquals(60, BuiltInRefreshRate({ emptyList() }, { throw SecurityException() }, shell).current())
        assertNull(BuiltInRefreshRate({ emptyList() }, { null }, RecordingShell()).current())
    }

    @Test
    fun pinsMinAndPeakTogether() {
        val shell = RecordingShell()
        BuiltInRefreshRate({ emptyList() }, { null }, shell).set(60)
        assertEquals(
            listOf("settings put system min_refresh_rate 60.0; settings put system peak_refresh_rate 60.0"),
            shell.commands,
        )
    }
}
