package android.media;

import android.util.Pair;

import java.util.Collections;
import java.util.List;

/**
 * Describes one physical microphone the device exposes (API 28).
 *
 * Mac.apk records through CoreAudio's default input device, and CoreAudio
 * describes an input as a stream with a channel count -- it does not publish the
 * physical geometry (position, orientation, sensitivity, SPL range, frequency
 * response) that this class was designed to carry. Every such field therefore
 * reports AOSP's OWN "unknown" sentinel, which exists precisely for hardware
 * that does not publish the value: POSITION_UNKNOWN / ORIENTATION_UNKNOWN,
 * SENSITIVITY_UNKNOWN, SPL_UNKNOWN, DIRECTIONALITY_UNKNOWN, LOCATION_UNKNOWN,
 * GROUP_UNKNOWN. That is an honest answer, not a placeholder, and apps already
 * branch on those sentinels because most Android phones do not populate the
 * geometry either.
 *
 * Source: frameworks/base/media/java/android/media/MicrophoneInfo.java
 * (android16-release).
 */
public final class MicrophoneInfo {

    public static final int LOCATION_UNKNOWN = 0;
    public static final int LOCATION_MAINBODY = 1;
    public static final int LOCATION_MAINBODY_MOVABLE = 2;
    public static final int LOCATION_PERIPHERAL = 3;

    public static final int DIRECTIONALITY_UNKNOWN = 0;
    public static final int DIRECTIONALITY_OMNI = 1;
    public static final int DIRECTIONALITY_BI_DIRECTIONAL = 2;
    public static final int DIRECTIONALITY_CARDIOID = 3;
    public static final int DIRECTIONALITY_HYPER_CARDIOID = 4;
    public static final int DIRECTIONALITY_SUPER_CARDIOID = 5;

    public static final int CHANNEL_MAPPING_DIRECT = 1;
    public static final int CHANNEL_MAPPING_PROCESSED = 2;

    public static final int GROUP_UNKNOWN = -1;
    public static final int INDEX_IN_THE_GROUP_UNKNOWN = -1;

    /** AOSP: -Float.MAX_VALUE (MicrophoneInfo.java L120-130). */
    public static final float SENSITIVITY_UNKNOWN = -Float.MAX_VALUE;
    public static final float SPL_UNKNOWN = -Float.MAX_VALUE;

    public static final Coordinate3F POSITION_UNKNOWN = new Coordinate3F(-Float.MAX_VALUE,
            -Float.MAX_VALUE, -Float.MAX_VALUE);
    public static final Coordinate3F ORIENTATION_UNKNOWN = new Coordinate3F(0.0f, 0.0f, 0.0f);

    private final String mDeviceId;
    private final int mType;
    private final String mAddress;
    private final int mLocation;
    private final int mGroup;
    private final int mIndexInTheGroup;
    private final Coordinate3F mPosition;
    private final Coordinate3F mOrientation;
    private final List<Pair<Float, Float>> mFrequencyResponse;
    private final List<Pair<Integer, Integer>> mChannelMapping;
    private final float mSensitivity;
    private final float mMaxSpl;
    private final float mMinSpl;
    private final int mDirectionality;
    private final int mId;

    MicrophoneInfo(String deviceId, int type, String address, int location, int group,
            int indexInTheGroup, Coordinate3F position, Coordinate3F orientation,
            List<Pair<Float, Float>> frequencyResponse,
            List<Pair<Integer, Integer>> channelMapping, float sensitivity, float maxSpl,
            float minSpl, int directionality, int id) {
        mDeviceId = deviceId;
        mType = type;
        mAddress = address;
        mLocation = location;
        mGroup = group;
        mIndexInTheGroup = indexInTheGroup;
        mPosition = position;
        mOrientation = orientation;
        mFrequencyResponse = frequencyResponse;
        mChannelMapping = channelMapping;
        mSensitivity = sensitivity;
        mMaxSpl = maxSpl;
        mMinSpl = minSpl;
        mDirectionality = directionality;
        mId = id;
    }

    /**
     * The host's default audio input, described with AOSP's unknown-sentinels
     * for every property CoreAudio does not publish. Package-private: it is
     * built by AudioManager.getMicrophones(), which is the only supported way an
     * app obtains one on Android too.
     */
    static MicrophoneInfo defaultBuiltIn(int id, String address) {
        return new MicrophoneInfo("builtin_mic", AudioDeviceInfo.TYPE_BUILTIN_MIC, address,
                LOCATION_UNKNOWN, GROUP_UNKNOWN, INDEX_IN_THE_GROUP_UNKNOWN,
                POSITION_UNKNOWN, ORIENTATION_UNKNOWN,
                Collections.<Pair<Float, Float>>emptyList(),
                Collections.<Pair<Integer, Integer>>emptyList(),
                SENSITIVITY_UNKNOWN, SPL_UNKNOWN, SPL_UNKNOWN, DIRECTIONALITY_UNKNOWN, id);
    }

    /** AOSP: the human-readable device id ("builtin_mic"). */
    public String getDescription() { return mDeviceId; }
    public int getId() { return mId; }
    public int getType() { return mType; }
    /** @NonNull in AOSP; the port address, empty for the built-in device. */
    public String getAddress() { return mAddress; }
    public int getLocation() { return mLocation; }
    public int getGroup() { return mGroup; }
    public int getIndexInTheGroup() { return mIndexInTheGroup; }
    public Coordinate3F getPosition() { return mPosition; }
    public Coordinate3F getOrientation() { return mOrientation; }
    /** (frequency Hz, response dB) pairs; empty when the HAL publishes none. */
    public List<Pair<Float, Float>> getFrequencyResponse() { return mFrequencyResponse; }
    /** (channel index, CHANNEL_MAPPING_*) pairs; empty when unpublished. */
    public List<Pair<Integer, Integer>> getChannelMapping() { return mChannelMapping; }
    public float getSensitivity() { return mSensitivity; }
    public float getMaxSpl() { return mMaxSpl; }
    public float getMinSpl() { return mMinSpl; }
    public int getDirectionality() { return mDirectionality; }

    /** A point or vector in the device's coordinate space. */
    public static final class Coordinate3F {
        public final float x;
        public final float y;
        public final float z;

        Coordinate3F(float x, float y, float z) { this.x = x; this.y = y; this.z = z; }

        @Override public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof Coordinate3F)) return false;
            Coordinate3F o = (Coordinate3F) obj;
            return x == o.x && y == o.y && z == o.z;
        }
        @Override public int hashCode() {
            return Float.hashCode(x) * 31 * 31 + Float.hashCode(y) * 31 + Float.hashCode(z);
        }
    }
}
