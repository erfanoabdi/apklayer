// android.system.OsConstants — symbolic POSIX errno + mode bits. AOSP:
//   https://cs.android.com/android/platform/superproject/main/+/main:libcore/luni/src/main/java/android/system/OsConstants.java
// We expose only the constants F-Droid (and a handful of other APKs)
// actually reference; this matches the standard Linux values and is
// host-independent.
package android.system;

public final class OsConstants {
    private OsConstants() {}

    // errno
    public static final int EPERM   = 1;
    public static final int ENOENT  = 2;
    public static final int EIO     = 5;
    public static final int EACCES  = 13;
    public static final int EEXIST  = 17;
    public static final int ENOTDIR = 20;
    public static final int EISDIR  = 21;
    public static final int EINVAL  = 22;
    public static final int ENOSPC  = 28;

    // open(2) flags. AOSP's OsConstants exposes the *bionic* O_* values (its
    // native initConstants() reads them straight from bionic's fcntl.h), so an
    // app reads these and passes them to Os.open, which translates them to
    // Darwin's before open(2) (see runtime/src/native_os.cpp NativeOs_open).
    // Values: aosp-mirror/platform_bionic libc/kernel/uapi/asm-generic/fcntl.h
    // (aarch64 uses the asm-generic set). Instagram's InstagramAppShell
    // .attachBaseContext reads O_RDONLY very early; without the field the
    // NoSuchFieldError aborts attachBaseContext, so SoLoader/NativeLoader.init
    // never runs and the entire app DI (IgSessionManager, IgExecutor) cascades.
    public static final int O_ACCMODE   = 00000003;
    public static final int O_RDONLY    = 00000000;
    public static final int O_WRONLY    = 00000001;
    public static final int O_RDWR      = 00000002;
    public static final int O_CREAT     = 00000100;
    public static final int O_EXCL      = 00000200;
    public static final int O_NOCTTY    = 00000400;
    public static final int O_TRUNC     = 00001000;
    public static final int O_APPEND    = 00002000;
    public static final int O_NONBLOCK  = 00004000;
    public static final int O_DSYNC     = 00010000;
    public static final int O_DIRECT    = 00040000;
    public static final int O_LARGEFILE = 00100000;
    public static final int O_DIRECTORY = 00200000;
    public static final int O_NOFOLLOW  = 00400000;
    public static final int O_CLOEXEC   = 02000000;
    public static final int O_SYNC      = 04010000; // __O_SYNC | O_DSYNC
    public static final int O_PATH      = 010000000;

    // st_mode masks
    public static final int S_IFMT   = 0170000;
    public static final int S_IFDIR  = 0040000;
    public static final int S_IFCHR  = 0020000;
    public static final int S_IFBLK  = 0060000;
    public static final int S_IFREG  = 0100000;
    public static final int S_IFIFO  = 0010000;
    public static final int S_IFLNK  = 0120000;
    public static final int S_IFSOCK = 0140000;

    // mode helpers used by F-Droid's StorageUtils
    public static boolean S_ISDIR (int mode) { return (mode & S_IFMT) == S_IFDIR;  }
    public static boolean S_ISCHR (int mode) { return (mode & S_IFMT) == S_IFCHR;  }
    public static boolean S_ISBLK (int mode) { return (mode & S_IFMT) == S_IFBLK;  }
    public static boolean S_ISREG (int mode) { return (mode & S_IFMT) == S_IFREG;  }
    public static boolean S_ISFIFO(int mode) { return (mode & S_IFMT) == S_IFIFO;  }
    public static boolean S_ISLNK (int mode) { return (mode & S_IFMT) == S_IFLNK;  }
    public static boolean S_ISSOCK(int mode) { return (mode & S_IFMT) == S_IFSOCK; }

    public static final int SEEK_SET = 0;
    public static final int SEEK_CUR = 1;
    public static final int SEEK_END = 2;

    public static final int _SC_CLK_TCK = 6;
    public static final int _SC_PAGE_SIZE = 40;
    public static final int _SC_PAGESIZE = 39;
    public static final int _SC_NPROCESSORS_CONF = 96;
    public static final int _SC_NPROCESSORS_ONLN = 97;
    public static final int ENOSYS = 38;
    public static final int EOPNOTSUPP = 95;
    public static final int EBADF = 9;
}



