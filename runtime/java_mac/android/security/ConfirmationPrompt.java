// android.security.ConfirmationPrompt -- Android Protected Confirmation: a
// prompt drawn by SECURE HARDWARE (a trusted display path outside the reach of
// the OS) whose "yes" is signed by KeyMint, so a relying party can verify that
// a human approved a specific message on a specific device.
//
// AOSP source: frameworks/base/core/java/android/security/ConfirmationPrompt.java
// (android16-release). Public surface mirrored exactly: Builder(Context),
// setPromptText, setExtraData, build, presentPrompt(Executor, callback),
// cancelPrompt, and the static isSupported(Context).
//
// ⚠ MAC.APK ANSWER, AND IT IS AN HONEST REFUSAL, NOT A STUB:
//   isSupported()  -> false
//   presentPrompt() -> throws ConfirmationNotAvailableException
// That is exactly what AOSP does on a device whose KeyMint reports the feature
// unimplemented (ConfirmationPrompt.java:288-290 maps ERROR_UNIMPLEMENTED to
// this exception), and the majority of real Android devices are in that state.
// macOS has no trusted display path, so a prompt we drew ourselves could not
// produce the signed confirmation token that gives this API its entire value --
// showing a normal window and calling onConfirmed would be forging a hardware
// attestation of user presence. We do not do that. Apps are documented to
// gate on isSupported() and fall back to an ordinary in-app confirmation.
package android.security;

import android.content.Context;
import java.util.concurrent.Executor;

public class ConfirmationPrompt {

    private final CharSequence mPromptText;
    private final byte[] mExtraData;

    private ConfirmationPrompt(CharSequence promptText, byte[] extraData) {
        mPromptText = promptText;
        mExtraData = extraData;
    }

    /** Always throws: there is no trusted display path on macOS. See header. */
    public void presentPrompt(Executor executor, ConfirmationCallback callback)
            throws ConfirmationAlreadyPresentingException, ConfirmationNotAvailableException {
        if (executor == null) throw new NullPointerException("executor == null");
        if (callback == null) throw new NullPointerException("callback == null");
        throw new ConfirmationNotAvailableException();
    }

    /** AOSP throws IllegalStateException when no prompt is presenting. One never
     *  is here, because presentPrompt cannot succeed. */
    public void cancelPrompt() {
        throw new IllegalStateException("No prompt is currently being presented");
    }

    /** Whether the device supports Android Protected Confirmation. False on
     *  macOS -- and false on most Android hardware too. */
    public static boolean isSupported(Context context) { return false; }

    public static final class Builder {
        private final Context mContext;
        private CharSequence mPromptText;
        private byte[] mExtraData;

        public Builder(Context context) { mContext = context; }

        public Builder setPromptText(CharSequence promptText) {
            mPromptText = promptText; return this;
        }

        public Builder setExtraData(byte[] extraData) { mExtraData = extraData; return this; }

        public ConfirmationPrompt build() {
            return new ConfirmationPrompt(mPromptText, mExtraData);
        }
    }
}
