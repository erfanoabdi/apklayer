// PORTED VERBATIM (Apache-2.0) from AOSP android14-release
//   frameworks/base/core/java/com/android/internal/graphics/palette/QuantizerMap.java
// Only the package, the imports and the android.annotation markers were changed,
// plus any edit flagged "MACAPK:" below. See docs/monet-wallpaper-seed_2026-09-26.md.
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

package com.macapk.loader.monet.palette;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts a set of pixels/colors into a map with keys of unique colors, and values of the count
 * of the unique color in the original set of pixels.
 *
 * This allows other quantizers to get a significant speed boost by simply running this quantizer,
 * and then performing operations using the map, rather than for each pixel.
 */
public final class QuantizerMap implements Quantizer {
    private HashMap<Integer, Integer> mColorToCount;
    private Palette mPalette;

    @Override
    public void quantize(int[] pixels, int colorCount) {
        final HashMap<Integer, Integer> colorToCount = new HashMap<>();
        for (int pixel : pixels) {
            colorToCount.merge(pixel, 1, Integer::sum);
        }
        mColorToCount = colorToCount;

        List<Palette.Swatch> swatches = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : colorToCount.entrySet()) {
            swatches.add(new Palette.Swatch(entry.getKey(), entry.getValue()));
        }
        mPalette = Palette.from(swatches);
    }

    @Override
    public List<Palette.Swatch> getQuantizedColors() {
        return mPalette.getSwatches();
    }

    public Map<Integer, Integer> getColorToCount() {
        return mColorToCount;
    }
}
