#define _GNU_SOURCE
#include <assert.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/syscall.h>
#include <unistd.h>

static void check(FILE *f, int filtered)
{
    assert(f);
    char line[1024]; int seen = 0, pid = 0, groups = 0;
    while (fgets(line, sizeof line, f)) {
        int uid = !strncmp(line, "Uid:", 4), gid = !strncmp(line, "Gid:", 4);
        if (uid || gid) {
            unsigned a, b, c, d, want = filtered ? 10001 : uid ? getuid() : getgid();
            assert(sscanf(line+4, "%u %u %u %u", &a, &b, &c, &d)==4);
            assert(a==want && b==want && c==want && d==want); seen++;
        }
        if (!strncmp(line, "Groups:", 7)) {
            gid_t expected[128];
            int count;
            if (filtered) { expected[0]=3003; expected[1]=9997; expected[2]=20001; expected[3]=50001; count=4; }
            else { count=getgroups(128,expected); assert(count>=0); }
            int found=0;
            for (char *v=strtok(line+7," \t\n"); v; v=strtok(NULL," \t\n")) {
                assert(found<count && strtoul(v,NULL,10)==expected[found]); found++;
            }
            assert(found==count); groups++;
        }
        if (!strncmp(line, "Umask:", 6) && filtered) assert(!strcmp(line, "Umask:\t0077\n"));
        if (!strncmp(line, "Tgid:", 5)) { assert(atoi(line+5)==getpid()); pid++; }
    }
    assert(!ferror(f) && seen==2 && pid==1 && groups==1);
    fclose(f);
}
int main(void)
{
    int filtered = getenv("APKLAYER_OWN_DATA") && !getenv("APKLAYER_PROC_VIEW");
    char path[96]; snprintf(path, sizeof path, "/proc/%d/task/%ld/status", getpid(), syscall(SYS_gettid));
    const char *paths[] = {"/proc/self/status", "/proc/thread-self/status", path};
    for (int i=0; i<3; i++) {
        check(fopen(paths[i], "r"), filtered);
        int fd= syscall(SYS_openat, AT_FDCWD, paths[i], O_RDONLY, 0); assert(fd>=0);
        check(fdopen(fd,"r"), filtered);
    }
    int dir=open("/proc/self", O_RDONLY|O_DIRECTORY); assert(dir>=0);
    int fd=openat(dir,"status",O_RDONLY); assert(fd>=0); check(fdopen(fd,"r"),filtered); close(dir);
    if (filtered) {
        char id[32] = ""; FILE *l = fopen("/proc/self/loginuid", "r");
        assert(l && fgets(id, sizeof id, l) && !strcmp(id, "4294967295")); fclose(l);
    }
    puts("PASS status: own PID, all UID/GID columns and supplementary groups, stdio/syscall/openat and thread aliases");
}
