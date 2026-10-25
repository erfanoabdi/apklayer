// PORTED VERBATIM (Apache-2.0) from AOSP android14-release
//   frameworks/base/packages/SystemUI/monet/src/com/android/systemui/monet/dynamiccolor/ToneDeltaConstraint.java
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

package com.macapk.loader.monet.libmonet.dynamiccolor;

/**
 * Documents a constraint between two DynamicColors, in which their tones must have a certain
 * distance from each other.
 */
public final class ToneDeltaConstraint {
    public final double delta;
    public final DynamicColor keepAway;
    public final TonePolarity keepAwayPolarity;

    /**
     * @param delta            the difference in tone required
     * @param keepAway         the color to distance in tone from
     * @param keepAwayPolarity whether the color to keep away from must be lighter, darker, or no
     *                         preference, in which case it should
     */
    public ToneDeltaConstraint(double delta, DynamicColor keepAway, TonePolarity keepAwayPolarity) {
        this.delta = delta;
        this.keepAway = keepAway;
        this.keepAwayPolarity = keepAwayPolarity;
    }
}
