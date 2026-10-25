// android.app.RecoverableSecurityException -- a SecurityException subtype
// carrying a user-facing recovery action. AOSP API-29+; MediaStore.delete()/
// update() throws it on scoped storage when the caller doesn't own the
// target file, and the catcher is expected to launch
// getUserAction().getActionIntent() via startIntentSenderForResult() to ask
// the user for permission, then retry.
//
// Entirely missing before this fix -- caught by debug/link_audit.sh --all
// against the corpus (2026-07-30): "MISSING android.app.RecoverableSecurityException
// (1 dependents) <- n5.m" for org.fossify.gallery_28.apk (an R8-obfuscated
// MediaStore-delete helper; Fossify Gallery is a real gallery/file-manager app
// that deletes files the user selects, which is exactly this API's use case).
//
// AOSP ref: frameworks/base/core/java/android/app/RecoverableSecurityException.java
// (android13-release), copied field-for-field and method-for-method for the
// PUBLIC surface. showAsNotification(Context,String) / showAsDialog(Activity)
// / the nested LocalDialog are deliberately NOT mirrored: AOSP marks all
// three @hide (system/privileged callers only), so no APK compiled against
// the public SDK can reference them by name -- the app-visible surface is
// exactly what is implemented below, and adding the hidden convenience
// methods would only pull in untested Notification.Builder/AlertDialog.Builder
// chains with no real caller.
package android.app;

import android.os.Parcel;

import java.util.Objects;

public final class RecoverableSecurityException extends SecurityException
        implements android.os.Parcelable {
    private final CharSequence mUserMessage;
    private final RemoteAction mUserAction;

    /**
     * @param cause original cause with details designed for engineering audiences.
     * @param userMessage short message describing the issue for end user audiences.
     * @param userAction primary action that will initiate the recovery; must launch
     *        an activity that sets Activity.setResult(int) before finishing.
     */
    public RecoverableSecurityException(Throwable cause, CharSequence userMessage,
            RemoteAction userAction) {
        super(cause.getMessage());
        mUserMessage = Objects.requireNonNull(userMessage);
        mUserAction = Objects.requireNonNull(userAction);
    }

    public CharSequence getUserMessage() { return mUserMessage; }
    public RemoteAction getUserAction() { return mUserAction; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(getMessage());
        dest.writeCharSequence(mUserMessage);
        mUserAction.writeToParcel(dest, flags);
    }

    // AOSP {@hide}: not SDK-visible, but a real (public, non-@hide) bytecode
    // member of the shipped platform class -- kept public to match, though no
    // compiled APK can name it directly either way.
    public RecoverableSecurityException(Parcel in) {
        this(new SecurityException(in.readString()), in.readCharSequence(),
                RemoteAction.CREATOR.createFromParcel(in));
    }

    public static final Creator<RecoverableSecurityException> CREATOR =
            new Creator<RecoverableSecurityException>() {
        @Override public RecoverableSecurityException createFromParcel(Parcel source) {
            return new RecoverableSecurityException(source);
        }
        @Override public RecoverableSecurityException[] newArray(int size) {
            return new RecoverableSecurityException[size];
        }
    };
}
