// android.view.ContentInfo -- the payload of the unified content-insertion API
// (View.performReceiveContent / OnReceiveContentListener), which is how modern
// Android delivers a paste, a drag-drop, an autofill fill and an IME rich
// content commit through ONE code path instead of four.
//
// Was a 1-line auto-generated stub (`public ContentInfo() {}`), so any app that
// built one via the Builder -- which androidx.core's ContentInfoCompat does on
// every paste when SDK_INT >= 31 -- hit NoSuchMethodError.
//
// Source: frameworks/base/core/java/android/view/ContentInfo.java
// https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/core/java/android/view/ContentInfo.java
package android.view;

import android.content.ClipData;
import android.content.ClipDescription;
import android.net.Uri;
import android.os.Bundle;
import android.util.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class ContentInfo implements android.os.Parcelable {

    // AOSP android14-release core/java/android/view/ContentInfo.java:60-92.
    // These are compile-time constants, so an app's bytecode carries the
    // NUMBERS: they must be AOSP's, or an app's `getSource() == SOURCE_CLIPBOARD`
    // (inlined 1) never matches a payload the framework built. The previous
    // values were AOSP's shifted down by one, with SOURCE_APP missing.
    /** Content came from the app itself (setClip/receiveContent by the app). */
    public static final int SOURCE_APP = 0;
    /** Content came from the clipboard (an explicit paste). */
    public static final int SOURCE_CLIPBOARD = 1;
    /** Content came from the IME (a keyboard sticker/GIF commit). */
    public static final int SOURCE_INPUT_METHOD = 2;
    /** Content came from a drag-and-drop. */
    public static final int SOURCE_DRAG_AND_DROP = 3;
    /** Content came from autofill. */
    public static final int SOURCE_AUTOFILL = 4;
    /** Content came from a process-text action. */
    public static final int SOURCE_PROCESS_TEXT = 5;

    /** Ask the receiver to strip styling and insert plain text only. */
    public static final int FLAG_CONVERT_TO_PLAIN_TEXT = 1 << 0;

    private final ClipData mClip;
    private final int mSource;
    private final int mFlags;
    private final Uri mLinkUri;
    private final Bundle mExtras;

    private ContentInfo(Builder b) {
        mClip = b.mClip;
        mSource = b.mSource;
        mFlags = b.mFlags;
        mLinkUri = b.mLinkUri;
        mExtras = b.mExtras;
    }

    public ClipData getClip() { return mClip; }

    public int getSource() { return mSource; }

    public int getFlags() { return mFlags; }

    public Uri getLinkUri() { return mLinkUri; }

    public Bundle getExtras() { return mExtras; }

    /**
     * AOSP's split helper: partition this payload into (accepted, remaining) so
     * a receiver can consume the items it understands and hand the rest back to
     * the platform. A null side is MEANINGFUL -- it means "nothing here" -- so
     * neither side may be substituted with an empty ContentInfo.
     */
    public Pair<ContentInfo, ContentInfo> partition(Predicate<ClipData.Item> itemPredicate) {
        if (mClip == null) return new Pair<>(null, this);
        final int count = mClip.getItemCount();
        if (count == 1) {
            boolean matched = itemPredicate.test(mClip.getItemAt(0));
            return matched ? new Pair<>(this, null) : new Pair<>(null, this);
        }
        List<ClipData.Item> accepted = new ArrayList<>();
        List<ClipData.Item> remaining = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ClipData.Item item = mClip.getItemAt(i);
            if (itemPredicate.test(item)) accepted.add(item);
            else remaining.add(item);
        }
        if (accepted.isEmpty()) return new Pair<>(null, this);
        if (remaining.isEmpty()) return new Pair<>(this, null);
        ContentInfo a = new Builder(this)
                .setClip(buildClipData(mClip.getDescription(), accepted)).build();
        ContentInfo r = new Builder(this)
                .setClip(buildClipData(mClip.getDescription(), remaining)).build();
        return new Pair<>(a, r);
    }

    private static ClipData buildClipData(ClipDescription description,
                                          List<ClipData.Item> items) {
        ClipData clip = new ClipData(description, items.get(0));
        for (int i = 1; i < items.size(); i++) clip.addItem(items.get(i));
        return clip;
    }

    @Override public String toString() {
        return "ContentInfo{clip=" + mClip
                + ", source=" + sourceToString(mSource)
                + ", flags=" + flagsToString(mFlags)
                + ", linkUri=" + mLinkUri
                + ", extras=" + mExtras + "}";
    }

    public static String sourceToString(int source) {
        switch (source) {
            case SOURCE_APP:            return "SOURCE_APP";
            case SOURCE_CLIPBOARD:      return "SOURCE_CLIPBOARD";
            case SOURCE_INPUT_METHOD:   return "SOURCE_INPUT_METHOD";
            case SOURCE_DRAG_AND_DROP:  return "SOURCE_DRAG_AND_DROP";
            case SOURCE_AUTOFILL:       return "SOURCE_AUTOFILL";
            case SOURCE_PROCESS_TEXT:   return "SOURCE_PROCESS_TEXT";
            default:                    return String.valueOf(source);
        }
    }

    public static String flagsToString(int flags) {
        return (flags & FLAG_CONVERT_TO_PLAIN_TEXT) != 0 ? "FLAG_CONVERT_TO_PLAIN_TEXT" : "0";
    }

    public static final class Builder {
        private ClipData mClip;
        private int mSource;
        private int mFlags;
        private Uri mLinkUri;
        private Bundle mExtras;

        /** Copy form, so a receiver can tweak exactly one field. */
        public Builder(ContentInfo other) {
            mClip = other.mClip;
            mSource = other.mSource;
            mFlags = other.mFlags;
            mLinkUri = other.mLinkUri;
            mExtras = other.mExtras;
        }

        public Builder(ClipData clip, int source) {
            mClip = clip;
            mSource = source;
        }

        public Builder setClip(ClipData clip) { mClip = clip; return this; }

        public Builder setSource(int source) { mSource = source; return this; }

        public Builder setFlags(int flags) { mFlags = flags; return this; }

        public Builder setLinkUri(Uri linkUri) { mLinkUri = linkUri; return this; }

        public Builder setExtras(Bundle extras) { mExtras = extras; return this; }

        public ContentInfo build() { return new ContentInfo(this); }
    }

    // ---- Parcelable ----------------------------------------------------
    // AOSP: core/java/android/view/ContentInfo.java (android16-release) :441.
    // A ContentInfo is the payload of View.performReceiveContent, and
    // androidx.core's ContentInfoCompat builds one on every paste at SDK 31+.
    // Without the interface a ContentInfo put into a Bundle read back as null
    // in this runtime (the put is unchecked under VM-wide -Xverify:none), so
    // the paste silently delivered nothing.
    //
    // Field order is AOSP's. AOSP's trailing mInputContentInfo and
    // mDragAndDropPermissions are @hide, never set on an app-built instance,
    // and not modelled here, so their two presence ints are absent rather than
    // written as constant zeros.
    //
    // mClip is written unconditionally, matching AOSP: its Builder requires a
    // non-null clip, so a ContentInfo with a null clip cannot be constructed
    // through the public API. mLinkUri goes through Uri's NULL-TOLERANT static
    // writer, which is why it does not need a presence int of its own.
    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        mClip.writeToParcel(dest, flags);
        dest.writeInt(mSource);
        dest.writeInt(mFlags);
        Uri.writeToParcel(dest, mLinkUri);
        dest.writeBundle(mExtras);
    }

    public static final android.os.Parcelable.Creator<ContentInfo> CREATOR =
            new android.os.Parcelable.Creator<ContentInfo>() {
        @Override public ContentInfo createFromParcel(android.os.Parcel parcel) {
            ClipData clip = ClipData.CREATOR.createFromParcel(parcel);
            int source = parcel.readInt();
            int flags = parcel.readInt();
            Uri linkUri = Uri.CREATOR.createFromParcel(parcel);
            Bundle extras = parcel.readBundle();
            return new ContentInfo.Builder(clip, source)
                    .setFlags(flags)
                    .setLinkUri(linkUri)
                    .setExtras(extras)
                    .build();
        }
        @Override public ContentInfo[] newArray(int size) {
            return new ContentInfo[size];
        }
    };
}
