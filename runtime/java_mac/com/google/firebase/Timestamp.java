package com.google.firebase;

import java.util.Date;

// com.google.firebase.Timestamp -- the immutable seconds+nanos timestamp value
// type shared by Firestore (and other Firebase SDKs). Pure client-side value
// object (faithful); no backend involved. Mirrors the published API: seconds
// since the Unix epoch plus a fractional nanosecond component in [0, 1e9).
public final class Timestamp implements Comparable<Timestamp> {
    private final long seconds;
    private final int nanoseconds;

    public Timestamp(long seconds, int nanoseconds) {
        if (nanoseconds < 0 || nanoseconds >= 1_000_000_000) {
            throw new IllegalArgumentException("Timestamp nanoseconds out of range: " + nanoseconds);
        }
        // Valid Firestore range is years 1-9999; mirror the published bound.
        if (seconds < -62_135_596_800L || seconds > 253_402_300_799L) {
            throw new IllegalArgumentException("Timestamp seconds out of range: " + seconds);
        }
        this.seconds = seconds;
        this.nanoseconds = nanoseconds;
    }

    public Timestamp(Date date) {
        this(date.getTime() / 1000, (int) ((date.getTime() % 1000) * 1_000_000));
    }

    public static Timestamp now() {
        return new Timestamp(new Date());
    }

    public long getSeconds() { return seconds; }

    public int getNanoseconds() { return nanoseconds; }

    public Date toDate() {
        return new Date(seconds * 1000 + nanoseconds / 1_000_000);
    }

    @Override
    public int compareTo(Timestamp other) {
        int c = Long.compare(seconds, other.seconds);
        return c == 0 ? Integer.compare(nanoseconds, other.nanoseconds) : c;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Timestamp)) return false;
        Timestamp t = (Timestamp) o;
        return seconds == t.seconds && nanoseconds == t.nanoseconds;
    }

    @Override
    public int hashCode() {
        int result = (int) (seconds ^ (seconds >>> 32));
        result = 31 * result + nanoseconds;
        return result;
    }

    @Override
    public String toString() {
        return "Timestamp(seconds=" + seconds + ", nanoseconds=" + nanoseconds + ")";
    }
}
