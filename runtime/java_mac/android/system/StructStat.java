// android.system.StructStat — POSIX struct stat result. AOSP:
//   https://cs.android.com/android/platform/superproject/main/+/main:libcore/luni/src/main/java/android/system/StructStat.java
// All fields are public for compatibility with AOSP's data-class style;
// callers (CleanCacheWorker) read st_mode + st_mtime + st_size.
package android.system;

public final class StructStat {
    public long st_dev;
    public long st_ino;
    public int  st_mode;
    public long st_nlink;
    public int  st_uid;
    public int  st_gid;
    public long st_rdev;
    public long st_size;
    public long st_atime;
    public long st_mtime;
    public long st_ctime;
    public long st_blksize;
    public long st_blocks;
    public long st_atime_nsec;
    public long st_mtime_nsec;
    public long st_ctime_nsec;

    public StructStat() {}
    public StructStat(long st_dev, long st_ino, int st_mode, long st_nlink,
                      int st_uid, int st_gid, long st_rdev, long st_size,
                      long st_atime, long st_mtime, long st_ctime,
                      long st_blksize, long st_blocks) {
        this.st_dev = st_dev; this.st_ino = st_ino;
        this.st_mode = st_mode; this.st_nlink = st_nlink;
        this.st_uid = st_uid; this.st_gid = st_gid;
        this.st_rdev = st_rdev; this.st_size = st_size;
        this.st_atime = st_atime; this.st_mtime = st_mtime; this.st_ctime = st_ctime;
        this.st_blksize = st_blksize; this.st_blocks = st_blocks;
    }
}
