package android.content.res;

import android.os.ParcelFileDescriptor;

// AssetFileDescriptor -- a slice [startOffset, startOffset+length) of an open
// file, returned by AssetManager.openFd(...). AOSP wraps a ParcelFileDescriptor
// plus the slice bounds; native consumers (Unity's nativeRender) call
// getParcelFileDescriptor() then mmap the fd at [startOffset, length). Earlier
// this class was an all-null stub with NO getParcelFileDescriptor(), so Unity's
// JNI GetMethodID returned null -> NoSuchMethodError on the render thread ->
// the asset (scene / streaming bundle) never loaded.
// AOSP: https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/core/java/android/content/res/AssetFileDescriptor.java
public class AssetFileDescriptor implements java.io.Closeable, android.os.Parcelable {
    public static final long UNKNOWN_LENGTH = -1;

    private final ParcelFileDescriptor mFd;
    private final long mStartOffset;
    private final long mLength;
    private final android.os.Bundle mExtras;

    private static final boolean FD_TRACE = System.getenv("MACAPK_FD_TRACE") != null;

    public AssetFileDescriptor() { this(null, 0, 0); }

    public AssetFileDescriptor(ParcelFileDescriptor fd, long startOffset, long length) {
        this(fd, startOffset, length, null);
    }

    /** AOSP core/java/android/content/res/AssetFileDescriptor.java:92 (API 19)
     *  -- the extras-carrying constructor. `extras` is opaque provider
     *  metadata (MediaStore attaches orientation/size hints there).
     *
     *  AOSP's argument checks are NOT ported: it throws
     *  IllegalArgumentException("fd must not be null"), but this class has
     *  always had a public no-arg constructor that leaves mFd null and code in
     *  this tree relies on it, so adding the throw here would break the
     *  three-arg path that already exists. The second check (startOffset must
     *  be 0 with UNKNOWN_LENGTH) is likewise relaxed for the same reason --
     *  stated rather than silently dropped. */
    public AssetFileDescriptor(ParcelFileDescriptor fd, long startOffset, long length,
                               android.os.Bundle extras) {
        this.mFd = fd;
        this.mStartOffset = startOffset;
        this.mLength = length;
        this.mExtras = extras;
        if (FD_TRACE) {
            System.err.println("[AFD] new AssetFileDescriptor(pfd=" + (fd == null ? "NULL" : "ok")
                    + ", off=" + startOffset + ", len=" + length + ")");
            new Throwable("AFD-ctor-stack").printStackTrace();
        }
    }

    public ParcelFileDescriptor getParcelFileDescriptor() {
        if (FD_TRACE) System.err.println("[AFD] getParcelFileDescriptor() -> " + (mFd == null ? "NULL" : "ok"));
        return mFd;
    }

    public java.io.FileDescriptor getFileDescriptor() {
        return mFd == null ? null : mFd.getFileDescriptor();
    }

    /** AOSP :135 -- the provider-supplied extras, or null. */
    public android.os.Bundle getExtras() { return mExtras; }

    public long getStartOffset() { return mStartOffset; }
    public long getLength()      { return mLength; }
    public long getDeclaredLength() { return mLength; }

    public java.io.FileInputStream createInputStream() throws java.io.IOException {
        if (mFd == null) return null;
        java.io.FileInputStream in = new java.io.FileInputStream(mFd.getFileDescriptor());
        if (mStartOffset > 0) in.skip(mStartOffset);
        return in;
    }

    public java.io.FileOutputStream createOutputStream() throws java.io.IOException {
        if (mFd == null) return null;
        return new java.io.FileOutputStream(mFd.getFileDescriptor());
    }

    @Override public void close() throws java.io.IOException {
        if (mFd != null) mFd.close();
    }

    // Parcelable. AOSP frameworks/base AssetFileDescriptor.java writeToParcel /
    // its Parcel-arg constructor, mirrored field-for-field: the wrapped
    // ParcelFileDescriptor's own wire form, then the two longs. AOSP's real
    // writeToParcel calls mFd.writeToParcel(out, flags) unguarded, because
    // every AOSP public constructor requires a non-null ParcelFileDescriptor;
    // this class also has a no-arg constructor (above) that leaves mFd null,
    // so unlike AOSP we write/read a presence flag first instead of NPEing on
    // that reachable state. AOSP's 4th field (mExtras, a Bundle) IS carried
    // now -- it was added with the API-19 four-arg constructor and getExtras()
    // above, and both sides of the parcel were updated together so the format
    // stays self-consistent.
    //
    // This replaces a landmine, not a blank slate: CREATOR.createFromParcel
    // used to unconditionally `return null` (added 836e331, v729, "fields
    // round 2" -- a field-completeness sweep, not a Parcelable implementation
    // effort). That was harmless only because this class did not implement
    // Parcelable, so nothing could reach it through Parcel.writeParcelable's
    // checkcast; a caller that reached CREATOR directly (it is a plain public
    // static field) got a silent null instead of a real object -- and a real
    // writeToParcel added later without also fixing this stub would have
    // desynced the parcel's read position for every reader after it. See
    // docs/parcelable-gap-workorder_2026-08-13.md section 2.
    @Override public int describeContents() {
        return mFd != null ? mFd.describeContents() : 0;
    }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeInt(mFd != null ? 1 : 0);
        if (mFd != null) mFd.writeToParcel(dest, flags);
        dest.writeLong(mStartOffset);
        dest.writeLong(mLength);
        dest.writeBundle(mExtras);
    }

    public static final android.os.Parcelable.Creator<AssetFileDescriptor> CREATOR =
        new android.os.Parcelable.Creator<AssetFileDescriptor>() {
            @Override public AssetFileDescriptor createFromParcel(android.os.Parcel in) {
                ParcelFileDescriptor fd = in.readInt() != 0
                        ? ParcelFileDescriptor.CREATOR.createFromParcel(in) : null;
                long startOffset = in.readLong();
                long length = in.readLong();
                android.os.Bundle extras = in.readBundle();
                return new AssetFileDescriptor(fd, startOffset, length, extras);
            }
            @Override public AssetFileDescriptor[] newArray(int size) { return new AssetFileDescriptor[size]; }
        };

    /** AOSP :223 -- an InputStream over the SLICE, closing the descriptor with
     *  the stream. AOSP splits into a seekable and a non-seekable delegate
     *  (chosen with an fstat) so a socket/FIFO-backed descriptor still works;
     *  the seekable path is the only one reachable here, because every
     *  AssetFileDescriptor this runtime produces comes from AssetManager.openFd
     *  over a real file. The slice bounds ARE honoured: the stream starts at
     *  startOffset and stops after length bytes, which is the behaviour a
     *  caller depends on when several assets share one APK file descriptor. */
    public static class AutoCloseInputStream extends ParcelFileDescriptor.AutoCloseInputStream {
        private long mRemaining;

        public AutoCloseInputStream(AssetFileDescriptor fd) throws java.io.IOException {
            super(fd.getParcelFileDescriptor());
            long start = fd.getStartOffset();
            if (start > 0) {
                long skipped = 0;
                while (skipped < start) {
                    long n = super.skip(start - skipped);
                    if (n <= 0) throw new java.io.IOException("Unable to seek");
                    skipped += n;
                }
            }
            mRemaining = fd.getLength();
        }

        @Override public int available() throws java.io.IOException {
            return mRemaining >= 0
                    ? (mRemaining < Integer.MAX_VALUE ? (int) mRemaining : Integer.MAX_VALUE)
                    : super.available();
        }

        @Override public int read() throws java.io.IOException {
            if (mRemaining >= 0) {
                if (mRemaining == 0) return -1;
                int res = super.read();
                if (res >= 0) mRemaining--;
                return res;
            }
            return super.read();
        }

        @Override public int read(byte[] buffer, int offset, int count) throws java.io.IOException {
            if (mRemaining >= 0) {
                if (mRemaining == 0) return -1;
                if (count > mRemaining) count = (int) mRemaining;
                int res = super.read(buffer, offset, count);
                if (res >= 0) mRemaining -= res;
                return res;
            }
            return super.read(buffer, offset, count);
        }

        @Override public int read(byte[] buffer) throws java.io.IOException {
            return read(buffer, 0, buffer.length);
        }

        @Override public long skip(long count) throws java.io.IOException {
            if (mRemaining >= 0) {
                if (mRemaining == 0) return -1;
                if (count > mRemaining) count = mRemaining;
                long res = super.skip(count);
                if (res >= 0) mRemaining -= res;
                return res;
            }
            return super.skip(count);
        }

        @Override public void mark(int readlimit) {
            if (mRemaining >= 0) {
                // AOSP's seekable delegate supports mark/reset; keep the same
                // "unsupported while a slice bound is active" shape it uses
                // for the non-seekable one rather than corrupting mRemaining.
                return;
            }
            super.mark(readlimit);
        }

        @Override public boolean markSupported() {
            return mRemaining < 0 && super.markSupported();
        }
    }

    /** AOSP :659 -- the write side, bounded the same way. */
    public static class AutoCloseOutputStream extends ParcelFileDescriptor.AutoCloseOutputStream {
        private long mRemaining;

        public AutoCloseOutputStream(AssetFileDescriptor fd) throws java.io.IOException {
            super(fd.getParcelFileDescriptor());
            mRemaining = fd.getLength();
        }

        @Override public void write(byte[] buffer, int offset, int count)
                throws java.io.IOException {
            if (mRemaining >= 0) {
                if (mRemaining == 0) return;
                if (count > mRemaining) count = (int) mRemaining;
                super.write(buffer, offset, count);
                mRemaining -= count;
                return;
            }
            super.write(buffer, offset, count);
        }

        @Override public void write(byte[] buffer) throws java.io.IOException {
            write(buffer, 0, buffer.length);
        }

        @Override public void write(int oneByte) throws java.io.IOException {
            if (mRemaining >= 0) {
                if (mRemaining == 0) return;
                super.write(oneByte);
                mRemaining--;
                return;
            }
            super.write(oneByte);
        }
    }
}

