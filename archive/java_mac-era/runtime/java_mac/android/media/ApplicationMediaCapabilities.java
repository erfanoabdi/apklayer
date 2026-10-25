package android.media;
import java.util.ArrayList;
import java.util.List;
/** The media formats an app declares it can handle (for transcoding decisions).
 *  Source: frameworks/base/media/java/android/media/ApplicationMediaCapabilities.java */
public final class ApplicationMediaCapabilities {
    private final List<String> mVideoMimes;
    private final List<String> mHdrTypes;
    private ApplicationMediaCapabilities(Builder b) { mVideoMimes=b.mVideo; mHdrTypes=b.mHdr; }
    public boolean isFormatSpecified(String mimeType) { return mVideoMimes.contains(mimeType); }
    public boolean isVideoMimeTypeSupported(String mimeType) { return mVideoMimes.contains(mimeType); }
    public boolean isHdrTypeSupported(String hdrType) { return mHdrTypes.contains(hdrType); }
    public List<String> getSupportedVideoMimeTypes() { return mVideoMimes; }
    public List<String> getSupportedHdrTypes() { return mHdrTypes; }
    public static final class Builder {
        private final List<String> mVideo = new ArrayList<>();
        private final List<String> mHdr = new ArrayList<>();
        public Builder() {}
        public Builder addSupportedVideoMimeType(String m) { mVideo.add(m); return this; }
        public Builder addUnsupportedVideoMimeType(String m) { return this; }
        public Builder addSupportedHdrType(String h) { mHdr.add(h); return this; }
        public Builder addUnsupportedHdrType(String h) { return this; }
        public ApplicationMediaCapabilities build() { return new ApplicationMediaCapabilities(this); }
    }
}
