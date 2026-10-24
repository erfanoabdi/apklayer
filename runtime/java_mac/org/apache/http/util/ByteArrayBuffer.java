package org.apache.http.util;
public final class ByteArrayBuffer {
    private byte[] buffer;
    private int len;
    public ByteArrayBuffer(int capacity) { buffer = new byte[Math.max(0, capacity)]; }
    public void append(int b) {
        if (len == buffer.length) ensureCapacity(len + 1);
        buffer[len++] = (byte) b;
    }
    public void append(byte[] b, int off, int n) {
        if (b == null || n == 0) return;
        ensureCapacity(len + n);
        System.arraycopy(b, off, buffer, len, n);
        len += n;
    }
    public byte[] toByteArray() {
        byte[] out = new byte[len];
        System.arraycopy(buffer, 0, out, 0, len);
        return out;
    }
    public int byteAt(int i) { return buffer[i] & 0xff; }
    public int capacity() { return buffer.length; }
    public int length()   { return len; }
    public void clear()   { len = 0; }
    public byte[] buffer() { return buffer; }
    public boolean isEmpty() { return len == 0; }
    public boolean isFull()  { return len == buffer.length; }
    public void setLength(int n) { this.len = n; }
    private void ensureCapacity(int needed) {
        if (needed <= buffer.length) return;
        int newCap = Math.max(buffer.length << 1, needed);
        byte[] newBuf = new byte[newCap];
        System.arraycopy(buffer, 0, newBuf, 0, len);
        buffer = newBuf;
    }
}
