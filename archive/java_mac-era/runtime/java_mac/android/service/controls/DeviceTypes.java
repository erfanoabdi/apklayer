package android.service.controls;

/**
 * The device-type taxonomy a ControlsProviderService tags each Control with, so
 * the system's device-controls panel can pick an icon and a default template.
 *
 * PACKAGE NOTE -- applies to android.service.controls and its two subpackages.
 * A ControlsProviderService is bound by System UI's device-controls panel, which
 * Mac.apk does not have: nothing binds the service, no publisher is subscribed
 * to, and performControlAction is never called. Everything else in the package
 * is ordinary immutable value objects (Control, ControlTemplate, ControlAction)
 * that a smart-home app builds constantly outside the callback, so those are
 * faithful rather than stubbed.
 *
 * ⚠ EVERY constant below was EXTRACTED MECHANICALLY from
 * /tmp/aosp_api/android16.txt (signature format 2.0) rather than retyped: this
 * is a 59-value run with SEVEN NEGATIVE values interleaved
 * (TYPE_GENERIC_ON_OFF = -1 down to TYPE_GENERIC_VIEWSTREAM = -7) and gaps in
 * the positive sequence (there is no 9), which is exactly the shape where a
 * hand transcription goes wrong invisibly.
 *
 * Source: frameworks/base/core/java/android/service/controls/DeviceTypes.java
 */
public class DeviceTypes {

    public static final int TYPE_AC_HEATER = 1;
    public static final int TYPE_AC_UNIT = 2;
    public static final int TYPE_AIR_FRESHENER = 3;
    public static final int TYPE_AIR_PURIFIER = 4;
    public static final int TYPE_AWNING = 33;
    public static final int TYPE_BLINDS = 34;
    public static final int TYPE_CAMERA = 50;
    public static final int TYPE_CLOSET = 35;
    public static final int TYPE_COFFEE_MAKER = 5;
    public static final int TYPE_CURTAIN = 36;
    public static final int TYPE_DEHUMIDIFIER = 6;
    public static final int TYPE_DISHWASHER = 24;
    public static final int TYPE_DISPLAY = 7;
    public static final int TYPE_DOOR = 37;
    public static final int TYPE_DOORBELL = 51;
    public static final int TYPE_DRAWER = 38;
    public static final int TYPE_DRYER = 25;
    public static final int TYPE_FAN = 8;
    public static final int TYPE_GARAGE = 39;
    public static final int TYPE_GATE = 40;
    public static final int TYPE_GENERIC_ARM_DISARM = -5;
    public static final int TYPE_GENERIC_LOCK_UNLOCK = -4;
    public static final int TYPE_GENERIC_ON_OFF = -1;
    public static final int TYPE_GENERIC_OPEN_CLOSE = -3;
    public static final int TYPE_GENERIC_START_STOP = -2;
    public static final int TYPE_GENERIC_TEMP_SETTING = -6;
    public static final int TYPE_GENERIC_VIEWSTREAM = -7;
    public static final int TYPE_HEATER = 47;
    public static final int TYPE_HOOD = 10;
    public static final int TYPE_HUMIDIFIER = 11;
    public static final int TYPE_KETTLE = 12;
    public static final int TYPE_LIGHT = 13;
    public static final int TYPE_LOCK = 45;
    public static final int TYPE_MICROWAVE = 14;
    public static final int TYPE_MOP = 26;
    public static final int TYPE_MOWER = 27;
    public static final int TYPE_MULTICOOKER = 28;
    public static final int TYPE_OUTLET = 15;
    public static final int TYPE_PERGOLA = 41;
    public static final int TYPE_RADIATOR = 16;
    public static final int TYPE_REFRIGERATOR = 48;
    public static final int TYPE_REMOTE_CONTROL = 17;
    public static final int TYPE_ROUTINE = 52;
    public static final int TYPE_SECURITY_SYSTEM = 46;
    public static final int TYPE_SET_TOP = 18;
    public static final int TYPE_SHOWER = 29;
    public static final int TYPE_SHUTTER = 42;
    public static final int TYPE_SPRINKLER = 30;
    public static final int TYPE_STANDMIXER = 19;
    public static final int TYPE_STYLER = 20;
    public static final int TYPE_SWITCH = 21;
    public static final int TYPE_THERMOSTAT = 49;
    public static final int TYPE_TV = 22;
    public static final int TYPE_UNKNOWN = 0;
    public static final int TYPE_VACUUM = 32;
    public static final int TYPE_VALVE = 44;
    public static final int TYPE_WASHER = 31;
    public static final int TYPE_WATER_HEATER = 23;
    public static final int TYPE_WINDOW = 43;

    private DeviceTypes() { }

    /** AOSP: true for TYPE_GENERIC_VIEWSTREAM (-7) through TYPE_ROUTINE (52). */
    public static boolean validDeviceType(int deviceType) {
        return deviceType >= TYPE_GENERIC_VIEWSTREAM && deviceType <= TYPE_ROUTINE;
    }
}
