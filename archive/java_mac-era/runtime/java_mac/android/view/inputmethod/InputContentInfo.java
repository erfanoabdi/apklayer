package android.view.inputmethod;

import android.content.ClipDescription;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * The payload an IME hands an editor when it commits rich content (a GIF from
 * the keyboard's media picker, a pasted image, a sticker).
 *
 * On real Android the interesting part is the permission dance:
 * requestPermission() asks the IME's process to grant the editor temporary read
 * access to the content URI across a process boundary, and releasePermission()
 * revokes it. Mac.apk runs the IME surface and the app in ONE process with one
 * filesystem view, so there is no cross-process grant to make: the URI is
 * already readable by the only process that exists. Both calls are therefore
 * genuinely no-ops here rather than unimplemented ones, and AOSP explicitly
 * documents both as safe to call when the platform has nothing to grant.
 *
 * Source: frameworks/base/core/java/android/view/inputmethod/InputContentInfo.java
 * https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/core/java/android/view/inputmethod/InputContentInfo.java
 */
public final class InputContentInfo implements Parcelable {

    private final Uri mContentUri;
    private final ClipDescription mDescription;
    private final Uri mLinkUri;

    public InputContentInfo(Uri contentUri, ClipDescription description) {
        this(contentUri, description, null);
    }

    public InputContentInfo(Uri contentUri, ClipDescription description, Uri linkUri) {
        // AOSP validates both eagerly so a malformed commit fails at the call
        // site rather than inside the editor.
        if (contentUri == null) throw new NullPointerException("contentUri");
        if (description == null) throw new NullPointerException("description");
        String scheme = contentUri.getScheme();
        if (scheme == null || !"content".equals(scheme.toLowerCase())) {
            throw new IllegalArgumentException("contentUri must have content scheme");
        }
        if (linkUri != null) {
            String ls = linkUri.getScheme();
            if (ls == null || (!"http".equalsIgnoreCase(ls) && !"https".equalsIgnoreCase(ls))) {
                throw new IllegalArgumentException("linkUri must be either http or https URI");
            }
        }
        mContentUri = contentUri;
        mDescription = description;
        mLinkUri = linkUri;
    }

    public Uri getContentUri() { return mContentUri; }

    public ClipDescription getDescription() { return mDescription; }

    public Uri getLinkUri() { return mLinkUri; }

    /**
     * No-op: single-process runtime, so the content URI needs no cross-process
     * grant. See the class comment.
     */
    public void requestPermission() { }

    /** No-op counterpart to requestPermission(). */
    public void releasePermission() { }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        Uri.writeToParcel(dest, mContentUri);
        if (mDescription != null) {
            dest.writeInt(1);
            mDescription.writeToParcel(dest, flags);
        } else {
            dest.writeInt(0);
        }
        Uri.writeToParcel(dest, mLinkUri);
    }

    public static final Creator<InputContentInfo> CREATOR = new Creator<InputContentInfo>() {
        @Override public InputContentInfo createFromParcel(Parcel source) {
            Uri contentUri = Uri.CREATOR.createFromParcel(source);
            ClipDescription description =
                    source.readInt() != 0 ? ClipDescription.CREATOR.createFromParcel(source) : null;
            Uri linkUri = Uri.CREATOR.createFromParcel(source);
            return new InputContentInfo(contentUri, description, linkUri);
        }
        @Override public InputContentInfo[] newArray(int size) {
            return new InputContentInfo[size];
        }
    };
}
