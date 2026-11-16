/* Reads, as the guest process, every path of the list (one per line): out.txt gets "path|lstat mode|size|readlink or dir entries|read bytes|fnv1a",
   dump/<line number> the content (<= 256 KB). Compare the out/dump of the native and the translated run (tools/ace/fsdiff.py's list = the paths ACE touches). */
#include <jni.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <dirent.h>
#include <fcntl.h>
#include <unistd.h>
#include <errno.h>
#include <sys/stat.h>
#include <sys/stat.h>

JNIEXPORT void JNICALL Java_hello_filedump_Main_run(JNIEnv *e, jclass c, jstring jl, jstring jo, jstring jd) {
  const char *l = (*e)->GetStringUTFChars(e, jl, 0), *o = (*e)->GetStringUTFChars(e, jo, 0), *d = (*e)->GetStringUTFChars(e, jd, 0);
  FILE *in = fopen(l, "r"), *out = fopen(o, "w"); char p[4096], q[4096], names[8192]; static unsigned char buf[262144]; int n = 0;
  mkdir(d, 0777);
  if (!in || !out) return;
  while (fgets(p, sizeof p, in)) {
    p[strcspn(p, "\n")] = 0; struct stat st; names[0] = 0; long rd = -1; unsigned long h = 0xcbf29ce484222325UL; n++;
    if (lstat(p, &st) != 0) { fprintf(out, "%s|ERR %d\n", p, errno); continue; }
    if (S_ISLNK(st.st_mode)) { ssize_t k = readlink(p, names, sizeof names - 1); names[k > 0 ? k : 0] = 0; }
    else if (S_ISDIR(st.st_mode)) { DIR *dd = opendir(p); struct dirent *de; size_t u = 0;
      while (dd && (de = readdir(dd)) && u < sizeof names - 300) u += snprintf(names + u, 300, "%s,", de->d_name); if (dd) closedir(dd); }
    else { int fd = open(p, O_RDONLY | O_NONBLOCK); if (fd >= 0) { rd = 0; ssize_t k; while (rd < (long)sizeof buf && (k = read(fd, buf + rd, sizeof buf - rd)) > 0) rd += k; close(fd);
      for (long i = 0; i < rd; i++) h = (h ^ buf[i]) * 0x100000001b3UL;
      snprintf(q, sizeof q, "%s/%d", d, n); FILE *f = fopen(q, "w"); if (f) { fwrite(buf, 1, rd, f); fclose(f); } } else rd = -errno; }
    fprintf(out, "%s|%o|%lld|%s|%ld|%lx\n", p, st.st_mode, (long long)st.st_size, names, rd, h);
  }
  fclose(out); fclose(in);
}
