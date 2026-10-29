package hello.system;

import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import java.io.File;

/** DownloadManager / DownloadProvider: a file from -Dhello.url (the runner serves one) is downloaded into the app's files directory and its size checked. */
final class DownloadsCheck implements Check {
    @Override public String run(Context context) throws Exception {
        String url = System.getProperty("hello.url", "http://127.0.0.1:8099/check.bin");
        DownloadManager downloads = context.getSystemService(DownloadManager.class);
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url)).setTitle("check").setDestinationInExternalFilesDir(context, null, "check.bin");
        long id = downloads.enqueue(request);
        for (int i = 0; i < 60; i++) {
            try (Cursor c = downloads.query(new DownloadManager.Query().setFilterById(id))) {
                if (c != null && c.moveToFirst()) {
                    int status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        long size = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                        File file = new File(context.getExternalFilesDir(null), "check.bin");
                        Check.require(file.length() == size && size > 0, "file " + file.length() + " bytes, provider says " + size);
                        downloads.remove(id);
                        return size + " bytes";
                    }
                    if (status == DownloadManager.STATUS_FAILED) throw new AssertionError("download failed, reason " + c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)));
                }
            }
            Thread.sleep(500);
        }
        throw new AssertionError("download did not finish in 30 s");
    }
}
