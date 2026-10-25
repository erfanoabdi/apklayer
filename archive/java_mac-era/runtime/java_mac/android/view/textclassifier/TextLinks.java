package android.view.textclassifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The result of asking the TextClassifier to find linkable entities (addresses,
 * phone numbers, URLs) in a span of text.
 *
 * Mac.apk has no on-device text-classifier model. AOSP's own contract when no
 * classifier is available is TextClassifier.NO_OP, which returns a TextLinks
 * with an EMPTY link set -- so callers fall back to their own Linkify patterns,
 * which still work. That is the behaviour here. Everything an app SETS on the
 * builder still comes back out, and apply() really does install spans, so a
 * caller that builds its own TextLinks gets device-identical behaviour.
 *
 * Source: frameworks/base/core/java/android/view/textclassifier/TextLinks.java
 * (android16-release). AOSP declares `implements Parcelable`.
 */
public final class TextLinks implements android.os.Parcelable {

    public static final int STATUS_LINKS_APPLIED = 0;
    public static final int STATUS_NO_LINKS_FOUND = 1;
    public static final int STATUS_NO_LINKS_APPLIED = 2;
    public static final int STATUS_DIFFERENT_TEXT = 3;
    public static final int STATUS_UNSUPPORTED_CHARACTER = 4;

    public static final int APPLY_STRATEGY_IGNORE = 0;
    public static final int APPLY_STRATEGY_REPLACE = 1;

    private final String mFullText;
    private final List<TextLink> mLinks;
    private final android.os.Bundle mExtras;

    private TextLinks(String fullText, List<TextLink> links, android.os.Bundle extras) {
        mFullText = fullText;
        mLinks = Collections.unmodifiableList(links);
        mExtras = extras;
    }

    // AOSP returns CharSequence.
    public CharSequence getText() { return mFullText; }

    public Collection<TextLink> getLinks() { return mLinks; }

    /** AOSP getExtras(): @NonNull, empty when the classifier set none. */
    public android.os.Bundle getExtras() { return mExtras; }

    /** AOSP TextLinks.apply(Spannable, int applyStrategy, Function): install a
     *  TextLinkSpan for each link, honouring the strategy when a ClickableSpan
     *  is already present. AOSP's status ladder is reproduced exactly:
     *  DIFFERENT_TEXT when the spannable is not the text these links describe,
     *  NO_LINKS_FOUND when there are none, NO_LINKS_APPLIED when every link was
     *  skipped, LINKS_APPLIED otherwise. */
    public int apply(android.text.Spannable text, int applyStrategy,
                     java.util.function.Function<TextLink, TextLinkSpan> spanFactory) {
        java.util.Objects.requireNonNull(text);
        checkApplyStrategy(applyStrategy);
        if (!text.toString().startsWith(mFullText)) return STATUS_DIFFERENT_TEXT;
        if (mLinks.isEmpty()) return STATUS_NO_LINKS_FOUND;

        int applyCount = 0;
        for (TextLink link : mLinks) {
            final TextLinkSpan span = spanFactory != null
                    ? spanFactory.apply(link) : new TextLinkSpan(link);
            if (span == null) continue;
            final android.text.style.ClickableSpan[] existing =
                    text.getSpans(link.getStart(), link.getEnd(),
                            android.text.style.ClickableSpan.class);
            if (existing != null && existing.length > 0) {
                if (applyStrategy == APPLY_STRATEGY_REPLACE) {
                    for (android.text.style.ClickableSpan s : existing) text.removeSpan(s);
                } else {
                    continue;   // APPLY_STRATEGY_IGNORE
                }
            }
            text.setSpan(span, link.getStart(), link.getEnd(),
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            applyCount++;
        }
        return applyCount == 0 ? STATUS_NO_LINKS_APPLIED : STATUS_LINKS_APPLIED;
    }

    private static void checkApplyStrategy(int applyStrategy) {
        if (applyStrategy != APPLY_STRATEGY_IGNORE && applyStrategy != APPLY_STRATEGY_REPLACE) {
            throw new IllegalArgumentException(
                    "Invalid apply strategy. See TextLinks.ApplyStrategy for options.");
        }
    }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeString(mFullText);
        dest.writeTypedList(mLinks);
        dest.writeBundle(mExtras);
    }
    public static final android.os.Parcelable.Creator<TextLinks> CREATOR =
            new android.os.Parcelable.Creator<TextLinks>() {
        @Override public TextLinks createFromParcel(android.os.Parcel in) {
            final String fullText = in.readString();
            List<TextLink> links = in.createTypedArrayList(TextLink.CREATOR);
            android.os.Bundle extras = in.readBundle();
            return new TextLinks(fullText,
                    links != null ? links : new ArrayList<TextLink>(),
                    extras != null ? extras : android.os.Bundle.EMPTY);
        }
        @Override public TextLinks[] newArray(int size) { return new TextLinks[size]; }
    };

    /** One detected entity range plus its confidence scores. */
    public static final class TextLink implements android.os.Parcelable {
        private final int mStart;
        private final int mEnd;
        private final List<String> mEntities;
        private final Map<String, Float> mEntityScores;
        private final android.os.Bundle mExtras;

        TextLink(int start, int end, Map<String, Float> entityScores,
                android.os.Bundle extras) {
            mStart = start;
            mEnd = end;
            mEntityScores = entityScores;
            mEntities = new ArrayList<String>(entityScores.keySet());
            // Descending confidence, as AOSP: getEntity(0) is the best guess.
            Collections.sort(mEntities, (a, b) ->
                    Float.compare(mEntityScores.get(b), mEntityScores.get(a)));
            mExtras = extras;
        }

        public int getStart() { return mStart; }
        public int getEnd() { return mEnd; }
        public int getEntityCount() { return mEntities.size(); }
        public String getEntity(int index) { return mEntities.get(index); }
        public float getConfidenceScore(String entityType) {
            Float f = mEntityScores.get(entityType);
            return f == null ? 0f : f.floatValue();
        }
        public android.os.Bundle getExtras() { return mExtras; }

        @Override public int describeContents() { return 0; }
        @Override public void writeToParcel(android.os.Parcel dest, int flags) {
            dest.writeInt(mStart);
            dest.writeInt(mEnd);
            dest.writeInt(mEntities.size());
            for (String e : mEntities) {
                dest.writeString(e);
                dest.writeFloat(getConfidenceScore(e));
            }
            dest.writeBundle(mExtras);
        }
        public static final android.os.Parcelable.Creator<TextLink> CREATOR =
                new android.os.Parcelable.Creator<TextLink>() {
            @Override public TextLink createFromParcel(android.os.Parcel in) {
                final int start = in.readInt();
                final int end = in.readInt();
                final int n = in.readInt();
                Map<String, Float> scores = new java.util.HashMap<String, Float>();
                for (int i = 0; i < n; i++) {
                    scores.put(in.readString(), Float.valueOf(in.readFloat()));
                }
                android.os.Bundle extras = in.readBundle();
                return new TextLink(start, end, scores,
                        extras != null ? extras : android.os.Bundle.EMPTY);
            }
            @Override public TextLink[] newArray(int size) { return new TextLink[size]; }
        };
    }

    /** The ClickableSpan the platform would install for a detected link. */
    public static class TextLinkSpan extends android.text.style.ClickableSpan {
        private final TextLink mTextLink;

        public TextLinkSpan(TextLink textLink) { mTextLink = textLink; }

        public final TextLink getTextLink() { return mTextLink; }

        @Override public void onClick(android.view.View widget) { }
    }

    public static final class Builder {
        private final String mFullText;
        private final List<TextLink> mLinks = new ArrayList<TextLink>();
        private android.os.Bundle mExtras = android.os.Bundle.EMPTY;

        public Builder(String fullText) {
            mFullText = java.util.Objects.requireNonNull(fullText);
        }

        public Builder addLink(int start, int end, Map<String, Float> entityScores) {
            return addLink(start, end, entityScores, android.os.Bundle.EMPTY);
        }

        /** AOSP Builder.addLink(int, int, Map, Bundle) (API 29). The range
         *  validation is AOSP's own. */
        public Builder addLink(int start, int end, Map<String, Float> entityScores,
                android.os.Bundle extras) {
            java.util.Objects.requireNonNull(entityScores);
            if (entityScores.isEmpty()) {
                throw new IllegalArgumentException("entityScores must not be empty");
            }
            if (start < 0 || end < start || end > mFullText.length()) {
                throw new IllegalArgumentException("Invalid range: " + start + ", " + end);
            }
            mLinks.add(new TextLink(start, end,
                    new java.util.HashMap<String, Float>(entityScores),
                    extras != null ? extras : android.os.Bundle.EMPTY));
            return this;
        }

        /** AOSP Builder.clearTextLinks() (API 29): drop the links added so far,
         *  keeping the text, so the builder can be reused. */
        public Builder clearTextLinks() { mLinks.clear(); return this; }

        /** AOSP Builder.setExtras(Bundle) (API 29). */
        public Builder setExtras(android.os.Bundle extras) {
            mExtras = extras != null ? extras : android.os.Bundle.EMPTY;
            return this;
        }

        public TextLinks build() {
            return new TextLinks(mFullText, new ArrayList<TextLink>(mLinks), mExtras);
        }
    }

    /** AOSP TextLinks.Request: the text to linkify plus the caller's options. */
    public static final class Request implements android.os.Parcelable {
        private final CharSequence mText;
        private android.os.LocaleList mLocales;
        private TextClassifier.EntityConfig mEntityConfig;
        private java.time.ZonedDateTime mReferenceTime;
        private android.os.Bundle mExtras = android.os.Bundle.EMPTY;
        private String mCallingPackageName;

        private Request(CharSequence text) { mText = text; }

        public CharSequence getText() { return mText; }
        public android.os.LocaleList getDefaultLocales() { return mLocales; }
        /** AOSP getEntityConfig(): which entity types to look for, null when the
         *  caller left it to the classifier. */
        public TextClassifier.EntityConfig getEntityConfig() { return mEntityConfig; }
        public java.time.ZonedDateTime getReferenceTime() { return mReferenceTime; }
        public android.os.Bundle getExtras() { return mExtras; }
        /** AOSP: stamped by the system on the way to the classifier service. */
        public String getCallingPackageName() { return mCallingPackageName; }

        @Override public int describeContents() { return 0; }
        @Override public void writeToParcel(android.os.Parcel dest, int flags) {
            dest.writeCharSequence(mText);
            dest.writeString(mLocales == null ? null : mLocales.toLanguageTags());
            dest.writeParcelable(mEntityConfig, flags);
            dest.writeString(mReferenceTime == null ? null : mReferenceTime.toString());
            dest.writeBundle(mExtras);
            dest.writeString(mCallingPackageName);
        }
        public static final android.os.Parcelable.Creator<Request> CREATOR =
                new android.os.Parcelable.Creator<Request>() {
            @Override public Request createFromParcel(android.os.Parcel in) {
                Request r = new Request(in.readCharSequence());
                String tags = in.readString();
                r.mLocales = tags == null ? null : android.os.LocaleList.forLanguageTags(tags);
                r.mEntityConfig = (TextClassifier.EntityConfig) in.readParcelable(
                        TextClassifier.EntityConfig.class.getClassLoader());
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
            private android.os.LocaleList mLocales;
            private TextClassifier.EntityConfig mEntityConfig;
            private java.time.ZonedDateTime mReferenceTime;
            private android.os.Bundle mExtras = android.os.Bundle.EMPTY;

            public Builder(CharSequence text) {
                mText = java.util.Objects.requireNonNull(text);
            }
            public Builder setDefaultLocales(android.os.LocaleList locales) {
                mLocales = locales; return this;
            }
            public Builder setEntityConfig(TextClassifier.EntityConfig entityConfig) {
                mEntityConfig = entityConfig; return this;
            }
            public Builder setReferenceTime(java.time.ZonedDateTime referenceTime) {
                mReferenceTime = referenceTime; return this;
            }
            public Builder setExtras(android.os.Bundle bundle) {
                mExtras = bundle != null ? bundle : android.os.Bundle.EMPTY; return this;
            }
            public Request build() {
                Request r = new Request(mText);
                r.mLocales = mLocales;
                r.mEntityConfig = mEntityConfig;
                r.mReferenceTime = mReferenceTime;
                r.mExtras = mExtras;
                return r;
            }
        }
    }
}
