// android.view.textclassifier.TextClassifierEvent -- the telemetry event a
// widget reports to TextClassifier.onTextClassifierEvent so the model can learn
// which of its suggestions were used. Source: frameworks/base
// core/java/android/view/textclassifier/TextClassifierEvent.java (android16-release).
//
// Mac.apk has no on-device model (TextClassifier.NO_OP), so the event is
// accepted and dropped -- which is exactly what AOSP's NO_OP does with it. The
// type hierarchy must exist because onTextClassifierEvent names it and because
// widgets CONSTRUCT one before calling.
package android.view.textclassifier;

public abstract class TextClassifierEvent implements android.os.Parcelable {
    public static final int CATEGORY_SELECTION = 1;
    public static final int CATEGORY_LINKIFY = 2;
    public static final int CATEGORY_CONVERSATION_ACTIONS = 3;
    public static final int CATEGORY_LANGUAGE_DETECTION = 4;

    public static final int TYPE_SELECTION_STARTED = 1;
    public static final int TYPE_SELECTION_MODIFIED = 2;
    public static final int TYPE_SMART_SELECTION_SINGLE = 3;
    public static final int TYPE_SMART_SELECTION_MULTI = 4;
    public static final int TYPE_AUTO_SELECTION = 5;
    public static final int TYPE_ACTIONS_SHOWN = 6;
    public static final int TYPE_LINK_CLICKED = 7;
    public static final int TYPE_OVERTYPE = 8;
    public static final int TYPE_COPY_ACTION = 9;
    public static final int TYPE_PASTE_ACTION = 10;
    public static final int TYPE_CUT_ACTION = 11;
    public static final int TYPE_SHARE_ACTION = 12;
    public static final int TYPE_SMART_ACTION = 13;
    public static final int TYPE_SELECTION_DRAG = 14;
    public static final int TYPE_SELECTION_DESTROYED = 15;
    public static final int TYPE_OTHER_ACTION = 16;
    public static final int TYPE_SELECT_ALL = 17;
    public static final int TYPE_SELECTION_RESET = 18;
    public static final int TYPE_MANUAL_REPLY = 19;
    public static final int TYPE_ACTIONS_GENERATED = 20;
    public static final int TYPE_LINKS_GENERATED = 21;

    private static final String[] NO_STRINGS = new String[0];
    private static final int[] NO_INTS = new int[0];
    private static final float[] NO_FLOATS = new float[0];

    private final int mEventCategory;
    private final int mEventType;
    private final String[] mEntityTypes;
    private final TextClassificationContext mEventContext;
    private final String mResultId;
    private final int mEventIndex;
    private final float[] mScores;
    private final String mModelName;
    private final int[] mActionIndices;
    private final android.icu.util.ULocale mLocale;
    private final android.os.Bundle mExtras;

    TextClassifierEvent(Builder<?> b) {
        mEventCategory = b.mEventCategory;
        mEventType = b.mEventType;
        mEntityTypes = b.mEntityTypes;
        mEventContext = b.mEventContext;
        mResultId = b.mResultId;
        mEventIndex = b.mEventIndex;
        mScores = b.mScores;
        mModelName = b.mModelName;
        mActionIndices = b.mActionIndices;
        mLocale = b.mLocale;
        mExtras = b.mExtras;
    }

    public int getEventCategory() { return mEventCategory; }
    public int getEventType() { return mEventType; }
    public String[] getEntityTypes() { return mEntityTypes; }
    public TextClassificationContext getEventContext() { return mEventContext; }
    public String getResultId() { return mResultId; }
    public int getEventIndex() { return mEventIndex; }
    public float[] getScores() { return mScores; }
    public String getModelName() { return mModelName; }
    public int[] getActionIndices() { return mActionIndices; }
    public android.icu.util.ULocale getLocale() { return mLocale; }
    public android.os.Bundle getExtras() { return mExtras; }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeInt(mEventCategory);
        dest.writeInt(mEventType);
        dest.writeStringArray(mEntityTypes);
        dest.writeParcelable(mEventContext, flags);
        dest.writeString(mResultId);
        dest.writeInt(mEventIndex);
        dest.writeFloatArray(mScores);
        dest.writeString(mModelName);
        dest.writeIntArray(mActionIndices);
        dest.writeString(mLocale == null ? null : mLocale.getName());
        dest.writeBundle(mExtras);
    }

    /** AOSP TextClassifierEvent.CREATOR dispatches on a leading token naming the
     *  concrete subclass; the same protocol is used here, and each subclass's own
     *  CREATOR is what actually rebuilds. Nothing in this tree ever parcels one
     *  (no model consumes events), so it exists for API completeness. */
    public static final android.os.Parcelable.Creator<TextClassifierEvent> CREATOR =
            new android.os.Parcelable.Creator<TextClassifierEvent>() {
        @Override public TextClassifierEvent createFromParcel(android.os.Parcel in) {
            final int start = in.dataPosition();
            final int category = in.readInt();
            in.setDataPosition(start);
            switch (category) {
                case CATEGORY_SELECTION:
                    return TextSelectionEvent.CREATOR.createFromParcel(in);
                case CATEGORY_LINKIFY:
                    return TextLinkifyEvent.CREATOR.createFromParcel(in);
                case CATEGORY_CONVERSATION_ACTIONS:
                    return ConversationActionsEvent.CREATOR.createFromParcel(in);
                case CATEGORY_LANGUAGE_DETECTION:
                    return LanguageDetectionEvent.CREATOR.createFromParcel(in);
                default:
                    throw new IllegalStateException(
                            "Unexpected event category in parcel: " + category);
            }
        }
        @Override public TextClassifierEvent[] newArray(int size) {
            return new TextClassifierEvent[size];
        }
    };

    /** AOSP TextClassifierEvent.Builder&lt;T&gt;: the self-typed base builder. */
    public abstract static class Builder<T extends Builder<T>> {
        private final int mEventCategory;
        private final int mEventType;
        private String[] mEntityTypes = NO_STRINGS;
        private TextClassificationContext mEventContext;
        private String mResultId;
        private int mEventIndex;
        private float[] mScores = NO_FLOATS;
        private String mModelName;
        private int[] mActionIndices = NO_INTS;
        private android.icu.util.ULocale mLocale;
        private android.os.Bundle mExtras = android.os.Bundle.EMPTY;

        Builder(int eventCategory, int eventType) {
            mEventCategory = eventCategory;
            mEventType = eventType;
        }

        public T setEntityTypes(String... entityTypes) {
            mEntityTypes = java.util.Objects.requireNonNull(entityTypes).clone();
            return self();
        }
        public T setEventContext(TextClassificationContext eventContext) {
            mEventContext = eventContext; return self();
        }
        public T setResultId(String resultId) { mResultId = resultId; return self(); }
        public T setEventIndex(int eventIndex) { mEventIndex = eventIndex; return self(); }
        public T setScores(float... scores) {
            mScores = java.util.Objects.requireNonNull(scores).clone();
            return self();
        }
        public T setModelName(String modelVersion) { mModelName = modelVersion; return self(); }
        public T setActionIndices(int... actionIndices) {
            mActionIndices = java.util.Objects.requireNonNull(actionIndices).clone();
            return self();
        }
        public T setLocale(android.icu.util.ULocale locale) { mLocale = locale; return self(); }
        public T setExtras(android.os.Bundle extras) {
            mExtras = java.util.Objects.requireNonNull(extras); return self();
        }

        @SuppressWarnings("unchecked")
        private T self() { return (T) this; }
    }

    /** AOSP TextClassifierEvent.TextSelectionEvent. */
    public static final class TextSelectionEvent extends TextClassifierEvent {
        private final int mRelativeWordStartIndex;
        private final int mRelativeWordEndIndex;
        private final int mRelativeSuggestedWordStartIndex;
        private final int mRelativeSuggestedWordEndIndex;

        private TextSelectionEvent(Builder b) {
            super(b);
            mRelativeWordStartIndex = b.mRelativeWordStartIndex;
            mRelativeWordEndIndex = b.mRelativeWordEndIndex;
            mRelativeSuggestedWordStartIndex = b.mRelativeSuggestedWordStartIndex;
            mRelativeSuggestedWordEndIndex = b.mRelativeSuggestedWordEndIndex;
        }

        public int getRelativeWordStartIndex() { return mRelativeWordStartIndex; }
        public int getRelativeWordEndIndex() { return mRelativeWordEndIndex; }
        public int getRelativeSuggestedWordStartIndex() { return mRelativeSuggestedWordStartIndex; }
        public int getRelativeSuggestedWordEndIndex() { return mRelativeSuggestedWordEndIndex; }

        public static final android.os.Parcelable.Creator<TextSelectionEvent> CREATOR =
                new android.os.Parcelable.Creator<TextSelectionEvent>() {
            @Override public TextSelectionEvent createFromParcel(android.os.Parcel in) {
                in.readInt();   // category, already peeked by TextClassifierEvent.CREATOR
                return new Builder(in.readInt()).build();
            }
            @Override public TextSelectionEvent[] newArray(int size) {
                return new TextSelectionEvent[size];
            }
        };

        public static final class Builder extends TextClassifierEvent.Builder<Builder> {
            private int mRelativeWordStartIndex;
            private int mRelativeWordEndIndex;
            private int mRelativeSuggestedWordStartIndex;
            private int mRelativeSuggestedWordEndIndex;

            public Builder(int eventType) { super(CATEGORY_SELECTION, eventType); }

            public Builder setRelativeWordStartIndex(int i) {
                mRelativeWordStartIndex = i; return this;
            }
            public Builder setRelativeWordEndIndex(int i) {
                mRelativeWordEndIndex = i; return this;
            }
            public Builder setRelativeSuggestedWordStartIndex(int i) {
                mRelativeSuggestedWordStartIndex = i; return this;
            }
            public Builder setRelativeSuggestedWordEndIndex(int i) {
                mRelativeSuggestedWordEndIndex = i; return this;
            }
            public TextSelectionEvent build() { return new TextSelectionEvent(this); }
        }
    }

    /** AOSP TextClassifierEvent.TextLinkifyEvent. */
    public static final class TextLinkifyEvent extends TextClassifierEvent {
        private TextLinkifyEvent(Builder b) { super(b); }

        public static final android.os.Parcelable.Creator<TextLinkifyEvent> CREATOR =
                new android.os.Parcelable.Creator<TextLinkifyEvent>() {
            @Override public TextLinkifyEvent createFromParcel(android.os.Parcel in) {
                in.readInt();
                return new Builder(in.readInt()).build();
            }
            @Override public TextLinkifyEvent[] newArray(int size) {
                return new TextLinkifyEvent[size];
            }
        };

        public static final class Builder extends TextClassifierEvent.Builder<Builder> {
            public Builder(int eventType) { super(CATEGORY_LINKIFY, eventType); }
            public TextLinkifyEvent build() { return new TextLinkifyEvent(this); }
        }
    }

    /** AOSP TextClassifierEvent.ConversationActionsEvent. */
    public static final class ConversationActionsEvent extends TextClassifierEvent {
        private ConversationActionsEvent(Builder b) { super(b); }

        public static final android.os.Parcelable.Creator<ConversationActionsEvent> CREATOR =
                new android.os.Parcelable.Creator<ConversationActionsEvent>() {
            @Override public ConversationActionsEvent createFromParcel(android.os.Parcel in) {
                in.readInt();
                return new Builder(in.readInt()).build();
            }
            @Override public ConversationActionsEvent[] newArray(int size) {
                return new ConversationActionsEvent[size];
            }
        };

        public static final class Builder extends TextClassifierEvent.Builder<Builder> {
            public Builder(int eventType) { super(CATEGORY_CONVERSATION_ACTIONS, eventType); }
            public ConversationActionsEvent build() { return new ConversationActionsEvent(this); }
        }
    }

    /** AOSP TextClassifierEvent.LanguageDetectionEvent. */
    public static final class LanguageDetectionEvent extends TextClassifierEvent {
        private LanguageDetectionEvent(Builder b) { super(b); }

        public static final android.os.Parcelable.Creator<LanguageDetectionEvent> CREATOR =
                new android.os.Parcelable.Creator<LanguageDetectionEvent>() {
            @Override public LanguageDetectionEvent createFromParcel(android.os.Parcel in) {
                in.readInt();
                return new Builder(in.readInt()).build();
            }
            @Override public LanguageDetectionEvent[] newArray(int size) {
                return new LanguageDetectionEvent[size];
            }
        };

        public static final class Builder extends TextClassifierEvent.Builder<Builder> {
            public Builder(int eventType) { super(CATEGORY_LANGUAGE_DETECTION, eventType); }
            public LanguageDetectionEvent build() { return new LanguageDetectionEvent(this); }
        }
    }
}
