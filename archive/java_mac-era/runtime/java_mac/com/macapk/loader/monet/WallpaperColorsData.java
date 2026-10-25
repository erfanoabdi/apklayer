// PORTED (Apache-2.0) from AOSP android14-release
//   frameworks/base/core/java/android/app/WallpaperColors.java
// The colour-extraction half of WallpaperColors: fromBitmap(bitmap, dimAmount) taken on
// a packed ARGB pixel array (the host decodes the wallpaper, see HostWallpaper), the
// @hide WallpaperColors(Map<Integer,Integer>, int) constructor that ranks the main
// colours, and calculateDarkHints. Kept separate from android.app.WallpaperColors
// (a public class another lane owns) so the maths can be driven by an offline gate on a
// bare JVM. See docs/monet-wallpaper-seed_2026-09-26.md.
/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.graphics.Color;

import com.macapk.loader.monet.cam.Cam;
import com.macapk.loader.monet.graphics.ColorUtils;
import com.macapk.loader.monet.palette.CelebiQuantizer;
import com.macapk.loader.monet.palette.Palette;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class WallpaperColorsData {
    /** WallpaperColors.HINT_SUPPORTS_DARK_TEXT / _DARK_THEME / (@hide) HINT_FROM_BITMAP. */
    public static final int HINT_SUPPORTS_DARK_TEXT = 1 << 0;
    public static final int HINT_SUPPORTS_DARK_THEME = 1 << 1;
    public static final int HINT_FROM_BITMAP = 1 << 2;

    // Maximum size that a bitmap can have to keep our calculations valid
    static final int MAX_BITMAP_SIZE = 112;
    // Even though we have a maximum size, we'll mainly match bitmap sizes
    // using the area instead. This way our comparisons are aspect ratio independent.
    static final int MAX_WALLPAPER_EXTRACTION_AREA = MAX_BITMAP_SIZE * MAX_BITMAP_SIZE;
    // Decides when dark theme is optimal for this wallpaper
    private static final float DARK_THEME_MEAN_LUMINANCE = 0.3f;
    // Minimum mean luminosity that an image needs to have to support dark text.
    // AOSP: SystemProperties.getInt("persist.wallpapercolors.threshold", 70) / 100f
    private static final float BRIGHT_IMAGE_MEAN_LUMINANCE = 70 / 100f;
    // We also check if the image has dark pixels in it,
    // to avoid bright images with some dark spots.
    private static final float DARK_PIXEL_CONTRAST = 5.5f;
    // AOSP: SystemProperties.getInt("persist.wallpapercolors.max_dark_area", 5) / 100f
    private static final float MAX_DARK_AREA = 5 / 100f;

    private final List<Integer> mMainColors;
    private final Map<Integer, Integer> mAllColors;
    private final int mColorHints;

    /** WallpaperColors.calculateOptimalSize(width, height): {w, h}. */
    public static int[] calculateOptimalSize(int width, int height) {
        // Calculate how big the bitmap needs to be.
        // This avoids unnecessary processing and allocation inside Palette.
        final int requestedArea = width * height;
        double scale = 1;
        if (requestedArea > MAX_WALLPAPER_EXTRACTION_AREA) {
            scale = Math.sqrt(MAX_WALLPAPER_EXTRACTION_AREA / (double) requestedArea);
        }
        int newWidth = (int) (width * scale);
        int newHeight = (int) (height * scale);
        // Dealing with edge cases of the drawable being too wide or too tall.
        // Width or height would end up being 0, in this case we'll set it to 1.
        if (newWidth == 0) {
            newWidth = 1;
        }
        if (newHeight == 0) {
            newHeight = 1;
        }
        return new int[] {newWidth, newHeight};
    }

    /**
     * WallpaperColors.fromBitmap(bitmap, dimAmount), split at the one place the host does the
     * work instead of Skia: the caller passes the pixels of the bitmap AFTER
     * Bitmap.createScaledBitmap(bitmap, optimalSize, filter=false), and the ORIGINAL
     * bitmap's area, which AOSP uses to size the cluster count.
     *
     * @param pixels          the (already downscaled) bitmap, packed non-premultiplied
     *                        sRGB ARGB, row-major -- what Bitmap.getPixels returns
     * @param originalArea    width * height of the bitmap BEFORE downscaling
     */
    public static WallpaperColorsData fromPixels(int[] pixels, int originalArea, float dimAmount) {
        final int bitmapArea = originalArea;
        // Palette.Builder.resizeBitmapArea(MAX_WALLPAPER_EXTRACTION_AREA): the scaled bitmap
        // is already at or under the area (calculateOptimalSize), so scaleBitmapDown()
        // returns it unchanged and no region is set -- getPixelsFromBitmap is all pixels.
        //
        // ActivityManager.isLowRamDeviceStatic() is false on Mac.apk (a Mac is not an
        // Android Go device), so the CelebiQuantizer branch is the one AOSP takes.
        // in any case, always use between 5 and 128 clusters
        int minClusters = 5;
        int maxClusters = 128;
        // if the bitmap is very small, use bitmapArea/16 clusters instead of 128
        int minPixelsPerCluster = 16;
        int numberOfColors = Math.max(minClusters,
                Math.min(maxClusters, bitmapArea / minPixelsPerCluster));
        CelebiQuantizer quantizer = new CelebiQuantizer();
        quantizer.quantize(pixels, numberOfColors);
        final Palette palette = Palette.from(quantizer.getQuantizedColors());

        // Remove insignificant colors and sort swatches by population
        final ArrayList<Palette.Swatch> swatches = new ArrayList<>(palette.getSwatches());
        swatches.sort((a, b) -> b.getPopulation() - a.getPopulation());

        final int swatchesSize = swatches.size();

        final Map<Integer, Integer> populationByColor = new HashMap<>();
        for (int i = 0; i < swatchesSize; i++) {
            Palette.Swatch swatch = swatches.get(i);
            int colorInt = swatch.getInt();
            populationByColor.put(colorInt, swatch.getPopulation());
        }

        int hints = calculateDarkHints(pixels, dimAmount);

        return new WallpaperColorsData(populationByColor, HINT_FROM_BITMAP | hints);
    }

    /** The @hide WallpaperColors(Map<Integer, Integer> colorToPopulation, int colorHints). */
    public WallpaperColorsData(Map<Integer, Integer> colorToPopulation, int colorHints) {
        mAllColors = colorToPopulation;

        final Map<Integer, Cam> colorToCam = new HashMap<>();
        for (int color : colorToPopulation.keySet()) {
            colorToCam.put(color, Cam.fromInt(color));
        }
        final double[] hueProportions = hueProportions(colorToCam, colorToPopulation);
        final Map<Integer, Double> colorToHueProportion = colorToHueProportion(
                colorToPopulation.keySet(), colorToCam, hueProportions);

        final Map<Integer, Double> colorToScore = new HashMap<>();
        for (Map.Entry<Integer, Double> mapEntry : colorToHueProportion.entrySet()) {
            int color = mapEntry.getKey();
            double proportion = mapEntry.getValue();
            double score = score(colorToCam.get(color), proportion);
            colorToScore.put(color, score);
        }
        ArrayList<Map.Entry<Integer, Double>> mapEntries = new ArrayList<>(colorToScore.entrySet());
        mapEntries.sort((a, b) -> b.getValue().compareTo(a.getValue()));

        List<Integer> colorsByScoreDescending = new ArrayList<>();
        for (Map.Entry<Integer, Double> colorToScoreEntry : mapEntries) {
            colorsByScoreDescending.add(colorToScoreEntry.getKey());
        }

        List<Integer> mainColorInts = new ArrayList<>();
        findSeedColorLoop:
        for (int color : colorsByScoreDescending) {
            Cam cam = colorToCam.get(color);
            for (int otherColor : mainColorInts) {
                Cam otherCam = colorToCam.get(otherColor);
                if (hueDiff(cam, otherCam) < 15) {
                    continue findSeedColorLoop;
                }
            }
            mainColorInts.add(color);
        }
        mMainColors = mainColorInts;
        mColorHints = colorHints;
    }

    /** WallpaperColors(primary, secondary, tertiary, hints): the population-0 form. */
    public static WallpaperColorsData ofMainColors(int[] mainColors, int colorHints) {
        return new WallpaperColorsData(mainColors, colorHints);
    }

    private WallpaperColorsData(int[] mainColors, int colorHints) {
        mMainColors = new ArrayList<>(3);
        mAllColors = new HashMap<>();
        for (int c : mainColors) {
            mMainColors.add(c);
            mAllColors.put(c, 0);
        }
        mColorHints = colorHints;
    }

    /** WallpaperColors.getMainColors(), as packed ints (Color.toArgb of each). */
    public List<Integer> getMainColorInts() {
        return Collections.unmodifiableList(mMainColors);
    }

    /** WallpaperColors.getAllColors(): colour -> population, in the HashMap's own order. */
    public Map<Integer, Integer> getAllColors() {
        return Collections.unmodifiableMap(mAllColors);
    }

    /** WallpaperColors.getColorHints(). */
    public int getColorHints() {
        return mColorHints;
    }

    private static double hueDiff(Cam a, Cam b) {
        return (180f - Math.abs(Math.abs(a.getHue() - b.getHue()) - 180f));
    }

    private static double score(Cam cam, double proportion) {
        return cam.getChroma() + (proportion * 100);
    }

    private static Map<Integer, Double> colorToHueProportion(Set<Integer> colors,
            Map<Integer, Cam> colorToCam, double[] hueProportions) {
        Map<Integer, Double> colorToHueProportion = new HashMap<>();
        for (int color : colors) {
            final int hue = wrapDegrees(Math.round(colorToCam.get(color).getHue()));
            double proportion = 0.0;
            for (int i = hue - 15; i < hue + 15; i++) {
                proportion += hueProportions[wrapDegrees(i)];
            }
            colorToHueProportion.put(color, proportion);
        }
        return colorToHueProportion;
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

    private static double[] hueProportions(Map<Integer, Cam> colorToCam,
            Map<Integer, Integer> colorToPopulation) {
        final double[] proportions = new double[360];

        double totalPopulation = 0;
        for (Map.Entry<Integer, Integer> entry : colorToPopulation.entrySet()) {
            totalPopulation += entry.getValue();
        }

        for (Map.Entry<Integer, Integer> entry : colorToPopulation.entrySet()) {
            final int color = (int) entry.getKey();
            final int population = colorToPopulation.get(color);
            final Cam cam = colorToCam.get(color);
            final int hue = wrapDegrees(Math.round(cam.getHue()));
            proportions[hue] = proportions[hue] + ((double) population / totalPopulation);
        }

        return proportions;
    }

    /** WallpaperColors.calculateDarkHints(source, dimAmount) over the scaled bitmap's pixels. */
    static int calculateDarkHints(int[] pixels, float dimAmount) {
        if (pixels == null) {
            return 0;
        }

        // MathUtils.saturate(dimAmount)
        dimAmount = dimAmount < 0f ? 0f : (dimAmount > 1f ? 1f : dimAmount);
        double totalLuminance = 0;
        final int maxDarkPixels = (int) (pixels.length * MAX_DARK_AREA);
        int darkPixels = 0;

        // Create a new black layer with dimAmount as the alpha to be accounted for when computing
        // the luminance.
        int dimmingLayerAlpha = (int) (255 * dimAmount);
        int blackTransparent = ColorUtils.setAlphaComponent(Color.BLACK, dimmingLayerAlpha);

        // This bitmap was already resized to fit the maximum allowed area.
        // Let's just loop through the pixels, no sweat!
        float[] tmpHsl = new float[3];
        for (int i = 0; i < pixels.length; i++) {
            int pixelColor = pixels[i];
            ColorUtils.colorToHSL(pixelColor, tmpHsl);
            final int alpha = Color.alpha(pixelColor);

            // Apply composite colors where the foreground is a black layer with an alpha value of
            // the dim amount and the background is the wallpaper pixel color.
            int compositeColors = ColorUtils.compositeColors(blackTransparent, pixelColor);

            // Calculate the adjusted luminance of the dimmed wallpaper pixel color.
            double adjustedLuminance = ColorUtils.calculateLuminance(compositeColors);

            // Make sure we don't have a dark pixel mass that will
            // make text illegible.
            final boolean satisfiesTextContrast = ColorUtils
                    .calculateContrast(pixelColor, Color.BLACK) > DARK_PIXEL_CONTRAST;
            if (!satisfiesTextContrast && alpha != 0) {
                darkPixels++;
            }
            totalLuminance += adjustedLuminance;
        }

        int hints = 0;
        double meanLuminance = totalLuminance / pixels.length;
        if (meanLuminance > BRIGHT_IMAGE_MEAN_LUMINANCE && darkPixels <= maxDarkPixels) {
            hints |= HINT_SUPPORTS_DARK_TEXT;
        }
        if (meanLuminance < DARK_THEME_MEAN_LUMINANCE) {
            hints |= HINT_SUPPORTS_DARK_THEME;
        }
        return hints;
    }

    @Override
    public String toString() {
        final StringBuilder colors = new StringBuilder();
        for (int i = 0; i < mMainColors.size(); i++) {
            colors.append(Integer.toHexString(mMainColors.get(i))).append(" ");
        }
        return "[WallpaperColors: " + colors + "h: " + mColorHints + "]";
    }
}
