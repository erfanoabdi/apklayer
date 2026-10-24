// android.os.MemoryFile -- shared-memory region backed by ashmem on Android.
// Mac.apk has no ashmem and no cross-process consumer, so we back it with a
// plain byte[] of the requested length. Single-process apps that use MemoryFile
// as a scratch buffer (image decode staging, IPC-style handoff within the app)
// get correct read/write/stream behavior.
//
// AOSP ref: frameworks/base/core/java/android/os/MemoryFile.java
package android.os;

public class MemoryFile {
    private byte[] mBuffer;
    private final int mLength;
    private boolean mAllowPurging = false;
    private boolean mClosed = false;

    public MemoryFile(String name, int length) throws java.io.IOException {
        if (length < 0) throw new java.io.IOException("MemoryFile length must be non-negative");
        mLength = length;
        mBuffer = new byte[length];
    }

    public void close() {
        mClosed = true;
        mBuffer = null;
    }

    public int length() { return mLength; }

    public boolean isPurgingAllowed() { return mAllowPurging; }

    // Returns the previous state. No real purging without ashmem; the flag is
    // tracked so the getter is honest.
    public synchronized boolean allowPurging(boolean allowPurging) throws java.io.IOException {
        boolean oldValue = mAllowPurging;
        mAllowPurging = allowPurging;
        return oldValue;
    }

    public java.io.InputStream getInputStream() {
        return new MemoryInputStream();
    }

    public java.io.OutputStream getOutputStream() {
        return new MemoryOutputStream();
    }

    public int readBytes(byte[] buffer, int srcOffset, int destOffset, int count)
            throws java.io.IOException {
        if (mClosed || mBuffer == null) throw new java.io.IOException("MemoryFile has been purged");
        if (srcOffset < 0 || srcOffset + count > mLength || count < 0) {
            throw new IndexOutOfBoundsException();
        }
        System.arraycopy(mBuffer, srcOffset, buffer, destOffset, count);
        return count;
    }

    public void writeBytes(byte[] buffer, int srcOffset, int destOffset, int count)
            throws java.io.IOException {
        if (mClosed || mBuffer == null) throw new java.io.IOException("MemoryFile has been purged");
        if (destOffset < 0 || destOffset + count > mLength || count < 0) {
            throw new IndexOutOfBoundsException();
        }
        System.arraycopy(buffer, srcOffset, mBuffer, destOffset, count);
    }

    private final class MemoryInputStream extends java.io.InputStream {
        private int mMark = 0;
        private int mOffset = 0;
        private final byte[] mSingleByte = new byte[1];

        @Override public int available() { return mLength - mOffset; }
        @Override public boolean markSupported() { return false; }
        @Override public void mark(int readlimit) { mMark = mOffset; }
        @Override public void reset() { mOffset = mMark; }

        @Override public int read() throws java.io.IOException {
            int result = read(mSingleByte, 0, 1);
            if (result != 1) return -1;
            return mSingleByte[0] & 0xff;
        }

        @Override public int read(byte[] buffer, int offset, int count) throws java.io.IOException {
            if (offset < 0 || count < 0 || offset + count > buffer.length) {
                throw new IndexOutOfBoundsException();
            }
            if (count < 1) return 0;
            if (mOffset >= mLength) return -1;
            if (mOffset + count > mLength) count = mLength - mOffset;
            readBytes(buffer, mOffset, offset, count);
            mOffset += count;
            return count;
        }

        @Override public long skip(long n) {
            if (mOffset + n > mLength) n = mLength - mOffset;
            mOffset += n;
            return n;
        }
    }

    private final class MemoryOutputStream extends java.io.OutputStream {
        private int mOffset = 0;
        private final byte[] mSingleByte = new byte[1];

        @Override public void write(byte[] buffer, int offset, int count) throws java.io.IOException {
            writeBytes(buffer, offset, mOffset, count);
            mOffset += count;
        }

        @Override public void write(int oneByte) throws java.io.IOException {
            mSingleByte[0] = (byte) oneByte;
            write(mSingleByte, 0, 1);
        }
    }
}
