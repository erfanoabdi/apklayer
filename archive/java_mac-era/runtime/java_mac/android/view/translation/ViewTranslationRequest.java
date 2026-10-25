package android.view.translation;
import java.util.HashMap;
import java.util.Map;
/** A request to translate a view's text (API 31). No on-device translator, so
 *  nothing translates, but the request value object round-trips.
 *  Source: frameworks/base .../view/translation/ViewTranslationRequest.java */
public final class ViewTranslationRequest implements android.os.Parcelable {
    public static final String ID_TEXT = "android:text";
    private final android.view.autofill.AutofillId mAutofillId;
    private final long mVirtualChildId;
    private final Map<String, TranslationRequestValue> mValues;
    private ViewTranslationRequest(Builder b) {
        mAutofillId = b.mAutofillId; mVirtualChildId = b.mVirtualChildId; mValues = b.mValues;
    }
    public android.view.autofill.AutofillId getAutofillId() { return mAutofillId; }
    public int getKeyCount() { return mValues.size(); }
    public java.util.Set<String> getKeys() { return mValues.keySet(); }
    public TranslationRequestValue getValue(String key) { return mValues.get(key); }
    public static final class Builder {
        private final android.view.autofill.AutofillId mAutofillId;
        private long mVirtualChildId = -1;
        private final Map<String, TranslationRequestValue> mValues = new HashMap<>();
        public Builder(android.view.autofill.AutofillId autofillId) { mAutofillId = autofillId; }
        public Builder(android.view.autofill.AutofillId autofillId, long virtualChildId) {
            mAutofillId = autofillId; mVirtualChildId = virtualChildId;
        }
        public Builder setValue(String key, TranslationRequestValue value) {
            mValues.put(key, value); return this;
        }
        public ViewTranslationRequest build() { return new ViewTranslationRequest(this); }
    }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeParcelable(mAutofillId, flags);
        dest.writeLong(mVirtualChildId);
        dest.writeInt(mValues.size());
        for (Map.Entry<String, TranslationRequestValue> e : mValues.entrySet()) {
            dest.writeString(e.getKey());
            dest.writeParcelable(e.getValue(), flags);
        }
    }
    public static final android.os.Parcelable.Creator<ViewTranslationRequest> CREATOR =
            new android.os.Parcelable.Creator<ViewTranslationRequest>() {
        @Override public ViewTranslationRequest createFromParcel(android.os.Parcel source) {
            android.view.autofill.AutofillId id = source.readParcelable(
                    android.view.autofill.AutofillId.class.getClassLoader());
            long virtualChildId = source.readLong();
            Builder b = new Builder(id, virtualChildId);
            final int n = source.readInt();
            ClassLoader cl = TranslationRequestValue.class.getClassLoader();
            for (int i = 0; i < n; i++) {
                b.setValue(source.readString(),
                        (TranslationRequestValue) source.readParcelable(cl));
            }
            return b.build();
        }
        @Override public ViewTranslationRequest[] newArray(int size) {
            return new ViewTranslationRequest[size];
        }
    };
}
