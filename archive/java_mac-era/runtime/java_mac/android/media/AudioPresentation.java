package android.media;

import android.icu.util.ULocale;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Describes one selectable audio presentation inside a broadcast/next-gen audio
 * stream -- the main mix, a commentary track, a visually-impaired mix, and so on
 * (API 28). This is DESCRIPTIVE data an extractor or a TV tuner reports, and an
 * app both reads it and (via the Builder) constructs it, so the whole class is
 * ordinary data modelling with nothing host-specific in it.
 *
 * Mac.apk has no next-gen-audio (AC-4 / MPEG-H) extractor, so
 * MediaExtractor.getAudioPresentations() honestly reports none; that is a
 * separate question from whether this type works, and it does.
 *
 * Source: frameworks/base/media/java/android/media/AudioPresentation.java
 * (android16-release).
 */
public final class AudioPresentation implements Parcelable {

    public static final int CONTENT_UNKNOWN = -1;
    public static final int CONTENT_MAIN = 0;
    public static final int CONTENT_MUSIC_AND_EFFECTS = 1;
    public static final int CONTENT_VISUALLY_IMPAIRED = 2;
    public static final int CONTENT_HEARING_IMPAIRED = 3;
    public static final int CONTENT_DIALOG = 4;
    public static final int CONTENT_COMMENTARY = 5;
    public static final int CONTENT_EMERGENCY = 6;
    public static final int CONTENT_VOICEOVER = 7;

    public static final int MASTERING_NOT_INDICATED = 0;
    public static final int MASTERED_FOR_STEREO = 1;
    public static final int MASTERED_FOR_SURROUND = 2;
    public static final int MASTERED_FOR_3D = 3;
    public static final int MASTERED_FOR_HEADPHONE = 4;

    public static final int PRESENTATION_ID_UNKNOWN = -1;
    public static final int PROGRAM_ID_UNKNOWN = -1;

    private final int mPresentationId;
    private final int mProgramId;
    private final ULocale mLanguage;
    private final int mMasteringIndication;
    private final boolean mAudioDescriptionAvailable;
    private final boolean mSpokenSubtitlesAvailable;
    private final boolean mDialogueEnhancementAvailable;
    private final Map<ULocale, CharSequence> mLabels;

    private AudioPresentation(Builder b) {
        mPresentationId = b.mPresentationId;
        mProgramId = b.mProgramId;
        mLanguage = b.mLanguage;
        mMasteringIndication = b.mMasteringIndication;
        mAudioDescriptionAvailable = b.mAudioDescriptionAvailable;
        mSpokenSubtitlesAvailable = b.mSpokenSubtitlesAvailable;
        mDialogueEnhancementAvailable = b.mDialogueEnhancementAvailable;
        mLabels = Collections.unmodifiableMap(new HashMap<>(b.mLabels));
    }

    public int getPresentationId() { return mPresentationId; }
    public int getProgramId() { return mProgramId; }

    /**
     * AOSP returns java.util.Locale here even though the Builder takes a ULocale
     * (AudioPresentation.java L200-210): the public getter predates the ULocale
     * switch. Converted through the language tag, as AOSP does.
     */
    public Locale getLocale() {
        return (mLanguage == null) ? new Locale("") : Locale.forLanguageTag(mLanguage.toLanguageTag());
    }

    /** Likewise Locale-keyed and String-valued on the way out, ULocale-keyed and
     *  CharSequence-valued on the way in. */
    public Map<Locale, String> getLabels() {
        Map<Locale, String> out = new HashMap<>(mLabels.size());
        for (Map.Entry<ULocale, CharSequence> e : mLabels.entrySet()) {
            out.put(Locale.forLanguageTag(e.getKey().toLanguageTag()),
                    String.valueOf(e.getValue()));
        }
        return Collections.unmodifiableMap(out);
    }

    public int getMasteringIndication() { return mMasteringIndication; }
    public boolean hasAudioDescription() { return mAudioDescriptionAvailable; }
    public boolean hasSpokenSubtitles() { return mSpokenSubtitlesAvailable; }
    public boolean hasDialogueEnhancement() { return mDialogueEnhancementAvailable; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mPresentationId);
        dest.writeInt(mProgramId);
        dest.writeString(mLanguage == null ? null : mLanguage.toLanguageTag());
        dest.writeInt(mMasteringIndication);
        dest.writeBoolean(mAudioDescriptionAvailable);
        dest.writeBoolean(mSpokenSubtitlesAvailable);
        dest.writeBoolean(mDialogueEnhancementAvailable);
        dest.writeInt(mLabels.size());
        for (Map.Entry<ULocale, CharSequence> e : mLabels.entrySet()) {
            dest.writeString(e.getKey().toLanguageTag());
            dest.writeString(String.valueOf(e.getValue()));
        }
    }

    @Override public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof AudioPresentation)) return false;
        AudioPresentation o = (AudioPresentation) obj;
        return mPresentationId == o.mPresentationId
                && mProgramId == o.mProgramId
                && mMasteringIndication == o.mMasteringIndication
                && mAudioDescriptionAvailable == o.mAudioDescriptionAvailable
                && mSpokenSubtitlesAvailable == o.mSpokenSubtitlesAvailable
                && mDialogueEnhancementAvailable == o.mDialogueEnhancementAvailable
                && Objects.equals(String.valueOf(mLanguage), String.valueOf(o.mLanguage))
                && mLabels.equals(o.mLabels);
    }

    @Override public int hashCode() {
        return Objects.hash(mPresentationId, mProgramId, mMasteringIndication,
                mAudioDescriptionAvailable, mSpokenSubtitlesAvailable,
                mDialogueEnhancementAvailable, String.valueOf(mLanguage), mLabels);
    }

    @Override public String toString() {
        return "AudioPresentation{ presentationId=" + mPresentationId
                + ", programId=" + mProgramId + ", language=" + mLanguage + " }";
    }

    public static final Parcelable.Creator<AudioPresentation> CREATOR =
            new Parcelable.Creator<AudioPresentation>() {
        @Override public AudioPresentation createFromParcel(Parcel in) {
            Builder b = new Builder(in.readInt());
            b.setProgramId(in.readInt());
            String tag = in.readString();
            // ULocale(String localeID) is the constructor form this tree's
            // android.icu.util.ULocale provides; it parses a language tag.
            if (tag != null) b.setLocale(new ULocale(tag));
            b.setMasteringIndication(in.readInt());
            b.setHasAudioDescription(in.readBoolean());
            b.setHasSpokenSubtitles(in.readBoolean());
            b.setHasDialogueEnhancement(in.readBoolean());
            int count = in.readInt();
            Map<ULocale, CharSequence> labels = new HashMap<>();
            for (int i = 0; i < count; i++) {
                String key = in.readString();
                labels.put(new ULocale(key), in.readString());
            }
            if (!labels.isEmpty()) b.setLabels(labels);
            return b.build();
        }
        @Override public AudioPresentation[] newArray(int size) {
            return new AudioPresentation[size];
        }
    };

    /** AOSP defaults: program id UNKNOWN, empty language, MASTERING_NOT_INDICATED,
     *  no audio description / spoken subtitles / dialogue enhancement, no labels
     *  (AudioPresentation.java L290-310). */
    public static final class Builder {
        private final int mPresentationId;
        private int mProgramId = PROGRAM_ID_UNKNOWN;
        private ULocale mLanguage = new ULocale("");
        private int mMasteringIndication = MASTERING_NOT_INDICATED;
        private boolean mAudioDescriptionAvailable;
        private boolean mSpokenSubtitlesAvailable;
        private boolean mDialogueEnhancementAvailable;
        private Map<ULocale, CharSequence> mLabels = new HashMap<>();

        public Builder(int presentationId) { mPresentationId = presentationId; }

        public Builder setProgramId(int programId) { mProgramId = programId; return this; }

        public Builder setLocale(ULocale language) {
            mLanguage = Objects.requireNonNull(language, "language must not be null");
            return this;
        }

        /** AOSP rejects a mastering indication outside the defined set
         *  (AudioPresentation.java L340-355). */
        public Builder setMasteringIndication(int masteringIndication) {
            if (masteringIndication != MASTERING_NOT_INDICATED
                    && masteringIndication != MASTERED_FOR_STEREO
                    && masteringIndication != MASTERED_FOR_SURROUND
                    && masteringIndication != MASTERED_FOR_3D
                    && masteringIndication != MASTERED_FOR_HEADPHONE) {
                throw new IllegalArgumentException(
                        "Unknown mastering indication: " + masteringIndication);
            }
            mMasteringIndication = masteringIndication;
            return this;
        }

        public Builder setLabels(Map<ULocale, CharSequence> labels) {
            mLabels = new HashMap<>(Objects.requireNonNull(labels, "labels must not be null"));
            return this;
        }

        public Builder setHasAudioDescription(boolean audioDescriptionAvailable) {
            mAudioDescriptionAvailable = audioDescriptionAvailable; return this;
        }
        public Builder setHasSpokenSubtitles(boolean spokenSubtitlesAvailable) {
            mSpokenSubtitlesAvailable = spokenSubtitlesAvailable; return this;
        }
        public Builder setHasDialogueEnhancement(boolean dialogueEnhancementAvailable) {
            mDialogueEnhancementAvailable = dialogueEnhancementAvailable; return this;
        }

        public AudioPresentation build() { return new AudioPresentation(this); }
    }
}
