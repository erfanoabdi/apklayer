// android.provider.MediaStoreProvider -- the real ContentProvider that backs
// the "media" SYSTEM authority (android.provider.MediaStore's
// EXTERNAL_CONTENT_URI family: Images.Media, Video.Media, Audio.Media,
// Files, Downloads).
//
// THE GAP THIS FILLS: ContentProviderRegistry (android.content) is populated
// exactly one way -- com.macapk.loader.Boot.installAutoInitProviders() walks
// the APK's manifest <provider> declarations and registers each one under
// its android:authorities. "media" is never declared by any app manifest
// (on real Android it is baked into the system image by
// PackageManagerService, not by the app), so before this class existed
// ContentProviderRegistry.get("media") always returned null,
// ContentResolver.query(MediaStore....EXTERNAL_CONTENT_URI, ...) always
// returned null, and every gallery / audio / video-picker app rendered an
// empty grid. Measured case: Fossify Gallery (speed run lane F-DB,
// 2026-07-29/30).
//
// ARCHITECTURE: real Android's MediaProvider
// (packages/providers/MediaProvider) is fundamentally a SQLite-backed
// ContentProvider that a MediaScanner keeps in sync with the filesystem;
// query() ultimately becomes a SQLiteQueryBuilder-built SELECT against a
// per-volume "external.db". Mac.apk has no separate scanner process or
// provider host, so this class folds "scanner" and "provider" into one:
// query() (re)syncs a real on-disk SQLite index
// (android.database.sqlite.SQLiteDatabase -- backed by the system
// libsqlite3.dylib via Java 21 FFM, NOT a stub, see SQLiteDatabase.java)
// against whatever is actually present under
// android.os.Environment.getExternalStorageDirectory(), then answers with a
// real SQL SELECT built from the caller's projection/selection/sortOrder.
// This means projection/selection/sortOrder are honoured by SQLite itself,
// exactly the way AOSP honours them, rather than by a hand-rolled
// interpreter that would inevitably diverge from SQL semantics on some
// selection string a real app passes.
//
// Column *names* are taken verbatim from android.provider.MediaStore's own
// column-name constants (same package, no import needed), so the physical
// SQL column names and the Cursor column names an app reads back are
// identical by construction -- there is no name-mapping layer to get wrong.
//
// AOSP refs (fetched 2026-07-30; MediaStore.java lives in the separate
// MediaProvider mirror repo as of the current AOSP `main`, NOT in
// platform_frameworks_base/core/java/android/provider/ where it used to be
// -- confirmed by directory listing before fetching):
//   https://raw.githubusercontent.com/aosp-mirror/platform_packages_providers_mediaprovider/main/apex/framework/java/android/provider/MediaStore.java
//     MediaColumns (interface, ~line 1727): DATA="_data", SIZE="_size",
//     DISPLAY_NAME="_display_name", DATE_ADDED="date_added" (seconds,
//     @CurrentTimeSecondsLong), DATE_MODIFIED="date_modified" (seconds),
//     MIME_TYPE="mime_type", WIDTH="width", HEIGHT="height",
//     RELATIVE_PATH="relative_path", BUCKET_ID="bucket_id",
//     BUCKET_DISPLAY_NAME="bucket_display_name", VOLUME_NAME="volume_name",
//     TITLE="title" (~2190), DURATION="duration" (ms, @DurationMillisLong,
//     ~2205), IS_DOWNLOAD="is_download" (~2061, "Flag indicating if a media
//     item has been marked as being part of the Downloads collection").
//     Files.FileColumns (~2450): MEDIA_TYPE="media_type",
//     MEDIA_TYPE_NONE=0, MEDIA_TYPE_IMAGE=1, MEDIA_TYPE_AUDIO=2,
//     MEDIA_TYPE_VIDEO=3 (~2507-2525).
//     Images.Media.getContentUri (~3152): AUTHORITY_URI/<volume>/images/media.
//     Images.Media.EXTERNAL_CONTENT_URI = getContentUri("external") (~3179).
//   https://raw.githubusercontent.com/aosp-mirror/platform_packages_providers_mediaprovider/main/src/com/android/providers/media/util/FileUtils.java
//     bucket_id/bucket_display_name derivation (~line 1350-1387):
//       "final File fileLower = new File(data.toLowerCase(Locale.ROOT));"
//       "final String parent = fileLower.getParent();"
//       "values.put(MediaColumns.BUCKET_ID, parent.hashCode());"
//       "values.put(MediaColumns.BUCKET_DISPLAY_NAME, file.getParentFile().getName());"
//       i.e. bucket_id = String.hashCode() of the LOWERCASED absolute parent
//       directory path; bucket_display_name = the parent directory's
//       original-case simple name. Mirrored exactly below.
//     isDirectoryHidden() (~line 1587-1629): a directory is hidden if its
//       name starts with "." , or if it contains a ".nomedia" file AND is
//       not one of the top-level default folders (DCIM, Pictures, Movies,
//       Music, Download, Documents, Podcasts, Ringtones, Notifications,
//       Alarms -- DEFAULT_FOLDER_NAMES, ~line 1017) and not "DCIM/Camera"
//       (CAMERA_RELATIVE_PATH, ~line 1070: "DCIM/Camera/", "should always
//       be visible regardless of .nomedia presence"). Mirrored below minus
//       the device-policy (shouldBeVisible) and screenshots-dir special
//       cases, which are out of scope for this pass (noted below).
//   https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/core/java/android/os/FileUtils.java
//     translateModeStringToPosix / translateModePosixToPfd (~line 1537-1613):
//     the exact mode-string -> ParcelFileDescriptor-flags mapping mirrored
//     by parseMode() below ("rw*" -> READ_WRITE|CREATE, "w*" -> WRITE_ONLY|
//     CREATE, else READ_ONLY; 't' adds TRUNCATE; 'a' adds APPEND).
//
// DELIBERATELY NOT DONE this pass (scope cuts, not lies -- every value below
// is either genuinely absent or a documented-honest default, never a faked
// one):
//   - insert()/update()/delete() are NOT overridden (inherited
//     ContentProvider defaults: null/0). Mac.apk's MediaStore.java already
//     documents "Mac.apk exposes no writable media provider" for
//     insertImage(); a real pending-file insert lifecycle is future work.
//   - EXIF (DATE_TAKEN, ORIENTATION, LATITUDE/LONGITUDE) is not parsed;
//     ORIENTATION defaults to 0 (no rotation, a real AOSP value), DATE_TAKEN
//     is simply absent from the schema (an unrequested column reads back an
//     honest NULL via the projection-fallback below, exactly like a real
//     MediaStore row whose EXIF extraction found nothing).
//   - DURATION (audio/video) is not computed (would need
//     MediaMetadataRetriever-grade container parsing); stored as 0.
//   - ⚠ CORRECTED 2026-08-23. This entry used to say the Images.Thumbnails /
//     Video.Thumbnails / Audio.Albums / Audio.Artists / Audio.Playlists
//     sub-tables were "not implemented; a query against one of those URIs
//     throws IllegalArgumentException the same way an unrecognized URI does on
//     real AOSP (ContentResolver already catches Throwable -> null around every
//     provider call, so the observable result is the same null a caller already
//     handles)". BOTH halves of that reasoning were wrong. (1) A URI THIS
//     PACKAGE PUBLISHES is not an "unrecognized URI" -- AOSP's UriMatcher
//     matches every one of them, so the IllegalArgumentException was ours
//     alone. (2) null and an empty Cursor are NOT the same observable result:
//     AOSP's ContentResolver.query returns null only when the provider is
//     ABSENT, so a null told the app "this device has no media provider" when
//     the truth was "it has one and there are no matching rows", and apps
//     branch very differently on the two. Worse, the note made
//     ContentResolver's catch(Throwable) load-bearing for correctness, so
//     narrowing that catch to AOSP's RemoteException set (which happened in the
//     same pass, see ContentResolver.java) would have turned every one of these
//     into an app-visible crash. All of those URIs are now ROUTED -- see the
//     AUDIO SUB-TABLE VIEWS block -- and the IllegalArgumentException is
//     reserved for URIs that really are unknown.
//   - isDirectoryHidden()'s device-policy (shouldBeVisible) and
//     screenshots-directory carve-outs are not mirrored (narrow polish, not
//     load-bearing for "the gallery is no longer empty").
package android.provider;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class MediaStoreProvider extends android.content.ContentProvider {
    private static final String TAG = "Mac.apk.MediaStoreProvider";

    // ---- physical schema (column names are MediaStore's own constants) --
    private static final String TABLE = "files";
    /** Bump on ANY column add/change -- onCreate drops and re-scans on mismatch. */
    private static final int SCHEMA_VERSION = 3;   // 2: + datetaken; 3: + AudioColumns/playlists

    private static final String COL_ID          = BaseColumns._ID;
    private static final String COL_DATA         = MediaStore.MediaColumns.DATA;
    private static final String COL_DISPLAY_NAME = MediaStore.MediaColumns.DISPLAY_NAME;
    private static final String COL_TITLE        = MediaStore.MediaColumns.TITLE;
    private static final String COL_SIZE         = MediaStore.MediaColumns.SIZE;
    private static final String COL_DATE_ADDED   = MediaStore.MediaColumns.DATE_ADDED;
    private static final String COL_DATE_MOD     = MediaStore.MediaColumns.DATE_MODIFIED;
    private static final String COL_MIME_TYPE    = MediaStore.MediaColumns.MIME_TYPE;
    private static final String COL_MEDIA_TYPE   = MediaStore.Files.FileColumns.MEDIA_TYPE;
    private static final String COL_WIDTH        = MediaStore.MediaColumns.WIDTH;
    private static final String COL_HEIGHT       = MediaStore.MediaColumns.HEIGHT;
    private static final String COL_DURATION     = MediaStore.MediaColumns.DURATION;
    private static final String COL_ORIENTATION  = MediaStore.MediaColumns.ORIENTATION;
    // DATE_TAKEN. MEASURED as load-bearing on Fossify Gallery: its very FIRST
    // query is `SELECT _id FROM files ORDER BY datetaken DESC LIMIT 1`, and
    // SQLite REJECTS an ORDER BY over a column that does not exist -- so one
    // missing column failed the whole query instead of returning NULLs, and the
    // gallery stayed empty with 9 queries fired and 0 answered. The lesson is
    // narrow and worth keeping: a column an app SORTS or FILTERS by cannot be
    // omitted the way a column it merely READS can. It is populated from the
    // file mtime as an honest lower bound; real EXIF parsing is separate work.
    private static final String COL_DATE_TAKEN   = MediaStore.MediaColumns.DATE_TAKEN;
    private static final String COL_BUCKET_ID    = MediaStore.MediaColumns.BUCKET_ID;
    private static final String COL_BUCKET_NAME  = MediaStore.MediaColumns.BUCKET_DISPLAY_NAME;
    private static final String COL_REL_PATH     = MediaStore.MediaColumns.RELATIVE_PATH;
    private static final String COL_VOLUME_NAME  = MediaStore.MediaColumns.VOLUME_NAME;
    private static final String COL_IS_PENDING   = MediaStore.MediaColumns.IS_PENDING;
    private static final String COL_IS_FAVORITE  = MediaStore.MediaColumns.IS_FAVORITE;
    private static final String COL_IS_TRASHED   = MediaStore.MediaColumns.IS_TRASHED;
    private static final String COL_IS_DOWNLOAD  = MediaStore.MediaColumns.IS_DOWNLOAD;
    private static final String COL_OWNER_PKG    = MediaStore.MediaColumns.OWNER_PACKAGE_NAME;

    // ---- AudioColumns (schema 3) ---------------------------------------
    // These exist so audio/albums, audio/artists and audio/genres can be
    // answered by the SAME GROUP BY aggregations AOSP's DatabaseHelper builds
    // its audio_albums / audio_artists / audio_genres VIEWs from (see the
    // AUDIO SUB-TABLE VIEWS block below for the verbatim SQL). Without them
    // there is nothing to group by, and the routes could only ever return an
    // empty cursor no matter how much audio is on disk.
    //
    // WHAT POPULATES THEM, and why it is not a fabrication: AOSP's
    // ModernMediaScanner.scanItemAudio (main, src/com/android/providers/media/
    // scan/ModernMediaScanner.java:1456-1498, fetched 2026-08-23) sets its
    // filesystem-only baseline BEFORE it ever opens the file:
    //     op.withValue(MediaColumns.ARTIST, UNKNOWN_STRING);
    //     op.withValue(MediaColumns.ALBUM, file.getParentFile().getName());
    //     op.withValue(AudioColumns.TRACK, null);
    //     FileUtils.computeAudioTypeValuesFromData(file.getAbsolutePath(), op::withValue);
    // and only THEN refines artist/album/genre/year from a MediaMetadataRetriever.
    // Mac.apk has no container-tag parser, so it stops after the baseline --
    // which is a real AOSP state (every untagged file on a device ends up
    // there), not an invented one. Everything the retriever would have supplied
    // stays NULL, which reads back as an honest NULL through the
    // projection-fallback in derivedQuery()/safeQuery().
    private static final String COL_ARTIST       = MediaStore.Audio.AudioColumns.ARTIST;
    private static final String COL_ARTIST_KEY   = MediaStore.Audio.AudioColumns.ARTIST_KEY;
    private static final String COL_ARTIST_ID    = MediaStore.Audio.AudioColumns.ARTIST_ID;
    private static final String COL_ALBUM        = MediaStore.Audio.AudioColumns.ALBUM;
    private static final String COL_ALBUM_KEY    = MediaStore.Audio.AudioColumns.ALBUM_KEY;
    private static final String COL_ALBUM_ID     = MediaStore.Audio.AudioColumns.ALBUM_ID;
    private static final String COL_ALBUM_ARTIST = MediaStore.Audio.AudioColumns.ALBUM_ARTIST;
    private static final String COL_TITLE_KEY    = MediaStore.Audio.AudioColumns.TITLE_KEY;
    private static final String COL_GENRE        = MediaStore.Audio.AudioColumns.GENRE;
    private static final String COL_GENRE_KEY    = MediaStore.Audio.AudioColumns.GENRE_KEY;
    private static final String COL_GENRE_ID     = MediaStore.Audio.AudioColumns.GENRE_ID;
    private static final String COL_YEAR         = MediaStore.Audio.AudioColumns.YEAR;
    private static final String COL_TRACK        = MediaStore.Audio.AudioColumns.TRACK;
    private static final String COL_COMPOSER     = MediaStore.Audio.AudioColumns.COMPOSER;
    private static final String COL_IS_MUSIC     = MediaStore.Audio.AudioColumns.IS_MUSIC;
    private static final String COL_IS_PODCAST   = MediaStore.Audio.AudioColumns.IS_PODCAST;
    private static final String COL_IS_RINGTONE  = MediaStore.Audio.AudioColumns.IS_RINGTONE;
    private static final String COL_IS_ALARM     = MediaStore.Audio.AudioColumns.IS_ALARM;
    private static final String COL_IS_NOTIF     = MediaStore.Audio.AudioColumns.IS_NOTIFICATION;
    private static final String COL_IS_AUDIOBOOK = MediaStore.Audio.AudioColumns.IS_AUDIOBOOK;
    private static final String COL_IS_RECORDING = MediaStore.Audio.AudioColumns.IS_RECORDING;
    private static final String COL_BOOKMARK     = MediaStore.Audio.AudioColumns.BOOKMARK;
    /** PlaylistsColumns.NAME -- the one column the audio_playlists view adds. */
    @SuppressWarnings("deprecation")
    private static final String COL_NAME         = MediaStore.Audio.PlaylistsColumns.NAME;

    private static final Set<String> COLUMN_SET = new LinkedHashSet<>(Arrays.asList(
        COL_ID, COL_DATA, COL_DISPLAY_NAME, COL_TITLE, COL_SIZE, COL_DATE_ADDED, COL_DATE_MOD,
        COL_MIME_TYPE, COL_MEDIA_TYPE, COL_WIDTH, COL_HEIGHT, COL_DURATION, COL_ORIENTATION,
        COL_DATE_TAKEN,
        COL_BUCKET_ID, COL_BUCKET_NAME, COL_REL_PATH, COL_VOLUME_NAME, COL_IS_PENDING,
        COL_IS_FAVORITE, COL_IS_TRASHED, COL_IS_DOWNLOAD, COL_OWNER_PKG,
        COL_ARTIST, COL_ARTIST_KEY, COL_ARTIST_ID, COL_ALBUM, COL_ALBUM_KEY, COL_ALBUM_ID,
        COL_ALBUM_ARTIST, COL_TITLE_KEY, COL_GENRE, COL_GENRE_KEY, COL_GENRE_ID,
        COL_YEAR, COL_TRACK, COL_COMPOSER,
        COL_IS_MUSIC, COL_IS_PODCAST, COL_IS_RINGTONE, COL_IS_ALARM, COL_IS_NOTIF,
        COL_IS_AUDIOBOOK, COL_IS_RECORDING, COL_BOOKMARK, COL_NAME));
    private static final String[] DEFAULT_PROJECTION = COLUMN_SET.toArray(new String[0]);

    private static final String CREATE_TABLE_SQL =
        "CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
        COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
        COL_DATA + " TEXT NOT NULL," +
        COL_DISPLAY_NAME + " TEXT," +
        COL_TITLE + " TEXT," +
        COL_SIZE + " INTEGER," +
        COL_DATE_ADDED + " INTEGER," +
        COL_DATE_MOD + " INTEGER," +
        COL_MIME_TYPE + " TEXT," +
        COL_MEDIA_TYPE + " INTEGER," +
        COL_WIDTH + " INTEGER DEFAULT 0," +
        COL_HEIGHT + " INTEGER DEFAULT 0," +
        COL_DURATION + " INTEGER DEFAULT 0," +
        COL_ORIENTATION + " INTEGER DEFAULT 0," +
        COL_DATE_TAKEN + " INTEGER," +
        COL_BUCKET_ID + " INTEGER," +
        COL_BUCKET_NAME + " TEXT," +
        COL_REL_PATH + " TEXT," +
        COL_VOLUME_NAME + " TEXT," +
        COL_IS_PENDING + " INTEGER DEFAULT 0," +
        COL_IS_FAVORITE + " INTEGER DEFAULT 0," +
        COL_IS_TRASHED + " INTEGER DEFAULT 0," +
        COL_IS_DOWNLOAD + " INTEGER DEFAULT 0," +
        COL_OWNER_PKG + " TEXT," +
        COL_ARTIST + " TEXT," +
        COL_ARTIST_KEY + " TEXT," +
        COL_ARTIST_ID + " INTEGER," +
        COL_ALBUM + " TEXT," +
        COL_ALBUM_KEY + " TEXT," +
        COL_ALBUM_ID + " INTEGER," +
        COL_ALBUM_ARTIST + " TEXT," +
        COL_TITLE_KEY + " TEXT," +
        COL_GENRE + " TEXT," +
        COL_GENRE_KEY + " TEXT," +
        COL_GENRE_ID + " INTEGER," +
        COL_YEAR + " INTEGER," +
        COL_TRACK + " INTEGER," +
        COL_COMPOSER + " TEXT," +
        COL_IS_MUSIC + " INTEGER DEFAULT 0," +
        COL_IS_PODCAST + " INTEGER DEFAULT 0," +
        COL_IS_RINGTONE + " INTEGER DEFAULT 0," +
        COL_IS_ALARM + " INTEGER DEFAULT 0," +
        COL_IS_NOTIF + " INTEGER DEFAULT 0," +
        COL_IS_AUDIOBOOK + " INTEGER DEFAULT 0," +
        COL_IS_RECORDING + " INTEGER DEFAULT 0," +
        COL_BOOKMARK + " INTEGER," +
        COL_NAME + " TEXT)";

    // AOSP's audio_playlists_map: which audio rows belong to which playlist
    // row, and in what order. MediaProvider's AUDIO_PLAYLISTS_ID_MEMBERS query
    // is `qb.setTables("audio_playlists_map, audio")` joined on
    // `audio._id = audio_id` (MediaProvider.java:6267-6290, fetched
    // 2026-08-23), which is exactly the join VIEW_PLAYLIST_MEMBERS builds.
    private static final String MAP_TABLE = "audio_playlists_map";
    private static final String COL_AUDIO_ID    = "audio_id";
    private static final String COL_PLAYLIST_ID = "playlist_id";
    private static final String COL_PLAY_ORDER  = "play_order";
    private static final String CREATE_MAP_SQL =
        "CREATE TABLE IF NOT EXISTS " + MAP_TABLE + " (" +
        COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
        COL_AUDIO_ID + " INTEGER NOT NULL," +
        COL_PLAYLIST_ID + " INTEGER NOT NULL," +
        COL_PLAY_ORDER + " INTEGER NOT NULL)";

    // ---- the macOS Photos library (MACAPK_PHOTOKIT) ----------------------
    // Which files rows are Photos assets, keyed by PhotoKit's localIdentifier so
    // an asset keeps its row (_id) and its DCIM/Camera name across syncs, and so
    // a removed asset takes exactly ITS exported copy with it (never a file an app
    // wrote there). `exported` = the copy at `data` was written by us. See
    // the Photos source section at the end of this file and
    // docs/photos-library-mediastore_2026-09-29.md.
    private static final String PHOTOS_TABLE = "macapk_photos";
    private static final String CREATE_PHOTOS_SQL =
        "CREATE TABLE IF NOT EXISTS " + PHOTOS_TABLE + " (" +
        "local_id TEXT PRIMARY KEY," +
        "data TEXT UNIQUE NOT NULL," +
        "modified_ms INTEGER NOT NULL DEFAULT 0," +
        "exported INTEGER NOT NULL DEFAULT 0)";

    // ---- extension -> media kind classification --------------------------
    private static final Set<String> IMAGE_EXT = new HashSet<>(Arrays.asList(
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "tiff", "tif"));
    private static final Set<String> VIDEO_EXT = new HashSet<>(Arrays.asList(
        "mp4", "3gp", "3gpp", "mkv", "webm", "mov", "m4v", "avi", "ts", "mpg", "mpeg"));
    private static final Set<String> AUDIO_EXT = new HashSet<>(Arrays.asList(
        "mp3", "m4a", "aac", "ogg", "oga", "opus", "wav", "flac", "wma", "mid", "midi", "amr"));
    // MEDIA_TYPE_PLAYLIST. Only the two PLAIN-TEXT playlist formats AOSP's
    // PlaylistPersister.resolvePersister handles are listed
    // (src/com/android/providers/media/playlist/{M3u,Pls}PlaylistPersister.java,
    // fetched 2026-08-23). AOSP also handles WPL and XSPF, which are XML; we do
    // not parse those, so they stay MEDIA_TYPE_NONE and never appear in the
    // playlists table at all. That is the honest answer -- listing a playlist
    // whose contents we cannot read would report it as an EMPTY playlist, which
    // is a different and wrong claim.
    private static final Set<String> PLAYLIST_EXT = new HashSet<>(Arrays.asList(
        "m3u", "m3u8", "pls"));

    // AOSP FileUtils.DEFAULT_FOLDER_NAMES (top-level dirs that stay visible
    // even with a .nomedia marker) -- see class header citation.
    private static final Set<String> TOP_LEVEL_VISIBLE = new HashSet<>(Arrays.asList(
        Environment.DIRECTORY_MUSIC, Environment.DIRECTORY_PODCASTS, Environment.DIRECTORY_RINGTONES,
        Environment.DIRECTORY_ALARMS, Environment.DIRECTORY_NOTIFICATIONS, Environment.DIRECTORY_PICTURES,
        Environment.DIRECTORY_MOVIES, Environment.DIRECTORY_DOWNLOADS, Environment.DIRECTORY_DCIM,
        Environment.DIRECTORY_DOCUMENTS));

    private SQLiteDatabase mDb;
    // Photos listing cache: re-listed only when photosGeneration() (the
    // library's change observer) or the granted kinds move.
    private long mPhotosGen = Long.MIN_VALUE;
    private int mPhotosKinds = -1;
    private List<String[]> mPhotosCache;
    private boolean mPhotosPoller;

    public MediaStoreProvider() {}

    // ---- lifecycle ---------------------------------------------------
    @Override
    public boolean onCreate() {
        try {
            File dbFile = dbFile();
            File parent = dbFile.getParentFile();
            if (parent != null) parent.mkdirs();
            mDb = SQLiteDatabase.openOrCreateDatabase(dbFile.getAbsolutePath(), null);
            // SCHEMA VERSIONING. CREATE TABLE IF NOT EXISTS silently keeps an OLD
            // schema, so adding a column would apply only to installs that had
            // never run the provider -- and the bug would present as "works on my
            // machine, empty gallery on yours". This index is a CACHE of the
            // filesystem (nothing here is user data that cannot be rebuilt), so the
            // correct migration is simply to drop and re-scan. BUMP SCHEMA_VERSION
            // whenever a column is added or changed. One thing is not derivable from
            // the volume alone: which DCIM/Camera files are OUR exports of Photos
            // assets (macapk_photos). That record survives an index loss as an xattr
            // on the copy itself (photosOwnerTag), so the drop-and-rebuild is safe
            // for it too.
            int have = 0;
            try {
                android.database.Cursor vc = mDb.rawQuery("PRAGMA user_version", null);
                if (vc != null) {
                    try { if (vc.moveToFirst()) have = vc.getInt(0); } finally { vc.close(); }
                }
            } catch (Throwable ignored) {}
            if (have != SCHEMA_VERSION) {
                com.macapk.loader.NativeLog.i(TAG, "media index schema " + have
                    + " -> " + SCHEMA_VERSION + "; rebuilding (it is a cache, not user data)");
                try { mDb.execSQL("DROP TABLE IF EXISTS " + TABLE); } catch (Throwable ignored) {}
                // The playlist map holds files._id values, so it is meaningless
                // once the files table is rebuilt; drop it in the same step.
                try { mDb.execSQL("DROP TABLE IF EXISTS " + MAP_TABLE); } catch (Throwable ignored) {}
                try { mDb.execSQL("PRAGMA user_version = " + SCHEMA_VERSION); } catch (Throwable ignored) {}
            }
            mDb.execSQL(CREATE_TABLE_SQL);
            mDb.execSQL(CREATE_MAP_SQL);
            if (photosEnabled()) {
                mDb.execSQL(CREATE_PHOTOS_SQL);
                // "is anything still to export" runs on EVERY query that reads bytes.
                mDb.execSQL("CREATE INDEX IF NOT EXISTS idx_photos_pending ON "
                    + PHOTOS_TABLE + "(exported)");
            }
            mDb.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_files_data ON "
                + TABLE + "(" + COL_DATA + ")");
            mDb.execSQL("CREATE INDEX IF NOT EXISTS idx_map_playlist ON "
                + MAP_TABLE + "(" + COL_PLAYLIST_ID + ")");
            return true;
        } catch (Throwable t) {
            com.macapk.loader.NativeLog.w(TAG, "onCreate: failed to open media index: " + t);
            mDb = null;
            return false;
        }
    }

    @Override
    public void shutdown() {
        if (mDb != null) {
            try { mDb.close(); } catch (Throwable ignored) {}
            mDb = null;
        }
    }

    // Same resolution order as Environment.java / SettingsStore.java (each
    // keeps its own copy rather than sharing a helper -- see their header
    // comments; this is a 3-line property/env/fallback read, not shared
    // logic worth a new cross-package utility for). ".macapk-media" mirrors
    // SettingsStore's ".macapk-settings": a dot-prefixed pseudo-package name
    // (real Android package names never start with '.') so a device-global
    // index doesn't collide with any real app's per-package data dir.
    private static File dbFile() {
        String root = System.getProperty("macapk.android.root");
        if (root == null || root.isEmpty()) root = System.getenv("MACAPK_ANDROID_ROOT");
        if (root == null || root.isEmpty()) {
            // Last-ditch dev default; inert in every real run because the
            // runtime always sets -Dmacapk.android.root. Kept in step with
            // ResolveAndroidRootDir (apk_unpacker.cpp:1345). Was
            // "~/Documents/android" until 2026-08-12 -- that shared root is
            // DELETED and A13_NO_LEGACY_ROOT fails if anything recreates it.
            root = System.getProperty("user.home", ".")
                    + "/Library/Caches/Mac.apk/scratch/android";
        }
        if (root.equals("~") || root.startsWith("~/")) {
            String home = System.getProperty("user.home");
            if (home != null && !home.isEmpty()) root = home + root.substring(1);
        }
        return new File(root, "data/data/.macapk-media/external.db");
    }

    // ---- URI routing ---------------------------------------------------
    // AOSP dispatches these shapes via a UriMatcher inside MediaProvider;
    // Mac.apk has one provider instance and a handful of shapes, so a plain
    // path-segment check is the direct equivalent without pulling in
    // UriMatcher for five cases.
    private static final class Route {
        final int mediaTypeFilter; // -1 == no media_type filter (Files/Downloads see everything)
        final boolean downloadsOnly;
        final int prefixLen;       // path segments consumed before an optional trailing id
        final String dirType;
        final String itemTypePrefix;
        Route(int mt, boolean dl, int prefixLen, String dirType, String itemTypePrefix) {
            this.mediaTypeFilter = mt;
            this.downloadsOnly = dl;
            this.prefixLen = prefixLen;
            this.dirType = dirType;
            this.itemTypePrefix = itemTypePrefix;
        }
    }

    // A Route matches ONLY the collection URI and the collection/<id> URI --
    // never a deeper path. That bound is load-bearing: without it
    // audio/media/<id>/genres (a URI MediaStore.Audio.Genres.getContentUriForAudioId
    // publishes) matched the audio/media route, extractId returned null because
    // the segment count was wrong, and the query answered with EVERY audio row
    // instead of that one file's genres. Anything deeper is an audio sub-table
    // and belongs to subFor() below.
    private static Route routeFor(List<String> seg) {
        if (seg.size() < 2) return null;
        String t1 = seg.get(1);
        boolean twoDeep = seg.size() >= 3 && seg.size() <= 4;
        if ("images".equals(t1) && twoDeep && "media".equals(seg.get(2))) {
            return new Route(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE, false, 3,
                MediaStore.Images.Media.CONTENT_TYPE, "vnd.android.cursor.item/image");
        }
        if ("video".equals(t1) && twoDeep && "media".equals(seg.get(2))) {
            return new Route(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO, false, 3,
                MediaStore.Video.Media.CONTENT_TYPE, "vnd.android.cursor.item/video");
        }
        if ("audio".equals(t1) && twoDeep && "media".equals(seg.get(2))) {
            return new Route(MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO, false, 3,
                MediaStore.Audio.Media.CONTENT_TYPE, "vnd.android.cursor.item/audio");
        }
        // audio/playlists is a plain files-table route, not an aggregate: AOSP's
        // audio_playlists VIEW is literally "SELECT <Audio.Playlists columns>
        // FROM files WHERE media_type=4" (DatabaseHelper.java:1567-1569, fetched
        // 2026-08-23), which is exactly what a mediaTypeFilter of
        // MEDIA_TYPE_PLAYLIST produces here.
        if ("audio".equals(t1) && twoDeep && "playlists".equals(seg.get(2))) {
            return new Route(MediaStore.Files.FileColumns.MEDIA_TYPE_PLAYLIST, false, 3,
                MediaStore.Audio.Playlists.CONTENT_TYPE,
                MediaStore.Audio.Playlists.ENTRY_CONTENT_TYPE);
        }
        if ("file".equals(t1) && seg.size() <= 3) {
            return new Route(-1, false, 2,
                "vnd.android.cursor.dir/file", "vnd.android.cursor.item/file");
        }
        if ("downloads".equals(t1) && seg.size() <= 3) {
            return new Route(-1, true, 2,
                MediaStore.Downloads.CONTENT_TYPE, "vnd.android.cursor.item/download");
        }
        return null;
    }

    /** Can this route return an image or video row? Files (any type), Images and
     *  Video can; Audio, playlists and Downloads-only cannot, so a music player or
     *  a downloads list never raises the macOS Photos prompt (which a user who
     *  declines there has declined for that app for good). */
    private static boolean routeMayReturnMedia(Route r) {
        if (r.downloadsOnly) return false;
        return r.mediaTypeFilter < 0
            || r.mediaTypeFilter == MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
            || r.mediaTypeFilter == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;
    }

    private static Long extractId(List<String> seg, Route route) {
        if (seg.size() != route.prefixLen + 1) return null;
        try { return Long.parseLong(seg.get(seg.size() - 1)); }
        catch (NumberFormatException e) { return null; }
    }

    // ---- AUDIO SUB-TABLE VIEWS (schema 3) -------------------------------
    //
    // WHY THIS EXISTS (2026-08-23). android.provider.MediaStore publishes
    // content URIs for audio/albums, audio/artists, audio/genres and
    // audio/playlists, and for images/thumbnails, video/thumbnails and
    // none/media_scanner. routeFor() answered NONE of them, so query() fell
    // through to `throw new IllegalArgumentException("Unknown URI")` -- which
    // ContentResolver's catch(Throwable) then turned into null. An app asking
    // for a URI THIS CLASS ADVERTISES is not making a caller error, and null is
    // not the same answer as an empty Cursor: AOSP's ContentResolver.query
    // returns null only when the PROVIDER IS ABSENT, so null tells the app
    // "there is no media provider on this device" when the truth is "there is
    // one and it has no matching rows". Apps branch very differently on those
    // two. Every URI this file's sibling MediaStore.java can hand out is now
    // routed; the IllegalArgumentException is reserved for URIs that really are
    // unknown, which is AOSP's contract for an unmatched UriMatcher.
    //
    // HOW THEY ARE BACKED. AOSP does not store albums/artists/genres; it derives
    // them with CREATE VIEW ... GROUP BY over the same `files` table
    // (DatabaseHelper.java:1604-1656, fetched 2026-08-23). The SQL below is that
    // view SQL with two mechanical substitutions: `FROM audio` becomes
    // `FROM files WHERE media_type=2` (AOSP's own definition of the audio view,
    // same file:1591-1593), and AOSP's `AND volume_name IN <filterVolumeNames>`
    // is dropped because there is exactly one volume here
    // (VOLUME_EXTERNAL_PRIMARY) and every row carries it, so the predicate is a
    // tautology. Nothing else is changed -- in particular the is_music=1 /
    // is_pending=0 / is_trashed=0 scope and the COUNT(DISTINCT ...) aggregates
    // are AOSP's, so an app's numsongs and number_of_tracks mean what they mean
    // on a device.
    private static final String AUDIO_SCOPE =
        COL_MEDIA_TYPE + "=" + MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO
        + " AND " + COL_IS_PENDING + "=0 AND " + COL_IS_TRASHED + "=0";
    private static final String MUSIC_SCOPE = AUDIO_SCOPE + " AND " + COL_IS_MUSIC + "=1";

    private static final String A_NUM_ALBUMS = MediaStore.Audio.ArtistColumns.NUMBER_OF_ALBUMS;
    private static final String A_NUM_TRACKS = MediaStore.Audio.ArtistColumns.NUMBER_OF_TRACKS;
    private static final String AL_NUMSONGS  = MediaStore.Audio.AlbumColumns.NUMBER_OF_SONGS;
    private static final String AL_NUMSONGS_ARTIST =
        MediaStore.Audio.AlbumColumns.NUMBER_OF_SONGS_FOR_ARTIST;
    private static final String AL_MINYEAR   = MediaStore.Audio.AlbumColumns.FIRST_YEAR;
    private static final String AL_MAXYEAR   = MediaStore.Audio.AlbumColumns.LAST_YEAR;
    private static final String AL_ART       = MediaStore.Audio.AlbumColumns.ALBUM_ART;
    private static final String G_NAME       = MediaStore.Audio.GenresColumns.NAME;

    private static final String VIEW_ARTISTS =
        "SELECT " + COL_ARTIST_ID + " AS " + COL_ID
        + ", MIN(" + COL_ARTIST + ") AS " + COL_ARTIST
        + ", " + COL_ARTIST_KEY + " AS " + COL_ARTIST_KEY
        + ", COUNT(DISTINCT " + COL_ALBUM_ID + ") AS " + A_NUM_ALBUMS
        + ", COUNT(DISTINCT " + COL_ID + ") AS " + A_NUM_TRACKS
        + " FROM " + TABLE + " WHERE " + MUSIC_SCOPE
        + " GROUP BY " + COL_ARTIST_ID;
    private static final Set<String> COLS_ARTISTS = new LinkedHashSet<>(Arrays.asList(
        COL_ID, COL_ARTIST, COL_ARTIST_KEY, A_NUM_ALBUMS, A_NUM_TRACKS));

    private static final String ALBUM_SELECT_TAIL =
        ", " + COL_ALBUM_ID + " AS " + COL_ALBUM_ID
        + ", MIN(" + COL_ALBUM + ") AS " + COL_ALBUM
        + ", " + COL_ALBUM_KEY + " AS " + COL_ALBUM_KEY
        + ", " + COL_ARTIST_ID + " AS " + COL_ARTIST_ID
        + ", " + COL_ARTIST + " AS " + COL_ARTIST
        + ", " + COL_ARTIST_KEY + " AS " + COL_ARTIST_KEY;

    private static final String VIEW_ALBUMS =
        "SELECT " + COL_ALBUM_ID + " AS " + COL_ID + ALBUM_SELECT_TAIL
        + ", COUNT(DISTINCT " + COL_ID + ") AS " + AL_NUMSONGS
        + ", COUNT(DISTINCT " + COL_ID + ") AS " + AL_NUMSONGS_ARTIST
        + ", MIN(" + COL_YEAR + ") AS " + AL_MINYEAR
        + ", MAX(" + COL_YEAR + ") AS " + AL_MAXYEAR
        + ", NULL AS " + AL_ART
        + " FROM " + TABLE + " WHERE " + MUSIC_SCOPE
        + " GROUP BY " + COL_ALBUM_ID;

    // AOSP audio_artists_albums: one row per (album, artist) pair, where
    // numsongs counts the whole album via a correlated subquery and
    // numsongs_by_artist counts only this artist's tracks on it.
    private static final String VIEW_ARTIST_ALBUMS =
        "SELECT " + COL_ALBUM_ID + " AS " + COL_ID + ALBUM_SELECT_TAIL
        + ", (SELECT COUNT(*) FROM " + TABLE + " WHERE " + COL_ALBUM_ID
        +      " = TEMP." + COL_ALBUM_ID + ") AS " + AL_NUMSONGS
        + ", COUNT(DISTINCT " + COL_ID + ") AS " + AL_NUMSONGS_ARTIST
        + ", MIN(" + COL_YEAR + ") AS " + AL_MINYEAR
        + ", MAX(" + COL_YEAR + ") AS " + AL_MAXYEAR
        + ", NULL AS " + AL_ART
        + " FROM " + TABLE + " TEMP WHERE " + MUSIC_SCOPE
        + " GROUP BY " + COL_ALBUM_ID + ", " + COL_ARTIST_ID;

    private static final Set<String> COLS_ALBUMS = new LinkedHashSet<>(Arrays.asList(
        COL_ID, COL_ALBUM_ID, COL_ALBUM, COL_ALBUM_KEY, COL_ARTIST_ID, COL_ARTIST,
        COL_ARTIST_KEY, AL_NUMSONGS, AL_NUMSONGS_ARTIST, AL_MINYEAR, AL_MAXYEAR, AL_ART));

    // AOSP's audio_genres view has NO "genre_id IS NOT NULL" guard, and it is
    // mirrored without one on purpose. The consequence on a scan-only index is
    // visible and correct: nothing here parses container tags, so every audio
    // row's genre is NULL, GROUP BY collapses them into a single group, and the
    // genres collection reports one row with a NULL _id and a NULL name. That is
    // precisely what a real device reports for a library of untagged files --
    // adding a filter would make us differ from AOSP in the one case we can
    // actually reach.
    private static final String VIEW_GENRES =
        "SELECT " + COL_GENRE_ID + " AS " + COL_ID
        + ", MIN(" + COL_GENRE + ") AS " + G_NAME
        + " FROM " + TABLE + " WHERE " + AUDIO_SCOPE
        + " GROUP BY " + COL_GENRE_ID;
    private static final Set<String> COLS_GENRES = new LinkedHashSet<>(Arrays.asList(
        COL_ID, G_NAME));

    // AOSP AUDIO_GENRES_ID_MEMBERS / AUDIO_GENRES_ALL_MEMBERS: the audio view
    // itself, with AUDIO_ID mapped to "_id AS audio_id"
    // (MediaProvider.java:6202-6231, fetched 2026-08-23).
    private static final String VIEW_GENRE_MEMBERS =
        "SELECT *, " + COL_ID + " AS " + COL_AUDIO_ID
        + " FROM " + TABLE + " WHERE " + AUDIO_SCOPE;
    private static final Set<String> COLS_GENRE_MEMBERS = genreMemberCols();
    private static Set<String> genreMemberCols() {
        Set<String> s = new LinkedHashSet<>(COLUMN_SET);
        s.add(COL_AUDIO_ID);
        return s;
    }

    // AOSP AUDIO_PLAYLISTS_ID_MEMBERS: "audio_playlists_map, audio" joined on
    // "audio._id = audio_id", with _ID taken from the MAP row so a member can be
    // addressed individually (MediaProvider.java:6263-6292).
    private static final String VIEW_PLAYLIST_MEMBERS = playlistMembersSql();
    private static String playlistMembersSql() {
        StringBuilder sb = new StringBuilder("SELECT m." + COL_ID + " AS " + COL_ID
            + ", m." + COL_AUDIO_ID + " AS " + COL_AUDIO_ID
            + ", m." + COL_PLAYLIST_ID + " AS " + COL_PLAYLIST_ID
            + ", m." + COL_PLAY_ORDER + " AS " + COL_PLAY_ORDER);
        for (String c : COLUMN_SET) {
            if (COL_ID.equals(c)) continue;   // already taken from the map row
            sb.append(", f.").append(c).append(" AS ").append(c);
        }
        sb.append(" FROM ").append(MAP_TABLE).append(" m, ").append(TABLE).append(" f")
          .append(" WHERE f.").append(COL_ID).append(" = m.").append(COL_AUDIO_ID)
          .append(" AND f.").append(COL_MEDIA_TYPE).append('=')
          .append(MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO);
        return sb.toString();
    }
    private static final Set<String> COLS_PLAYLIST_MEMBERS = playlistMemberCols();
    private static Set<String> playlistMemberCols() {
        Set<String> s = new LinkedHashSet<>(COLUMN_SET);
        s.add(COL_AUDIO_ID);
        s.add(COL_PLAYLIST_ID);
        s.add(COL_PLAY_ORDER);
        return s;
    }

    // images/thumbnails and video/thumbnails. Mac.apk generates no thumbnail
    // files, so the table genuinely holds zero rows -- "WHERE 0" is a real empty
    // table with the AOSP column shape, not a refusal. MediaStore.Images.
    // Thumbnails.getThumbnail() already documents the same absence by returning
    // null. If a thumbnail cache is ever added, replace the FROM clause; nothing
    // else about the route changes.
    private static final String VIEW_IMAGE_THUMBS =
        "SELECT NULL AS " + COL_ID + ", NULL AS " + MediaStore.Images.Thumbnails.DATA
        + ", NULL AS " + MediaStore.Images.Thumbnails.IMAGE_ID
        + ", NULL AS " + MediaStore.Images.Thumbnails.KIND
        + ", NULL AS " + MediaStore.Images.Thumbnails.WIDTH
        + ", NULL AS " + MediaStore.Images.Thumbnails.HEIGHT + " WHERE 0";
    private static final Set<String> COLS_IMAGE_THUMBS = new LinkedHashSet<>(Arrays.asList(
        COL_ID, MediaStore.Images.Thumbnails.DATA, MediaStore.Images.Thumbnails.IMAGE_ID,
        MediaStore.Images.Thumbnails.KIND, MediaStore.Images.Thumbnails.WIDTH,
        MediaStore.Images.Thumbnails.HEIGHT));
    private static final String VIEW_VIDEO_THUMBS =
        "SELECT NULL AS " + COL_ID + ", NULL AS " + MediaStore.Video.Thumbnails.DATA
        + ", NULL AS " + MediaStore.Video.Thumbnails.VIDEO_ID
        + ", NULL AS " + MediaStore.Video.Thumbnails.KIND
        + ", NULL AS " + MediaStore.Video.Thumbnails.WIDTH
        + ", NULL AS " + MediaStore.Video.Thumbnails.HEIGHT + " WHERE 0";
    private static final Set<String> COLS_VIDEO_THUMBS = new LinkedHashSet<>(Arrays.asList(
        COL_ID, MediaStore.Video.Thumbnails.DATA, MediaStore.Video.Thumbnails.VIDEO_ID,
        MediaStore.Video.Thumbnails.KIND, MediaStore.Video.Thumbnails.WIDTH,
        MediaStore.Video.Thumbnails.HEIGHT));

    /** One derived (view-backed) audio sub-table, plus the predicate the URI's
     *  own path segments contribute. */
    private static final class Sub {
        final String fromSql;
        final Set<String> cols;
        final String where;        // over the view's OUTPUT columns; may be null
        final String[] whereArgs;  // may be null
        final String type;         // the MIME type getType() answers with
        final boolean needsSync;   // false for the empty views: nothing to scan
        Sub(String fromSql, Set<String> cols, String where, String[] whereArgs,
            String type, boolean needsSync) {
            this.fromSql = fromSql; this.cols = cols; this.where = where;
            this.whereArgs = whereArgs; this.type = type; this.needsSync = needsSync;
        }
    }

    private static String segOrNull(List<String> seg, int i) {
        return i < seg.size() ? seg.get(i) : null;
    }

    /** content://media/none/media_scanner (MediaStore.getMediaScannerUri()). */
    private static boolean isMediaScannerUri(List<String> seg) {
        return seg.size() == 2 && "media_scanner".equals(seg.get(1));
    }

    /** Every media URI android.provider.MediaStore publishes that routeFor()
     *  does not serve. Returns null only for a URI that really is unknown. */
    private static Sub subFor(List<String> seg) {
        if (seg.size() < 2) return null;
        final String t1 = seg.get(1);
        final String t2 = segOrNull(seg, 2);
        final String t3 = segOrNull(seg, 3);
        final String t4 = segOrNull(seg, 4);
        final int n = seg.size();

        // seg is [volume, images, thumbnails] for the collection and
        // [volume, images, thumbnails, <id>] for one row -- the id lives at
        // index 3, so n==4 is the item form. (Getting this off by one made the
        // COLLECTION URI query "_id=?" with an empty argument list, which
        // SQLite rejects; caught by debug/msprobe.sh.)
        if ("images".equals(t1) && "thumbnails".equals(t2) && n <= 4) {
            String where = null; String[] args = null;
            if (n == 4) { where = COL_ID + "=?"; args = new String[]{t3}; }
            return new Sub(VIEW_IMAGE_THUMBS, COLS_IMAGE_THUMBS, where, args,
                n == 4 ? "vnd.android.cursor.item/image" : "vnd.android.cursor.dir/image", false);
        }
        if ("video".equals(t1) && "thumbnails".equals(t2) && n <= 4) {
            String where = null; String[] args = null;
            if (n == 4) { where = COL_ID + "=?"; args = new String[]{t3}; }
            return new Sub(VIEW_VIDEO_THUMBS, COLS_VIDEO_THUMBS, where, args,
                n == 4 ? "vnd.android.cursor.item/video" : "vnd.android.cursor.dir/video", false);
        }
        if (!"audio".equals(t1)) return null;

        // audio/albums, audio/albums/<id>
        if ("albums".equals(t2) && n <= 4) {
            String where = null; String[] args = null;
            if (n == 4) { where = COL_ID + "=?"; args = new String[]{t3}; }
            return new Sub(VIEW_ALBUMS, COLS_ALBUMS, where, args,
                n == 4 ? MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE
                       : MediaStore.Audio.Albums.CONTENT_TYPE, true);
        }
        if ("artists".equals(t2)) {
            // audio/artists/<id>/albums -- AOSP AUDIO_ARTISTS_ID_ALBUMS
            if (n == 5 && "albums".equals(t4)) {
                return new Sub(VIEW_ARTIST_ALBUMS, COLS_ALBUMS,
                    COL_ARTIST_ID + "=?", new String[]{t3},
                    MediaStore.Audio.Albums.CONTENT_TYPE, true);
            }
            if (n <= 4) {
                String where = null; String[] args = null;
                if (n == 4) { where = COL_ID + "=?"; args = new String[]{t3}; }
                return new Sub(VIEW_ARTISTS, COLS_ARTISTS, where, args,
                    n == 4 ? MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE
                           : MediaStore.Audio.Artists.CONTENT_TYPE, true);
            }
            return null;
        }
        if ("genres".equals(t2)) {
            // audio/genres/<id>/members and audio/genres/all/members
            if (n == 5 && "members".equals(t4)) {
                boolean all = "all".equals(t3);
                return new Sub(VIEW_GENRE_MEMBERS, COLS_GENRE_MEMBERS,
                    all ? null : COL_GENRE_ID + "=?", all ? null : new String[]{t3},
                    MediaStore.Audio.Media.CONTENT_TYPE, true);
            }
            if (n <= 4) {
                String where = null; String[] args = null;
                if (n == 4) { where = COL_ID + "=?"; args = new String[]{t3}; }
                return new Sub(VIEW_GENRES, COLS_GENRES, where, args,
                    n == 4 ? MediaStore.Audio.Genres.ENTRY_CONTENT_TYPE
                           : MediaStore.Audio.Genres.CONTENT_TYPE, true);
            }
            return null;
        }
        // audio/playlists/<id>/members[/<member id>] -- AOSP
        // AUDIO_PLAYLISTS_ID_MEMBERS(_ID). The collection and <id> forms are
        // served by routeFor() as a plain media_type=4 files query.
        if ("playlists".equals(t2) && n >= 5 && "members".equals(t4)) {
            StringBuilder w = new StringBuilder(COL_PLAYLIST_ID + "=?");
            List<String> args = new ArrayList<>();
            args.add(t3);
            if (n == 6) { w.append(" AND ").append(COL_ID).append("=?"); args.add(seg.get(5)); }
            else if (n != 5) return null;
            return new Sub(VIEW_PLAYLIST_MEMBERS, COLS_PLAYLIST_MEMBERS,
                w.toString(), args.toArray(new String[0]),
                n == 6 ? MediaStore.Audio.Media.ENTRY_CONTENT_TYPE
                       : MediaStore.Audio.Media.CONTENT_TYPE, true);
        }
        // audio/media/<id>/genres -- AOSP AUDIO_MEDIA_ID_GENRES, whose query is
        // "_id IN (SELECT genre_id FROM audio WHERE _id=?)" over audio_genres
        // (MediaProvider.java:6188-6192).
        if ("media".equals(t2) && n == 5 && "genres".equals(t4)) {
            return new Sub(VIEW_GENRES, COLS_GENRES,
                COL_ID + " IN (SELECT " + COL_GENRE_ID + " FROM " + TABLE
                    + " WHERE " + COL_ID + "=? AND " + AUDIO_SCOPE + ")",
                new String[]{t3},
                MediaStore.Audio.Genres.CONTENT_TYPE, true);
        }
        return null;
    }

    /** Project / filter / sort a derived view exactly the way safeQuery() does
     *  for the files table: SQLite honours the caller's selection and sortOrder,
     *  and a projection column the view does not carry reads back as an honest
     *  NULL rather than failing the whole query. */
    private Cursor derivedQuery(Sub sub, String[] cols, String selection,
                                 String[] selectionArgs, String sortOrder) {
        StringBuilder sql = new StringBuilder("SELECT ");
        for (int i = 0; i < cols.length; i++) {
            if (i > 0) sql.append(',');
            String c = cols[i];
            if (sub.cols.contains(c)) sql.append(c);
            else if (isSafeIdentifier(c)) sql.append("NULL AS \"").append(c).append('"');
            else sql.append("NULL AS unsupported_column");
        }
        sql.append(" FROM (").append(sub.fromSql).append(')');

        List<String> args = new ArrayList<>();
        StringBuilder where = new StringBuilder();
        if (sub.where != null) {
            where.append('(').append(sub.where).append(')');
            if (sub.whereArgs != null) args.addAll(Arrays.asList(sub.whereArgs));
        }
        if (selection != null && !selection.isEmpty()) {
            if (where.length() > 0) where.append(" AND ");
            where.append('(').append(selection).append(')');
            if (selectionArgs != null) args.addAll(Arrays.asList(selectionArgs));
        }
        if (where.length() > 0) sql.append(" WHERE ").append(where);
        if (sortOrder != null && !sortOrder.isEmpty()) sql.append(" ORDER BY ").append(sortOrder);

        Cursor result;
        try {
            result = mDb.rawQuery(sql.toString(), args.toArray(new String[0]));
        } catch (RuntimeException e) {
            com.macapk.loader.NativeLog.w(TAG, "sub-table query failed, returning empty cursor: " + e);
            return new MatrixCursor(cols);
        }
        // Same normalization safeQuery() applies: SQLiteDatabase degrades a
        // prepare failure to an empty ZERO-column cursor rather than throwing,
        // and a caller that asked for N columns must get N columns back.
        if (result == null || result.getColumnCount() != cols.length) {
            if (result != null) { try { result.close(); } catch (Throwable ignored) {} }
            return new MatrixCursor(cols);
        }
        return result;
    }

    // ---- ContentProvider surface ---------------------------------------
    @Override
    public String getType(Uri uri) {
        if (uri == null) return null;
        List<String> seg = uri.getPathSegments();
        Route route = routeFor(seg);
        if (route == null) {
            // The audio sub-tables and the thumbnail collections carry their
            // MIME type on the Sub itself (dir vs item is decided by the path).
            Sub sub = subFor(seg);
            if (sub != null) return sub.type;
            if (isMediaScannerUri(seg)) return null;   // AOSP has no type for it
            return null;
        }
        Long id = extractId(seg, route);
        if (id == null) return route.dirType;
        if (mDb != null) {
            try (Cursor c = mDb.query(TABLE, new String[]{COL_MIME_TYPE}, COL_ID + "=?",
                    new String[]{String.valueOf(id)}, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    String m = c.getString(0);
                    if (m != null && !m.isEmpty()) return m;
                }
            } catch (Throwable ignored) {}
        }
        return route.itemTypePrefix;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                         String[] selectionArgs, String sortOrder) {
        if (uri == null) return null;
        List<String> seg = uri.getPathSegments();
        if (seg.isEmpty()) return null;
        String[] cols = (projection != null) ? projection : DEFAULT_PROJECTION;
        // Diagnostic (INFO, not gated): this class is freshly wired up and
        // has never been exercised by a real app, so the first bring-up
        // needs to see exactly what shape callers ask for -- unknown
        // column names in a projection are otherwise silently answered
        // with NULL by safeQuery() below, which is correct AOSP behavior
        // but easy to mistake for "the provider isn't being reached" if
        // the request is invisible. Cheap (one string join per query) and
        // permanently useful for the next unfamiliar media app, so left
        // in rather than stripped after bring-up.
        com.macapk.loader.NativeLog.i(TAG, "query uri=" + uri
            + " projection=" + Arrays.toString(cols)
            + " selection=" + selection
            + " args=" + Arrays.toString(selectionArgs)
            + " sortOrder=" + sortOrder);

        // MediaStore.getMediaScannerUri() -- content://media/none/media_scanner.
        // AOSP MediaProvider handles this BEFORE it looks at any database
        // (MediaProvider.java:3908-3914, fetched 2026-08-23):
        //   MatrixCursor c = new MatrixCursor(new String[] {MEDIA_SCANNER_VOLUME});
        //   c.addRow(new String[] {mMediaScannerVolume});
        // i.e. always one row naming the volume being scanned, and null in that
        // cell when no scan is running -- which is our permanent state, because
        // sync() scans inline on every query instead of running a background
        // scanner. Note AOSP ignores `projection` entirely here; mirrored.
        if (isMediaScannerUri(seg)) {
            MatrixCursor c = new MatrixCursor(new String[]{MediaStore.MEDIA_SCANNER_VOLUME});
            c.addRow(new Object[]{null});
            return c;
        }

        // Mac.apk has one real scanned tree (the "external"/"external_primary"
        // volume MediaStore.getExternalVolumeNames() already advertises); there
        // is no separate "internal" media collection to serve, so that volume
        // is honestly empty rather than aliased onto the external one.
        String volume = seg.get(0);
        if (MediaStore.VOLUME_INTERNAL.equalsIgnoreCase(volume)) {
            return new MatrixCursor(cols);
        }

        Route route = routeFor(seg);
        if (route == null) {
            // The audio sub-tables (albums / artists / genres / playlist
            // members) and the thumbnail collections. These are URIs
            // android.provider.MediaStore PUBLISHES, so an app asking for one is
            // not making a caller error and must get a real Cursor -- see the
            // AUDIO SUB-TABLE VIEWS block for why null is a different and wrong
            // answer here.
            Sub sub = subFor(seg);
            if (sub != null) {
                if (mDb == null) return null;
                // AOSP AccessChecker: the thumbnail tables follow their media
                // collection, and the parsed audio tables (albums, artists,
                // genres, playlist members) are NO_ACCESS_SQL without
                // READ_MEDIA_AUDIO -- "callers need to hold READ_MEDIA_AUDIO".
                if (!subVisible(seg.size() > 1 ? seg.get(1) : "", access())) {
                    com.macapk.loader.NativeLog.i(TAG, "query -> rows=0 sub=" + uri.getPath()
                        + " (no read permission for this collection)");
                    return new MatrixCursor(cols);
                }
                if (sub.needsSync) sync(false);      // audio sub-tables: never a Photos row
                Cursor out = derivedQuery(sub, cols, selection, selectionArgs, sortOrder);
                try {
                    com.macapk.loader.NativeLog.i(TAG, "query -> rows="
                        + (out == null ? -1 : out.getCount()) + " sub=" + uri.getPath());
                } catch (Throwable ignored) {}
                return out;
            }
            com.macapk.loader.NativeLog.w(TAG, "query: unrecognized media URI " + uri);
            // AOSP: an unmatched UriMatcher throws IllegalArgumentException from
            // query(), and that is a genuine caller bug (a URI no MediaStore
            // constant produces). It PROPAGATES to the app, exactly as on a
            // device -- ContentResolver no longer swallows provider exceptions.
            throw new IllegalArgumentException("Unknown URI: " + uri);
        }
        if (mDb == null) return null;

        Long singleId = extractId(seg, route);

        sync(routeMayReturnMedia(route));

        StringBuilder where = new StringBuilder();
        List<String> args = new ArrayList<>();
        if (route.mediaTypeFilter >= 0) {
            where.append(COL_MEDIA_TYPE).append("=?");
            args.add(String.valueOf(route.mediaTypeFilter));
        }
        if (route.downloadsOnly) {
            if (where.length() > 0) where.append(" AND ");
            where.append(COL_IS_DOWNLOAD).append("=1");
        }
        if (singleId != null) {
            if (where.length() > 0) where.append(" AND ");
            where.append(COL_ID).append("=?");
            args.add(String.valueOf(singleId));
        }
        String accessSql = accessWhere(route.mediaTypeFilter, route.downloadsOnly, access());
        if (accessSql != null) {
            if (where.length() > 0) where.append(" AND ");
            where.append('(').append(accessSql).append(')');
        }
        if (selection != null && !selection.isEmpty()) {
            if (where.length() > 0) where.append(" AND ");
            where.append('(').append(selection).append(')');
            if (selectionArgs != null) args.addAll(Arrays.asList(selectionArgs));
        }

        if (photosEnabled() && wantsBytes(cols)) {
            materializePhotos(where.toString(), args.toArray(new String[0]), sortOrder);
        }
        Cursor out = safeQuery(cols, where.toString(), args.toArray(new String[0]), sortOrder);
        // ROW COUNT on the way out. The query log above says what was ASKED;
        // without this there is no way to tell an empty result from a filtered
        // one, and an app showing an empty grid looks identical in both cases.
        // MEASURED need: 83 queries executed with zero SQL errors over a table
        // holding 4 correctly-indexed rows, and the grid was still empty -- the
        // question "did the cursor come back empty" was unanswerable from
        // outside the process.
        try {
            com.macapk.loader.NativeLog.i(TAG, "query -> rows=" + (out == null ? -1 : out.getCount())
                + " where=" + (where.length() == 0 ? "(none)" : where.toString()));
        } catch (Throwable ignored) {}
        return out;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (uri == null) throw new FileNotFoundException("null uri");
        List<String> seg = uri.getPathSegments();
        Route route = routeFor(seg);
        Long id = route != null ? extractId(seg, route) : null;
        if (id == null) throw new FileNotFoundException("no row id in " + uri);
        if (mDb == null) throw new FileNotFoundException("media index unavailable");

        int pfdMode = parseMode(mode);
        int a = access();
        // AOSP MediaProvider.openFile -> openFileCommon calls
        // hasAccessToCollection(uri, forWrite=true) for any write-shaped mode;
        // a caller holding only a READ_MEDIA_* grant is read-only, same as a
        // real device. Before this (review of 2026-09-28), accessWhere() only
        // ever filtered the READ path, so an app with just READ_MEDIA_IMAGES
        // could open, and truncate, the user's own ~/Pictures photo through a
        // "w" mode.
        if (modeWantsWrite(pfdMode) && !writeAllowed(a)) {
            throw new SecurityException("no write access to " + uri);
        }

        String path = null;
        String accessSql = accessWhere(route.mediaTypeFilter, route.downloadsOnly, a);
        try (Cursor c = mDb.query(TABLE, new String[]{COL_DATA}, COL_ID + "=?"
                + (accessSql != null ? " AND (" + accessSql + ")" : ""),
                new String[]{String.valueOf(id)}, null, null, null)) {
            if (c != null && c.moveToFirst()) path = c.getString(0);
        }
        if (path == null) {
            // A row that EXISTS but was filtered out by the access clause is a
            // permission failure, not a missing file -- AOSP's
            // enforceCallingPermission throws SecurityException for exactly
            // this case (a caller that can see the collection but not this
            // row), which callers rely on to tell "denied" apart from "gone".
            if (accessSql != null && rowExistsIgnoringAccess(id)) {
                throw new SecurityException("no read access to " + uri);
            }
            throw new FileNotFoundException("no row for " + uri);
        }
        File f = new File(path);
        if (photosEnabled()) {
            String[] photo = photoByData(path);
            if (photo != null) {
                // Design decision 2: the Photos library is READ-ONLY to Android
                // apps. Writing the exported copy would change nothing in Photos
                // and be silently undone by the next export, so refuse it the
                // way a device refuses a write it will not honour.
                if (modeWantsWrite(pfdMode)) {
                    throw new SecurityException("read-only: " + uri
                        + " is backed by the macOS Photos library");
                }
                if (!f.exists() || !"1".equals(photo[2])) {
                    if (!exportPhoto(photo[0], path, Long.parseLong(photo[1]))) {
                        throw new FileNotFoundException(path + " (the Photos asset could not be exported)");
                    }
                }
            }
        }
        if (!f.exists()) throw new FileNotFoundException(path);
        return ParcelFileDescriptor.open(f, pfdMode);
    }

    /** AOSP MediaProvider.openTypedAssetFileCommon (packages/providers/MediaProvider
     *  main, MediaProvider.java:9501-9540): a caller passing
     *  ContentResolver.EXTRA_SIZE with an image/* filter wants a THUMBNAIL
     *      final boolean wantsThumb = (opts != null) && opts.containsKey(ContentResolver.EXTRA_SIZE)
     *              && StringUtils.startsWithIgnoreCase(mimeTypeFilter, "image/");
     *  and gets ensureThumbnail(uri, signal) instead of the original. For a Photos
     *  row that thumbnail comes from PhotoKit (rendered from the library's own
     *  caches), so filling a grid never exports a whole library. Every other row
     *  keeps the base behaviour (the whole file). */
    @Override
    public android.content.res.AssetFileDescriptor openTypedAssetFile(Uri uri,
            String mimeTypeFilter, android.os.Bundle opts) throws FileNotFoundException {
        boolean wantsThumb = opts != null
            && opts.containsKey(android.content.ContentResolver.EXTRA_SIZE)
            && mimeTypeFilter != null && mimeTypeFilter.regionMatches(true, 0, "image/", 0, 6);
        if (wantsThumb && mDb != null && photosEnabled() && uri != null) {
            List<String> seg = uri.getPathSegments();
            Route route = routeFor(seg);
            Long id = route != null ? extractId(seg, route) : null;
            String[] photo = null;
            if (id != null) {
                String accessSql = accessWhere(route.mediaTypeFilter, route.downloadsOnly, access());
                try (Cursor c = mDb.rawQuery("SELECT p.local_id, p.modified_ms FROM " + TABLE
                        + " JOIN " + PHOTOS_TABLE + " p ON p.data=" + TABLE + "." + COL_DATA
                        + " WHERE " + TABLE + "." + COL_ID + "=?"
                        + (accessSql != null ? " AND (" + accessSql + ")" : ""),
                        new String[]{String.valueOf(id)})) {
                    if (c != null && c.moveToFirst()) photo = new String[]{c.getString(0), c.getString(1)};
                }
            }
            if (photo != null) {
                Object size = opts.get(android.content.ContentResolver.EXTRA_SIZE);
                int w = 256, h = 256;
                if (size instanceof android.graphics.Point) {
                    w = Math.max(1, ((android.graphics.Point) size).x);
                    h = Math.max(1, ((android.graphics.Point) size).y);
                }
                File thumb = new File(photoThumbDir(), photoKey(photo[0]) + "_" + photo[1]
                    + "_" + w + "x" + h + ".jpg");
                if (thumb.isFile() || photosThumbnail(photo[0], w, h, thumb)) {
                    return new android.content.res.AssetFileDescriptor(
                        ParcelFileDescriptor.open(thumb, ParcelFileDescriptor.MODE_READ_ONLY), 0,
                        android.content.res.AssetFileDescriptor.UNKNOWN_LENGTH);
                }
            }
        }
        return super.openTypedAssetFile(uri, mimeTypeFilter, opts);
    }

    private boolean rowExistsIgnoringAccess(long id) {
        try (Cursor c = mDb.query(TABLE, new String[]{COL_ID}, COL_ID + "=?",
                new String[]{String.valueOf(id)}, null, null, null)) {
            return c != null && c.moveToFirst();
        } catch (Throwable t) {
            return false;
        }
    }

    // AOSP ParcelFileDescriptor.parseMode(String) delegates to
    // FileUtils.translateModeStringToPosix()+translateModePosixToPfd()
    // (frameworks/base/core/java/android/os/FileUtils.java, fetched
    // 2026-07-30, ~line 1537-1613) -- mirrored directly rather than
    // reimplemented from memory: "rw" prefix -> READ_WRITE|CREATE, "w"
    // prefix -> WRITE_ONLY|CREATE, else READ_ONLY; a 't' anywhere in the
    // mode string adds TRUNCATE, an 'a' adds APPEND.
    private static int parseMode(String mode) {
        if (mode == null || mode.isEmpty()) return ParcelFileDescriptor.MODE_READ_ONLY;
        int res;
        if (mode.startsWith("rw")) {
            res = ParcelFileDescriptor.MODE_READ_WRITE | ParcelFileDescriptor.MODE_CREATE;
        } else if (mode.startsWith("w")) {
            res = ParcelFileDescriptor.MODE_WRITE_ONLY | ParcelFileDescriptor.MODE_CREATE;
        } else {
            res = ParcelFileDescriptor.MODE_READ_ONLY;
        }
        if (mode.indexOf('t') >= 0) res |= ParcelFileDescriptor.MODE_TRUNCATE;
        if (mode.indexOf('a') >= 0) res |= ParcelFileDescriptor.MODE_APPEND;
        return res;
    }

    // ---- projection-safe query: honour exactly the requested column list,
    // including columns our filesystem-only index does not populate (they
    // read back NULL, matching a real MediaStore row whose scanner never
    // extracted that metadata, rather than failing the whole query) --------
    private Cursor safeQuery(String[] cols, String selection, String[] args, String sortOrder) {
        String[] selectCols = new String[cols.length];
        for (int i = 0; i < cols.length; i++) {
            String c = cols[i];
            if (COLUMN_SET.contains(c)) {
                selectCols[i] = c;
            } else if (isSafeIdentifier(c)) {
                selectCols[i] = "NULL AS \"" + c + "\"";
            } else {
                selectCols[i] = "NULL AS unsupported_column";
            }
        }
        Cursor result;
        try {
            result = mDb.query(TABLE, selectCols, selection, args, null, null, sortOrder);
        } catch (RuntimeException e) {
            com.macapk.loader.NativeLog.w(TAG, "media query failed, returning empty cursor: " + e);
            return new MatrixCursor(cols);
        }
        // SQLiteDatabase degrades a prepare failure (e.g. selection referencing
        // an unknown column) to an empty ZERO-column cursor rather than
        // throwing; normalize that back to the caller's exact requested shape
        // so "asks for two columns, gets two columns" holds even on that path.
        if (result == null || result.getColumnCount() != cols.length) {
            if (result != null) { try { result.close(); } catch (Throwable ignored) {} }
            return new MatrixCursor(cols);
        }
        return result;
    }

    private static boolean isSafeIdentifier(String s) {
        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                      || (c >= '0' && c <= '9') || c == '_';
            if (!ok) return false;
        }
        return true;
    }

    // ---- filesystem sync (the "MediaScanner" half) ----------------------
    // Runs at the top of every query(): cheap after the first call because
    // unchanged files (same date_modified as last sync) are skipped, and
    // Mac.apk's corpus is a local Pictures/DCIM tree, not a real device's
    // millions of rows. Synchronized so two overlapping query() calls from
    // different threads cannot both decide the same new file is unseen and
    // race an INSERT against the _data UNIQUE index.
    private synchronized void sync(boolean mayPrompt) {
        File root = Environment.getExternalStorageDirectory();
        List<File> found = new ArrayList<>();
        long t0 = System.nanoTime();
        final int a = access();
        walkVolume(root, found, a);
        long walkMs = (System.nanoTime() - t0) / 1_000_000L;

        Map<String, long[]> existing = new HashMap<>(); // path -> {id, date_modified}
        try (Cursor c = mDb.query(TABLE, new String[]{COL_ID, COL_DATA, COL_DATE_MOD},
                null, null, null, null, null)) {
            while (c != null && c.moveToNext()) {
                existing.put(c.getString(1), new long[]{c.getLong(0), c.getLong(2)});
            }
        } catch (Throwable t) {
            com.macapk.loader.NativeLog.w(TAG, "sync: failed to read existing index: " + t);
            return;
        }

        // AOSP's own scanner commits a whole volume walk as one operation
        // (ModernMediaScanner applies its ContentProviderOperations in a batch,
        // which MediaProvider.applyBatch wraps in a single transaction); a
        // per-file autocommit insert/update/delete pays a much higher cost per
        // file. Measured after the owner reversal mapped ~/Documents into the
        // volume (MACAPK_USER_FOLDERS): 73,747 walkable files on this machine,
        // 13,924 of them images needing a bounds decode -- an All-files app's
        // first sync of that tree is now the dominant cost of its first
        // MediaStore query, and it was paying SQLite's autocommit fsync on
        // every single one of them on top of that.
        Set<String> seen = new HashSet<>();
        final boolean photosOn = photosEnabled();
        // An exported Photos copy is a real file in DCIM/Camera, so the walk finds
        // it; its row belongs to the Photos pass (dates and size from PhotoKit, not
        // from the copy's own mtime), so the file pass leaves it alone.
        Map<String, String[]> photos = photosOn ? loadPhotos() : null;
        // Copies in DCIM/Camera that carry OUR owner tag but that the index has no
        // row for (the index was lost or rebuilt): the Photos pass re-adopts or
        // removes them; they are not ordinary files.
        Map<String, String> orphans = new HashMap<>();
        final File camDir = new File(root, Environment.DIRECTORY_DCIM + "/Camera");
        mDb.beginTransaction();
        try {
            for (File f : found) {
                try {
                    // Not added to `seen` here: whether the row survives is the
                    // Photos pass's call (a removed asset's copy is still on disk
                    // at this point, and must not keep its row alive).
                    if (photos != null) {
                        if (photos.containsKey(f.getAbsolutePath())) continue;
                        if (camDir.equals(f.getParentFile())) {
                            String tag = photosOwnerTag(f);
                            if (tag != null) {
                                orphans.put(f.getAbsolutePath(), tag);
                                continue;
                            }
                        }
                    }
                    syncOne(f, root, existing, seen);
                } catch (Throwable t) {
                    com.macapk.loader.NativeLog.w(TAG, "sync: skipping " + f + ": " + t);
                }
            }
            if (photos != null) {
                try {
                    syncPhotos(root, existing, seen, photos, a, mayPrompt, orphans);
                } catch (Throwable t) {
                    com.macapk.loader.NativeLog.w(TAG, "sync: Photos pass failed: " + t);
                    seen.addAll(photos.keySet());   // cannot say: keep what is there
                    for (String o : orphans.keySet()) {
                        try { syncOne(new File(o), root, existing, seen); } catch (Throwable ignored) {}
                    }
                }
            }
            for (String path : existing.keySet()) {
                if (!seen.contains(path)) {
                    try { mDb.delete(TABLE, COL_DATA + "=?", new String[]{path}); }
                    catch (Throwable ignored) {}
                }
            }
            mDb.setTransactionSuccessful();
        } finally {
            mDb.endTransaction();
        }

        // ONE line per sync, and it earns its keep: an empty gallery has three
        // very different causes -- the walk found nothing, the walk found files
        // but none became rows, or rows exist and the QUERY filters them out --
        // and from outside the process those are indistinguishable. MEASURED
        // need: 83 successful queries returned an empty grid with no error
        // anywhere in the log. `rows` is read back from the table rather than
        // counted in the loop, because `seen` grows for every walked file
        // (including the unchanged fast-path) and would overstate it.
        long rows = -1;
        try (Cursor rc = mDb.rawQuery("SELECT COUNT(*) FROM " + TABLE, null)) {
            if (rc != null && rc.moveToFirst()) rows = rc.getLong(0);
        } catch (Throwable ignored) {}
        com.macapk.loader.NativeLog.i(TAG, "sync: root=" + root
            + " walked=" + found.size() + " walkMs=" + walkMs + " rows=" + rows
            + " previously=" + existing.size());

        syncPlaylistMembers();
    }

    // ---- the macOS Photos library pass (MACAPK_PHOTOKIT) -------------------
    //
    // WHERE THE ASSETS APPEAR: DCIM/Camera/, the device's camera roll -- AOSP
    // FileUtils (packages/providers/MediaProvider main, util/FileUtils.java:1070)
    //     private static final String CAMERA_RELATIVE_PATH =
    //             String.format("%s/%s/", Environment.DIRECTORY_DCIM, "Camera");
    // is the one folder MediaStore keeps visible whatever .nomedia says, and where
    // every gallery looks first. A Mac's camera roll IS the Photos library, which
    // is why DCIM is deliberately not mapped to a Mac folder (SharedStorage).
    //
    // NAMES: the asset's own file name (IMG_0001.HEIC); a clash with a file
    // already there (an app's own camera shot, another asset) gets AOSP's unique
    // name, FileUtils.buildUniqueFileWithExtension (:564-642):
    //     final String res = (i == 0) ? name : name + " (" + i + ")";
    //
    // BYTES: a row exists as soon as PhotoKit lists the asset; its bytes are
    // exported into that path only when an app asks for them (a query projecting
    // _data or _size, or openFile) -- see materializePhotos. A gallery that
    // renders from content:// thumbnails therefore never copies the library, and
    // one that reads _data (Fossify/Glide) finds a real file there.
    //
    // WHO MAY SEE THEM: only an app whose access() carries images or video (AOSP
    // READ_MEDIA_IMAGES / READ_MEDIA_VIDEO, or the grants that imply them), and
    // only the kinds it holds. Without one, Photos is never even asked -- no
    // status call, no prompt -- and any rows and copies from an earlier grant are
    // removed, so a revoked grant leaves nothing of the library behind.

    /** Which Photos kinds the calling app may see: bit 1 images, bit 2 videos. */
    static int photoKinds(int a) {
        int k = 0;
        if ((a & (A_ALL | A_LEGACY_READ | A_IMAGES)) != 0) k |= 1;
        if ((a & (A_ALL | A_LEGACY_READ | A_VIDEO)) != 0) k |= 2;
        return k;
    }

    /** data -> {local_id, modified_ms, exported} for every Photos-backed row. */
    private Map<String, String[]> loadPhotos() {
        Map<String, String[]> out = new HashMap<>();
        try (Cursor c = mDb.rawQuery("SELECT local_id, data, modified_ms, exported FROM "
                + PHOTOS_TABLE, null)) {
            while (c != null && c.moveToNext()) {
                out.put(c.getString(1), new String[]{c.getString(0), c.getString(2), c.getString(3)});
            }
        } catch (Throwable t) {
            // No table (created only while MACAPK_PHOTOKIT is on) reads as none.
        }
        return out;
    }

    private void syncPhotos(File root, Map<String, long[]> existing, Set<String> seen,
                            Map<String, String[]> photos, int a, boolean mayPrompt,
                            Map<String, String> orphans) {
        final int kinds = photoKinds(a);
        final File cam = new File(root, Environment.DIRECTORY_DCIM + "/Camera");
        // A killed runtime, or an export that timed out while PhotoKit was still
        // writing, leaves a dot-named temp file the walk never lists: age them out.
        photosSweepPending(cam);
        photosSweepPending(photoThumbDir());
        List<String[]> assets;
        if (kinds == 0) {
            assets = new ArrayList<>();                 // no grant: never ask Photos
        } else {
            int st = photosStatus();
            if (st == PH_UNAVAILABLE) {
                assets = null;                          // no backend: cannot say
            } else if (st == PH_NOT_DETERMINED) {
                mPhotosCache = null;
                // Only a query that can return image/video rows may raise macOS's
                // prompt: a music player or a downloads list must not ask for the
                // photo library (and a "Don't Allow" answered there is for good).
                if (mayPrompt && photosRequestAuthorization()) startPhotosPoller();
                assets = new ArrayList<>();
            } else if (!photosReadable(st)) {
                mPhotosCache = null;                    // deny -> re-allow must re-list
                assets = new ArrayList<>();             // denied / restricted
            } else {
                long gen = photosGeneration();
                if (mPhotosCache != null && gen == mPhotosGen && kinds == mPhotosKinds) {
                    assets = mPhotosCache;
                } else {
                    assets = photosList((kinds & 1) != 0, (kinds & 2) != 0);
                    if (assets != null) {
                        mPhotosCache = assets;
                        mPhotosGen = gen;
                        mPhotosKinds = kinds;
                    }
                }
            }
        }
        if (assets == null) {
            seen.addAll(photos.keySet());
            for (String o : orphans.keySet()) {         // cannot say: leave them as files
                try { syncOne(new File(o), root, existing, seen); } catch (Throwable ignored) {}
            }
            return;
        }

        Map<String, Long> listedMod = new HashMap<>();  // local_id -> PhotoKit modification ms
        for (String[] f : assets) listedMod.putIfAbsent(f[PH_F_ID], parseLong(f[PH_F_MODIFIED_MS]));
        Map<String, String> byId = new HashMap<>();     // local_id -> data
        for (Map.Entry<String, String[]> e : photos.entrySet()) byId.put(e.getValue()[0], e.getKey());
        Set<String> taken = new HashSet<>();
        for (String d : photos.keySet()) taken.add(d.toLowerCase(Locale.ROOT));
        Set<String> thumbPrefixes = new HashSet<>();    // photoKey(id) + "_", flushed once

        // OWNERSHIP TRAVELS WITH THE FILE (an xattr, see photosTagFile), so losing
        // the index (a schema bump, a deleted external.db) can neither duplicate the
        // library nor strand copies. A tagged copy with no row is ours: re-adopted
        // when the asset is still listed and the copy still matches its version,
        // deleted when it is not (asset gone, or access no longer granted).
        for (Map.Entry<String, String> o : orphans.entrySet()) {
            String path = o.getKey(), id = o.getValue();
            File of = new File(path);
            Long modMs = listedMod.get(id);
            if (modMs == null) {
                of.delete();
            } else if (byId.containsKey(id)) {
                // The same tag on a second file: an app copied our copy. Its file.
                try { syncOne(of, root, existing, seen); } catch (Throwable ignored) {}
            } else if (Math.abs(of.lastModified() - modMs) < 1000L) {
                ContentValues pv = new ContentValues();
                pv.put("local_id", id);
                pv.put("data", path);
                pv.put("modified_ms", modMs);
                pv.put("exported", 1);
                mDb.insert(PHOTOS_TABLE, null, pv);
                photos.put(path, new String[]{id, String.valueOf(modMs), "1"});
                byId.put(id, path);
                taken.add(path.toLowerCase(Locale.ROOT));
            } else {
                of.delete();                            // stale version: re-exported on read
            }
        }

        int bucketId = cam.getAbsolutePath().toLowerCase(Locale.ROOT).hashCode();
        Set<String> listed = new HashSet<>();
        int inserted = 0;
        for (String[] f : assets) {
            String id = f[PH_F_ID];
            if (!listed.add(id)) continue;
            long modMs = parseLong(f[PH_F_MODIFIED_MS]);
            String path = byId.get(id);
            String[] prior = path != null ? photos.get(path) : null;
            boolean adoptClash = false;
            if (path == null) {
                path = uniquePhotoPath(cam, f[PH_F_NAME], taken);
                ContentValues pv = new ContentValues();
                pv.put("local_id", id);
                pv.put("data", path);
                pv.put("modified_ms", modMs);
                pv.put("exported", 0);
                mDb.insert(PHOTOS_TABLE, null, pv);
                inserted++;
            } else if (prior != null && !"1".equals(prior[2]) && new File(path).exists()) {
                // A file sits at the name this (unexported) asset was given and it is
                // not ours: an app wrote it after the name was handed out. Never
                // overwrite it and never leave it shadowed under the asset's
                // metadata: the asset moves to a fresh AOSP-unique name and the
                // app's file becomes an ordinary row.
                File clash = new File(path);
                if (id.equals(photosOwnerTag(clash))) {
                    adoptClash = true;                  // ours after all (the row lost its flag)
                } else {
                    String np = uniquePhotoPath(cam, clash.getName(), taken);
                    ContentValues pv = new ContentValues();
                    pv.put("data", np);
                    mDb.update(PHOTOS_TABLE, pv, "local_id=?", new String[]{id});
                    long[] old = existing.remove(path);
                    if (old != null) {
                        ContentValues cv = new ContentValues();
                        cv.put(COL_DATA, np);
                        cv.put(COL_DISPLAY_NAME, new File(np).getName());
                        cv.put(COL_TITLE, stripExtension(new File(np).getName()));
                        mDb.update(TABLE, cv, COL_ID + "=?", new String[]{String.valueOf(old[0])});
                        existing.put(np, old);
                    }
                    try { syncOne(clash, root, existing, seen); } catch (Throwable ignored) {}
                    photos.remove(path);
                    photos.put(np, new String[]{id, prior[1], "0"});
                    path = np;
                    prior = photos.get(np);
                }
            }
            taken.add(path.toLowerCase(Locale.ROOT));
            seen.add(path);
            File copy = new File(path);
            final boolean dbExported = prior != null && "1".equals(prior[2]);
            boolean changed = prior != null && parseLong(prior[1]) != modMs;
            boolean exported = dbExported || (adoptClash && !changed);
            if (adoptClash && changed) copy.delete();   // a stale copy of an edited asset
            if (dbExported && (changed || !copy.isFile())) {
                // Edited in Photos since we exported it, or the app deleted the
                // copy: drop ours and re-export on the next read (the design's
                // "the app sees its delete undone").
                if (changed) copy.delete();
                exported = false;
            }
            if (changed) thumbPrefixes.add(photoKey(id) + "_");   // its old renditions
            boolean flipped = dbExported && !exported;
            boolean adopted = !dbExported && exported;
            if (changed || flipped || adopted) {
                ContentValues pv = new ContentValues();
                pv.put("modified_ms", modMs);
                pv.put("exported", exported ? 1 : 0);
                mDb.update(PHOTOS_TABLE, pv, "local_id=?", new String[]{id});
            }
            long[] ex = existing.get(path);
            long size = exported ? copy.length() : 0L;
            if (ex != null && ex[1] == modMs / 1000L && !changed && !flipped && !adopted) {
                continue;                               // row already current
            }
            String name = new File(path).getName();
            int mediaType = "3".equals(f[PH_F_MEDIA_TYPE])
                ? MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                : MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE;
            long createdMs = parseLong(f[PH_F_CREATED_MS]);
            ContentValues cv = new ContentValues();
            cv.put(COL_DATA, path);
            cv.put(COL_DISPLAY_NAME, name);
            cv.put(COL_TITLE, stripExtension(name));
            cv.put(COL_SIZE, size);
            if (ex == null) cv.put(COL_DATE_ADDED, createdMs / 1000L);
            cv.put(COL_DATE_MOD, modMs / 1000L);
            // DATE_TAKEN in ms: the capture time Photos recorded (EXIF-derived for
            // a camera shot), which is exactly what MediaStore's scanner would
            // have parsed from the file.
            cv.put(COL_DATE_TAKEN, createdMs);
            cv.put(COL_MIME_TYPE, f[PH_F_MIME]);
            cv.put(COL_MEDIA_TYPE, mediaType);
            cv.put(COL_WIDTH, (int) parseLong(f[PH_F_WIDTH]));
            cv.put(COL_HEIGHT, (int) parseLong(f[PH_F_HEIGHT]));
            cv.put(COL_DURATION, parseLong(f[PH_F_DURATION_MS]));
            cv.put(COL_BUCKET_ID, bucketId);
            cv.put(COL_BUCKET_NAME, cam.getName());
            cv.put(COL_REL_PATH, Environment.DIRECTORY_DCIM + "/Camera/");
            cv.put(COL_VOLUME_NAME, MediaStore.VOLUME_EXTERNAL_PRIMARY);
            cv.put(COL_IS_PENDING, 0);
            cv.put(COL_IS_FAVORITE, 0);
            cv.put(COL_IS_TRASHED, 0);
            cv.put(COL_IS_DOWNLOAD, 0);
            if (ex != null) {
                mDb.update(TABLE, cv, COL_ID + "=?", new String[]{String.valueOf(ex[0])});
            } else {
                mDb.insert(TABLE, null, cv);
            }
        }
        // Assets no longer listed (deleted in Photos, a limited selection
        // narrowed, access revoked, or a kind no longer granted): the row goes (it
        // is not in `seen`, so the delete loop takes it) and so does OUR copy --
        // only ever a file we exported, never an app's file at that path.
        int removed = 0;
        for (Map.Entry<String, String[]> e : photos.entrySet()) {
            String id = e.getValue()[0];
            if (listed.contains(id)) continue;
            if ("1".equals(e.getValue()[2])) new File(e.getKey()).delete();
            thumbPrefixes.add(photoKey(id) + "_");
            mDb.delete(PHOTOS_TABLE, "local_id=?", new String[]{id});
            removed++;
        }
        deletePhotoThumbs(thumbPrefixes);
        if (inserted > 0 || removed > 0) {
            com.macapk.loader.NativeLog.i(TAG, "sync: Photos library assets=" + listed.size()
                + " new=" + inserted + " removed=" + removed + " kinds=" + kinds);
        }
    }

    /** AOSP buildUniqueFileWithExtension naming in `dir`, case-insensitively
     *  (APFS), against both the disk and names already handed out. */
    private static String uniquePhotoPath(File dir, String fileName, Set<String> taken) {
        String name = fileName == null || fileName.isEmpty() ? "IMG" : fileName;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 0; ; i++) {
            String n = (i == 0) ? base : base + " (" + i + ")";
            File f = new File(dir, n + ext);
            String key = f.getAbsolutePath().toLowerCase(Locale.ROOT);
            if (!taken.contains(key) && !f.exists()) return f.getAbsolutePath();
        }
    }

    private static long parseLong(String s) {
        try { return Long.parseLong(s); } catch (Throwable t) { return 0L; }
    }

    /** A projection that reads a row's BYTES (a path to open, or their size)
     *  rather than only its metadata. null projection = every column. */
    private static boolean wantsBytes(String[] cols) {
        if (cols == DEFAULT_PROJECTION) return true;
        for (String c : cols) {
            if (COL_DATA.equals(c) || COL_SIZE.equals(c)) return true;
        }
        return false;
    }

    /** Export the not-yet-exported Photos rows THIS query will return (same WHERE,
     *  same ORDER BY/LIMIT), so the _data paths in the cursor name real files and
     *  _size is their real size.
     *
     *  ONE statement, and no table load: the query's own WHERE/ORDER BY/LIMIT run
     *  as a subselect over `files`, joined to the still-unexported rows of
     *  macapk_photos through the UNIQUE `data` index, so a Glide-style per-item
     *  query (WHERE _id=?) costs one indexed lookup, not a scan of the library.
     *
     *  SIZE PREDICATES. An unexported row carries _size 0 until its bytes exist,
     *  and a picker library asks "_size>0" (Matisse, PictureSelector...): run as
     *  is, the predicate would exclude exactly the rows this method exists to
     *  materialize. A real MediaStore row always has its file size, so when the
     *  query mentions _size the selection is also evaluated with the pending rows
     *  provisionally reading as tiny (1 byte) and as huge (Long.MAX_VALUE/2)
     *  -- covering lower- and upper-bound filters -- and the union is exported;
     *  the real query then runs over real sizes. The provisional value exists
     *  only inside a derived table of THIS statement (no write, so no other
     *  thread can ever observe it). */
    private void materializePhotos(String where, String[] args, String sortOrder) {
        if (!hasPendingPhotos()) return;
        final boolean sizeQuery = mentionsSize(where) || mentionsSize(sortOrder);
        Map<String, String[]> want = new java.util.LinkedHashMap<>();
        boolean derivedFailed = false;
        if (sizeQuery) {
            for (long provisional : new long[]{1L, Long.MAX_VALUE >> 1}) {
                if (!pendingCandidates(provisionalSizeFrom(provisional), where, args, sortOrder, want)) {
                    derivedFailed = true;
                    break;
                }
            }
        }
        if (!sizeQuery || derivedFailed) {
            if (!pendingCandidates(TABLE, where, args, sortOrder, want)) return;   // the real query reports it
        }
        int done = 0;
        for (Map.Entry<String, String[]> e : want.entrySet()) {
            String[] p = e.getValue();
            if (exportPhoto(p[0], e.getKey(), parseLong(p[1]))) done++;
        }
        if (!want.isEmpty()) {
            com.macapk.loader.NativeLog.i(TAG, "Photos: exported " + done + "/" + want.size()
                + " asset(s) for a query reading their bytes");
        }
    }

    private boolean pendingCandidates(String from, String where, String[] args, String sortOrder,
                                      Map<String, String[]> into) {
        String sql = "SELECT s." + COL_DATA + ", p.local_id, p.modified_ms FROM (SELECT " + COL_DATA
            + " FROM " + from
            + (where.isEmpty() ? "" : " WHERE " + where)
            + (sortOrder == null || sortOrder.isEmpty() ? "" : " ORDER BY " + sortOrder)
            + ") s JOIN " + PHOTOS_TABLE + " p ON p.data=s." + COL_DATA + " AND p.exported=0";
        try (Cursor c = mDb.rawQuery(sql, args)) {
            while (c != null && c.moveToNext()) {
                into.putIfAbsent(c.getString(0), new String[]{c.getString(1), c.getString(2)});
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** `files`, with the still-unexported Photos rows reading `size` for _size. */
    private static String provisionalSizeFrom(long size) {
        StringBuilder sb = new StringBuilder("(SELECT ");
        for (String c : COLUMN_SET) {
            if (!COL_SIZE.equals(c)) sb.append(c).append(", ");
        }
        sb.append("CASE WHEN ").append(COL_DATA).append(" IN (SELECT data FROM ").append(PHOTOS_TABLE)
          .append(" WHERE exported=0) THEN ").append(size).append(" ELSE ").append(COL_SIZE)
          .append(" END AS ").append(COL_SIZE).append(" FROM ").append(TABLE).append(")");
        return sb.toString();
    }

    private static boolean mentionsSize(String sql) {
        return sql != null && sql.toLowerCase(Locale.ROOT).contains(COL_SIZE);
    }

    private boolean hasPendingPhotos() {
        try (Cursor c = mDb.rawQuery("SELECT 1 FROM " + PHOTOS_TABLE + " WHERE exported=0 LIMIT 1", null)) {
            return c != null && c.moveToFirst();
        } catch (Throwable t) {
            return false;                               // no table: no Photos rows
        }
    }

    /** {local_id, modified_ms, exported} when `data` is a Photos row, else null. */
    private String[] photoByData(String data) {
        try (Cursor c = mDb.rawQuery("SELECT local_id, modified_ms, exported FROM " + PHOTOS_TABLE
                + " WHERE data=?", new String[]{data})) {
            if (c != null && c.moveToFirst()) {
                return new String[]{c.getString(0), c.getString(1), c.getString(2)};
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private synchronized boolean exportPhoto(String localId, String data, long modMs) {
        File dest = new File(data);
        if (dest.exists()) {
            // Ours only if it carries this asset's owner tag (the index may have
            // been lost while the copy stayed): then it IS the export. Anything
            // else is an app's file at a name we had assigned: never overwrite it
            // (the next sync gives the asset a fresh name).
            if (!localId.equals(photosOwnerTag(dest))) return false;
        } else {
            if (!photosExport(localId, dest)) return false;
            if (modMs > 0) dest.setLastModified(modMs);
        }
        ContentValues pv = new ContentValues();
        pv.put("exported", 1);
        mDb.update(PHOTOS_TABLE, pv, "local_id=?", new String[]{localId});
        ContentValues cv = new ContentValues();
        cv.put(COL_SIZE, dest.length());
        mDb.update(TABLE, cv, COL_DATA + "=?", new String[]{data});
        return true;
    }

    private static File photoThumbDir() {
        return new File(dbFile().getParentFile(), "photo-thumbs");
    }

    private static String photoKey(String localId) {
        return Long.toHexString(fnv1a63(localId));
    }

    /** Deletes every cached thumbnail whose file name starts with one of `prefixes`
     *  (photoKey(id) + "_"): ONE directory listing per Photos pass however many
     *  assets went, not one per asset. */
    private static void deletePhotoThumbs(Set<String> prefixes) {
        if (prefixes.isEmpty()) return;
        File[] fs = photoThumbDir().listFiles();
        if (fs == null) return;
        for (File f : fs) {
            String n = f.getName();
            int us = n.indexOf('_');
            if (us > 0 && prefixes.contains(n.substring(0, us + 1))) f.delete();
        }
    }

    /** Leftover `.pending-*` temp files (see photosExport / photosThumbnail) older than
     *  a few minutes: an export bounded at 120 s that PhotoKit finished writing
     *  late, or a runtime killed mid-export. The walk never lists dot files, so
     *  without this they leak bytes invisibly. */
    private static final long PHOTOS_PENDING_STALE_MS = TimeUnit.MINUTES.toMillis(5);
    private static void photosSweepPending(File dir) {
        File[] fs = dir.listFiles((d, n) -> n.startsWith(".pending-"));
        if (fs == null) return;
        long now = System.currentTimeMillis();
        for (File f : fs) {
            if (now - f.lastModified() > PHOTOS_PENDING_STALE_MS) f.delete();
        }
    }

    /** After the macOS prompt is raised, watch for the user's answer and tell
     *  MediaStore observers the collections changed, as MediaProvider notifies
     *  after a scan -- so an open gallery refreshes the moment Photos is allowed. */
    private synchronized void startPhotosPoller() {
        if (mPhotosPoller) return;
        mPhotosPoller = true;
        Thread t = new Thread(() -> {
            long end = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(10);
            while (System.currentTimeMillis() < end) {
                try { Thread.sleep(500); } catch (InterruptedException e) { return; }
                if (photosStatus()
                        == PH_NOT_DETERMINED) continue;
                android.content.Context ctx = getContext();
                if (ctx != null) {
                    android.content.ContentResolver cr = ctx.getContentResolver();
                    cr.notifyChange(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null);
                    cr.notifyChange(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, null);
                    cr.notifyChange(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL), null);
                }
                return;
            }
        }, "macapk-photos-auth");
        t.setDaemon(true);
        t.start();
    }

    // The audio_playlists_map half of the scan. Rebuilt wholesale rather than
    // incrementally: a playlist's membership depends on which of its entries
    // currently resolve to indexed audio rows, so it can change when a MEDIA
    // file appears or disappears, not only when the playlist file itself is
    // touched -- an mtime fast-path would miss exactly that case. Playlists are
    // a handful of small text files, so rebuilding costs nothing measurable.
    private void syncPlaylistMembers() {
        Map<String, Long> byPath = new HashMap<>();
        List<long[]> playlists = new ArrayList<>();      // {id}
        List<String> playlistPaths = new ArrayList<>();
        try (Cursor c = mDb.query(TABLE, new String[]{COL_ID, COL_DATA, COL_MEDIA_TYPE},
                null, null, null, null, null)) {
            while (c != null && c.moveToNext()) {
                long id = c.getLong(0);
                String path = c.getString(1);
                int mt = c.getInt(2);
                if (mt == MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO) {
                    byPath.put(path, id);
                } else if (mt == MediaStore.Files.FileColumns.MEDIA_TYPE_PLAYLIST) {
                    playlists.add(new long[]{id});
                    playlistPaths.add(path);
                }
            }
        } catch (Throwable t) {
            com.macapk.loader.NativeLog.w(TAG, "syncPlaylistMembers: read failed: " + t);
            return;
        }

        try { mDb.delete(MAP_TABLE, null, null); } catch (Throwable ignored) {}
        for (int i = 0; i < playlists.size(); i++) {
            long playlistId = playlists.get(i)[0];
            File pf = new File(playlistPaths.get(i));
            List<String> entries = readPlaylist(pf);
            int order = 0;
            for (String entry : entries) {
                Long audioId = byPath.get(resolvePlaylistEntry(pf, entry));
                if (audioId == null) continue;   // entry names a file we have not indexed
                ContentValues cv = new ContentValues();
                cv.put(COL_AUDIO_ID, audioId);
                cv.put(COL_PLAYLIST_ID, playlistId);
                cv.put(COL_PLAY_ORDER, order++);
                try { mDb.insert(MAP_TABLE, null, cv); } catch (Throwable ignored) {}
            }
        }
    }

    // AOSP M3uPlaylistPersister.read / PlsPlaylistPersister.read
    // (platform_packages_providers_mediaprovider main,
    // src/com/android/providers/media/playlist/, fetched 2026-08-23), mirrored:
    //   M3U: every non-empty line that does not start with '#', with '\'
    //        rewritten to '/'.
    //   PLS: lines matching Pattern.compile("File(\\d+)=(.+)"), ordered by the
    //        captured index, same backslash rewrite.
    private static List<String> readPlaylist(File f) {
        List<String> out = new ArrayList<>();
        String name = f.getName().toLowerCase(Locale.ROOT);
        boolean pls = name.endsWith(".pls");
        try (java.io.BufferedReader r = new java.io.BufferedReader(
                new java.io.InputStreamReader(new java.io.FileInputStream(f)))) {
            if (pls) {
                Map<Integer, String> indexed = new java.util.TreeMap<>();
                String line;
                while ((line = r.readLine()) != null) {
                    java.util.regex.Matcher m = PATTERN_PLS.matcher(line);
                    if (m.matches()) {
                        indexed.put(Integer.parseInt(m.group(1)), m.group(2).replace('\\', '/'));
                    }
                }
                out.addAll(indexed.values());
            } else {
                String line;
                while ((line = r.readLine()) != null) {
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        out.add(line.replace('\\', '/'));
                    }
                }
            }
        } catch (Throwable t) {
            com.macapk.loader.NativeLog.w(TAG, "readPlaylist: " + f + ": " + t);
        }
        return out;
    }

    private static final java.util.regex.Pattern PATTERN_PLS =
        java.util.regex.Pattern.compile("File(\\d+)=(.+)");

    /** A playlist entry is either absolute or relative to the playlist's own
     *  directory; canonicalize so the result compares equal to the absolute
     *  paths stored in files._data. */
    private static String resolvePlaylistEntry(File playlistFile, String entry) {
        File f = entry.startsWith("/") ? new File(entry)
                                       : new File(playlistFile.getParentFile(), entry);
        // getCanonicalPath() resolves symlinks. That used to be harmless because
        // nothing under the volume was a link; now SharedStorage.mapUserFolders
        // (owner reversal 2026-09-28) can put the Music folder behind one, so a
        // playlist under it canonicalized to the HOST path
        // (/Users/<u>/Music/...) while syncPlaylistMembers keys byPath by _data,
        // which is the VOLUME-spelled path (File.getAbsolutePath() in syncOne
        // never resolves a link). Every playlist in a mapped folder then matched
        // zero members. Normalize LEXICALLY instead -- it still collapses "."
        // and ".." the way a real path comparison should, but never consults the
        // filesystem, so it keeps whichever spelling (volume or host) the
        // playlist file and its entries already agree on.
        return f.toPath().toAbsolutePath().normalize().toString();
    }

    private void syncOne(File f, File root, Map<String, long[]> existing, Set<String> seen) {
        String path = f.getAbsolutePath();
        seen.add(path);
        long mtimeSec = f.lastModified() / 1000L;
        long[] prior = existing.get(path);
        if (prior != null && prior[1] == mtimeSec) return; // unchanged since last sync

        String name = f.getName();
        int dot = name.lastIndexOf('.');
        String ext = dot >= 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        int mediaType = mediaTypeOf(ext);
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        if (mime == null || mime.isEmpty()) mime = defaultMimeFor(mediaType);

        int width = 0, height = 0;
        if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE) {
            try {
                android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                android.graphics.BitmapFactory.decodeFile(path, opts);
                width = Math.max(opts.outWidth, 0);
                height = Math.max(opts.outHeight, 0);
            } catch (Throwable ignored) {}
        }

        File parent = f.getParentFile();
        String bucketName = parent != null ? parent.getName() : null;
        // AOSP FileUtils bucket derivation, see class header citation:
        // bucket_id = hashCode() of the LOWERCASED absolute parent path.
        int bucketId = parent != null
            ? parent.getAbsolutePath().toLowerCase(Locale.ROOT).hashCode() : 0;

        String relPath = relativePathOf(parent, root);
        boolean isDownload = relPath != null
            && relPath.startsWith(Environment.DIRECTORY_DOWNLOADS + "/");

        long dateAdded = readCreationSeconds(f, mtimeSec);

        ContentValues cv = new ContentValues();
        cv.put(COL_DATA, path);
        cv.put(COL_DISPLAY_NAME, name);
        cv.put(COL_TITLE, stripExtension(name));
        cv.put(COL_SIZE, f.length());
        cv.put(COL_DATE_ADDED, dateAdded);
        cv.put(COL_DATE_MOD, mtimeSec);
        // DATE_TAKEN in MILLISECONDS (AOSP's unit for this column, unlike the
        // seconds used by DATE_ADDED/DATE_MODIFIED -- getting the unit wrong
        // would sort correctly and then display a 1970 date). File mtime is an
        // honest lower bound until EXIF parsing lands.
        cv.put(COL_DATE_TAKEN, mtimeSec * 1000L);
        cv.put(COL_MIME_TYPE, mime);
        cv.put(COL_MEDIA_TYPE, mediaType);
        cv.put(COL_WIDTH, width);
        cv.put(COL_HEIGHT, height);
        cv.put(COL_BUCKET_ID, bucketId);
        cv.put(COL_BUCKET_NAME, bucketName);
        cv.put(COL_REL_PATH, relPath);
        cv.put(COL_VOLUME_NAME, MediaStore.VOLUME_EXTERNAL_PRIMARY);
        cv.put(COL_IS_PENDING, 0);
        cv.put(COL_IS_FAVORITE, 0);
        cv.put(COL_IS_TRASHED, 0);
        cv.put(COL_IS_DOWNLOAD, isDownload ? 1 : 0);

        // AOSP ModernMediaScanner.scanItemAudio's pre-retriever baseline, plus
        // the key/id derivation MediaProvider.insert applies to it. See the
        // AudioColumns block at the top of this file for the citations and for
        // why stopping at the baseline is a real AOSP state rather than a
        // shortcut. Everything a MediaMetadataRetriever would refine (genre,
        // year, track, composer, album_artist, duration) is deliberately left
        // NULL/0 -- an app reads back an honest NULL, exactly as it does for a
        // real device file whose tags could not be parsed.
        if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO) {
            cv.put(COL_ARTIST, MediaStore.UNKNOWN_STRING);
            cv.put(COL_ALBUM, bucketName);
            computeAudioTypeValuesFromData(path, cv);
            computeAudioKeyValues(cv);
        } else if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_PLAYLIST) {
            // AOSP ModernMediaScanner.scanItemPlaylist (:1500-1512):
            //     op.withValue(PlaylistsColumns.NAME,
            //             FileUtils.extractFileName(file.getName()));
            // i.e. the file name with its extension removed, which is what
            // stripExtension() computes above for COL_TITLE.
            cv.put(COL_NAME, stripExtension(name));
        }

        if (prior != null) {
            mDb.update(TABLE, cv, COL_ID + "=?", new String[]{String.valueOf(prior[0])});
        } else {
            mDb.insert(TABLE, null, cv);
        }
    }

    private static int mediaTypeOf(String ext) {
        if (IMAGE_EXT.contains(ext)) return MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE;
        if (VIDEO_EXT.contains(ext)) return MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;
        if (AUDIO_EXT.contains(ext)) return MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO;
        if (PLAYLIST_EXT.contains(ext)) return MediaStore.Files.FileColumns.MEDIA_TYPE_PLAYLIST;
        return MediaStore.Files.FileColumns.MEDIA_TYPE_NONE;
    }

    // AOSP FileUtils.sAudioTypes + computeAudioTypeValuesFromData
    // (platform_packages_providers_mediaprovider main,
    // src/com/android/providers/media/util/FileUtils.java:1437-1470, fetched
    // 2026-08-23), mirrored verbatim including its fallback:
    //     final String lowPath = filePath.toLowerCase(Locale.ROOT);
    //     boolean anyMatch = false;
    //     for (...) { match = lowPath.contains('/' + dirName.toLowerCase + '/');
    //                 consumer.accept(column, match ? 1 : 0); anyMatch |= match; }
    //     if (!anyMatch) consumer.accept(AudioColumns.IS_MUSIC, 1);
    // i.e. is_music/is_ringtone/is_alarm/is_notification/is_podcast/
    // is_audiobook/is_recording are derived from the PATH alone, no tag parsing,
    // and anything outside those directories counts as music. This is what makes
    // the audio_albums / audio_artists views (which filter is_music=1) return
    // rows at all on a scan-only index.
    //
    // "Audiobooks" / "Recordings" are string literals rather than
    // Environment.DIRECTORY_* because android.os.Environment in this tree does
    // not declare DIRECTORY_AUDIOBOOKS or DIRECTORY_RECORDINGS yet; the values
    // are AOSP's own (Environment.java). Swap them for the constants when that
    // file gains them.
    private static final String[][] AUDIO_TYPE_DIRS = {
        { Environment.DIRECTORY_RINGTONES,     COL_IS_RINGTONE  },
        { Environment.DIRECTORY_NOTIFICATIONS, COL_IS_NOTIF     },
        { Environment.DIRECTORY_ALARMS,        COL_IS_ALARM     },
        { Environment.DIRECTORY_PODCASTS,      COL_IS_PODCAST   },
        { "Audiobooks",                        COL_IS_AUDIOBOOK },
        { Environment.DIRECTORY_MUSIC,         COL_IS_MUSIC     },
        { "Recordings",                        COL_IS_RECORDING },
    };

    private static void computeAudioTypeValuesFromData(String filePath, ContentValues out) {
        final String lowPath = filePath.toLowerCase(Locale.ROOT);
        boolean anyMatch = false;
        for (String[] pair : AUDIO_TYPE_DIRS) {
            boolean match = lowPath.contains('/' + pair[0].toLowerCase(Locale.ROOT) + '/');
            out.put(pair[1], match ? 1 : 0);
            anyMatch |= match;
        }
        if (!anyMatch) out.put(COL_IS_MUSIC, 1);
    }

    // AOSP MediaProvider.computeAudioKeyValue (MediaProvider.java:2790-2810,
    // fetched 2026-08-23): the *_KEY column is Audio.keyFor(value), and the
    // matching *_ID is a 63-bit hash of (key + hashValue) --
    //     final long id = Hashing.farmHashFingerprint64()
    //             .hashString(key + hashValue, UTF_8).asLong() & ~(1L << 63);
    // with the comment "Many apps break if we generate negative IDs, so trim off
    // the highest bit to ensure we're always unsigned".
    //
    // ONE deviation, and it is not observable: farmhash is Guava, which this
    // runtime does not ship, so the digest below is FNV-1a 64. The id is an
    // OPAQUE grouping token -- an app only ever compares it to other ids from
    // this same provider, or appends it to content://media/.../audio/albums/<id>
    // which comes straight back here -- so what has to hold is determinism and
    // the cleared sign bit, both of which do. It must NOT be
    // String.hashCode(): 32 bits over a corpus keyed by album name collides far
    // too readily, and a collision silently MERGES two albums.
    private static void computeAudioKeyValue(ContentValues values, String focus,
            String focusKey, String focusId, int hashValue) {
        if (focusKey != null) values.remove(focusKey);
        if (focusId != null) values.remove(focusId);
        final String value = values.getAsString(focus);
        if (value == null || value.isEmpty()) return;
        final String key = MediaStore.Audio.keyFor(value);
        if (key == null) return;
        if (focusKey != null) values.put(focusKey, key);
        if (focusId != null) values.put(focusId, fnv1a63(key + hashValue));
    }

    private static long fnv1a63(String s) {
        long h = 0xcbf29ce484222325L;
        for (int i = 0; i < s.length(); i++) {
            h ^= s.charAt(i);
            h *= 0x100000001b3L;
        }
        return h & ~(1L << 63);
    }

    /** AOSP MediaProvider.computeAudioAlbumKeyValue: "To distinguish same-named
     *  albums, we append a hash. The hash is based on the 'album artist' tag if
     *  present, otherwise on the path of the parent directory of the audio
     *  file." (MediaProvider.java:2769-2788.) */
    private static void computeAudioKeyValues(ContentValues values) {
        computeAudioKeyValue(values, COL_TITLE, COL_TITLE_KEY, null, 0);
        computeAudioKeyValue(values, COL_ARTIST, COL_ARTIST_KEY, COL_ARTIST_ID, 0);
        computeAudioKeyValue(values, COL_GENRE, COL_GENRE_KEY, COL_GENRE_ID, 0);

        int hashCode = 0;
        final String albumArtist = values.getAsString(COL_ALBUM_ARTIST);
        if (albumArtist != null && !albumArtist.isEmpty()) {
            hashCode = albumArtist.hashCode();
        } else {
            final String path = values.getAsString(COL_DATA);
            if (path != null && !path.isEmpty() && path.lastIndexOf('/') > 0) {
                hashCode = path.substring(0, path.lastIndexOf('/')).hashCode();
            }
        }
        computeAudioKeyValue(values, COL_ALBUM, COL_ALBUM_KEY, COL_ALBUM_ID, hashCode);
    }

    private static String defaultMimeFor(int mediaType) {
        if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE) return "image/*";
        if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO) return "audio/*";
        if (mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) return "video/*";
        return "application/octet-stream";
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    // AOSP: "The relative path for files in the top directory is '/'"
    // (FileUtils.java, same method cited in the class header).
    private static String relativePathOf(File parentDir, File root) {
        if (parentDir == null) return null;
        String rootPath = root.getAbsolutePath();
        String parentPath = parentDir.getAbsolutePath();
        if (parentPath.equals(rootPath)) return "/";
        String prefix = rootPath + File.separator;
        if (parentPath.startsWith(prefix)) {
            return parentPath.substring(prefix.length()).replace(File.separatorChar, '/') + "/";
        }
        return null; // not under root; shouldn't happen given walk()'s construction
    }

    // macOS/APFS exposes a real file creation time distinct from mtime;
    // fall back to mtime (already the honest "we don't know" answer real
    // Android gives when a file's true add-time isn't tracked) if the
    // filesystem doesn't report one.
    private static long readCreationSeconds(File f, long fallbackSeconds) {
        try {
            BasicFileAttributes attrs = Files.readAttributes(f.toPath(), BasicFileAttributes.class);
            long ctimeSec = attrs.creationTime().to(TimeUnit.SECONDS);
            return ctimeSec > 0 ? ctimeSec : fallbackSeconds;
        } catch (Throwable t) {
            return fallbackSeconds;
        }
    }

    // AOSP FileUtils.isDirectoryHidden(), simplified (see class header for
    // the two omitted carve-outs).
    private static boolean isDirectoryHidden(File dir, File root) {
        String name = dir.getName();
        if (name.startsWith(".")) return true;
        if (!new File(dir, ".nomedia").exists()) return false;
        File parent = dir.getParentFile();
        boolean isTopLevel = parent != null && parent.equals(root);
        if (isTopLevel && TOP_LEVEL_VISIBLE.contains(name)) return false; // never hidden
        if (parent != null && "Camera".equals(name) && "DCIM".equals(parent.getName())
                && parent.getParentFile() != null && parent.getParentFile().equals(root)) {
            return false; // DCIM/Camera always visible
        }
        return true;
    }

    // ---- the volume walk over the REAL Mac folders ------------------------
    //
    // Owner reversal 2026-09-28 (docs/shared-storage-real-mac-folders-design_2026-09-25.md):
    // Pictures, Movies, Music, Download and Documents in the volume root are links
    // to the Mac user's folders (SharedStorage.mapUserFolders). Three host facts
    // the old walk did not have to know, each behind MACAPK_MEDIASTORE_HOSTSCAN
    // (default ON; =0 is the old walk, which follows every link):
    //   1. LINKS. AOSP's scanner walks with Files.walkFileTree(mRoot.toPath(), this)
    //      (ModernMediaScanner.java walkFileTree(), android main), which is
    //      walkFileTree(start, EnumSet.noneOf(FileVisitOption.class), MAX_VALUE,
    //      visitor): it never follows a symbolic link. The volume's own mapping
    //      links are the equivalent of its mounts and are followed; no other link
    //      is (a link cycle inside ~/Documents would otherwise recurse forever).
    //   2. PACKAGES. A macOS package (Photos Library.photoslibrary, Music
    //      Library.musiclibrary, an .app) is ONE document to the Mac user; its
    //      internals (Photos' originals/, derivatives/) are not the user's loose
    //      files and would flood a gallery with every thumbnail Photos keeps.
    //      The Photos library is exposed through PhotoKit instead (design note).
    //   3. TCC. ~/Documents and ~/Downloads are privacy-protected: the first read
    //      makes macOS ask, naming the app. They are scanned only for an app
    //      with All files access -- the only apps AOSP lets see the non-media
    //      files there, and the ones that asked for "all files" in the first
    //      place (design 3e) -- so a music player opening MediaStore does not
    //      raise a Documents prompt. The media files the user keeps in
    //      Pictures/Movies/Music are unaffected.
    // A mapped tree whose walk is SLOW (>= 250 ms: a large Music or Documents)
    // is re-walked at most every 5x its last walk time, so one gallery burst of
    // queries does not pay the walk once per query; small trees are walked on
    // every query exactly as before.

    static boolean hostScanEnabled() {
        String e = System.getenv("MACAPK_MEDIASTORE_HOSTSCAN");
        return e == null || e.isEmpty() || e.charAt(0) != '0';
    }

    /** Folder extensions macOS presents as a single document (LaunchServices
     *  packages): the media libraries and the bundle types a user's Pictures /
     *  Music / Documents actually hold. */
    static final Set<String> HOST_PACKAGE_EXT = new HashSet<>(Arrays.asList(
        "photoslibrary", "photolibrary", "migratedphotolibrary", "aplibrary",
        "musiclibrary", "tvlibrary", "imovielibrary", "theater", "fcpbundle",
        "logicx", "band", "app", "bundle", "framework", "plugin", "kext",
        "xcodeproj", "xcworkspace", "playground", "rtfd", "pages", "numbers",
        "key", "sparsebundle", "photobooth", "lrlibrary", "lrdata", "cocatalog"));

    /** True for a macOS package: a directory whose extension names one of the
     *  known package types, OR (review of 2026-09-28) any directory that
     *  carries Contents/Info.plist -- CFBundle's own definition of a package,
     *  which some real ones use with no matching extension at all (Photo
     *  Booth's own library is literally named "Photo Booth Library", no dot).
     *  `public` so android.app.usage.StorageStatsManager's size walk can skip
     *  the same folders MediaStore never scans into. */
    public static boolean isHostPackage(String dirName, File dir) {
        int dot = dirName.lastIndexOf('.');
        if (dot > 0 && HOST_PACKAGE_EXT.contains(
                dirName.substring(dot + 1).toLowerCase(Locale.ROOT))) {
            return true;
        }
        try { return new File(dir, "Contents/Info.plist").isFile(); }
        catch (Throwable t) { return false; }
    }

    /** The volume folders that are TCC-protected Mac folders when mapped. */
    static boolean isPrivacyProtectedMapping(String volumeName) {
        return Environment.DIRECTORY_DOCUMENTS.equals(volumeName)
            || Environment.DIRECTORY_DOWNLOADS.equals(volumeName);
    }

    private static final Map<String, Object[]> sMappedWalks = new HashMap<>(); // path -> {endMs, durMs, List<File>}

    /** Walk the whole volume into `out`. `a` is the caller's access() bitmask:
     *  it decides both whether the TCC-protected Documents/Download mappings
     *  are walked at all (below) and, later, which rows accessWhere() lets the
     *  caller see -- an app with only READ_MEDIA_IMAGES must still find its
     *  images under Download/Documents, same as AOSP (AccessChecker filters by
     *  MEDIA TYPE, never by folder). */
    static void walkVolume(File root, List<File> out, int a) {
        if (!hostScanEnabled()) { walk(root, root, out); return; }
        File[] children = root.listFiles();
        if (children == null) return;
        for (File f : children) {
            String name = f.getName();
            if (name.startsWith(".")) continue;
            if (Files.isSymbolicLink(f.toPath())) {
                // A root-level link is followed as a mount ONLY when it is one
                // of SharedStorage's own mapping links, by name AND target.
                // Nothing refuses a JAVA-created symlink here (android.system.Os
                // / java.nio can make one with any name pointing anywhere), so
                // trusting "only the mapping links live here" would let a guest
                // walk the whole disk with e.g. /sdcard/x -> / (review of
                // 2026-09-28; AOSP's own scanner never follows a link at all --
                // ModernMediaScanner.walkFileTree uses the 2-arg
                // Files.walkFileTree, which defaults FOLLOW_LINKS off).
                if (!com.macapk.loader.SharedStorage.isRecognizedFolderLink(f)) continue;
                // Skip Download/Documents unless the caller has SOME grant that
                // would let it see rows there -- All files, legacy read, or any
                // READ_MEDIA_* -- not only All files as before: a plain
                // READ_MEDIA_IMAGES grant must still surface images that live in
                // ~/Downloads or ~/Documents, exactly as accessWhere() already
                // lets it see images anywhere else once they are indexed.
                if (isPrivacyProtectedMapping(name)
                        && (a & (A_MANAGER | A_LEGACY_READ | A_IMAGES | A_VIDEO | A_AUDIO)) == 0) {
                    continue;
                }
                if (!f.isDirectory() || isDirectoryHidden(f, root)) continue;
                out.addAll(walkMapped(f, root));
                continue;
            }
            if (f.isDirectory()) {
                if (isDirectoryHidden(f, root)) continue;
                walk(f, root, out);
            } else if (f.isFile()) {
                out.add(f);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static List<File> walkMapped(File top, File root) {
        String key = top.getAbsolutePath();
        long now = System.currentTimeMillis();
        synchronized (sMappedWalks) {
            Object[] c = sMappedWalks.get(key);
            if (c != null) {
                long end = (Long) c[0], dur = (Long) c[1];
                if (dur >= 250 && now - end < 5 * dur) return (List<File>) c[2];
            }
        }
        List<File> files = new ArrayList<>();
        long t0 = System.currentTimeMillis();
        walk(top, root, files);
        long t1 = System.currentTimeMillis();
        synchronized (sMappedWalks) {
            sMappedWalks.put(key, new Object[]{t1, t1 - t0, files});
        }
        return files;
    }

    // ---- who may read what (AOSP AccessChecker) -----------------------------
    //
    // Owner reversal 2026-09-28: the volume's Pictures/Movies/Music are the Mac
    // user's real folders, so the index now holds the user's own photos, and a
    // MediaStore query must show them only to an app the user allowed, as
    // MediaProvider does. Mirrored from AccessChecker.java (android main):
    //   hasAccessToCollection: IMAGES_* -> checkCallingPermissionImages, VIDEO_*
    //     -> ...Video, AUDIO_* (media, playlists, albums, artists, genres,
    //     members) -> ...Audio, FILES/DOWNLOADS -> legacy read;
    //   getWhereForConstrainedAccess (no collection access): AUDIO_MEDIA = owner
    //     OR is_ringtone=1 OR is_alarm=1 OR is_notification=1; IMAGES/VIDEO/
    //     PLAYLISTS = owner; parsed audio tables = NO_ACCESS_SQL; FILES = owner
    //     OR each readable media type (audio+playlist+subtitle / video+subtitle
    //     / image); DOWNLOADS = owner;
    //   checkCallingPermissionGlobal: an All-files (manager) app sees everything.
    // checkCallingPermissionImages is PERMISSION_READ_IMAGES, i.e.
    // READ_MEDIA_IMAGES at targetSdk 33+ and READ_EXTERNAL_STORAGE below
    // (PermissionUtils); SpecialAccessStore's permission model already answers
    // READ_EXTERNAL_STORAGE DENIED where it no longer counts, so "either one"
    // is the same rule. We record no owner_package_name, so "owner" matches
    // nothing: an app without the permission sees none of its own inserts
    // either (a known, stricter-than-AOSP gap). MACAPK_MEDIASTORE_ACCESS=0
    // (default ON) restores the old index-wide answer.

    static boolean accessGateEnabled() {
        String e = System.getenv("MACAPK_MEDIASTORE_ACCESS");
        return e == null || e.isEmpty() || e.charAt(0) != '0';
    }

    /** What the calling app may read, as bits (an int rather than a nested
     *  class on purpose: a new framework CLASS moves FRAMEWORK_ID and re-converts
     *  every installed app's DEX, see CLAUDE.md "Runtime freshness"). */
    static final int A_ALL = 1, A_MANAGER = 2, A_LEGACY_READ = 4,
                     A_IMAGES = 8, A_VIDEO = 16, A_AUDIO = 32, A_LEGACY_WRITE = 64;
    static final int A_EVERYTHING =
        A_ALL | A_MANAGER | A_LEGACY_READ | A_IMAGES | A_VIDEO | A_AUDIO | A_LEGACY_WRITE;

    static int access() {
        boolean manager = false;
        try { manager = Environment.isExternalStorageManager(); } catch (Throwable ignored) {}
        if (!accessGateEnabled()) return A_EVERYTHING & (manager ? ~0 : ~A_MANAGER);
        if (manager) return A_EVERYTHING;
        boolean read = granted(com.macapk.loader.SpecialAccessStore.PERM_READ_EXTERNAL_STORAGE);
        boolean write = granted(com.macapk.loader.SpecialAccessStore.PERM_WRITE_EXTERNAL_STORAGE);
        boolean legacy = false;
        try { legacy = Environment.isExternalStorageLegacy(); } catch (Throwable ignored) {}
        int a = 0;
        if (legacy && read) a |= A_LEGACY_READ;
        if (legacy && write) a |= A_LEGACY_WRITE;
        if (read || granted(com.macapk.loader.SpecialAccessStore.PERM_READ_MEDIA_IMAGES)) a |= A_IMAGES;
        if (read || granted(com.macapk.loader.SpecialAccessStore.PERM_READ_MEDIA_VIDEO)) a |= A_VIDEO;
        if (read || granted(com.macapk.loader.SpecialAccessStore.PERM_READ_MEDIA_AUDIO)) a |= A_AUDIO;
        return a;
    }

    /** AOSP hasAccessToCollection(..., forWrite=true): a caller with only
     *  READ_MEDIA_* (or plain READ_EXTERNAL_STORAGE on an S5-legacy app) is
     *  restricted to reading; writing needs the legacy WRITE grant or All
     *  files. Used by openFile() below. */
    static boolean writeAllowed(int a) {
        return (a & (A_MANAGER | A_LEGACY_WRITE)) != 0;
    }

    /** True when `pfdMode` (ParcelFileDescriptor.parseMode's result) asks for
     *  any kind of write: WRITE_ONLY, READ_WRITE, TRUNCATE or APPEND. Pulled
     *  out of openFile() so it (and writeAllowed above) can be exercised
     *  offline without a database -- see debug/userfolders_gate.sh.
     *
     *  Tests WRITE_ONLY's bit alone, not READ_WRITE's: ParcelFileDescriptor
     *  defines MODE_READ_WRITE = MODE_READ_ONLY | MODE_WRITE_ONLY (see that
     *  file's own parseMode citation), so MODE_READ_ONLY's bit
     *  (0x10000000) is already a SUBSET of MODE_READ_WRITE's pattern
     *  (0x30000000) -- ORing the READ_WRITE constant into this mask would
     *  therefore also match a bare MODE_READ_ONLY. WRITE_ONLY's bit
     *  (0x20000000) is the one READ_WRITE carries that READ_ONLY does not. */
    static boolean modeWantsWrite(int pfdMode) {
        return (pfdMode & (ParcelFileDescriptor.MODE_WRITE_ONLY
                | ParcelFileDescriptor.MODE_TRUNCATE
                | ParcelFileDescriptor.MODE_APPEND)) != 0;
    }

    /** checkPermission's answer for a storage permission: the S5 model when it
     *  decides, else the pre-S5 rule (declared == held). */
    private static boolean granted(String perm) {
        try {
            Boolean b = com.macapk.loader.SpecialAccessStore.storagePermissionGranted(perm);
            if (b != null) return b;
            return com.macapk.loader.SpecialAccessStore.declaresPermission(perm);
        } catch (Throwable t) {
            return false;
        }
    }

    /** The access WHERE clause for a collection route, or null for no filter. */
    static String accessWhere(int mediaTypeFilter, boolean downloadsOnly, int a) {
        if ((a & A_ALL) != 0) return null;
        final boolean images = (a & A_IMAGES) != 0, video = (a & A_VIDEO) != 0,
                      audio = (a & A_AUDIO) != 0, legacyRead = (a & A_LEGACY_READ) != 0;
        final int IMG = MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE;
        final int VID = MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;
        final int AUD = MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO;
        final int PLS = MediaStore.Files.FileColumns.MEDIA_TYPE_PLAYLIST;
        final int SUB = MediaStore.Files.FileColumns.MEDIA_TYPE_SUBTITLE;
        final String NONE = "0";                 // AccessChecker NO_ACCESS_SQL / owner match
        if (downloadsOnly) return legacyRead ? null : NONE;
        if (mediaTypeFilter == IMG) return images ? null : NONE;
        if (mediaTypeFilter == VID) return video ? null : NONE;
        if (mediaTypeFilter == PLS) return audio ? null : NONE;
        if (mediaTypeFilter == AUD) {
            return audio ? null
                : "(" + COL_IS_RINGTONE + "=1 OR " + COL_IS_ALARM + "=1 OR " + COL_IS_NOTIF + "=1)";
        }
        if (mediaTypeFilter >= 0) return NONE;   // a collection with no AOSP read rule
        // FILES
        if (legacyRead) return null;
        List<Integer> types = new ArrayList<>();
        if (audio) { types.add(AUD); types.add(PLS); types.add(SUB); }
        if (video) { types.add(VID); if (!types.contains(SUB)) types.add(SUB); }
        if (images) types.add(IMG);
        if (types.isEmpty()) return NONE;
        StringBuilder sb = new StringBuilder(COL_MEDIA_TYPE).append(" IN (");
        for (int i = 0; i < types.size(); i++) { if (i > 0) sb.append(','); sb.append(types.get(i)); }
        return sb.append(')').toString();
    }

    /** May the app read the derived sub-table under content://media/<v>/<kind>/... ? */
    static boolean subVisible(String kind, int a) {
        if ((a & A_ALL) != 0) return true;
        if ("images".equals(kind)) return (a & A_IMAGES) != 0;
        if ("video".equals(kind)) return (a & A_VIDEO) != 0;
        if ("audio".equals(kind)) return (a & A_AUDIO) != 0;
        return (a & A_LEGACY_READ) != 0;
    }

    private static void walk(File dir, File root, List<File> out) {
        File[] children = dir.listFiles();
        if (children == null) return;
        final boolean host = hostScanEnabled();
        for (File f : children) {
            String name = f.getName();
            if (name.startsWith(".")) continue;
            if (host && Files.isSymbolicLink(f.toPath())) continue;   // AOSP: no FOLLOW_LINKS
            if (f.isDirectory()) {
                if (host && isHostPackage(name, f)) continue;
                if (isDirectoryHidden(f, root)) continue;
                walk(f, root, out);
            } else if (f.isFile()) {
                out.add(f);
            }
        }
    }

    // ==== THE PHOTOS SOURCE ================================================
    // The macOS Photos LIBRARY as a read-only media source for this provider.
    //
    // A Mac's camera roll is the Photos library, not loose files, so without this an
    // Android gallery shows an almost empty volume. The Photos pass above asks this section
    // for the library's assets and exposes them as rows under DCIM/Camera/ (AOSP
    // FileUtils CAMERA_RELATIVE_PATH = "DCIM/Camera/", the one collection a device keeps
    // visible regardless of .nomedia), exporting an asset's bytes into that folder only
    // when an app actually reads its _data or opens it. Design and decisions:
    // docs/photokit-mediastore-design_2026-09-28.md; lane note with the measured
    // behaviour: docs/photos-library-mediastore_2026-09-29.md.
    //
    // TWO BACKENDS, NO NEW CLASS. This lives INSIDE MediaStoreProvider and its natives
    // are registered on this class, because ANY new class in either framework jar moves
    // FRAMEWORK_ID (runtime/scripts/build_ids.py hashes every class name) and makes
    // every installed app re-convert its DEX on its next launch; methods do not:
    //   - PhotoKit, through runtime/src/photos_bridge.mm (the natives below). The only
    //     backend in a shipped app.
    //   - A DIRECTORY, when MACAPK_PHOTOKIT_SOURCE_DIR names one (or the system
    //     property macapk.photokit.source.dir). DIAGNOSTIC ONLY: every regular file in
    //     it is an "asset", and the optional files .status (notdetermined / restricted /
    //     denied / authorized / limited) and .canprompt steer the authorization state.
    //     It exists so the offline gate (debug/photos_gate.sh) and a live gallery run
    //     can exercise the whole MediaStore side without anybody's real library and
    //     without a TCC prompt a harness cannot answer.
    //
    // GRANT ORDER (design decision 3): the Photos pass calls in here only when the
    // APP holds READ_MEDIA_IMAGES or READ_MEDIA_VIDEO (or a grant that implies them).
    // Then, and only from a bundle whose Info.plist carries
    // NSPhotoLibraryUsageDescription, photosRequestAuthorization() raises macOS's own Photos
    // prompt, once, without blocking. PHAuthorizationStatusLimited ("selected photos")
    // needs nothing special: PhotoKit's fetch returns exactly the selected assets,
    // which is the READ_MEDIA_VISUAL_USER_SELECTED shape.
    //
    // Gate: MACAPK_PHOTOKIT, default ON. =0: photosEnabled() is false and nothing here
    // is ever called: the pre-change provider byte for byte (debug/photos_gate.sh).

    /** PHAuthorizationStatus values (Photos.framework PHPhotoLibrary.h:19). */
    static final int PH_NOT_DETERMINED = 0, PH_RESTRICTED = 1, PH_DENIED = 2,
                            PH_AUTHORIZED = 3, PH_LIMITED = 4;
    /** No backend at all (no natives bound, e.g. a plain JVM). */
    static final int PH_UNAVAILABLE = -1;

    /** Record fields returned by photosList(). */
    static final int PH_F_ID = 0, PH_F_MEDIA_TYPE = 1, PH_F_CREATED_MS = 2, PH_F_MODIFIED_MS = 3,
                            PH_F_WIDTH = 4, PH_F_HEIGHT = 5, PH_F_DURATION_MS = 6, PH_F_NAME = 7, PH_F_MIME = 8;

    private static final java.util.concurrent.atomic.AtomicLong sPhotosSourceCalls = new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong sPhotosRequests = new java.util.concurrent.atomic.AtomicLong();

    private static native int nativePhotosStatus();
    private static native boolean nativePhotosCanPrompt();
    private static native boolean nativePhotosRequestAuth();
    private static native long nativePhotosGeneration();
    private static native String[] nativePhotosList(boolean images, boolean videos);
    private static native String nativePhotosExport(String localId, String path);
    private static native boolean nativePhotosThumbnail(String localId, int w, int h, String path);

    /** MACAPK_PHOTOKIT, default ON. */
    static boolean photosEnabled() {
        String e = System.getenv("MACAPK_PHOTOKIT");
        return e == null || e.isEmpty() || e.charAt(0) != '0';
    }

    /** The diagnostic directory backend, or null for PhotoKit. */
    static File photosSourceDir() {
        String d = System.getProperty("macapk.photokit.source.dir");
        if (d == null || d.isEmpty()) d = System.getenv("MACAPK_PHOTOKIT_SOURCE_DIR");
        return (d == null || d.isEmpty()) ? null : new File(d);
    }

    /** How many times a backend was consulted. The offline gate reads it to prove
     *  that an app WITHOUT a media grant never reaches Photos at all. */
    static long photosSourceCalls() { return sPhotosSourceCalls.get(); }
    /** How many authorization requests were actually issued (prompts raised). */
    static long photosRequests() { return sPhotosRequests.get(); }

    static boolean photosReadable(int status) {
        return status == PH_AUTHORIZED || status == PH_LIMITED;
    }

    static int photosStatus() {
        sPhotosSourceCalls.incrementAndGet();
        File dir = photosSourceDir();
        if (dir != null) {
            String s = photosReadSmall(new File(dir, ".status"));
            if (s == null) return PH_AUTHORIZED;
            switch (s.trim().toLowerCase(Locale.ROOT)) {
                case "notdetermined": return PH_NOT_DETERMINED;
                case "restricted": return PH_RESTRICTED;
                case "denied": return PH_DENIED;
                case "limited": return PH_LIMITED;
                default: return PH_AUTHORIZED;
            }
        }
        try { return nativePhotosStatus(); } catch (Throwable t) { return PH_UNAVAILABLE; }
    }

    /** Raise macOS's Photos prompt, once per process, never blocking. True when a
     *  request was actually issued. A bundle without the purpose string never asks
     *  (TCC would terminate it). */
    static boolean photosRequestAuthorization() {
        boolean issued;
        File dir = photosSourceDir();
        if (dir != null) {
            issued = new File(dir, ".canprompt").exists() && sPhotosRequests.get() == 0;
        } else {
            try { issued = nativePhotosRequestAuth(); } catch (Throwable t) { issued = false; }
        }
        if (issued) {
            sPhotosRequests.incrementAndGet();
            com.macapk.loader.NativeLog.i(TAG, "Photos access requested (macOS asks the user)");
        }
        return issued;
    }

    /** Bumped by the library's change observer and by an authorization answer; a
     *  caller re-lists only when it moves. The directory backend has no observer,
     *  so it reports a new generation every time (it is cheap to re-list). */
    static long photosGeneration() {
        File dir = photosSourceDir();
        if (dir != null) {
            // DIAGNOSTIC: a .generation file pins the generation so the gate can
            // exercise the listing cache (deny -> re-allow must never reuse it).
            String g = photosReadSmall(new File(dir, ".generation"));
            return g != null ? parseLong(g.trim()) : System.nanoTime();
        }
        try { return nativePhotosGeneration(); } catch (Throwable t) { return 0; }
    }

    /** Every asset of the requested kinds, as F_* records, or null when the library
     *  is not readable. An EMPTY list is an authoritative "there is nothing" (for
     *  example a limited selection of zero); null is "cannot say", and a caller
     *  must never delete rows on null. */
    static List<String[]> photosList(boolean images, boolean videos) {
        if (!images && !videos) return new ArrayList<>();
        sPhotosSourceCalls.incrementAndGet();
        File dir = photosSourceDir();
        if (dir != null) return photosListDir(dir, images, videos);
        String[] raw;
        try { raw = nativePhotosList(images, videos); } catch (Throwable t) { return null; }
        if (raw == null) return null;
        List<String[]> out = new ArrayList<>(raw.length);
        for (String r : raw) {
            String[] f = r.split("\t", -1);
            if (f.length == 9 && !f[PH_F_ID].isEmpty()) out.add(f);
        }
        return out;
    }

    /** Exports asset `id` to `dest` (its current rendition, local data only).
     *  Written beside `dest` under a dot-name first, so no walker ever sees a
     *  half-written file, then renamed into place. Refused when the volume is at
     *  AOSP's low-storage threshold (lowBytes), and undone if the copy pushed it
     *  there: a gallery scroll must never fill the Mac's disk. */
    static boolean photosExport(String id, File dest) {
        File parent = dest.getParentFile();
        if (parent == null) return false;
        parent.mkdirs();
        long low = photosLowBytes(parent);
        if (parent.getUsableSpace() <= low) {
            com.macapk.loader.NativeLog.w(TAG, "export skipped, storage low: " + dest.getName());
            return false;
        }
        File tmp = new File(parent, ".pending-" + System.nanoTime() + "-" + dest.getName());
        String err;
        sPhotosSourceCalls.incrementAndGet();
        File dir = photosSourceDir();
        if (dir != null) {
            err = photosCopyFromDir(dir, id, tmp);
        } else {
            try { err = nativePhotosExport(id, tmp.getAbsolutePath()); }
            catch (Throwable t) { err = String.valueOf(t); }
        }
        if (err != null || !tmp.isFile()) {
            tmp.delete();
            com.macapk.loader.NativeLog.w(TAG, "export failed for " + dest.getName() + ": " + err);
            return false;
        }
        // Ownership travels with the file (see photosOwnerTag): tagged BEFORE the
        // rename so no copy is ever visible untagged.
        photosTagFile(tmp, id);
        if (parent.getUsableSpace() <= low) {
            tmp.delete();
            com.macapk.loader.NativeLog.w(TAG, "export undone, storage low after writing " + dest.getName());
            return false;
        }
        if (!tmp.renameTo(dest)) {
            tmp.delete();
            return false;
        }
        return true;
    }

    /** The user xattr that marks a file as OUR export of one Photos asset; its value
     *  is the asset's PhotoKit localIdentifier. The index (external.db) is a cache
     *  that may be dropped at any time (a schema bump, a deleted file), and without
     *  a record that survives it, a lost index would export the library a second
     *  time beside the first copies and leave the old ones behind after a revoke.
     *  Ownership lives WITH the copy instead: a file we wrote carries it, an app's
     *  file never does. (macOS keeps xattrs across rename, and APFS/HFS+ store
     *  them; a volume without xattr support loses only this recovery, and the
     *  index alone then decides, as before.) */
    static final String PHOTOS_OWNER_XATTR = "org.macapk.photos.id";
    private static final java.util.concurrent.atomic.AtomicBoolean sTagWarned =
        new java.util.concurrent.atomic.AtomicBoolean();

    static boolean photosTagFile(File f, String id) {
        try {
            java.nio.file.attribute.UserDefinedFileAttributeView v = Files.getFileAttributeView(
                f.toPath(), java.nio.file.attribute.UserDefinedFileAttributeView.class,
                java.nio.file.LinkOption.NOFOLLOW_LINKS);
            if (v == null) throw new UnsupportedOperationException("no user xattrs here");
            v.write(PHOTOS_OWNER_XATTR, java.nio.ByteBuffer.wrap(
                id.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            return true;
        } catch (Throwable t) {
            if (sTagWarned.compareAndSet(false, true)) {
                com.macapk.loader.NativeLog.w(TAG, "cannot tag exported Photos copies (" + t
                    + "); losing the media index will leave them behind");
            }
            return false;
        }
    }

    /** The asset id `f` was exported for, or null when it is not one of our exports. */
    static String photosOwnerTag(File f) {
        try {
            java.nio.file.attribute.UserDefinedFileAttributeView v = Files.getFileAttributeView(
                f.toPath(), java.nio.file.attribute.UserDefinedFileAttributeView.class,
                java.nio.file.LinkOption.NOFOLLOW_LINKS);
            if (v == null || !v.list().contains(PHOTOS_OWNER_XATTR)) return null;
            java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(v.size(PHOTOS_OWNER_XATTR));
            v.read(PHOTOS_OWNER_XATTR, b);
            b.flip();
            return java.nio.charset.StandardCharsets.UTF_8.decode(b).toString();
        } catch (Throwable t) {
            return null;
        }
    }

    /** A JPEG no smaller than needed to fill w x h, rendered by the backend
     *  (PhotoKit renders it from its own caches, so a grid needs no full export).
     *  The directory backend hands back the original bytes; ContentResolver.
     *  loadThumbnail scales whatever it gets, as AOSP's does. */
    static boolean photosThumbnail(String id, int w, int h, File dest) {
        File parent = dest.getParentFile();
        if (parent != null) parent.mkdirs();
        File tmp = new File(parent, ".pending-" + System.nanoTime() + "-" + dest.getName());
        boolean ok;
        sPhotosSourceCalls.incrementAndGet();
        File dir = photosSourceDir();
        if (dir != null) {
            ok = photosCopyFromDir(dir, id, tmp) == null;
        } else {
            try { ok = nativePhotosThumbnail(id, w, h, tmp.getAbsolutePath()); }
            catch (Throwable t) { ok = false; }
        }
        if (!ok || !tmp.isFile() || !tmp.renameTo(dest)) {
            tmp.delete();
            return false;
        }
        return true;
    }

    /** AOSP StorageManager.getStorageLowBytes (frameworks/base main,
     *  core/java/android/os/storage/StorageManager.java:1508-1517):
     *      lowBytes = path.getTotalSpace() * DEFAULT_STORAGE_THRESHOLD_PERCENT_LOW / 100
     *      return Math.min(lowBytes, DEFAULT_THRESHOLD_MAX_BYTES)
     *  with DEFAULT_STORAGE_THRESHOLD_PERCENT_LOW = 5 (:1464) and
     *  DEFAULT_THRESHOLD_MAX_BYTES = 500 MiB (:1486). The Settings.Global overrides
     *  are not modelled (no such settings exist on a Mac). */
    static long photosLowBytes(File path) {
        long pct = path.getTotalSpace() * 5L / 100L;
        return Math.min(pct, 500L * 1024L * 1024L);
    }

    // ---- the diagnostic directory backend -----------------------------------

    private static final List<String> PH_IMAGE_EXT = Arrays.asList(
        "jpg", "jpeg", "png", "gif", "heic", "heif", "webp", "bmp", "dng", "tif", "tiff");
    private static final List<String> PH_VIDEO_EXT = Arrays.asList(
        "mp4", "mov", "m4v", "3gp", "mkv", "webm");

    private static List<String[]> photosListDir(File dir, boolean images, boolean videos) {
        File[] files = dir.listFiles();
        if (files == null) return null;
        Arrays.sort(files);
        List<String[]> out = new ArrayList<>();
        for (File f : files) {
            String name = f.getName();
            if (name.startsWith(".") || !f.isFile()) continue;
            String ext = photosExtOf(name);
            boolean img = PH_IMAGE_EXT.contains(ext), vid = PH_VIDEO_EXT.contains(ext);
            if (!(img && images) && !(vid && videos)) continue;
            int w = 0, h = 0;
            if (img) {
                int[] d = photosImageSize(f);
                w = d[0];
                h = d[1];
            }
            long ms = f.lastModified();
            out.add(new String[]{"dir:" + name, img ? "1" : "3", String.valueOf(ms),
                String.valueOf(ms), String.valueOf(w), String.valueOf(h), "0", name,
                photosMimeOf(ext, img)});
        }
        return out;
    }

    private static String photosCopyFromDir(File dir, String id, File dest) {
        if (id == null || !id.startsWith("dir:")) return "no such asset";
        File src = new File(dir, id.substring(4));
        if (!src.isFile() || src.getName().startsWith(".")) return "no such asset";
        try {
            java.nio.file.Files.copy(src.toPath(), dest.toPath());
            return null;
        } catch (Throwable t) {
            return String.valueOf(t);
        }
    }

    private static int[] photosImageSize(File f) {
        // Header-only read through the JDK's ImageIO readers: no pixels, no AWT
        // toolkit. HEIC has no JDK reader and answers 0x0, an honest "unknown".
        try (javax.imageio.stream.ImageInputStream in = javax.imageio.ImageIO.createImageInputStream(f)) {
            if (in != null) {
                java.util.Iterator<javax.imageio.ImageReader> it = javax.imageio.ImageIO.getImageReaders(in);
                if (it.hasNext()) {
                    javax.imageio.ImageReader r = it.next();
                    try {
                        r.setInput(in);
                        return new int[]{r.getWidth(0), r.getHeight(0)};
                    } finally {
                        r.dispose();
                    }
                }
            }
        } catch (Throwable ignored) {}
        return new int[]{0, 0};
    }

    private static String photosExtOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    private static String photosMimeOf(String ext, boolean image) {
        switch (ext) {
            case "jpg": case "jpeg": return "image/jpeg";
            case "png": return "image/png";
            case "gif": return "image/gif";
            case "heic": return "image/heic";
            case "heif": return "image/heif";
            case "webp": return "image/webp";
            case "bmp": return "image/bmp";
            case "dng": return "image/x-adobe-dng";
            case "tif": case "tiff": return "image/tiff";
            case "mp4": return "video/mp4";
            case "mov": return "video/quicktime";
            case "m4v": return "video/x-m4v";
            case "3gp": return "video/3gpp";
            case "mkv": return "video/x-matroska";
            case "webm": return "video/webm";
            default: return image ? "image/*" : "video/*";
        }
    }

    private static String photosReadSmall(File f) {
        try {
            if (!f.isFile() || f.length() > 256) return null;
            return new String(java.nio.file.Files.readAllBytes(f.toPath()),
                java.nio.charset.StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return null;
        }
    }
}
