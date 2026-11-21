package com.odininputmirror.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbPadDriverBinderTest {
    private class RecordingShell(var delivers: Boolean = true) : MirrorShell {
        val commands = mutableListOf<String>()
        override val isAvailable = true
        override fun exec(command: String): Boolean {
            commands += command
            return delivers
        }
        override fun read(command: String) = ""
        override fun launchDaemon(command: String) = false
    }

    @Test
    fun writesEachIdToXpadNewIdInItsOwnSubshell() {
        val command = UsbPadDriverBinder.command(
            listOf(UsbPadDriverBinder.UsbId(0x2dc8, 0x3106), UsbPadDriverBinder.UsbId(0x045e, 0x028e)),
        )
        assertEquals(
            "(echo 2dc8 3106 > /sys/bus/usb/drivers/xpad/new_id) 2>/dev/null; " +
                "(echo 045e 028e > /sys/bus/usb/drivers/xpad/new_id) 2>/dev/null",
            command,
        )
    }

    @Test
    fun knownIdsIncludeThe8BitDoUltimateReceiver() {
        assertTrue(UsbPadDriverBinder.UsbId(0x2dc8, 0x3106) in UsbPadDriverBinder.KNOWN_XPAD_IDS)
    }

    @Test
    fun writesOnlyOnceOnceDelivered() {
        val shell = RecordingShell()
        val binder = UsbPadDriverBinder(shell)
        repeat(3) { binder.ensureApplied() }
        assertEquals(1, shell.commands.size)
    }

    @Test
    fun retriesWhileTheCommandIsNotDelivered() {
        val shell = RecordingShell(delivers = false)
        val binder = UsbPadDriverBinder(shell)
        binder.ensureApplied()
        binder.ensureApplied()
        shell.delivers = true
        binder.ensureApplied()
        binder.ensureApplied()
        assertEquals(3, shell.commands.size)
    }

    @Test
    fun doesNothingWithoutIds() {
        val shell = RecordingShell()
        UsbPadDriverBinder(shell, ids = emptyList()).ensureApplied()
        assertEquals(0, shell.commands.size)
    }
}
