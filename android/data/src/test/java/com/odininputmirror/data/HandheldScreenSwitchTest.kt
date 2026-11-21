package com.odininputmirror.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HandheldScreenSwitchTest {
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
    fun settingOneMeansTheScreenGoesOffWhenDocked() {
        assertEquals(false, HandheldScreenSwitch.parse("1", vendorSettingsInstalled = true))
        assertEquals(true, HandheldScreenSwitch.parse("0\n", vendorSettingsInstalled = true))
    }

    @Test
    fun absentKeyIsTheFirmwareDefaultOnARetroidAndUnsupportedElsewhere() {
        assertEquals(true, HandheldScreenSwitch.parse(null, vendorSettingsInstalled = true))
        assertNull(HandheldScreenSwitch.parse(null, vendorSettingsInstalled = false))
        assertNull(HandheldScreenSwitch.parse("null", vendorSettingsInstalled = false))
    }

    @Test
    fun writesTheVendorKeyThroughTheShell() {
        val shell = RecordingShell()
        val switch = HandheldScreenSwitch({ "0" }, { true }, shell)
        assertTrue(switch.setShownWhenDocked(false))
        switch.setShownWhenDocked(true)
        assertEquals(
            listOf(
                "settings put system close_screen_when_output_video 1 >/dev/null 2>&1",
                "settings put system close_screen_when_output_video 0 >/dev/null 2>&1",
            ),
            shell.commands,
        )
    }

    @Test
    fun readsThroughTheShellWhenTheProviderRefuses() {
        val shell = RecordingShell(readResult = "1")
        val switch = HandheldScreenSwitch({ throw SecurityException("not readable") }, { true }, shell)
        assertFalse(switch.isShownWhenDocked()!!)
        assertEquals(listOf("settings get system close_screen_when_output_video"), shell.commands)
    }
}
