package android.media;

import android.os.Handler;

/**
 * Interface implemented by AudioTrack/AudioRecord for output/input device
 * routing. On Mac.apk there is one synthetic output device; getRoutedDevice
 * reports it and routing never changes, so listeners are retained but never
 * fire -- the same observable result as a device whose route is fixed.
 *
 * Source: frameworks/base/media/java/android/media/AudioRouting.java
 */
public interface AudioRouting {

    AudioDeviceInfo getRoutedDevice();

    void addOnRoutingChangedListener(OnRoutingChangedListener listener, Handler handler);

    void removeOnRoutingChangedListener(OnRoutingChangedListener listener);

    // AOSP returns boolean ("true if successful"). AudioTrack already returns
    // boolean here, so the interface was the half that disagreed with the SDK.
    // frameworks/base/media/java/android/media/AudioRouting.java
    boolean setPreferredDevice(AudioDeviceInfo deviceInfo);

    AudioDeviceInfo getPreferredDevice();

    /**
     * API 35. The full set of devices this routing is going to, which on a
     * device with route groups can be more than one. AOSP declares it as a
     * DEFAULT method returning a singleton of getRoutedDevice() (or the empty
     * list when that is null), so every existing implementor keeps working --
     * mirrored here verbatim rather than forcing each class to repeat it.
     */
    default java.util.List<AudioDeviceInfo> getRoutedDevices() {
        AudioDeviceInfo routed = getRoutedDevice();
        return (routed == null) ? java.util.Collections.<AudioDeviceInfo>emptyList()
                : java.util.Collections.singletonList(routed);
    }

    interface OnRoutingChangedListener {
        void onRoutingChanged(AudioRouting router);
    }
}
