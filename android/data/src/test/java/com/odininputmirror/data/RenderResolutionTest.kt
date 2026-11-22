package com.odininputmirror.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderResolutionTest {
    private class RecordingShell : MirrorShell {
        val commands = mutableListOf<String>()
        override val isAvailable = true
        override fun exec(command: String): Boolean {
            commands += command
            return true
        }
        override fun read(command: String) = ""
        override fun launchDaemon(command: String) = false
    }

    private val rp5Panel = 1080 to 1920

    @Test
    fun reducesThePortraitPanelTo720pWithScaledDensity() {
        assertEquals(
            "wm size 720x1280; d=\$(wm density | sed -n 's/^Physical density: //p'); " +
                "[ -n \"\$d\" ] && wm density \$((d * 720 / 1080))",
            RenderResolution.reduceCommand(1080, 1920),
        )
    }

    @Test
    fun oddScaledSidesRoundDownToEven() {
        // 1080 x 2340 -> 720 x 1560; 1200 x 1920 -> 720 x 1152.
        assertTrue(RenderResolution.reduceCommand(1080, 2340).startsWith("wm size 720x1560;"))
        assertTrue(RenderResolution.reduceCommand(1200, 1920).startsWith("wm size 720x1152;"))
    }

    @Test
    fun reportsWhetherAndroidRunsBelowThePanel() {
        val full = RenderResolution({ rp5Panel }, { rp5Panel }, RecordingShell())
        val reduced = RenderResolution({ rp5Panel }, { 720 to 1280 }, RecordingShell())
        assertFalse(full.isReduced())
        assertTrue(reduced.isReduced())
        assertEquals(720, reduced.currentLines())
    }

    @Test
    fun aPanelAlreadyAt720HasNothingToReduce() {
        assertFalse(RenderResolution({ 720 to 1280 }, { 720 to 1280 }, RecordingShell()).isSupported())
        assertTrue(RenderResolution({ rp5Panel }, { rp5Panel }, RecordingShell()).isSupported())
    }

    @Test
    fun restoringResetsBothOverrides() {
        val shell = RecordingShell()
        RenderResolution({ rp5Panel }, { 720 to 1280 }, shell).setReduced(false)
        assertEquals(listOf("wm size reset; wm density reset"), shell.commands)
    }
}
