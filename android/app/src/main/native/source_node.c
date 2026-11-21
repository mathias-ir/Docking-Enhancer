#include "source_node.h"

#include <errno.h>
#include <fcntl.h>
#include <linux/input.h>
#include <stdio.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <sys/sysmacros.h>
#include <sys/xattr.h>
#include <unistd.h>

// "eventN" and nothing else. The name ends up in a /sys path, so anything that could walk out of
// /sys/class/input is refused before it gets there.
static int is_event_name(const char *name) {
    if (strncmp(name, "event", 5) != 0 || name[5] == '\0') {
        return 0;
    }
    for (const char *c = name + 5; *c != '\0'; c++) {
        if (*c < '0' || *c > '9') {
            return 0;
        }
    }
    return 1;
}

static int open_through_sysfs(const char *path) {
    const char *slash = strrchr(path, '/');
    const char *name = slash != NULL ? slash + 1 : path;
    if (!is_event_name(name)) {
        errno = ENOENT;
        return -1;
    }

    char sys_path[96];
    snprintf(sys_path, sizeof(sys_path), "/sys/class/input/%s/dev", name);
    FILE *f = fopen(sys_path, "r");
    if (f == NULL) {
        // No such input device any more: the pad really is gone.
        errno = ENOENT;
        return -1;
    }
    unsigned int maj = 0;
    unsigned int min = 0;
    int parsed = fscanf(f, "%u:%u", &maj, &min);
    fclose(f);
    if (parsed != 2) {
        errno = ENOENT;
        return -1;
    }

    // Outside /dev/input, so EventHub's inotify watch never reports it, and labelled like a real
    // input node so the pservice domain is allowed to open it — the same staging restore uses.
    char staging[64];
    snprintf(staging, sizeof(staging), "/dev/.input_mirror_source.%d", (int)getpid());
    unlink(staging);
    if (mknod(staging, S_IFCHR | 0600, makedev(maj, min)) != 0) {
        int saved = errno;
        fprintf(stderr, "source: mknod for %s failed: %s\n", path, strerror(saved));
        errno = saved;
        return -1;
    }
    const char *ctx = "u:object_r:input_device:s0";
    if (setxattr(staging, "security.selinux", ctx, strlen(ctx) + 1, 0) != 0) {
        fprintf(stderr, "source: setxattr for %s: %s\n", path, strerror(errno));
    }
    int fd = open(staging, O_RDONLY | O_CLOEXEC);
    int saved = errno;
    unlink(staging);
    if (fd >= 0) {
        fprintf(stderr, "source: %s has no node; opened it as %u:%u\n", path, maj, min);
    }
    errno = saved;
    return fd;
}

int open_source_node(const char *path, const struct source_id *expected) {
    int fd = open(path, O_RDONLY | O_CLOEXEC);
    if (fd < 0 && errno == ENOENT && expected != NULL) {
        fd = open_through_sysfs(path);
    }
    if (fd < 0 || expected == NULL) {
        return fd;
    }

    struct input_id id;
    if (ioctl(fd, EVIOCGID, &id) != 0 || id.vendor != expected->vendor || id.product != expected->product) {
        fprintf(stderr, "source: %s is not %04x:%04x, refusing\n", path, expected->vendor, expected->product);
        close(fd);
        errno = ENODEV;
        return -1;
    }
    return fd;
}
