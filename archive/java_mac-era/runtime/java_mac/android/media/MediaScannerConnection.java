// android.media.MediaScannerConnection -- asks the system MediaScanner to index
// a file into the MediaStore. AOSP frameworks/base
// media/java/android/media/MediaScannerConnection.java.
//
// Mac.apk has no MediaStore/MediaScanner service. The faithful no-op contract:
// connect()/disconnect() succeed, scanFile() immediately reports the file as
// scanned with a null content Uri (no provider), and the static scanFile(...)
// helper invokes the caller's listener once per path so gallery/save flows
// proceed. Without this, scanFile threw NoSuchMethodError on save.
package android.media;

public class MediaScannerConnection implements android.content.ServiceConnection {
    public interface MediaScannerConnectionClient extends OnScanCompletedListener {
        void onMediaScannerConnected();
    }
    public interface OnScanCompletedListener {
        void onScanCompleted(String path, android.net.Uri uri);
    }

    private final MediaScannerConnectionClient mClient;

    public MediaScannerConnection(android.content.Context context,
                                  MediaScannerConnectionClient client) {
        mClient = client;
    }

    public void connect() {
        // No service to bind; report connected immediately so the client's
        // onMediaScannerConnected -> scanFile chain runs.
        if (mClient != null) {
            try { mClient.onMediaScannerConnected(); } catch (Throwable ignore) {}
        }
    }
    public void disconnect() {}

    // AOSP's MediaScannerConnection IS the ServiceConnection it passes to
    // bindService, and it declares both callbacks publicly (android16.txt:
    // "public class MediaScannerConnection implements android.content
    // .ServiceConnection"). An app that hands this object somewhere expecting a
    // ServiceConnection -- or that subclasses it and calls super -- needs the
    // type to be there. Nothing binds here (there is no MediaScanner service to
    // bind to, see the class comment), so neither callback is ever delivered by
    // the platform; they still forward to the client the way AOSP's do, so a
    // caller that invokes them directly gets AOSP's observable behaviour.
    @Override
    public void onServiceConnected(android.content.ComponentName className,
            android.os.IBinder service) {
        if (mClient != null) {
            try { mClient.onMediaScannerConnected(); } catch (Throwable ignore) { }
        }
    }

    @Override
    public void onServiceDisconnected(android.content.ComponentName className) {
        // AOSP just drops its IMediaScanner reference here; there is none to drop.
    }
    public boolean isConnected() { return true; }

    public void scanFile(String path, String mimeType) {
        if (mClient != null) {
            try { mClient.onScanCompleted(path, null); } catch (Throwable ignore) {}
        }
    }

    // Static convenience: connect, scan each path, invoke the callback, disconnect.
    public static void scanFile(android.content.Context context, String[] paths,
                                String[] mimeTypes, final OnScanCompletedListener callback) {
        if (paths == null) return;
        for (final String path : paths) {
            if (callback != null) {
                try { callback.onScanCompleted(path, null); } catch (Throwable ignore) {}
            }
        }
    }
}
