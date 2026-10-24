package android.view.translation;
import java.util.HashMap;
import java.util.Map;
/** The per-view translation result keyed by field id. Empty on Mac.apk (no
 *  translation service, so nothing ever fills one in), but an app that builds
 *  one with the Builder reads back exactly what it set.
 *  Source: frameworks/base .../view/translation/ViewTranslationResponse.java
 *  (android16-release). AOSP declares `implements Parcelable`. */
public final class ViewTranslationResponse implements android.os.Parcelable {
    private final android.view.autofill.AutofillId mAutofillId;
    private final Map<String, TranslationResponseValue> mValues;

    private ViewTranslationResponse(android.view.autofill.AutofillId autofillId,
            Map<String, TranslationResponseValue> values) {
        mAutofillId = autofillId;
        mValues = values;
    }

    /** @hide -- kept because this tree already shipped a public no-arg form. */
    public ViewTranslationResponse() {
        this(null, new HashMap<String, TranslationResponseValue>());
    }

    public android.view.autofill.AutofillId getAutofillId() { return mAutofillId; }
    /** AOSP: @NonNull -- an unknown key yields an error value, not null. */
    public TranslationResponseValue getValue(String key) {
        TranslationResponseValue v = mValues.get(key);
        return v != null ? v : TranslationResponseValue.forError();
    }
    public java.util.Set<String> getKeys() { return mValues.keySet(); }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeParcelable(mAutofillId, flags);
        dest.writeInt(mValues.size());
        for (Map.Entry<String, TranslationResponseValue> e : mValues.entrySet()) {
            dest.writeString(e.getKey());
            dest.writeParcelable(e.getValue(), flags);
        }
    }
    public static final android.os.Parcelable.Creator<ViewTranslationResponse> CREATOR =
            new android.os.Parcelable.Creator<ViewTranslationResponse>() {
        @Override public ViewTranslationResponse createFromParcel(android.os.Parcel source) {
            android.view.autofill.AutofillId id = source.readParcelable(
                    android.view.autofill.AutofillId.class.getClassLoader());
            Builder b = new Builder(id);
            final int n = source.readInt();
            ClassLoader cl = TranslationResponseValue.class.getClassLoader();
            for (int i = 0; i < n; i++) {
                b.setValue(source.readString(),
                        (TranslationResponseValue) source.readParcelable(cl));
            }
            return b.build();
        }
        @Override public ViewTranslationResponse[] newArray(int size) {
            return new ViewTranslationResponse[size];
        }
    };

    /** AOSP ViewTranslationResponse.Builder(AutofillId). */
    public static final class Builder {
        private final android.view.autofill.AutofillId mAutofillId;
        private final Map<String, TranslationResponseValue> mValues =
                new HashMap<String, TranslationResponseValue>();
        public Builder(android.view.autofill.AutofillId autofillId) {
            mAutofillId = java.util.Objects.requireNonNull(autofillId);
        }
        public Builder setValue(String key, TranslationResponseValue value) {
            mValues.put(key, value);
            return this;
        }
        public ViewTranslationResponse build() {
            return new ViewTranslationResponse(mAutofillId, mValues);
        }
    }
}
