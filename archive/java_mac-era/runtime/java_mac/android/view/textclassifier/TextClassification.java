package android.view.textclassifier;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The result of classifying a span of text (the entity types + suggested
 * actions the platform smart-linkify would offer). Mac.apk has no on-device
 * classifier, so TextClassifier.NO_OP returns an empty classification -- apps
 * fall back to their own handling, which is AOSP's documented no-classifier
 * behaviour. Everything an app SETS on the builder still comes back out, which
 * is what makes this a faithful value object rather than a sink.
 *
 * Source: frameworks/base/core/java/android/view/textclassifier/TextClassification.java
 * (android16-release). AOSP declares `implements Parcelable`.
 */
public final class TextClassification implements android.os.Parcelable {

    public static final TextClassification EMPTY = new Builder().build();

    private final String mText;
    private final Drawable mLegacyIcon;
    private final String mLegacyLabel;
    private final Intent mLegacyIntent;
    private final View.OnClickListener mLegacyOnClickListener;
    private final List<android.app.RemoteAction> mActions;
    private final List<String> mEntities;          // descending confidence
    private final java.util.Map<String, Float> mEntityScores;
    private final String mId;
    private final android.os.Bundle mExtras;

    private TextClassification(Builder b) {
        mText = b.mText;
        mLegacyIcon = b.mLegacyIcon;
        mLegacyLabel = b.mLegacyLabel;
        mLegacyIntent = b.mLegacyIntent;
        mLegacyOnClickListener = b.mLegacyOnClickListener;
        mActions = Collections.unmodifiableList(new ArrayList<android.app.RemoteAction>(b.mActions));
        mEntityScores = b.mEntityScores;
        mEntities = new ArrayList<String>(b.mEntityScores.keySet());
        // AOSP sorts entity types by DESCENDING confidence, and getEntity(0) is
        // documented as the highest-scoring type; callers rely on that ordering.
        Collections.sort(mEntities, (a, c) ->
                Float.compare(mEntityScores.get(c), mEntityScores.get(a)));
        mId = b.mId;
        mExtras = b.mExtras;
    }

    public String getText() { return mText; }
    public int getEntityCount() { return mEntities.size(); }
    public String getEntity(int index) { return mEntities.get(index); }
    public float getConfidenceScore(String entity) {
        Float f = mEntityScores.get(entity);
        return f == null ? 0f : f.floatValue();
    }
    public List<android.app.RemoteAction> getActions() { return mActions; }
    @Deprecated
    public CharSequence getLabel() { return mLegacyLabel; }
    @Deprecated
    public Drawable getIcon() { return mLegacyIcon; }
    @Deprecated
    public Intent getIntent() { return mLegacyIntent; }
    @Deprecated
    public View.OnClickListener getOnClickListener() { return mLegacyOnClickListener; }
    public String getId() { return mId; }
    /** AOSP getExtras(): @NonNull, empty when the classifier set none. */
    public android.os.Bundle getExtras() { return mExtras; }

    @Override public String toString() {
        return "TextClassification{text=" + mText + ", entities=" + mEntities
                + ", actions=" + mActions.size() + ", id=" + mId + "}";
    }

    // ---- Parcelable ---------------------------------------------------------
    // The legacy icon / OnClickListener are LIVE objects (a Drawable and a
    // listener) that AOSP itself cannot marshal either -- its writeToParcel
    // omits them and the rebuilt object has them null. Same here, so a round
    // trip loses exactly what it loses on a device.
    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeString(mText);
        dest.writeTypedList(mActions);
        dest.writeInt(mEntities.size());
        for (String entity : mEntities) {
            dest.writeString(entity);
            dest.writeFloat(getConfidenceScore(entity));
        }
        dest.writeString(mId);
        dest.writeBundle(mExtras);
        dest.writeString(mLegacyLabel);
        dest.writeParcelable(mLegacyIntent, flags);
    }

    public static final android.os.Parcelable.Creator<TextClassification> CREATOR =
            new android.os.Parcelable.Creator<TextClassification>() {
        @Override public TextClassification createFromParcel(android.os.Parcel in) {
            Builder b = new Builder().setText(in.readString());
            List<android.app.RemoteAction> actions =
                    in.createTypedArrayList(android.app.RemoteAction.CREATOR);
            if (actions != null) {
                for (android.app.RemoteAction a : actions) b.addAction(a);
            }
            final int n = in.readInt();
            for (int i = 0; i < n; i++) b.setEntityType(in.readString(), in.readFloat());
            b.setId(in.readString());
            android.os.Bundle extras = in.readBundle();
            if (extras != null) b.setExtras(extras);
            b.setLabel(in.readString());
            b.setIntent((Intent) in.readParcelable(Intent.class.getClassLoader()));
            return b.build();
        }
        @Override public TextClassification[] newArray(int size) {
            return new TextClassification[size];
        }
    };

    public static final class Builder {
        private String mText;
        private Drawable mLegacyIcon;
        private String mLegacyLabel;
        private Intent mLegacyIntent;
        private View.OnClickListener mLegacyOnClickListener;
        private final List<android.app.RemoteAction> mActions =
                new ArrayList<android.app.RemoteAction>();
        private final java.util.Map<String, Float> mEntityScores =
                new java.util.HashMap<String, Float>();
        private String mId;
        private android.os.Bundle mExtras = android.os.Bundle.EMPTY;

        public Builder() { }

        public Builder setText(String text) { mText = text; return this; }
        public Builder setId(String id) { mId = id; return this; }

        /** AOSP Builder.setEntityType(String, float): AOSP validates the score
         *  range and keeps the highest-scoring type first (see the sort in the
         *  constructor). */
        public Builder setEntityType(String type, float confidenceScore) {
            java.util.Objects.requireNonNull(type);
            if (confidenceScore < 0 || confidenceScore > 1) {
                throw new IllegalArgumentException("confidenceScore must be in [0, 1]");
            }
            mEntityScores.put(type, Float.valueOf(confidenceScore));
            return this;
        }

        /** AOSP Builder.addAction(RemoteAction): append a suggested action; the
         *  FIRST one added is the primary action. */
        public Builder addAction(android.app.RemoteAction action) {
            mActions.add(java.util.Objects.requireNonNull(action));
            return this;
        }

        /** AOSP Builder.setExtras(Bundle). */
        public Builder setExtras(android.os.Bundle extras) {
            mExtras = extras != null ? extras : android.os.Bundle.EMPTY;
            return this;
        }

        /** AOSP Builder.setIcon / setLabel / setIntent / setOnClickListener, all
         *  deprecated at API 28 in favour of addAction(RemoteAction). They still
         *  round-trip through the matching deprecated getters. */
        @Deprecated
        public Builder setIcon(Drawable icon) { mLegacyIcon = icon; return this; }
        @Deprecated
        public Builder setLabel(String label) { mLegacyLabel = label; return this; }
        @Deprecated
        public Builder setIntent(Intent intent) { mLegacyIntent = intent; return this; }
        @Deprecated
        public Builder setOnClickListener(View.OnClickListener onClickListener) {
            mLegacyOnClickListener = onClickListener;
            return this;
        }

        public TextClassification build() { return new TextClassification(this); }
    }

    /** AOSP TextClassification.Request: the text and range to classify. */
    public static final class Request implements android.os.Parcelable {
        private final CharSequence mText;
        private final int mStart, mEnd;
        private android.os.LocaleList mLocales;
        private java.time.ZonedDateTime mReferenceTime;
        private android.os.Bundle mExtras = android.os.Bundle.EMPTY;
        private String mCallingPackageName;

        private Request(CharSequence text, int start, int end) {
            mText = text; mStart = start; mEnd = end;
        }
        public CharSequence getText() { return mText; }
        public int getStartIndex() { return mStart; }
        public int getEndIndex() { return mEnd; }
        public android.os.LocaleList getDefaultLocales() { return mLocales; }
        /** AOSP getReferenceTime(): the "now" a relative date ("tomorrow") is
         *  resolved against, null when the caller did not supply one. */
        public java.time.ZonedDateTime getReferenceTime() { return mReferenceTime; }
        public android.os.Bundle getExtras() { return mExtras; }
        /** AOSP: stamped by the SYSTEM as the request crosses to the classifier
         *  service. Null for a request that never left the app, which is every
         *  request here. */
        public String getCallingPackageName() { return mCallingPackageName; }

        @Override public int describeContents() { return 0; }
        @Override public void writeToParcel(android.os.Parcel dest, int flags) {
            dest.writeCharSequence(mText);
            dest.writeInt(mStart);
            dest.writeInt(mEnd);
            dest.writeString(mLocales == null ? null : mLocales.toLanguageTags());
            dest.writeString(mReferenceTime == null ? null : mReferenceTime.toString());
            dest.writeBundle(mExtras);
            dest.writeString(mCallingPackageName);
        }
        public static final android.os.Parcelable.Creator<Request> CREATOR =
                new android.os.Parcelable.Creator<Request>() {
            @Override public Request createFromParcel(android.os.Parcel in) {
                Request r = new Request(in.readCharSequence(), in.readInt(), in.readInt());
                String tags = in.readString();
                r.mLocales = tags == null ? null : android.os.LocaleList.forLanguageTags(tags);
                String time = in.readString();
                r.mReferenceTime = time == null ? null : java.time.ZonedDateTime.parse(time);
                android.os.Bundle extras = in.readBundle();
                if (extras != null) r.mExtras = extras;
                r.mCallingPackageName = in.readString();
                return r;
            }
            @Override public Request[] newArray(int size) { return new Request[size]; }
        };

        public static final class Builder {
            private final CharSequence mText;
            private final int mStart, mEnd;
            private android.os.LocaleList mLocales;
            private java.time.ZonedDateTime mReferenceTime;
            private android.os.Bundle mExtras = android.os.Bundle.EMPTY;

            public Builder(CharSequence text, int startIndex, int endIndex) {
                mText = text; mStart = startIndex; mEnd = endIndex;
            }
            public Builder setDefaultLocales(android.os.LocaleList locales) {
                mLocales = locales; return this;
            }
            public Builder setReferenceTime(java.time.ZonedDateTime referenceTime) {
                mReferenceTime = referenceTime; return this;
            }
            public Builder setExtras(android.os.Bundle bundle) {
                mExtras = bundle != null ? bundle : android.os.Bundle.EMPTY; return this;
            }
            public Request build() {
                Request r = new Request(mText, mStart, mEnd);
                r.mLocales = mLocales;
                r.mReferenceTime = mReferenceTime;
                r.mExtras = mExtras;
                return r;
            }
        }
    }
}
