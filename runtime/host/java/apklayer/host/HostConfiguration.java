package apklayer.host;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.Display;
import java.util.List;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;

/**
 * The instance's host settings (HubHostSettings: the desktop's, or the instance's overrides) as an app process's configuration: night mode
 * and locale as resource qualifiers on top of the window's size ones, the font scale, the default locale and time zone, the animator duration
 * scale, UiModeManager's night mode (contrast is the hub's CONTRAST_LEVEL only). AppHost hands a change to its activities as ActivityThread's handleConfigurationChanged does.
 */
final class HostConfiguration {
    private HostConfiguration() {}

    /** The configuration bits the host settings change (what decides whether an activity handles the change itself or is recreated). */
    static final int SETTINGS_CHANGES = android.content.pm.ActivityInfo.CONFIG_UI_MODE | android.content.pm.ActivityInfo.CONFIG_LOCALE
            | android.content.pm.ActivityInfo.CONFIG_LAYOUT_DIRECTION | android.content.pm.ActivityInfo.CONFIG_FONT_SCALE;
    private static String size = System.getProperty("apklayer.qualifiers", "w480dp-h800dp");
    private static Bundle settings = new Bundle();

    /** The instance's colour scheme; null when the host has none (no portal). */
    static Boolean night() { return settings.containsKey("night") ? settings.getBoolean("night") : null; }

    /** The window style of this app (`apklayer settings set window-mode[.<package>] auto|mobile|desktop`): its own, else the instance's, else auto. */
    static String windowMode() {
        String own = settings.getString("windowMode." + RuntimeEnvironment.getApplication().getPackageName());
        return own != null ? own : settings.getString("windowMode", "auto");
    }

    /** The hub's settings (HubClient at registration, HubEndpoint on a change), on the main thread: the configuration bits that changed. */
    static int update(Bundle next) {
        try { return apply(next); }
        catch (RuntimeException e) {  // a setting the resources refuse must not end the app
            System.out.println("[apklayer] host settings not applied: " + e);
            e.printStackTrace(System.out);
            return 0;
        }
    }

    private static int apply(Bundle next) {
        Configuration before = new Configuration(RuntimeEnvironment.getApplication().getResources().getConfiguration());
        settings = next == null ? new Bundle() : next;
        apply();
        String tz = settings.getString("timezone");
        if (tz != null && !tz.isEmpty()) {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(tz));
            android.icu.util.TimeZone.setDefault(android.icu.util.TimeZone.getTimeZone(tz));
        }
        android.animation.ValueAnimator.setDurationScale(settings.getFloat("animatorScale", 1f));  // WindowManagerGlobal sets it from the system's on a device
        Boolean night = night();
        android.app.UiModeManager modes = RuntimeEnvironment.getApplication().getSystemService(android.app.UiModeManager.class);
        if (night != null && modes != null) {  // the shadow's own setNightMode writes Settings.Secure, which only the system (the hub) may
            try {
                Object shadow = org.robolectric.shadow.api.Shadow.extract(modes);
                java.lang.reflect.Field mode = org.robolectric.shadows.ShadowUIModeManager.class.getDeclaredField("currentNightMode");
                mode.setAccessible(true);
                mode.setInt(java.lang.reflect.Modifier.isStatic(mode.getModifiers()) ? null : shadow, night ? android.app.UiModeManager.MODE_NIGHT_YES : android.app.UiModeManager.MODE_NIGHT_NO);
            } catch (ReflectiveOperationException | RuntimeException e) {
                System.out.println("[apklayer] UiModeManager night mode not set: " + e);
            }
        }
        int changes = before.diff(RuntimeEnvironment.getApplication().getResources().getConfiguration()) & SETTINGS_CHANGES;
        System.out.println("[apklayer] host settings " + settings.keySet().stream().sorted().map(k -> k + "=" + settings.get(k)).toList() + (changes != 0 ? " changes=0x" + Integer.toHexString(changes) : ""));
        return changes;
    }

    /** The window's size qualifiers (AppHost.hostResize); the settings' stay on top of them. */
    static void setSize(String qualifiers) { size = qualifiers; apply(); }

    private static void apply() {
        StringBuilder extra = new StringBuilder();
        String locale = settings.getString("locale");
        if (locale != null && !locale.isEmpty()) extra.append("b+").append(locale.replace('-', '+'));  // BCP-47 as a resource qualifier
        Boolean night = night();
        if (night != null) extra.append(extra.length() > 0 ? "-" : "").append(night ? "night" : "notnight");
        RuntimeEnvironment.setQualifiers(extra.length() > 0 ? size + " +" + extra : size);
        // not a qualifier, and the qualifiers' configuration leaves it undefined (0), which keeps the last one: set every time, 1 included
        RuntimeEnvironment.setFontScale(settings.getFloat("fontScale", 1f));
        if (locale != null && !locale.isEmpty()) android.os.LocaleList.setDefault(RuntimeEnvironment.getApplication().getResources().getConfiguration().getLocales());
    }

    /**
     * The new configuration to the application and each activity (ActivityThread.handleConfigurationChanged): an activity that declares the
     * changes in its configChanges gets onConfigurationChanged, any other is recreated (ActivityController.configurationChange) and handed to
     * {@code recreated} with the activity it replaced. {@code asleep}: stopped activities, which only get the new resources.
     */
    static void dispatch(List<ActivityController<? extends Activity>> controllers, java.util.function.Predicate<Activity> asleep, int changes,
                         java.util.function.BiConsumer<ActivityController<? extends Activity>, Activity> recreated) {
        android.app.Application app = RuntimeEnvironment.getApplication();
        Configuration config = app.getResources().getConfiguration();
        app.onConfigurationChanged(new Configuration(config));  // its ComponentCallbacks too
        if (changes == 0) return;
        for (ActivityController<? extends Activity> c : controllers) {
            Activity old = c.get();
            try {
                // shortcut: a stopped activity only gets the new resources (its views stay as drawn); relaunch it at its restart as ActivityThread does when an app shows stale colours
                if (asleep.test(old)) {
                    old.getResources().updateConfiguration(config, app.getResources().getDisplayMetrics());
                    continue;
                }
                c.configurationChange(new Configuration(config), app.getResources().getDisplayMetrics(), changes);
                if (c.get() != old) {
                    System.out.println("[apklayer] configuration change: recreated " + old.getClass().getName());
                    recreated.accept(c, old);
                }
            } catch (Throwable e) {
                System.out.println("[apklayer] configuration change failed: " + e);
                e.printStackTrace(System.out);
            }
        }
    }

    /** A new size from the shell (AppHost.hostResize): applied in place, like an activity that handles the change itself. */
    static void resize(String qualifiers, List<ActivityController<? extends Activity>> controllers) {
        setSize(qualifiers);
        // Android would recreate activities that don't declare it; recreation doesn't re-attach windows in this sandbox yet, and the startup
        // scale is applied before any launch.
        android.content.res.Resources appRes = RuntimeEnvironment.getApplication().getResources();
        for (ActivityController<? extends Activity> c : controllers) {
            try {
                Activity a = c.get();
                a.getResources().updateConfiguration(appRes.getConfiguration(), appRes.getDisplayMetrics());
                a.onConfigurationChanged(appRes.getConfiguration());
                android.view.ViewRootImpl root = a.getWindow().getDecorView().getViewRootImpl();
                if (root != null) root.updateConfiguration(Display.INVALID_DISPLAY);
            } catch (Throwable e) {
                System.out.println("[apklayer] configuration change failed: " + e);
                e.printStackTrace(System.out);
            }
        }
    }
}
