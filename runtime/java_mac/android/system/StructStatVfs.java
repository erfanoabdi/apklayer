// android.system.StructStatVfs -- POSIX struct statvfs result. AOSP:
//   https://android.googlesource.com/platform/libcore/+/refs/heads/main/luni/src/main/java/android/system/StructStatVfs.java
// Field names, order, types and the single 11-arg constructor are copied
// verbatim from that file; every field is public final there, so callers read
// them directly (there are no getters to mirror).
//
// AOSP's toString() delegates to libcore.util.Objects, which reflects over the
// fields and prints "StructStatVfs[f_bsize=...,f_frsize=...]". We are not
// carrying that helper in, so toString is written out longhand to the same
// shape rather than left as Object's identity hash -- an app that logs a
// StructStatVfs should see the numbers, not "android.system.StructStatVfs@1f".
package android.system;

public final class StructStatVfs {
    public final long f_bsize;    /* unsigned long -- file system block size */
    public final long f_frsize;   /* unsigned long -- fundamental block size */
    public final long f_blocks;   /* fsblkcnt_t -- blocks on FS in f_frsize units */
    public final long f_bfree;    /* fsblkcnt_t -- free blocks */
    public final long f_bavail;   /* fsblkcnt_t -- free blocks available to non-root */
    public final long f_files;    /* fsfilcnt_t -- total inodes */
    public final long f_ffree;    /* fsfilcnt_t -- free inodes */
    public final long f_favail;   /* fsfilcnt_t -- free inodes available to non-root */
    public final long f_fsid;     /* unsigned long -- filesystem id */
    public final long f_flag;     /* unsigned long -- ST_* bit mask */
    public final long f_namemax;  /* unsigned long -- max filename length */

    public StructStatVfs(long f_bsize, long f_frsize, long f_blocks, long f_bfree,
                         long f_bavail, long f_files, long f_ffree, long f_favail,
                         long f_fsid, long f_flag, long f_namemax) {
        this.f_bsize = f_bsize;
        this.f_frsize = f_frsize;
        this.f_blocks = f_blocks;
        this.f_bfree = f_bfree;
        this.f_bavail = f_bavail;
        this.f_files = f_files;
        this.f_ffree = f_ffree;
        this.f_favail = f_favail;
        this.f_fsid = f_fsid;
        this.f_flag = f_flag;
        this.f_namemax = f_namemax;
    }

    @Override public String toString() {
        return "StructStatVfs[f_bsize=" + f_bsize + ",f_frsize=" + f_frsize
                + ",f_blocks=" + f_blocks + ",f_bfree=" + f_bfree
                + ",f_bavail=" + f_bavail + ",f_files=" + f_files
                + ",f_ffree=" + f_ffree + ",f_favail=" + f_favail
                + ",f_fsid=" + f_fsid + ",f_flag=" + f_flag
                + ",f_namemax=" + f_namemax + "]";
    }
}
