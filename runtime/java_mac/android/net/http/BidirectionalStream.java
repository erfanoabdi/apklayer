package android.net.http;
import java.nio.ByteBuffer;
/** HTTP/2 bidirectional stream. java.net has no bidi equivalent, so this is a
 *  link-only surface; apps needing bidi bundle their own Cronet.
 *  Source: frameworks/base .../net/http/BidirectionalStream.java */
public abstract class BidirectionalStream {
    public abstract void start();
    public abstract void read(ByteBuffer buffer);
    public abstract void write(ByteBuffer buffer, boolean endOfStream);
    public abstract void flush();
    public abstract void cancel();
    public abstract boolean isDone();

    // INTERFACE, not an abstract class -- the same kind mismatch that was fixed
    // on UrlRequest.Callback in this same package, and for the same reason. The
    // platform-ified Cronet API diverges from the org.chromium.net library it was
    // derived from (there, Callback IS an abstract class); the platform declares
    //
    //   public static interface BidirectionalStream.Callback   (API 34, S Ext 7)
    //   "Callback interface used to receive callbacks from a BidirectionalStream."
    //
    // -- developer.android.com/reference/android/net/http/BidirectionalStream.Callback
    // A guest compiled against the real SDK therefore lists this type in its class
    // file's `interfaces` array, and loading it against a class-shaped shadow dies
    // with IncompatibleClassChangeError ("... can not implement ..., because it is
    // not an interface"), taking the whole enclosing data source down with it.
    // onResponseTrailersReceived was simply MISSING from our surface; the real
    // interface declares eight methods, all `public abstract void`.
    // See docs/candycrush-plagueinc_2026-07-29.md.
    public interface Callback {
        void onStreamReady(BidirectionalStream stream);
        void onResponseHeadersReceived(BidirectionalStream stream, UrlResponseInfo info);
        void onReadCompleted(BidirectionalStream stream, UrlResponseInfo info,
                ByteBuffer buffer, boolean endOfStream);
        void onWriteCompleted(BidirectionalStream stream, UrlResponseInfo info,
                ByteBuffer buffer, boolean endOfStream);
        void onResponseTrailersReceived(BidirectionalStream stream, UrlResponseInfo info,
                HeaderBlock trailers);
        void onSucceeded(BidirectionalStream stream, UrlResponseInfo info);
        void onFailed(BidirectionalStream stream, UrlResponseInfo info, HttpException error);
        // AOSP declares this abstract like the rest; kept `default` to match the
        // sibling decision in UrlRequest.Callback (strictly more permissive at
        // link time, and nothing in the contract depends on it being abstract).
        default void onCanceled(BidirectionalStream stream, UrlResponseInfo info) { }
    }
    public static final class Builder {
        public Builder() { }
        public Builder addHeader(String header, String value) { return this; }
        public Builder setHttpMethod(String method) { return this; }
        public Builder setPriority(int priority) { return this; }
        public Builder setTrafficStatsTag(int tag) { return this; }
        public Builder setTrafficStatsUid(int uid) { return this; }
        public Builder setDelayRequestHeadersUntilFirstFlushEnabled(boolean enabled) { return this; }
        public BidirectionalStream build() {
            return new BidirectionalStream() {
                @Override public void start() {}
                @Override public void read(java.nio.ByteBuffer buffer) {}
                @Override public void write(java.nio.ByteBuffer buffer, boolean endOfStream) {}
                @Override public void flush() {}
                @Override public void cancel() {}
                @Override public boolean isDone() { return true; }
            };
        }
    }
}
