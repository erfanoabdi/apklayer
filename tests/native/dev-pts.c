/* The desktop's terminals in an app's /dev/pts (bridge/native/paths.c desktop_terminal_path): once the process knows its package, /dev/pts/<n> is ENOENT
 * under stat, access and open; /dev/pts and /dev/pts/ptmx stay (a program's own pty); APKLAYER_PTS_VIEW=host turns the filter off. Skipped (77) where the host
 * has no numbered terminal to look at. */
#define _GNU_SOURCE
#include <assert.h>
#include <dirent.h>
#include <dlfcn.h>
#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <unistd.h>

int main(int argc, char **argv) {
    char path[300] = "";
    if (!getenv("APKLAYER_TEST_PTS")) {  /* the numbered terminal is found before the mapping, and handed to the re-run: the path layer reads its environment once */
        DIR *d = opendir("/dev/pts");
        struct dirent *e;
        while (d && (e = readdir(d))) if (e->d_name[0] >= '0' && e->d_name[0] <= '9') { snprintf(path, sizeof path, "/dev/pts/%s", e->d_name); break; }
        if (d) closedir(d);
        if (!path[0]) { puts("skipped: no numbered terminal in /dev/pts"); return 77; }
        setenv("APKLAYER_TEST_PTS", path, 1);
        execv("/proc/self/exe", argv);
        return 1;
    }
    snprintf(path, sizeof path, "%s", getenv("APKLAYER_TEST_PTS"));
    struct stat st;
    assert(stat(path, &st) == 0);                              /* the host's terminal, before the mapping */
    void (*map)(const char *, long, const char *, const char *) = dlsym(RTLD_DEFAULT, "apklayer_map_own_data");
    assert(map);
    map("p.pts", 12345, "", "/tmp/plt");
    if (getenv("APKLAYER_PTS_VIEW")) {                         /* the switch: the terminal is there again */
        assert(stat(path, &st) == 0);
        puts("dev-pts host view ok");
        return 0;
    }
    assert(stat(path, &st) == -1 && errno == ENOENT);
    assert(access(path, F_OK) == -1 && errno == ENOENT);
    assert(open(path, O_RDONLY | O_NOCTTY) == -1 && errno == ENOENT);
    assert(stat("/dev/pts", &st) == 0);                        /* the directory and the pty master stay */
    assert(stat("/dev/pts/ptmx", &st) == 0 || errno == ENOENT);
    if (fork() == 0) { setenv("APKLAYER_PTS_VIEW", "host", 1); execv("/proc/self/exe", argv); _exit(1); }
    int status; wait(&status);
    assert(WIFEXITED(status) && WEXITSTATUS(status) == 0);
    puts("dev-pts ok");
    return 0;
}
