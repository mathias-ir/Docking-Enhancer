#!/system/bin/sh
# Shared by e2e.sh and latency.sh: how to get root, and which handheld this is.
#
# --- privilege -----------------------------------------------------------------------------------
#
# Root is test scaffolding only (copying the binary out, killing a daemon to stage an orphan, deleting
# a heartbeat, cleaning up). Where the device has `su` (a rooted Odin) it is used as before. Where it
# does not (a stock Retroid Pocket 5), the debug build's E2eBridgeReceiver runs the command through
# the same PServerBinder the product uses — so on those devices the daemon even runs in the product's
# own SELinux context. `am` waits for the receiver, so priv returns only once the command finished.
BRIDGE_RCV=com.odininputmirror/com.odininputmirror.data.E2eBridgeReceiver
if su -c true >/dev/null 2>&1; then PRIV=su; else PRIV=pserver; fi

# $1 = EXEC|LAUNCH, $2 = command. Prints the first line of the command's stdout (all PServer returns).
# umask 022: root's default would make every file the daemon writes unreadable to the assertions.
bridge_call() {
    am broadcast -f 32 -n $BRIDGE_RCV -a "com.odininputmirror.e2e.$1" --es cmd "umask 022; $2" 2>/dev/null \
        | sed -n 's/^Broadcast completed: result=-1, data="\(..*\)"$/\1/p'
}
priv()    { if [ "$PRIV" = su ]; then su -c "$1"; else bridge_call EXEC "$1"; fi; }
# Detached. Through the bridge this must not be a trailing `&`: the daemon would be SIGHUP'd when the
# transact returns, so the receiver backgrounds a script that runs it in the foreground instead.
priv_bg() { if [ "$PRIV" = su ]; then su -c "$1 &"; else bridge_call LAUNCH "$1" >/dev/null; fi; }

# Aborts unless root is reachable one way or the other. Call before touching anything.
require_priv() {
    echo "  privilegios: $PRIV"
    if [ "$PRIV" = pserver ]; then
        if [ "$(bridge_call EXEC 'echo bridge-ok')" != "bridge-ok" ]; then
            echo "  ABORT: sin su y sin el puente e2e (instala el build DEBUG de la app)"
            exit 1
        fi
        # PServer's root may not override file permissions the way su does, so everything the suite
        # creates for the daemon (scratch dir, fifos, configs) has to be open to it. Scratch only.
        umask 000
    fi
}

# --- handheld ------------------------------------------------------------------------------------
#
# Synthetic pads must carry the handheld's OWN vendor. The firmware republishes any controller with a
# foreign vendor as a twin under its own and deletes the original node, and the daemon only hides
# nodes carrying the built-in pad's vendor. The target takes the handheld's Xbox-layout product id so
# the daemon's Nintendo face-button swap (Odin 2020:0111 only) stays off and forwarding is 1:1.
if grep -q 'Vendor=2022 Product=3002' /proc/bus/input/devices; then
    HANDHELD="Retroid Pocket 5"; PAD_VENDOR=0x2022; SRC_PRODUCT=0x3001; DST_PRODUCT=0x3002
else
    HANDHELD="Odin"; PAD_VENDOR=0x2020; SRC_PRODUCT=0x0111; DST_PRODUCT=0x0112
fi
