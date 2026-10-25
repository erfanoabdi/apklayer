// android.hardware.camera2.params.ColorSpaceProfiles -- which (color space,
// image format, dynamic range profile) triples a camera supports. Value type of
// CameraCharacteristics.REQUEST_AVAILABLE_COLOR_SPACE_PROFILES_MAP. API 34.
//
// Mirrors frameworks/base/core/java/android/hardware/camera2/params/
// ColorSpaceProfiles.java (aosp-mirror, android16-release). AOSP's containers
// are ArrayMap/ArraySet; LinkedHashMap/LinkedHashSet are used here because they
// give the same Map/Set contract with the same insertion iteration order, which
// is all any caller can observe through the public API.
package android.hardware.camera2.params;

import android.graphics.ColorSpace;
import android.graphics.ImageFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class ColorSpaceProfiles {

    /** AOSP :45 -- CameraMetadata.REQUEST_AVAILABLE_COLOR_SPACE_PROFILES_MAP_UNSPECIFIED. */
    public static final int UNSPECIFIED = -1;

    private final Map<ColorSpace.Named, Map<Integer, Set<Long>>> mProfileMap =
            new LinkedHashMap<>();

    /** AOSP :54 -- flat (colorSpaceOrdinal, imageFormat, dynamicRangeBitmap)
     *  triples straight out of the HAL metadata tag. */
    public ColorSpaceProfiles(long[] elements) {
        if ((elements.length % 3) != 0) {
            throw new IllegalArgumentException("Color space profile map length "
                    + elements.length + " is not divisible by 3!");
        }
        for (int i = 0; i < elements.length; i += 3) {
            int colorSpace = (int) elements[i];
            checkProfileValue(colorSpace);
            ColorSpace.Named namedColorSpace = ColorSpace.Named.values()[colorSpace];
            int imageFormat = (int) elements[i + 1];
            long dynamicRangeProfileBitmap = elements[i + 2];

            mProfileMap.computeIfAbsent(namedColorSpace, k -> new LinkedHashMap<>());
            mProfileMap.get(namedColorSpace)
                    .computeIfAbsent(imageFormat, k -> new LinkedHashSet<>());

            if (dynamicRangeProfileBitmap != 0) {
                for (long dynamicRangeProfile = DynamicRangeProfiles.STANDARD;
                        dynamicRangeProfile < DynamicRangeProfiles.PUBLIC_MAX;
                        dynamicRangeProfile <<= 1) {
                    if ((dynamicRangeProfileBitmap & dynamicRangeProfile) != 0) {
                        mProfileMap.get(namedColorSpace).get(imageFormat).add(dynamicRangeProfile);
                    }
                }
            }
        }
    }

    /** AOSP :94. @hide there; kept because the constructor above calls it and
     *  its throw is part of the constructor's observable contract. */
    public static void checkProfileValue(int colorSpace) {
        boolean found = false;
        for (ColorSpace.Named value : ColorSpace.Named.values()) {
            if (colorSpace == value.ordinal()) { found = true; break; }
        }
        if (!found) {
            throw new IllegalArgumentException("Unknown ColorSpace " + colorSpace);
        }
    }

    /** AOSP :123 -- ImageFormat.UNKNOWN means "any format". */
    public Set<ColorSpace.Named> getSupportedColorSpaces(int imageFormat) {
        Set<ColorSpace.Named> supported = new LinkedHashSet<>();
        for (ColorSpace.Named colorSpace : mProfileMap.keySet()) {
            if (imageFormat == ImageFormat.UNKNOWN) {
                supported.add(colorSpace);
            } else if (mProfileMap.get(colorSpace).containsKey(imageFormat)) {
                supported.add(colorSpace);
            }
        }
        return supported;
    }

    /** AOSP :148 */
    public Set<Integer> getSupportedImageFormatsForColorSpace(ColorSpace.Named colorSpace) {
        Map<Integer, Set<Long>> imageFormatMap = mProfileMap.get(colorSpace);
        if (imageFormatMap == null) return new LinkedHashSet<Integer>();
        return imageFormatMap.keySet();
    }

    /** AOSP :171 */
    public Set<Long> getSupportedDynamicRangeProfiles(ColorSpace.Named colorSpace,
            int imageFormat) {
        Map<Integer, Set<Long>> imageFormatMap = mProfileMap.get(colorSpace);
        if (imageFormatMap == null) return new LinkedHashSet<Long>();
        Set<Long> dynamicRangeProfiles;
        if (imageFormat == ImageFormat.UNKNOWN) {
            dynamicRangeProfiles = new LinkedHashSet<>();
            for (int supportedImageFormat : imageFormatMap.keySet()) {
                dynamicRangeProfiles.addAll(imageFormatMap.get(supportedImageFormat));
            }
        } else {
            dynamicRangeProfiles = imageFormatMap.get(imageFormat);
            if (dynamicRangeProfiles == null) return new LinkedHashSet<>();
        }
        return dynamicRangeProfiles;
    }

    /** AOSP :212 */
    public Set<ColorSpace.Named> getSupportedColorSpacesForDynamicRange(int imageFormat,
            long dynamicRangeProfile) {
        Set<ColorSpace.Named> supported = new LinkedHashSet<>();
        for (ColorSpace.Named colorSpace : mProfileMap.keySet()) {
            Map<Integer, Set<Long>> imageFormatMap = mProfileMap.get(colorSpace);
            if (imageFormat == ImageFormat.UNKNOWN) {
                for (int supportedImageFormat : imageFormatMap.keySet()) {
                    if (imageFormatMap.get(supportedImageFormat).contains(dynamicRangeProfile)) {
                        supported.add(colorSpace);
                    }
                }
            } else if (imageFormatMap.containsKey(imageFormat)) {
                if (imageFormatMap.get(imageFormat).contains(dynamicRangeProfile)) {
                    supported.add(colorSpace);
                }
            }
        }
        return supported;
    }
}
