package com.odininputmirror.data

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.io.File

/**
 * DEBUG BUILDS ONLY. Lets the on-device e2e suite (`.claude/skills/mirror-e2e`) run its root
 * scaffolding on a handheld that has no `su`.
 *
 * The suite needs root only as scaffolding — killing a daemon to stage an orphan, deleting a
 * heartbeat, cleaning up. On a rooted Odin it uses `su`. A stock Retroid Pocket 5 has no `su`, and
 * PServerBinder cannot be reached from a shell (servicemanager hides it from `service call`, even
 * under `run-as`), so the only process that can drive it is this app. This receiver hands the
 * suite's commands to the same [PServerExec] the product uses:
 *
 *     am broadcast -f 32 -n com.odininputmirror/com.odininputmirror.data.E2eBridgeReceiver \
 *         -a com.odininputmirror.e2e.EXEC   --es cmd '<cmd>'     # runs it, waits for it
 *     ...   -a com.odininputmirror.e2e.LAUNCH --es cmd '<cmd>'   # starts a daemon, returns
 *
 * `am broadcast` waits for the receiver and prints its result data, which carries the first line of
 * stdout (all PServer returns). A broadcast rather than a content provider because this firmware
 * ships no `content` command.
 *
 * It is a root shell by design, so it is fenced three ways: it exists only in the debug source set
 * (a release APK does not contain it); the manifest requires the sender to hold DUMP, which the adb
 * shell has and no third-party app can be granted; and it runs in its own `:e2e` process, where
 * [com.odininputmirror.MainApplication] does not start the supervisor that would otherwise fight
 * the suite for the controllers.
 */
internal class E2eBridgeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val command = intent.getStringExtra(EXTRA_CMD)
        if (command.isNullOrBlank()) {
            setResult(Activity.RESULT_CANCELED, "missing --es $EXTRA_CMD", null)
            return
        }

        val result = when (intent.action) {
            // Runs to completion before the broadcast finishes.
            ACTION_EXEC -> transact(command)
            // A daemon must not be started with a trailing `&`: it is SIGHUP'd when the transact
            // returns and input_mirror exits on SIGHUP. Same remedy as PServerShell.launchDaemon —
            // run it in the foreground of a script and background the script.
            ACTION_LAUNCH -> {
                val script = File(context.filesDir, LAUNCH_SCRIPT)
                script.writeText("$command\n")
                script.setReadable(true, false)
                transact("sh ${script.absolutePath} </dev/null >/dev/null 2>&1 &")
            }
            else -> Result.failure(IllegalArgumentException("unknown action ${intent.action}"))
        }

        result
            .onSuccess { out -> setResult(Activity.RESULT_OK, out.orEmpty(), null) }
            .onFailure { error -> setResult(Activity.RESULT_CANCELED, "bridge error: ${error.message}", null) }
    }

    // PServerBinder cannot service overlapping transactions.
    private fun transact(command: String): Result<String?> = synchronized(lock) {
        PServerExec.shared.executeAsRoot(command)
    }

    private companion object {
        const val ACTION_EXEC = "com.odininputmirror.e2e.EXEC"
        const val ACTION_LAUNCH = "com.odininputmirror.e2e.LAUNCH"
        const val EXTRA_CMD = "cmd"
        const val LAUNCH_SCRIPT = "e2e_launch.sh"
        val lock = Any()
    }
}
