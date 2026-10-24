// android.gesture.GesturePoint -- one timed point of a gesture stroke.
// Mirrors AOSP frameworks/base/core/java/android/gesture/GesturePoint.java
// declaration-for-declaration (aosp-mirror android16-release, fetched
// 2026-08-24). The public fields are part of the ABI: apps read p.x / p.y /
// p.timestamp directly, and GestureStroke's constructor walks them.
package android.gesture;

import java.io.DataInputStream;
import java.io.IOException;

public class GesturePoint {
    public final float x;
    public final float y;

    public final long timestamp;

    public GesturePoint(float x, float y, long t) {
        this.x = x;
        this.y = y;
        timestamp = t;
    }

    static GesturePoint deserialize(DataInputStream in) throws IOException {
        // Read X and Y
        final float x = in.readFloat();
        final float y = in.readFloat();
        // Read timestamp
        final long timeStamp = in.readLong();
        return new GesturePoint(x, y, timeStamp);
    }

    @Override
    public Object clone() {
        return new GesturePoint(x, y, timestamp);
    }
}
