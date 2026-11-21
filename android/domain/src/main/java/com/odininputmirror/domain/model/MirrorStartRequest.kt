package com.odininputmirror.domain.model

data class MirrorStartRequest(
    val source: String,
    val target: String,
    val homeAsBack: Boolean,
    val comboHoldKillApp: Boolean,
    val sourceGuid: String? = null,
    val targetGuid: String? = null,
    val virtualMouse: Boolean = false,
    // /dev/input nodes the daemon unlinks at startup (hiding them from the framework) and restores
    // on exit — the external controller's framework-visible Odin node. Empty when none was resolved.
    val hideNodes: List<String> = emptyList(),
    // The controller's captured mapping, resolved from its real identity by the caller. Empty means
    // "no correction", which is what every controller gets until someone runs the wizard.
    val mapping: ControllerMapping = ControllerMapping(),
    // The real pad the daemon reads in place of [source] (see [DirectSource]). [source] stays the
    // twin's path regardless: it is what is persisted, compared and re-materialised, so only the
    // daemon's argv changes.
    val directSource: DirectSource? = null,
)
