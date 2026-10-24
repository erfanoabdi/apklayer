// android.provider.CallLog -- the call-log content provider contract.
// Apps read recent calls via ContentResolver against Calls.CONTENT_URI; a
// dialer/telecom app would also write entries. Mac.apk has no telephony or
// telecom stack behind this authority, so queries return null (no rows) --
// the same honest "empty call log" a factory-reset device gives, per the
// same pattern as ContactsContract's "no contacts provider" handling.
//
// Source: frameworks/base/core/java/android/provider/CallLog.java
// (android11-release). AOSP's three addCall(...) overloads and
// shouldHaveSharedCallLogEntries are all @hide (stripped from the public
// SDK, and gated by system/carrier permissions besides), and addCall also
// needs android.telecom.CallerInfo, which isn't part of this framework's
// public surface -- so all four are omitted. Everything else here -- every
// URI/column/type constant, and the one public helper method
// (getLastOutgoingCall) -- mirrors AOSP's public API exactly.
package android.provider;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

public class CallLog {
    /** AOSP declares a public no-arg constructor on this contract class. */
    public CallLog() { }

    public static final String AUTHORITY = "call_log";

    /** The content:// style URL for this provider. */
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY);

    /** Contains the recent calls. */
    public static class Calls implements BaseColumns {
        /** AOSP declares a public no-arg constructor on this contract class. */
        public Calls() { }

        /** The content:// style URL for this table. */
        public static final Uri CONTENT_URI = Uri.parse("content://call_log/calls");

        /** The content:// style URL for filtering this table on phone numbers. */
        public static final Uri CONTENT_FILTER_URI =
                Uri.parse("content://call_log/calls/filter");

        /**
         * Query parameter used to limit the number of call logs returned.
         * <p>TYPE: integer</p>
         */
        public static final String LIMIT_PARAM_KEY = "limit";

        /**
         * Query parameter used to specify the starting record to return.
         * <p>TYPE: integer</p>
         */
        public static final String OFFSET_PARAM_KEY = "offset";

        // @hide in AOSP; used only to build CONTENT_URI_WITH_VOICEMAIL below,
        // exactly as AOSP does -- apps never reference the key name itself.
        private static final String ALLOW_VOICEMAILS_PARAM_KEY = "allow_voicemails";

        /**
         * An optional extra used with {@link #CONTENT_TYPE} and
         * {@link android.content.Intent#ACTION_VIEW} to specify that the
         * presented list of calls should be filtered for a particular call
         * type. Apps implementing a call log UI should check for this extra
         * and filter accordingly, or silently ignore it if not applicable.
         */
        public static final String EXTRA_CALL_TYPE_FILTER =
                "android.provider.extra.CALL_TYPE_FILTER";

        /**
         * Content uri used to access call log entries, including voicemail
         * records. Requires READ_CALL_LOG/WRITE_CALL_LOG (and
         * READ_VOICEMAIL/WRITE_VOICEMAIL for the voicemail records).
         */
        public static final Uri CONTENT_URI_WITH_VOICEMAIL = CONTENT_URI.buildUpon()
                .appendQueryParameter(ALLOW_VOICEMAILS_PARAM_KEY, "true")
                .build();

        /** The default sort order for this table. */
        public static final String DEFAULT_SORT_ORDER = "date DESC";

        /**
         * The MIME type of {@link #CONTENT_URI} and {@link #CONTENT_FILTER_URI}
         * providing a directory of calls.
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/calls";

        /** The MIME type of a {@link #CONTENT_URI} sub-directory of a single call. */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/calls";

        /**
         * The type of the call (incoming, outgoing or missed).
         * <p>Type: INTEGER (int)</p>
         * Allowed values: {@link #INCOMING_TYPE}, {@link #OUTGOING_TYPE},
         * {@link #MISSED_TYPE}, {@link #VOICEMAIL_TYPE}, {@link #REJECTED_TYPE},
         * {@link #BLOCKED_TYPE}, {@link #ANSWERED_EXTERNALLY_TYPE}.
         */
        public static final String TYPE = "type";

        /** Call log type for incoming calls. */
        public static final int INCOMING_TYPE = 1;
        /** Call log type for outgoing calls. */
        public static final int OUTGOING_TYPE = 2;
        /** Call log type for missed calls. */
        public static final int MISSED_TYPE = 3;
        /** Call log type for voicemails. */
        public static final int VOICEMAIL_TYPE = 4;
        /** Call log type for calls rejected by direct user action. */
        public static final int REJECTED_TYPE = 5;
        /** Call log type for calls blocked automatically. */
        public static final int BLOCKED_TYPE = 6;
        /**
         * Call log type for a call which was answered on another device. Used
         * when a call rings on multiple devices simultaneously and ends up
         * answered on a device other than the current one.
         */
        public static final int ANSWERED_EXTERNALLY_TYPE = 7;

        /**
         * Bit-mask describing features of the call (e.g. video).
         * <p>Type: INTEGER (int)</p>
         */
        public static final String FEATURES = "features";

        /** Call had video. */
        public static final int FEATURES_VIDEO = 1 << 0;
        /** Call was pulled externally. */
        public static final int FEATURES_PULLED_EXTERNALLY = 1 << 1;
        /** Call was HD. */
        public static final int FEATURES_HD_CALL = 1 << 2;
        /** Call was a WIFI call. */
        public static final int FEATURES_WIFI = 1 << 3;
        /** Call underwent Assisted Dialing. */
        public static final int FEATURES_ASSISTED_DIALING_USED = 1 << 4;
        /** Call was on RTT at some point. */
        public static final int FEATURES_RTT = 1 << 5;
        /** Call was VoLTE. */
        public static final int FEATURES_VOLTE = 1 << 6;

        /**
         * The phone number as the user entered it.
         * <p>Type: TEXT</p>
         */
        public static final String NUMBER = "number";

        /**
         * The number presenting rules set by the network.
         * <p>Type: INTEGER</p>
         * Allowed values: {@link #PRESENTATION_ALLOWED}, {@link #PRESENTATION_RESTRICTED},
         * {@link #PRESENTATION_UNKNOWN}, {@link #PRESENTATION_PAYPHONE}.
         */
        public static final String NUMBER_PRESENTATION = "presentation";

        /** Number is allowed to display for caller id. */
        public static final int PRESENTATION_ALLOWED = 1;
        /** Number is blocked by user. */
        public static final int PRESENTATION_RESTRICTED = 2;
        /** Number is not specified or unknown by network. */
        public static final int PRESENTATION_UNKNOWN = 3;
        /** Number is a pay phone. */
        public static final int PRESENTATION_PAYPHONE = 4;

        /**
         * The ISO 3166-1 two letter country code of the country the user was
         * in when the call was made or received.
         * <p>Type: TEXT</p>
         */
        public static final String COUNTRY_ISO = "countryiso";

        /**
         * The date the call occurred, in milliseconds since the epoch.
         * <p>Type: INTEGER (long)</p>
         */
        public static final String DATE = "date";

        /**
         * The duration of the call in seconds.
         * <p>Type: INTEGER (long)</p>
         */
        public static final String DURATION = "duration";

        /**
         * The data usage of the call in bytes.
         * <p>Type: INTEGER (long)</p>
         */
        public static final String DATA_USAGE = "data_usage";

        /**
         * Whether or not the call has been acknowledged.
         * <p>Type: INTEGER (boolean)</p>
         */
        public static final String NEW = "new";

        /**
         * The cached name associated with the phone number, if it exists.
         * Typically filled in by the dialer app for caching purposes, so it
         * is not guaranteed present or current.
         * <p>Type: TEXT</p>
         */
        public static final String CACHED_NAME = "name";

        /**
         * The cached number type (Home, Work, etc) associated with the
         * phone number, if it exists.
         * <p>Type: INTEGER</p>
         */
        public static final String CACHED_NUMBER_TYPE = "numbertype";

        /**
         * The cached number label, for a custom number type, associated with
         * the phone number, if it exists.
         * <p>Type: TEXT</p>
         */
        public static final String CACHED_NUMBER_LABEL = "numberlabel";

        /**
         * URI of the voicemail entry. Populated only for {@link #VOICEMAIL_TYPE}.
         * <p>Type: TEXT</p>
         */
        public static final String VOICEMAIL_URI = "voicemail_uri";

        /**
         * Transcription of the call or voicemail entry. Only populated for
         * {@link #VOICEMAIL_TYPE} entries that have a valid transcription.
         */
        public static final String TRANSCRIPTION = "transcription";

        /**
         * Whether this item has been read or otherwise consumed by the user.
         * Unlike {@link #NEW} (which just means "acknowledged"), this implies
         * the user has interacted with the entry.
         * <p>Type: INTEGER (boolean)</p>
         */
        public static final String IS_READ = "is_read";

        /**
         * A geocoded location (city, state, or country) for the number
         * associated with this call.
         * <p>Type: TEXT</p>
         */
        public static final String GEOCODED_LOCATION = "geocoded_location";

        /**
         * The cached URI to look up the contact associated with the phone
         * number, if it exists.
         * <p>Type: TEXT</p>
         */
        public static final String CACHED_LOOKUP_URI = "lookup_uri";

        /**
         * The cached phone number of the contact which matches this entry,
         * if it exists.
         * <p>Type: TEXT</p>
         */
        public static final String CACHED_MATCHED_NUMBER = "matched_number";

        /**
         * The cached normalized (E.164) version of the phone number, if it exists.
         * <p>Type: TEXT</p>
         */
        public static final String CACHED_NORMALIZED_NUMBER = "normalized_number";

        /**
         * The cached photo id of the picture associated with the phone
         * number, if it exists.
         * <p>Type: INTEGER (long)</p>
         */
        public static final String CACHED_PHOTO_ID = "photo_id";

        /**
         * The cached photo URI of the picture associated with the phone
         * number, if it exists.
         * <p>Type: TEXT (URI)</p>
         */
        public static final String CACHED_PHOTO_URI = "photo_uri";

        /**
         * The cached phone number, formatted with rules based on the country
         * the user was in when the call was made or received.
         * <p>Type: TEXT</p>
         */
        public static final String CACHED_FORMATTED_NUMBER = "formatted_number";

        // Note: PHONE_ACCOUNT_* constant VALUES are "subscription_*" due to a
        // historic naming baked into call log databases (AOSP's own comment,
        // reproduced verbatim because the values themselves must match).

        /**
         * The component name of the account used to place or receive the
         * call, in string form.
         * <p>Type: TEXT</p>
         */
        public static final String PHONE_ACCOUNT_COMPONENT_NAME = "subscription_component_name";

        /**
         * The identifier for the account used to place or receive the call.
         * <p>Type: TEXT</p>
         */
        public static final String PHONE_ACCOUNT_ID = "subscription_id";

        /**
         * The post-dial portion of a dialed number, including any digits
         * dialed after a TelecomManager pause/wait character and those
         * characters themselves.
         * <p>Type: TEXT</p>
         */
        public static final String POST_DIAL_DIGITS = "post_dial_digits";

        /**
         * For an incoming call, the secondary line number the call was
         * received via, when a SIM has multiple numbers associated with it.
         */
        public static final String VIA_NUMBER = "via_number";

        /**
         * The date the row is last inserted, updated, or marked as deleted,
         * in milliseconds since the epoch. Read only.
         * <p>Type: INTEGER (long)</p>
         */
        public static final String LAST_MODIFIED = "last_modified";

        /**
         * Value for {@link #BLOCK_REASON}: the call was not blocked by a
         * CallScreeningService or any other system call blocking method.
         */
        public static final int BLOCK_REASON_NOT_BLOCKED = 0;

        /**
         * Value for {@link #BLOCK_REASON} when {@link #TYPE} is
         * {@link #BLOCKED_TYPE}: blocked by a CallScreeningService. See
         * {@link #CALL_SCREENING_COMPONENT_NAME} / {@link #CALL_SCREENING_APP_NAME}
         * for which service was responsible.
         */
        public static final int BLOCK_REASON_CALL_SCREENING_SERVICE = 1;

        /**
         * Value for {@link #BLOCK_REASON} when {@link #TYPE} is
         * {@link #BLOCKED_TYPE}: blocked because the user configured this
         * contact to be sent directly to voicemail.
         */
        public static final int BLOCK_REASON_DIRECT_TO_VOICEMAIL = 2;

        /**
         * Value for {@link #BLOCK_REASON} when {@link #TYPE} is
         * {@link #BLOCKED_TYPE}: blocked because the number is in the
         * BlockedNumbers provider.
         */
        public static final int BLOCK_REASON_BLOCKED_NUMBER = 3;

        /**
         * Value for {@link #BLOCK_REASON} when {@link #TYPE} is
         * {@link #BLOCKED_TYPE}: blocked because the user chose to block all
         * calls from unknown numbers.
         */
        public static final int BLOCK_REASON_UNKNOWN_NUMBER = 4;

        /**
         * Value for {@link #BLOCK_REASON} when {@link #TYPE} is
         * {@link #BLOCKED_TYPE}: blocked because the user chose to block all
         * calls from restricted numbers.
         */
        public static final int BLOCK_REASON_RESTRICTED_NUMBER = 5;

        /**
         * Value for {@link #BLOCK_REASON} when {@link #TYPE} is
         * {@link #BLOCKED_TYPE}: blocked because the user chose to block all
         * calls from pay phones.
         */
        public static final int BLOCK_REASON_PAY_PHONE = 6;

        /**
         * Value for {@link #BLOCK_REASON} when {@link #TYPE} is
         * {@link #BLOCKED_TYPE}: blocked because the user chose to block all
         * calls from numbers not in their contacts.
         */
        public static final int BLOCK_REASON_NOT_IN_CONTACTS = 7;

        /**
         * The ComponentName of the CallScreeningService which blocked this
         * call. Populated when {@link #TYPE} is {@link #BLOCKED_TYPE}.
         * <p>Type: TEXT</p>
         */
        public static final String CALL_SCREENING_COMPONENT_NAME =
                "call_screening_component_name";

        /**
         * The name of the app which blocked a call. Populated when
         * {@link #TYPE} is {@link #BLOCKED_TYPE}; kept even if that app is
         * later uninstalled.
         * <p>Type: TEXT</p>
         */
        public static final String CALL_SCREENING_APP_NAME = "call_screening_app_name";

        /**
         * Where {@link #TYPE} is {@link #BLOCKED_TYPE}, indicates the reason
         * a call is blocked.
         * <p>Type: INTEGER</p>
         * Allowed values: {@link #BLOCK_REASON_NOT_BLOCKED},
         * {@link #BLOCK_REASON_CALL_SCREENING_SERVICE},
         * {@link #BLOCK_REASON_DIRECT_TO_VOICEMAIL}, {@link #BLOCK_REASON_BLOCKED_NUMBER},
         * {@link #BLOCK_REASON_UNKNOWN_NUMBER}, {@link #BLOCK_REASON_RESTRICTED_NUMBER},
         * {@link #BLOCK_REASON_PAY_PHONE}, {@link #BLOCK_REASON_NOT_IN_CONTACTS}.
         */
        public static final String BLOCK_REASON = "block_reason";

        /**
         * Query the call log database for the last dialed number.
         * @param context Used to get the content resolver.
         * @return The last phone number dialed (outgoing), or an empty
         * string if none exist yet.
         */
        public static String getLastOutgoingCall(Context context) {
            final ContentResolver resolver = context.getContentResolver();
            Cursor c = null;
            try {
                c = resolver.query(
                        CONTENT_URI,
                        new String[] { NUMBER },
                        TYPE + " = " + OUTGOING_TYPE,
                        null,
                        DEFAULT_SORT_ORDER + " LIMIT 1");
                if (c == null || !c.moveToFirst()) {
                    return "";
                }
                return c.getString(0);
            } finally {
                if (c != null) c.close();
            }
        }
    }

    /**
     * The (very small) table of geocoded call locations.
     *
     * Mac.apk registers no call-log provider, so a query returns nothing --
     * which is what a device with no call history returns too.
     *
     * Source: frameworks/base/core/java/android/provider/CallLog.java
     */
    public static class Locations implements android.provider.BaseColumns {
        private Locations() { }
public static final String AUTHORITY = "call_composer_locations";
public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/call_composer_location";
public static final String CONTENT_TYPE = "vnd.android.cursor.dir/call_composer_location";
public static final Uri CONTENT_URI = Uri.parse("content://call_log/location");
public static final String LATITUDE = "latitude";
public static final String LONGITUDE = "longitude";
    }
}
