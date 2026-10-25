// android.media.tv.TvContract -- the public contract between the TV provider
// (content://android.media.tv) and apps: the column names, content URIs and
// URI builders for channels, programs, recorded/preview/watch-next programs,
// plus the Genres encode/decode helpers (API 21).
//   AOSP: frameworks/base/media/java/android/media/tv/TvContract.java
//         (android14-release). The CODE is AOSP's, transformed mechanically:
//         javadoc and source-only annotations (@IntDef/@StringDef typedefs,
//         @NonNull/@Nullable, @SdkConstant) removed, and every member the SDK
//         stub does not publish (@SystemApi / @hide) made package-private so our
//         public surface is exactly the SDK's. Nothing else was edited except
//         requestChannelBrowsable(), whose target is a @hide TvInputManager
//         method -- see the comment there.
//
// Measured consumer: VLC's Android TV code links TvContract.buildChannelUri /
// buildProgramsUriForChannel / isProgramUri etc. (debug/fwgap_census.py,
// column T). They are pure URI arithmetic and now answer exactly as a device
// does. Queries against these URIs go to content://android.media.tv, a
// provider Mac.apk does not host -- the answer a phone without the TV
// provider gives (ContentResolver finds no provider), not an empty table
// pretending to be one.
// Census + method: docs/fwgap-new-platform-classes_2026-09-26.md.
package android.media.tv;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.net.Uri;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.util.ArraySet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class TvContract {
    public static final String AUTHORITY = "android.media.tv";
    static final String PERMISSION_READ_TV_LISTINGS = "android.permission.READ_TV_LISTINGS";
    private static final String PATH_CHANNEL = "channel";
    private static final String PATH_PROGRAM = "program";
    private static final String PATH_RECORDED_PROGRAM = "recorded_program";
    private static final String PATH_PREVIEW_PROGRAM = "preview_program";
    private static final String PATH_WATCH_NEXT_PROGRAM = "watch_next_program";
    private static final String PATH_PASSTHROUGH = "passthrough";
    static final String ACTION_CHANNEL_BROWSABLE_REQUESTED =
            "android.media.tv.action.CHANNEL_BROWSABLE_REQUESTED";
    public static final String ACTION_REQUEST_CHANNEL_BROWSABLE =
            "android.media.tv.action.REQUEST_CHANNEL_BROWSABLE";
    public static final String ACTION_PREVIEW_PROGRAM_BROWSABLE_DISABLED =
            "android.media.tv.action.PREVIEW_PROGRAM_BROWSABLE_DISABLED";
    public static final String ACTION_WATCH_NEXT_PROGRAM_BROWSABLE_DISABLED =
            "android.media.tv.action.WATCH_NEXT_PROGRAM_BROWSABLE_DISABLED";
    public static final String ACTION_PREVIEW_PROGRAM_ADDED_TO_WATCH_NEXT =
            "android.media.tv.action.PREVIEW_PROGRAM_ADDED_TO_WATCH_NEXT";
    public static final String ACTION_INITIALIZE_PROGRAMS =
            "android.media.tv.action.INITIALIZE_PROGRAMS";
    public static final String EXTRA_CHANNEL_ID = "android.media.tv.extra.CHANNEL_ID";
    static final String EXTRA_PACKAGE_NAME = "android.media.tv.extra.PACKAGE_NAME";
    public static final String EXTRA_PREVIEW_PROGRAM_ID =
            "android.media.tv.extra.PREVIEW_PROGRAM_ID";
    public static final String EXTRA_WATCH_NEXT_PROGRAM_ID =
            "android.media.tv.extra.WATCH_NEXT_PROGRAM_ID";
    static final String EXTRA_RESULT_CODE = "android.media.tv.extra.RESULT_CODE";
    static final int RESULT_OK = 0;
    static final int RESULT_ERROR_IO = 1;
    static final int RESULT_ERROR_INVALID_ARGUMENT = 2;
    static final String METHOD_GET_COLUMNS = "get_columns";
    static final String METHOD_ADD_COLUMN = "add_column";
    static final String METHOD_GET_BLOCKED_PACKAGES = "get_blocked_packages";
    static final String METHOD_BLOCK_PACKAGE = "block_package";
    static final String METHOD_UNBLOCK_PACKAGE = "unblock_package";
    static final String EXTRA_EXISTING_COLUMN_NAMES =
            "android.media.tv.extra.EXISTING_COLUMN_NAMES";
    static final String EXTRA_COLUMN_NAME = "android.media.tv.extra.COLUMN_NAME";
    static final String EXTRA_DATA_TYPE = "android.media.tv.extra.DATA_TYPE";
    static final String EXTRA_DEFAULT_VALUE = "android.media.tv.extra.DEFAULT_VALUE";
    static final String EXTRA_BLOCKED_PACKAGES = "android.media.tv.extra.BLOCKED_PACKAGES";
    static final String PARAM_INPUT = "input";
    static final String PARAM_CHANNEL = "channel";
    static final String PARAM_START_TIME = "start_time";
    static final String PARAM_END_TIME = "end_time";
    static final String PARAM_BROWSABLE_ONLY = "browsable_only";
    static final String PARAM_CANONICAL_GENRE = "canonical_genre";
    static final String PARAM_PREVIEW = "preview";
    static final String PARAM_PACKAGE = "package";
    public static String buildInputId(ComponentName name) {
        return name.flattenToShortString();
    }
    public static Uri buildChannelUri(long channelId) {
        return ContentUris.withAppendedId(Channels.CONTENT_URI, channelId);
    }
    public static Uri buildChannelUriForPassthroughInput(String inputId) {
        return new Uri.Builder().scheme(ContentResolver.SCHEME_CONTENT).authority(AUTHORITY)
                .appendPath(PATH_PASSTHROUGH).appendPath(inputId).build();
    }
    public static Uri buildChannelLogoUri(long channelId) {
        return buildChannelLogoUri(buildChannelUri(channelId));
    }
    public static Uri buildChannelLogoUri(Uri channelUri) {
        if (!isChannelUriForTunerInput(channelUri)) {
            throw new IllegalArgumentException("Not a channel: " + channelUri);
        }
        return Uri.withAppendedPath(channelUri, Channels.Logo.CONTENT_DIRECTORY);
    }
    public static Uri buildChannelsUriForInput(String inputId) {
        return buildChannelsUriForInput(inputId, false);
    }
    static Uri buildChannelsUriForInput(String inputId,
            boolean browsableOnly) {
        Uri.Builder builder = Channels.CONTENT_URI.buildUpon();
        if (inputId != null) {
            builder.appendQueryParameter(PARAM_INPUT, inputId);
        }
        return builder.appendQueryParameter(PARAM_BROWSABLE_ONLY, String.valueOf(browsableOnly))
                .build();
    }
    static Uri buildChannelsUriForInput(String inputId,
            String genre, boolean browsableOnly) {
        if (genre == null) {
            return buildChannelsUriForInput(inputId, browsableOnly);
        }
        if (!Programs.Genres.isCanonical(genre)) {
            throw new IllegalArgumentException("Not a canonical genre: '" + genre + "'");
        }
        return buildChannelsUriForInput(inputId, browsableOnly).buildUpon()
                .appendQueryParameter(PARAM_CANONICAL_GENRE, genre).build();
    }
    public static Uri buildProgramUri(long programId) {
        return ContentUris.withAppendedId(Programs.CONTENT_URI, programId);
    }
    public static Uri buildProgramsUriForChannel(long channelId) {
        return Programs.CONTENT_URI.buildUpon()
                .appendQueryParameter(PARAM_CHANNEL, String.valueOf(channelId)).build();
    }
    public static Uri buildProgramsUriForChannel(Uri channelUri) {
        if (!isChannelUriForTunerInput(channelUri)) {
            throw new IllegalArgumentException("Not a channel: " + channelUri);
        }
        return buildProgramsUriForChannel(ContentUris.parseId(channelUri));
    }
    public static Uri buildProgramsUriForChannel(long channelId, long startTime,
            long endTime) {
        Uri uri = buildProgramsUriForChannel(channelId);
        return uri.buildUpon().appendQueryParameter(PARAM_START_TIME, String.valueOf(startTime))
                .appendQueryParameter(PARAM_END_TIME, String.valueOf(endTime)).build();
    }
    public static Uri buildProgramsUriForChannel(Uri channelUri, long startTime,
            long endTime) {
        if (!isChannelUriForTunerInput(channelUri)) {
            throw new IllegalArgumentException("Not a channel: " + channelUri);
        }
        return buildProgramsUriForChannel(ContentUris.parseId(channelUri), startTime, endTime);
    }
    public static Uri buildRecordedProgramUri(long recordedProgramId) {
        return ContentUris.withAppendedId(RecordedPrograms.CONTENT_URI, recordedProgramId);
    }
    public static Uri buildPreviewProgramUri(long previewProgramId) {
        return ContentUris.withAppendedId(PreviewPrograms.CONTENT_URI, previewProgramId);
    }
    public static Uri buildPreviewProgramsUriForChannel(long channelId) {
        return PreviewPrograms.CONTENT_URI.buildUpon()
                .appendQueryParameter(PARAM_CHANNEL, String.valueOf(channelId)).build();
    }
    public static Uri buildPreviewProgramsUriForChannel(Uri channelUri) {
        if (!isChannelUriForTunerInput(channelUri)) {
            throw new IllegalArgumentException("Not a channel: " + channelUri);
        }
        return buildPreviewProgramsUriForChannel(ContentUris.parseId(channelUri));
    }
    public static Uri buildWatchNextProgramUri(long watchNextProgramId) {
        return ContentUris.withAppendedId(WatchNextPrograms.CONTENT_URI, watchNextProgramId);
    }
    static Uri buildWatchedProgramUri(long watchedProgramId) {
        return ContentUris.withAppendedId(WatchedPrograms.CONTENT_URI, watchedProgramId);
    }
    private static boolean isTvUri(Uri uri) {
        return uri != null && ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())
                && AUTHORITY.equals(uri.getAuthority());
    }
    private static boolean isTwoSegmentUriStartingWith(Uri uri, String pathSegment) {
        List<String> pathSegments = uri.getPathSegments();
        return pathSegments.size() == 2 && pathSegment.equals(pathSegments.get(0));
    }
    public static boolean isChannelUri(Uri uri) {
        return isChannelUriForTunerInput(uri) || isChannelUriForPassthroughInput(uri);
    }
    public static boolean isChannelUriForTunerInput(Uri uri) {
        return isTvUri(uri) && isTwoSegmentUriStartingWith(uri, PATH_CHANNEL);
    }
    public static boolean isChannelUriForPassthroughInput(Uri uri) {
        return isTvUri(uri) && isTwoSegmentUriStartingWith(uri, PATH_PASSTHROUGH);
    }
    public static boolean isProgramUri(Uri uri) {
        return isTvUri(uri) && isTwoSegmentUriStartingWith(uri, PATH_PROGRAM);
    }
    public static boolean isRecordedProgramUri(Uri uri) {
        return isTvUri(uri) && isTwoSegmentUriStartingWith(uri, PATH_RECORDED_PROGRAM);
    }
    public static void requestChannelBrowsable(Context context, long channelId) {
        TvInputManager manager = (TvInputManager) context.getSystemService(
            Context.TV_INPUT_SERVICE);
        if (manager != null) {
            // AOSP forwards buildChannelUri(channelId) to the @hide
            // TvInputManager.requestChannelBrowsable(Uri), a binder call into
            // TvInputManagerService, which broadcasts
            // ACTION_CHANNEL_BROWSABLE_REQUESTED to the system TV app's
            // receiver. Mac.apk has no TvInputManagerService and no system TV
            // app, so the request has nowhere to go -- the same outcome as a
            // phone, where getSystemService(TV_INPUT_SERVICE) is null and this
            // branch never runs. The URI is still built so a bad id fails
            // here exactly as AOSP's argument evaluation would.
            buildChannelUri(channelId);
        }
    }
    private TvContract() {}
    public interface BaseTvColumns extends BaseColumns {
        String COLUMN_PACKAGE_NAME = "package_name";
    }
    interface ProgramColumns {
        int REVIEW_RATING_STYLE_STARS = 0;
        int REVIEW_RATING_STYLE_THUMBS_UP_DOWN = 1;
        int REVIEW_RATING_STYLE_PERCENTAGE = 2;
        String COLUMN_TITLE = "title";
        String COLUMN_SEASON_DISPLAY_NUMBER = "season_display_number";
        String COLUMN_SEASON_TITLE = "season_title";
        String COLUMN_EPISODE_DISPLAY_NUMBER = "episode_display_number";
        String COLUMN_EPISODE_TITLE = "episode_title";
        String COLUMN_CANONICAL_GENRE = "canonical_genre";
        String COLUMN_SHORT_DESCRIPTION = "short_description";
        String COLUMN_LONG_DESCRIPTION = "long_description";
        String COLUMN_VIDEO_WIDTH = "video_width";
        String COLUMN_VIDEO_HEIGHT = "video_height";
        String COLUMN_AUDIO_LANGUAGE = "audio_language";
        String COLUMN_CONTENT_RATING = "content_rating";
        String COLUMN_POSTER_ART_URI = "poster_art_uri";
        String COLUMN_THUMBNAIL_URI = "thumbnail_uri";
        String COLUMN_SEARCHABLE = "searchable";
        String COLUMN_INTERNAL_PROVIDER_DATA = "internal_provider_data";
        String COLUMN_INTERNAL_PROVIDER_FLAG1 = "internal_provider_flag1";
        String COLUMN_INTERNAL_PROVIDER_FLAG2 = "internal_provider_flag2";
        String COLUMN_INTERNAL_PROVIDER_FLAG3 = "internal_provider_flag3";
        String COLUMN_INTERNAL_PROVIDER_FLAG4 = "internal_provider_flag4";
        String COLUMN_VERSION_NUMBER = "version_number";
        String COLUMN_REVIEW_RATING_STYLE = "review_rating_style";
        String COLUMN_REVIEW_RATING = "review_rating";
        String COLUMN_SERIES_ID = "series_id";
        String COLUMN_SPLIT_ID = "split_id";
    }
    interface PreviewProgramColumns {
        int TYPE_MOVIE = 0;
        int TYPE_TV_SERIES = 1;
        int TYPE_TV_SEASON = 2;
        int TYPE_TV_EPISODE = 3;
        int TYPE_CLIP = 4;
        int TYPE_EVENT = 5;
        int TYPE_CHANNEL = 6;
        int TYPE_TRACK = 7;
        int TYPE_ALBUM = 8;
        int TYPE_ARTIST = 9;
        int TYPE_PLAYLIST = 10;
        int TYPE_STATION = 11;
        int ASPECT_RATIO_16_9 = 0;
        int ASPECT_RATIO_3_2 = 1;
        int ASPECT_RATIO_4_3 = 2;
        int ASPECT_RATIO_1_1 = 3;
        int ASPECT_RATIO_2_3 = 4;
        int AVAILABILITY_AVAILABLE = 0;
        int AVAILABILITY_FREE_WITH_SUBSCRIPTION = 1;
        int AVAILABILITY_PAID_CONTENT = 2;
        int INTERACTION_TYPE_VIEWS = 0;
        int INTERACTION_TYPE_LISTENS = 1;
        int INTERACTION_TYPE_FOLLOWERS = 2;
        int INTERACTION_TYPE_FANS = 3;
        int INTERACTION_TYPE_LIKES = 4;
        int INTERACTION_TYPE_THUMBS = 5;
        int INTERACTION_TYPE_VIEWERS = 6;
        String COLUMN_TYPE = "type";
        String COLUMN_POSTER_ART_ASPECT_RATIO = "poster_art_aspect_ratio";
        String COLUMN_THUMBNAIL_ASPECT_RATIO = "poster_thumbnail_aspect_ratio";
        String COLUMN_LOGO_URI = "logo_uri";
        String COLUMN_AVAILABILITY = "availability";
        String COLUMN_STARTING_PRICE = "starting_price";
        String COLUMN_OFFER_PRICE = "offer_price";
        String COLUMN_RELEASE_DATE = "release_date";
        String COLUMN_ITEM_COUNT = "item_count";
        String COLUMN_LIVE = "live";
        String COLUMN_INTERNAL_PROVIDER_ID = "internal_provider_id";
        String COLUMN_PREVIEW_VIDEO_URI = "preview_video_uri";
        String COLUMN_LAST_PLAYBACK_POSITION_MILLIS =
                "last_playback_position_millis";
        String COLUMN_DURATION_MILLIS = "duration_millis";
        String COLUMN_INTENT_URI = "intent_uri";
        String COLUMN_TRANSIENT = "transient";
        String COLUMN_INTERACTION_TYPE = "interaction_type";
        String COLUMN_INTERACTION_COUNT = "interaction_count";
        String COLUMN_AUTHOR = "author";
        String COLUMN_BROWSABLE = "browsable";
        String COLUMN_CONTENT_ID = "content_id";
        String COLUMN_START_TIME_UTC_MILLIS = "start_time_utc_millis";
        String COLUMN_END_TIME_UTC_MILLIS = "end_time_utc_millis";
    }
    public static final class Channels implements BaseTvColumns {
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/"
                + PATH_CHANNEL);
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/channel";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/channel";
        public static final String TYPE_OTHER = "TYPE_OTHER";
        public static final String TYPE_NTSC = "TYPE_NTSC";
        public static final String TYPE_PAL = "TYPE_PAL";
        public static final String TYPE_SECAM = "TYPE_SECAM";
        public static final String TYPE_DVB_T = "TYPE_DVB_T";
        public static final String TYPE_DVB_T2 = "TYPE_DVB_T2";
        public static final String TYPE_DVB_S = "TYPE_DVB_S";
        public static final String TYPE_DVB_S2 = "TYPE_DVB_S2";
        public static final String TYPE_DVB_C = "TYPE_DVB_C";
        public static final String TYPE_DVB_C2 = "TYPE_DVB_C2";
        public static final String TYPE_DVB_H = "TYPE_DVB_H";
        public static final String TYPE_DVB_SH = "TYPE_DVB_SH";
        public static final String TYPE_ATSC_T = "TYPE_ATSC_T";
        public static final String TYPE_ATSC_C = "TYPE_ATSC_C";
        public static final String TYPE_ATSC_M_H = "TYPE_ATSC_M_H";
        public static final String TYPE_ATSC3_T = "TYPE_ATSC3_T";
        public static final String TYPE_ISDB_T = "TYPE_ISDB_T";
        public static final String TYPE_ISDB_TB = "TYPE_ISDB_TB";
        public static final String TYPE_ISDB_S = "TYPE_ISDB_S";
        public static final String TYPE_ISDB_S3 = "TYPE_ISDB_S3";
        public static final String TYPE_ISDB_C = "TYPE_ISDB_C";
        public static final String TYPE_1SEG = "TYPE_1SEG";
        public static final String TYPE_DTMB = "TYPE_DTMB";
        public static final String TYPE_CMMB = "TYPE_CMMB";
        public static final String TYPE_T_DMB = "TYPE_T_DMB";
        public static final String TYPE_S_DMB = "TYPE_S_DMB";
        public static final String TYPE_PREVIEW = "TYPE_PREVIEW";
        public static final String SERVICE_TYPE_OTHER = "SERVICE_TYPE_OTHER";
        public static final String SERVICE_TYPE_AUDIO_VIDEO = "SERVICE_TYPE_AUDIO_VIDEO";
        public static final String SERVICE_TYPE_AUDIO = "SERVICE_TYPE_AUDIO";
        public static final String VIDEO_FORMAT_240P = "VIDEO_FORMAT_240P";
        public static final String VIDEO_FORMAT_360P = "VIDEO_FORMAT_360P";
        public static final String VIDEO_FORMAT_480I = "VIDEO_FORMAT_480I";
        public static final String VIDEO_FORMAT_480P = "VIDEO_FORMAT_480P";
        public static final String VIDEO_FORMAT_576I = "VIDEO_FORMAT_576I";
        public static final String VIDEO_FORMAT_576P = "VIDEO_FORMAT_576P";
        public static final String VIDEO_FORMAT_720P = "VIDEO_FORMAT_720P";
        public static final String VIDEO_FORMAT_1080I = "VIDEO_FORMAT_1080I";
        public static final String VIDEO_FORMAT_1080P = "VIDEO_FORMAT_1080P";
        public static final String VIDEO_FORMAT_2160P = "VIDEO_FORMAT_2160P";
        public static final String VIDEO_FORMAT_4320P = "VIDEO_FORMAT_4320P";
        public static final String VIDEO_RESOLUTION_SD = "VIDEO_RESOLUTION_SD";
        public static final String VIDEO_RESOLUTION_ED = "VIDEO_RESOLUTION_ED";
        public static final String VIDEO_RESOLUTION_HD = "VIDEO_RESOLUTION_HD";
        public static final String VIDEO_RESOLUTION_FHD = "VIDEO_RESOLUTION_FHD";
        public static final String VIDEO_RESOLUTION_UHD = "VIDEO_RESOLUTION_UHD";
        private static final Map<String, String> VIDEO_FORMAT_TO_RESOLUTION_MAP = new HashMap<>();
        static {
            VIDEO_FORMAT_TO_RESOLUTION_MAP.put(VIDEO_FORMAT_480I, VIDEO_RESOLUTION_SD);
            VIDEO_FORMAT_TO_RESOLUTION_MAP.put(VIDEO_FORMAT_480P, VIDEO_RESOLUTION_ED);
            VIDEO_FORMAT_TO_RESOLUTION_MAP.put(VIDEO_FORMAT_576I, VIDEO_RESOLUTION_SD);
            VIDEO_FORMAT_TO_RESOLUTION_MAP.put(VIDEO_FORMAT_576P, VIDEO_RESOLUTION_ED);
            VIDEO_FORMAT_TO_RESOLUTION_MAP.put(VIDEO_FORMAT_720P, VIDEO_RESOLUTION_HD);
            VIDEO_FORMAT_TO_RESOLUTION_MAP.put(VIDEO_FORMAT_1080I, VIDEO_RESOLUTION_HD);
            VIDEO_FORMAT_TO_RESOLUTION_MAP.put(VIDEO_FORMAT_1080P, VIDEO_RESOLUTION_FHD);
            VIDEO_FORMAT_TO_RESOLUTION_MAP.put(VIDEO_FORMAT_2160P, VIDEO_RESOLUTION_UHD);
            VIDEO_FORMAT_TO_RESOLUTION_MAP.put(VIDEO_FORMAT_4320P, VIDEO_RESOLUTION_UHD);
        }
        public static final String getVideoResolution(String videoFormat) {
            return VIDEO_FORMAT_TO_RESOLUTION_MAP.get(videoFormat);
        }
        public static final String COLUMN_INPUT_ID = "input_id";
        public static final String COLUMN_TYPE = "type";
        public static final String COLUMN_SERVICE_TYPE = "service_type";
        public static final String COLUMN_ORIGINAL_NETWORK_ID = "original_network_id";
        public static final String COLUMN_TRANSPORT_STREAM_ID = "transport_stream_id";
        public static final String COLUMN_SERVICE_ID = "service_id";
        public static final String COLUMN_DISPLAY_NUMBER = "display_number";
        public static final String COLUMN_DISPLAY_NAME = "display_name";
        public static final String COLUMN_NETWORK_AFFILIATION = "network_affiliation";
        public static final String COLUMN_DESCRIPTION = "description";
        public static final String COLUMN_VIDEO_FORMAT = "video_format";
        public static final String COLUMN_BROWSABLE = "browsable";
        public static final String COLUMN_SEARCHABLE = "searchable";
        public static final String COLUMN_LOCKED = "locked";
        public static final String COLUMN_APP_LINK_ICON_URI = "app_link_icon_uri";
        public static final String COLUMN_APP_LINK_POSTER_ART_URI = "app_link_poster_art_uri";
        public static final String COLUMN_APP_LINK_TEXT = "app_link_text";
        public static final String COLUMN_APP_LINK_COLOR = "app_link_color";
        public static final String COLUMN_APP_LINK_INTENT_URI = "app_link_intent_uri";
        public static final String COLUMN_INTERNAL_PROVIDER_ID = "internal_provider_id";
        public static final String COLUMN_INTERNAL_PROVIDER_DATA = "internal_provider_data";
        public static final String COLUMN_INTERNAL_PROVIDER_FLAG1 = "internal_provider_flag1";
        public static final String COLUMN_INTERNAL_PROVIDER_FLAG2 = "internal_provider_flag2";
        public static final String COLUMN_INTERNAL_PROVIDER_FLAG3 = "internal_provider_flag3";
        public static final String COLUMN_INTERNAL_PROVIDER_FLAG4 = "internal_provider_flag4";
        public static final String COLUMN_VERSION_NUMBER = "version_number";
        public static final String COLUMN_TRANSIENT = "transient";
        public static final String COLUMN_GLOBAL_CONTENT_ID = "global_content_id";
        public static final String COLUMN_REMOTE_CONTROL_KEY_PRESET_NUMBER =
                "remote_control_key_preset_number";
        public static final String COLUMN_SCRAMBLED = "scrambled";
        public static final String COLUMN_VIDEO_RESOLUTION = "video_resolution";
        public static final String COLUMN_CHANNEL_LIST_ID = "channel_list_id";
        public static final String COLUMN_BROADCAST_GENRE = Programs.COLUMN_BROADCAST_GENRE;
        private Channels() {}
        public static final class Logo {
            public static final String CONTENT_DIRECTORY = "logo";
            private Logo() {}
        }
    }
    public static final class Programs implements BaseTvColumns, ProgramColumns {
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/"
                + PATH_PROGRAM);
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/program";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/program";
        public static final String COLUMN_CHANNEL_ID = "channel_id";
        @Deprecated
        public static final String COLUMN_SEASON_NUMBER = "season_number";
        @Deprecated
        public static final String COLUMN_EPISODE_NUMBER = "episode_number";
        public static final String COLUMN_START_TIME_UTC_MILLIS = "start_time_utc_millis";
        public static final String COLUMN_END_TIME_UTC_MILLIS = "end_time_utc_millis";
        public static final String COLUMN_BROADCAST_GENRE = "broadcast_genre";
        public static final String COLUMN_RECORDING_PROHIBITED = "recording_prohibited";
        public static final String COLUMN_EVENT_ID = "event_id";
        public static final String COLUMN_GLOBAL_CONTENT_ID = "global_content_id";
        public static final String COLUMN_SCRAMBLED = "scrambled";
        public static final String COLUMN_MULTI_SERIES_ID = "multi_series_id";
        public static final String COLUMN_INTERNAL_PROVIDER_ID = "internal_provider_id";
        private Programs() {}
        public static final class Genres {
            public static final String FAMILY_KIDS = "FAMILY_KIDS";
            public static final String SPORTS = "SPORTS";
            public static final String SHOPPING = "SHOPPING";
            public static final String MOVIES = "MOVIES";
            public static final String COMEDY = "COMEDY";
            public static final String TRAVEL = "TRAVEL";
            public static final String DRAMA = "DRAMA";
            public static final String EDUCATION = "EDUCATION";
            public static final String ANIMAL_WILDLIFE = "ANIMAL_WILDLIFE";
            public static final String NEWS = "NEWS";
            public static final String GAMING = "GAMING";
            public static final String ARTS = "ARTS";
            public static final String ENTERTAINMENT = "ENTERTAINMENT";
            public static final String LIFE_STYLE = "LIFE_STYLE";
            public static final String MUSIC = "MUSIC";
            public static final String PREMIER = "PREMIER";
            public static final String TECH_SCIENCE = "TECH_SCIENCE";
            private static final ArraySet<String> CANONICAL_GENRES = new ArraySet<>();
            static {
                CANONICAL_GENRES.add(FAMILY_KIDS);
                CANONICAL_GENRES.add(SPORTS);
                CANONICAL_GENRES.add(SHOPPING);
                CANONICAL_GENRES.add(MOVIES);
                CANONICAL_GENRES.add(COMEDY);
                CANONICAL_GENRES.add(TRAVEL);
                CANONICAL_GENRES.add(DRAMA);
                CANONICAL_GENRES.add(EDUCATION);
                CANONICAL_GENRES.add(ANIMAL_WILDLIFE);
                CANONICAL_GENRES.add(NEWS);
                CANONICAL_GENRES.add(GAMING);
                CANONICAL_GENRES.add(ARTS);
                CANONICAL_GENRES.add(ENTERTAINMENT);
                CANONICAL_GENRES.add(LIFE_STYLE);
                CANONICAL_GENRES.add(MUSIC);
                CANONICAL_GENRES.add(PREMIER);
                CANONICAL_GENRES.add(TECH_SCIENCE);
            }
            private static final char DOUBLE_QUOTE = '"';
            private static final char COMMA = ',';
            private static final String DELIMITER = ",";
            private static final String[] EMPTY_STRING_ARRAY = new String[0];
            private Genres() {}
            public static String encode(String... genres) {
                if (genres == null) {
                    return null;
                }
                StringBuilder sb = new StringBuilder();
                String separator = "";
                for (String genre : genres) {
                    sb.append(separator).append(encodeToCsv(genre));
                    separator = DELIMITER;
                }
                return sb.toString();
            }
            private static String encodeToCsv(String genre) {
                StringBuilder sb = new StringBuilder();
                int length = genre.length();
                for (int i = 0; i < length; ++i) {
                    char c = genre.charAt(i);
                    switch (c) {
                        case DOUBLE_QUOTE:
                            sb.append(DOUBLE_QUOTE);
                            break;
                        case COMMA:
                            sb.append(DOUBLE_QUOTE);
                            break;
                    }
                    sb.append(c);
                }
                return sb.toString();
            }
            public static String[] decode(String genres) {
                if (TextUtils.isEmpty(genres)) {
                    return EMPTY_STRING_ARRAY;
                }
                if (genres.indexOf(COMMA) == -1 && genres.indexOf(DOUBLE_QUOTE) == -1) {
                    return new String[] {genres.trim()};
                }
                StringBuilder sb = new StringBuilder();
                List<String> results = new ArrayList<>();
                int length = genres.length();
                boolean escape = false;
                for (int i = 0; i < length; ++i) {
                    char c = genres.charAt(i);
                    switch (c) {
                        case DOUBLE_QUOTE:
                            if (!escape) {
                                escape = true;
                                continue;
                            }
                            break;
                        case COMMA:
                            if (!escape) {
                                String string = sb.toString().trim();
                                if (string.length() > 0) {
                                    results.add(string);
                                }
                                sb = new StringBuilder();
                                continue;
                            }
                            break;
                    }
                    sb.append(c);
                    escape = false;
                }
                String string = sb.toString().trim();
                if (string.length() > 0) {
                    results.add(string);
                }
                return results.toArray(new String[results.size()]);
            }
            public static boolean isCanonical(String genre) {
                return CANONICAL_GENRES.contains(genre);
            }
        }
    }
    public static final class RecordedPrograms implements BaseTvColumns, ProgramColumns {
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/"
                + PATH_RECORDED_PROGRAM);
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/recorded_program";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/recorded_program";
        public static final String COLUMN_CHANNEL_ID = "channel_id";
        public static final String COLUMN_INPUT_ID = "input_id";
        public static final String COLUMN_START_TIME_UTC_MILLIS =
                Programs.COLUMN_START_TIME_UTC_MILLIS;
        public static final String COLUMN_END_TIME_UTC_MILLIS = Programs.COLUMN_END_TIME_UTC_MILLIS;
        public static final String COLUMN_BROADCAST_GENRE = Programs.COLUMN_BROADCAST_GENRE;
        public static final String COLUMN_RECORDING_DATA_URI = "recording_data_uri";
        public static final String COLUMN_RECORDING_DATA_BYTES = "recording_data_bytes";
        public static final String COLUMN_RECORDING_DURATION_MILLIS = "recording_duration_millis";
        public static final String COLUMN_RECORDING_EXPIRE_TIME_UTC_MILLIS =
                "recording_expire_time_utc_millis";
        public static final String COLUMN_MULTI_SERIES_ID = "multi_series_id";
        public static final String COLUMN_INTERNAL_PROVIDER_ID = "internal_provider_id";
        private RecordedPrograms() {}
    }
    public static final class PreviewPrograms implements BaseTvColumns, ProgramColumns,
        PreviewProgramColumns {
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/"
                + PATH_PREVIEW_PROGRAM);
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/preview_program";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/preview_program";
        public static final String COLUMN_CHANNEL_ID = "channel_id";
        public static final String COLUMN_WEIGHT = "weight";
        private PreviewPrograms() {}
    }
    public static final class WatchNextPrograms implements BaseTvColumns, ProgramColumns,
        PreviewProgramColumns {
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/"
                + PATH_WATCH_NEXT_PROGRAM);
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/watch_next_program";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/watch_next_program";
        public static final int WATCH_NEXT_TYPE_CONTINUE = 0;
        public static final int WATCH_NEXT_TYPE_NEXT = 1;
        public static final int WATCH_NEXT_TYPE_NEW = 2;
        public static final int WATCH_NEXT_TYPE_WATCHLIST = 3;
        public static final String COLUMN_WATCH_NEXT_TYPE = "watch_next_type";
        public static final String COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS =
                "last_engagement_time_utc_millis";
        private WatchNextPrograms() {}
    }
    static final class WatchedPrograms implements BaseTvColumns {
        public static final Uri CONTENT_URI =
                Uri.parse("content://" + AUTHORITY + "/watched_program");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/watched_program";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/watched_program";
        public static final String COLUMN_WATCH_START_TIME_UTC_MILLIS =
                "watch_start_time_utc_millis";
        public static final String COLUMN_WATCH_END_TIME_UTC_MILLIS = "watch_end_time_utc_millis";
        public static final String COLUMN_CHANNEL_ID = "channel_id";
        public static final String COLUMN_TITLE = "title";
        public static final String COLUMN_START_TIME_UTC_MILLIS = "start_time_utc_millis";
        public static final String COLUMN_END_TIME_UTC_MILLIS = "end_time_utc_millis";
        public static final String COLUMN_DESCRIPTION = "description";
        public static final String COLUMN_INTERNAL_TUNE_PARAMS = "tune_params";
        public static final String COLUMN_INTERNAL_SESSION_TOKEN = "session_token";
        private WatchedPrograms() {}
    }
}
