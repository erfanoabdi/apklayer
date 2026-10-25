package android.net.http;
/** Sink an UploadDataProvider writes body bytes into. Source: frameworks/base .../net/http/UploadDataSink.java */
public abstract class UploadDataSink {
    public abstract void onReadSucceeded(boolean finalChunk);
    public abstract void onReadError(Exception exception);
    public abstract void onRewindSucceeded();
    public abstract void onRewindError(Exception exception);
}
