package apklayer.host;

import android.app.DownloadManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowDownloadManager;

/**
 * DownloadManager that downloads (PLAN P5.4): Robolectric only records requests. Each enqueued request is fetched
 * on a thread into its destination (default: Download/ in the app's external files dir), the request's status is
 * updated for query(), and ACTION_DOWNLOAD_COMPLETE is broadcast like the system does.
 */
@Implements(DownloadManager.class)
public class ShadowHostDownloadManager extends ShadowDownloadManager {
    @Implementation
    @Override
    protected long enqueue(DownloadManager.Request request) {
        long id = super.enqueue(request);
        ShadowDownloadManager.ShadowRequest shadow = Shadow.extract(request);
        shadow.setStatus(DownloadManager.STATUS_RUNNING);
        Uri source = shadow.getUri(), destination = shadow.getDestination();
        new Thread(() -> download(id, shadow, source, destination), "download-" + id).start();
        return id;
    }

    private static void download(long id, ShadowDownloadManager.ShadowRequest shadow, Uri source, Uri destination) {
        int status = DownloadManager.STATUS_FAILED;
        try {
            File target = destination != null && "file".equals(destination.getScheme()) ? new File(destination.getPath())
                    : new File(RuntimeEnvironment.getApplication().getExternalFilesDir("Download"), source.getLastPathSegment());
            target.getParentFile().mkdirs();
            try (InputStream in = new URL(source.toString()).openStream()) {
                Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            shadow.setTotalSize(target.length());
            shadow.setBytesSoFar(target.length());
            status = DownloadManager.STATUS_SUCCESSFUL;
            System.out.println("[apklayer] download " + id + " -> " + target + " (" + target.length() + " bytes)");
        } catch (IOException | RuntimeException e) {
            System.out.println("[apklayer] download " + id + " failed: " + e);
        }
        shadow.setStatus(status);
        new Handler(Looper.getMainLooper()).post(() -> RuntimeEnvironment.getApplication().sendBroadcast(
                new Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE).putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, id)));
    }
}
