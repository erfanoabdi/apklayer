// android.provider.MediaStore -- the media-index contract (gallery /
// audio / video / downloads). Apps use the EXTERNAL_CONTENT_URI
// constants + column-name interfaces to query images/audio/video via
// ContentResolver, and the ACTION_* intents to launch the camera /
// picker.
//
// ⚠ CORRECTED 2026-08-23. This header used to say "Mac.apk hosts no
// MediaStore provider, so ContentResolver queries against these URIs
// return null (no rows)". That has been FALSE since
// android.provider.MediaStoreProvider landed (2026-07-30): the "media"
// authority IS served, backed by a real SQLite index of the external
// storage tree, and queries come back as real Cursors. The stale
// sentence mattered because it described exactly the failure this file's
// constants used to CAUSE -- a query for a URI we publish but do not
// route threw inside the provider and surfaced as null -- and null is
// not "no rows" to a caller (AOSP returns null only when the provider is
// ABSENT). Constant names + string values mirror AOSP; every content URI
// declared here is routed by MediaStoreProvider.routeFor().
package android.provider;

import android.net.Uri;
import android.os.Bundle;

public final class MediaStore {
    /** AOSP declares a public no-arg constructor on this contract class. */
    public MediaStore() { }

    public static final String AUTHORITY = "media";
    public static final Uri AUTHORITY_URI = Uri.parse("content://media");

    public static final String VOLUME_INTERNAL = "internal";
    public static final String VOLUME_EXTERNAL = "external";
    public static final String VOLUME_EXTERNAL_PRIMARY = "external_primary";

    // Picker / capture intents.
    public static final String ACTION_IMAGE_CAPTURE = "android.media.action.IMAGE_CAPTURE";
    public static final String ACTION_IMAGE_CAPTURE_SECURE =
            "android.media.action.IMAGE_CAPTURE_SECURE";
    public static final String ACTION_VIDEO_CAPTURE = "android.media.action.VIDEO_CAPTURE";
    public static final String ACTION_PICK_IMAGES = "android.provider.action.PICK_IMAGES";
    public static final String INTENT_ACTION_MEDIA_SEARCH = "android.intent.action.MEDIA_SEARCH";
    public static final String INTENT_ACTION_MUSIC_PLAYER =
            "android.intent.action.MUSIC_PLAYER";
    public static final String INTENT_ACTION_STILL_IMAGE_CAMERA =
            "android.media.action.STILL_IMAGE_CAMERA";
    public static final String INTENT_ACTION_VIDEO_CAMERA =
            "android.media.action.VIDEO_CAMERA";

    public static final String EXTRA_OUTPUT = "output";
    public static final String EXTRA_VIDEO_QUALITY = "android.intent.extra.videoQuality";
    public static final String EXTRA_DURATION_LIMIT = "android.intent.extra.durationLimit";
    public static final String EXTRA_SIZE_LIMIT = "android.intent.extra.sizeLimit";
    public static final String EXTRA_FINISH_ON_COMPLETION = "android.intent.extra.finishOnCompletion";
    public static final String EXTRA_PICK_IMAGES_MAX = "android.provider.extra.PICK_IMAGES_MAX";

    public static final String UNKNOWN_STRING = "<unknown>";

    /** AOSP MediaStore.MEDIA_SCANNER_VOLUME: the single column of the cursor
     *  getMediaScannerUri() answers with, naming the volume currently being
     *  scanned (null when no scan is running). */
    public static final String MEDIA_SCANNER_VOLUME = "volume";

    public static Uri getMediaScannerUri() {
        return Uri.parse("content://media/none/media_scanner");
    }

    public static int getPickImagesMaxLimit() { return 100; }

    // ---- MediaColumns: base columns shared by all media tables ----------
    public interface MediaColumns extends BaseColumns {
        public static final String DATA = "_data";
        public static final String SIZE = "_size";
        public static final String DISPLAY_NAME = "_display_name";
        public static final String TITLE = "title";
        public static final String DATE_ADDED = "date_added";
        public static final String DATE_MODIFIED = "date_modified";
        public static final String DATE_TAKEN = "datetaken";
        public static final String MIME_TYPE = "mime_type";
        public static final String WIDTH = "width";
        public static final String HEIGHT = "height";
        public static final String DURATION = "duration";
        public static final String ORIENTATION = "orientation";
        public static final String RELATIVE_PATH = "relative_path";
        public static final String BUCKET_ID = "bucket_id";
        public static final String BUCKET_DISPLAY_NAME = "bucket_display_name";
        public static final String VOLUME_NAME = "volume_name";
        public static final String IS_PENDING = "is_pending";
        public static final String IS_FAVORITE = "is_favorite";
        public static final String IS_TRASHED = "is_trashed";
        public static final String OWNER_PACKAGE_NAME = "owner_package_name";
        public static final String DOCUMENT_ID = "document_id";
        // AOSP (fetched 2026-07-30, platform_packages_providers_mediaprovider
        // apex/framework/java/android/provider/MediaStore.java, MediaColumns,
        // ~line 2061): "Flag indicating if a media item has been marked as
        // being part of the Downloads collection." Was missing entirely --
        // android.provider.MediaStoreProvider needs it to answer
        // Downloads.EXTERNAL_CONTENT_URI queries.
        public static final String IS_DOWNLOAD = "is_download";
    }

    // ---- Images ---------------------------------------------------------
    public static final class Images {
        /** AOSP declares a public no-arg constructor. */
        public Images() { }
        public interface ImageColumns extends MediaColumns {
            public static final String DESCRIPTION = "description";
            public static final String PICASA_ID = "picasa_id";
            public static final String IS_PRIVATE = "isprivate";
            public static final String LATITUDE = "latitude";
            public static final String LONGITUDE = "longitude";
            public static final String DATE_TAKEN = "datetaken";
            public static final String ORIENTATION = "orientation";
            public static final String MINI_THUMB_MAGIC = "mini_thumb_magic";
            public static final String BUCKET_ID = "bucket_id";
            public static final String BUCKET_DISPLAY_NAME = "bucket_display_name";
        }
        public static final class Media implements ImageColumns {
            public static final Uri INTERNAL_CONTENT_URI =
                    Uri.parse("content://media/internal/images/media");
            public static final Uri EXTERNAL_CONTENT_URI =
                    Uri.parse("content://media/external/images/media");
            public static final String CONTENT_TYPE = "vnd.android.cursor.dir/image";
            public static final String DEFAULT_SORT_ORDER = "bucket_display_name";
            public static Uri getContentUri(String volumeName) {
                return Uri.parse("content://media/" + volumeName + "/images/media");
            }
            public static Uri getContentUri(String volumeName, long id) {
                return android.content.ContentUris.withAppendedId(getContentUri(volumeName), id);
            }

            // AOSP's three deprecated query() convenience wrappers. Pure
            // ContentResolver.query() delegation with the class's own
            // DEFAULT_SORT_ORDER, so these are exact.
            /** @deprecated AOSP deprecated these in API 29. */
            @Deprecated
            public static android.database.Cursor query(android.content.ContentResolver cr,
                    Uri uri, String[] projection) {
                return cr.query(uri, projection, null, null, DEFAULT_SORT_ORDER);
            }
            /** @deprecated see {@link #query(android.content.ContentResolver, Uri, String[])}. */
            @Deprecated
            public static android.database.Cursor query(android.content.ContentResolver cr,
                    Uri uri, String[] projection, String where, String orderBy) {
                return cr.query(uri, projection, where, null,
                        orderBy == null ? DEFAULT_SORT_ORDER : orderBy);
            }
            /** @deprecated see {@link #query(android.content.ContentResolver, Uri, String[])}. */
            @Deprecated
            public static android.database.Cursor query(android.content.ContentResolver cr,
                    Uri uri, String[] projection, String selection, String[] selectionArgs,
                    String orderBy) {
                return cr.query(uri, projection, selection, selectionArgs,
                        orderBy == null ? DEFAULT_SORT_ORDER : orderBy);
            }
            /** Decode a bitmap from the resolver's stream. Real behavior. */
            public static android.graphics.Bitmap getBitmap(android.content.ContentResolver cr, Uri url)
                    throws java.io.FileNotFoundException, java.io.IOException {
                java.io.InputStream in = cr.openInputStream(url);
                try {
                    return android.graphics.BitmapFactory.decodeStream(in);
                } finally {
                    if (in != null) in.close();
                }
            }
            /** Insert an image. Mac.apk exposes no writable media provider, so the
             *  insert fails and AOSP contract-returns null. */
            public static String insertImage(android.content.ContentResolver cr, android.graphics.Bitmap source,
                    String title, String description) { return null; }
            public static String insertImage(android.content.ContentResolver cr, String imagePath,
                    String name, String description) throws java.io.FileNotFoundException { return null; }
        }
        // AOSP-conformance (framework_lint vs android-30 stub jar): AOSP does not
        // mark this final. Final-when-AOSP-is-not is the dangerous direction -- an
        // app compiled against real Android that subclasses this fails to resolve
        // against our jar. See docs/aosp-conformance-audit_2026-08-23.md.
        public static class Thumbnails implements BaseColumns {
            /** AOSP's DEFAULT_SORT_ORDER for the image-thumbnail table. */
            public static final String DEFAULT_SORT_ORDER = "image_id ASC";
            public static final Uri INTERNAL_CONTENT_URI =
                    Uri.parse("content://media/internal/images/thumbnails");
            public static final Uri EXTERNAL_CONTENT_URI =
                    Uri.parse("content://media/external/images/thumbnails");
            public static final String DATA = "_data";
            public static final String IMAGE_ID = "image_id";
            public static final String KIND = "kind";
            public static final String WIDTH = "width";
            public static final String HEIGHT = "height";
            public static final int MINI_KIND = 1;
            public static final int FULL_SCREEN_KIND = 2;
            public static final int MICRO_KIND = 3;
            public static Uri getContentUri(String volumeName) {
                return Uri.parse("content://media/" + volumeName + "/images/thumbnails");
            }
            /** No thumbnail store on a de-Googled device; AOSP returns null. */
            public static android.graphics.Bitmap getThumbnail(android.content.ContentResolver cr, long origId,
                    int kind, android.graphics.BitmapFactory.Options options) { return null; }
            public static android.graphics.Bitmap getThumbnail(android.content.ContentResolver cr, long origId,
                    long groupId, int kind, android.graphics.BitmapFactory.Options options) { return null; }

            /**
             * AOSP's thumbnail generation is synchronous since API 29, so there
             * is nothing in flight to cancel; AOSP's own body is empty. A no-op
             * here is faithful, not a stub.
             *
             * @deprecated AOSP deprecated the Thumbnails API in API 29.
             */
            @Deprecated
            public static void cancelThumbnailRequest(android.content.ContentResolver cr,
                    long origId) { }
            /** @deprecated see {@link #cancelThumbnailRequest(android.content.ContentResolver, long)}. */
            @Deprecated
            public static void cancelThumbnailRequest(android.content.ContentResolver cr,
                    long origId, long groupId) { }

            /** AOSP's fixed thumbnail dimensions; pure constants. */
            @Deprecated
            public static android.util.Size getKindSize(int kind) {
                return MediaStore.getThumbnailKindSize(kind);
            }

            /** @deprecated AOSP query wrapper; pure resolver delegation. */
            @Deprecated
            public static final android.database.Cursor query(android.content.ContentResolver cr,
                    Uri uri, String[] projection) {
                return cr.query(uri, projection, null, null, DEFAULT_SORT_ORDER);
            }


            /** @deprecated AOSP wrapper: the MINI thumbnail row for one image. */
            @Deprecated
            public static final android.database.Cursor queryMiniThumbnail(
                    android.content.ContentResolver cr, long origId, int kind,
                    String[] projection) {
                return cr.query(EXTERNAL_CONTENT_URI, projection,
                        IMAGE_ID + " = " + origId + " AND " + KIND + " = " + kind,
                        null, null);
            }

            /** @deprecated AOSP wrapper: every thumbnail row of a given kind. */
            @Deprecated
            public static final android.database.Cursor queryMiniThumbnails(
                    android.content.ContentResolver cr, Uri uri, int kind, String[] projection) {
                return cr.query(uri, projection, KIND + " = " + kind, null, null);
            }
        }
    }

    // ---- Video ----------------------------------------------------------
    public static final class Video {
        /** AOSP declares a public no-arg constructor. */
        public Video() { }
        public interface VideoColumns extends MediaColumns {
            public static final String DURATION = "duration";
            public static final String ARTIST = "artist";
            public static final String ALBUM = "album";
            public static final String RESOLUTION = "resolution";
            public static final String DESCRIPTION = "description";
            public static final String IS_PRIVATE = "isprivate";
            public static final String TAGS = "tags";
            public static final String CATEGORY = "category";
            public static final String LATITUDE = "latitude";
            public static final String LONGITUDE = "longitude";
            public static final String DATE_TAKEN = "datetaken";
            public static final String BUCKET_ID = "bucket_id";
            public static final String BUCKET_DISPLAY_NAME = "bucket_display_name";
            public static final String BOOKMARK = "bookmark";
        }
        public static final class Media implements VideoColumns {
            public static final Uri INTERNAL_CONTENT_URI =
                    Uri.parse("content://media/internal/video/media");
            public static final Uri EXTERNAL_CONTENT_URI =
                    Uri.parse("content://media/external/video/media");
            public static final String CONTENT_TYPE = "vnd.android.cursor.dir/video";
            // Video.Media sorts by "title", unlike Images.Media's "_display_name".
            // frameworks/base/core/java/android/provider/MediaStore.java (android-11)
            public static final String DEFAULT_SORT_ORDER = "title";
            public static Uri getContentUri(String volumeName) {
                return Uri.parse("content://media/" + volumeName + "/video/media");
            }
            public static Uri getContentUri(String volumeName, long id) {
                return android.content.ContentUris.withAppendedId(getContentUri(volumeName), id);
            }
        }
        // AOSP-conformance (framework_lint vs android-30 stub jar): AOSP does not
        // mark this final. Final-when-AOSP-is-not is the dangerous direction -- an
        // app compiled against real Android that subclasses this fails to resolve
        // against our jar. See docs/aosp-conformance-audit_2026-08-23.md.
        /** @deprecated AOSP query wrapper; pure resolver delegation. */
        @Deprecated
        public static android.database.Cursor query(android.content.ContentResolver cr, Uri uri,
                String[] projection) {
            return cr.query(uri, projection, null, null, Media.DEFAULT_SORT_ORDER);
        }

        public static class Thumbnails implements BaseColumns {
            public static final Uri INTERNAL_CONTENT_URI =
                    Uri.parse("content://media/internal/video/thumbnails");
            public static final Uri EXTERNAL_CONTENT_URI =
                    Uri.parse("content://media/external/video/thumbnails");
            public static final String DATA = "_data";
            public static final String VIDEO_ID = "video_id";
            public static final String KIND = "kind";
            public static final String WIDTH = "width";
            public static final String HEIGHT = "height";
            public static final int MINI_KIND = 1;
            public static final int FULL_SCREEN_KIND = 2;
            public static final int MICRO_KIND = 3;
            /** No thumbnail store on a de-Googled device; AOSP returns null. */
            public static android.graphics.Bitmap getThumbnail(android.content.ContentResolver cr, long origId,
                    int kind, android.graphics.BitmapFactory.Options options) { return null; }
            public static android.graphics.Bitmap getThumbnail(android.content.ContentResolver cr, long origId,
                    long groupId, int kind, android.graphics.BitmapFactory.Options options) {
                return null;
            }

            public static Uri getContentUri(String volumeName) {
                return Uri.parse("content://media/" + volumeName + "/video/thumbnails");
            }

            /** See Images.Thumbnails.cancelThumbnailRequest: AOSP's body is empty. */
            @Deprecated
            public static void cancelThumbnailRequest(android.content.ContentResolver cr,
                    long origId) { }
            /** @deprecated see the single-arg form. */
            @Deprecated
            public static void cancelThumbnailRequest(android.content.ContentResolver cr,
                    long origId, long groupId) { }

            @Deprecated
            public static android.util.Size getKindSize(int kind) {
                return MediaStore.getThumbnailKindSize(kind);
            }
        }
    }

    // ---- Audio ----------------------------------------------------------
    // Column / URI / MIME surface mirrors AOSP verbatim, fetched 2026-08-23 from
    //   https://raw.githubusercontent.com/aosp-mirror/platform_packages_providers_mediaprovider/main/apex/framework/java/android/provider/MediaStore.java
    // (MediaStore.java lives in the MediaProvider mirror, not in
    // platform_frameworks_base/core/java/android/provider/, as of AOSP main):
    //   AudioColumns          ~3400-3638  (the *_KEY / *_ID / is_* family below)
    //   Audio.keyFor(String)  ~3640-3688
    //   Genres/GenresColumns  ~3779-3874
    //   Playlists(+Members)   ~3888-4036
    //   Artists/ArtistColumns ~4038-4130  (incl. the nested Artists.Albums)
    //   Albums/AlbumColumns   ~4135-4282
    //
    // WHY THE SURFACE GREW (2026-08-23): android.provider.MediaStoreProvider now
    // ROUTES audio/albums, audio/artists, audio/genres and audio/playlists, which
    // it answers with the same GROUP BY aggregations AOSP's DatabaseHelper builds
    // its audio_albums / audio_artists / audio_genres VIEWs from. Those views
    // select album_id/artist_id/genre_id/*_key columns, and the DEFAULT_SORT_ORDER
    // constants right here name album_key / artist_key / title_key -- so an app
    // that passes DEFAULT_SORT_ORDER straight through (the documented use) makes
    // SQLite ORDER BY a column that has to exist. Same lesson the DATE_TAKEN
    // comment in MediaStoreProvider records: a column an app SORTS or FILTERS by
    // cannot be omitted the way a column it merely READS can.
    public static final class Audio {
        /** AOSP declares a public no-arg constructor. */
        public Audio() { }
        public interface AudioColumns extends MediaColumns {
            public static final String TITLE_KEY = "title_key";
            public static final String BOOKMARK = "bookmark";
            public static final String ARTIST_ID = "artist_id";
            public static final String ARTIST = "artist";
            public static final String ALBUM_ARTIST = "album_artist";
            public static final String ARTIST_KEY = "artist_key";
            public static final String COMPOSER = "composer";
            public static final String ALBUM_ID = "album_id";
            public static final String ALBUM = "album";
            public static final String ALBUM_KEY = "album_key";
            public static final String TRACK = "track";
            public static final String YEAR = "year";
            public static final String IS_MUSIC = "is_music";
            public static final String IS_PODCAST = "is_podcast";
            public static final String IS_RINGTONE = "is_ringtone";
            public static final String IS_ALARM = "is_alarm";
            public static final String IS_NOTIFICATION = "is_notification";
            public static final String IS_AUDIOBOOK = "is_audiobook";
            public static final String IS_RECORDING = "is_recording";
            public static final String GENRE_ID = "genre_id";
            public static final String GENRE = "genre";
            public static final String GENRE_KEY = "genre_key";
            public static final String TITLE_RESOURCE_URI = "title_resource_uri";
        }

        // AOSP Audio.PATTERN_TRIM_BEFORE / PATTERN_TRIM_AFTER + keyFor(), copied
        // verbatim from the citation above. keyFor is @Deprecated on AOSP but is
        // still the ONLY definition of what goes in title_key / artist_key /
        // album_key / genre_key, and MediaProvider.computeAudioKeyValues() calls
        // it to build those columns -- so MediaStoreProvider needs the real
        // function, not an approximation, or its keys would sort differently from
        // a device's. java.text.Collator is plain JDK, so this is exact.
        private static final java.util.regex.Pattern PATTERN_TRIM_BEFORE =
                java.util.regex.Pattern.compile("(?i)(^(the|an|a) |,\\s*(the|an|a)$|[^\\w\\s]|^\\s+|\\s+$)");
        private static final java.util.regex.Pattern PATTERN_TRIM_AFTER =
                java.util.regex.Pattern.compile("(^(00)+|(00)+$)");

        /** AOSP MediaStore.Audio.keyFor(String): "Converts a user-visible string
         *  into a 'key' that can be used for grouping, sorting, and searching."
         *  Opaque token; never displayed. */
        public static String keyFor(String name) {
            if (name == null || name.isEmpty()) return "";
            if (UNKNOWN_STRING.equals(name)) return "01";
            final boolean sortFirst = name.startsWith("\001");
            name = PATTERN_TRIM_BEFORE.matcher(name).replaceAll("");
            if (name.isEmpty()) return "";
            final java.text.Collator c = java.text.Collator.getInstance(java.util.Locale.ROOT);
            c.setStrength(java.text.Collator.PRIMARY);
            name = encodeToString(c.getCollationKey(name).toByteArray());
            name = PATTERN_TRIM_AFTER.matcher(name).replaceAll("");
            if (sortFirst) name = "01" + name;
            return name;
        }

        private static String encodeToString(byte[] bytes) {
            final StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        }

        public static final class Media implements AudioColumns {
            public static final Uri INTERNAL_CONTENT_URI =
                    Uri.parse("content://media/internal/audio/media");
            public static final Uri EXTERNAL_CONTENT_URI =
                    Uri.parse("content://media/external/audio/media");
            public static final String CONTENT_TYPE = "vnd.android.cursor.dir/audio";
            public static final String ENTRY_CONTENT_TYPE = "vnd.android.cursor.item/audio";
            public static final String DEFAULT_SORT_ORDER = "title_key";
            public static final String RECORD_SOUND_ACTION =
                    "android.provider.MediaStore.RECORD_SOUND";
            public static final String EXTRA_MAX_BYTES =
                    "android.provider.MediaStore.extra.MAX_BYTES";
            public static Uri getContentUri(String volumeName) {
                return Uri.parse("content://media/" + volumeName + "/audio/media");
            }
            public static Uri getContentUri(String volumeName, long id) {
                return android.content.ContentUris.withAppendedId(getContentUri(volumeName), id);
            }

            /**
             * AOSP maps a filesystem path onto the volume that holds it. The
             * regex ("/storage/<volume>/...") is AOSP's FileUtils.extractVolumeName
             * logic, including the "emulated" -> external_primary rename.
             *
             * @deprecated AOSP deprecated path-based lookup in API 29.
             */
            @Deprecated
            public static Uri getContentUriForPath(String path) {
                return getContentUri(MediaStore.getVolumeNameForPath(path));
            }
        }

        // ---- Genres ------------------------------------------------------
        public interface GenresColumns {
            public static final String NAME = "name";
        }
        public static final class Genres implements BaseColumns, GenresColumns {
            public static Uri getContentUri(String volumeName) {
                return Uri.parse("content://media/" + volumeName + "/audio/genres");
            }
            public static Uri getContentUriForAudioId(String volumeName, int audioId) {
                return Uri.parse("content://media/" + volumeName + "/audio/media/"
                        + audioId + "/genres");
            }
            public static final Uri INTERNAL_CONTENT_URI = getContentUri("internal");
            public static final Uri EXTERNAL_CONTENT_URI = getContentUri("external");
            public static final String CONTENT_TYPE = "vnd.android.cursor.dir/genre";
            public static final String ENTRY_CONTENT_TYPE = "vnd.android.cursor.item/genre";
            public static final String DEFAULT_SORT_ORDER = NAME;
            public static final class Members implements AudioColumns {
                public static Uri getContentUri(String volumeName, long genreId) {
                    return Uri.parse("content://media/" + volumeName + "/audio/genres/"
                            + genreId + "/members");
                }
                public static final String CONTENT_DIRECTORY = "members";
                public static final String DEFAULT_SORT_ORDER = TITLE_KEY;
                public static final String AUDIO_ID = "audio_id";
                public static final String GENRE_ID = "genre_id";
            }
        }

        // ---- Playlists ---------------------------------------------------
        /** @deprecated AOSP: "Android playlists are now deprecated ... M3U files
         *  can be used as an alternative." Kept because apps still query it. */
        @Deprecated
        public interface PlaylistsColumns extends MediaColumns {
            public static final String NAME = "name";
            public static final String DATA = "_data";
            public static final String DATE_ADDED = "date_added";
            public static final String DATE_MODIFIED = "date_modified";
        }
        @Deprecated
        public static final class Playlists implements BaseColumns, PlaylistsColumns {
            public static Uri getContentUri(String volumeName) {
                return Uri.parse("content://media/" + volumeName + "/audio/playlists");
            }
            public static final Uri INTERNAL_CONTENT_URI = getContentUri("internal");
            public static final Uri EXTERNAL_CONTENT_URI = getContentUri("external");
            public static final String CONTENT_TYPE = "vnd.android.cursor.dir/playlist";
            public static final String ENTRY_CONTENT_TYPE = "vnd.android.cursor.item/playlist";
            public static final String DEFAULT_SORT_ORDER = NAME;
            public static final class Members implements AudioColumns {
                public static Uri getContentUri(String volumeName, long playlistId) {
                    return Uri.parse("content://media/" + volumeName + "/audio/playlists/"
                            + playlistId + "/members");
                }
                public static final String _ID = "_id";
                public static final String CONTENT_DIRECTORY = "members";
                public static final String AUDIO_ID = "audio_id";
                public static final String PLAYLIST_ID = "playlist_id";
                public static final String PLAY_ORDER = "play_order";
                public static final String DEFAULT_SORT_ORDER = PLAY_ORDER;

                /**
                 * AOSP reorders a playlist entry by UPDATEing a "move" URI --
                 * pure ContentResolver work that the provider behind the URI
                 * carries out, reproduced verbatim.
                 *
                 * @deprecated playlists are deprecated on AOSP.
                 */
                @Deprecated
                public static boolean moveItem(android.content.ContentResolver res,
                        long playlistId, int from, int to) {
                    Uri uri = getContentUri("external", playlistId).buildUpon()
                            .appendEncodedPath(String.valueOf(from))
                            .appendQueryParameter("move", "true")
                            .build();
                    android.content.ContentValues values = new android.content.ContentValues();
                    values.put(PLAY_ORDER, to);
                    return res.update(uri, values, null, null) != 0;
                }
            }
        }

        // ---- Artists -----------------------------------------------------
        public interface ArtistColumns {
            public static final String ARTIST = "artist";
            public static final String ARTIST_KEY = "artist_key";
            public static final String NUMBER_OF_ALBUMS = "number_of_albums";
            public static final String NUMBER_OF_TRACKS = "number_of_tracks";
        }
        public static final class Artists implements BaseColumns, ArtistColumns {
            public static Uri getContentUri(String volumeName) {
                return Uri.parse("content://media/" + volumeName + "/audio/artists");
            }
            public static final Uri INTERNAL_CONTENT_URI =
                    Uri.parse("content://media/internal/audio/artists");
            public static final Uri EXTERNAL_CONTENT_URI =
                    Uri.parse("content://media/external/audio/artists");
            public static final String CONTENT_TYPE = "vnd.android.cursor.dir/artists";
            public static final String ENTRY_CONTENT_TYPE = "vnd.android.cursor.item/artist";
            public static final String DEFAULT_SORT_ORDER = ARTIST_KEY;
            /** AOSP: "Sub-directory of each artist containing all albums on which
             *  a song by the artist appears." */
            public static final class Albums implements BaseColumns, AlbumColumns {
                public static Uri getContentUri(String volumeName, long artistId) {
                    return Uri.parse("content://media/" + volumeName + "/audio/artists/"
                            + artistId + "/albums");
                }
            }
        }

        // ---- Albums ------------------------------------------------------
        public interface AlbumColumns {
            public static final String ALBUM_ID = "album_id";
            public static final String ALBUM = "album";
            public static final String ARTIST_ID = "artist_id";
            public static final String ARTIST = "artist";
            public static final String ARTIST_KEY = "artist_key";
            public static final String NUMBER_OF_SONGS = "numsongs";
            public static final String NUMBER_OF_SONGS_FOR_ARTIST = "numsongs_by_artist";
            public static final String FIRST_YEAR = "minyear";
            public static final String LAST_YEAR = "maxyear";
            public static final String ALBUM_KEY = "album_key";
            public static final String ALBUM_ART = "album_art";
        }
        public static final class Albums implements BaseColumns, AlbumColumns {
            public static Uri getContentUri(String volumeName) {
                return Uri.parse("content://media/" + volumeName + "/audio/albums");
            }
            public static final Uri INTERNAL_CONTENT_URI =
                    Uri.parse("content://media/internal/audio/albums");
            public static final Uri EXTERNAL_CONTENT_URI =
                    Uri.parse("content://media/external/audio/albums");
            public static final String CONTENT_TYPE = "vnd.android.cursor.dir/albums";
            public static final String ENTRY_CONTENT_TYPE = "vnd.android.cursor.item/album";
            public static final String DEFAULT_SORT_ORDER = ALBUM_KEY;
        }

        public static final class Radio {
            public static final String ENTRY_CONTENT_TYPE = "vnd.android.cursor.item/radio";
        }
    }
    // ---- Files ----------------------------------------------------------
    public static final class Files {
        /** AOSP declares a public no-arg constructor. */
        public Files() { }
        public interface FileColumns extends MediaColumns {
            public static final String MEDIA_TYPE = "media_type";
            public static final int MEDIA_TYPE_NONE = 0;
            public static final int MEDIA_TYPE_IMAGE = 1;
            public static final int MEDIA_TYPE_AUDIO = 2;
            public static final int MEDIA_TYPE_VIDEO = 3;
            public static final int MEDIA_TYPE_PLAYLIST = 4;
            public static final int MEDIA_TYPE_SUBTITLE = 5;
            public static final int MEDIA_TYPE_DOCUMENT = 6;
            public static final String PARENT = "parent";
            public static final String MIME_TYPE = "mime_type";
            public static final String TITLE = "title";
        }
        public static Uri getContentUri(String volumeName) {
            return Uri.parse("content://media/" + volumeName + "/file");
        }
        public static Uri getContentUri(String volumeName, long rowId) {
            return Uri.parse("content://media/" + volumeName + "/file/" + rowId);
        }
    }

    // ---- Downloads ------------------------------------------------------
    /**
     * ⚠ AOSP declares this at MediaStore level (MediaStore.DownloadColumns) and
     * has Downloads IMPLEMENT it. Mac.apk previously nested it inside Downloads,
     * so the descriptor was MediaStore$Downloads$DownloadColumns and Downloads
     * implemented nothing -- an app naming MediaStore.DownloadColumns did not
     * resolve.
     */
    /**
     * The narrow column set the photo picker exposes for a picked item -- it is
     * deliberately NOT the full MediaColumns surface, because a picker grant
     * does not carry read access to the rest of the row.
     *
     * Source: packages/providers/MediaProvider .../MediaStore.java
     *
     * A CLASS, not an interface: AOSP declares "public static class
     * PickerMediaColumns { private PickerMediaColumns() {} ..." (platform_
     * packages_providers_mediaprovider android14-release, apex/framework/java/
     * android/provider/MediaStore.java:1901) and the SDK-34 android.jar agrees.
     * It was an interface here. The String constants are compile-time
     * constants and inline into app code, so no app call site linked through
     * the type; what the kind DID change is reflection (isInterface,
     * getModifiers) and the class shape the DEX converter's hierarchy oracle
     * reads. Found by debug/apishape_gate.sh (KNOWN_OPEN, struck 2026-09-26).
     */
    public static class PickerMediaColumns {
        private PickerMediaColumns() {}
        public static final String DATA = "_data";
        public static final String SIZE = "_size";
        public static final String DISPLAY_NAME = "_display_name";
        public static final String DATE_TAKEN = "datetaken";
        public static final String MIME_TYPE = "mime_type";
        public static final String DURATION_MILLIS = "duration";
        public static final String WIDTH = "width";
        public static final String HEIGHT = "height";
        public static final String ORIENTATION = "orientation";
    }

    public interface DownloadColumns extends MediaColumns {
        public static final String DOWNLOAD_URI = "download_uri";
        public static final String REFERER_URI = "referer_uri";
    }

    public static final class Downloads implements DownloadColumns {
        private Downloads() {}
        public static final Uri INTERNAL_CONTENT_URI =
                Uri.parse("content://media/internal/downloads");
        public static final Uri EXTERNAL_CONTENT_URI =
                Uri.parse("content://media/external/downloads");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/download";
        public static Uri getContentUri(String volumeName) {
            return Uri.parse("content://media/" + volumeName + "/downloads");
        }
        public static Uri getContentUri(String volumeName, long id) {
            return android.content.ContentUris.withAppendedId(getContentUri(volumeName), id);
        }
    }

    /** The single writable external volume on this host. */
    public static java.util.Set<String> getExternalVolumeNames(android.content.Context context) {
        java.util.Set<String> s = new java.util.HashSet<>();
        s.add(VOLUME_EXTERNAL_PRIMARY);
        return s;
    }
    /** Tag a media Uri to request the original (non-redacted) bytes. */
    public static Uri setRequireOriginal(Uri uri) {
        return uri.buildUpon().appendQueryParameter("requireOriginal", "1").build();
    }
    public static Uri getMediaUri(android.content.Context context, Uri uri) { return uri; }

    /** AOSP MediaStore.canManageMedia(Context) (API 31+; platform_packages_
     *  providers_mediaprovider android13-release, apex/framework/java/
     *  android/provider/MediaStore.java:4657-4680), fetched 2026-08-12:
     *
     *      public static boolean canManageMedia(@NonNull Context context) {
     *          Objects.requireNonNull(context);
     *          final String packageName = context.getOpPackageName();
     *          final int uid = context.getApplicationInfo().uid;
     *          final String permission = android.Manifest.permission.MANAGE_MEDIA;
     *          final AppOpsManager appOps = context.getSystemService(AppOpsManager.class);
     *          final int opMode = appOps.unsafeCheckOpNoThrow(
     *                  AppOpsManager.permissionToOp(permission), uid, packageName);
     *          switch (opMode) {
     *              case AppOpsManager.MODE_DEFAULT:
     *                  return PackageManager.PERMISSION_GRANTED == context.checkPermission(
     *                          permission, android.os.Process.myPid(), uid);
     *              case AppOpsManager.MODE_ALLOWED: return true;
     *              case AppOpsManager.MODE_ERRORED:
     *              case AppOpsManager.MODE_IGNORED:  return false;
     *              default:                          return false;
     *          }
     *      }
     *
     *  (android14 ms14.java:4804-4826 is the same body.)
     *
     *  Stage S1 (docs/shared-storage-real-mac-folders-design_2026-09-25.md)
     *  ports that body verbatim. The op is MANAGE_MEDIA (op 110), which the
     *  user grants in macapk-installhelper's out-of-process consent dialog --
     *  reached from Settings.ACTION_REQUEST_MANAGE_MEDIA exactly as on a device
     *  (com.macapk.loader.SpecialAccessHandler) -- and on MODE_DEFAULT the
     *  permission, which MacApkPackageManager.checkPermission answers DENIED
     *  (signature|appop|preinstalled). So: false until granted, true after.
     *  It used to be a hardcoded false, reasoned as "nothing in this environment
     *  could ever set it to true"; that stopped being so once the grant flow
     *  existed. MACAPK_ALLFILES_HONEST=0 restores the hardcoded false. */
    public static boolean canManageMedia(android.content.Context context) {
        java.util.Objects.requireNonNull(context);
        if (!com.macapk.loader.SpecialAccessStore.honest()) return false;   // pre-S1
        final String packageName = context.getPackageName();
        int uid;
        try { uid = context.getApplicationInfo().uid; }
        catch (Throwable t) { uid = android.os.Process.myUid(); }
        final String permission = com.macapk.loader.SpecialAccessStore.PERM_MANAGE_MEDIA;
        android.app.AppOpsManager appOps = null;
        try { appOps = context.getSystemService(android.app.AppOpsManager.class); }
        catch (Throwable ignored) {}
        if (appOps == null) appOps = new android.app.AppOpsManager(context);
        final int opMode = appOps.unsafeCheckOpNoThrow(
                android.app.AppOpsManager.permissionToOp(permission), uid, packageName);
        switch (opMode) {
            case android.app.AppOpsManager.MODE_DEFAULT:
                return android.content.pm.PackageManager.PERMISSION_GRANTED
                        == context.checkPermission(permission, android.os.Process.myPid(), uid);
            case android.app.AppOpsManager.MODE_ALLOWED:
                return true;
            case android.app.AppOpsManager.MODE_ERRORED:
            case android.app.AppOpsManager.MODE_IGNORED:
                return false;
            default:
                return false;
        }
    }

    /** AOSP MediaStore.openAssetFileDescriptor(ContentResolver, Uri, String,
     *  CancellationSignal) (platform_packages_providers_mediaprovider main,
     *  apex/framework/java/android/provider/MediaStore.java:4833-4857),
     *  fetched 2026-08-12 (behind Flags.FLAG_MEDIA_STORE_OPEN_FILE --
     *  unreleased/tip at fetch time):
     *
     *      public static @Nullable AssetFileDescriptor openAssetFileDescriptor(
     *              @NonNull ContentResolver resolver, @NonNull Uri uri, @NonNull String mode,
     *              @Nullable CancellationSignal cancellationSignal)
     *              throws FileNotFoundException {
     *          Objects.requireNonNull(resolver, "resolver");
     *          Objects.requireNonNull(uri, "uri");
     *          Objects.requireNonNull(mode, "mode");
     *          if (!ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())
     *                  || !AUTHORITY.equals(uri.getAuthority())) {
     *              throw new IllegalArgumentException("Given Uri " + uri + " should be a media URI");
     *          }
     *          if (isNonCloudPickerUri(uri)) {
     *              // In case of non cloud picker uris, use content resolver API normally
     *              return resolver.openAssetFileDescriptor(uri, mode, cancellationSignal);
     *          }
     *          if (ParcelFileDescriptor.parseMode(mode) != ParcelFileDescriptor.MODE_READ_ONLY) {
     *              throw new SecurityException("PhotoPicker Uris can only be accessed to read."
     *                      + " Uri: " + uri);
     *          }
     *          return openTypedAssetFileDescriptorInternal(resolver, uri, "*{@code /}*", null,
     *                  cancellationSignal);
     *      }
     *
     *  documented as "preferred over ContentResolver#openAssetFileDescriptor
     *  ... when opening media Uri for ensuring system stability", especially
     *  for Uris returned by ACTION_PICK_IMAGES.
     *
     *  Mac.apk has no cloud media provider and no PhotoPicker, so
     *  isNonCloudPickerUri(uri) is true for every Uri this runtime could
     *  ever hand back -- the picker-only branches beneath it (the
     *  read-only SecurityException guard, openTypedAssetFileDescriptorInternal)
     *  are dead code for us by construction, not something being skipped
     *  for convenience: there is no picker Uri shape they could ever match
     *  here. What remains after removing genuinely unreachable branches is
     *  exactly: validate the inputs, validate this is a media: Uri, then
     *  delegate to the ContentResolver method that already implements the
     *  real descriptor-open logic (ContentResolver.openAssetFileDescriptor,
     *  which already carries the scheme "file" / provider-lookup
     *  special-casing -- see that method's own header comment), instead of
     *  re-deriving that logic here. */
    public static android.content.res.AssetFileDescriptor openAssetFileDescriptor(
            android.content.ContentResolver resolver, Uri uri, String mode,
            android.os.CancellationSignal cancellationSignal) throws java.io.FileNotFoundException {
        java.util.Objects.requireNonNull(resolver, "resolver");
        java.util.Objects.requireNonNull(uri, "uri");
        java.util.Objects.requireNonNull(mode, "mode");
        if (!android.content.ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())
                || !AUTHORITY.equals(uri.getAuthority())) {
            throw new IllegalArgumentException("Given Uri " + uri + " should be a media URI");
        }
        return resolver.openAssetFileDescriptor(uri, mode, cancellationSignal);
    }

    // ========================================================================
    // API-36 top-level surface added 2026-08-24.
    // Source: packages/providers/MediaProvider apex/framework/java/android/provider/MediaStore.java
    // ========================================================================

    /** @hide AOSP's query-parameter names, used by the setter/getter pairs below. */
    public static final String PARAM_INCLUDE_PENDING = "includePending";
    /** @hide */ public static final String PARAM_REQUIRE_ORIGINAL = "requireOriginal";
    /** @hide AOSP's provider call() method names. */
    public static final String GET_VERSION_CALL = "get_version";
    /** @hide */ public static final String GET_GENERATION_CALL = "get_generation";

    private static final java.util.regex.Pattern PATTERN_VOLUME_NAME =
            java.util.regex.Pattern.compile("(?i)^/storage/([^/]+)");

    /** @hide AOSP ThumbnailConstants sizes, verbatim. */
    private static android.util.Size getThumbnailKindSize(int kind) {
        switch (kind) {
            case 3: return new android.util.Size(96, 96);       // MICRO_KIND
            case 2: return new android.util.Size(1024, 786);    // FULL_SCREEN_KIND
            case 1: return new android.util.Size(512, 384);     // MINI_KIND
            default: throw new IllegalArgumentException("Unsupported kind: " + kind);
        }
    }

    /** @hide AOSP FileUtils.extractVolumeName, as used by getContentUriForPath. */
    private static String getVolumeNameForPath(String path) {
        final java.util.regex.Matcher matcher =
                PATTERN_VOLUME_NAME.matcher(new java.io.File(path).getAbsolutePath());
        if (matcher.find()) {
            final String volumeName = matcher.group(1);
            if (volumeName.equals("emulated")) return VOLUME_EXTERNAL_PRIMARY;
            return volumeName.toLowerCase(java.util.Locale.ROOT);
        }
        return VOLUME_INTERNAL;
    }

    /** AOSP: the volume name is the first path segment of a media: URI. */
    public static String getVolumeName(Uri uri) {
        final java.util.List<String> segments = uri.getPathSegments();
        if (AUTHORITY.equals(uri.getAuthority()) && segments != null && !segments.isEmpty()) {
            return segments.get(0);
        }
        throw new IllegalArgumentException("Missing volume name: " + uri);
    }

    /** The inverse of setRequireOriginal; pure query-parameter inspection. */
    public static boolean getRequireOriginal(Uri uri) {
        return uri.getBooleanQueryParameter(PARAM_REQUIRE_ORIGINAL, false);
    }

    /** @deprecated AOSP replaced this by QUERY_ARG_MATCH_PENDING in API 30. */
    @Deprecated
    public static Uri setIncludePending(Uri uri) {
        return uri.buildUpon().appendQueryParameter(PARAM_INCLUDE_PENDING, "1").build();
    }

    /**
     * AOSP asks MediaProvider for an opaque version string that changes when the
     * media database is rebuilt. Mac.apk's MediaStoreProvider answers the same
     * call() if it implements it; when it does not, we fall back to a string
     * that is STABLE for the life of the process, which is the property callers
     * actually depend on (they compare it against a previously stored value to
     * decide whether their cache is still valid). Making one up per call would
     * force a rescan on every launch.
     */
    public static String getVersion(android.content.Context context) {
        return getVersion(context, VOLUME_EXTERNAL_PRIMARY);
    }

    public static String getVersion(android.content.Context context, String volumeName) {
        final Bundle in = new Bundle();
        in.putString(android.content.Intent.EXTRA_TEXT, volumeName);
        final Bundle out = context.getContentResolver().call(AUTHORITY, GET_VERSION_CALL, null, in);
        if (out != null) {
            final String version = out.getString(android.content.Intent.EXTRA_TEXT);
            if (version != null) return version;
        }
        return sProcessMediaVersion + ":" + volumeName;
    }

    /** Stable for the life of the process; see getVersion. */
    private static final String sProcessMediaVersion =
            Long.toHexString(android.os.Process.myPid()) + "-"
                    + Long.toHexString(System.currentTimeMillis());

    /**
     * AOSP returns a monotonically increasing generation for a volume, used to
     * detect changes since a previous read. Our provider answers the same call()
     * when it implements it; otherwise 0 is returned, which is AOSP's own
     * starting generation and reads as "nothing has changed yet".
     */
    public static long getGeneration(android.content.Context context, String volumeName) {
        final Bundle in = new Bundle();
        in.putString(android.content.Intent.EXTRA_TEXT, volumeName);
        final Bundle out = context.getContentResolver()
                .call(AUTHORITY, GET_GENERATION_CALL, null, in);
        return out == null ? 0L : out.getLong(android.content.Intent.EXTRA_INDEX);
    }

    /**
     * AOSP: the volumes that have been mounted recently, a subset of
     * getExternalVolumeNames. This host has exactly one external volume and it
     * is always mounted, so the two sets coincide.
     */
    public static java.util.Set<String> getRecentExternalVolumeNames(
            android.content.Context context) {
        return getExternalVolumeNames(context);
    }

    // --- Consent-gated bulk operations --------------------------------------
    //
    // ⚠ KNOWN-OPEN, and deliberately NOT faked. On Android these four return a
    // PendingIntent the app launches to get the USER's confirmation, and the
    // platform performs the delete/trash/favorite/grant only after the user
    // agrees. Mac.apk has no consent activity, so there is nothing to launch
    // and nothing that can grant.
    //
    // Two wrong answers were rejected here:
    //   (a) performing the operation eagerly and reporting success -- that
    //       deletes the user's media at request-CREATION time, before any
    //       confirmation, which Android never does;
    //   (b) returning null -- AOSP declares all four @NonNull, so a caller may
    //       dereference the result immediately and would NPE.
    // So each returns a real PendingIntent wrapping a Mac.apk-specific
    // no-consent-UI action. Launching it starts no activity, and
    // Activity.startIntentSenderForResult settles the pending result as
    // cancelled -- which the app reads as "the user declined", the one outcome
    // that is both truthful and safe.
    //
    // Fixing this properly needs a confirmation Activity in the host (outside
    // android.provider); see the lane report.
    private static final String ACTION_NO_CONSENT_UI =
            "org.macapk.action.MEDIA_REQUEST_NO_CONSENT_UI";

    private static android.app.PendingIntent createMediaRequest(String op,
            java.util.Collection<Uri> uris, boolean value) {
        final android.content.Intent intent = new android.content.Intent(ACTION_NO_CONSENT_UI);
        intent.putExtra("op", op);
        intent.putExtra("value", value);
        intent.putParcelableArrayListExtra("uris",
                new java.util.ArrayList<Uri>(uris == null
                        ? java.util.Collections.<Uri>emptyList() : uris));
        return android.app.PendingIntent.getActivity(
                android.app.Application.getInstance(), op.hashCode(), intent,
                android.app.PendingIntent.FLAG_IMMUTABLE
                        | android.app.PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public static android.app.PendingIntent createWriteRequest(
            android.content.ContentResolver resolver, java.util.Collection<Uri> uris) {
        return createMediaRequest("write", uris, true);
    }

    public static android.app.PendingIntent createDeleteRequest(
            android.content.ContentResolver resolver, java.util.Collection<Uri> uris) {
        return createMediaRequest("delete", uris, true);
    }

    public static android.app.PendingIntent createTrashRequest(
            android.content.ContentResolver resolver, java.util.Collection<Uri> uris,
            boolean value) {
        return createMediaRequest("trash", uris, value);
    }

    public static android.app.PendingIntent createFavoriteRequest(
            android.content.ContentResolver resolver, java.util.Collection<Uri> uris,
            boolean value) {
        return createMediaRequest("favorite", uris, value);
    }

    /**
     * Unlike the create*Request family this one needs NO user consent on AOSP
     * either -- it is a direct write of the IS_FAVORITE column -- so it is
     * implemented for real, through the same ContentResolver.update AOSP uses.
     */
    public static void markIsFavoriteStatus(android.content.ContentResolver resolver,
            java.util.Collection<Uri> uris, boolean value) {
        java.util.Objects.requireNonNull(resolver, "resolver");
        java.util.Objects.requireNonNull(uris, "uris");
        final android.content.ContentValues values = new android.content.ContentValues();
        values.put(MediaColumns.IS_FAVORITE, value ? 1 : 0);
        for (Uri uri : uris) {
            resolver.update(uri, values, null, null);
        }
    }

    // --- Cloud media provider -----------------------------------------------
    // Mac.apk installs no CloudMediaProvider and the photo picker has no cloud
    // backend, so no authority is current or supported. false / a no-op notify
    // is the accurate answer, not a placeholder.

    public static boolean isCurrentCloudMediaProviderAuthority(
            android.content.ContentResolver resolver, String authority) {
        return false;
    }

    public static boolean isSupportedCloudMediaProviderAuthority(
            android.content.ContentResolver resolver, String authority) {
        return false;
    }

    public static void notifyCloudMediaChangedEvent(android.content.ContentResolver resolver,
            String authority, String currentMediaCollectionId) throws SecurityException {
        // AOSP throws SecurityException unless the caller IS the current cloud
        // provider. Nothing is the current cloud provider here, so no caller can
        // legitimately reach this; AOSP's own no-op path (provider absent) is
        // what is left.
    }

    /**
     * AOSP: whether the named package currently holds the System Gallery role.
     * Mac.apk assigns no roles, so no package holds it.
     */
    public static boolean isCurrentSystemGallery(android.content.ContentResolver resolver,
            int uid, String packageName) {
        return false;
    }

    // --- Descriptor / URI translation ---------------------------------------

    /**
     * AOSP asks MediaProvider to translate a media: URI into the equivalent
     * DocumentsProvider URI. Mac.apk registers no MediaDocumentsProvider, so
     * there is no document URI for the item and null -- AOSP's documented
     * @Nullable answer -- is correct.
     */
    public static Uri getDocumentUri(android.content.Context context, Uri mediaUri) {
        return null;
    }

    /**
     * AOSP returns a URI whose reads have location metadata redacted. Redaction
     * is performed by MediaProvider; Mac.apk's provider does not implement it,
     * and handing back the ORIGINAL uri under a "redacted" name would leak the
     * very metadata the caller asked to strip. null (@Nullable in AOSP) says
     * "no redacted view is available", which is the truthful answer.
     */
    public static Uri getRedactedUri(android.content.ContentResolver resolver, Uri uri) {
        return null;
    }

    /** The list form; AOSP is @NonNull, so an empty list stands for "none". */
    public static java.util.List<Uri> getRedactedUri(android.content.ContentResolver resolver,
            java.util.List<Uri> uris) {
        return java.util.Collections.emptyList();
    }

    /**
     * AOSP hands back a descriptor onto the file in its ORIGINAL format,
     * bypassing the HEVC-to-AVC transcoder. Mac.apk never transcodes, so the
     * descriptor the caller already holds IS the original format; AOSP is
     * @NonNull, so a duplicate of it is returned (a dup, not the same object,
     * so the caller can close either independently as the contract implies).
     */
    public static android.os.ParcelFileDescriptor getOriginalMediaFormatFileDescriptor(
            android.content.Context context, android.os.ParcelFileDescriptor pfd)
            throws java.io.IOException {
        java.util.Objects.requireNonNull(pfd, "pfd");
        return pfd.dup();
    }

    /** Pure ContentResolver delegation, as AOSP's own body is. */
    public static android.os.ParcelFileDescriptor openFileDescriptor(
            android.content.ContentResolver resolver, Uri uri, String mode,
            android.os.CancellationSignal cancellationSignal)
            throws java.io.FileNotFoundException {
        java.util.Objects.requireNonNull(resolver, "resolver");
        java.util.Objects.requireNonNull(uri, "uri");
        java.util.Objects.requireNonNull(mode, "mode");
        return resolver.openFileDescriptor(uri, mode, cancellationSignal);
    }

    /** Pure ContentResolver delegation, as AOSP's own body is. */
    public static android.content.res.AssetFileDescriptor openTypedAssetFileDescriptor(
            android.content.ContentResolver resolver, Uri uri, String mimeType, Bundle opts,
            android.os.CancellationSignal cancellationSignal)
            throws java.io.FileNotFoundException {
        java.util.Objects.requireNonNull(resolver, "resolver");
        java.util.Objects.requireNonNull(uri, "uri");
        java.util.Objects.requireNonNull(mimeType, "mimeType");
        return resolver.openTypedAssetFileDescriptor(uri, mimeType, opts, cancellationSignal);
    }
}
