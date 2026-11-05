/* Is the host's PipeWire daemon or PulseAudio server there? A probe's cheap check (docs/PLUGINS.md): a connect() to its local socket, no client
 * library started (PipeWire's client prints "can't load config client.conf" on a host without PipeWire, and a context costs a roundtrip).
 * Private to apklayer's own backends (audio and camera), not part of the plugin API. */
#ifndef APKLAYER_SOUND_SERVER_H
#define APKLAYER_SOUND_SERVER_H

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>

/* a server listens at this unix socket */
static inline int apklayer_socket_answers(const char *path)
{
    struct sockaddr_un a = {.sun_family = AF_UNIX};
    if (!path || !*path || strlen(path) >= sizeof a.sun_path) return 0;
    strcpy(a.sun_path, path);
    int fd = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (fd < 0) return 0;
    int ok = connect(fd, (struct sockaddr *)&a, sizeof a) == 0;
    close(fd);
    return ok;
}

/* <dir>/<name>: dir is $<dir_env> when set, else $XDG_RUNTIME_DIR, else /run/user/<uid> */
static inline void apklayer_runtime_path(char *out, size_t size, const char *dir_env, const char *name)
{
    const char *dir = getenv(dir_env);
    if (!dir || !*dir) dir = getenv("XDG_RUNTIME_DIR");
    if (dir && *dir) snprintf(out, size, "%s/%s", dir, name);
    else snprintf(out, size, "/run/user/%u/%s", (unsigned)getuid(), name);
}

/* PipeWire's socket: $PIPEWIRE_REMOTE (a path, or a name in the runtime directory), else pipewire-0 */
static inline int apklayer_pipewire_answers(void)
{
    const char *remote = getenv("PIPEWIRE_REMOTE");
    char path[512];
    if (remote && *remote == '/') return apklayer_socket_answers(remote);
    apklayer_runtime_path(path, sizeof path, "PIPEWIRE_RUNTIME_DIR", remote && *remote ? remote : "pipewire-0");
    return apklayer_socket_answers(path);
}

/* PulseAudio's (or pipewire-pulse's) socket: $PULSE_SERVER ("unix:<path>" or a path; a TCP server counts as there), else
 * $PULSE_RUNTIME_PATH/native, else pulse/native in the runtime directory */
static inline int apklayer_pulse_answers(void)
{
    const char *server = getenv("PULSE_SERVER"), *runtime = getenv("PULSE_RUNTIME_PATH");
    char path[512];
    if (server && *server) {
        if (!strncmp(server, "unix:", 5)) return apklayer_socket_answers(server + 5);
        if (*server == '/') return apklayer_socket_answers(server);
        return 1;  /* tcp:host or a list of servers: libpulse tries them, no cheap check here */
    }
    if (runtime && *runtime) snprintf(path, sizeof path, "%s/native", runtime);
    else apklayer_runtime_path(path, sizeof path, "XDG_RUNTIME_DIR", "pulse/native");
    return apklayer_socket_answers(path);
}

#endif
