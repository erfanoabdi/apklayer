// PORTED (Apache-2.0) from AOSP android14-release
//   frameworks/base/packages/SystemUI/monet/src/com/android/systemui/monet/ColorScheme.kt
//   frameworks/base/packages/SystemUI/monet/src/com/android/systemui/monet/Shades.java
// Kotlin -> Java, and ONLY Style.TONAL_SPOT: ThemeOverlayController reads the style from
// Settings.Secure THEME_CUSTOMIZATION_OVERLAY_PACKAGES, whose default is TONAL_SPOT, and
// Mac.apk has no wallpaper picker that could choose another. The arithmetic, including
// which values are float and which are double, is kept exactly as the Kotlin spells it,
// because CTS (tests/tests/graphics/res/xml/valid_themes.xml) demands BYTE-EXACT ramps.
// See docs/monet-wallpaper-seed_2026-09-26.md.
/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.macapk.loader.monet;

import com.macapk.loader.monet.cam.Cam;
import com.macapk.loader.monet.graphics.ColorUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ColorScheme {

    /** ColorScheme.kt: const val GOOGLE_BLUE = 0xFF1b6ef3.toInt(). */
    public static final int GOOGLE_BLUE = 0xFF1b6ef3;
    /** ColorScheme.kt: const val ACCENT1_CHROMA = 48.0f. */
    static final float ACCENT1_CHROMA = 48.0f;
    /** ColorScheme.kt: const val MIN_CHROMA = 5. */
    static final int MIN_CHROMA = 5;

    /** TonalPalette.SHADE_KEYS: the resource suffixes ThemeOverlayController overlays.
     *  Note there is no 0: system_*_0 is never overlaid and stays the #ffffff static. */
    public static final int[] SHADE_KEYS = {10, 50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 1000};

    /** Shades.MIDDLE_LSTAR: the smallest L* that contrasts >= 4.5 with L* 100. */
    static final float MIDDLE_LSTAR = 49.6f;

    /** The TONAL_SPOT CoreSpec, as (hue rotation added, chroma) per palette:
     *  a1 = TonalSpec(HueSource(), ChromaConstant(36.0)), a2 = (HueSource, 16.0),
     *  a3 = (HueAdd(60.0), 24.0), n1 = (HueSource, 6.0), n2 = (HueSource, 8.0). */
    private static final double[] TONAL_SPOT_HUE_ADD = {0.0, 0.0, 60.0, 0.0, 0.0};
    private static final double[] TONAL_SPOT_CHROMA = {36.0, 16.0, 24.0, 6.0, 8.0};

    public final int seed;
    /** The seed the palettes were actually built from (GOOGLE_BLUE for an unusable seed). */
    public final int seedArgb;
    public final int[] accent1, accent2, accent3, neutral1, neutral2;

    /** ColorScheme(seed, darkTheme, Style.TONAL_SPOT). darkTheme does not reach the palettes. */
    public ColorScheme(int seed) {
        this.seed = seed;
        Cam proposedSeedCam = Cam.fromInt(seed);
        // Style.CONTENT is the only style that keeps a low-chroma seed; TONAL_SPOT is not it.
        if (seed == 0 /* Color.TRANSPARENT */) {
            seedArgb = GOOGLE_BLUE;
        } else if (proposedSeedCam.getChroma() < 5) {
            seedArgb = GOOGLE_BLUE;
        } else {
            seedArgb = seed;
        }
        Cam seedCam = Cam.fromInt(seedArgb);
        accent1 = shades(seedCam, 0);
        accent2 = shades(seedCam, 1);
        accent3 = shades(seedCam, 2);
        neutral1 = shades(seedCam, 3);
        neutral2 = shades(seedCam, 4);
    }

    /** TonalSpec.shades(sourceColor): Shades.of(hue.toFloat(), chroma.toFloat()). */
    private static int[] shades(Cam sourceColor, int spec) {
        double hue = TONAL_SPOT_HUE_ADD[spec] == 0.0
                ? (double) sourceColor.getHue()                                  // HueSource
                : wrapDegreesDouble((double) sourceColor.getHue() + TONAL_SPOT_HUE_ADD[spec]);
        double chroma = TONAL_SPOT_CHROMA[spec];                                 // ChromaConstant
        return shadesOf((float) hue, (float) chroma);
    }

    /** Shades.of(hue, chroma): 12 shades, lightness descending (L* 99, 95, 90 ... 10, 0). */
    static int[] shadesOf(float hue, float chroma) {
        int[] shades = new int[12];
        // At tone 90 and above, blue and yellow hues can reach a much higher chroma.
        // To preserve a consistent appearance across all hues, use a maximum chroma of 40.
        shades[0] = ColorUtils.CAMToColor(hue, Math.min(40f, chroma), 99);
        shades[1] = ColorUtils.CAMToColor(hue, Math.min(40f, chroma), 95);
        for (int i = 2; i < 12; i++) {
            float lStar = (i == 6) ? MIDDLE_LSTAR : 100 - 10 * (i - 1);
            shades[i] = ColorUtils.CAMToColor(hue, chroma, lStar);
        }
        return shades;
    }

    /** The five palettes in ThemeOverlayController order, as (name, shades) pairs. */
    public Map<String, int[]> palettes() {
        Map<String, int[]> m = new LinkedHashMap<>();
        m.put("accent1", accent1);
        m.put("accent2", accent2);
        m.put("accent3", accent3);
        m.put("neutral1", neutral1);
        m.put("neutral2", neutral2);
        return m;
    }

    /** ColorScheme.wrapDegreesDouble. */
    static double wrapDegreesDouble(double degrees) {
        if (degrees < 0) {
            return (degrees % 360) + 360;
        } else if (degrees >= 360) {
            return degrees % 360;
        } else {
            return degrees;
        }
    }

    private static int wrapDegrees(int degrees) {
        if (degrees < 0) {
            return (degrees % 360) + 360;
        } else if (degrees >= 360) {
            return degrees % 360;
        } else {
            return degrees;
        }
    }

    /** ColorScheme.hueDiff(a: Float, b: Float): Float -- float arithmetic throughout. */
    private static float hueDiff(float a, float b) {
        return 180f - Math.abs(Math.abs(a - b) - 180f);
    }

    /** ColorScheme.score(cam, proportion). `cam.chroma - ACCENT1_CHROMA` is a Float
     *  subtraction in the Kotlin, widened to Double only for the multiply. */
    private static double score(Cam cam, double proportion) {
        double proportionScore = 0.7 * 100.0 * proportion;
        double chromaScore = cam.getChroma() < ACCENT1_CHROMA
                ? 0.1 * (double) (cam.getChroma() - ACCENT1_CHROMA)
                : 0.3 * (double) (cam.getChroma() - ACCENT1_CHROMA);
        return chromaScore + proportionScore;
    }

    /** ColorScheme.getSeedColor(wallpaperColors, filter = true). */
    public static int getSeedColor(WallpaperColorsData wallpaperColors) {
        return getSeedColors(wallpaperColors, true).get(0);
    }

    /** ColorScheme.getSeedColors(wallpaperColors, filter): filters and ranks colors.
     *  The Kotlin collection operations (mapValues, filter, sortByDescending) all preserve
     *  the source map's iteration order and the sort is stable, so ties break by the
     *  HashMap order of WallpaperColors.mAllColors -- reproduced by iterating that same
     *  map through LinkedHashMaps here. */
    public static List<Integer> getSeedColors(WallpaperColorsData wallpaperColors, boolean filter) {
        Map<Integer, Integer> allColors = wallpaperColors.getAllColors();
        int sum = 0;
        boolean first = true;
        for (int v : allColors.values()) {
            sum = first ? v : sum + v;   // Kotlin reduce { a, b -> a + b } on Int
            first = false;
        }
        if (first) throw new UnsupportedOperationException("Empty collection can't be reduced.");
        final double totalPopulation = (double) sum;
        final boolean totalPopulationMeaningless = (totalPopulation == 0.0);
        if (totalPopulationMeaningless) {
            // WallpaperColors with a population of 0 indicate the colors didn't come from
            // quantization. Instead of scoring, trust the ordering of the provided primary
            // secondary/tertiary colors.
            List<Integer> distinctColors = new ArrayList<>();
            for (int c : wallpaperColors.getMainColorInts()) {
                if (distinctColors.contains(c)) continue;
                if (!filter || Cam.fromInt(c).getChroma() >= MIN_CHROMA) distinctColors.add(c);
            }
            if (distinctColors.isEmpty()) {
                List<Integer> gb = new ArrayList<>();
                gb.add(GOOGLE_BLUE);
                return gb;
            }
            return distinctColors;
        }

        Map<Integer, Double> intToProportion = new LinkedHashMap<>();
        Map<Integer, Cam> intToCam = new LinkedHashMap<>();
        for (Map.Entry<Integer, Integer> e : allColors.entrySet()) {
            intToProportion.put(e.getKey(), e.getValue().doubleValue() / totalPopulation);
        }
        for (Map.Entry<Integer, Integer> e : allColors.entrySet()) {
            intToCam.put(e.getKey(), Cam.fromInt(e.getKey()));
        }

        // Get an array with 360 slots. A slot contains the percentage of colors with that hue.
        double[] hueProportions = huePopulations(intToCam, intToProportion, filter);
        // Map each color to the percentage of the image with its hue.
        Map<Integer, Double> intToHueProportion = new LinkedHashMap<>();
        for (Map.Entry<Integer, Integer> e : allColors.entrySet()) {
            Cam cam = intToCam.get(e.getKey());
            int hue = Math.round(cam.getHue());                 // Float.roundToInt()
            double proportion = 0.0;
            for (int i = hue - 15; i <= hue + 15; i++) {        // Kotlin `..` is inclusive
                proportion += hueProportions[wrapDegrees(i)];
            }
            intToHueProportion.put(e.getKey(), proportion);
        }
        // Remove any inappropriate seed colors. For example, low chroma colors look grayscale
        // raising their chroma will turn them to a much louder color that may not have been
        // in the image.
        Map<Integer, Cam> filteredIntToCam;
        if (!filter) {
            filteredIntToCam = intToCam;
        } else {
            filteredIntToCam = new LinkedHashMap<>();
            for (Map.Entry<Integer, Cam> e : intToCam.entrySet()) {
                Cam cam = e.getValue();
                double proportion = intToHueProportion.get(e.getKey());
                if (cam.getChroma() >= MIN_CHROMA
                        && (totalPopulationMeaningless || proportion > 0.01)) {
                    filteredIntToCam.put(e.getKey(), cam);
                }
            }
        }
        // Sort the colors by score, from high to low.
        List<Map.Entry<Integer, Double>> intToScore = new ArrayList<>();
        for (Map.Entry<Integer, Cam> e : filteredIntToCam.entrySet()) {
            intToScore.add(new java.util.AbstractMap.SimpleEntry<>(e.getKey(),
                    score(e.getValue(), intToHueProportion.get(e.getKey()))));
        }
        // sortByDescending: a stable sort on the natural order of the selector, reversed.
        intToScore.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        // Go through the colors, from high score to low score.
        // If the color is distinct in hue from colors picked so far, pick the color.
        // Iteratively decrease the amount of hue distinctness required, thus ensuring we
        // maximize difference between colors.
        final int minimumHueDistance = 15;
        List<Integer> seeds = new ArrayList<>();
        maximizeHueDistance:
        for (int i = 90; i >= minimumHueDistance; i--) {
            seeds.clear();
            for (Map.Entry<Integer, Double> entry : intToScore) {
                int argb = entry.getKey();
                boolean existingSeedNearby = false;
                for (int s : seeds) {
                    float hueA = intToCam.get(argb).getHue();
                    float hueB = intToCam.get(s).getHue();
                    if (hueDiff(hueA, hueB) < i) {
                        existingSeedNearby = true;
                        break;
                    }
                }
                if (existingSeedNearby) {
                    continue;
                }
                seeds.add(argb);
                if (seeds.size() >= 4) {
                    break maximizeHueDistance;
                }
            }
        }

        if (seeds.isEmpty()) {
            // Use gBlue 500 if there are 0 colors
            seeds.add(GOOGLE_BLUE);
        }

        return seeds;
    }

    /** ColorScheme.huePopulations. */
    private static double[] huePopulations(Map<Integer, Cam> camByColor,
            Map<Integer, Double> populationByColor, boolean filter) {
        double[] huePopulation = new double[360];
        for (Map.Entry<Integer, Double> entry : populationByColor.entrySet()) {
            double population = populationByColor.get(entry.getKey());
            Cam cam = camByColor.get(entry.getKey());
            int hue = Math.round(cam.getHue()) % 360;
            if (filter && cam.getChroma() <= MIN_CHROMA) {
                continue;
            }
            huePopulation[hue] = huePopulation[hue] + population;
        }
        return huePopulation;
    }
}
