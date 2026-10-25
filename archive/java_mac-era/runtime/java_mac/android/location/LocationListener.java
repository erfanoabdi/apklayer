package android.location;
import android.os.Bundle;
public interface LocationListener {
    default void onLocationChanged(Location location) {}
    default void onProviderDisabled(String provider) {}
    /**
     * AOSP LocationListener.onFlushComplete (API 31): invoked once a flush
     * requested via LocationManager.requestFlush has finished and any flushed
     * locations have been delivered.
     * Source: frameworks/base/location/java/android/location/LocationListener.java
     */
    default void onFlushComplete(int requestCode) {}
    default void onProviderEnabled(String provider) {}
    default void onStatusChanged(String provider, int status, Bundle extras) {}
}
