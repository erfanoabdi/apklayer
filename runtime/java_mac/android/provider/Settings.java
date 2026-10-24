// android.provider.Settings -- the global/system/secure key/value
// store. Apps mainly call Secure.getString(Secure.ANDROID_ID) for a
// stable per-install identifier, read the animation scales, or
// round-trip their own keys. Real Android keeps these in a SQLite DB
// behind the Settings ContentProvider; Mac.apk backs them with a real
// in-process store (SettingsStore) that persists to the android-root
// data tree, so putInt/putFloat/putString round-trip and survive runs.
package android.provider;

import android.content.ContentResolver;
import android.content.Context;

public final class Settings {
    /** AOSP declares a public no-arg constructor on this contract class. */
    public Settings() { }

    /**
     * Settings "panels" -- the slide-up mini-settings surfaces. Pure Intent
     * action constants; Mac.apk ships no Settings app, so an app that fires one
     * finds no activity to handle it, exactly as on a device whose Settings app
     * is disabled. Declaring the constant is not a claim that a panel exists.
     *
     * Source: frameworks/base/core/java/android/provider/Settings.java
     */
    public static final class Panel {
        private Panel() { }
        public static final String ACTION_INTERNET_CONNECTIVITY =
                "android.settings.panel.action.INTERNET_CONNECTIVITY";
        public static final String ACTION_NFC = "android.settings.panel.action.NFC";
        public static final String ACTION_VOLUME = "android.settings.panel.action.VOLUME";
        public static final String ACTION_WIFI = "android.settings.panel.action.WIFI";
    }

    // ---- ACTION_* activity intents (raw github aosp-mirror Settings.java).
    // Apps launch these via startActivity(new Intent(ACTION_*)) to deep-link
    // into the system Settings UI. Mac.apk has no system Settings activity,
    // but the constant strings must exist so the intent-construction code
    // compiles/runs (the launch is a no-op when nothing handles the action).
    public static final String ACTION_SETTINGS = "android.settings.SETTINGS";
    public static final String ACTION_APN_SETTINGS = "android.settings.APN_SETTINGS";
    public static final String ACTION_LOCATION_SOURCE_SETTINGS =
            "android.settings.LOCATION_SOURCE_SETTINGS";
    public static final String ACTION_WIFI_SETTINGS = "android.settings.WIFI_SETTINGS";
    public static final String ACTION_WIRELESS_SETTINGS = "android.settings.WIRELESS_SETTINGS";
    public static final String ACTION_AIRPLANE_MODE_SETTINGS =
            "android.settings.AIRPLANE_MODE_SETTINGS";
    public static final String ACTION_BLUETOOTH_SETTINGS = "android.settings.BLUETOOTH_SETTINGS";
    public static final String ACTION_DATE_SETTINGS = "android.settings.DATE_SETTINGS";
    public static final String ACTION_SOUND_SETTINGS = "android.settings.SOUND_SETTINGS";
    public static final String ACTION_DISPLAY_SETTINGS = "android.settings.DISPLAY_SETTINGS";
    public static final String ACTION_LOCALE_SETTINGS = "android.settings.LOCALE_SETTINGS";
    public static final String ACTION_INPUT_METHOD_SETTINGS =
            "android.settings.INPUT_METHOD_SETTINGS";
    public static final String ACTION_SECURITY_SETTINGS = "android.settings.SECURITY_SETTINGS";
    public static final String ACTION_PRIVACY_SETTINGS = "android.settings.PRIVACY_SETTINGS";
    public static final String ACTION_VPN_SETTINGS = "android.settings.VPN_SETTINGS";
    public static final String ACTION_DEVICE_INFO_SETTINGS =
            "android.settings.DEVICE_INFO_SETTINGS";
    public static final String ACTION_APPLICATION_SETTINGS =
            "android.settings.APPLICATION_SETTINGS";
    public static final String ACTION_APPLICATION_DEVELOPMENT_SETTINGS =
            "android.settings.APPLICATION_DEVELOPMENT_SETTINGS";
    public static final String ACTION_APPLICATION_DETAILS_SETTINGS =
            "android.settings.APPLICATION_DETAILS_SETTINGS";
    public static final String ACTION_MANAGE_APPLICATIONS_SETTINGS =
            "android.settings.MANAGE_APPLICATIONS_SETTINGS";
    public static final String ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS =
            "android.settings.MANAGE_ALL_APPLICATIONS_SETTINGS";
    public static final String ACTION_APP_NOTIFICATION_SETTINGS =
            "android.settings.APP_NOTIFICATION_SETTINGS";
    public static final String ACTION_CHANNEL_NOTIFICATION_SETTINGS =
            "android.settings.CHANNEL_NOTIFICATION_SETTINGS";
    public static final String ACTION_NOTIFICATION_LISTENER_SETTINGS =
            "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS";
    public static final String ACTION_ACCESSIBILITY_SETTINGS =
            "android.settings.ACCESSIBILITY_SETTINGS";
    public static final String ACTION_USAGE_ACCESS_SETTINGS =
            "android.settings.USAGE_ACCESS_SETTINGS";
    public static final String ACTION_BATTERY_SAVER_SETTINGS =
            "android.settings.BATTERY_SAVER_SETTINGS";
    public static final String ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS =
            "android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS";
    public static final String ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS =
            "android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS";
    public static final String ACTION_MANAGE_OVERLAY_PERMISSION =
            "android.settings.action.MANAGE_OVERLAY_PERMISSION";
    public static final String ACTION_MANAGE_WRITE_SETTINGS =
            "android.settings.action.MANAGE_WRITE_SETTINGS";
    public static final String ACTION_MANAGE_UNKNOWN_APP_SOURCES =
            "android.settings.MANAGE_UNKNOWN_APP_SOURCES";
    // The special-app-access screens (API 30 / 30 / 31), AOSP verbatim
    // (core/java/android/provider/Settings.java android14 :19778-19779,
    // :19798-19799, :592-593). Stage S1 resolves all three to the host consent
    // surface (com.macapk.loader.SpecialAccessHandler), so a
    // resolveActivity() guard now finds them, as it does on a device.
    public static final String ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION =
            "android.settings.MANAGE_ALL_FILES_ACCESS_PERMISSION";
    public static final String ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION =
            "android.settings.MANAGE_APP_ALL_FILES_ACCESS_PERMISSION";
    public static final String ACTION_REQUEST_MANAGE_MEDIA =
            "android.settings.REQUEST_MANAGE_MEDIA";
    public static final String ACTION_NFC_SETTINGS = "android.settings.NFC_SETTINGS";
    public static final String ACTION_NFCSHARING_SETTINGS = "android.settings.NFCSHARING_SETTINGS";
    public static final String ACTION_DATA_ROAMING_SETTINGS =
            "android.settings.DATA_ROAMING_SETTINGS";
    public static final String ACTION_INTERNAL_STORAGE_SETTINGS =
            "android.settings.INTERNAL_STORAGE_SETTINGS";
    public static final String ACTION_MEMORY_CARD_SETTINGS =
            "android.settings.MEMORY_CARD_SETTINGS";
    public static final String ACTION_HOME_SETTINGS = "android.settings.HOME_SETTINGS";
    public static final String ACTION_DREAM_SETTINGS = "android.settings.DREAM_SETTINGS";
    public static final String ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS =
            "android.settings.NOTIFICATION_POLICY_ACCESS_SETTINGS";
    public static final String ACTION_ZEN_MODE_PRIORITY_SETTINGS =
            "android.settings.ZEN_MODE_PRIORITY_SETTINGS";
    public static final String ACTION_USER_DICTIONARY_SETTINGS =
            "android.settings.USER_DICTIONARY_SETTINGS";
    public static final String ACTION_QUICK_LAUNCH_SETTINGS =
            "android.settings.QUICK_LAUNCH_SETTINGS";
    public static final String ACTION_MANAGE_DEFAULT_APPS_SETTINGS =
            "android.settings.MANAGE_DEFAULT_APPS_SETTINGS";
    public static final String ACTION_ADD_ACCOUNT = "android.settings.ADD_ACCOUNT_SETTINGS";
    public static final String ACTION_SYNC_SETTINGS = "android.settings.SYNC_SETTINGS";
    public static final String ACTION_FINGERPRINT_ENROLL =
            "android.settings.FINGERPRINT_ENROLL";
    public static final String ACTION_BIOMETRIC_ENROLL = "android.settings.BIOMETRIC_ENROLL";
    public static final String ACTION_HARD_KEYBOARD_SETTINGS =
            "android.settings.HARD_KEYBOARD_SETTINGS";
    public static final String ACTION_CAPTIONING_SETTINGS =
            "android.settings.CAPTIONING_SETTINGS";
    public static final String ACTION_PRINT_SETTINGS = "android.settings.ACTION_PRINT_SETTINGS";
    public static final String ACTION_VOICE_INPUT_SETTINGS =
            "android.settings.VOICE_INPUT_SETTINGS";

    // Intent extras commonly read by deep-linked settings activities.
    public static final String EXTRA_AUTHORITIES = "authorities";
    public static final String EXTRA_ACCOUNT_TYPES = "account_types";
    public static final String EXTRA_INPUT_METHOD_ID = "input_method_id";
    public static final String EXTRA_APP_PACKAGE = "android.provider.extra.APP_PACKAGE";
    public static final String EXTRA_CHANNEL_ID = "android.provider.extra.CHANNEL_ID";
    public static final String EXTRA_BLUETOOTH_DEVICE = "android.provider.extra.BLUETOOTH_DEVICE";

    public static final String AUTHORITY = "settings";

    // Real Android throws this from getInt/getLong/etc. when the
    // setting is missing and no default was supplied. Several SDKs catch
    // this type explicitly, so the class must exist with the right name.
    // AOSP: `extends android.util.AndroidException`.
    // frameworks/base/core/java/android/provider/Settings.java
    public static class SettingNotFoundException extends android.util.AndroidException {
        public SettingNotFoundException(String name) { super(name); }
    }

    // Shared coercion helpers so the three namespaces behave identically.
    private static int parseInt(String v, int def) {
        if (v == null) return def;
        try { return Integer.parseInt(v.trim()); } catch (NumberFormatException e) { return def; }
    }
    private static int parseIntOrThrow(String v, String name) throws SettingNotFoundException {
        if (v == null) throw new SettingNotFoundException(name);
        try { return Integer.parseInt(v.trim()); }
        catch (NumberFormatException e) { throw new SettingNotFoundException(name); }
    }
    private static long parseLong(String v, long def) {
        if (v == null) return def;
        try { return Long.parseLong(v.trim()); } catch (NumberFormatException e) { return def; }
    }
    private static long parseLongOrThrow(String v, String name) throws SettingNotFoundException {
        if (v == null) throw new SettingNotFoundException(name);
        try { return Long.parseLong(v.trim()); }
        catch (NumberFormatException e) { throw new SettingNotFoundException(name); }
    }
    private static float parseFloat(String v, float def) {
        if (v == null) return def;
        try { return Float.parseFloat(v.trim()); } catch (NumberFormatException e) { return def; }
    }
    private static float parseFloatOrThrow(String v, String name) throws SettingNotFoundException {
        if (v == null) throw new SettingNotFoundException(name);
        try { return Float.parseFloat(v.trim()); }
        catch (NumberFormatException e) { throw new SettingNotFoundException(name); }
    }

    // AOSP's SettingsProvider notifies every registered ContentObserver after
    // a successful write (insertForUser/updateForUser both end in
    // notifyChange()). Our put*() methods below wrote straight to
    // SettingsStore and never touched ContentResolver's observer registry, so
    // a ContentObserver registered via
    // cr.registerContentObserver(getUriFor(name), false, observer) never
    // fired for a change made through this same class's put path -- see the
    // F-Droid airplane-mode comment on Global.getUriFor below, which names
    // this exact scenario as the reason getUriFor exists.
    // ContentResolver.notifyChange(Uri, ContentObserver) is already public
    // and already implements the real observer dispatch (registered via the
    // same cr instance every put*() method here already receives), so this
    // is a same-file fix: no change to ContentResolver needed. cr may be
    // null for a bare pre-attach ContentResolver; skip rather than NPE,
    // matching this file's existing best-effort-write ethos.
    private static void notifyChanged(ContentResolver cr, android.net.Uri uri) {
        if (cr == null || uri == null) return;
        try { cr.notifyChange(uri, null); } catch (Throwable ignored) {}
    }

    /** AOSP body (core/java/android/provider/Settings.java):
     *  {@code isCallingPackageAllowedToDrawOverlays(context, Process.myUid(),
     *  context.getOpPackageName(), false) ||
     *  context.checkSelfPermission(SYSTEM_APPLICATION_OVERLAY) ==
     *  PERMISSION_GRANTED} -- i.e. it ultimately bottoms out in a
     *  checkSelfPermission-style grant check. Mac.apk has no AppOps model to
     *  answer the first disjunct (the finer per-permission user toggle
     *  isCallingPackageAllowedToDrawOverlays reads), so this mirrors the
     *  second: Context.checkSelfPermission already answers every self-
     *  permission query GRANTED (Context.checkPermission's single decision
     *  point), so this evaluates true, consistent with that established
     *  blanket-grant model rather than a second hardcoded constant here. */
    public static boolean canDrawOverlays(Context context) {
        // AOSP: `isCallingPackageAllowedToDrawOverlays(context, uid, pkg, true)
        //        || context.checkSelfPermission(SYSTEM_APPLICATION_OVERLAY) == GRANTED`
        // The FIRST disjunct is the one that answers true on a real device: it
        // reads the OP_SYSTEM_ALERT_WINDOW AppOp the user toggles. The second
        // reads a signature|privileged permission no normal app can hold.
        //
        // This used to be written as the SECOND disjunct and returned true only
        // because Context.checkSelfPermission answered GRANTED to everything.
        // The PackageManager lane made that honest (v1.0.924: GRANTED iff the
        // manifest declares it), which flipped this to false for every app and
        // would have sent NewPipe's popup mode, screen recorders and floating
        // tools down an "ask the user to enable overlays" branch that dead-ends
        // in a runtime with no Settings UI.
        //
        // So answer the FIRST disjunct instead, which is what a device with the
        // permission granted answers. It is honest here for a reason that is
        // not a shortcut: WindowManager.addView really does work -- Mac.apk
        // composites every overlay itself through WindowOverlayStack -- so an
        // app told "yes" can genuinely draw one. Returning false would deny a
        // capability we actually provide.
        return true;
    }

    /**
     * AOSP's shared base for System/Secure/Global. It is public API
     * (`public class Settings$NameValueTable implements BaseColumns` with
     * NAME/VALUE and getUriFor(Uri,String)), and the three tables EXTEND it --
     * so `Settings.System.NAME`, `Settings.Secure._ID` and any
     * BaseColumns-typed use of them only resolve through this link.
     * Source: frameworks/base/core/java/android/provider/Settings.java
     */
    public static class NameValueTable implements BaseColumns {
        public static final String NAME = "name";
        public static final String VALUE = "value";

        public static android.net.Uri getUriFor(android.net.Uri uri, String name) {
            return android.net.Uri.withAppendedPath(uri, name);
        }

        protected static boolean putString(android.content.ContentResolver resolver,
                                           android.net.Uri uri, String name, String value) {
            return false;
        }
    }

    public static class System extends NameValueTable {
        public static final String VOLUME_MUSIC   = "volume_music";
        public static final String VOLUME_RING    = "volume_ring";
        public static final String VOLUME_ALARM   = "volume_alarm";
        public static final String VOLUME_SYSTEM  = "volume_system";
        public static final String VOLUME_VOICE   = "volume_voice";
        public static final String VOLUME_NOTIFICATION = "volume_notification";
        public static final String SCREEN_OFF_TIMEOUT = "screen_off_timeout";
        public static final String SCREEN_BRIGHTNESS = "screen_brightness";
        public static final String SCREEN_BRIGHTNESS_MODE = "screen_brightness_mode";
        public static final int SCREEN_BRIGHTNESS_MODE_MANUAL = 0;
        public static final int SCREEN_BRIGHTNESS_MODE_AUTOMATIC = 1;
        public static final String ACCELEROMETER_ROTATION = "accelerometer_rotation";
        public static final String USER_ROTATION = "user_rotation";
        public static final String HAPTIC_FEEDBACK_ENABLED = "haptic_feedback_enabled";
        public static final String SOUND_EFFECTS_ENABLED = "sound_effects_enabled";
        public static final String VIBRATE_ON = "vibrate_on";
        public static final String TEXT_AUTO_REPLACE = "auto_replace";
        public static final String TEXT_AUTO_CAPS = "auto_caps";
        public static final String TEXT_AUTO_PUNCTUATE = "auto_punctuate";
        public static final String TIME_12_24 = "time_12_24";
        public static final String DATE_FORMAT = "date_format";
        public static final String FONT_SCALE = "font_scale";
        /** @hide AOSP's system-locale list, used by get/putConfiguration. */
        public static final String SYSTEM_LOCALES = "system_locales";
        /** @deprecated AOSP: "Do not use." */
        @Deprecated
        public static final String SHOW_GTALK_SERVICE_STATUS = "SHOW_GTALK_SERVICE_STATUS";

        /**
         * AOSP copies the font scale and locale list out of Settings into the
         * Configuration. Reproduced exactly (including the "negative scale falls
         * back to the default" guard); it reads OUR settings store, so the
         * answer is real, not stubbed.
         */
        public static void getConfiguration(ContentResolver cr,
                android.content.res.Configuration outConfig) {
            outConfig.fontScale = getFloat(cr, FONT_SCALE, 1.0f);
            if (outConfig.fontScale < 0) {
                outConfig.fontScale = 1.0f;
            }
            final String localeValue = getString(cr, SYSTEM_LOCALES);
            if (localeValue != null) {
                outConfig.setLocales(android.os.LocaleList.forLanguageTags(localeValue));
            }
        }

        /** The inverse of getConfiguration; writes both settings, per AOSP. */
        public static boolean putConfiguration(ContentResolver cr,
                android.content.res.Configuration config) {
            return putFloat(cr, FONT_SCALE, config.fontScale)
                    && putString(cr, SYSTEM_LOCALES, config.getLocales().toLanguageTags());
        }

        /** @deprecated AOSP: "Do not use." Reads the stored flag, as AOSP does. */
        @Deprecated
        public static boolean getShowGTalkServiceStatus(ContentResolver cr) {
            return getInt(cr, SHOW_GTALK_SERVICE_STATUS, 0) != 0;
        }

        /** @deprecated AOSP: "Do not use." */
        @Deprecated
        public static void setShowGTalkServiceStatus(ContentResolver cr, boolean flag) {
            putInt(cr, SHOW_GTALK_SERVICE_STATUS, flag ? 1 : 0);
        }
        /** AOSP DEFAULT_NOTIFICATION_URI -- placeholder content URI for
         *  the system default notification sound. Points at AOSP's
         *  canonical path; a parseable Uri even though we can't play it. */
        public static final android.net.Uri DEFAULT_NOTIFICATION_URI =
            android.net.Uri.parse("content://settings/system/notification_sound");
        public static final android.net.Uri DEFAULT_RINGTONE_URI =
            android.net.Uri.parse("content://settings/system/ringtone");
        public static final android.net.Uri DEFAULT_ALARM_ALERT_URI =
            android.net.Uri.parse("content://settings/system/alarm_alert");
        public static final android.net.Uri CONTENT_URI =
            android.net.Uri.parse("content://settings/system");

        public static String getString(ContentResolver cr, String name) {
            return SettingsStore.getString(SettingsStore.NS_SYSTEM, name);
        }
        public static int getInt(ContentResolver cr, String name) throws SettingNotFoundException {
            return parseIntOrThrow(SettingsStore.getString(SettingsStore.NS_SYSTEM, name), name);
        }
        public static int getInt(ContentResolver cr, String name, int def) {
            return parseInt(SettingsStore.getString(SettingsStore.NS_SYSTEM, name), def);
        }
        public static long getLong(ContentResolver cr, String name) throws SettingNotFoundException {
            return parseLongOrThrow(SettingsStore.getString(SettingsStore.NS_SYSTEM, name), name);
        }
        public static long getLong(ContentResolver cr, String name, long def) {
            return parseLong(SettingsStore.getString(SettingsStore.NS_SYSTEM, name), def);
        }
        public static float getFloat(ContentResolver cr, String name) throws SettingNotFoundException {
            return parseFloatOrThrow(SettingsStore.getString(SettingsStore.NS_SYSTEM, name), name);
        }
        public static float getFloat(ContentResolver cr, String name, float def) {
            return parseFloat(SettingsStore.getString(SettingsStore.NS_SYSTEM, name), def);
        }
        public static boolean putString(ContentResolver cr, String name, String value) {
            boolean ok = SettingsStore.putString(SettingsStore.NS_SYSTEM, name, value);
            if (ok) notifyChanged(cr, getUriFor(name));
            return ok;
        }
        public static boolean putInt(ContentResolver cr, String name, int value) {
            boolean ok = SettingsStore.putString(SettingsStore.NS_SYSTEM, name, Integer.toString(value));
            if (ok) notifyChanged(cr, getUriFor(name));
            return ok;
        }
        public static boolean putLong(ContentResolver cr, String name, long value) {
            boolean ok = SettingsStore.putString(SettingsStore.NS_SYSTEM, name, Long.toString(value));
            if (ok) notifyChanged(cr, getUriFor(name));
            return ok;
        }
        public static boolean putFloat(ContentResolver cr, String name, float value) {
            boolean ok = SettingsStore.putString(SettingsStore.NS_SYSTEM, name, Float.toString(value));
            if (ok) notifyChanged(cr, getUriFor(name));
            return ok;
        }
        /** AOSP Settings.System.getUriFor(name) = content://settings/system/<name>. */
        public static android.net.Uri getUriFor(String name) {
            return android.net.Uri.parse("content://settings/system/" + name);
        }
        public static boolean canWrite(Context context) { return true; }
    }

    public static class Secure extends NameValueTable {
        public static final String ANDROID_ID         = "android_id";
        public static final String LOCATION_PROVIDERS_ALLOWED = "location_providers_allowed";

        /**
         * AOSP asks LocationManagerService whether the provider is enabled.
         * Mac.apk's android.location.LocationManager already answers that
         * question honestly (only an app-added test provider is ever enabled),
         * so this delegates there rather than reading the settings string --
         * which is what AOSP does too, and which keeps the two answers from
         * disagreeing.
         *
         * @deprecated AOSP deprecated this in API 19 in favour of
         * LocationManager.isProviderEnabled.
         */
        @Deprecated
        public static boolean isLocationProviderEnabled(ContentResolver cr, String provider) {
            return new android.location.LocationManager().isProviderEnabled(provider);
        }

        /**
         * @deprecated AOSP deprecated this in API 19 and its CURRENT body is
         * literally empty -- changing provider enablement from Settings was
         * removed from the platform. A no-op here is therefore AOSP-faithful,
         * not a stub.
         */
        @Deprecated
        public static void setLocationProviderEnabled(ContentResolver cr, String provider,
                boolean enabled) { }
        public static final String LOCATION_MODE = "location_mode";
        public static final int LOCATION_MODE_OFF = 0;
        public static final int LOCATION_MODE_SENSORS_ONLY = 1;
        public static final int LOCATION_MODE_BATTERY_SAVING = 2;
        public static final int LOCATION_MODE_HIGH_ACCURACY = 3;
        public static final String INSTALL_NON_MARKET_APPS = "install_non_market_apps";
        public static final String ENABLED_ACCESSIBILITY_SERVICES =
                "enabled_accessibility_services";
        public static final String ACCESSIBILITY_ENABLED = "accessibility_enabled";
        public static final String DEFAULT_INPUT_METHOD = "default_input_method";
        public static final String ALLOW_MOCK_LOCATION = "mock_location";
        public static final String BLUETOOTH_NAME = "bluetooth_name";
        public static final android.net.Uri CONTENT_URI =
            android.net.Uri.parse("content://settings/secure");

        public static String getString(ContentResolver cr, String name) {
            return SettingsStore.getString(SettingsStore.NS_SECURE, name);
        }
        public static int getInt(ContentResolver cr, String name) throws SettingNotFoundException {
            return parseIntOrThrow(SettingsStore.getString(SettingsStore.NS_SECURE, name), name);
        }
        public static int getInt(ContentResolver cr, String name, int def) {
            return parseInt(SettingsStore.getString(SettingsStore.NS_SECURE, name), def);
        }
        public static long getLong(ContentResolver cr, String name) throws SettingNotFoundException {
            return parseLongOrThrow(SettingsStore.getString(SettingsStore.NS_SECURE, name), name);
        }
        public static long getLong(ContentResolver cr, String name, long def) {
            return parseLong(SettingsStore.getString(SettingsStore.NS_SECURE, name), def);
        }
        public static float getFloat(ContentResolver cr, String name) throws SettingNotFoundException {
            return parseFloatOrThrow(SettingsStore.getString(SettingsStore.NS_SECURE, name), name);
        }
        public static float getFloat(ContentResolver cr, String name, float def) {
            return parseFloat(SettingsStore.getString(SettingsStore.NS_SECURE, name), def);
        }
        public static boolean putString(ContentResolver cr, String name, String value) {
            boolean ok = SettingsStore.putString(SettingsStore.NS_SECURE, name, value);
            if (ok) notifyChanged(cr, getUriFor(name));
            return ok;
        }
        public static boolean putInt(ContentResolver cr, String name, int value) {
            boolean ok = SettingsStore.putString(SettingsStore.NS_SECURE, name, Integer.toString(value));
            if (ok) notifyChanged(cr, getUriFor(name));
            return ok;
        }
        public static boolean putLong(ContentResolver cr, String name, long value) {
            boolean ok = SettingsStore.putString(SettingsStore.NS_SECURE, name, Long.toString(value));
            if (ok) notifyChanged(cr, getUriFor(name));
            return ok;
        }
        public static boolean putFloat(ContentResolver cr, String name, float value) {
            boolean ok = SettingsStore.putString(SettingsStore.NS_SECURE, name, Float.toString(value));
            if (ok) notifyChanged(cr, getUriFor(name));
            return ok;
        }
        /** AOSP Secure.getUriFor(name) = content://settings/secure/<name>. */
        public static android.net.Uri getUriFor(String name) {
            return android.net.Uri.parse("content://settings/secure/" + name);
        }
    }

    public static class Global extends NameValueTable {
        public static final String AIRPLANE_MODE_ON = "airplane_mode_on";
        public static final String ANIMATOR_DURATION_SCALE = "animator_duration_scale";
        public static final String TRANSITION_ANIMATION_SCALE = "transition_animation_scale";
        public static final String WINDOW_ANIMATION_SCALE = "window_animation_scale";
        public static final String AUTO_TIME = "auto_time";
        public static final String AUTO_TIME_ZONE = "auto_time_zone";
        public static final String DEVICE_NAME = "device_name";
        public static final String DEVELOPMENT_SETTINGS_ENABLED = "development_settings_enabled";
        public static final String ADB_ENABLED = "adb_enabled";
        public static final String WIFI_ON = "wifi_on";
        public static final String DATA_ROAMING = "data_roaming";
        public static final String BLUETOOTH_ON = "bluetooth_on";
        public static final String MOBILE_DATA = "mobile_data";
        public static final android.net.Uri CONTENT_URI =
            android.net.Uri.parse("content://settings/global");

        public static String getString(ContentResolver cr, String name) {
            return SettingsStore.getString(SettingsStore.NS_GLOBAL, name);
        }
        public static int getInt(ContentResolver cr, String name) throws SettingNotFoundException {
            return parseIntOrThrow(SettingsStore.getString(SettingsStore.NS_GLOBAL, name), name);
        }
        public static int getInt(ContentResolver cr, String name, int def) {
            return parseInt(SettingsStore.getString(SettingsStore.NS_GLOBAL, name), def);
        }
        public static long getLong(ContentResolver cr, String name) throws SettingNotFoundException {
            return parseLongOrThrow(SettingsStore.getString(SettingsStore.NS_GLOBAL, name), name);
        }
        public static long getLong(ContentResolver cr, String name, long def) {
            return parseLong(SettingsStore.getString(SettingsStore.NS_GLOBAL, name), def);
        }
        public static float getFloat(ContentResolver cr, String name) throws SettingNotFoundException {
            return animScaleOverride(name,
                parseFloatOrThrow(SettingsStore.getString(SettingsStore.NS_GLOBAL, name), name));
        }
        public static float getFloat(ContentResolver cr, String name, float def) {
            return animScaleOverride(name,
                parseFloat(SettingsStore.getString(SettingsStore.NS_GLOBAL, name), def));
        }
        public static boolean putString(ContentResolver cr, String name, String value) {
            boolean ok = SettingsStore.putString(SettingsStore.NS_GLOBAL, name, value);
            if (ok) notifyChanged(cr, getUriFor(name));
            return ok;
        }
        public static boolean putInt(ContentResolver cr, String name, int value) {
            boolean ok = SettingsStore.putString(SettingsStore.NS_GLOBAL, name, Integer.toString(value));
            if (ok) notifyChanged(cr, getUriFor(name));
            return ok;
        }
        public static boolean putLong(ContentResolver cr, String name, long value) {
            boolean ok = SettingsStore.putString(SettingsStore.NS_GLOBAL, name, Long.toString(value));
            if (ok) notifyChanged(cr, getUriFor(name));
            return ok;
        }
        public static boolean putFloat(ContentResolver cr, String name, float value) {
            boolean ok = SettingsStore.putString(SettingsStore.NS_GLOBAL, name, Float.toString(value));
            if (ok) notifyChanged(cr, getUriFor(name));
            return ok;
        }
        // DIAGNOSTIC (MACAPK_FORCE_ANIM_SCALE_0): force the system animation
        // duration scales to 0 so Compose's getAnimationScaleFlowFor() reports
        // animations-disabled -> animateTo() SNAPS instantly. Decisive test for
        // "the Reddit auth bottom sheet is frozen at its hidden offset". NOT a
        // shipping flag; see Current_State Per-APK-patches notes.
        private static float animScaleOverride(String name, float def) {
            if (name != null && "1".equals(java.lang.System.getenv("MACAPK_FORCE_ANIM_SCALE_0"))
                && (ANIMATOR_DURATION_SCALE.equals(name)
                    || TRANSITION_ANIMATION_SCALE.equals(name)
                    || WINDOW_ANIMATION_SCALE.equals(name))) {
                return 0f;
            }
            return def;
        }
        /** v1.0.110: build a content-Uri pointing at the given Global key.
         *  F-Droid registers a ContentObserver on getUriFor("airplane_mode_on")
         *  to refresh network status; we hand back a stable Uri. */
        public static android.net.Uri getUriFor(String name) {
            return android.net.Uri.parse("content://settings/global/" + name);
        }
    }
}
