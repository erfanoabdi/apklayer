// android.content.ClipDescription -- ClipData metadata. Per AOSP
// frameworks/base/core/java/android/content/ClipDescription.java
// (android14-release).
//
// 2026-09-26 (clipboard host bridge): the mime list is AOSP's growable list
// (addMimeTypes), a null mime array is AOSP's NullPointerException, the
// timestamp is 0 until the clipboard service stamps the clip it stores
// (AOSP ClipboardService: `description.setTimestamp(System.currentTimeMillis())`
// in setPrimaryClipInternalNoClassifyLocked -- construction does not stamp it),
// and getMimeType is a plain List.get like AOSP's.
package android.content;

import java.util.ArrayList;
import java.util.Arrays;

public class ClipDescription implements android.os.Parcelable {
    public static final String MIMETYPE_TEXT_PLAIN = "text/plain";
    public static final String MIMETYPE_TEXT_HTML = "text/html";
    public static final String MIMETYPE_TEXT_URILIST = "text/uri-list";
    public static final String MIMETYPE_TEXT_INTENT = "text/vnd.android.intent";
    public static final String MIMETYPE_UNKNOWN = "application/octet-stream";
    public static final String EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE";
    /** AOSP core/java/android/content/ClipDescription.java -- set on a clip
     *  that arrived from another device (Nearby / cross-device copy). */
    public static final String EXTRA_IS_REMOTE_DEVICE = "android.content.extra.IS_REMOTE_DEVICE";

    // AOSP getClassificationStatus() value domain. Note there is no
    // CLASSIFICATION_* == 0: AOSP starts at 1.
    public static final int CLASSIFICATION_NOT_COMPLETE = 1;
    public static final int CLASSIFICATION_NOT_PERFORMED = 2;
    public static final int CLASSIFICATION_COMPLETE = 3;

    private CharSequence label;
    private ArrayList<String> mimeTypes;
    private android.os.PersistableBundle extras;
    private long timeStamp;
    private boolean isStyledText;

    /** Not in AOSP's public surface; used by CREATOR below. */
    public ClipDescription() { this.mimeTypes = new ArrayList<String>(); }
    public ClipDescription(CharSequence label, String[] mimeTypes) {
        if (mimeTypes == null) {
            throw new NullPointerException("mimeTypes is null");
        }
        this.label = label;
        this.mimeTypes = new ArrayList<String>(Arrays.asList(mimeTypes));
    }
    /** AOSP copies label, mime types and timestamp (not extras). */
    public ClipDescription(ClipDescription o) {
        this.label = o.label;
        this.mimeTypes = new ArrayList<String>(o.mimeTypes);
        this.timeStamp = o.timeStamp;
    }

    /** Everything a Parcel round trip carries (label, mime types, extras,
     *  timestamp, styled flag) on a new object -- see
     *  ClipData.macapkCopyForTransfer. */
    ClipDescription macapkCopyForTransfer() {
        ClipDescription d = new ClipDescription(this);
        if (extras != null) d.extras = new android.os.PersistableBundle(extras);
        d.isStyledText = isStyledText;
        return d;
    }

    public CharSequence getLabel() { return label; }
    public int getMimeTypeCount() { return mimeTypes.size(); }
    /** AOSP `return mMimeTypes.get(index);` -- out of range throws. */
    public String getMimeType(int index) {
        return mimeTypes.get(index);
    }
    /** AOSP filterMimeTypes()/hasMimeType() BOTH route through
     *  compareMimeTypes(), so "text/&#42;" matches "text/plain" and "&#42;/&#42;"
     *  matches everything. The previous bodies used startsWith-after-stripping-
     *  stars and exact equality respectively, which agreed with AOSP only for a
     *  filter with no wildcard: hasMimeType("text/&#42;") answered false on a
     *  text/plain clip, and filterMimeTypes("&#42;/&#42;") reduced to a
     *  startsWith("/") that matched nothing. Both are the exact questions a
     *  drag-and-drop or rich-content target asks. */
    public String[] filterMimeTypes(String filter) {
        if (filter == null) return mimeTypes.toArray(new String[0]);
        ArrayList<String> out = null;
        for (String m : mimeTypes) {
            if (m != null && compareMimeTypes(m, filter)) {
                if (out == null) out = new ArrayList<String>();
                out.add(m);
            }
        }
        return out == null ? null : out.toArray(new String[0]);
    }
    public boolean hasMimeType(String type) {
        if (type == null) return false;
        for (String m : mimeTypes) {
            if (m != null && compareMimeTypes(m, type)) return true;
        }
        return false;
    }

    /** AOSP package-private: add each type not already present (ClipData
     *  .addItem(ContentResolver, Item) widens the description with it). */
    void addMimeTypes(String[] types) {
        for (int i = 0; i != types.length; i++) {
            final String mimeType = types[i];
            if (!mimeTypes.contains(mimeType)) {
                mimeTypes.add(mimeType);
            }
        }
    }

    /** AOSP :236 -- verbatim. `desiredType` is the pattern side and may carry
     *  a trailing wildcard ("&#42;/&#42;" or "text/&#42;"); `concreteType` may
     *  not. Note AOSP requires a '/' at index > 0 in the pattern, so a bare
     *  "text" never matches. */
    public static boolean compareMimeTypes(String concreteType, String desiredType) {
        final int typeLength = desiredType.length();
        if (typeLength == 3 && desiredType.equals("*/*")) {
            return true;
        }
        final int slashpos = desiredType.indexOf('/');
        if (slashpos > 0) {
            if (typeLength == slashpos + 2 && desiredType.charAt(slashpos + 1) == '*') {
                if (desiredType.regionMatches(0, concreteType, 0, slashpos + 1)) {
                    return true;
                }
            } else if (desiredType.equals(concreteType)) {
                return true;
            }
        }
        return false;
    }
    public android.os.PersistableBundle getExtras() { return extras; }
    /** AOSP stores a copy: `mExtras = new PersistableBundle(extras)`. */
    public void setExtras(android.os.PersistableBundle ext) {
        this.extras = ext != null ? new android.os.PersistableBundle(ext) : null;
    }
    /** The time the clip was set as primary; 0 for a clip never set. */
    public long getTimestamp() { return timeStamp; }
    /** AOSP @hide; ClipboardService stamps the clip it stores. */
    public void setTimestamp(long timeStamp) { this.timeStamp = timeStamp; }

    /** AOSP @hide validate(): at least one mime type, none null. */
    public void validate() {
        if (mimeTypes == null) {
            throw new NullPointerException("null mime types");
        }
        final int size = mimeTypes.size();
        if (size <= 0) {
            throw new IllegalArgumentException("must have at least 1 mime type");
        }
        for (int i = 0; i < size; i++) {
            if (mimeTypes.get(i) == null) {
                throw new NullPointerException("mime type at " + i + " is null");
            }
        }
    }

    /** AOSP :473 returns an INT from the CLASSIFICATION_* domain, not a
     *  boolean -- this used to be declared `boolean`, so an app compiled
     *  against android.jar emitted `getClassificationStatus:()I` and got
     *  NoSuchMethodError against our `()Z`. Mac.apk runs no TextClassifier
     *  over clipboard content, so classification is never ATTEMPTED, which is
     *  precisely CLASSIFICATION_NOT_PERFORMED (a device also reports that when
     *  the text is too long). */
    public int getClassificationStatus() { return CLASSIFICATION_NOT_PERFORMED; }

    /** AOSP :458 -- "@throws IllegalStateException if classification is not
     *  complete". getClassificationStatus() above is never COMPLETE here, so
     *  this always throws, exactly as it does on a device whose classifier did
     *  not run. Returning 0f instead would look like a confident "no entity of
     *  that type", which is a different claim. */
    public float getConfidenceScore(String entity) {
        if (getClassificationStatus() != CLASSIFICATION_COMPLETE) {
            throw new IllegalStateException("Classification not complete");
        }
        return 0f;
    }

    /** AOSP :418 -- whether the first item of the associated clip carries
     *  styled (span-bearing) text. Set by ClipData when the description is
     *  attached; false until then, as upstream. */
    public boolean isStyledText() { return isStyledText; }

    /** AOSP's package-private setter, used by ClipData. */
    void setIsStyledText(boolean styled) { this.isStyledText = styled; }

    @Override public String toString() {
        return "ClipDescription{label=" + label + "}";
    }

    // AOSP declares ClipDescription as Parcelable, and the drag-and-drop /
    // clipboard / IME-rich-content paths all round-trip one through a Parcel.
    // Without this the class kind is wrong and any such round trip fails.
    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        android.text.TextUtils.writeToParcel(label, dest, flags);
        dest.writeStringArray(mimeTypes.toArray(new String[0]));
        dest.writeLong(timeStamp);
    }

    public static final Creator<ClipDescription> CREATOR = new Creator<ClipDescription>() {
        @Override public ClipDescription createFromParcel(android.os.Parcel in) {
            ClipDescription d = new ClipDescription();
            d.label = android.text.TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
            String[] types = in.createStringArray();
            d.mimeTypes = types != null
                ? new ArrayList<String>(Arrays.asList(types)) : new ArrayList<String>();
            d.timeStamp = in.readLong();
            return d;
        }
        @Override public ClipDescription[] newArray(int size) {
            return new ClipDescription[size];
        }
    };
}
