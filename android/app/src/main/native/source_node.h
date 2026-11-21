// Opening the mirror's source, including a pad the firmware has taken out of /dev/input.
//
// A handheld that republishes external pads (the RP5, the Odin) deletes the real pad's node and
// leaves only its twin. The twin has a fixed axis set, though, and a pad whose axes do not fit it
// loses them there: an Xbox-protocol pad's right stick (ABS_RX/ABS_RY) has nowhere to go. The real
// input device still exists and its /sys entry still names its device number, so it can be opened
// through a private node the framework never sees.
#ifndef SOURCE_NODE_H
#define SOURCE_NODE_H

struct source_id {
    unsigned short vendor;
    unsigned short product;
};

// Open `path` read-only. When it has no node and `expected` is given, materialise one from
// /sys/class/input/<eventN>/dev outside /dev/input (so EventHub never sees it), open that, and drop
// it again: the fd is all the mirror needs, and nothing is left to restore on exit.
//
// With `expected`, the opened device must carry that vendor:product, or the open is refused: event
// numbers are recycled, and the app resolved this path a moment before we act on it. Returns the fd,
// or -1 with errno set.
int open_source_node(const char *path, const struct source_id *expected);

#endif  // SOURCE_NODE_H
