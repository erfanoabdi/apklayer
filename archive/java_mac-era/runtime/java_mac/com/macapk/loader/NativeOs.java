// NativeOs -- thin JNI wrapper over Darwin libc (native_os.cpp), backing
// the raw-fd operations of android.system.Os (lseek/close/dup/sysconf/
// memfd_create/statvfs). Raw int fds cross the boundary; android.system.Os
// converts to/from java.io.FileDescriptor and throws ErrnoException.
// Results < 0 from lseek/close/dup carry -errno. See native_os.cpp.
package com.macapk.loader;

public final class NativeOs {
    private NativeOs() {}
    public static native int  open(String path, int flags, int mode);   // new fd, else -errno (flags = bionic O_*)
    public static native long lseek(int fd, long offset, int whence);   // >=0 offset, else -errno
    public static native int  close(int fd);                            // 0, else -errno
    public static native int  dup(int fd);                              // new fd, else -errno
    public static native int  dup2(int oldFd, int newFd);               // newFd, else -errno
    public static native long sysconf(int name);                        // host value (name = bionic _SC_*)
    public static native int  memfdCreate(String name, int flags);      // new fd, else -errno
    public static native long[] fstat(int fd);                          // stat fields, or null
    public static native int  ftruncate(int fd, long length);           // 0, else -errno
    public static native int  takeRunLock(String path);                 // held fd, else -errno (RunLock)
    public static native int  read(int fd, byte[] buf, int off, int count);   // bytes read (0=EOF), else -errno
    public static native int  write(int fd, byte[] buf, int off, int count);  // bytes written, else -errno

    /** Decompress an .xz / LZMA stream with macOS's own Compression framework;
     *  null if the input is not a decodable stream. Java SE ships no XZ decoder,
     *  and a Meta app packs its compiled layouts into one xz blob it addresses by
     *  offset -- see native_os.cpp's NativeOs_xzDecompress and
     *  ResourceIndex.macapkPackedBlobBytes. */
    public static native byte[] xzDecompress(byte[] in);

    // statvfs(3) / fstatvfs(3). Both exist on Darwin with a field-for-field
    // identical struct to Bionic's, so these are the host call, not an
    // emulation. Returned as long[11] in struct-declaration order --
    // {f_bsize, f_frsize, f_blocks, f_bfree, f_bavail, f_files, f_ffree,
    //  f_favail, f_fsid, f_flag, f_namemax} -- or null if the call failed.
    public static native long[] statvfs(String path);                   // fields, or null
    public static native long[] fstatvfs(int fd);                       // fields, or null

    // Process environment. These reach the REAL libc environment, not
    // java.lang.System.getenv()'s immutable JVM-startup snapshot -- native
    // code loaded through Stage 6 reads it with getenv(3), which is the only
    // reason an app calls android.system.Os.setenv at all.
    public static native int    setenv(String name, String value, boolean overwrite); // 0, else -errno
    public static native String getenv(String name);                    // value, or null
    public static native int    unsetenv(String name);                  // 0, else -errno
}
