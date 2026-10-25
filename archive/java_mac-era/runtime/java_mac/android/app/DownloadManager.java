// android.app.DownloadManager
//
// AOSP's system download service: enqueue a long-running HTTP(S) fetch, poll
// its progress through a Cursor, and receive ACTION_DOWNLOAD_COMPLETE when it
// reaches a terminal state.
//
// AOSP ref: frameworks/base/core/java/android/app/DownloadManager.java
// (the client) plus packages/providers/DownloadProvider (the backend that
// actually performs the transfer and writes the status rows).
//
// v1.0.502: this class used to be surface-only. enqueue() handed back an id
// and started nothing, query() always returned an empty Cursor, and every
// Request setter discarded its argument, so an app that enqueued a download
// and waited on the completion broadcast waited forever with no error to
// notice. Since there is no DownloadProvider process to talk to, Mac.apk IS
// the backend: the transfer runs on a bounded worker pool using JDK 21's
// java.net.http.HttpClient (no new dependency), the id -> status table lives
// in this process, and the completion broadcast is posted to the main looper
// so receivers run on the thread AOSP runs them on.
//
// The download table is static because AOSP's is process-external: two
// getSystemService(DOWNLOAD_SERVICE) calls return different DownloadManager
// instances that must still see the same downloads.
package android.app;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public class DownloadManager {

    public static final String ACTION_DOWNLOAD_COMPLETE =
        "android.intent.action.DOWNLOAD_COMPLETE";
    public static final String ACTION_NOTIFICATION_CLICKED =
        "android.intent.action.DOWNLOAD_NOTIFICATION_CLICKED";
    public static final String ACTION_VIEW_DOWNLOADS =
        "android.intent.action.VIEW_DOWNLOADS";
    public static final String EXTRA_DOWNLOAD_ID = "extra_download_id";
    public static final String EXTRA_NOTIFICATION_CLICK_DOWNLOAD_IDS =
        "extra_click_download_ids";
    public static final String INTENT_EXTRAS_SORT_BY_SIZE =
        "android.app.DownloadManager.extra_sortBySize";

    // Column names are the literal strings the provider exposes; apps look
    // them up with Cursor.getColumnIndex(DownloadManager.COLUMN_X), so the
    // values matter, not just the constants' presence.
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_TITLE = "title";
    public static final String COLUMN_DESCRIPTION = "description";
    public static final String COLUMN_URI = "uri";
    public static final String COLUMN_MEDIA_TYPE = "media_type";
    public static final String COLUMN_TOTAL_SIZE_BYTES = "total_size";
    public static final String COLUMN_LOCAL_URI = "local_uri";
    /** @deprecated AOSP steers apps to ContentResolver.openFileDescriptor. */
    @Deprecated
    public static final String COLUMN_LOCAL_FILENAME = "local_filename";
    public static final String COLUMN_STATUS = "status";
    public static final String COLUMN_REASON = "reason";
    public static final String COLUMN_BYTES_DOWNLOADED_SO_FAR = "bytes_so_far";
    public static final String COLUMN_LAST_MODIFIED_TIMESTAMP = "last_modified_timestamp";
    public static final String COLUMN_MEDIAPROVIDER_URI = "mediaprovider_uri";

    public static final int STATUS_PENDING     = 1 << 0;
    public static final int STATUS_RUNNING     = 1 << 1;
    public static final int STATUS_PAUSED      = 1 << 2;
    public static final int STATUS_SUCCESSFUL  = 1 << 3;
    public static final int STATUS_FAILED      = 1 << 4;

    public static final int ERROR_UNKNOWN = 1000;
    public static final int ERROR_FILE_ERROR = 1001;
    public static final int ERROR_UNHANDLED_HTTP_CODE = 1002;
    public static final int ERROR_HTTP_DATA_ERROR = 1004;
    public static final int ERROR_TOO_MANY_REDIRECTS = 1005;
    public static final int ERROR_INSUFFICIENT_SPACE = 1006;
    public static final int ERROR_DEVICE_NOT_FOUND = 1007;
    public static final int ERROR_CANNOT_RESUME = 1008;
    public static final int ERROR_FILE_ALREADY_EXISTS = 1009;
    public static final int ERROR_BLOCKED = 1010;

    public static final int PAUSED_WAITING_TO_RETRY = 1;
    public static final int PAUSED_WAITING_FOR_NETWORK = 2;
    public static final int PAUSED_QUEUED_FOR_WIFI = 3;
    public static final int PAUSED_UNKNOWN = 4;

    private static final String[] QUERY_COLUMNS = {
        COLUMN_ID, COLUMN_TITLE, COLUMN_DESCRIPTION, COLUMN_URI, COLUMN_MEDIA_TYPE,
        COLUMN_TOTAL_SIZE_BYTES, COLUMN_LOCAL_URI, COLUMN_LOCAL_FILENAME,
        COLUMN_STATUS, COLUMN_REASON, COLUMN_BYTES_DOWNLOADED_SO_FAR,
        COLUMN_LAST_MODIFIED_TIMESTAMP, COLUMN_MEDIAPROVIDER_URI
    };

    /** One row of the download table. Fields written by a worker thread and
     *  read by query() on the caller's thread, hence volatile. */
    private static final class Entry {
        final long id;
        final Uri uri;
        final boolean explicitDestination;
        final String title;
        final String description;
        final List<String[]> headers;
        final boolean visibleInDownloadsUi;
        final boolean scannable;
        /** Not final: when the manager picked the name itself it may have to
         *  disambiguate against an existing file, and COLUMN_LOCAL_URI must
         *  report where the bytes actually went. */
        volatile File destination;
        volatile String mimeType;
        volatile int status = STATUS_PENDING;
        volatile int reason;
        volatile long totalBytes = -1;
        volatile long bytesSoFar;
        volatile long lastModified = System.currentTimeMillis();
        volatile boolean cancelled;

        Entry(long id, Uri uri, File destination, boolean explicitDestination, Request r) {
            this.id = id;
            this.uri = uri;
            this.destination = destination;
            this.explicitDestination = explicitDestination;
            this.title = r == null || r.mTitle == null ? null : r.mTitle.toString();
            this.description = r == null || r.mDescription == null ? null : r.mDescription.toString();
            // Copied, not aliased: the caller still owns the Request and may
            // keep mutating it after enqueue().
            this.headers = r == null ? new ArrayList<String[]>()
                                     : new ArrayList<String[]>(r.mRequestHeaders);
            this.visibleInDownloadsUi = r == null || r.mIsVisibleInDownloadsUi;
            this.scannable = r != null && r.mScannable;
            this.mimeType = r == null ? null : r.mMimeType;
        }
    }

    /** Insertion-ordered so an unfiltered query() lists downloads in the order
     *  they were enqueued before the order-by is applied. */
    private static final Map<Long, Entry> sDownloads = new LinkedHashMap<Long, Entry>();
    private static final AtomicLong sNextId = new AtomicLong(1);

    /** AOSP's DownloadProvider runs a bounded number of transfers at once and
     *  leaves the rest in STATUS_PENDING; a bounded pool with an unbounded
     *  queue reproduces that exactly. */
    private static final ExecutorService sExecutor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "macapk-download");
        t.setDaemon(true);
        return t;
    });

    private static volatile HttpClient sHttp;

    private final Context mContext;

    public DownloadManager() { mContext = null; }
    public DownloadManager(Context context) { mContext = context; }

    /** AOSP: hand the request to the download backend and return the id that
     *  identifies it in query() / remove() / the completion broadcast. */
    public long enqueue(Request request) {
        if (request == null) throw new NullPointerException("request cannot be null");
        final long id = sNextId.getAndIncrement();
        final boolean explicit = request.mDestinationUri != null;
        final File dest = resolveDestination(request);
        final Entry e = new Entry(id, request.mUri, dest, explicit, request);
        synchronized (sDownloads) {
            sDownloads.put(id, e);
        }
        final Context ctx = mContext;
        sExecutor.execute(() -> runDownload(e, ctx));
        return id;
    }

    /** AOSP: cancel the downloads and delete their files; returns how many
     *  rows were actually removed. */
    public int remove(long... ids) {
        if (ids == null || ids.length == 0) {
            throw new IllegalArgumentException("input param 'ids' can't be null");
        }
        int removed = 0;
        for (long id : ids) {
            Entry e;
            synchronized (sDownloads) {
                e = sDownloads.remove(id);
            }
            if (e == null) continue;
            removed++;
            e.cancelled = true;
            // Only delete what we created. A file registered through
            // addCompletedDownload existed before us and is not ours to unlink.
            if (e.uri != null && e.destination != null) {
                try { e.destination.delete(); } catch (SecurityException ignored) {}
            }
        }
        return removed;
    }

    public Cursor query(Query query) {
        final List<Entry> rows;
        synchronized (sDownloads) {
            rows = new ArrayList<Entry>(sDownloads.values());
        }
        if (query != null) query.filter(rows);

        final MatrixCursor c = new MatrixCursor(QUERY_COLUMNS);
        for (Entry e : rows) {
            final String localUri = e.destination == null
                    ? null : Uri.fromFile(e.destination).toString();
            c.addRow(new Object[] {
                e.id,
                e.title,
                e.description,
                e.uri == null ? null : e.uri.toString(),
                e.mimeType,
                e.totalBytes,
                localUri,
                e.destination == null ? null : e.destination.getAbsolutePath(),
                e.status,
                e.reason,
                e.bytesSoFar,
                e.lastModified,
                null
            });
        }
        return c;
    }

    /** AOSP's projection-limited overload. Our Cursor is materialized in
     *  memory, so the full row set answers any projection. */
    public Cursor query(Query query, String[] projection) { return query(query); }

    /** AOSP returns a content:// Uri into the downloads provider, which the
     *  app then opens through ContentResolver. Mac.apk has no downloads
     *  provider, so the honest reachable answer is the file: Uri of what we
     *  actually wrote; ContentResolver.openInputStream/openOutputStream here
     *  handle the file: scheme, so callers that follow the AOSP idiom still
     *  get bytes. Null unless the download succeeded, per AOSP. */
    public Uri getUriForDownloadedFile(long id) {
        final Entry e = find(id);
        if (e == null || e.status != STATUS_SUCCESSFUL || e.destination == null) return null;
        return Uri.fromFile(e.destination);
    }

    public String getMimeTypeForDownloadedFile(long id) {
        final Entry e = find(id);
        return e == null ? null : e.mimeType;
    }

    /** AOSP: open a completed download read-only. */
    public android.os.ParcelFileDescriptor openDownloadedFile(long id)
            throws java.io.FileNotFoundException {
        final Entry e = find(id);
        if (e == null || e.status != STATUS_SUCCESSFUL || e.destination == null) {
            throw new java.io.FileNotFoundException("No downloaded file with id " + id);
        }
        return android.os.ParcelFileDescriptor.open(
                e.destination, android.os.ParcelFileDescriptor.MODE_READ_ONLY);
    }

    /**
     * AOSP: register a file the app downloaded itself so it shows up in the
     * downloads list and can be queried by id like any other download.
     *
     * @deprecated AOSP steers apps to MediaStore.Downloads.
     */
    @Deprecated
    public long addCompletedDownload(String title, String description,
            boolean isMediaScannerScannable, String mimeType, String path, long length,
            boolean showNotification) {
        return addCompletedDownload(title, description, isMediaScannerScannable, mimeType, path,
                length, showNotification, false, null, null);
    }

    /** AOSP addCompletedDownload(..., Uri uri, Uri referer) -- the 9-argument
     *  public overload (DownloadManager.java:1498-1503):
     *  {@code return addCompletedDownload(title, description,
     *  isMediaScannerScannable, mimeType, path, length, showNotification, false,
     *  uri, referer);} verbatim. It sits between the 7-argument form above and
     *  the 10-argument one below, both of which already existed here.
     *  @deprecated matches AOSP. */
    @Deprecated
    public long addCompletedDownload(String title, String description,
            boolean isMediaScannerScannable, String mimeType, String path, long length,
            boolean showNotification, Uri uri, Uri referer) {
        return addCompletedDownload(title, description, isMediaScannerScannable, mimeType, path,
                length, showNotification, false, uri, referer);
    }

    /** @deprecated see the 7-argument overload. */
    @Deprecated
    public long addCompletedDownload(String title, String description,
            boolean isMediaScannerScannable, String mimeType, String path, long length,
            boolean showNotification, boolean allowWrite, Uri uri, Uri referer) {
        validateArgumentIsNonEmpty("title", title);
        validateArgumentIsNonEmpty("description", description);
        validateArgumentIsNonEmpty("path", path);
        validateArgumentIsNonEmpty("mimeType", mimeType);
        if (length < 0) {
            throw new IllegalArgumentException(" invalid value for param: totalBytes");
        }

        final long id = sNextId.getAndIncrement();
        final Request r = uri != null ? new Request(uri) : null;
        // The source Uri stays null for a file we did not fetch, which is also
        // what marks it as "not ours to delete" in remove().
        final Entry e = new Entry(id, null, new File(path), true, r);
        e.mimeType = mimeType;
        e.status = STATUS_SUCCESSFUL;
        e.totalBytes = length;
        e.bytesSoFar = length;
        synchronized (sDownloads) {
            sDownloads.put(id, e);
        }
        return id;
    }

    private static void validateArgumentIsNonEmpty(String paramName, String val) {
        if (val == null || val.isEmpty()) {
            throw new IllegalArgumentException(paramName + " can't be null");
        }
    }

    private static Entry find(long id) {
        synchronized (sDownloads) { return sDownloads.get(id); }
    }

    /** AOSP with no explicit destination hands the file to the download
     *  manager's own managed storage, reachable only through the returned
     *  content:// Uri. The public Downloads directory is the equivalent
     *  user-visible location here, and unlike a private cache it keeps
     *  getUriForDownloadedFile() useful. */
    private static File resolveDestination(Request r) {
        if (r.mDestinationUri != null) {
            final String p = r.mDestinationUri.getPath();
            if (p != null && !p.isEmpty()) return new File(p);
        }
        final File dir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS);
        return new File(dir, guessFileName(r.mUri));
    }

    /** The last path segment of the source URI, which is what AOSP's
     *  URLUtil.guessFileName falls back to once the Content-Disposition and
     *  Content-Type hints are unavailable (they are, at enqueue time). */
    private static String guessFileName(Uri uri) {
        String name = uri == null ? null : uri.getLastPathSegment();
        if (name != null) {
            final int q = name.indexOf('?');
            if (q >= 0) name = name.substring(0, q);
            name = name.trim();
        }
        if (name == null || name.isEmpty()) return "downloadfile";
        // Keep the name a single path component; a segment can never contain
        // a separator on Android, but a hand-built Uri could.
        return name.replace('/', '_');
    }

    private static HttpClient http() {
        HttpClient c = sHttp;
        if (c == null) {
            synchronized (DownloadManager.class) {
                c = sHttp;
                if (c == null) {
                    c = sHttp = HttpClient.newBuilder()
                            .followRedirects(HttpClient.Redirect.NORMAL)
                            .connectTimeout(Duration.ofSeconds(30))
                            .build();
                }
            }
        }
        return c;
    }

    private static void runDownload(Entry e, Context ctx) {
        e.status = STATUS_RUNNING;
        e.lastModified = System.currentTimeMillis();
        try {
            // AOSP fails an explicitly-placed download rather than clobbering
            // the caller's file; a manager-chosen name is ours to disambiguate.
            if (e.explicitDestination && e.destination.exists()) {
                finish(e, STATUS_FAILED, ERROR_FILE_ALREADY_EXISTS, ctx);
                return;
            }
            final File dest = e.explicitDestination ? e.destination : uniquify(e.destination);
            e.destination = dest;
            final File parent = dest.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                finish(e, STATUS_FAILED, ERROR_FILE_ERROR, ctx);
                return;
            }

            final HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(e.uri.toString())).GET();
            for (String[] h : e.headers) b.header(h[0], h[1]);

            final HttpResponse<InputStream> resp =
                    http().send(b.build(), HttpResponse.BodyHandlers.ofInputStream());
            final int code = resp.statusCode();
            if (code < 200 || code > 299) {
                // AOSP COLUMN_REASON: "If an HTTP error occurred, this will
                // hold the HTTP status code"; anything else that is not a
                // recognized error is ERROR_UNHANDLED_HTTP_CODE.
                finish(e, STATUS_FAILED, (code >= 400 && code < 600) ? code
                        : ERROR_UNHANDLED_HTTP_CODE, ctx);
                return;
            }

            e.totalBytes = resp.headers().firstValueAsLong("content-length").orElse(-1L);
            if (e.mimeType == null) {
                final String ct = resp.headers().firstValue("content-type").orElse(null);
                if (ct != null) {
                    final int semi = ct.indexOf(';');
                    e.mimeType = (semi < 0 ? ct : ct.substring(0, semi)).trim();
                }
            }

            long soFar = 0;
            try (InputStream in = resp.body(); OutputStream out = new FileOutputStream(dest)) {
                final byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) {
                    if (e.cancelled) {
                        // remove() already dropped the row; leave no partial file.
                        try { out.close(); } catch (IOException ignored) {}
                        dest.delete();
                        return;
                    }
                    out.write(buf, 0, n);
                    soFar += n;
                    e.bytesSoFar = soFar;
                }
            }
            if (e.totalBytes < 0) e.totalBytes = soFar;
            finish(e, STATUS_SUCCESSFUL, 0, ctx);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            finish(e, STATUS_FAILED, ERROR_UNKNOWN, ctx);
        } catch (IOException ioe) {
            finish(e, STATUS_FAILED, ERROR_HTTP_DATA_ERROR, ctx);
        } catch (RuntimeException re) {
            finish(e, STATUS_FAILED, ERROR_UNKNOWN, ctx);
        }
    }

    /** Append "-1", "-2", ... the way the download manager disambiguates a
     *  name it chose itself. */
    private static File uniquify(File f) {
        if (!f.exists()) return f;
        final String name = f.getName();
        final int dot = name.lastIndexOf('.');
        final String stem = dot > 0 ? name.substring(0, dot) : name;
        final String ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 1; i < 1000; i++) {
            final File c = new File(f.getParentFile(), stem + "-" + i + ext);
            if (!c.exists()) return c;
        }
        return f;
    }

    private static void finish(Entry e, int status, int reason, Context ctx) {
        e.status = status;
        e.reason = reason;
        e.lastModified = System.currentTimeMillis();
        if (e.cancelled || ctx == null) return;
        // AOSP broadcasts the completion for every terminal state, success or
        // failure; the receiver queries COLUMN_STATUS to tell them apart.
        final Intent i = new Intent(ACTION_DOWNLOAD_COMPLETE);
        i.putExtra(EXTRA_DOWNLOAD_ID, e.id);
        try { i.setPackage(ctx.getPackageName()); } catch (Throwable ignored) {}
        // Broadcast receivers run on the main thread on Android; delivering
        // straight from this worker would run app code that expects the UI
        // thread off it.
        try {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(
                    () -> ctx.sendBroadcast(i));
        } catch (Throwable t) {
            ctx.sendBroadcast(i);
        }
    }

    /** AOSP DownloadManager.Request: the builder describing one download. */
    public static class Request {
        public static final int NETWORK_MOBILE = 1 << 0;
        public static final int NETWORK_WIFI   = 1 << 1;
        public static final int NETWORK_BLUETOOTH = 1 << 2;
        public static final int VISIBILITY_VISIBLE = 0;
        public static final int VISIBILITY_VISIBLE_NOTIFY_COMPLETED = 1;
        public static final int VISIBILITY_HIDDEN = 2;
        public static final int VISIBILITY_VISIBLE_NOTIFY_ONLY_COMPLETION = 3;

        private final Uri mUri;
        private Uri mDestinationUri;
        private final List<String[]> mRequestHeaders = new ArrayList<String[]>();
        private CharSequence mTitle;
        private CharSequence mDescription;
        private String mMimeType;
        private int mAllowedNetworkTypes = ~0;   // AOSP default: all types allowed
        private boolean mRoamingAllowed = true;
        private boolean mMeteredAllowed = true;
        private boolean mIsVisibleInDownloadsUi = true;
        private boolean mScannable = false;
        private boolean mRequiresCharging = false;
        private boolean mRequiresDeviceIdle = false;
        private int mNotificationVisibility = VISIBILITY_VISIBLE;

        /** AOSP rejects anything but http/https here, so an app that would
         *  crash on a real device crashes the same way instead of silently
         *  enqueuing a download that can never run. */
        public Request(Uri uri) {
            if (uri == null) {
                throw new NullPointerException();
            }
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equals("http") && !scheme.equals("https"))) {
                throw new IllegalArgumentException("Can only download HTTP/HTTPS URIs: " + uri);
            }
            mUri = uri;
        }

        public Request setTitle(CharSequence title) { mTitle = title; return this; }
        public Request setDescription(CharSequence description) { mDescription = description; return this; }
        public Request setMimeType(String mimeType) { mMimeType = mimeType; return this; }
        public Request setDestinationUri(Uri uri) { mDestinationUri = uri; return this; }

        public Request setDestinationInExternalFilesDir(Context context, String dirType,
                String subPath) {
            final File file = context.getExternalFilesDir(dirType);
            if (file == null) {
                throw new IllegalStateException("Failed to get external storage files directory");
            } else if (file.exists()) {
                if (!file.isDirectory()) {
                    throw new IllegalStateException(file.getAbsolutePath() +
                            " already exists and is not a directory");
                }
            } else {
                if (!file.mkdirs()) {
                    throw new IllegalStateException("Unable to create directory: " +
                            file.getAbsolutePath());
                }
            }
            setDestinationFromBase(file, subPath);
            return this;
        }

        public Request setDestinationInExternalPublicDir(String dirType, String subPath) {
            final File file = Environment.getExternalStoragePublicDirectory(dirType);
            if (file == null) {
                throw new IllegalStateException("Failed to get external storage public directory");
            } else if (file.exists()) {
                if (!file.isDirectory()) {
                    throw new IllegalStateException(file.getAbsolutePath() +
                            " already exists and is not a directory");
                }
            } else {
                if (!file.mkdirs()) {
                    throw new IllegalStateException("Unable to create directory: " +
                            file.getAbsolutePath());
                }
            }
            setDestinationFromBase(file, subPath);
            return this;
        }

        private void setDestinationFromBase(File base, String subPath) {
            if (subPath == null) {
                throw new NullPointerException("subPath cannot be null");
            }
            mDestinationUri = Uri.withAppendedPath(Uri.fromFile(base), subPath);
        }

        /** AOSP marks the finished file for the media scanner. Mac.apk has no
         *  MediaStore to insert into, so the flag is recorded and nothing is
         *  scanned; the file itself is written to the requested location
         *  either way, which is the part apps observe.
         *  @deprecated AOSP steers apps to MediaStore.Downloads. */
        @Deprecated
        public void allowScanningByMediaScanner() { mScannable = true; }

        public Request addRequestHeader(String header, String value) {
            if (header == null) {
                throw new NullPointerException("header cannot be null");
            }
            if (header.contains(":")) {
                throw new IllegalArgumentException("header may not contain ':'");
            }
            if (value == null) {
                value = "";
            }
            mRequestHeaders.add(new String[] { header, value });
            return this;
        }

        public Request setAllowedNetworkTypes(int flags) { mAllowedNetworkTypes = flags; return this; }
        public Request setAllowedOverMetered(boolean allow) { mMeteredAllowed = allow; return this; }
        public Request setAllowedOverRoaming(boolean allow) { mRoamingAllowed = allow; return this; }
        public Request setNotificationVisibility(int visibility) { mNotificationVisibility = visibility; return this; }
        public Request setVisibleInDownloadsUi(boolean isVisible) { mIsVisibleInDownloadsUi = isVisible; return this; }
        public Request setRequiresCharging(boolean requiresCharging) { mRequiresCharging = requiresCharging; return this; }
        public Request setRequiresDeviceIdle(boolean requiresDeviceIdle) { mRequiresDeviceIdle = requiresDeviceIdle; return this; }

        /** @deprecated AOSP: use setNotificationVisibility(int). */
        @Deprecated
        public Request setShowRunningNotification(boolean show) {
            return show ? setNotificationVisibility(VISIBILITY_VISIBLE)
                        : setNotificationVisibility(VISIBILITY_HIDDEN);
        }

        public Uri getUri() { return mUri; }
    }

    /** AOSP DownloadManager.Query: the filter + ordering applied by query(). */
    public static class Query {
        public static final int ORDER_ASCENDING  = 1;
        public static final int ORDER_DESCENDING = 2;

        private long[] mIds;
        private Integer mStatusFlags;
        private String mFilterString;
        private Boolean mOnlyIncludeVisibleInDownloadsUi;
        // AOSP orders by last-modified descending unless told otherwise.
        private String mOrderByColumn = COLUMN_LAST_MODIFIED_TIMESTAMP;
        private int mOrderDirection = ORDER_DESCENDING;

        public Query setFilterById(long... ids) { mIds = ids; return this; }
        public Query setFilterByString(String filter) { mFilterString = filter; return this; }
        public Query setFilterByStatus(int flags) { mStatusFlags = flags; return this; }

        public Query setOnlyIncludeVisibleInDownloadsUi(boolean value) {
            mOnlyIncludeVisibleInDownloadsUi = value;
            return this;
        }

        /** AOSP accepts only the last-modified and total-size columns here and
         *  throws for anything else. */
        public Query orderBy(String column, int direction) {
            if (direction != ORDER_ASCENDING && direction != ORDER_DESCENDING) {
                throw new IllegalArgumentException("Invalid direction: " + direction);
            }
            if (!COLUMN_LAST_MODIFIED_TIMESTAMP.equals(column)
                    && !COLUMN_TOTAL_SIZE_BYTES.equals(column)) {
                throw new IllegalArgumentException("Cannot order by " + column);
            }
            mOrderByColumn = column;
            mOrderDirection = direction;
            return this;
        }

        /** Not an AOSP method name (upstream is orderBy); kept because it has
         *  been on this class since v1.0.92 and costs nothing. */
        public Query setOrderBy(String column, int direction) { return orderBy(column, direction); }

        void filter(List<Entry> rows) {
            if (mIds != null) {
                rows.removeIf(e -> {
                    for (long id : mIds) if (id == e.id) return false;
                    return true;
                });
            }
            if (mStatusFlags != null) {
                final int flags = mStatusFlags;
                rows.removeIf(e -> (e.status & flags) == 0);
            }
            if (mFilterString != null && !mFilterString.isEmpty()) {
                final String needle = mFilterString.toLowerCase();
                rows.removeIf(e -> !contains(e.title, needle) && !contains(e.description, needle));
            }
            if (mOnlyIncludeVisibleInDownloadsUi != null) {
                final boolean want = mOnlyIncludeVisibleInDownloadsUi;
                rows.removeIf(e -> e.visibleInDownloadsUi != want);
            }
            final boolean bySize = COLUMN_TOTAL_SIZE_BYTES.equals(mOrderByColumn);
            Comparator<Entry> cmp = (a, b) -> Long.compare(
                    bySize ? a.totalBytes : a.lastModified,
                    bySize ? b.totalBytes : b.lastModified);
            if (mOrderDirection == ORDER_DESCENDING) cmp = cmp.reversed();
            rows.sort(cmp);
        }

        private static boolean contains(String hay, String lowerNeedle) {
            return hay != null && hay.toLowerCase().contains(lowerNeedle);
        }
    }

    /** AOSP getMaxBytesOverMobile(Context): reads
     *  Settings.Global.DOWNLOAD_MAX_BYTES_OVER_MOBILE and returns NULL when the
     *  setting is absent -- AOSP's documented "there's no limit". No such
     *  setting exists on macOS, and no limit is the truthful answer: our
     *  transfers go over whatever interface the host has, which we do not
     *  meter. Returning a number would make an app refuse a download for a
     *  cellular cap that does not exist. */
    public static Long getMaxBytesOverMobile(android.content.Context context) { return null; }

    /** AOSP getRecommendedMaxBytesOverMobile(Context): same shape, reading
     *  DOWNLOAD_RECOMMENDED_MAX_BYTES_OVER_MOBILE; null means "no recommended
     *  limit". */
    public static Long getRecommendedMaxBytesOverMobile(android.content.Context context) {
        return null;
    }
}
