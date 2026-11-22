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

    private val unmanaged = ExternalDisplayColorFix.UNMANAGED
    private val restore = ExternalDisplayColorFix.RESTORE

    @Test
    fun appliesOnDockConfirmsOnceThenStaysQuiet() {
        val shell = RecordingShell()
        val fix = ExternalDisplayColorFix(shell)
        repeat(4) { fix.sync(setOf(4)) }
        assertEquals(listOf(unmanaged, unmanaged), shell.commands)
    }

    @Test
    fun restoresOnUndockAndReappliesOnRedock() {
        val shell = RecordingShell()
        val fix = ExternalDisplayColorFix(shell)
        fix.sync(setOf(4))
        fix.sync(setOf(4))
        fix.sync(emptySet())
        fix.sync(emptySet())
        fix.sync(setOf(5))
        assertEquals(listOf(unmanaged, unmanaged, restore, unmanaged), shell.commands)
    }

    @Test
    fun aReplugBetweenTicksStillReapplies() {
        val shell = RecordingShell()
        val fix = ExternalDisplayColorFix(shell)
        fix.sync(setOf(4))
        fix.sync(setOf(4))
        // Unplugged and plugged back within one tick: never seen undocked, but the id moved on.
        fix.sync(setOf(5))
        fix.sync(setOf(5))
        fix.sync(setOf(5))
        assertEquals(listOf(unmanaged, unmanaged, unmanaged, unmanaged), shell.commands)
    }

    @Test
    fun firstUndockedTickRestoresInCaseAPreviousProcessDiedDocked() {
        val shell = RecordingShell()
        ExternalDisplayColorFix(shell).sync(emptySet())
        assertEquals(listOf(restore), shell.commands)
    }

    @Test
    fun retriesWhileTheCommandIsNotDelivered() {
        val shell = RecordingShell(delivers = false)
        val fix = ExternalDisplayColorFix(shell)
        fix.sync(setOf(4))
        shell.delivers = true
        fix.sync(setOf(4))
        fix.sync(setOf(4))
        fix.sync(setOf(4))
        // Undelivered, applied, confirmed, quiet.
        assertEquals(3, shell.commands.size)
    }

    @Test
    fun restoreReplaysTheValuesSystemServerKeepsInItsProperties() {
        assertEquals(
            "n=\$(getprop persist.sys.sf.native_mode); c=\$(getprop persist.sys.sf.color_mode); " +
                "service call SurfaceFlinger 1023 i32 \${n:-0} i32 \${c:-0} >/dev/null 2>&1",
            restore,
        )
    }
}
