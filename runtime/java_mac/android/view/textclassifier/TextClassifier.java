// android.view.textclassifier.TextClassifier -- AOSP declares this an
// INTERFACE, and that KIND is load-bearing.
//
// WHY (found 2026-07-29, same audit as ViewManager / ScrollCaptureCallback /
// ViewTranslationCallback): we shipped it as a `public class` (v1.0.92
// auto-generated stub, later given real method bodies), so a guest class that
// correctly declares `implements TextClassifier` -- the normal way an app or a
// library supplies its own classifier via TextView.setTextClassifier -- fails to
// link with IncompatibleClassChangeError.
//
// AOSP (frameworks/base/core/java/android/view/textclassifier/TextClassifier.java)
// declares EVERY method `default`, with a NO_OP constant implemented as an
// anonymous no-override instance:
//   TextClassifier NO_OP = new TextClassifier() {
//       @Override public String toString() { return "TextClassifier.NO_OP"; }
//   };
// That shape is why the interface conversion is behaviour-preserving here: the
// bodies we already had become interface defaults verbatim, and every caller
// that used to hold a `new TextClassifier()` now holds NO_OP, which AOSP
// documents as returning default/empty results for every request -- exactly
// Mac.apk's situation (no on-device model).
package android.view.textclassifier;

/** Interface for providing text classification related features. */
public interface TextClassifier {
    // ---- entity types the classifier can report (AOSP TYPE_*) ---------------
    String TYPE_UNKNOWN = "";
    String TYPE_OTHER = "other";
    String TYPE_EMAIL = "email";
    String TYPE_PHONE = "phone";
    String TYPE_ADDRESS = "address";
    String TYPE_URL = "url";
    String TYPE_DATE = "date";
    String TYPE_DATE_TIME = "datetime";
    String TYPE_FLIGHT_NUMBER = "flight";
    String TYPE_OTP = "otp";

    // ---- hints and widget types (AOSP HINT_* / WIDGET_TYPE_*) ---------------
    String HINT_TEXT_IS_EDITABLE = "android.text_is_editable";
    String HINT_TEXT_IS_NOT_EDITABLE = "android.text_is_not_editable";

    String WIDGET_TYPE_TEXTVIEW = "textview";
    String WIDGET_TYPE_EDITTEXT = "edittext";
    String WIDGET_TYPE_UNSELECTABLE_TEXTVIEW = "nosel-textview";
    String WIDGET_TYPE_WEBVIEW = "webview";
    String WIDGET_TYPE_EDIT_WEBVIEW = "edit-webview";
    String WIDGET_TYPE_CUSTOM_TEXTVIEW = "customview";
    String WIDGET_TYPE_CUSTOM_EDITTEXT = "customedit";
    String WIDGET_TYPE_CUSTOM_UNSELECTABLE_TEXTVIEW = "nosel-customview";
    String WIDGET_TYPE_NOTIFICATION = "notification";
    String WIDGET_TYPE_CLIPBOARD = "clipboard";
    String WIDGET_TYPE_UNKNOWN = "unknown";

    String EXTRA_FROM_TEXT_CLASSIFIER = "android.view.textclassifier.extra.FROM_TEXT_CLASSIFIER";
    String EXTRA_TEXT_ORIGIN_PACKAGE = "android.view.textclassifier.extra.TEXT_ORIGIN_PACKAGE";

    /** AOSP TextClassifier.NO_OP (API 26): the classifier used when no
     *  on-device model is available. Interface fields are implicitly
     *  public static final, matching AOSP's declaration. */
    TextClassifier NO_OP = new TextClassifier() {
        @Override public String toString() { return "TextClassifier.NO_OP"; }
    };

    default TextSelection suggestSelection(TextSelection.Request request) {
        int s = request != null ? request.getStartIndex() : 0;
        int e = request != null ? request.getEndIndex() : 0;
        return new TextSelection.Builder(s, e).build();
    }

    /** AOSP suggestSelection(CharSequence, int, int, LocaleList): the
     *  convenience overload, which AOSP implements by building a Request and
     *  delegating -- reproduced, so a classifier that overrides only the Request
     *  form still serves both. */
    default TextSelection suggestSelection(CharSequence text, int selectionStartIndex,
            int selectionEndIndex, android.os.LocaleList defaultLocales) {
        return suggestSelection(new TextSelection.Request
                .Builder(text, selectionStartIndex, selectionEndIndex)
                .setDefaultLocales(defaultLocales)
                .build());
    }

    default TextClassification classifyText(TextClassification.Request request) {
        return new TextClassification.Builder().build();
    }

    /** AOSP classifyText(CharSequence, int, int, LocaleList): same delegation. */
    default TextClassification classifyText(CharSequence text, int startIndex, int endIndex,
            android.os.LocaleList defaultLocales) {
        return classifyText(new TextClassification.Request.Builder(text, startIndex, endIndex)
                .setDefaultLocales(defaultLocales)
                .build());
    }

    /** AOSP generateLinks(TextLinks.Request): AOSP's default returns an EMPTY
     *  TextLinks over the request's own text -- so Linkify falls back to its own
     *  regex patterns, which still work. That is the documented no-classifier
     *  behaviour, not a stand-in. */
    default TextLinks generateLinks(TextLinks.Request request) {
        java.util.Objects.requireNonNull(request);
        return new TextLinks.Builder(request.getText().toString()).build();
    }

    /** AOSP getMaxGenerateLinksTextLength(): the longest text generateLinks will
     *  accept. AOSP's default is Integer.MAX_VALUE for a classifier with no
     *  model-imposed limit. */
    default int getMaxGenerateLinksTextLength() { return Integer.MAX_VALUE; }

    /** AOSP detectLanguage(TextLanguage.Request): AOSP's default returns
     *  TextLanguage.EMPTY -- no hypothesis at all -- which is what a classifier
     *  with no language model reports. Referenced by Mastodon. */
    default TextLanguage detectLanguage(TextLanguage.Request request) {
        java.util.Objects.requireNonNull(request);
        return TextLanguage.EMPTY;
    }

    /** AOSP suggestConversationActions(ConversationActions.Request): AOSP's
     *  default returns an EMPTY action list. */
    default ConversationActions suggestConversationActions(ConversationActions.Request request) {
        java.util.Objects.requireNonNull(request);
        return new ConversationActions(java.util.Collections.<ConversationAction>emptyList(), null);
    }

    /** AOSP onSelectionEvent(SelectionEvent) / onTextClassifierEvent(
     *  TextClassifierEvent): telemetry the widget reports so the model can
     *  learn. There is no model, so AOSP's own empty default applies. */
    default void onSelectionEvent(SelectionEvent event) { }
    default void onTextClassifierEvent(TextClassifierEvent event) { }

    default boolean isDestroyed() { return false; }

    default void destroy() { }

    /** AOSP TextClassifier.EntityConfig: which entity types a request wants the
     *  classifier to look for. */
    final class EntityConfig implements android.os.Parcelable {
        private final java.util.Collection<String> mHints;
        private final java.util.Collection<String> mExcludedTypes;
        private final java.util.Collection<String> mIncludedTypes;
        private final boolean mIncludeTypesFromTextClassifier;

        private EntityConfig(java.util.Collection<String> hints,
                java.util.Collection<String> includedTypes,
                java.util.Collection<String> excludedTypes,
                boolean includeTypesFromTextClassifier) {
            mHints = hints;
            mIncludedTypes = includedTypes;
            mExcludedTypes = excludedTypes;
            mIncludeTypesFromTextClassifier = includeTypesFromTextClassifier;
        }

        /** AOSP EntityConfig.create(hints, includedTypes, excludedTypes),
         *  deprecated at API 29 in favour of the Builder. */
        @Deprecated
        public static EntityConfig create(java.util.Collection<String> hints,
                java.util.Collection<String> includedEntityTypes,
                java.util.Collection<String> excludedEntityTypes) {
            return new Builder()
                    .setHints(hints)
                    .setIncludedTypes(includedEntityTypes)
                    .setExcludedTypes(excludedEntityTypes)
                    .build();
        }
        /** AOSP EntityConfig.createWithExplicitEntityList(entityTypes),
         *  deprecated at API 29: use ONLY these types, ignoring the
         *  classifier's own. */
        @Deprecated
        public static EntityConfig createWithExplicitEntityList(
                java.util.Collection<String> entityTypes) {
            return new Builder()
                    .setIncludedTypes(entityTypes)
                    .includeTypesFromTextClassifier(false)
                    .build();
        }
        /** AOSP EntityConfig.createWithHints(hints), deprecated at API 29. */
        @Deprecated
        public static EntityConfig createWithHints(java.util.Collection<String> hints) {
            return new Builder().setHints(hints).includeTypesFromTextClassifier(true).build();
        }

        public java.util.Collection<String> getHints() { return mHints; }
        public boolean shouldIncludeTypesFromTextClassifier() {
            return mIncludeTypesFromTextClassifier;
        }

        /** AOSP EntityConfig.resolveEntityListModifications(Collection): the
         *  classifier's own list, plus the included types, minus the excluded
         *  ones -- and only the included ones when the classifier's list was
         *  opted out. Verbatim from AOSP. */
        public java.util.Collection<String> resolveEntityListModifications(
                java.util.Collection<String> entityTypes) {
            final java.util.Set<String> finalSet = new java.util.HashSet<String>();
            if (mIncludeTypesFromTextClassifier) finalSet.addAll(entityTypes);
            finalSet.addAll(mIncludedTypes);
            finalSet.removeAll(mExcludedTypes);
            return finalSet;
        }

        @Override public int describeContents() { return 0; }
        @Override public void writeToParcel(android.os.Parcel dest, int flags) {
            dest.writeStringList(new java.util.ArrayList<String>(mHints));
            dest.writeStringList(new java.util.ArrayList<String>(mExcludedTypes));
            dest.writeStringList(new java.util.ArrayList<String>(mIncludedTypes));
            dest.writeBoolean(mIncludeTypesFromTextClassifier);
        }
        public static final android.os.Parcelable.Creator<EntityConfig> CREATOR =
                new android.os.Parcelable.Creator<EntityConfig>() {
            @Override public EntityConfig createFromParcel(android.os.Parcel in) {
                java.util.List<String> hints = in.createStringArrayList();
                java.util.List<String> excluded = in.createStringArrayList();
                java.util.List<String> included = in.createStringArrayList();
                boolean fromClassifier = in.readBoolean();
                return new EntityConfig(hints, included, excluded, fromClassifier);
            }
            @Override public EntityConfig[] newArray(int size) { return new EntityConfig[size]; }
        };

        public static final class Builder {
            private java.util.Collection<String> mHints = java.util.Collections.emptyList();
            private java.util.Collection<String> mExcludedTypes =
                    java.util.Collections.emptyList();
            private java.util.Collection<String> mIncludedTypes =
                    java.util.Collections.emptyList();
            private boolean mIncludeTypesFromTextClassifier = true;

            public Builder() {}
            public Builder setHints(java.util.Collection<String> hints) {
                mHints = hints != null ? hints : java.util.Collections.<String>emptyList();
                return this;
            }
            public Builder setIncludedTypes(java.util.Collection<String> includedTypes) {
                mIncludedTypes = includedTypes != null
                        ? includedTypes : java.util.Collections.<String>emptyList();
                return this;
            }
            public Builder setExcludedTypes(java.util.Collection<String> excludedTypes) {
                mExcludedTypes = excludedTypes != null
                        ? excludedTypes : java.util.Collections.<String>emptyList();
                return this;
            }
            public Builder includeTypesFromTextClassifier(boolean include) {
                mIncludeTypesFromTextClassifier = include;
                return this;
            }
            public EntityConfig build() {
                return new EntityConfig(mHints, mIncludedTypes, mExcludedTypes,
                        mIncludeTypesFromTextClassifier);
            }
        }
    }
}
