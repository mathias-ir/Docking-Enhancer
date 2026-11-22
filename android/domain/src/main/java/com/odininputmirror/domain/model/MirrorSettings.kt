package com.odininputmirror.domain.model

data class MirrorSettings(
    val source: String? = null,
    val target: String? = null,
    val sourceGuid: String? = null,
    val targetGuid: String? = null,
    val homeAsBack: Boolean = false,
    val comboHoldKillApp: Boolean = false,
    val virtualMouse: Boolean = false,
    val autoMirrorEnabled: Boolean = true,
    // Mirror while undocked too, for an external pad used with the handheld's own screen. Off by
    // default: undocked is the pocketed state, and waiting for a pad there costs a privileged
    // enumeration every few seconds.
    val mirrorWhenUndocked: Boolean = false,
    val expectedRunning: Boolean = false,
    val startedAt: Long = 0L,
    val manualInternalGuid: String? = null,
    // The external controller the user picked. Null means "first other controller".
    val manualExternalGuid: String? = null,
    // The picked controller's name. The guid is only vendor:product, and some handhelds publish a
    // device of their own on the same id as a republished pad (the RP5's virtual mouse shares
    // 2022:3001 with every external pad's twin), so the name is what tells them apart. Null for a
    // pick saved before names were recorded: it then matches on the guid alone.
    val manualExternalName: String? = null,
    // Advances every time a live option changes. The running daemon echoes the generation it has
    // actually adopted in its heartbeat, so comparing the two says whether a toggle has taken
    // effect — which is what lets a settings change reach the daemon without restarting it.
    val configGeneration: Long = 0L,
)
