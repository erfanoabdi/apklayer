// What SystemUI's ThemeOverlayController writes into the "android" package's colour
// resources for one seed colour, as a name -> ARGB map.
//
// AOSP android14-release, frameworks/base/packages/SystemUI/src/com/android/systemui/theme/
// ThemeOverlayController.java createOverlays(color) builds THREE FabricatedOverlays:
//   neutral  : system_neutral{1,2}_<SHADE_KEY>   from ColorScheme(color, TONAL_SPOT)
//   accent   : system_accent{1,2,3}_<SHADE_KEY>  from the same ColorScheme
//   dynamic  : system_<role>_{light,dark}        from SchemeTonalSpot(Hct.fromInt(color),
//                                                  isDark, mContrast) over
//              DynamicColors.ALL_DYNAMIC_COLORS_MAPPED (packages/SystemUI/src/com/android/
//              systemui/theme/DynamicColors.kt), plus system_<fixed role> from the LIGHT
//              scheme over DynamicColors.FIXED_COLORS_MAPPED.
// Every value is written with ColorUtils.setAlphaComponent(value, 0xFF) for the ramps and
// as DynamicColor.getArgb(scheme) for the roles. This class reproduces that table; it does
// not decide WHEN to use it -- see Monet.
// See docs/monet-wallpaper-seed_2026-09-26.md.
package com.macapk.loader.monet;

import com.macapk.loader.monet.graphics.ColorUtils;
import com.macapk.loader.monet.libmonet.dynamiccolor.DynamicColor;
import com.macapk.loader.monet.libmonet.dynamiccolor.MaterialDynamicColors;
import com.macapk.loader.monet.libmonet.hct.Hct;
import com.macapk.loader.monet.libmonet.scheme.DynamicScheme;
import com.macapk.loader.monet.libmonet.scheme.SchemeTonalSpot;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MonetOverlay {
    private MonetOverlay() {}

    /** DynamicColors.ALL_DYNAMIC_COLORS_MAPPED, names in AOSP order. */
    static final String[] DYNAMIC_NAMES = {
        "primary_container", "on_primary_container", "primary", "on_primary",
        "secondary_container", "on_secondary_container", "secondary", "on_secondary",
        "tertiary_container", "on_tertiary_container", "tertiary", "on_tertiary",
        "background", "on_background", "surface", "on_surface",
        "surface_container_low", "surface_container_lowest", "surface_container",
        "surface_container_high", "surface_container_highest", "surface_bright", "surface_dim",
        "surface_variant", "on_surface_variant", "outline", "outline_variant",
        "error", "on_error", "error_container", "on_error_container",
        "control_activated", "control_normal", "control_highlight",
        "text_primary_inverse", "text_secondary_and_tertiary_inverse",
        "text_primary_inverse_disable_only", "text_secondary_and_tertiary_inverse_disabled",
        "text_hint_inverse",
        "palette_key_color_primary", "palette_key_color_secondary",
        "palette_key_color_tertiary", "palette_key_color_neutral",
        "palette_key_color_neutral_variant",
    };

    /** DynamicColors.FIXED_COLORS_MAPPED, names in AOSP order. */
    static final String[] FIXED_NAMES = {
        "primary_fixed", "primary_fixed_dim", "on_primary_fixed", "on_primary_fixed_variant",
        "secondary_fixed", "secondary_fixed_dim", "on_secondary_fixed",
        "on_secondary_fixed_variant",
        "tertiary_fixed", "tertiary_fixed_dim", "on_tertiary_fixed", "on_tertiary_fixed_variant",
    };

    /** How many entries overlayFor returns: 5 palettes x 12 shade keys, the dynamic roles in
     *  both polarities, and the fixed roles once (160). */
    static int expectedSize() {
        return 5 * ColorScheme.SHADE_KEYS.length + 2 * DYNAMIC_NAMES.length + FIXED_NAMES.length;
    }

    /** Is `name` one of the resource names overlayFor produces? Lets a cache reader reject an
     *  entry list that has the right COUNT but not the right NAMES (a stale or foreign file). */
    static boolean isOverlayName(String name) {
        if (!name.startsWith("system_")) return false;
        String n = name.substring(7);
        for (String p : new String[] {"accent1_", "accent2_", "accent3_", "neutral1_", "neutral2_"}) {
            if (n.startsWith(p)) {
                String k = n.substring(p.length());
                for (int key : ColorScheme.SHADE_KEYS) if (k.equals(Integer.toString(key))) return true;
                return false;
            }
        }
        for (String f : FIXED_NAMES) if (n.equals(f)) return true;
        for (String d : DYNAMIC_NAMES) {
            if (n.equals(d + "_light") || n.equals(d + "_dark")) return true;
        }
        return false;
    }

    private static DynamicColor dynamic(MaterialDynamicColors m, String name) {
        switch (name) {
            case "primary_container": return m.primaryContainer();
            case "on_primary_container": return m.onPrimaryContainer();
            case "primary": return m.primary();
            case "on_primary": return m.onPrimary();
            case "secondary_container": return m.secondaryContainer();
            case "on_secondary_container": return m.onSecondaryContainer();
            case "secondary": return m.secondary();
            case "on_secondary": return m.onSecondary();
            case "tertiary_container": return m.tertiaryContainer();
            case "on_tertiary_container": return m.onTertiaryContainer();
            case "tertiary": return m.tertiary();
            case "on_tertiary": return m.onTertiary();
            case "background": return m.background();
            case "on_background": return m.onBackground();
            case "surface": return m.surface();
            case "on_surface": return m.onSurface();
            case "surface_container_low": return m.surfaceContainerLow();
            case "surface_container_lowest": return m.surfaceContainerLowest();
            case "surface_container": return m.surfaceContainer();
            case "surface_container_high": return m.surfaceContainerHigh();
            case "surface_container_highest": return m.surfaceContainerHighest();
            case "surface_bright": return m.surfaceBright();
            case "surface_dim": return m.surfaceDim();
            case "surface_variant": return m.surfaceVariant();
            case "on_surface_variant": return m.onSurfaceVariant();
            case "outline": return m.outline();
            case "outline_variant": return m.outlineVariant();
            case "error": return m.error();
            case "on_error": return m.onError();
            case "error_container": return m.errorContainer();
            case "on_error_container": return m.onErrorContainer();
            case "control_activated": return m.controlActivated();
            case "control_normal": return m.controlNormal();
            case "control_highlight": return m.controlHighlight();
            case "text_primary_inverse": return m.textPrimaryInverse();
            case "text_secondary_and_tertiary_inverse": return m.textSecondaryAndTertiaryInverse();
            case "text_primary_inverse_disable_only": return m.textPrimaryInverseDisableOnly();
            case "text_secondary_and_tertiary_inverse_disabled":
                return m.textSecondaryAndTertiaryInverseDisabled();
            case "text_hint_inverse": return m.textHintInverse();
            case "palette_key_color_primary": return m.primaryPaletteKeyColor();
            case "palette_key_color_secondary": return m.secondaryPaletteKeyColor();
            case "palette_key_color_tertiary": return m.tertiaryPaletteKeyColor();
            case "palette_key_color_neutral": return m.neutralPaletteKeyColor();
            case "palette_key_color_neutral_variant": return m.neutralVariantPaletteKeyColor();
            case "primary_fixed": return m.primaryFixed();
            case "primary_fixed_dim": return m.primaryFixedDim();
            case "on_primary_fixed": return m.onPrimaryFixed();
            case "on_primary_fixed_variant": return m.onPrimaryFixedVariant();
            case "secondary_fixed": return m.secondaryFixed();
            case "secondary_fixed_dim": return m.secondaryFixedDim();
            case "on_secondary_fixed": return m.onSecondaryFixed();
            case "on_secondary_fixed_variant": return m.onSecondaryFixedVariant();
            case "tertiary_fixed": return m.tertiaryFixed();
            case "tertiary_fixed_dim": return m.tertiaryFixedDim();
            case "on_tertiary_fixed": return m.onTertiaryFixed();
            case "on_tertiary_fixed_variant": return m.onTertiaryFixedVariant();
            default: throw new IllegalArgumentException(name);
        }
    }

    /**
     * ThemeOverlayController.createOverlays(color) for Style.TONAL_SPOT, as the resource
     * name -> ARGB map the three fabricated overlays install (names WITHOUT "android:color/").
     *
     * @param color          the seed ThemeOverlayController hands both halves -- the RAW
     *                       getSeedColor answer; ColorScheme swaps an unusable one for
     *                       GOOGLE_BLUE itself, dynamicSchemeFromStyle does not
     * @param contrastLevel  UiModeManager.getContrast(): 0 standard, 0.5 medium, 1 high
     */
    public static Map<String, Integer> overlayFor(int color, double contrastLevel) {
        Map<String, Integer> out = new LinkedHashMap<>();
        ColorScheme cs = new ColorScheme(color);
        // createNeutralOverlay(), then createAccentOverlay() -- names only, order is cosmetic.
        for (Map.Entry<String, int[]> p : cs.palettes().entrySet()) {
            int[] shades = p.getValue();
            for (int i = 0; i < ColorScheme.SHADE_KEYS.length; i++) {
                out.put("system_" + p.getKey() + "_" + ColorScheme.SHADE_KEYS[i],
                        ColorUtils.setAlphaComponent(shades[i], 0xFF));
            }
        }
        DynamicScheme dark = new SchemeTonalSpot(Hct.fromInt(color), true, contrastLevel);
        DynamicScheme light = new SchemeTonalSpot(Hct.fromInt(color), false, contrastLevel);
        // createDynamicOverlay(): dark first, then light, then the fixed roles from LIGHT.
        MaterialDynamicColors mdc = new MaterialDynamicColors();
        for (String n : DYNAMIC_NAMES) {
            out.put("system_" + n + "_dark", dynamic(mdc, n).getArgb(dark));
        }
        for (String n : DYNAMIC_NAMES) {
            out.put("system_" + n + "_light", dynamic(mdc, n).getArgb(light));
        }
        for (String n : FIXED_NAMES) {
            out.put("system_" + n, dynamic(mdc, n).getArgb(light));
        }
        return out;
    }
}
