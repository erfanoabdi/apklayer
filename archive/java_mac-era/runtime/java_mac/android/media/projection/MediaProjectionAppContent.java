package android.media.projection;

import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * One shareable piece of app content offered in the media-projection consent
 * dialog: a thumbnail, a label, and the app's own id for it (API 36).
 *
 * Pure app-supplied DATA, so it is implemented in full. Whether the projection
 * it describes can ever be granted is answered where it belongs --
 * MediaProjectionManager, which has no consent transport on this host.
 *
 * Source: frameworks/base/media/java/android/media/projection/MediaProjectionAppContent.java
 */
public final class MediaProjectionAppContent implements Parcelable {

    private final Bitmap mThumbnail;
    private final CharSequence mLabel;
    private final int mId;

    public MediaProjectionAppContent(Bitmap thumbnail, CharSequence label, int id) {
        mThumbnail = Objects.requireNonNull(thumbnail, "thumbnail must not be null");
        mLabel = Objects.requireNonNull(label, "label must not be null");
        mId = id;
    }

    /** @hide -- AOSP publishes no getters on this class; the system UI reads it
     *  back off the Parcel. Kept package-private so the fields are not dead. */
    Bitmap getThumbnail() { return mThumbnail; }
    CharSequence getLabel() { return mLabel; }
    int getId() { return mId; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedObject(mThumbnail, flags);
        dest.writeCharSequence(mLabel);
        dest.writeInt(mId);
    }

    public static final Parcelable.Creator<MediaProjectionAppContent> CREATOR =
            new Parcelable.Creator<MediaProjectionAppContent>() {
        @Override public MediaProjectionAppContent createFromParcel(Parcel in) {
            Bitmap thumbnail = in.readTypedObject(Bitmap.CREATOR);
            CharSequence label = in.readCharSequence();
            return new MediaProjectionAppContent(thumbnail, label == null ? "" : label,
                    in.readInt());
        }
        @Override public MediaProjectionAppContent[] newArray(int size) {
            return new MediaProjectionAppContent[size];
        }
    };
}
