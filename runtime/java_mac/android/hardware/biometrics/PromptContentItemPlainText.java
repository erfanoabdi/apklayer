// android.hardware.biometrics.PromptContentItemPlainText -- one plain-text row
// of a BiometricPrompt vertical-list content view.
//
// Mirrors frameworks/base/core/java/android/hardware/biometrics/PromptContentItemPlainText.java
// (android16-release). A pure value carrier, faithfully implemented.
package android.hardware.biometrics;

public final class PromptContentItemPlainText
        implements android.os.Parcelable, PromptContentItem {

    private final String mText;

    public PromptContentItemPlainText(String text) {
        mText = text;
    }

    /** Package-private in AOSP; PromptVerticalListContentView.Builder needs the
     *  text to enforce its per-item character limit. */
    /* package */ String getText() { return mText; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeString(mText);
    }

    public static final Creator<PromptContentItemPlainText> CREATOR =
            new Creator<PromptContentItemPlainText>() {
        @Override public PromptContentItemPlainText createFromParcel(android.os.Parcel in) {
            return new PromptContentItemPlainText(in.readString());
        }
        @Override public PromptContentItemPlainText[] newArray(int size) {
            return new PromptContentItemPlainText[size];
        }
    };
}
