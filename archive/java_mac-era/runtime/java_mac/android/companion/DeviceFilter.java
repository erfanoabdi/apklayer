// android.companion.DeviceFilter -- the marker interface every companion
// device filter implements (Bluetooth, BLE, Wi-Fi).
//
// A pure interface, so it is mirrored exactly. Mirrors
// frameworks/base/core/java/android/companion/DeviceFilter.java's PUBLIC
// surface: AOSP's own copy carries @hide matching methods that are not part of
// the public API, and adding them here would invent API.
package android.companion;

import android.os.Parcelable;

public interface DeviceFilter<D extends Parcelable> extends Parcelable {
}
