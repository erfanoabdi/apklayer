// PORTED VERBATIM (Apache-2.0) from AOSP android14-release
//   frameworks/base/packages/SystemUI/monet/src/com/android/systemui/monet/dislike/DislikeAnalyzer.java
// Only the package, the imports and the android.annotation markers were changed,
// plus any edit flagged "MACAPK:" below. See docs/monet-wallpaper-seed_2026-09-26.md.
/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.macapk.loader.monet.libmonet.dislike;

import com.macapk.loader.monet.libmonet.hct.Hct;

/**
 * Check and/or fix universally disliked colors.
 *
 * <p>Color science studies of color preference indicate universal distaste for dark yellow-greens,
 * and also show this is correlated to distate for biological waste and rotting food.
 *
 * <p>See Palmer and Schloss, 2010 or Schloss and Palmer's Chapter 21 in Handbook of Color
 * Psychology (2015).
 */
public final class DislikeAnalyzer {

    private DislikeAnalyzer() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns true if color is disliked.
     *
     * <p>Disliked is defined as a dark yellow-green that is not neutral.
     */
    public static boolean isDisliked(Hct hct) {
        final boolean huePasses = Math.round(hct.getHue()) >= 90.0 && Math.round(hct.getHue())
                <= 111.0;
        final boolean chromaPasses = Math.round(hct.getChroma()) > 16.0;
        final boolean tonePasses = Math.round(hct.getTone()) < 70.0;

        return huePasses && chromaPasses && tonePasses;
    }

    /** If color is disliked, lighten it to make it likable. */
    public static Hct fixIfDisliked(Hct hct) {
        if (isDisliked(hct)) {
            return Hct.from(hct.getHue(), hct.getChroma(), 70.0);
        }

        return hct;
    }
}
