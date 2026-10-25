// android.hardware.biometrics.PromptContentItemBulletedText -- one bulleted row
// of a BiometricPrompt vertical-list content view.
//
// Mirrors frameworks/base/core/java/android/hardware/biometrics/PromptContentItemBulletedText.java
// (android16-release). A pure value carrier, faithfully implemented.
package android.hardware.biometrics;

public final class PromptContentItemBulletedText
        implements android.os.Parcelable, PromptContentItem {

    private final String mText;

    public PromptContentItemBulletedText(String text) {
        mText = text;
    }

    /* package */ String getText() { return mText; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeString(mText);
    }

    public static final Creator<PromptContentItemBulletedText> CREATOR =
            new Creator<PromptContentItemBulletedText>() {
        @Override public PromptContentItemBulletedText createFromParcel(android.os.Parcel in) {
            return new PromptContentItemBulletedText(in.readString());
        }
        @Override public PromptContentItemBulletedText[] newArray(int size) {
            return new PromptContentItemBulletedText[size];
        }
    };
}
