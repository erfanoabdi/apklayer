// android.view.inputmethod.ExtractedTextRequest -- AOSP request shape passed
// to InputConnection.getExtractedText describing how much text the IME wants
// and how to be notified of changes. AOSP:
//   frameworks/base/core/java/android/view/inputmethod/ExtractedTextRequest.java
//   public class ExtractedTextRequest implements Parcelable
// Public fields mirror AOSP; callers construct it and read/write the fields
// directly.
package android.view.inputmethod;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

public class ExtractedTextRequest implements Parcelable {
    public int token;
    public Bundle extras;
    public int flags;
    public int hintMaxLines;
    public int hintMaxChars;

    public ExtractedTextRequest() {}

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) {
        // In-process InputConnection: no IPC marshaling needed. No-op.
    }

    public static final Parcelable.Creator<ExtractedTextRequest> CREATOR =
        new Parcelable.Creator<ExtractedTextRequest>() {
            @Override public ExtractedTextRequest createFromParcel(Parcel in) {
                return new ExtractedTextRequest();
            }
            @Override public ExtractedTextRequest[] newArray(int size) {
                return new ExtractedTextRequest[size];
            }
        };
}
