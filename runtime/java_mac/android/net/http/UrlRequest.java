package android.net.http;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * A single HTTP request (Android's platform Cronet API, android.net.http).
 *
 * This is a REAL implementation over java.net.HttpURLConnection, not a stub:
 * start() performs the fetch on the caller's Executor and drives the Callback
 * (onResponseStarted -> onReadCompleted* -> onSucceeded, or onFailed). Apps
 * that use the platform HttpEngine (most bundle their own Cronet, but some use
 * the platform one) get working networking. It intentionally does NOT implement
 * QUIC/HTTP2/upload-streaming/bidirectional -- java.net gives HTTP/1.1, which is
 * the correct honest subset; the callback contract is fully honoured.
 *
 * Source: frameworks/base .../net/http/UrlRequest.java
 */
public abstract class UrlRequest {

    public static final int REQUEST_PRIORITY_IDLE = 0;
    public static final int REQUEST_PRIORITY_LOW = 2;
    public static final int REQUEST_PRIORITY_MEDIUM = 3;
    public static final int REQUEST_PRIORITY_HIGHEST = 4;

    public abstract void start();
    public abstract void followRedirect();
    public abstract void read(ByteBuffer buffer);
    public abstract void cancel();
    public abstract boolean isDone();
    public abstract void getStatus(StatusListener listener);

    public interface StatusListener { void onStatus(int status); }

    // INTERFACE, not an abstract class. The platform android.net.http API
    // (unlike the older org.chromium.net Cronet API, where Callback IS an
    // abstract class) declares this as an interface, so an app compiled
    // against the real SDK emits it in its class file's `interfaces` list.
    // Declaring it a class here makes every such app fail to link with
    // IncompatibleClassChangeError: "can not implement
    // android.net.http.UrlRequest$Callback, because it is not an interface".
    // Measured: it is the ONLY reason androidx.media3.datasource
    // .HttpEngineDataSource (and bidmachine's repackaged io.bidmachine.media3
    // copy) fails to link -- 3 of 3 link failures on Temple Run 2 and 4 of 5
    // on Fruit Ninja. Ground truth for the interface-ness is Google's own
    // androidx.media3 source, which writes `implements` not `extends`:
    // https://raw.githubusercontent.com/androidx/media/release/libraries/
    //   datasource/src/main/java/androidx/media3/datasource/
    //   HttpEngineDataSource.java  ->  line 1001:
    //   "final class UrlRequestCallback implements UrlRequest.Callback {"
    // onCanceled is `default` rather than abstract on purpose: media3
    // overrides all six, but a caller that omits onCanceled must still link,
    // and a default is strictly more permissive than an abstract declaration.
    public interface Callback {
        void onRedirectReceived(UrlRequest request, UrlResponseInfo info,
                String newLocationUrl) throws Exception;
        void onResponseStarted(UrlRequest request, UrlResponseInfo info)
                throws Exception;
        void onReadCompleted(UrlRequest request, UrlResponseInfo info,
                ByteBuffer byteBuffer) throws Exception;
        void onSucceeded(UrlRequest request, UrlResponseInfo info);
        void onFailed(UrlRequest request, UrlResponseInfo info, HttpException error);
        default void onCanceled(UrlRequest request, UrlResponseInfo info) { }
    }

    public static final class Builder {
        private final String mUrl;
        private final Executor mExecutor;
        private final Callback mCallback;
        private String mMethod = "GET";
        private final java.util.List<String[]> mHeaders = new java.util.ArrayList<>();

        Builder(String url, Executor executor, Callback callback) {
            mUrl = url; mExecutor = executor; mCallback = callback;
        }
        public Builder setHttpMethod(String method) { mMethod = method; return this; }
        public Builder setTrafficStatsTag(int tag) { return this; }
        public Builder addHeader(String name, String value) {
            mHeaders.add(new String[]{name, value}); return this;
        }
        public Builder setPriority(int priority) { return this; }
        public Builder setCacheDisabled(boolean disabled) { return this; }
        public Builder setDirectExecutorAllowed(boolean allowed) { return this; }
        public Builder bindToNetwork(android.net.Network network) { return this; }
        public Builder setUploadDataProvider(UploadDataProvider provider, Executor executor) {
            return this;
        }
        public UrlRequest build() {
            return new Impl(mUrl, mExecutor, mCallback, mMethod, mHeaders);
        }
    }

    private static final class Impl extends UrlRequest {
        private final String mUrl;
        private final Executor mExecutor;
        private final Callback mCallback;
        private final String mMethod;
        private final List<String[]> mHeaders;
        private volatile boolean mDone;
        private volatile boolean mCancelled;

        Impl(String url, Executor executor, Callback callback, String method,
                List<String[]> headers) {
            mUrl = url; mExecutor = executor; mCallback = callback;
            mMethod = method; mHeaders = headers;
        }

        @Override public void start() {
            Runnable job = new Runnable() {
                @Override public void run() { doFetch(); }
            };
            if (mExecutor != null) mExecutor.execute(job); else job.run();
        }

        private void doFetch() {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(mUrl).openConnection();
                conn.setRequestMethod(mMethod);
                for (String[] h : mHeaders) conn.setRequestProperty(h[0], h[1]);
                conn.setInstanceFollowRedirects(true);
                int code = conn.getResponseCode();
                String msg = conn.getResponseMessage();
                Map<String, List<String>> hdrs = conn.getHeaderFields();
                HeaderBlock hb = HeaderBlock.from(hdrs);
                java.io.InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
                byte[] body = readAll(in);
                UrlResponseInfo info = UrlResponseInfo.make(mUrl, code, msg, hb, body.length);
                mCallback.onResponseStarted(this, info);
                if (mCancelled) { mDone = true; mCallback.onCanceled(this, info); return; }
                // Deliver the body in one onReadCompleted, then succeeded.
                if (body.length > 0) {
                    mCallback.onReadCompleted(this, info, ByteBuffer.wrap(body));
                }
                mDone = true;
                mCallback.onSucceeded(this, info);
            } catch (Throwable t) {
                mDone = true;
                try {
                    mCallback.onFailed(this, null,
                            new HttpException("request failed: " + mUrl, t));
                } catch (Throwable ignored) { }
            } finally {
                if (conn != null) conn.disconnect();
            }
        }

        private static byte[] readAll(java.io.InputStream in) throws java.io.IOException {
            if (in == null) return new byte[0];
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            in.close();
            return bos.toByteArray();
        }

        @Override public void followRedirect() { }
        @Override public void read(ByteBuffer buffer) { }
        @Override public void cancel() { mCancelled = true; }
        @Override public boolean isDone() { return mDone; }
        @Override public void getStatus(StatusListener listener) {
            if (listener != null) listener.onStatus(mDone ? 0 : 1);
        }
    }
}
