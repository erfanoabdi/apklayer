// PORTED (Apache-2.0) from AOSP android14-release
//   frameworks/base/core/java/com/android/internal/graphics/palette/Palette.java
// TRIMMED to the part the quantizers use: Swatch, from(List<Swatch>), getSwatches,
// getDominantSwatch. The Bitmap Builder is replaced by the pixel-array entry point in
// com.macapk.loader.monet.WallpaperColorsData, which reproduces Builder.generate()'s
// only relevant behaviour for WallpaperColors (a bitmap already at or under
// resizeBitmapArea is quantized as-is, no region). See
// docs/monet-wallpaper-seed_2026-09-26.md.
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
package com.macapk.loader.monet.palette;

import android.graphics.Color;

import java.util.Collections;
import java.util.List;

/** A color palette: the list of swatches a Quantizer produced. */
public final class Palette {

    /** Generate a Palette from the pre-generated list of swatches (Builder(swatches).generate()). */
    public static Palette from(List<Swatch> swatches) {
        return new Palette(swatches);
    }

    private final List<Swatch> mSwatches;
    private final Swatch mDominantSwatch;

    Palette(List<Swatch> swatches) {
        mSwatches = swatches;
        mDominantSwatch = findDominantSwatch();
    }

    /** Returns all of the swatches which make up the palette. */
    public List<Swatch> getSwatches() {
        return Collections.unmodifiableList(mSwatches);
    }

    /** Returns the swatch with the highest population, or null if there are no swatches. */
    public Swatch getDominantSwatch() {
        return mDominantSwatch;
    }

    private Swatch findDominantSwatch() {
        int maxPop = Integer.MIN_VALUE;
        Swatch maxSwatch = null;
        for (int i = 0, count = mSwatches.size(); i < count; i++) {
            Swatch swatch = mSwatches.get(i);
            if (swatch.getPopulation() > maxPop) {
                maxSwatch = swatch;
                maxPop = swatch.getPopulation();
            }
        }
        return maxSwatch;
    }

    /** A color swatch: an RGB color and the number of pixels it represents. */
    public static class Swatch {
        private final Color mColor;
        private final int mPopulation;

        public Swatch(int colorInt, int population) {
            mColor = Color.valueOf(colorInt);
            mPopulation = population;
        }

        /** @return this swatch's RGB color value */
        public int getInt() {
            return mColor.toArgb();
        }

        /** @return the number of pixels represented by this swatch */
        public int getPopulation() {
            return mPopulation;
        }

        @Override
        public String toString() {
            return new StringBuilder(getClass().getSimpleName())
                    .append(" [")
                    .append(mColor)
                    .append(']')
                    .append(" [Population: ")
                    .append(mPopulation)
                    .append(']')
                    .toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Swatch swatch = (Swatch) o;
            return mPopulation == swatch.mPopulation && mColor.toArgb() == swatch.mColor.toArgb();
        }

        @Override
        public int hashCode() {
            return 31 * mColor.toArgb() + mPopulation;
        }
    }
}
