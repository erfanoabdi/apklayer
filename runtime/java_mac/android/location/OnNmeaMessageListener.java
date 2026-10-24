package android.location;

/**
 * Callback for raw NMEA sentences from the GNSS engine.
 *
 * A Mac has no GNSS engine, so LocationManager.addNmeaListener registers the
 * listener and never calls it -- which is the same observable behaviour as a
 * device with location off or no fix. AOSP declares this as an INTERFACE (not
 * an abstract class), so it must stay one or implementing classes fail to link.
 *
 * Source: frameworks/base/location/java/android/location/OnNmeaMessageListener.java
 */
public interface OnNmeaMessageListener {
    void onNmeaMessage(String message, long timestamp);
}
