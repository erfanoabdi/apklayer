// android.hardware.camera2.params.DynamicRangeProfiles (API 33).
//
// The set of 10-bit dynamic range profiles (HLG10, HDR10, Dolby Vision ...) a
// camera supports, plus which of them may be combined in one capture request.
// Apps reference the CONSTANTS (DynamicRangeProfiles.HLG10 and friends) when
// calling OutputConfiguration.setDynamicRangeProfile, which is why the class has
// to resolve even on a device that will never report a 10-bit-capable camera.
//
// This is a REAL implementation, not a device-absent answer: AOSP's constructor
// is a pure transformation of the long[] metadata entry into two maps, with no
// device access anywhere, so it is reproduced exactly. What is device-dependent
// is who CONSTRUCTS one -- on a real device CameraCharacteristics does, from the
// REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES metadata tag. We report no cameras,
// so nothing constructs one today; when a capture backend lands it can hand the
// raw long[] straight to this constructor and the behaviour is already correct.
//
// Mirrored from
// frameworks/base/core/java/android/hardware/camera2/params/DynamicRangeProfiles.java
// (aosp-mirror/platform_frameworks_base, main): the profile constants L59-149
// (each aliasing a CameraMetadata.REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES_MAP_*
// value, which is why those were added to our CameraMetadata rather than
// inlined here), the constructor L198-236, checkProfileValue L242-254,
// getSupportedProfiles L259-261, getProfileCaptureRequestConstraints L288-295,
// isExtraLatencyPresent L314-321.
package android.hardware.camera2.params;

import android.hardware.camera2.CameraMetadata;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public final class DynamicRangeProfiles {
    /** The default 8-bit standard profile, used when a client does not
     *  explicitly configure one. */
    public static final long STANDARD =
            CameraMetadata.REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES_MAP_STANDARD;

    /** 10-bit samples, Hybrid log-gamma transfer function. All 10-bit capable
     *  devices are required to support this profile. */
    public static final long HLG10 =
            CameraMetadata.REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES_MAP_HLG10;

    /** 10-bit samples, SMPTE ST 2084 transfer function, static metadata. */
    public static final long HDR10 =
            CameraMetadata.REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES_MAP_HDR10;

    /** 10-bit samples, SMPTE ST 2084 transfer function, per-frame metadata. */
    public static final long HDR10_PLUS =
            CameraMetadata.REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES_MAP_HDR10_PLUS;

    public static final long DOLBY_VISION_10B_HDR_REF =
            CameraMetadata.REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES_MAP_DOLBY_VISION_10B_HDR_REF;
    public static final long DOLBY_VISION_10B_HDR_REF_PO =
            CameraMetadata.REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES_MAP_DOLBY_VISION_10B_HDR_REF_PO;
    public static final long DOLBY_VISION_10B_HDR_OEM =
            CameraMetadata.REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES_MAP_DOLBY_VISION_10B_HDR_OEM;
    public static final long DOLBY_VISION_10B_HDR_OEM_PO =
            CameraMetadata.REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES_MAP_DOLBY_VISION_10B_HDR_OEM_PO;
    public static final long DOLBY_VISION_8B_HDR_REF =
            CameraMetadata.REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES_MAP_DOLBY_VISION_8B_HDR_REF;
    public static final long DOLBY_VISION_8B_HDR_REF_PO =
            CameraMetadata.REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES_MAP_DOLBY_VISION_8B_HDR_REF_PO;
    public static final long DOLBY_VISION_8B_HDR_OEM =
            CameraMetadata.REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES_MAP_DOLBY_VISION_8B_HDR_OEM;
    public static final long DOLBY_VISION_8B_HDR_OEM_PO =
            CameraMetadata.REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES_MAP_DOLBY_VISION_8B_HDR_OEM_PO;

    /** AOSP marks this @hide but declares it public; kept public for the same
     *  reason the hidden InputConfiguration constructor is. */
    public static final long PUBLIC_MAX =
            CameraMetadata.REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES_MAP_MAX;

    private final HashMap<Long, Set<Long>> mProfileMap = new HashMap<>();
    private final HashMap<Long, Boolean> mLookahedLatencyMap = new HashMap<>();

    /** Three longs per entry: the profile, a bitmap of the profiles usable
     *  alongside it in one request (0 meaning unconstrained), and a non-zero
     *  extra-latency hint. AOSP documents this constructor as available for
     *  testing; camera clients normally read the value out of
     *  CameraCharacteristics instead. */
    public DynamicRangeProfiles(final long[] elements) {
        if ((elements.length % 3) != 0) {
            throw new IllegalArgumentException("Dynamic range profile map length "
                    + elements.length + " is not even!");
        }

        for (int i = 0; i < elements.length; i += 3) {
            checkProfileValue(elements[i]);
            // STANDARD is not expected to be included
            if (elements[i] == STANDARD) {
                throw new IllegalArgumentException("Dynamic range profile map must not include a"
                        + " STANDARD profile entry!");
            }
            HashSet<Long> profiles = new HashSet<>();

            if (elements[i + 1] != 0) {
                for (long profile = STANDARD; profile < PUBLIC_MAX; profile <<= 1) {
                    if ((elements[i + 1] & profile) != 0) {
                        profiles.add(profile);
                    }
                }
            }

            mProfileMap.put(elements[i], profiles);
            mLookahedLatencyMap.put(elements[i], elements[i + 2] != 0L);
        }

        // Build the STANDARD constraints depending on the advertised 10-bit
        // limitations.
        HashSet<Long> standardConstraints = new HashSet<>();
        standardConstraints.add(STANDARD);
        for (Long profile : mProfileMap.keySet()) {
            if (mProfileMap.get(profile).isEmpty() || mProfileMap.get(profile).contains(STANDARD)) {
                standardConstraints.add(profile);
            }
        }

        mProfileMap.put(STANDARD, standardConstraints);
        mLookahedLatencyMap.put(STANDARD, false);
    }

    public static void checkProfileValue(long profile) {
        if (profile == STANDARD || profile == HLG10 || profile == HDR10 || profile == HDR10_PLUS
                || profile == DOLBY_VISION_10B_HDR_REF || profile == DOLBY_VISION_10B_HDR_REF_PO
                || profile == DOLBY_VISION_10B_HDR_OEM || profile == DOLBY_VISION_10B_HDR_OEM_PO
                || profile == DOLBY_VISION_8B_HDR_REF || profile == DOLBY_VISION_8B_HDR_REF_PO
                || profile == DOLBY_VISION_8B_HDR_OEM
                || profile == DOLBY_VISION_8B_HDR_OEM_PO) { // No-op
        } else {
            throw new IllegalArgumentException("Unknown profile " + profile);
        }
    }

    public Set<Long> getSupportedProfiles() {
        return Collections.unmodifiableSet(mProfileMap.keySet());
    }

    public Set<Long> getProfileCaptureRequestConstraints(long profile) {
        Set<Long> ret = mProfileMap.get(profile);
        if (ret == null) {
            throw new IllegalArgumentException("Unsupported profile!");
        }
        return Collections.unmodifiableSet(ret);
    }

    public boolean isExtraLatencyPresent(long profile) {
        Boolean ret = mLookahedLatencyMap.get(profile);
        if (ret == null) {
            throw new IllegalArgumentException("Unsupported profile!");
        }
        return ret;
    }
}
