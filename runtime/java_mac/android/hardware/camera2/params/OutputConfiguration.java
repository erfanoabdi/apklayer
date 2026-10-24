// android.hardware.camera2.params.OutputConfiguration (API 24; individual
// members from 26, 28, 31, 33 and 35 -- each noted at its declaration).
//
// A capture-session output target: the Surface (or the promise of one), plus
// every knob the app can turn before the session is configured -- surface
// sharing, physical camera id, dynamic range profile, mirror mode, timestamp
// base, stream use case, colour space.
//
// This is a REAL implementation, not a device-absent answer. Everything here is
// app-side bookkeeping that AOSP performs before any of it reaches the camera
// HAL, so the state machine, the argument validation and the getters are
// reproduced faithfully and an app that configures an OutputConfiguration and
// reads it back sees exactly what a real device would return. What is missing is
// only what a HAL would have supplied: the two places where AOSP consults the
// Surface itself are called out inline (see addSurface).
//
// Mirrored from
// frameworks/base/core/java/android/hardware/camera2/params/OutputConfiguration.java
// (aosp-mirror/platform_frameworks_base, main): constants L131-341,
// constructors L353, L427, L553, L579, L810, L875, enableSurfaceSharing L1030,
// setPhysicalCameraId L1068, addSensorPixelModeUsed L1113, removeSensorPixelModeUsed
// L1140, addSurface L1220-1250 (incl. MAX_SURFACES_COUNT=4 at L394), removeSurface
// L1276, setStreamUseCase L1337, getStreamUseCase L1357, setTimestampBase L1380,
// getTimestampBase L1406, setMirrorMode L1434, getMirrorMode L1454,
// setReadoutTimestampEnabled L1545, isReadoutTimestampEnabled L1553,
// setDynamicRangeProfile L478, getDynamicRangeProfile L487, setColorSpace L504,
// clearColorSpace L514, getColorSpace L526, getMaxSharedSurfaceCount L1669,
// getSurface L1679, getSurfaces L1694, getRotation L1707, getSurfaceGroupId L1717.
package android.hardware.camera2.params;

import android.graphics.ColorSpace;
import android.hardware.camera2.CameraMetadata;
import android.util.Size;
import android.view.Surface;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class OutputConfiguration implements android.os.Parcelable {

    public static final int ROTATION_0 = 0;
    public static final int ROTATION_90 = 1;
    public static final int ROTATION_180 = 2;
    public static final int ROTATION_270 = 3;

    public static final int SURFACE_GROUP_ID_NONE = -1;

    // Timestamp bases, API 33.
    public static final int TIMESTAMP_BASE_DEFAULT = 0;
    public static final int TIMESTAMP_BASE_SENSOR = 1;
    public static final int TIMESTAMP_BASE_MONOTONIC = 2;
    public static final int TIMESTAMP_BASE_REALTIME = 3;
    public static final int TIMESTAMP_BASE_CHOREOGRAPHER_SYNCED = 4;

    // Mirror modes, API 33.
    public static final int MIRROR_MODE_AUTO = 0;
    public static final int MIRROR_MODE_NONE = 1;
    public static final int MIRROR_MODE_H = 2;
    public static final int MIRROR_MODE_V = 3;

    /** AOSP's private cap on shared surfaces per configuration (L394). It is
     *  both the addSurface limit and the getMaxSharedSurfaceCount answer, so it
     *  has to be one constant, not two literals. */
    private static final int MAX_SURFACES_COUNT = 4;

    /** ColorSpaceProfiles.UNSPECIFIED == CameraMetadata
     *  .REQUEST_AVAILABLE_COLOR_SPACE_PROFILES_MAP_UNSPECIFIED == -1 (AOSP
     *  CameraMetadata.java L1407). Kept private rather than adding the whole
     *  ColorSpaceProfiles class, which no app in the corpus references. */
    private static final int COLOR_SPACE_UNSPECIFIED = -1;

    private final List<Surface> mSurfaces = new ArrayList<>();
    private final int mSurfaceGroupId;
    private final int mRotation;
    private final Size mConfiguredSize;
    private String mPhysicalCameraId;
    private boolean mIsShared;
    private long mDynamicRangeProfile = DynamicRangeProfiles.STANDARD;
    private int mColorSpace = COLOR_SPACE_UNSPECIFIED;
    private long mStreamUseCase = CameraMetadata.SCALER_AVAILABLE_STREAM_USE_CASES_DEFAULT;
    private int mTimestampBase = TIMESTAMP_BASE_DEFAULT;
    private int mMirrorMode = MIRROR_MODE_AUTO;
    private boolean mReadoutTimestampEnabled;
    private final List<Integer> mSensorPixelModesUsed = new ArrayList<>();
    /** The gralloc usage flags of the API-36 usage-taking constructors; 0 when
     *  the configuration was built by any other constructor, exactly as AOSP
     *  leaves mUsage at 0 there. */
    private long mUsage;
    /** Set by createInstancesForMultiResolutionOutput, per AOSP's private
     *  setMultiResolutionOutput(). */
    private boolean mIsMultiResolution;
    /** Per-Surface mirror mode, parallel to mSurfaces. AOSP keeps the same
     *  parallel array (mMirrorModeForSurfaces) and appends MIRROR_MODE_AUTO
     *  whenever a Surface is added. */
    private final List<Integer> mMirrorModeForSurfaces = new ArrayList<>();
    /** AOSP's sNextMultiResolutionGroupId -- one shared counter, so two
     *  MultiResolutionImageReaders never collide on a surface group id. */
    private static final java.util.concurrent.atomic.AtomicInteger
            sNextMultiResolutionGroupId = new java.util.concurrent.atomic.AtomicInteger(0);

    public OutputConfiguration(Surface surface) {
        this(SURFACE_GROUP_ID_NONE, surface, ROTATION_0);
    }

    public OutputConfiguration(int surfaceGroupId, Surface surface) {
        this(surfaceGroupId, surface, ROTATION_0);
    }

    /** API 26. AOSP rejects a rotation outside ROTATION_0..ROTATION_270 and a
     *  null Surface. */
    public OutputConfiguration(Surface surface, int rotation) {
        this(SURFACE_GROUP_ID_NONE, surface, rotation);
    }

    /** API 26. */
    public OutputConfiguration(int surfaceGroupId, Surface surface, int rotation) {
        if (surface == null) {
            throw new NullPointerException("Surface must not be null");
        }
        if (rotation < ROTATION_0 || rotation > ROTATION_270) {
            throw new IllegalArgumentException("Illegal rotation value: " + rotation);
        }
        mSurfaces.add(surface);
        mMirrorModeForSurfaces.add(MIRROR_MODE_AUTO);
        mSurfaceGroupId = surfaceGroupId;
        mRotation = rotation;
        // AOSP reads the real size out of the Surface here via
        // SurfaceUtils.getSurfaceSize (a gralloc query). There is no
        // Surface-backed buffer queue to interrogate on this device, so the
        // configured size stays unknown until an app supplies one through the
        // deferred/ImageReader constructors below. addSurface's size comparison
        // is skipped when it is null, and only logs a warning in AOSP anyway.
        mConfiguredSize = null;
    }

    /** API 26 -- the DEFERRED configuration: the app declares the size and the
     *  kind of consumer now and supplies the Surface later via addSurface.
     *  AOSP accepts exactly SurfaceHolder, SurfaceTexture, MediaRecorder and
     *  MediaCodec, and rejects anything else plus a zero dimension. */
    public <T> OutputConfiguration(Size surfaceSize, Class<T> klass) {
        if (surfaceSize == null) {
            throw new NullPointerException("surfaceSize must not be null");
        }
        if (klass == null) {
            throw new NullPointerException("klass must not be null");
        }
        if (klass != android.view.SurfaceHolder.class
                && klass != android.graphics.SurfaceTexture.class
                && klass != android.media.MediaRecorder.class
                && klass != android.media.MediaCodec.class) {
            throw new IllegalArgumentException("Unknown surface source class type");
        }
        if (surfaceSize.getWidth() == 0 || surfaceSize.getHeight() == 0) {
            throw new IllegalArgumentException("Surface size needs to be non-zero");
        }
        mSurfaceGroupId = SURFACE_GROUP_ID_NONE;
        mRotation = ROTATION_0;
        mConfiguredSize = surfaceSize;
    }

    /** API 35 -- an ImageReader output described by format and size, with the
     *  Surface supplied later. Introduced alongside CameraDeviceSetup so a
     *  session's support can be queried without allocating buffers. */
    public OutputConfiguration(int format, Size surfaceSize) {
        this(SURFACE_GROUP_ID_NONE, format, surfaceSize);
    }

    /** API 35. */
    public OutputConfiguration(int surfaceGroupId, int format, Size surfaceSize) {
        if (surfaceSize == null) {
            throw new NullPointerException("surfaceSize must not be null");
        }
        if (surfaceSize.getWidth() == 0 || surfaceSize.getHeight() == 0) {
            throw new IllegalArgumentException("Surface size needs to be non-zero");
        }
        mSurfaceGroupId = surfaceGroupId;
        mRotation = ROTATION_0;
        mConfiguredSize = surfaceSize;
    }

    public void enableSurfaceSharing() {
        mIsShared = true;
    }

    public void setPhysicalCameraId(String physicalCameraId) {
        mPhysicalCameraId = physicalCameraId;
    }

    public String getPhysicalCameraId() {
        return mPhysicalCameraId;
    }

    /** API 31. */
    public void addSensorPixelModeUsed(int sensorPixelModeUsed) {
        if (sensorPixelModeUsed != CameraMetadata.SENSOR_PIXEL_MODE_DEFAULT
                && sensorPixelModeUsed != CameraMetadata.SENSOR_PIXEL_MODE_MAXIMUM_RESOLUTION) {
            throw new IllegalArgumentException("Not a valid sensor pixel mode "
                    + sensorPixelModeUsed);
        }
        if (!mSensorPixelModesUsed.contains(sensorPixelModeUsed)) {
            mSensorPixelModesUsed.add(sensorPixelModeUsed);
        }
    }

    /** API 31. AOSP THROWS when the mode was never added -- it is not a
     *  tolerant remove. */
    public void removeSensorPixelModeUsed(int sensorPixelModeUsed) {
        if (!mSensorPixelModesUsed.remove(Integer.valueOf(sensorPixelModeUsed))) {
            throw new IllegalArgumentException("sensorPixelMode " + sensorPixelModeUsed
                    + "is not part of this output configuration");
        }
    }

    /** API 26. Adds a shared Surface, or completes a deferred configuration.
     *
     *  AOSP performs three more checks after these, all of which query the
     *  Surface's underlying buffer queue through SurfaceUtils (size, format,
     *  dataspace) and reject a mismatch. Those are HAL-adjacent queries with
     *  nothing behind them on this device, so they are deliberately not faked:
     *  inventing a format to compare against would reject Surfaces a real
     *  device accepts, which is worse than accepting them. The ordering-visible
     *  checks -- already-added, non-shared-already-has-one, and the count cap --
     *  are app-side state and ARE enforced, so the exception an app sees for a
     *  programming error is the one AOSP throws. */
    public void addSurface(Surface surface) {
        if (surface == null) {
            throw new NullPointerException("Surface must not be null");
        }
        if (mSurfaces.contains(surface)) {
            throw new IllegalStateException("Surface is already added!");
        }
        if (mSurfaces.size() == 1 && !mIsShared) {
            throw new IllegalStateException(
                    "Cannot have 2 surfaces for a non-sharing configuration");
        }
        if (mSurfaces.size() + 1 > MAX_SURFACES_COUNT) {
            throw new IllegalArgumentException("Exceeds maximum number of surfaces");
        }
        mSurfaces.add(surface);
        mMirrorModeForSurfaces.add(MIRROR_MODE_AUTO);
    }

    /** API 26. */
    public void removeSurface(Surface surface) {
        if (getSurface() == surface) {
            throw new IllegalArgumentException(
                    "Cannot remove surface associated with this output configuration");
        }
        int index = mSurfaces.indexOf(surface);
        if (index < 0) {
            throw new IllegalArgumentException("Surface is not part of this output configuration");
        }
        mSurfaces.remove(index);
        // Kept in lockstep so getMirrorMode(Surface) keeps answering for the
        // Surfaces that remain; AOSP's mMirrorModeForSurfaces is indexed the
        // same way.
        if (index < mMirrorModeForSurfaces.size()) {
            mMirrorModeForSurfaces.remove(index);
        }
    }

    /** API 33. AOSP validates against DynamicRangeProfiles' known values only
     *  when the configuration reaches the session, not here, so this stores. */
    public void setDynamicRangeProfile(long profile) {
        mDynamicRangeProfile = profile;
    }

    /** API 33. Defaults to DynamicRangeProfiles.STANDARD. */
    public long getDynamicRangeProfile() {
        return mDynamicRangeProfile;
    }

    /** API 34. */
    public void setColorSpace(ColorSpace.Named colorSpace) {
        if (colorSpace == null) {
            throw new NullPointerException("colorSpace must not be null");
        }
        mColorSpace = colorSpace.ordinal();
    }

    /** API 34. */
    public void clearColorSpace() {
        mColorSpace = COLOR_SPACE_UNSPECIFIED;
    }

    /** API 34. Null when never set, which is what AOSP returns. */
    public ColorSpace getColorSpace() {
        if (mColorSpace != COLOR_SPACE_UNSPECIFIED) {
            return ColorSpace.get(ColorSpace.Named.values()[mColorSpace]);
        }
        return null;
    }

    /** API 33. The range check is AOSP's verbatim: a value above CROPPED_RAW is
     *  only legal once it reaches the vendor range. */
    public void setStreamUseCase(long streamUseCase) {
        long maxUseCaseValue = CameraMetadata.SCALER_AVAILABLE_STREAM_USE_CASES_CROPPED_RAW;
        if (streamUseCase > maxUseCaseValue
                && streamUseCase < CameraMetadata.SCALER_AVAILABLE_STREAM_USE_CASES_VENDOR_START) {
            throw new IllegalArgumentException("Not a valid stream use case value "
                    + streamUseCase);
        }
        mStreamUseCase = streamUseCase;
    }

    /** API 33. Defaults to SCALER_AVAILABLE_STREAM_USE_CASES_DEFAULT. */
    public long getStreamUseCase() {
        return mStreamUseCase;
    }

    /** API 33. */
    public void setTimestampBase(int timestampBase) {
        if (timestampBase < TIMESTAMP_BASE_DEFAULT
                || timestampBase > TIMESTAMP_BASE_CHOREOGRAPHER_SYNCED) {
            throw new IllegalArgumentException("Not a valid timestamp base value "
                    + timestampBase);
        }
        mTimestampBase = timestampBase;
    }

    /** API 33. */
    public int getTimestampBase() {
        return mTimestampBase;
    }

    /** API 33. */
    public void setMirrorMode(int mirrorMode) {
        if (mirrorMode < MIRROR_MODE_AUTO || mirrorMode > MIRROR_MODE_V) {
            throw new IllegalArgumentException("Not a valid mirror mode " + mirrorMode);
        }
        mMirrorMode = mirrorMode;
    }

    /** API 33. Defaults to MIRROR_MODE_AUTO. */
    public int getMirrorMode() {
        return mMirrorMode;
    }

    /** API 34. */
    public void setReadoutTimestampEnabled(boolean on) {
        mReadoutTimestampEnabled = on;
    }

    /** API 34. */
    public boolean isReadoutTimestampEnabled() {
        return mReadoutTimestampEnabled;
    }

    /** API 28. A constant on real devices too -- AOSP returns the same private
     *  MAX_SURFACES_COUNT regardless of the camera, so this answer is complete
     *  rather than device-absent. */
    public int getMaxSharedSurfaceCount() {
        return MAX_SURFACES_COUNT;
    }

    /** Null for a deferred configuration whose Surface has not been added yet,
     *  matching AOSP. */
    public Surface getSurface() {
        return mSurfaces.isEmpty() ? null : mSurfaces.get(0);
    }

    /** API 26. */
    public List<Surface> getSurfaces() {
        return Collections.unmodifiableList(mSurfaces);
    }

    /** API 24. */
    public int getRotation() {
        return mRotation;
    }

    public int getSurfaceGroupId() {
        return mSurfaceGroupId;
    }

    // ---------------------------------------------------------------------
    // API 33-36 additions, mirrored from frameworks/base/core/java/android/
    // hardware/camera2/params/OutputConfiguration.java (aosp-mirror,
    // android16-release).
    // ---------------------------------------------------------------------

    /** API 36 (FLAG_CAMERA_DEVICE_SETUP). AOSP :930 -- the format+size deferred
     *  constructor plus explicit gralloc usage flags. */
    public OutputConfiguration(int format, Size surfaceSize, long usage) {
        this(SURFACE_GROUP_ID_NONE, format, surfaceSize, usage);
    }

    /** API 36 (FLAG_CAMERA_DEVICE_SETUP). AOSP :958. */
    public OutputConfiguration(int surfaceGroupId, int format, Size surfaceSize, long usage) {
        this(surfaceGroupId, format, surfaceSize);
        mUsage = usage;
    }

    /** @hide -- AOSP's getUsage(). Exposed package-privately so a future
     *  configure path can honour the flags the app asked for. */
    long getUsage() { return mUsage; }

    /** @hide -- AOSP's setMultiResolutionOutput(). */
    void setMultiResolutionOutput() { mIsMultiResolution = true; }

    /** @hide -- AOSP's isMultiResolution(). */
    boolean isMultiResolution() { return mIsMultiResolution; }

    /** @hide -- AOSP's getConfiguredSize(); null when the configuration was
     *  built from a Surface whose size we cannot query. */
    Size getConfiguredSize() { return mConfiguredSize; }

    /**
     * API 36 (FLAG_MIRROR_MODE_SHARED_SURFACES). AOSP :1484 -- a per-Surface
     * mirror mode, for a shared configuration whose Surfaces want different
     * mirroring. Rejects a mode outside MIRROR_MODE_AUTO..MIRROR_MODE_V and a
     * Surface that is not part of this configuration, both with
     * IllegalArgumentException.
     */
    public void setMirrorMode(Surface surface, int mirrorMode) {
        if (surface == null) {
            throw new NullPointerException("Surface must not be null");
        }
        if (mirrorMode < MIRROR_MODE_AUTO || mirrorMode > MIRROR_MODE_V) {
            throw new IllegalArgumentException("Not a valid mirror mode " + mirrorMode);
        }
        int surfaceIndex = mSurfaces.indexOf(surface);
        if (surfaceIndex == -1) {
            throw new IllegalArgumentException("Surface not part of the OutputConfiguration");
        }
        mMirrorModeForSurfaces.set(surfaceIndex, mirrorMode);
    }

    /** API 36 (FLAG_MIRROR_MODE_SHARED_SURFACES). AOSP :1502. */
    public int getMirrorMode(Surface surface) {
        if (surface == null) {
            throw new NullPointerException("Surface must not be null");
        }
        int surfaceIndex = mSurfaces.indexOf(surface);
        if (surfaceIndex == -1) {
            throw new IllegalArgumentException("Surface not part of the OutputConfiguration");
        }
        return mMirrorModeForSurfaces.get(surfaceIndex);
    }

    /**
     * API 31. AOSP :620 -- one OutputConfiguration per underlying ImageReader of
     * a MultiResolutionImageReader, all sharing one surface group id and each
     * tagged with its physical camera id.
     *
     * Reproduced in full, because every piece it needs is real here:
     * MultiResolutionImageReader genuinely owns one android.media.ImageReader
     * per stream, and each of those genuinely has a Surface. The collection this
     * returns is only ever REJECTED later, at
     * CameraDeviceImpl.createCaptureSession, because no camera here is a logical
     * multi-camera -- which is the same place a real single-camera device
     * rejects it.
     */
    public static java.util.Collection<OutputConfiguration>
            createInstancesForMultiResolutionOutput(
                    android.hardware.camera2.MultiResolutionImageReader
                            multiResolutionImageReader) {
        if (multiResolutionImageReader == null) {
            throw new NullPointerException("Multi-resolution image reader must not be null");
        }
        int groupId = sNextMultiResolutionGroupId.getAndIncrement();
        android.media.ImageReader[] imageReaders = multiResolutionImageReader.getReaders();
        ArrayList<OutputConfiguration> configs = new ArrayList<OutputConfiguration>();
        for (int i = 0; i < imageReaders.length; i++) {
            MultiResolutionStreamInfo streamInfo =
                    multiResolutionImageReader.getStreamInfoForImageReader(imageReaders[i]);
            OutputConfiguration config =
                    new OutputConfiguration(groupId, imageReaders[i].getSurface());
            config.setPhysicalCameraId(streamInfo.getPhysicalCameraId());
            config.setMultiResolutionOutput();
            configs.add(config);
        }
        return configs;
    }

    /** API 36 (FLAG_CAMERA_DEVICE_SETUP). AOSP :679 -- the same thing built
     *  from stream INFO rather than from live readers, so a session's support
     *  can be queried before any buffer is allocated. */
    public static java.util.List<OutputConfiguration> createInstancesForMultiResolutionOutput(
            java.util.Collection<MultiResolutionStreamInfo> streams, int format) {
        if (streams == null || streams.size() <= 1) {
            throw new IllegalArgumentException("The streams list must contain at least 2 entries");
        }
        if (format == android.graphics.ImageFormat.NV21) {
            throw new IllegalArgumentException("NV21 format is not supported");
        }
        int groupId = sNextMultiResolutionGroupId.getAndIncrement();
        ArrayList<OutputConfiguration> configs = new ArrayList<OutputConfiguration>();
        for (MultiResolutionStreamInfo stream : streams) {
            Size surfaceSize = new Size(stream.getWidth(), stream.getHeight());
            OutputConfiguration config = new OutputConfiguration(groupId, format, surfaceSize);
            config.setPhysicalCameraId(stream.getPhysicalCameraId());
            config.setMultiResolutionOutput();
            configs.add(config);
        }
        return configs;
    }

    /** API 36 (FLAG_CAMERA_DEVICE_SETUP). AOSP :735 -- attach the reader's
     *  Surfaces to configurations previously built by the INFO-based factory
     *  above, matching each one by its configured size and physical camera id. */
    public static void setSurfacesForMultiResolutionOutput(
            java.util.Collection<OutputConfiguration> outputConfigurations,
            android.hardware.camera2.MultiResolutionImageReader multiResolutionImageReader) {
        if (outputConfigurations == null) {
            throw new NullPointerException("outputConfigurations must not be null");
        }
        if (multiResolutionImageReader == null) {
            throw new NullPointerException("multiResolutionImageReader must not be null");
        }
        if (outputConfigurations.size() != multiResolutionImageReader.getReaders().length) {
            throw new IllegalArgumentException(
                    "outputConfigurations and multiResolutionImageReader sizes must match");
        }
        for (OutputConfiguration config : outputConfigurations) {
            String physicalCameraId = config.getPhysicalCameraId();
            if (physicalCameraId == null) {
                physicalCameraId = "";
            }
            Surface surface = multiResolutionImageReader.getSurface(
                    config.getConfiguredSize(), physicalCameraId);
            config.addSurface(surface);
        }
    }

    // ---- Parcelable -------------------------------------------------------
    //
    // writeToParcel writes ZERO bytes ON PURPOSE, and android.os.Parcel's
    // documented empty-writer fallback (Parcel.writeParcelableBody, case 2 of
    // its live-object block comment) then carries the LIVE OutputConfiguration
    // and warns once on stderr.
    //
    // The alternative is not available, and it is worth saying why rather than
    // leaving it looking like laziness: an OutputConfiguration's whole point is
    // the Surfaces it holds, android.view.Surface in this tree is NOT a
    // Parcelable and not Serializable, and Parcel.writeValue throws
    // RuntimeException("unable to marshal value") for such a type. A
    // "real" writeToParcel would therefore either throw on ordinary
    // configurations or silently drop the Surfaces -- and a configuration that
    // came back with no Surface would configure a session that renders nowhere.
    // Keeping the live object round-trips it exactly, which is the whole of the
    // transport this Parcel has.
    //
    // REMOVAL CONDITION is automatic: the fallback is driven by a measurement
    // (did the body write anything), so the day Surface gains a real
    // writeToParcel and this class marshals for real, it stops firing with no
    // edit to Parcel.

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) { }

    public static final android.os.Parcelable.Creator<OutputConfiguration> CREATOR =
            new android.os.Parcelable.Creator<OutputConfiguration>() {
        /** Reads nothing, matching writeToParcel; Parcel then substitutes the
         *  live configuration it kept. The placeholder needs a Surface, and
         *  a fresh one is the only thing available -- it is never observed,
         *  because a well-formed round trip always replaces it. */
        @Override public OutputConfiguration createFromParcel(android.os.Parcel in) {
            return new OutputConfiguration(new Surface());
        }
        @Override public OutputConfiguration[] newArray(int size) {
            return new OutputConfiguration[size];
        }
    };
}
