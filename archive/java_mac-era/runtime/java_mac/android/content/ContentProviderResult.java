// android.content.ContentProviderResult -- the outcome of ONE
// ContentProviderOperation inside a batch.
//
// Why Mac.apk needs it: Fossify Gallery and Telegram reference it directly,
// and every applyBatch caller receives an array of them. It is also the input
// side of value back-references: ContentProviderOperation.BackReference.resolve
// reads `extras` / `uri` / `count` off an earlier result to fill in a later
// operation's value, so the "exactly one of the four fields is set" invariant
// below is load-bearing rather than documentation.
//
// AOSP source mirrored: frameworks/base/core/java/android/content/
// ContentProviderResult.java (android11-release), field-for-field.
//
// Two departures:
//   1. AOSP encodes `exception` with android.os.ParcelableException, which
//      exists to carry an exception ACROSS a binder boundary by name. This
//      tree has no such class and no such boundary -- our Parcel is an
//      in-process object-ref parcel -- so the Throwable reference is written
//      with writeValue and read back as itself, which preserves more than the
//      remote encoding does.
//   2. AOSP's @hide ContentProviderResult(ContentProviderResult, int userId)
//      constructor is omitted: it exists only to rewrite a Uri for a
//      cross-user provider call, which this single-user runtime never makes.
package android.content;

import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * Contains the result of the application of a ContentProviderOperation.
 * <p>
 * It is guaranteed to have exactly one of uri, count, extras, or exception set.
 */
public class ContentProviderResult implements Parcelable {
    public final Uri uri;
    public final Integer count;
    public final Bundle extras;
    public final Throwable exception;

    public ContentProviderResult(Uri uri) {
        this(Objects.requireNonNull(uri), null, null, null);
    }

    public ContentProviderResult(int count) {
        this(null, count, null, null);
    }

    public ContentProviderResult(Bundle extras) {
        this(null, null, Objects.requireNonNull(extras), null);
    }

    public ContentProviderResult(Throwable exception) {
        this(null, null, null, exception);
    }

    /** {@hide} */
    public ContentProviderResult(Uri uri, Integer count, Bundle extras, Throwable exception) {
        this.uri = uri;
        this.count = count;
        this.extras = extras;
        this.exception = exception;
    }

    public ContentProviderResult(Parcel source) {
        if (source.readInt() != 0) {
            uri = Uri.CREATOR.createFromParcel(source);
        } else {
            uri = null;
        }
        if (source.readInt() != 0) {
            count = source.readInt();
        } else {
            count = null;
        }
        if (source.readInt() != 0) {
            extras = source.readBundle();
        } else {
            extras = null;
        }
        if (source.readInt() != 0) {
            exception = (Throwable) source.readValue(null);
        } else {
            exception = null;
        }
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        if (uri != null) {
            dest.writeInt(1);
            uri.writeToParcel(dest, flags);
        } else {
            dest.writeInt(0);
        }
        if (count != null) {
            dest.writeInt(1);
            dest.writeInt(count);
        } else {
            dest.writeInt(0);
        }
        if (extras != null) {
            dest.writeInt(1);
            dest.writeBundle(extras);
        } else {
            dest.writeInt(0);
        }
        if (exception != null) {
            dest.writeInt(1);
            dest.writeValue(exception);
        } else {
            dest.writeInt(0);
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<ContentProviderResult> CREATOR =
            new Creator<ContentProviderResult>() {
        @Override
        public ContentProviderResult createFromParcel(Parcel source) {
            return new ContentProviderResult(source);
        }

        @Override
        public ContentProviderResult[] newArray(int size) {
            return new ContentProviderResult[size];
        }
    };

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ContentProviderResult(");
        if (uri != null) {
            sb.append("uri=" + uri + " ");
        }
        if (count != null) {
            sb.append("count=" + count + " ");
        }
        if (extras != null) {
            sb.append("extras=" + extras + " ");
        }
        if (exception != null) {
            sb.append("exception=" + exception + " ");
        }
        sb.deleteCharAt(sb.length() - 1);
        sb.append(")");
        return sb.toString();
    }
}
