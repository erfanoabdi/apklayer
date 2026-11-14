/* The app's views of its own process beyond maps and ids (bridge/native/proc_task.c): task comm names (JVM and apklayer threads under ART's names, the same in
 * comm, stat and status), the cgroup line, the security state (status and prctl), and fd links of host runtime jars.
 * Run with APKLAYER_OWN_DATA ("app"): the views; with APKLAYER_PROC_VIEW=host ("host") and without it ("service"): the kernel's values. */
#define _GNU_SOURCE
#include <assert.h>
#include <fcntl.h>
#include <limits.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/prctl.h>
#include <sys/syscall.h>
#include <unistd.h>

static void *named(void *name)
{
    prctl(PR_SET_NAME, (const char *)name, 0, 0, 0);
    sleep(3);
    return NULL;
}

static char *slurp(const char *path)
{
    static char text[16384];
    FILE *f = fopen(path, "r");
    assert(f);
    size_t n = fread(text, 1, sizeof text - 1, f);
    fclose(f);
    text[n] = 0;
    return text;
}

static void line_of(const char *text, const char *key, char *out, size_t size)
{
    const char *at = strstr(text, key);
    assert(at);
    at += strlen(key);
    while (*at == ' ' || *at == '\t') at++;
    snprintf(out, size, "%.*s", (int)strcspn(at, "\n"), at);
}

/* the comm of thread `tid` as comm, stat (field 2) and status (Name) show it: all three agree */
static void comm_of(long tid, char *out, size_t size)
{
    char path[96], a[64], b[64], c[64], *text;
    snprintf(path, sizeof path, "/proc/self/task/%ld/comm", tid);
    text = slurp(path);
    snprintf(a, sizeof a, "%.*s", (int)strcspn(text, "\n"), text);
    snprintf(path, sizeof path, "/proc/self/task/%ld/stat", tid);
    text = slurp(path);
    char *open_paren = strchr(text, '('), *close_paren = strrchr(text, ')');
    assert(open_paren && close_paren > open_paren);
    snprintf(b, sizeof b, "%.*s", (int)(close_paren - open_paren - 1), open_paren + 1);
    snprintf(path, sizeof path, "/proc/self/task/%ld/status", tid);
    line_of(slurp(path), "Name:", c, sizeof c);
    assert(!strcmp(a, b) && !strcmp(a, c));
    snprintf(out, size, "%s", a);
}

struct probe { const char *thread, *app_name; pthread_t id; long tid; };

int main(void)
{
    int app = getenv("APKLAYER_OWN_DATA") && !getenv("APKLAYER_PROC_VIEW");
    struct probe probes[] = {{"VM Thread", "HeapTaskDaemon"}, {"C2 CompilerThre", "Jit thread pool"}, {"apklayer-vsync", NULL}, {"Signal Dispatch", "Signal Catcher"},
                             {"MyGameThread", "MyGameThread"}};
    for (size_t i = 0; i < sizeof probes / sizeof *probes; i++) pthread_create(&probes[i].id, NULL, named, (void *)probes[i].thread);
    usleep(300000);
    char found[256], names[sizeof probes / sizeof *probes][64];
    int seen = 0;
    /* every task of the process by its listing: the thread named x is found by its (projected or real) comm */
    char path[64];
    snprintf(path, sizeof path, "/proc/self/task");
    FILE *ls = popen("ls /proc/self/task", "r");
    assert(ls);
    long tids[64]; int n = 0;
    while (fgets(found, sizeof found, ls) && n < 64) tids[n++] = atol(found);
    pclose(ls);
    for (int i = 0; i < n; i++) {
        char comm[64];
        comm_of(tids[i], comm, sizeof comm);
        for (size_t j = 0; j < sizeof probes / sizeof *probes; j++) {
            char real[64];
            snprintf(real, sizeof real, "%.15s", probes[j].thread);
            if (!app && !strcmp(comm, real)) { snprintf(names[j], sizeof names[j], "%s", comm); seen++; }
            if (app && probes[j].app_name && !strcmp(comm, probes[j].app_name)) { snprintf(names[j], sizeof names[j], "%s", comm); seen++; }
            if (app && !probes[j].app_name && !strncmp(comm, "binder:", 7)) { snprintf(names[j], sizeof names[j], "%s", comm); seen++; }
        }
        if (app) { assert(strncmp(comm, "VM Thread", 9) && strncmp(comm, "C2 Compiler", 11) && strncmp(comm, "apklayer-", 9) && strcmp(comm, "Signal Dispatch")); }
    }
    printf("threads seen under the expected names: %d of %zu\n", seen, sizeof probes / sizeof *probes);
    assert(seen >= (int)(sizeof probes / sizeof *probes));

    /* security state and process lines */
    char value[128];
    char *status = slurp("/proc/self/status");
    line_of(status, "NoNewPrivs:", value, sizeof value);
    long nnp = prctl(PR_GET_NO_NEW_PRIVS, 0, 0, 0, 0), dump = prctl(PR_GET_DUMPABLE), sec = prctl(PR_GET_SECCOMP);
    printf("status NoNewPrivs=%s prctl no_new_privs=%ld dumpable=%ld seccomp=%ld\n", value, nnp, dump, sec);
    if (app) {
        assert(!strcmp(value, "1") && nnp == 1 && dump == 0 && sec == 2);
        line_of(slurp("/proc/self/status"), "Seccomp:", value, sizeof value);
        assert(!strcmp(value, "2"));
        line_of(slurp("/proc/self/status"), "CapBnd:", value, sizeof value);
        assert(!strcmp(value, "0000000000000000"));
        char expected[96];
        snprintf(expected, sizeof expected, "0::/uid_%d/pid_%d\n", (int)getuid() /* the test's uid mapping is the app uid only through the real one */, getpid());
        char *cgroup = slurp("/proc/self/cgroup");
        printf("cgroup: %s", cgroup);
        assert(!strncmp(cgroup, "0::/uid_", 8) && strstr(cgroup, "/pid_") && !strstr(cgroup, "systemd") && !strstr(cgroup, "user.slice"));
        (void)expected;
    } else {
        assert(nnp == (strcmp(value, "0") ? 1 : 0));
        assert(!strstr(slurp("/proc/self/cgroup"), "/uid_10001/"));
    }

    /* an fd of the runtime's jar is an Android jar to the app, and any other file keeps its path */
    char dir[] = "/tmp/apklayer-proc-views-XXXXXX", jar[96], plain[96], link[PATH_MAX], target[PATH_MAX];
    assert(mkdtemp(dir));
    snprintf(jar, sizeof jar, "%s/Robolectric_all.jar", dir);
    snprintf(plain, sizeof plain, "%s/plain.txt", dir);
    int a = open(jar, O_CREAT | O_RDWR, 0600), b = open(plain, O_CREAT | O_RDWR, 0600);
    assert(a >= 0 && b >= 0);
    snprintf(link, sizeof link, "/proc/self/fd/%d", a);
    ssize_t k = readlink(link, target, sizeof target - 1);
    assert(k > 0);
    target[k] = 0;
    printf("fd %d -> %s\n", a, target);
    assert(app ? !strcmp(target, "/apex/com.android.art/javalib/core-libart.jar") : !strcmp(target, jar));
    snprintf(link, sizeof link, "/proc/self/fd/%d", b);
    k = readlink(link, target, sizeof target - 1);
    target[k > 0 ? k : 0] = 0;
    assert(!strcmp(target, plain));
    close(a); close(b); unlink(jar); unlink(plain); rmdir(dir);
    puts("proc-views ok");
    _exit(0);
}
