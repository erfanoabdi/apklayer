package android.media;

import android.media.projection.MediaProjection;

// android.media.AudioPlaybackCaptureConfiguration -- API 29. Describes which
// OTHER apps' playback an AudioRecord should capture, built from a
// MediaProjection the user consented to.
//
// WHY IT EXISTS HERE AND WHAT IT HONESTLY CANNOT DO.
// AudioRecord.Builder.setAudioPlaybackCaptureConfig takes this type, so the
// type has to exist for that method to be declarable at all. The capture it
// configures does NOT exist and cannot: on Android, playback capture inserts a
// loopback AudioMix into AudioFlinger's policy engine and re-routes other apps'
// output into it. Mac.apk runs exactly one Android app per process and mixes it
// straight into CoreAudio -- there is no second app whose output could be
// captured, and no policy engine to insert a mix into.
//
// So the failure is surfaced at the point AOSP itself surfaces it: the Builder
// constructor, which on AOSP throws IllegalArgumentException("MediaProjection
// can not project audio") when handed a projection that is not audio-capable
// (AudioPlaybackCaptureConfiguration.java, Builder(MediaProjection) ->
// Preconditions.checkArgument(projection.getProjection().canProjectAudio())).
// Ours never is (MediaProjection.canProjectAudio, which explains why), so that
// is the exception an app gets -- the SAME one it would get on a real device
// after consenting to a video-only projection, which its existing error path
// already handles. The alternative -- accepting the config and letting
// AudioRecord quietly hand back microphone audio, or silence -- would be a lie
// the app has no way to detect.
//
// One AOSP-shape departure: AOSP stores the criteria in an
// android.media.audiopolicy.AudioMixingRule and the getters read them back out
// of it. That package does not exist in this tree (it is the client half of the
// policy engine we do not have), so the criteria are held directly. Every
// public method below -- the four getters, the four Builder rules, their
// IllegalStateException on mixing inclusive with exclusive -- behaves as AOSP's
// does; only the private storage differs.
public final class AudioPlaybackCaptureConfiguration {

    private final MediaProjection mProjection;
    private final int[] mMatchingUsages;
    private final int[] mMatchingUids;
    private final int[] mExcludeUsages;
    private final int[] mExcludeUids;

    private AudioPlaybackCaptureConfiguration(MediaProjection projection,
            int[] matchingUsages, int[] matchingUids,
            int[] excludeUsages, int[] excludeUids) {
        mProjection = projection;
        mMatchingUsages = matchingUsages;
        mMatchingUids = matchingUids;
        mExcludeUsages = excludeUsages;
        mExcludeUids = excludeUids;
    }

    /** The MediaProjection this configuration was built from. */
    public MediaProjection getMediaProjection() {
        return mProjection;
    }

    /** The usages passed to {@link Builder#addMatchingUsage(int)}. */
    public int[] getMatchingUsages() {
        return mMatchingUsages.clone();
    }

    /** The UIDs passed to {@link Builder#addMatchingUid(int)}. */
    public int[] getMatchingUids() {
        return mMatchingUids.clone();
    }

    /** The usages passed to {@link Builder#excludeUsage(int)}. */
    public int[] getExcludeUsages() {
        return mExcludeUsages.clone();
    }

    /** The UIDs passed to {@link Builder#excludeUid(int)}. */
    public int[] getExcludeUids() {
        return mExcludeUids.clone();
    }

    /** Builder for {@link AudioPlaybackCaptureConfiguration} instances. */
    public static final class Builder {

        private static final int MATCH_TYPE_UNSPECIFIED = 0;
        private static final int MATCH_TYPE_INCLUSIVE = 1;
        private static final int MATCH_TYPE_EXCLUSIVE = 2;

        private static final String ERROR_MESSAGE_MISMATCHED_RULES =
                "Inclusive and exclusive usage rules cannot be combined";
        private static final String ERROR_MESSAGE_NON_AUDIO_PROJECTION =
                "MediaProjection can not project audio";

        private final MediaProjection mProjection;
        private final java.util.ArrayList<Integer> mMatchingUsages =
                new java.util.ArrayList<Integer>();
        private final java.util.ArrayList<Integer> mMatchingUids =
                new java.util.ArrayList<Integer>();
        private final java.util.ArrayList<Integer> mExcludeUsages =
                new java.util.ArrayList<Integer>();
        private final java.util.ArrayList<Integer> mExcludeUids =
                new java.util.ArrayList<Integer>();
        private int mUsageMatchType = MATCH_TYPE_UNSPECIFIED;
        private int mUidMatchType = MATCH_TYPE_UNSPECIFIED;

        /**
         * @param projection a MediaProjection that supports audio projection.
         * @throws NullPointerException if projection is null (AOSP:
         *         Preconditions.checkNotNull).
         * @throws IllegalArgumentException if the projection cannot project
         *         audio. On Mac.apk none can -- see the class comment and
         *         MediaProjection.canProjectAudio. This throw is the whole point
         *         of the class existing: an app finds out here, at the call it
         *         made, instead of after recording a file of nothing.
         */
        public Builder(MediaProjection projection) {
            java.util.Objects.requireNonNull(projection);
            if (!projection.canProjectAudio()) {
                throw new IllegalArgumentException(ERROR_MESSAGE_NON_AUDIO_PROJECTION);
            }
            mProjection = projection;
        }

        /**
         * Only capture playback with the given usage. Repeat calls widen the
         * match (any of the given usages).
         *
         * @throws IllegalStateException if combined with {@link #excludeUsage(int)}.
         */
        public Builder addMatchingUsage(int usage) {
            if (mUsageMatchType == MATCH_TYPE_EXCLUSIVE) {
                throw new IllegalStateException(ERROR_MESSAGE_MISMATCHED_RULES);
            }
            mMatchingUsages.add(Integer.valueOf(usage));
            mUsageMatchType = MATCH_TYPE_INCLUSIVE;
            return this;
        }

        /**
         * Only capture playback from the app with the given uid. Repeat calls
         * widen the match.
         *
         * @throws IllegalStateException if combined with {@link #excludeUid(int)}.
         */
        public Builder addMatchingUid(int uid) {
            if (mUidMatchType == MATCH_TYPE_EXCLUSIVE) {
                throw new IllegalStateException(ERROR_MESSAGE_MISMATCHED_RULES);
            }
            mMatchingUids.add(Integer.valueOf(uid));
            mUidMatchType = MATCH_TYPE_INCLUSIVE;
            return this;
        }

        /**
         * Only capture playback that does NOT have the given usage.
         *
         * @throws IllegalStateException if combined with {@link #addMatchingUsage(int)}.
         */
        public Builder excludeUsage(int usage) {
            if (mUsageMatchType == MATCH_TYPE_INCLUSIVE) {
                throw new IllegalStateException(ERROR_MESSAGE_MISMATCHED_RULES);
            }
            mExcludeUsages.add(Integer.valueOf(usage));
            mUsageMatchType = MATCH_TYPE_EXCLUSIVE;
            return this;
        }

        /**
         * Only capture playback from apps that do NOT have the given uid.
         *
         * @throws IllegalStateException if combined with {@link #addMatchingUid(int)}.
         */
        public Builder excludeUid(int uid) {
            if (mUidMatchType == MATCH_TYPE_INCLUSIVE) {
                throw new IllegalStateException(ERROR_MESSAGE_MISMATCHED_RULES);
            }
            mExcludeUids.add(Integer.valueOf(uid));
            mUidMatchType = MATCH_TYPE_EXCLUSIVE;
            return this;
        }

        /**
         * Builds the configuration.
         *
         * @throws UnsupportedOperationException if the parameters set are
         *         incompatible (AOSP's declared throw, raised by AudioMixingRule
         *         .build(); the rules here are already rejected at the setter,
         *         which is where AOSP rejects them too).
         */
        public AudioPlaybackCaptureConfiguration build() {
            return new AudioPlaybackCaptureConfiguration(mProjection,
                    toIntArray(mMatchingUsages), toIntArray(mMatchingUids),
                    toIntArray(mExcludeUsages), toIntArray(mExcludeUids));
        }

        private static int[] toIntArray(java.util.List<Integer> list) {
            final int[] out = new int[list.size()];
            for (int i = 0; i < out.length; i++) out[i] = list.get(i).intValue();
            return out;
        }
    }
}
