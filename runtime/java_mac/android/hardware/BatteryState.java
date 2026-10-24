// android.hardware.BatteryState -- the battery inside an INPUT DEVICE (a game
// controller, a stylus, a Bluetooth keyboard), not the host's own battery.
// Reached through InputDevice.getBatteryState().
//
// Mirrors frameworks/base/core/java/android/hardware/BatteryState.java
// (android16-release). Abstract there and abstract here: the framework
// subclasses it per input device, and an app only ever receives an instance.
//
// The constants are deliberately routed through android.os.BatteryManager
// rather than written out as literals, exactly as AOSP does, so the two
// battery APIs can never drift apart.
//
// (The HOST's battery is a different question with a different answer:
// android.os.BatteryManager reads real values from /usr/bin/pmset. This class
// is about peripherals, and a peripheral that reports no battery is what
// isPresent() == false means.)
package android.hardware;

import android.os.BatteryManager;

public abstract class BatteryState {

    public static final int STATUS_UNKNOWN = BatteryManager.BATTERY_STATUS_UNKNOWN;
    public static final int STATUS_CHARGING = BatteryManager.BATTERY_STATUS_CHARGING;
    public static final int STATUS_DISCHARGING = BatteryManager.BATTERY_STATUS_DISCHARGING;
    public static final int STATUS_NOT_CHARGING = BatteryManager.BATTERY_STATUS_NOT_CHARGING;
    public static final int STATUS_FULL = BatteryManager.BATTERY_STATUS_FULL;

    public BatteryState() { }

    /** False when the device has no battery, or has one whose state cannot be
     *  read -- in which case getCapacity() is -1 and getStatus() is
     *  STATUS_UNKNOWN, per AOSP's javadoc. */
    public abstract boolean isPresent();

    public abstract int getStatus();

    /** 0.0 to 1.0, or -1 when unknown. Note the range: a FRACTION, not a
     *  percentage -- BatteryManager's EXTRA_LEVEL is the percentage one. */
    public abstract float getCapacity();
}
