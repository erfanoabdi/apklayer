/* /proc as an app sees it: every spelling of another process's entry (the path layer's filter, bridge/native/paths.c) is gone. The checks that Android answers
 * too (kill(pid, 0), getsid: pids are global there as well) are listed apart. Run in its own cgroup scope (systemd-run --user --scope): in a shared one every
 * process is the app's. */
#define _GNU_SOURCE
#include <dlfcn.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <string.h>
#include <dirent.h>
#include <signal.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <sys/xattr.h>
#include <sched.h>
#include <sys/resource.h>
static int bad, same_as_android;
static void chkA(const char *what, int leaked) { printf("%-46s %s\n", what, leaked ? "answers (as on Android: pids are global, SELinux/uid decide)" : "denied"); same_as_android += leaked; }
static void chk(const char *what, int leaked) { printf("%-46s %s\n", what, leaked ? "LEAK" : "ok"); bad += leaked; }
int main(void){
  void (*m)(const char*,long,const char*,const char*)=dlsym(RTLD_DEFAULT,"apklayer_map_own_data"); m("p.kg",12345,"","/tmp/plt");
  { /* the filter judges processes by cgroup: only meaningful when this process is alone in its own */
    char c1[256] = "", path[600], line[64]; int others = 0;
    /* the kernel's cgroup line: the app's /proc/self/cgroup is Android-shaped (uid_N/pid_N) once the package is mapped, no path to cgroup.procs */
    long (*raw)(long, ...) = dlsym(RTLD_NEXT, "syscall");
    int cg = raw(SYS_openat, AT_FDCWD, "/proc/self/cgroup", O_RDONLY | O_CLOEXEC, 0);
    FILE *f = cg >= 0 ? fdopen(cg, "r") : NULL;
    if (f && fgets(c1, sizeof c1, f)) { c1[strcspn(c1, "\n")] = 0; snprintf(path, sizeof path, "/sys/fs/cgroup%s/cgroup.procs", c1 + 3); }
    else path[0] = 0;
    if (f) fclose(f);
    if (path[0] && (f = fopen(path, "r"))) { while (fgets(line, sizeof line, f)) others += atoi(line) != getpid(); fclose(f); }
    if (!path[0] || others) { puts("skipped: other processes share this cgroup (run it in systemd-run --user --scope)"); return 77; } }
  struct stat st; char b[256], l[256];
  chk("stat /proc/1", stat("/proc/1",&st)==0);
  chk("lstat /proc/1", lstat("/proc/1",&st)==0);
  chk("access /proc/1/status", access("/proc/1/status",F_OK)==0);
  chk("open /proc/1/cmdline", open("/proc/1/cmdline",O_RDONLY)>=0);
  chk("open /proc/1/environ", open("/proc/1/environ",O_RDONLY)>=0);
  chk("openat2-style O_PATH /proc/1", open("/proc/1",O_PATH)>=0);
  chk("opendir /proc/1", opendir("/proc/1")!=NULL);
  chk("opendir /proc/1/task", opendir("/proc/1/task")!=NULL);
  chk("readlink /proc/1/exe", readlink("/proc/1/exe",l,sizeof l)>=0);
  chk("readlink /proc/1/cwd", readlink("/proc/1/cwd",l,sizeof l)>=0);
  chk("readlink /proc/1/root", readlink("/proc/1/root",l,sizeof l)>=0);
  chk("readlink /proc/1/ns/mnt", readlink("/proc/1/ns/mnt",l,sizeof l)>=0);
  chk("stat /proc/1/ns/pid", stat("/proc/1/ns/pid",&st)==0);
  chk("getxattr /proc/1", getxattr("/proc/1","user.x",b,8)>=0 || errno!=ENOENT);
  chk("statx /proc/1", syscall(SYS_statx,AT_FDCWD,"/proc/1",0,0xfff,&st)==0);
  chk("raw openat /proc/1/status", syscall(SYS_openat,AT_FDCWD,"/proc/1/status",O_RDONLY)>=0);
  chk("raw newfstatat /proc/1", syscall(SYS_newfstatat,AT_FDCWD,"/proc/1",&st,0)==0);
  chk("raw faccessat /proc/1", syscall(SYS_faccessat,AT_FDCWD,"/proc/1",F_OK,0)==0);
  chk("raw readlinkat /proc/1/exe", syscall(SYS_readlinkat,AT_FDCWD,"/proc/1/exe",l,sizeof l)>=0);
  chk("stat /proc/1 via symlink", (symlink("/proc/1","/tmp/plt/lnk")==0||errno==EEXIST) && stat("/tmp/plt/lnk",&st)==0);
  chk("stat /proc/self/root/proc/1", stat("/proc/self/root/proc/1",&st)==0);
  chk("stat /proc/thread-self/../1", stat("/proc/thread-self/../1",&st)==0);
  chk("stat /proc/self/cwd/../..proc/1 (cwd=/)", (chdir("/")==0) && stat("proc/1",&st)==0);
  chk("stat //proc//1", stat("//proc//1",&st)==0);
  chk("stat /proc/001 (leading zeros)", stat("/proc/001",&st)==0);
  chk("stat /proc/+1", stat("/proc/+1",&st)==0);
  chk("stat /proc/1\\n-style trailing slash dots", stat("/proc/1/.",&st)==0 || stat("/proc/1/./",&st)==0);
  chk("stat /proc/1/task/1", stat("/proc/1/task/1",&st)==0);
  chk("stat /proc/self/task/../../1", stat("/proc/self/task/../../1",&st)==0);
  chk("stat /proc/net/../1", stat("/proc/net/../1",&st)==0);
  chk("stat /proc/sys/../1", stat("/proc/sys/../1",&st)==0);
  int pd=open("/proc",O_RDONLY|O_DIRECTORY);
  chk("fstatat(procfd,\"1\")", fstatat(pd,"1",&st,0)==0);
  chk("fstatat(procfd,\"./1\")", fstatat(pd,"./1",&st,0)==0);
  chk("fstatat(procfd,\"self/../1\")", fstatat(pd,"self/../1",&st,0)==0);
  chk("openat(procfd,\"1/status\")", openat(pd,"1/status",O_RDONLY)>=0);
  chk("openat(procfd,\"../proc/1\")", openat(pd,"../proc/1",O_RDONLY)>=0);
  int dd=dup(pd); chk("fstatat(dup(procfd),\"1\")", fstatat(dd,"1",&st,0)==0);
  int fd2=open("/proc/self/fd/0",O_PATH); (void)fd2;
  chkA("kill(1,0) tells the pid exists", kill(1,0)==0 || errno==EPERM);
  chkA("sched_getaffinity(1)", sched_getaffinity(1,sizeof(cpu_set_t),&(cpu_set_t){0})==0);
  chkA("getpriority(PRIO_PROCESS,1)", (errno=0, getpriority(PRIO_PROCESS,1)!=-1 || errno==0));
  chkA("getsid(1)", getsid(1)!=-1);
  chkA("getpgid(1)", getpgid(1)!=-1);
  chkA("sched_getscheduler(1)", sched_getscheduler(1)!=-1);
  chkA("pidfd_open(1)", syscall(434,1,0)>=0);
  struct dirent *e; DIR *d=opendir("/proc"); int n=0; while(d&&(e=readdir(d))) if(e->d_name[0]>='0'&&e->d_name[0]<='9') n++;
  chk("readdir(/proc) lists pids besides ours", n>3);
  printf("pids listed: %d\n", n);
  chk("scandir(/proc)", ({struct dirent **ll; int k=scandir("/proc",&ll,NULL,NULL); int c=0; for(int i=0;i<k;i++) c+= ll[i]->d_name[0]>='0'&&ll[i]->d_name[0]<='9'; c>3;}));
  chk("/proc/self/task lists only own threads", ({DIR*t=opendir("/proc/self/task"); int c=0; while(t&&(e=readdir(t))) c+=e->d_name[0]>='0'&&e->d_name[0]<='9'; c>3;}));
  printf("leaks: %d\n", bad);
  return bad ? 1 : 0; }
