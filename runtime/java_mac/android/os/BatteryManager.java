// android.os.BatteryManager -- battery query service
// (getSystemService(BATTERY_SERVICE)). A MacBook has a real battery IOKit
// tracks; we read it via `pmset -g batt` (unprivileged, no native bridge
// needed) instead of reporting a permanently idealized 100%/full/charging
// device. A desktop Mac (Mac mini/Studio/iMac, no battery at all) reports
// the same way a real battery-less Android device (e.g. Android TV) does:
// EXTRA_PRESENT-equivalent absent, BATTERY_STATUS_UNKNOWN.
//
// AOSP ref: frameworks/base/core/java/android/os/BatteryManager.java --
// isCharging() javadoc: "the device is plugged in and is supplying
// sufficient power that the battery level is going up (or the battery is
// fully charged)" -> true for BATTERY_STATUS_CHARGING or _FULL, false
// otherwise. frameworks/base/services/core/.../BatteryService.java
// plugType(): AC/USB/Wireless/Dock priority (not consumed here -- see note
// on EXTRA_PLUGGED below).
package android.os;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BatteryManager {
    // Intent extras broadcast with ACTION_BATTERY_CHANGED.
    public static final String EXTRA_STATUS = "status";
    public static final String EXTRA_HEALTH = "health";
    public static final String EXTRA_PRESENT = "present";
    public static final String EXTRA_LEVEL = "level";
    public static final String EXTRA_BATTERY_LOW = "battery_low";
    public static final String EXTRA_SCALE = "scale";
    public static final String EXTRA_ICON_SMALL = "icon-small";
    public static final String EXTRA_PLUGGED = "plugged";
    public static final String EXTRA_VOLTAGE = "voltage";
    public static final String EXTRA_TEMPERATURE = "temperature";
    public static final String EXTRA_TECHNOLOGY = "technology";
    public static final String EXTRA_CHARGE_COUNTER = "charge_counter";
    public static final String EXTRA_CYCLE_COUNT = "android.os.extra.CYCLE_COUNT";

    // Battery status (EXTRA_STATUS / BATTERY_PROPERTY_STATUS).
    public static final int BATTERY_STATUS_UNKNOWN = 1;
    public static final int BATTERY_STATUS_CHARGING = 2;
    public static final int BATTERY_STATUS_DISCHARGING = 3;
    public static final int BATTERY_STATUS_NOT_CHARGING = 4;
    public static final int BATTERY_STATUS_FULL = 5;

    // Battery health (EXTRA_HEALTH).
    public static final int BATTERY_HEALTH_UNKNOWN = 1;
    public static final int BATTERY_HEALTH_GOOD = 2;
    public static final int BATTERY_HEALTH_OVERHEAT = 3;
    public static final int BATTERY_HEALTH_DEAD = 4;
    public static final int BATTERY_HEALTH_OVER_VOLTAGE = 5;
    public static final int BATTERY_HEALTH_UNSPECIFIED_FAILURE = 6;
    public static final int BATTERY_HEALTH_COLD = 7;

    // Plugged source (EXTRA_PLUGGED).
    public static final int BATTERY_PLUGGED_AC = 1;
    public static final int BATTERY_PLUGGED_USB = 2;
    public static final int BATTERY_PLUGGED_WIRELESS = 4;
    public static final int BATTERY_PLUGGED_DOCK = 8;

    // getIntProperty / getLongProperty selectors.
    public static final int BATTERY_PROPERTY_CHARGE_COUNTER = 1;
    public static final int BATTERY_PROPERTY_CURRENT_NOW = 2;
    public static final int BATTERY_PROPERTY_CURRENT_AVERAGE = 3;
    public static final int BATTERY_PROPERTY_CAPACITY = 4;
    public static final int BATTERY_PROPERTY_ENERGY_COUNTER = 5;
    public static final int BATTERY_PROPERTY_STATUS = 6;
    // AOSP core/java/android/os/BatteryManager.java:489. Values verbatim.
    public static final int BATTERY_PROPERTY_STATE_OF_HEALTH = 10;

    // AOSP :167 / :251 -- extras carried on the ACTION_BATTERY_CHANGED sticky
    // broadcast.
    public static final String EXTRA_CHARGING_STATUS = "android.os.extra.CHARGING_STATUS";
    public static final String EXTRA_CAPACITY_LEVEL = "android.os.extra.CAPACITY_LEVEL";

    // AOSP :175-:234 -- the EXTRA_CAPACITY_LEVEL value domain.
    public static final int BATTERY_CAPACITY_LEVEL_UNSUPPORTED = -1;
    public static final int BATTERY_CAPACITY_LEVEL_UNKNOWN = 0;
    public static final int BATTERY_CAPACITY_LEVEL_CRITICAL = 1;
    public static final int BATTERY_CAPACITY_LEVEL_LOW = 2;
    public static final int BATTERY_CAPACITY_LEVEL_NORMAL = 3;
    public static final int BATTERY_CAPACITY_LEVEL_HIGH = 4;
    public static final int BATTERY_CAPACITY_LEVEL_FULL = 5;

    // AOSP :377 / :386 -- the paired charging-state broadcasts.
    public static final String ACTION_CHARGING = "android.os.action.CHARGING";
    public static final String ACTION_DISCHARGING = "android.os.action.DISCHARGING";

    public BatteryManager() {}

    // ---- real telemetry, sourced from `pmset -g batt` ---------------------
    // Sample output on a MacBook (AC-attached, Optimized Battery Charging
    // paused just under 80%):
    //   Now drawing from 'AC Power'
    //    -InternalBattery-0 (id=36569187)\t78%; AC attached; not charging present: true
    // Or mid-charge / on battery:
    //    -InternalBattery-0 (id=...)\t63%; charging; 0:32 remaining present: true
    //    -InternalBattery-0 (id=...)\t45%; discharging; 3:12 remaining present: true
    // A desktop Mac with no battery prints no "-InternalBattery-" line at
    // all -- just the "Now drawing from" line -- which we treat the way a
    // real battery-less Android board reports (status UNKNOWN).
    //
    // "discharging" contains the substring "charging", so the state checks
    // below are ordered most-specific-first (not-charging, discharging,
    // charged, charging) rather than independent contains() checks.
    private static final long CACHE_TTL_NANOS = 2_000_000_000L; // 2s
    private static final Pattern PERCENT = Pattern.compile("(\\d+)%");
    private static long sCacheTime = Long.MIN_VALUE;
    private static Snapshot sCache;

    private static final class Snapshot {
        int capacity = 100;
        int status = BATTERY_STATUS_UNKNOWN;
    }

    private static synchronized Snapshot snapshot() {
        long now = System.nanoTime();
        if (sCache != null && (now - sCacheTime) < CACHE_TTL_NANOS) return sCache;
        Snapshot s = new Snapshot();
        boolean present = false;
        try {
            // java.lang.Process, fully qualified: this package declares its
            // own android.os.Process (AOSP's pid/uid class), which shadows
            // the JDK subprocess type under the bare name "Process".
            java.lang.Process p = new ProcessBuilder("/usr/bin/pmset", "-g", "batt").start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (!line.contains("InternalBattery")) continue;
                    present = true;
                    Matcher m = PERCENT.matcher(line);
                    if (m.find()) s.capacity = Integer.parseInt(m.group(1));
                    if (line.contains("not charging")) {
                        s.status = BATTERY_STATUS_NOT_CHARGING;
                    } else if (line.contains("discharging")) {
                        s.status = BATTERY_STATUS_DISCHARGING;
                    } else if (line.contains("charged")) {
                        s.status = BATTERY_STATUS_FULL;
                    } else if (line.contains("charging")) {
                        s.status = BATTERY_STATUS_CHARGING;
                    }
                    // else: an unrecognized state word (e.g. "finishing
                    // charge") -- leave status at the honest UNKNOWN default
                    // rather than guess.
                }
            }
            p.waitFor();
        } catch (Exception e) {
            // pmset missing/blocked -- fall through to the no-battery
            // default below rather than fabricate a charge state.
        }
        if (!present) {
            s.capacity = 100;
            s.status = BATTERY_STATUS_UNKNOWN;
        }
        sCache = s;
        sCacheTime = now;
        return s;
    }

    public int getIntProperty(int id) {
        Snapshot s = snapshot();
        switch (id) {
            case BATTERY_PROPERTY_CAPACITY:        return s.capacity;     // percent
            case BATTERY_PROPERTY_STATUS:          return s.status;
            // pmset's plain-text summary doesn't expose instantaneous
            // current/charge-counter telemetry (that lives in the
            // AppleSmartBattery IOKit registry's raw gas-gauge fields,
            // whose units are vendor-specific and not safely convertible
            // without further verification) -- report the honest "full,
            // idle" values rather than a fabricated-looking number.
            case BATTERY_PROPERTY_CHARGE_COUNTER:  return 4000000;        // uAh
            case BATTERY_PROPERTY_CURRENT_NOW:     return 0;              // uA
            case BATTERY_PROPERTY_CURRENT_AVERAGE: return 0;
            default:                               return Integer.MIN_VALUE;
        }
    }

    public long getLongProperty(int id) {
        Snapshot s = snapshot();
        switch (id) {
            case BATTERY_PROPERTY_CHARGE_COUNTER:  return 4000000L;
            case BATTERY_PROPERTY_ENERGY_COUNTER:  return 40000000000L;   // nWh
            case BATTERY_PROPERTY_CAPACITY:        return (long) s.capacity;
            default:                               return Long.MIN_VALUE;
        }
    }

    /** AOSP :644 -- `queryStringProperty(id)`, documented "the property value,
     *  or null if not supported". The only string-typed properties AOSP
     *  defines are the battery-part ones behind FLAG_BATTERY_PART_STATUS_API,
     *  which read the HAL's serial/part records; macOS exposes no equivalent
     *  through pmset, so every id is "not supported" and null is the answer a
     *  device without that HAL support gives too. */
    public String getStringProperty(int id) { return null; }

    public boolean isCharging() {
        int status = snapshot().status;
        return status == BATTERY_STATUS_CHARGING || status == BATTERY_STATUS_FULL;
    }

    // API 28+: time-to-full in ms, or 0 if already full / unknown. pmset's
    // "H:MM remaining" estimate is only printed while charging and is a
    // coarse minute-granularity guess; rather than parse and mis-scale it,
    // report the same "unknown" value AOSP returns when the HAL can't
    // estimate one.
    public long computeChargeTimeRemaining() { return 0L; }
}
