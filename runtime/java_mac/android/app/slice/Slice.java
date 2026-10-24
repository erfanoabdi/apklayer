package android.app.slice;
import java.util.ArrayList;
import java.util.List;
/** A tree of SliceItems describing app content for remote display.
 *
 *  AOSP ref: frameworks/base/core/java/android/app/slice/Slice.java
 *  (android16-release). @Deprecated there, like the rest of android.app.slice. */
@Deprecated
public final class Slice implements android.os.Parcelable {

    /** AOSP HINT_*: presentation hints an app attaches to a slice or item. */
    public static final String HINT_TITLE = "title";
    public static final String HINT_LIST = "list";
    public static final String HINT_LIST_ITEM = "list_item";
    public static final String HINT_LARGE = "large";
    public static final String HINT_ACTIONS = "actions";
    public static final String HINT_SELECTED = "selected";
    public static final String HINT_NO_TINT = "no_tint";
    public static final String HINT_SHORTCUT = "shortcut";
    public static final String HINT_PARTIAL = "partial";
    public static final String HINT_SEE_MORE = "see_more";
    public static final String HINT_KEYWORDS = "keywords";
    public static final String HINT_TTL = "ttl";
    public static final String HINT_LAST_UPDATED = "last_updated";
    public static final String HINT_HORIZONTAL = "horizontal";
    public static final String HINT_PERMISSION_REQUEST = "permission_request";
    public static final String HINT_ERROR = "error";
    public static final String HINT_SUMMARY = "summary";

    /** AOSP SUBTYPE_*: the item sub-type an app tags a SliceItem with. */
    public static final String SUBTYPE_COLOR = "color";
    public static final String SUBTYPE_CONTENT_DESCRIPTION = "content_description";
    public static final String SUBTYPE_LAYOUT_DIRECTION = "layout_direction";
    public static final String SUBTYPE_MAX = "max";
    public static final String SUBTYPE_MESSAGE = "message";
    public static final String SUBTYPE_MILLIS = "millis";
    public static final String SUBTYPE_PRIORITY = "priority";
    public static final String SUBTYPE_RANGE = "range";
    public static final String SUBTYPE_SOURCE = "source";
    public static final String SUBTYPE_TOGGLE = "toggle";
    public static final String SUBTYPE_VALUE = "value";

    /** AOSP EXTRA_*: the extras a slice host puts on the PendingIntent it fires
     *  back for a toggle or a range control. */
    public static final String EXTRA_TOGGLE_STATE = "android.app.slice.extra.TOGGLE_STATE";
    public static final String EXTRA_RANGE_VALUE = "android.app.slice.extra.RANGE_VALUE";

    private final List<SliceItem> mItems;
    private final SliceSpec mSpec;
    private final android.net.Uri mUri;
    private final List<String> mHints;
    private final boolean mCallerNeeded;

    private Slice(Builder b) {
        mItems = b.mItems;
        mSpec = b.mSpec;
        mUri = b.mUri;
        mHints = new ArrayList<String>(b.mHints);
        mCallerNeeded = b.mCallerNeeded;
    }

    /** AOSP's public (protected in the signature file) Parcel constructor: a
     *  Slice crosses the Binder from the SliceProvider to the slice host. */
    protected Slice(android.os.Parcel in) {
        mUri = in.readInt() != 0 ? android.net.Uri.CREATOR.createFromParcel(in) : null;
        mSpec = in.readTypedObject(SliceSpec.CREATOR);
        mCallerNeeded = in.readInt() != 0;
        mHints = new ArrayList<String>();
        in.readStringList(mHints);
        SliceItem[] items = in.createTypedArray(SliceItem.CREATOR);
        mItems = new ArrayList<SliceItem>();
        if (items != null) {
            for (SliceItem item : items) if (item != null) mItems.add(item);
        }
    }

    public List<SliceItem> getItems() { return mItems; }
    public SliceSpec getSpec() { return mSpec; }
    public android.net.Uri getUri() { return mUri; }

    /** AOSP getHints(): the hints attached to this slice via
     *  Builder.addHints. Was an unconditional empty list, which silently
     *  discarded whatever the app added; the builder now keeps them. */
    public List<String> getHints() { return mHints; }

    /** AOSP isCallerNeeded(): "@return true if the caller needs to be
     *  specified", i.e. the slice's content depends on WHO is displaying it, so
     *  the host must pass its own identity when binding. */
    public boolean isCallerNeeded() { return mCallerNeeded; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        if (mUri != null) {
            dest.writeInt(1);
            android.net.Uri.writeToParcel(dest, mUri);
        } else {
            dest.writeInt(0);
        }
        dest.writeTypedObject(mSpec, flags);
        dest.writeInt(mCallerNeeded ? 1 : 0);
        dest.writeStringList(mHints);
        dest.writeTypedArray(mItems.toArray(new SliceItem[0]), flags);
    }

    public static final android.os.Parcelable.Creator<Slice> CREATOR =
            new android.os.Parcelable.Creator<Slice>() {
        @Override public Slice createFromParcel(android.os.Parcel in) { return new Slice(in); }
        @Override public Slice[] newArray(int size) { return new Slice[size]; }
    };

    // AOSP's Slice.Builder (core/java/android/app/slice/Slice.java,
    // android16-release) is not final -- framework_lint vs the android-30
    // stub jar. Not final so an app compiled against real Android that
    // subclasses it does not fail at resolution against our jar.
    public static class Builder {
        private final List<SliceItem> mItems = new ArrayList<>();
        private final List<String> mHints = new ArrayList<>();
        private final SliceSpec mSpec;
        private final android.net.Uri mUri;
        private boolean mCallerNeeded;
        public Builder(android.net.Uri uri, SliceSpec spec) { mUri = uri; mSpec = spec; }
        public Builder(Slice.Builder parent) { mUri = parent.mUri; mSpec = parent.mSpec; }
        public Builder addText(CharSequence value, String subType, List<String> hints) { mItems.add(new SliceItem(SliceItem.FORMAT_TEXT, subType, value, hints)); return this; }
        public Builder addInt(int value, String subType, List<String> hints) { mItems.add(new SliceItem(SliceItem.FORMAT_INT, subType, value, hints)); return this; }
        public Builder addLong(long value, String subType, List<String> hints) { mItems.add(new SliceItem(SliceItem.FORMAT_LONG, subType, value, hints)); return this; }
        public Builder addIcon(android.graphics.drawable.Icon icon, String subType, List<String> hints) { mItems.add(new SliceItem(SliceItem.FORMAT_IMAGE, subType, icon, hints)); return this; }
        public Builder addAction(android.app.PendingIntent action, Slice subSlice, String subType) { mItems.add(new SliceItem(SliceItem.FORMAT_ACTION, subType, action, null)); return this; }
        public Builder addBundle(android.os.Bundle bundle, String subType, List<String> hints) { mItems.add(new SliceItem(SliceItem.FORMAT_BUNDLE, subType, bundle, hints)); return this; }

        /** AOSP addRemoteInput(RemoteInput, String subType, List<String> hints):
         *  a FORMAT_REMOTE_INPUT item -- an inline reply box inside a slice.
         *  SliceItem.getRemoteInput() is its reader. */
        public Builder addRemoteInput(android.app.RemoteInput remoteInput, String subType,
                List<String> hints) {
            mItems.add(new SliceItem(SliceItem.FORMAT_REMOTE_INPUT, subType, remoteInput, hints));
            return this;
        }

        /** AOSP addHints(List<String>): was a no-op that dropped its argument,
         *  so Slice.getHints() could never answer. */
        public Builder addHints(List<String> hints) {
            if (hints != null) mHints.addAll(hints);
            return this;
        }

        /** AOSP setCallerNeeded(boolean): "Sets whether the caller needs to be
         *  specified for this slice"; read back by Slice.isCallerNeeded(). */
        public Builder setCallerNeeded(boolean callerNeeded) {
            mCallerNeeded = callerNeeded;
            return this;
        }

        public Builder addSubSlice(Slice slice, String subType) { mItems.add(new SliceItem(SliceItem.FORMAT_SLICE, subType, slice, null)); return this; }
        public Slice build() { return new Slice(this); }
    }
}
