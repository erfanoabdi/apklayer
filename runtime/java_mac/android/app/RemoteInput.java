// android.app.RemoteInput -- a request for free-form / choice input
// attached to a Notification.Action (inline reply). AOSP API-20+.
//
// AOSP ref: frameworks/base/core/java/android/app/RemoteInput.java.
// Built via RemoteInput.Builder; the system populates the results
// Bundle into the action's PendingIntent when the user replies, read
// back via getResultsFromIntent. Mac.apk doesn't render inline-reply
// UI, so no results are ever produced, but the full API resolves.
package android.app;

import android.content.Intent;
import android.os.Bundle;

public final class RemoteInput implements android.os.Parcelable {
    public static final String RESULTS_CLIP_LABEL = "android.remoteinput.results";
    public static final String EXTRA_RESULTS_DATA = "android.remoteinput.resultsData";

    public static final int EDIT_CHOICES_BEFORE_SENDING_AUTO     = 0;
    public static final int EDIT_CHOICES_BEFORE_SENDING_DISABLED = 1;
    public static final int EDIT_CHOICES_BEFORE_SENDING_ENABLED  = 2;

    public static final int SOURCE_FREE_FORM_INPUT = 0;
    public static final int SOURCE_CHOICE          = 1;

    private final String mResultKey;
    private final CharSequence mLabel;
    private final CharSequence[] mChoices;
    private final boolean mAllowFreeFormInput;
    private final int mEditChoicesBeforeSending;
    private final Bundle mExtras;

    // ---- Parcelable (v1.0.943) -------------------------------------------
    // AOSP's RemoteInput is Parcelable (core/java/android/app/RemoteInput.java).
    // Ours was not, which is why Notification.Action.mRemoteInputs could only
    // write a count and read back null -- inline reply could not survive a
    // parcel round trip at all.
    //
    // AOSP's field order: resultKey, label, choices, flags, extras (+ allowed
    // data types and editChoicesBeforeSending on later API levels). We marshal
    // the six fields this tree declares, in that relative order.
    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel out, int flags) {
        out.writeString(mResultKey);
        android.text.TextUtils.writeToParcel(mLabel, out, flags);
        if (mChoices == null) {
            out.writeInt(-1);
        } else {
            out.writeInt(mChoices.length);
            for (CharSequence c : mChoices) {
                android.text.TextUtils.writeToParcel(c, out, flags);
            }
        }
        out.writeInt(mAllowFreeFormInput ? 1 : 0);
        out.writeInt(mEditChoicesBeforeSending);
        out.writeBundle(mExtras);
    }

    public static final android.os.Parcelable.Creator<RemoteInput> CREATOR =
            new android.os.Parcelable.Creator<RemoteInput>() {
        @Override public RemoteInput createFromParcel(android.os.Parcel in) {
            Builder b = new Builder(in.readString());
            b.setLabel(android.text.TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in));
            int n = in.readInt();
            if (n >= 0) {
                CharSequence[] cs = new CharSequence[n];
                for (int i = 0; i < n; i++) {
                    cs[i] = android.text.TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
                }
                b.setChoices(cs);
            }
            b.setAllowFreeFormInput(in.readInt() != 0);
            b.setEditChoicesBeforeSending(in.readInt());
            android.os.Bundle x = in.readBundle();
            if (x != null) b.addExtras(x);
            return b.build();
        }
        @Override public RemoteInput[] newArray(int size) { return new RemoteInput[size]; }
    };

    private RemoteInput(Builder b) {
        mResultKey = b.mResultKey;
        mLabel = b.mLabel;
        mChoices = b.mChoices;
        mAllowFreeFormInput = b.mAllowFreeFormInput;
        mEditChoicesBeforeSending = b.mEditChoicesBeforeSending;
        mExtras = b.mExtras;
    }

    public String getResultKey() { return mResultKey; }
    public CharSequence getLabel() { return mLabel; }
    public CharSequence[] getChoices() { return mChoices; }
    public boolean getAllowFreeFormInput() { return mAllowFreeFormInput; }
    public int getEditChoicesBeforeSending() { return mEditChoicesBeforeSending; }
    public Bundle getExtras() { return mExtras; }
    public boolean isDataOnly() { return !mAllowFreeFormInput && (mChoices == null || mChoices.length == 0); }

    /** AOSP: pulls the per-key reply text the system attached to the
     *  Intent. Mac.apk never produces inline replies, so this is
     *  null unless an app round-tripped results via addResultsToIntent. */
    public static Bundle getResultsFromIntent(Intent intent) {
        if (intent == null) return null;
        Bundle ex = intent.getExtras();
        Object data = ex != null ? ex.get(EXTRA_RESULTS_DATA) : null;
        return data instanceof Bundle ? (Bundle) data : null;
    }

    public static void addResultsToIntent(RemoteInput[] remoteInputs, Intent intent, Bundle results) {
        if (intent == null || results == null) return;
        Bundle existing = getResultsFromIntent(intent);
        Bundle merged = existing != null ? existing : new Bundle();
        merged.putAll(results);
        Bundle holder = new Bundle();
        holder.putBundle(EXTRA_RESULTS_DATA, merged);
        intent.putExtras(holder);
    }

    public static java.util.Map<String, Object> getDataResultsFromIntent(Intent intent, String remoteInputResultKey) {
        return new java.util.HashMap<>();
    }
    public static void addDataResultToIntent(RemoteInput remoteInput, Intent intent, java.util.Map<String, android.net.Uri> results) {}
    public static int getResultsSource(Intent intent) { return SOURCE_FREE_FORM_INPUT; }
    public static void setResultsSource(Intent intent, int source) {}

    public static final class Builder {
        private final String mResultKey;
        private CharSequence mLabel;
        private CharSequence[] mChoices;
        private boolean mAllowFreeFormInput = true;
        private int mEditChoicesBeforeSending = EDIT_CHOICES_BEFORE_SENDING_AUTO;
        private final Bundle mExtras = new Bundle();
        public Builder(String resultKey) {
            if (resultKey == null) throw new IllegalArgumentException("Result key can't be null");
            mResultKey = resultKey;
        }
        public Builder setLabel(CharSequence label) { mLabel = label; return this; }
        public Builder setAllowDataType(String mimeType, boolean allow) { return this; }
        public Builder setChoices(CharSequence[] choices) { mChoices = choices; return this; }
        public Builder setAllowFreeFormInput(boolean allowFreeFormInput) { mAllowFreeFormInput = allowFreeFormInput; return this; }
        public Builder setEditChoicesBeforeSending(int editChoicesBeforeSending) { mEditChoicesBeforeSending = editChoicesBeforeSending; return this; }
        public Builder addExtras(Bundle extras) { if (extras != null) mExtras.putAll(extras); return this; }
        public Bundle getExtras() { return mExtras; }
        public RemoteInput build() { return new RemoteInput(this); }
    }

    /** AOSP getAllowedDataTypes (API 26): MIME types this input accepts besides
     *  free-form text. Default: none (text-only). */
    public java.util.Set<String> getAllowedDataTypes() { return java.util.Collections.emptySet(); }
}

