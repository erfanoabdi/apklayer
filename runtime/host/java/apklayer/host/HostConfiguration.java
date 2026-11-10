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
 * scale, UiModeManager's night mode and contrast, the dynamic colour palette (HostPalette). AppHost hands a change to its activities as ActivityThread's handleConfigurationChanged does.
 */
final class HostConfiguration {
    private HostConfiguration() {}

    /** The configuration bits the host settings change (what decides whether an activity handles the change itself or is recreated). */
    static final int SETTINGS_CHANGES = android.content.pm.ActivityInfo.CONFIG_UI_MODE | android.content.pm.ActivityInfo.CONFIG_LOCALE
            | android.content.pm.ActivityInfo.CONFIG_LAYOUT_DIRECTION | android.content.pm.ActivityInfo.CONFIG_FONT_SCALE;
    private static String size = System.getProperty("apklayer.qualifiers", "w480dp-h800dp");
    private static Bundle settings = new Bundle();
    private static android.graphics.Rect screen;

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

    /** The desktop session's lock (the hub's "locked", logind's LockedHint): KeyguardManager.isKeyguardLocked and isDeviceLocked, as TrustManager has them. */
    private static void locked(boolean locked) {
        org.robolectric.util.ReflectionHelpers.setStaticField(org.robolectric.shadows.ShadowKeyguardManager.class, "isKeyguardLocked", locked);
        org.robolectric.util.ReflectionHelpers.setStaticField(org.robolectric.shadows.ShadowKeyguardManager.class, "isDeviceLocked", locked);
        java.util.Set<Integer> users = org.robolectric.util.ReflectionHelpers.getStaticField(org.robolectric.shadows.ShadowKeyguardManager.class, "deviceLockedForUsers");
        if (locked) users.add(android.os.UserHandle.myUserId()); else users.remove(android.os.UserHandle.myUserId());
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
        locked(settings.getBoolean("locked"));
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
        if (modes != null && settings.containsKey("contrast")) {  // UiModeManager's ContrastChangeListeners (getContrast: the Globals shadow below)
            // the user's callback as UiModeManagerService calls it (the shadow's setContrast picks its path by API level, 36, not this framework's Globals)
            try {
                java.lang.reflect.Field globals = android.app.UiModeManager.class.getDeclaredField("sGlobals");
                globals.setAccessible(true);
                java.lang.reflect.Method user = globals.getType().getDeclaredMethod("getUserCallbackOrCreate", int.class);
                user.setAccessible(true);
                Object callback = user.invoke(globals.get(null), android.os.UserHandle.myUserId());
                java.lang.reflect.Method notify = callback.getClass().getDeclaredMethod("notifyContrastChanged", float.class);
                notify.setAccessible(true);
                notify.invoke(callback, settings.getFloat("contrast"));
            } catch (ReflectiveOperationException | RuntimeException e) { System.out.println("[apklayer] UiModeManager contrast not set: " + e); }
        }
        int changes = before.diff(RuntimeEnvironment.getApplication().getResources().getConfiguration()) & SETTINGS_CHANGES;
        if (HostPalette.update(settings.containsKey("accent") ? settings.getInt("accent") : null, Boolean.TRUE.equals(night), settings.getFloat("contrast", 0f)))
            changes |= android.content.pm.ActivityInfo.CONFIG_ASSETS_PATHS;  // a new overlay on a device: no activity handles it itself
        System.out.println("[apklayer] host settings " + settings.keySet().stream().sorted().map(k -> k + "=" + settings.get(k)).toList() + (changes != 0 ? " changes=0x" + Integer.toHexString(changes) : ""));
        return changes;
    }

    /** The window's size qualifiers (AppHost.hostResize); the settings' stay on top of them. */
    static void setSize(String qualifiers) { size = qualifiers; apply(); }

    /** The screen in pixels (AppHost.hostResize: the shell's output), null: unknown (Robolectric's max bounds, the window). */
    static void setScreen(android.graphics.Rect bounds) {
        if (bounds != null && !bounds.equals(screen) && (screen == null || bounds.width() * bounds.height() != screen.width() * screen.height()))
            android.view.ViewConfiguration.resetCacheForTesting();  // one made before the screen was known (cached per density) has a window-sized drawing cache limit
        screen = bounds;
    }

    private static void apply() {
        StringBuilder extra = new StringBuilder();
        String locale = settings.getString("locale");
        if (locale != null && !locale.isEmpty()) extra.append("b+").append(locale.replace('-', '+'));  // BCP-47 as a resource qualifier
        Boolean night = night();
        if (night != null) extra.append(extra.length() > 0 ? "-" : "").append(night ? "night" : "notnight");
        RuntimeEnvironment.setQualifiers(extra.length() > 0 ? size + " +" + extra : size);
        if (screen != null) {  // Robolectric's qualifiers make the max bounds the window (DeviceConfig.setBounds); on Android they are the display
            // and so is the display mode (Display.getMode: the panel, at rotation 0 the real size); Robolectric's is the window. Before the max
            // bounds: changeDisplay resets the default display's to its logical size
            android.view.DisplayInfo info = android.hardware.display.DisplayManagerGlobal.getInstance().getDisplayInfo(android.view.Display.DEFAULT_DISPLAY);
            android.view.Display.Mode mode = info.getMode();
            info.supportedModes = new android.view.Display.Mode[] {new android.view.Display.Mode(mode.getModeId(), screen.width(), screen.height(), mode.getRefreshRate())};
            if (org.robolectric.util.ReflectionHelpers.hasField(android.view.DisplayInfo.class, "appsSupportedModes"))
                org.robolectric.util.ReflectionHelpers.setField(info, "appsSupportedModes", info.supportedModes);
            org.robolectric.util.ReflectionHelpers.callInstanceMethod(org.robolectric.shadow.api.Shadow.extract(android.hardware.display.DisplayManagerGlobal.getInstance()), "changeDisplay",
                    org.robolectric.util.ReflectionHelpers.ClassParameter.from(int.class, android.view.Display.DEFAULT_DISPLAY),
                    org.robolectric.util.ReflectionHelpers.ClassParameter.from(android.view.DisplayInfo.class, info));
            Configuration c = new Configuration(RuntimeEnvironment.getApplication().getResources().getConfiguration());
            c.windowConfiguration.setMaxBounds(screen);
            c.seq = 0;  // unsequenced: always applied
            android.app.ResourcesManager.getInstance().applyConfigurationToResources(c, null);
        }
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
                if (asleep.test(old)) {  // the new resources now, the relaunch at its restart (ActivityThread defers it until the activity is visible)
                    old.getResources().updateConfiguration(config, app.getResources().getDisplayMetrics());
                    pending.merge(old, changes, (a, b) -> a | b);
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

    /** UiModeManager.getContrast(): the instance's contrast, where Robolectric's IUiModeManager stand-in answers 0 once no listener keeps the user's callback. */
    @org.robolectric.annotation.Implements(className = "android.app.UiModeManager$Globals", isInAndroidSdk = false)
    public static final class ShadowGlobals {
        @org.robolectric.annotation.Implementation protected float getContrast(int userId) { return settings.getFloat("contrast", 0f); }
    }

    /** Changes that arrived while an activity was stopped, applied by {@link #relaunch} at its restart. */
    private static final java.util.Map<Activity, Integer> pending = new java.util.WeakHashMap<>();

    /**
     * A stopped activity coming back (ActivityTask.restart): the changes it missed while stopped, as ActivityThread relaunches it then
     * (recreated unless it handles them itself; Robolectric's recreation leaves it stopped). Returns the activity it replaced, or null.
     */
    static Activity relaunch(ActivityController<? extends Activity> c) {
        Activity old = c.get();
        Integer changes = pending.remove(old);
        if (changes == null) return null;
        android.content.res.Resources res = RuntimeEnvironment.getApplication().getResources();
        try {
            c.configurationChange(new Configuration(res.getConfiguration()), res.getDisplayMetrics(), changes);
        } catch (Throwable e) {
            System.out.println("[apklayer] configuration change failed: " + e);
            e.printStackTrace(System.out);
        }
        if (c.get() == old) return null;
        System.out.println("[apklayer] configuration change: relaunched " + old.getClass().getName() + " at its restart");
        return old;
    }

    /** A phone's turn (AppHost.hostResize): the new size; returns the configuration changes for {@link #dispatch}. */
    static int turn(String qualifiers) {
        Configuration before = new Configuration(RuntimeEnvironment.getApplication().getResources().getConfiguration());
        setSize(qualifiers);
        return before.diff(RuntimeEnvironment.getApplication().getResources().getConfiguration());
    }

    /** A new size from the shell (AppHost.hostResize): applied in place, like an activity that handles the change itself. */
    static void resize(String qualifiers, List<ActivityController<? extends Activity>> controllers) {
        setSize(qualifiers);
        // a resize of the same orientation in place (a turn recreates: AppHost.hostResize); Android would recreate those that don't declare it; the startup
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
        // the other windows (dialogs, popups, an SDK's own windows) are resized too, as the window manager resizes every window of the app: a
        // MATCH_PARENT dialog's requestLayout alone keeps its old frame (ViewRootImpl relayouts only when its size or configuration changed),
        // and PGR's login dialog stayed a dim rectangle of the old window size after the window moved to a monitor of another scale
        List<android.view.ViewRootImpl> roots = org.robolectric.util.ReflectionHelpers.getField(android.view.WindowManagerGlobal.getInstance(), "mRoots");
        for (android.view.ViewRootImpl root : new java.util.ArrayList<>(roots)) {
            try { root.updateConfiguration(Display.INVALID_DISPLAY); }
            catch (Throwable e) { System.out.println("[apklayer] window configuration change failed: " + e); }
        }
    }
}
