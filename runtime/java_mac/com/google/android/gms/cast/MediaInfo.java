package com.google.android.gms.cast;

// com.google.android.gms.cast.MediaInfo -- describes a media item to cast. Pure
// data built via its Builder; Mac.apk casts nothing (no Cast backend), but the
// type and Builder must link for apps that construct one. Minimal published
// surface (content id, stream type, content type).
// Ref: developers.google.com/android/reference/.../cast/MediaInfo
public class MediaInfo {

    public static final int STREAM_TYPE_NONE = 0;
    public static final int STREAM_TYPE_BUFFERED = 1;
    public static final int STREAM_TYPE_LIVE = 2;
    public static final int STREAM_TYPE_INVALID = -1;

    private final String contentId;
    private final int streamType;
    private final String contentType;

    private MediaInfo(String contentId, int streamType, String contentType) {
        this.contentId = contentId;
        this.streamType = streamType;
        this.contentType = contentType;
    }

    public String getContentId() { return contentId; }
    public int getStreamType() { return streamType; }
    public String getContentType() { return contentType; }

    public static class Builder {
        private final String contentId;
        private int streamType = STREAM_TYPE_BUFFERED;
        private String contentType;
        public Builder(String contentId) { this.contentId = contentId; }
        public Builder setStreamType(int streamType) { this.streamType = streamType; return this; }
        public Builder setContentType(String contentType) { this.contentType = contentType; return this; }
        public MediaInfo build() { return new MediaInfo(contentId, streamType, contentType); }
    }
}
