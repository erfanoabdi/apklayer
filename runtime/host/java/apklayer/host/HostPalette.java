package apklayer.host;

import android.app.Activity;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.loader.ResourcesLoader;
import android.content.res.loader.ResourcesProvider;
import android.util.Pair;
import android.util.SparseIntArray;
import com.android.systemui.monet.ColorScheme;
import com.android.systemui.monet.DynamicColors;
import com.google.ux.material.libmonet.dynamiccolor.DynamicColor;
import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.robolectric.RuntimeEnvironment;

/**
 * Dynamic colour (Material You) from the instance's accent: SystemUI's ThemeOverlayController builds monet's light and dark ColorScheme from
 * the seed (the instance's contrast too) and overlays the framework's system_* colours with them. Here no overlay manager exists, so the public
 * colours (system_neutral1_0 .. system_error_1000) take the current scheme's values through a ResourcesLoader on the app's and each activity's
 * resources, built as RemoteViews.ColorResources does for widgets (framework-res's compiled-table template raw/remote_views_color_resources).
 */
final class HostPalette {
    private HostPalette() {}

    private static final int FIRST = android.R.color.system_neutral1_0, LAST = android.R.color.system_error_1000;
    private static final int ENTRY_SIZE = 16;  // an entry in the template table's colour array
    private static ResourcesLoader loader;
    private static boolean watching;
    private static long built;  // ms the last palette took
    private static String applied = "";  // seed, scheme and contrast the loader shows

    /** The palette for this seed (null: the framework's defaults); true when it changed (activities are recreated: CONFIG_ASSETS_PATHS). */
    static boolean update(Integer seed, boolean dark, float contrast) {
        String key = seed == null ? "" : String.format("%08X/%s/%s", seed, dark, contrast);
        if (key.equals(applied)) return false;
        applied = key;
        android.app.Application app = RuntimeEnvironment.getApplication();
        ResourcesLoader old = loader;
        loader = null;
        if (old != null) {
            app.getResources().removeLoaders(old);
            for (var c : ActivityTask.stack) c.get().getResources().removeLoaders(old);
        }
        if (seed != null) {
            try {
                long t0 = System.nanoTime();
                loader = build(app.getResources(), seed, dark, contrast);
                built = (System.nanoTime() - t0) / 1_000_000;
            } catch (Exception | LinkageError e) {
                System.out.println("[apklayer] dynamic colour not applied: " + e);
                return old != null;
            }
            if (!watching) app.registerActivityLifecycleCallbacks(new android.app.Application.ActivityLifecycleCallbacks() {  // activities created later
                @Override public void onActivityPreCreated(Activity a, android.os.Bundle state) { attach(a); }
                @Override public void onActivityCreated(Activity a, android.os.Bundle state) {}
                @Override public void onActivityStarted(Activity a) {}
                @Override public void onActivityResumed(Activity a) {}
                @Override public void onActivityPaused(Activity a) {}
                @Override public void onActivityStopped(Activity a) {}
                @Override public void onActivitySaveInstanceState(Activity a, android.os.Bundle state) {}
                @Override public void onActivityDestroyed(Activity a) {}
            });
            watching = true;
            attach(app.getResources());
            for (var c : ActivityTask.stack) attach(c.get());
            System.out.println("[apklayer] dynamic colour " + key + " (" + built + " ms): system_accent1_600 #" + Integer.toHexString(app.getColor(android.R.color.system_accent1_600)));
        }
        return true;
    }

    private static void attach(Activity a) { attach(a.getResources()); }

    private static void attach(Resources res) {
        if (loader != null && !res.getLoaders().contains(loader)) res.addLoaders(loader);
    }

    private static ResourcesLoader build(Resources res, int seed, boolean dark, float contrast) throws Exception {
        int style = new ColorScheme(seed, false).getStyle();
        ColorScheme light = new ColorScheme(seed, false, style, contrast), night = new ColorScheme(seed, true, style, contrast);
        Map<String, DynamicColor> tokens = new HashMap<>();
        for (List<Pair<String, DynamicColor>> list : List.of(DynamicColors.getAllAccentPalette(), DynamicColors.getAllNeutralPalette(),
                DynamicColors.getAllErrorPalette(), DynamicColors.getAllDynamicColorsMapped(), DynamicColors.getFixedColorsMapped(),
                DynamicColors.getCustomColorsMapped()))
            for (Pair<String, DynamicColor> p : list) tokens.put(p.first, p.second);
        // ThemeOverlayController writes system_<token>_light and _dark; the public names are either those or <token> for the current scheme
        SparseIntArray colours = new SparseIntArray();
        for (int id = FIRST; id <= LAST; id++) {
            String name;
            try { name = res.getResourceEntryName(id); } catch (Resources.NotFoundException none) { continue; }
            if (!name.startsWith("system_")) continue;
            String token = name.substring(7);
            ColorScheme scheme = dark ? night : light;
            if (token.endsWith("_light") && tokens.containsKey(token.substring(0, token.length() - 6))) { scheme = light; token = token.substring(0, token.length() - 6); }
            else if (token.endsWith("_dark") && tokens.containsKey(token.substring(0, token.length() - 5))) { scheme = night; token = token.substring(0, token.length() - 5); }
            DynamicColor colour = tokens.get(token);
            if (colour != null) colours.put(id, colour.getArgb(scheme.getMaterialScheme()));
        }
        // RemoteViews.ColorResources.createCompiledResourcesContent: the template's values are the last entries, indexed by the id's low half
        byte[] table;
        try (InputStream in = res.openRawResource(com.android.internal.R.raw.remote_views_color_resources)) { table = in.readAllBytes(); }
        int values = table.length - (LAST & 0xffff) * ENTRY_SIZE - 4;
        if (values < 0) throw new IllegalStateException("the colour table template is invalid");
        for (int id = FIRST; id <= LAST; id++) {
            int offset = values + (id & 0xffff) * ENTRY_SIZE, value = colours.get(id, res.getColor(id, null));
            for (int b = 0; b < 4; b++, value >>= 8) table[offset + b] = (byte) value;
        }
        // at night framework-res's values-night entries (system_accent1_0 -> @color/system_accent1_0_dark) outrank the template's default
        // config; a loader wins only at an equal config, so the dark table carries the night qualifier (ResTable_config.uiMode)
        if (dark) table[typeConfig(table) + 29] = (byte) Configuration.UI_MODE_NIGHT_YES;
        // a directory per table (ApkAssets is cached by path); memfd as in ColorResources is not needed on a JVM
        File dir = new File(RuntimeEnvironment.getApplication().getCodeCacheDir(), "apklayer-palette/" + Integer.toHexString(java.util.Arrays.hashCode(table)));
        if (!dir.isDirectory() && !dir.mkdirs()) throw new java.io.IOException("cannot create " + dir);
        java.nio.file.Files.write(new File(dir, "resources.arsc").toPath(), table);
        ResourcesLoader made = new ResourcesLoader();
        made.addProvider(ResourcesProvider.loadFromDirectory(dir.getPath(), null));
        return made;
    }

    /** Offset of the ResTable_config in the table's one RES_TABLE_TYPE_TYPE chunk (after its 20-byte head), found by walking the chunks. */
    private static int typeConfig(byte[] t) {
        java.nio.ByteBuffer b = java.nio.ByteBuffer.wrap(t).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        // the table's chunks (string pool, package) after its header; the package's (type/key pools, type spec, type) after the package header
        for (int at = b.getShort(2); at + 8 <= t.length && b.getInt(at + 4) > 0; at += b.getInt(at + 4)) {
            if (b.getShort(at) != 0x0200) continue;
            for (int in = at + b.getShort(at + 2); in + 8 <= at + b.getInt(at + 4) && b.getInt(in + 4) > 0; in += b.getInt(in + 4))
                if (b.getShort(in) == 0x0201) return in + 20;
        }
        throw new IllegalStateException("no type chunk in the colour table template");
    }
}
