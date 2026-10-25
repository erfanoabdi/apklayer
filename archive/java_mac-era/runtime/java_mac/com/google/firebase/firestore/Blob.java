package com.google.firebase.firestore;

import java.util.Arrays;

// com.google.firebase.firestore.Blob -- immutable byte-array value type used
// for binary fields. Pure client-side value object (faithful, no backend).
public class Blob implements Comparable<Blob> {
    private final byte[] bytes;

    private Blob(byte[] bytes) { this.bytes = bytes; }

    // fromBytes copies the input, matching the SDK's immutability contract.
    public static Blob fromBytes(byte[] bytes) {
        return new Blob(Arrays.copyOf(bytes, bytes.length));
    }

    public byte[] toBytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }

    @Override
    public int compareTo(Blob other) {
        int size = Math.min(bytes.length, other.bytes.length);
        for (int i = 0; i < size; i++) {
            // Firestore compares blobs byte-by-byte as unsigned values.
            int cmp = Integer.compare(bytes[i] & 0xff, other.bytes[i] & 0xff);
            if (cmp != 0) return cmp;
        }
        return Integer.compare(bytes.length, other.bytes.length);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Blob)) return false;
        return Arrays.equals(bytes, ((Blob) o).bytes);
    }

    @Override
    public int hashCode() { return Arrays.hashCode(bytes); }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Blob { bytes=");
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.append(" }").toString();
    }
}
