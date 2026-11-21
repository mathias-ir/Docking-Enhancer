package com.odininputmirror.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ExternalDisplayColorFixTest {
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
    fun appliesUnmanagedOnceWhileDocked() {
        val shell = RecordingShell()
        val fix = ExternalDisplayColorFix(shell)
        repeat(3) { fix.sync(dockActive = true) }
        assertEquals(listOf(ExternalDisplayColorFix.UNMANAGED), shell.commands)
    }

    @Test
    fun restoresOnUndockAndReappliesOnRedock() {
        val shell = RecordingShell()
        val fix = ExternalDisplayColorFix(shell)
        fix.sync(dockActive = true)
        fix.sync(dockActive = false)
        fix.sync(dockActive = false)
        fix.sync(dockActive = true)
        assertEquals(
            listOf(
                ExternalDisplayColorFix.UNMANAGED,
                ExternalDisplayColorFix.RESTORE,
                ExternalDisplayColorFix.UNMANAGED,
            ),
            shell.commands,
        )
    }

    @Test
    fun firstUndockedTickRestoresInCaseAPreviousProcessDiedDocked() {
        val shell = RecordingShell()
        ExternalDisplayColorFix(shell).sync(dockActive = false)
        assertEquals(listOf(ExternalDisplayColorFix.RESTORE), shell.commands)
    }

    @Test
    fun retriesWhileTheCommandIsNotDelivered() {
        val shell = RecordingShell(delivers = false)
        val fix = ExternalDisplayColorFix(shell)
        fix.sync(dockActive = true)
        shell.delivers = true
        fix.sync(dockActive = true)
        fix.sync(dockActive = true)
        assertEquals(2, shell.commands.size)
    }

    @Test
    fun restoreReplaysTheValuesSystemServerKeepsInItsProperties() {
        assertEquals(
            "n=\$(getprop persist.sys.sf.native_mode); c=\$(getprop persist.sys.sf.color_mode); " +
                "service call SurfaceFlinger 1023 i32 \${n:-0} i32 \${c:-0} >/dev/null 2>&1",
            ExternalDisplayColorFix.RESTORE,
        )
    }
}
