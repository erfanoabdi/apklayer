// android.hardware.biometrics.PromptContentViewWithMoreOptionsButton -- a
// BiometricPrompt content area that shows a description plus a "more options"
// button the app handles itself.
//
// Mirrors frameworks/base/core/java/android/hardware/biometrics/
// PromptContentViewWithMoreOptionsButton.java (android16-release).
//
// The button listener is stored and never invoked here: no prompt is ever
// shown (BiometricPrompt.authenticate reports BIOMETRIC_ERROR_HW_NOT_PRESENT),
// so there is no button for the user to press. The getter still returns what
// the app supplied, which is what AOSP's getMoreOptionsButtonListener does.
package android.hardware.biometrics;

import android.content.DialogInterface;

import java.util.Objects;
import java.util.concurrent.Executor;

public final class PromptContentViewWithMoreOptionsButton
        implements android.os.Parcelable, PromptContentView {

    static final int MAX_DESCRIPTION_CHARACTER_NUMBER = 225;
    private static final String TAG = "PromptContentViewWithMoreOptionsButton";

    private final String mDescription;
    private final DialogInterface.OnClickListener mListener;

    private PromptContentViewWithMoreOptionsButton(String description,
            DialogInterface.OnClickListener listener) {
        mDescription = description;
        mListener = listener;
    }

    public String getDescription() { return mDescription; }

    public DialogInterface.OnClickListener getMoreOptionsButtonListener() { return mListener; }

    @Override public int describeContents() { return 0; }

    /** The listener is a live callback, so it does not survive parcelling --
     *  AOSP marshals an IBinder token for it that only the system UI can use.
     *  Only the description crosses. */
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeString(mDescription);
    }

    public static final Creator<PromptContentViewWithMoreOptionsButton> CREATOR =
            new Creator<PromptContentViewWithMoreOptionsButton>() {
        @Override public PromptContentViewWithMoreOptionsButton createFromParcel(
                android.os.Parcel in) {
            return new PromptContentViewWithMoreOptionsButton(in.readString(), null);
        }
        @Override public PromptContentViewWithMoreOptionsButton[] newArray(int size) {
            return new PromptContentViewWithMoreOptionsButton[size];
        }
    };

    public static final class Builder {
        private String mDescription;
        private DialogInterface.OnClickListener mListener;

        public Builder() {}

        public Builder setDescription(String description) {
            if (description.length() > MAX_DESCRIPTION_CHARACTER_NUMBER) {
                // Logged, not thrown -- same as PromptVerticalListContentView.
                System.err.println("[" + TAG + "] The character number of description exceeds "
                        + MAX_DESCRIPTION_CHARACTER_NUMBER);
            }
            mDescription = description;
            return this;
        }

        public Builder setMoreOptionsButtonListener(Executor executor,
                DialogInterface.OnClickListener listener) {
            Objects.requireNonNull(executor);
            mListener = Objects.requireNonNull(listener);
            return this;
        }

        public PromptContentViewWithMoreOptionsButton build() {
            return new PromptContentViewWithMoreOptionsButton(mDescription, mListener);
        }
    }
}
