package android.provider;

/**
 * Intent actions and extras for driving the device's clock app.
 *
 * Pure constants. Mac.apk ships no clock app, so an app that fires
 * ACTION_SET_ALARM finds no activity to handle it -- exactly as on a device
 * with no clock app installed. Declaring the constants is not a claim that one
 * exists; it is what lets the app build the Intent at all.
 *
 * Source: frameworks/base/core/java/android/provider/AlarmClock.java
 */
public final class AlarmClock {

    /** AOSP declares a public no-arg constructor on this contract class. */
    public AlarmClock() { }

    public static final String ACTION_SET_ALARM = "android.intent.action.SET_ALARM";
    public static final String ACTION_DISMISS_ALARM = "android.intent.action.DISMISS_ALARM";
    public static final String ACTION_SNOOZE_ALARM = "android.intent.action.SNOOZE_ALARM";
    public static final String ACTION_SHOW_ALARMS = "android.intent.action.SHOW_ALARMS";
    public static final String ACTION_SET_TIMER = "android.intent.action.SET_TIMER";
    public static final String ACTION_DISMISS_TIMER = "android.intent.action.DISMISS_TIMER";
    public static final String ACTION_SHOW_TIMERS = "android.intent.action.SHOW_TIMERS";

    public static final String ALARM_SEARCH_MODE_ALL = "android.all";
    public static final String ALARM_SEARCH_MODE_LABEL = "android.label";
    public static final String ALARM_SEARCH_MODE_NEXT = "android.next";
    public static final String ALARM_SEARCH_MODE_TIME = "android.time";

    public static final String EXTRA_ALARM_SEARCH_MODE =
            "android.intent.extra.alarm.SEARCH_MODE";
    public static final String EXTRA_ALARM_SNOOZE_DURATION =
            "android.intent.extra.alarm.SNOOZE_DURATION";
    public static final String EXTRA_DAYS = "android.intent.extra.alarm.DAYS";
    public static final String EXTRA_HOUR = "android.intent.extra.alarm.HOUR";
    public static final String EXTRA_IS_PM = "android.intent.extra.alarm.IS_PM";
    public static final String EXTRA_LENGTH = "android.intent.extra.alarm.LENGTH";
    public static final String EXTRA_MESSAGE = "android.intent.extra.alarm.MESSAGE";
    public static final String EXTRA_MINUTES = "android.intent.extra.alarm.MINUTES";
    public static final String EXTRA_RINGTONE = "android.intent.extra.alarm.RINGTONE";
    public static final String EXTRA_SKIP_UI = "android.intent.extra.alarm.SKIP_UI";
    public static final String EXTRA_VIBRATE = "android.intent.extra.alarm.VIBRATE";

    public static final String VALUE_RINGTONE_SILENT = "silent";
}
