package android.media;
/** Container for scheme-specific DRM initialization data. Mac.apk plays no DRM
 *  content, so instances are only referenced, not populated. */
public abstract class DrmInitData {
    public abstract SchemeInitData get(java.util.UUID schemeUuid);
    public int getSchemeInitDataCount() { return 0; }
    public SchemeInitData getSchemeInitDataAt(int index) { return null; }
    public static final class SchemeInitData {
        /** AOSP: "The UUID identifying an universal scheme." Verbatim value from
         *  frameworks/base/media/java/android/media/DrmInitData.java -- the
         *  all-zero UUID, meaning "this init data is not scheme-specific". */
        public static final java.util.UUID UUID_NIL = new java.util.UUID(0, 0);

        public final java.util.UUID uuid;
        public final String mimeType;
        public final byte[] data;
        public SchemeInitData(java.util.UUID uuid, String mimeType, byte[] data) {
            this.uuid = uuid; this.mimeType = mimeType; this.data = data;
        }
    }
}
