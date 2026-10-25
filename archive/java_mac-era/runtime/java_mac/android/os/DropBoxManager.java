// android.os.DropBoxManager -- a system-level ring buffer of tagged diagnostic
// entries (crashes, ANRs, custom logs). Mac.apk has no system dropbox, so
// addText/addData are accepted no-ops and getNextEntry() always returns null
// (an empty dropbox). The Entry nested Parcelable + flag constants are present so
// apps that read/enumerate entries compile and behave as if the box is empty.
//
// AOSP ref: frameworks/base/core/java/android/os/DropBoxManager.java
package android.os;

public class DropBoxManager {
    public static final String ACTION_DROPBOX_ENTRY_ADDED =
            "android.intent.action.DROPBOX_ENTRY_ADDED";
    public static final String EXTRA_TAG = "tag";
    public static final String EXTRA_TIME = "time";
    public static final String EXTRA_DROPPED_COUNT = "android.os.extra.DROPPED_COUNT";

    public static final int IS_EMPTY = 1;
    public static final int IS_TEXT = 2;
    public static final int IS_GZIPPED = 4;

    public DropBoxManager() {}

    public void addText(String tag, String data) {}

    public void addData(String tag, byte[] data, int flags) {}

    public void addFile(String tag, java.io.File file, int flags) throws java.io.IOException {}

    public boolean isTagEnabled(String tag) { return true; }

    // Empty dropbox: there is never a next entry.
    public Entry getNextEntry(String tag, long msec) { return null; }

    // DropBoxManager.Entry -- a single stored entry. AOSP ref: same file.
    public static class Entry implements Parcelable, java.io.Closeable {
        private final String mTag;
        private final long mTimeMillis;
        private final byte[] mData;
        private final int mFlags;

        public Entry(String tag, long millis) {
            this(tag, millis, new byte[0], IS_EMPTY);
        }
        public Entry(String tag, long millis, String text) {
            this(tag, millis,
                 text == null ? new byte[0] : text.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                 IS_TEXT);
        }
        public Entry(String tag, long millis, byte[] data, int flags) {
            mTag = tag;
            mTimeMillis = millis;
            mData = data;
            mFlags = flags;
        }

        public String getTag() { return mTag; }
        public long getTimeMillis() { return mTimeMillis; }
        public int getFlags() { return mFlags & ~IS_GZIPPED; }

        public String getText(int maxBytes) {
            if ((mFlags & IS_TEXT) == 0 || mData == null) return null;
            int n = Math.min(maxBytes, mData.length);
            return new String(mData, 0, n, java.nio.charset.StandardCharsets.UTF_8);
        }

        public java.io.InputStream getInputStream() throws java.io.IOException {
            if (mData == null) return null;
            return new java.io.ByteArrayInputStream(mData);
        }

        @Override public void close() {}

        @Override public int describeContents() { return 0; }
        @Override public void writeToParcel(Parcel dest, int flags) {
            dest.writeString(mTag);
            dest.writeLong(mTimeMillis);
            dest.writeInt(mFlags);
            dest.writeByteArray(mData);
        }

        public static final Parcelable.Creator<Entry> CREATOR =
            new Parcelable.Creator<Entry>() {
                @Override public Entry createFromParcel(Parcel in) {
                    String tag = in.readString();
                    long millis = in.readLong();
                    int flags = in.readInt();
                    byte[] data = in.createByteArray();
                    return new Entry(tag, millis, data, flags);
                }
                @Override public Entry[] newArray(int size) { return new Entry[size]; }
            };
    }
}
