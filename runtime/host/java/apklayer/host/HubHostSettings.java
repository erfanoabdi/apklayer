package apklayer.host;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * The desktop's settings as the instance's system settings, live: the hub reads them through the xdg Settings portal (bridge/portal/settings.py),
 * lays the instance's overrides over them (`apklayer settings`: the instance's `settings` file) and does what the system does on a device when
 * they change: SettingsProvider's values (UiModeManagerService's UI_NIGHT_MODE, FONT_SCALE, TIME_12_24, the animation scales, CONTRAST_LEVEL,
 * ThemeOverlayController's colour seed), persist.sys.timezone and persist.sys.locale, the new configuration to every running app
 * (ENDPOINT_CONFIGURATION, HostConfiguration) and the broadcasts (CONFIGURATION_CHANGED, LOCALE_CHANGED, TIMEZONE_CHANGED, TIME_SET).
 */
final class HubHostSettings {
    /** The override keys (`apklayer settings set <key> <value>`) and their values. */
    static final String KEYS = "night yes|no, accent #rrggbb, contrast high|normal, animations on|off, font-scale <float>, locale <BCP-47>, timezone <tz id>, time-format 12|24, window-mode[.<package>] auto|mobile|desktop";
    private final Context context;
    private final HubDirectory directory;
    private final Map<String, String> host = new HashMap<>();
    private Bundle current = new Bundle();

    HubHostSettings(Context context, HubDirectory directory) { this.context = context; this.directory = directory; }

    /** What an app process starts with (HubProtocol.HOST_SETTINGS): once the desktop's are known (or FIRST_WAIT_S passed), so its first frame has them. */
    Bundle current() {
        try { first.await(FIRST_WAIT_S, java.util.concurrent.TimeUnit.SECONDS); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        synchronized (this) { return new Bundle(current); }
    }
    private final java.util.concurrent.CountDownLatch first = new java.util.concurrent.CountDownLatch(1);
    private static final long FIRST_WAIT_S = 3;

    /** Reads the desktop's settings until the hub ends (a thread of its own); without python or a portal the overrides alone apply. */
    void watch() {
        Process reader = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(HostProcess.dying("python3", HostLocation.script("settings.py"))).redirectError(ProcessBuilder.Redirect.DISCARD);
            if (System.getenv("DBUS_SESSION_BUS_ADDRESS") == null && System.getenv("XDG_RUNTIME_DIR") != null)
                builder.environment().put("DBUS_SESSION_BUS_ADDRESS", "unix:path=" + System.getenv("XDG_RUNTIME_DIR") + "/bus");
            reader = builder.start();
        } catch (IOException noPython) {
            System.out.println("[apklayer-hub] host settings: no reader (" + noPython.getMessage() + ")");
        }
        if (reader == null) { changed(); return; }
        boolean ready = false;
        try (BufferedReader lines = new BufferedReader(new InputStreamReader(reader.getInputStream()))) {
            for (String line; (line = lines.readLine()) != null; ) {
                if (line.equals("ready")) { ready = true; changed(); continue; }
                String[] kv = line.split(" ", 2);
                if (kv.length < 2 || kv[0].equals("unavailable")) { System.out.println("[apklayer-hub] host settings: " + line); continue; }
                synchronized (this) { host.put(kv[0], kv[1]); }
                if (ready) changed();
            }
        } catch (IOException ended) { /* the reader went away */ }
        if (!ready) changed();  // a reader that failed (no python gi): the overrides still apply
    }

    /** The instance's overrides (written by the CLI, cli/manage.cpp settingsCommand; the CLI then calls SETTINGS_CHANGED). */
    static Path overridesFile() { return Path.of(System.getProperty("apklayer.storage")).getParent().resolve("settings"); }

    private static Map<String, String> overrides() {
        Map<String, String> out = new HashMap<>();
        try {
            for (String line : Files.readAllLines(overridesFile())) {
                int eq = line.indexOf('=');
                if (eq > 0 && !line.startsWith("#")) out.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
            }
        } catch (IOException none) { /* no overrides */ }
        return out;
    }

    /** The settings as the instance sees them: an override wins over the desktop's value; a setting neither has stays out (Android's default). */
    private synchronized Bundle effective() {
        Map<String, String> o = overrides();
        Bundle b = new Bundle();
        String scheme = host.get("color-scheme");
        String night = o.getOrDefault("night", scheme == null ? null : "1".equals(scheme) ? "yes" : "no");  // "no preference" is light, Android's default
        if (night != null) b.putBoolean("night", night.equals("yes"));
        Integer accent = o.containsKey("accent") ? parseColour(o.get("accent")) : rgb(host.get("accent-color"));
        if (accent != null) b.putInt("accent", accent);
        String contrast = o.getOrDefault("contrast", host.get("contrast") == null ? null : "1".equals(host.get("contrast")) ? "high" : "normal");
        if (contrast != null) b.putFloat("contrast", contrast.equals("high") ? 1f : 0f);
        String animations = o.get("animations");
        if (animations == null && host.containsKey("reduced-motion")) animations = "1".equals(host.get("reduced-motion")) ? "off" : "on";
        if (animations == null && host.containsKey("enable-animations")) animations = "false".equals(host.get("enable-animations")) ? "off" : "on";  // portals before reduced-motion
        if (animations != null) b.putFloat("animatorScale", animations.equals("off") ? 0f : 1f);
        Float fontScale = parseFloat(o.getOrDefault("font-scale", host.get("text-scaling-factor")));
        if (fontScale != null && fontScale > 0) b.putFloat("fontScale", fontScale);
        String locale = o.containsKey("locale") ? languageTag(o.get("locale")) : posixLocale(host.get("locale"));
        if (locale != null) b.putString("locale", locale);
        String zone = o.getOrDefault("timezone", host.get("timezone"));
        if (zone != null && !zone.isEmpty()) b.putString("timezone", zone);
        String format = o.getOrDefault("time-format", host.get("clock-format") == null ? null : host.get("clock-format").startsWith("12") ? "12" : "24");
        if (format != null) b.putBoolean("time24", !format.startsWith("12"));
        for (Map.Entry<String, String> e : o.entrySet())  // the window style: the instance's, and an app's own (window-mode.<package>); the shell applies it
            if (e.getKey().equals("window-mode") || e.getKey().startsWith("window-mode.")) b.putString("windowMode" + e.getKey().substring(11), e.getValue());
        return b;
    }

    /** The desktop or the overrides changed: apply what differs from the instance's current settings. */
    void changed() { synchronized (applying) { apply(); } }
    private final Object applying = new Object();

    private void apply() {
        Bundle next = effective(), before;
        synchronized (this) { before = current; current = next; }
        boolean boot = first.getCount() > 0;
        first.countDown();  // before the settings provider is written to: its own process registers with HOST_SETTINGS
        if (same(before, next)) return;
        System.out.println("[apklayer-hub] host settings " + describe(next));
        HubBootSettings.awaitBoot();  // the hub's resolver reaches the settings provider from then on
        writeSystemSettings(before, next);
        for (HubDirectory.Peer peer : directory.peersExcept(null)) {
            try { HubProtocol.call(peer.endpoint(), HubProtocol.ENDPOINT, HubProtocol.ENDPOINT_CONFIGURATION, request -> request.writeBundle(next), reply -> null); }
            catch (IllegalStateException gone) { /* it ended meanwhile */ }
        }
        if (boot) return;  // the instance's first settings: nothing changed for anyone
        if (differs(before, next, "night") || differs(before, next, "locale") || differs(before, next, "fontScale"))
            directory.sendSystem(new Intent(Intent.ACTION_CONFIGURATION_CHANGED).addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY), false);
        if (differs(before, next, "locale")) directory.sendSystem(new Intent(Intent.ACTION_LOCALE_CHANGED), false);
        if (differs(before, next, "timezone")) directory.sendSystem(new Intent(Intent.ACTION_TIMEZONE_CHANGED).putExtra(Intent.EXTRA_TIMEZONE, next.getString("timezone")), false);
        if (differs(before, next, "time24"))
            directory.sendSystem(new Intent(Intent.ACTION_TIME_CHANGED).putExtra(Intent.EXTRA_TIME_PREF_24_HOUR_FORMAT, next.getBoolean("time24") ? 1 : 0), false);
    }

    private void writeSystemSettings(Bundle before, Bundle next) {
        ContentResolver resolver = context.getContentResolver();
        try {
            if (next.containsKey("night")) Settings.Secure.putInt(resolver, Settings.Secure.UI_NIGHT_MODE, next.getBoolean("night") ? 2 : 1);  // UiModeManager.MODE_NIGHT_YES/NO
            if (next.containsKey("fontScale")) Settings.System.putFloat(resolver, Settings.System.FONT_SCALE, next.getFloat("fontScale"));
            if (next.containsKey("time24")) Settings.System.putString(resolver, Settings.System.TIME_12_24, next.getBoolean("time24") ? "24" : "12");
            if (next.containsKey("animatorScale")) {
                for (String key : new String[] {Settings.Global.ANIMATOR_DURATION_SCALE, Settings.Global.TRANSITION_ANIMATION_SCALE, Settings.Global.WINDOW_ANIMATION_SCALE})
                    Settings.Global.putFloat(resolver, key, next.getFloat("animatorScale"));
            }
            if (next.containsKey("contrast")) Settings.Secure.putFloat(resolver, Settings.Secure.CONTRAST_LEVEL, next.getFloat("contrast"));
            if (next.containsKey("accent") && differs(before, next, "accent"))  // what ThemeOverlayController (SystemUI) reads for the dynamic colour palette
                Settings.Secure.putString(resolver, Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, String.format(
                        "{\"android.theme.customization.system_palette\":\"%08X\",\"android.theme.customization.color_source\":\"preset\"}", next.getInt("accent")));
            if (next.containsKey("timezone")) android.os.SystemProperties.set("persist.sys.timezone", next.getString("timezone"));
            if (next.containsKey("locale")) android.os.SystemProperties.set("persist.sys.locale", next.getString("locale"));
        } catch (RuntimeException failed) {
            System.out.println("[apklayer-hub] host settings not written: " + failed);
            failed.printStackTrace(System.out);
        }
    }

    private static boolean differs(Bundle a, Bundle b, String key) { return !java.util.Objects.equals(a.get(key), b.get(key)); }

    private static boolean same(Bundle a, Bundle b) {
        if (!a.keySet().equals(b.keySet())) return false;
        for (String key : a.keySet()) if (differs(a, b, key)) return false;
        return true;
    }

    private static String describe(Bundle b) { return b.keySet().stream().sorted().map(k -> k + "=" + b.get(k)).toList().toString(); }

    /** "#rrggbb" (or "rrggbb") as an opaque ARGB colour; null when it is none. */
    static Integer parseColour(String text) {
        if (text == null) return null;
        String hex = text.startsWith("#") ? text.substring(1) : text;
        if (!hex.matches("[0-9a-fA-F]{6}")) return null;
        return 0xff000000 | Integer.parseInt(hex, 16);
    }

    /** The portal's accent-color "r,g,b" (0..1 each; outside that range: none chosen) as an ARGB colour. */
    private static Integer rgb(String text) {
        if (text == null) return null;
        String[] parts = text.split(",");
        if (parts.length != 3) return null;
        int argb = 0xff000000;
        for (int i = 0; i < 3; i++) {
            Float c = parseFloat(parts[i]);
            if (c == null || c < 0 || c > 1) return null;
            argb |= Math.round(c * 255) << (16 - 8 * i);
        }
        return argb;
    }

    private static Float parseFloat(String text) {
        if (text == null) return null;
        try { return Float.parseFloat(text.trim()); } catch (NumberFormatException no) { return null; }
    }

    /** A POSIX locale ("de_DE.UTF-8", "sr_RS@latin") as a BCP-47 tag; null for C/POSIX (no language chosen). */
    static String posixLocale(String posix) {
        if (posix == null) return null;
        String base = posix.split("[.@]", 2)[0];
        if (base.isEmpty() || base.equals("C") || base.equals("POSIX")) return null;
        return languageTag(base.replace('_', '-'));
    }

    /** A well-formed BCP-47 tag (what the apps' resource qualifiers can take), or null. */
    static String languageTag(String text) {
        String tag = java.util.Locale.forLanguageTag(text.trim()).toLanguageTag();
        return tag.equals("und") ? null : tag;
    }
}
