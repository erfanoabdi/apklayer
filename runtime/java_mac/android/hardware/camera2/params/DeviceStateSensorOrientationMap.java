// android.hardware.camera2.params.DeviceStateSensorOrientationMap -- maps a
// foldable's device state to the sensor orientation that applies in it. Value
// type of CameraCharacteristics.INFO_DEVICE_STATE_SENSOR_ORIENTATION_MAP.
// API 32.
//
// Mirrors frameworks/base/core/java/android/hardware/camera2/params/
// DeviceStateSensorOrientationMap.java (aosp-mirror, android16-release). The
// long[] constructor is @hide in AOSP (the public route is Builder), so it is
// package-private here.
package android.hardware.camera2.params;

import android.hardware.camera2.utils.HashCodeHelpers;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Objects;

public final class DeviceStateSensorOrientationMap {

    /** AOSP :48 -- the default, unfolded state. */
    public static final long NORMAL = 0;
    /** AOSP :53 -- 1 << 2. */
    public static final long FOLDED = 1 << 2;

    private final HashMap<Long, Integer> mDeviceStateOrientationMap;
    private final long[] mElements;

    /** AOSP :77 -- flat (state, orientation) pairs; an odd length or an
     *  orientation that is not a multiple of 90 is IllegalArgumentException. */
    DeviceStateSensorOrientationMap(long[] elements) {
        mElements = Objects.requireNonNull(elements, "elements must not be null");
        mDeviceStateOrientationMap = new HashMap<>();
        if ((elements.length % 2) != 0) {
            throw new IllegalArgumentException("Device state sensor orientation map length "
                    + elements.length + " is not even!");
        }
        for (int i = 0; i < elements.length; i += 2) {
            if ((elements[i + 1] % 90) != 0) {
                throw new IllegalArgumentException("Sensor orientation not divisible by 90: "
                        + elements[i + 1]);
            }
            mDeviceStateOrientationMap.put(elements[i], Math.toIntExact(elements[i + 1]));
        }
    }

    private DeviceStateSensorOrientationMap(ArrayList<Long> elements,
            HashMap<Long, Integer> deviceStateOrientationMap) {
        mElements = new long[elements.size()];
        for (int i = 0; i < elements.size(); i++) {
            mElements[i] = elements.get(i);
        }
        mDeviceStateOrientationMap = deviceStateOrientationMap;
    }

    /** AOSP :113 -- an unmapped device state throws rather than returning a
     *  default, because a wrong orientation silently rotates every frame. */
    public int getSensorOrientation(long deviceState) {
        if (!mDeviceStateOrientationMap.containsKey(deviceState)) {
            throw new IllegalArgumentException("Invalid device state: " + deviceState);
        }
        return mDeviceStateOrientationMap.get(deviceState);
    }

    @Override public boolean equals(Object obj) {
        if (obj == null) return false;
        if (this == obj) return true;
        if (obj instanceof DeviceStateSensorOrientationMap) {
            return Arrays.equals(mElements, ((DeviceStateSensorOrientationMap) obj).mElements);
        }
        return false;
    }

    @Override public int hashCode() {
        return HashCodeHelpers.hashCodeGeneric(mElements);
    }

    /** AOSP :160. */
    public static final class Builder {
        private final ArrayList<Long> mElements = new ArrayList<>();
        private final HashMap<Long, Integer> mDeviceStateOrientationMap = new HashMap<>();

        public Builder() {}

        public Builder addOrientationForState(long deviceState, long angle) {
            if (angle % 90 != 0) {
                throw new IllegalArgumentException(
                        "Sensor orientation not divisible by 90: " + angle);
            }
            mDeviceStateOrientationMap.put(deviceState, Math.toIntExact(angle));
            mElements.add(deviceState);
            mElements.add(angle);
            return this;
        }

        public DeviceStateSensorOrientationMap build() {
            if (mElements.size() == 0) {
                throw new IllegalStateException("Cannot build a map with 0 elements!");
            }
            return new DeviceStateSensorOrientationMap(mElements, mDeviceStateOrientationMap);
        }
    }
}
