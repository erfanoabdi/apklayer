// android.telecom.VideoProfile -- the video state + quality of one call.
//
// A REAL implementation: this is a pure immutable value type plus a set of
// static bit tests over its own STATE_* mask. Nothing in it is a device
// capability, so every method here computes the same answer it would on
// Android. VideoProfile.isBidirectional(state) is arithmetic, not a radio
// query.
//
// It exists in this tree because Connection.VideoProvider and
// RemoteConnection.VideoProvider name it in their signatures. A Mac has no
// cellular video-call path, so no VideoProvider callback is ever DRIVEN --
// but an app that builds a VideoProfile and asks it questions gets correct
// answers.
//
// Mirrors telecomm/java/android/telecom/VideoProfile.java; public surface and
// constant values confirmed against frameworks/base/core/api/current.txt.
package android.telecom;

import android.os.Parcel;
import android.os.Parcelable;

public class VideoProfile implements Parcelable {

    public static final int QUALITY_UNKNOWN = 0;
    public static final int QUALITY_HIGH = 1;
    public static final int QUALITY_MEDIUM = 2;
    public static final int QUALITY_LOW = 3;
    public static final int QUALITY_DEFAULT = 4;

    public static final int STATE_AUDIO_ONLY = 0x0;
    public static final int STATE_TX_ENABLED = 0x1;
    public static final int STATE_RX_ENABLED = 0x2;
    public static final int STATE_BIDIRECTIONAL = STATE_TX_ENABLED | STATE_RX_ENABLED;
    public static final int STATE_PAUSED = 0x4;

    private final int mVideoState;
    private final int mVideoQuality;

    public VideoProfile(int videoState) {
        this(videoState, QUALITY_DEFAULT);
    }

    public VideoProfile(int videoState, int quality) {
        mVideoState = videoState;
        mVideoQuality = quality;
    }

    public int getVideoState() {
        return mVideoState;
    }

    public int getQuality() {
        return mVideoQuality;
    }

    @Override public int describeContents() {
        return 0;
    }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mVideoState);
        dest.writeInt(mVideoQuality);
    }

    @Override public String toString() {
        return "[VideoProfile videoState = " + videoStateToString(mVideoState)
                + " videoQuality = " + mVideoQuality + "]";
    }

    public static final Creator<VideoProfile> CREATOR = new Creator<VideoProfile>() {
        @Override public VideoProfile createFromParcel(Parcel source) {
            return new VideoProfile(source.readInt(), source.readInt());
        }

        @Override public VideoProfile[] newArray(int size) {
            return new VideoProfile[size];
        }
    };

    // The bit tests below are AOSP's, verbatim in behaviour: audio-only means
    // NEITHER direction is enabled (a PAUSED bidirectional call is still not
    // audio-only), and isVideo() is "any direction or paused".

    public static boolean isAudioOnly(int videoState) {
        return !hasState(videoState, STATE_TX_ENABLED)
                && !hasState(videoState, STATE_RX_ENABLED);
    }

    public static boolean isVideo(int videoState) {
        return hasState(videoState, STATE_TX_ENABLED)
                || hasState(videoState, STATE_RX_ENABLED)
                || hasState(videoState, STATE_BIDIRECTIONAL);
    }

    public static boolean isTransmissionEnabled(int videoState) {
        return hasState(videoState, STATE_TX_ENABLED);
    }

    public static boolean isReceptionEnabled(int videoState) {
        return hasState(videoState, STATE_RX_ENABLED);
    }

    public static boolean isBidirectional(int videoState) {
        return hasState(videoState, STATE_BIDIRECTIONAL);
    }

    public static boolean isPaused(int videoState) {
        return hasState(videoState, STATE_PAUSED);
    }

    /** AOSP's private helper: an exact-mask test, not a "any bit" test. */
    private static boolean hasState(int videoState, int state) {
        return (videoState & state) == state;
    }

    public static String videoStateToString(int videoState) {
        StringBuilder sb = new StringBuilder();
        sb.append("Audio");
        if (isAudioOnly(videoState)) {
            sb.append(" Only");
        } else {
            if (isTransmissionEnabled(videoState)) {
                sb.append(" Tx");
            }
            if (isReceptionEnabled(videoState)) {
                sb.append(" Rx");
            }
            if (isPaused(videoState)) {
                sb.append(" Pause");
            }
        }
        return sb.toString();
    }

    /**
     * The camera's still/zoom capabilities, reported back through
     * Connection.VideoProvider.changeCameraCapabilities. Pure value type.
     */
    public static final class CameraCapabilities implements Parcelable {

        private final int mWidth;
        private final int mHeight;
        private final boolean mZoomSupported;
        private final float mMaxZoom;

        public CameraCapabilities(int width, int height) {
            this(width, height, false, 1.0f);
        }

        public CameraCapabilities(int width, int height, boolean zoomSupported, float maxZoom) {
            mWidth = width;
            mHeight = height;
            mZoomSupported = zoomSupported;
            mMaxZoom = maxZoom;
        }

        public int getWidth() {
            return mWidth;
        }

        public int getHeight() {
            return mHeight;
        }

        public boolean isZoomSupported() {
            return mZoomSupported;
        }

        public float getMaxZoom() {
            return mMaxZoom;
        }

        @Override public int describeContents() {
            return 0;
        }

        @Override public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mWidth);
            dest.writeInt(mHeight);
            dest.writeByte((byte) (mZoomSupported ? 1 : 0));
            dest.writeFloat(mMaxZoom);
        }

        public static final Creator<CameraCapabilities> CREATOR =
                new Creator<CameraCapabilities>() {
            @Override public CameraCapabilities createFromParcel(Parcel source) {
                int width = source.readInt();
                int height = source.readInt();
                boolean zoomSupported = source.readByte() != 0;
                float maxZoom = source.readFloat();
                return new CameraCapabilities(width, height, zoomSupported, maxZoom);
            }

            @Override public CameraCapabilities[] newArray(int size) {
                return new CameraCapabilities[size];
            }
        };
    }
}
