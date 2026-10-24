package android.provider;

import android.content.Context;
import android.net.Uri;

/**
 * The Telephony provider contract (SMS/MMS/carriers).
 *
 * ⚠ Mac.apk deliberately registers NO telephony provider -- that is a standing,
 * separately-tracked design decision, not an oversight. This file therefore
 * declares only the CONTRACT: URI and column-name constants an SMS-aware app
 * compiles against, plus the one accessor AOSP answers from PackageManager
 * rather than from the provider. Declaring a Uri constant does not register a
 * provider and does not change ContentResolver's routing; a query against
 * CONTENT_URI still resolves to nothing, which is what an app on a device with
 * SMS disabled already sees.
 *
 * Source: frameworks/base/core/java/android/provider/Telephony.java
 */
public final class Telephony {

    private Telephony() { }

    /** Columns shared by the SMS tables. */
    public interface TextBasedSmsColumns {
        public static final String TYPE = "type";
        public static final int MESSAGE_TYPE_ALL = 0;
        public static final int MESSAGE_TYPE_INBOX = 1;
        public static final int MESSAGE_TYPE_SENT = 2;
        public static final int MESSAGE_TYPE_DRAFT = 3;
        public static final int MESSAGE_TYPE_OUTBOX = 4;
        public static final int MESSAGE_TYPE_FAILED = 5;
        public static final int MESSAGE_TYPE_QUEUED = 6;

        public static final String THREAD_ID = "thread_id";
        public static final String ADDRESS = "address";
        public static final String DATE = "date";
        public static final String DATE_SENT = "date_sent";
        public static final String READ = "read";
        public static final String SEEN = "seen";
        public static final String STATUS = "status";
        public static final int STATUS_NONE = -1;
        public static final int STATUS_COMPLETE = 0;
        public static final int STATUS_PENDING = 32;
        public static final int STATUS_FAILED = 64;

        public static final String SUBJECT = "subject";
        public static final String BODY = "body";
        public static final String PERSON = "person";
        public static final String PROTOCOL = "protocol";
        public static final String REPLY_PATH_PRESENT = "reply_path_present";
        public static final String SERVICE_CENTER = "service_center";
        public static final String LOCKED = "locked";
        public static final String SUBSCRIPTION_ID = "sub_id";
        public static final String ERROR_CODE = "error_code";
        public static final String CREATOR = "creator";
    }

    /** SMS message table contract. */
    public static final class Sms implements BaseColumns, TextBasedSmsColumns {

        private Sms() { }

        public static final Uri CONTENT_URI = Uri.parse("content://sms");

        public static final String DEFAULT_SORT_ORDER = "date DESC";

        /**
         * AOSP answers this from RoleManager/PackageManager, not from the SMS
         * provider. Mac.apk has no SMS-capable app installed and no role
         * assignment, so null -- AOSP's documented "no default SMS app" -- is
         * the accurate answer.
         */
        public static String getDefaultSmsPackage(Context context) { return null; }

        public static final class Inbox implements BaseColumns, TextBasedSmsColumns {
            private Inbox() { }
    public static final Uri CONTENT_URI = Uri.parse("content://sms/inbox");
    public static final String DEFAULT_SORT_ORDER = "date DESC";
        }

        public static final class Sent implements BaseColumns, TextBasedSmsColumns {
            private Sent() { }
    public static final Uri CONTENT_URI = Uri.parse("content://sms/sent");
    public static final String DEFAULT_SORT_ORDER = "date DESC";
        }

        public static final class Draft implements BaseColumns, TextBasedSmsColumns {
            private Draft() { }
    public static final Uri CONTENT_URI = Uri.parse("content://sms/draft");
    public static final String DEFAULT_SORT_ORDER = "date DESC";
        }

        public static final class Outbox implements BaseColumns, TextBasedSmsColumns {
            private Outbox() { }
    public static final Uri CONTENT_URI = Uri.parse("content://sms/outbox");
    public static final String DEFAULT_SORT_ORDER = "date DESC";
        }

        public static final class Conversations implements BaseColumns, TextBasedSmsColumns {
            private Conversations() { }
    public static final Uri CONTENT_URI = Uri.parse("content://sms/conversations");
    public static final String DEFAULT_SORT_ORDER = "date DESC";
    public static final String MESSAGE_COUNT = "msg_count";
    public static final String SNIPPET = "snippet";
        }

        /**
         * The SMS broadcast actions. An SMS app declares a receiver with these
         * in its manifest; without the constants it cannot even build. Mac.apk
         * never broadcasts them (no radio), so the receiver simply never fires
         * -- the same as a device with SMS disabled.
         */
        public static final class Intents {
            private Intents() { }
    public static final String ACTION_CHANGE_DEFAULT = "android.provider.Telephony.ACTION_CHANGE_DEFAULT";
    public static final String ACTION_DEFAULT_SMS_PACKAGE_CHANGED = "android.provider.action.DEFAULT_SMS_PACKAGE_CHANGED";
    public static final String ACTION_EXTERNAL_PROVIDER_CHANGE = "android.provider.action.EXTERNAL_PROVIDER_CHANGE";
    public static final String DATA_SMS_RECEIVED_ACTION = "android.intent.action.DATA_SMS_RECEIVED";
    public static final String EXTRA_IS_DEFAULT_SMS_APP = "android.provider.extra.IS_DEFAULT_SMS_APP";
    public static final String EXTRA_PACKAGE_NAME = "package";
    public static final int RESULT_SMS_DATABASE_ERROR = 10;
    public static final int RESULT_SMS_DISPATCH_FAILURE = 6;
    public static final int RESULT_SMS_DUPLICATED = 5;
    public static final int RESULT_SMS_GENERIC_ERROR = 2;
    public static final int RESULT_SMS_HANDLED = 1;
    public static final int RESULT_SMS_INVALID_URI = 11;
    public static final int RESULT_SMS_NULL_MESSAGE = 8;
    public static final int RESULT_SMS_NULL_PDU = 7;
    public static final int RESULT_SMS_OUT_OF_MEMORY = 3;
    public static final int RESULT_SMS_RECEIVED_WHILE_ENCRYPTED = 9;
    public static final int RESULT_SMS_UNSUPPORTED = 4;
    public static final String SECRET_CODE_ACTION = "android.provider.Telephony.SECRET_CODE";
    public static final String SIM_FULL_ACTION = "android.provider.Telephony.SIM_FULL";
    public static final String SMS_CB_RECEIVED_ACTION = "android.provider.Telephony.SMS_CB_RECEIVED";
    public static final String SMS_DELIVER_ACTION = "android.provider.Telephony.SMS_DELIVER";
    public static final String SMS_RECEIVED_ACTION = "android.provider.Telephony.SMS_RECEIVED";
    public static final String SMS_REJECTED_ACTION = "android.provider.Telephony.SMS_REJECTED";
    public static final String SMS_SERVICE_CATEGORY_PROGRAM_DATA_RECEIVED_ACTION = "android.provider.Telephony.SMS_SERVICE_CATEGORY_PROGRAM_DATA_RECEIVED";
    public static final String WAP_PUSH_DELIVER_ACTION = "android.provider.Telephony.WAP_PUSH_DELIVER";
    public static final String WAP_PUSH_RECEIVED_ACTION = "android.provider.Telephony.WAP_PUSH_RECEIVED";

            /**
             * Decodes the "pdus" extra of an SMS_RECEIVED broadcast. Pure
             * parsing over whatever the caller was handed, reproduced verbatim
             * -- so a test that fabricates the broadcast itself works exactly
             * as it would on a device.
             */
            public static android.telephony.SmsMessage[] getMessagesFromIntent(
                    android.content.Intent intent) {
                Object[] messages;
                try {
                    messages = (Object[]) intent.getSerializableExtra("pdus");
                } catch (ClassCastException e) {
                    return null;
                }
                if (messages == null) return null;
                String format = intent.getStringExtra("format");
                int pduCount = messages.length;
                android.telephony.SmsMessage[] msgs =
                        new android.telephony.SmsMessage[pduCount];
                for (int i = 0; i < pduCount; i++) {
                    byte[] pdu = (byte[]) messages[i];
                    msgs[i] = android.telephony.SmsMessage.createFromPdu(pdu, format);
                }
                return msgs;
            }
        }
    }

    // ========================================================================
    // API-36 SMS/MMS contract added 2026-08-24.
    //
    // Same standing rule as the header: these are CONSTANTS ONLY plus a handful
    // of pure helpers. Declaring them registers no provider. Sms.Intents in
    // particular carries the broadcast ACTIONS an SMS app's receiver filters on
    // -- without them the app cannot even declare its receiver, which is a
    // link-time failure rather than a graceful "no SMS here".
    // ========================================================================

public static final String ADDRESS = "address";

    /** The canonical (normalised) address table's columns. */
    public interface CanonicalAddressesColumns extends BaseColumns {
        public static final String ADDRESS = "address";
    }

    /** Columns shared by the MMS tables. */
    public interface BaseMmsColumns extends BaseColumns {
public static final String CONTENT_CLASS = "ct_cls";
public static final String CONTENT_LOCATION = "ct_l";
public static final String CONTENT_TYPE = "ct_t";
public static final String CREATOR = "creator";
public static final String DATE = "date";
public static final String DATE_SENT = "date_sent";
public static final String DELIVERY_REPORT = "d_rpt";
public static final String DELIVERY_TIME = "d_tm";
public static final String EXPIRY = "exp";
public static final String LOCKED = "locked";
public static final String MESSAGE_BOX = "msg_box";
public static final int MESSAGE_BOX_ALL = 0;
public static final int MESSAGE_BOX_DRAFTS = 3;
public static final int MESSAGE_BOX_FAILED = 5;
public static final int MESSAGE_BOX_INBOX = 1;
public static final int MESSAGE_BOX_OUTBOX = 4;
public static final int MESSAGE_BOX_SENT = 2;
public static final String MESSAGE_CLASS = "m_cls";
public static final String MESSAGE_ID = "m_id";
public static final String MESSAGE_SIZE = "m_size";
public static final String MESSAGE_TYPE = "m_type";
public static final String MMS_VERSION = "v";
public static final String PRIORITY = "pri";
public static final String READ = "read";
public static final String READ_REPORT = "rr";
public static final String READ_STATUS = "read_status";
public static final String REPORT_ALLOWED = "rpt_a";
public static final String RESPONSE_STATUS = "resp_st";
public static final String RESPONSE_TEXT = "resp_txt";
public static final String RETRIEVE_STATUS = "retr_st";
public static final String RETRIEVE_TEXT = "retr_txt";
public static final String RETRIEVE_TEXT_CHARSET = "retr_txt_cs";
public static final String SEEN = "seen";
public static final String STATUS = "st";
public static final String SUBJECT = "sub";
public static final String SUBJECT_CHARSET = "sub_cs";
public static final String SUBSCRIPTION_ID = "sub_id";
public static final String TEXT_ONLY = "text_only";
public static final String THREAD_ID = "thread_id";
public static final String TRANSACTION_ID = "tr_id";
    }

    /** Columns of the conversation-thread table. */
    public interface ThreadsColumns extends BaseColumns {
public static final String ARCHIVED = "archived";
public static final String DATE = "date";
public static final String ERROR = "error";
public static final String HAS_ATTACHMENT = "has_attachment";
public static final String MESSAGE_COUNT = "message_count";
public static final String READ = "read";
public static final String RECIPIENT_IDS = "recipient_ids";
public static final String SNIPPET = "snippet";
public static final String SNIPPET_CHARSET = "snippet_cs";
public static final String TYPE = "type";
    }

    public static final class Threads implements ThreadsColumns {
        private Threads() { }
public static final int BROADCAST_THREAD = 1;
public static final int COMMON_THREAD = 0;
public static final Uri CONTENT_URI = Uri.withAppendedPath(MmsSms.CONTENT_URI, "conversations");
public static final Uri OBSOLETE_THREADS_URI = Uri.withAppendedPath(CONTENT_URI, "obsolete");

        /**
         * AOSP resolves (or creates) the thread id for a recipient set by
         * querying a dedicated provider URI. With no telephony provider
         * registered the query returns nothing, and AOSP itself throws
         * IllegalArgumentException when it cannot get a thread id -- so that is
         * what surfaces here rather than a fabricated id, which would make the
         * caller write messages into a thread that does not exist.
         */
        public static long getOrCreateThreadId(android.content.Context context,
                String recipient) {
            java.util.Set<String> recipients = new java.util.HashSet<String>();
            recipients.add(recipient);
            return getOrCreateThreadId(context, recipients);
        }

        public static long getOrCreateThreadId(android.content.Context context,
                java.util.Set<String> recipients) {
            Uri.Builder uriBuilder = THREAD_ID_CONTENT_URI.buildUpon();
            for (String recipient : recipients) {
                uriBuilder.appendQueryParameter("recipient", recipient);
            }
            Uri uri = uriBuilder.build();
            android.database.Cursor cursor = context.getContentResolver().query(
                    uri, new String[] { _ID }, null, null, null);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) return cursor.getLong(0);
                } finally {
                    cursor.close();
                }
            }
            throw new IllegalArgumentException("Unable to find or allocate a thread ID.");
        }

        private static final Uri THREAD_ID_CONTENT_URI =
                Uri.parse("content://mms-sms/threadID");
    }

    public static final class Carriers implements BaseColumns {
        private Carriers() { }
public static final String ALWAYS_ON = "always_on";
public static final String APN = "apn";
public static final String AUTH_TYPE = "authtype";
public static final String BEARER = "bearer";
public static final String CARRIER_ENABLED = "carrier_enabled";
public static final String CARRIER_ID = "carrier_id";
public static final Uri CONTENT_URI = Uri.parse("content://telephony/carriers");
public static final String CURRENT = "current";
public static final String DEFAULT_SORT_ORDER = "name ASC";
public static final String MCC = "mcc";
public static final String MMSC = "mmsc";
public static final String MMSPORT = "mmsport";
public static final String MMSPROXY = "mmsproxy";
public static final String MNC = "mnc";
public static final String MTU_V4 = "mtu_v4";
public static final String MTU_V6 = "mtu_v6";
public static final String MVNO_MATCH_DATA = "mvno_match_data";
public static final String MVNO_TYPE = "mvno_type";
public static final String NAME = "name";
public static final String NETWORK_TYPE_BITMASK = "network_type_bitmask";
public static final String NUMERIC = "numeric";
public static final String PASSWORD = "password";
public static final String PORT = "port";
public static final String PROTOCOL = "protocol";
public static final String PROXY = "proxy";
public static final String ROAMING_PROTOCOL = "roaming_protocol";
public static final String SERVER = "server";
public static final Uri SIM_APN_URI = Uri.parse("content://telephony/carriers/sim_apn_list");
public static final String SUBSCRIPTION_ID = "sub_id";
public static final String TYPE = "type";
public static final String USER = "user";
public static final String USER_EDITABLE = "user_editable";
public static final String USER_VISIBLE = "user_visible";
    }

    public static final class CarrierId implements BaseColumns {
        private CarrierId() { }
public static final String CARRIER_ID = "carrier_id";
public static final String CARRIER_NAME = "carrier_name";
public static final Uri CONTENT_URI = Uri.parse("content://carrier_id");
public static final String SPECIFIC_CARRIER_ID = "specific_carrier_id";
public static final String SPECIFIC_CARRIER_ID_NAME = "specific_carrier_id_name";

        public static Uri getUriForSubscriptionId(int subscriptionId) {
            return CONTENT_URI.buildUpon()
                    .appendEncodedPath(String.valueOf(subscriptionId)).build();
        }

        public static Uri getSpecificCarrierIdUriForSubscriptionId(int subscriptionId) {
            return Uri.withAppendedPath(Uri.withAppendedPath(CONTENT_URI, "specific"),
                    String.valueOf(subscriptionId));
        }
    }

    public static final class ServiceStateTable {
        private ServiceStateTable() { }
public static final String AUTHORITY = "service-state";
public static final Uri CONTENT_URI = Uri.parse("content://service-state/");
public static final String DATA_NETWORK_TYPE = "data_network_type";
public static final String DATA_REG_STATE = "data_reg_state";
public static final String DUPLEX_MODE = "duplex_mode";
public static final String IS_MANUAL_NETWORK_SELECTION = "is_manual_network_selection";
public static final String VOICE_OPERATOR_NUMERIC = "voice_operator_numeric";
public static final String VOICE_REG_STATE = "voice_reg_state";

        public static Uri getUriForSubscriptionId(int subscriptionId) {
            return CONTENT_URI.buildUpon()
                    .appendEncodedPath(String.valueOf(subscriptionId)).build();
        }

        public static Uri getUriForSubscriptionIdAndField(int subscriptionId, String field) {
            return CONTENT_URI.buildUpon()
                    .appendEncodedPath(String.valueOf(subscriptionId))
                    .appendEncodedPath(field).build();
        }
    }

    public static final class Mms implements BaseMmsColumns {
        private Mms() { }
public static final Uri CONTENT_URI = Uri.parse("content://mms");
public static final String DEFAULT_SORT_ORDER = "date DESC";
public static final Uri REPORT_REQUEST_URI = Uri.withAppendedPath(CONTENT_URI, "report-request");
public static final Uri REPORT_STATUS_URI = Uri.withAppendedPath(CONTENT_URI, "report-status");

        public static final class Inbox implements BaseMmsColumns {
            private Inbox() { }
    public static final Uri CONTENT_URI = Uri.parse("content://mms/inbox");
    public static final String DEFAULT_SORT_ORDER = "date DESC";
        }

        public static final class Sent implements BaseMmsColumns {
            private Sent() { }
    public static final Uri CONTENT_URI = Uri.parse("content://mms/sent");
    public static final String DEFAULT_SORT_ORDER = "date DESC";
        }

        public static final class Draft implements BaseMmsColumns {
            private Draft() { }
    public static final Uri CONTENT_URI = Uri.parse("content://mms/drafts");
    public static final String DEFAULT_SORT_ORDER = "date DESC";
        }

        public static final class Outbox implements BaseMmsColumns {
            private Outbox() { }
    public static final Uri CONTENT_URI = Uri.parse("content://mms/outbox");
    public static final String DEFAULT_SORT_ORDER = "date DESC";
        }

        public static final class Addr implements BaseColumns {
            private Addr() { }
    public static final String ADDRESS = "address";
    public static final String CHARSET = "charset";
    public static final String CONTACT_ID = "contact_id";
    public static final String MSG_ID = "msg_id";
    public static final String TYPE = "type";

            public static Uri getAddrUriForMessage(String messageId) {
                return Mms.CONTENT_URI.buildUpon()
                        .appendPath(String.valueOf(messageId)).appendPath("addr").build();
            }
        }

        public static final class Part implements BaseColumns {
            private Part() { }
    public static final String CHARSET = "chset";
    public static final String CONTENT_DISPOSITION = "cd";
    public static final String CONTENT_ID = "cid";
    public static final String CONTENT_LOCATION = "cl";
    public static final String CONTENT_TYPE = "ct";
    public static final Uri CONTENT_URI = Uri.withAppendedPath(Mms.CONTENT_URI, "part");
    public static final String CT_START = "ctt_s";
    public static final String CT_TYPE = "ctt_t";
    public static final String FILENAME = "fn";
    public static final String MSG_ID = "mid";
    public static final String NAME = "name";
    public static final String SEQ = "seq";
    public static final String TEXT = "text";
    public static final String _DATA = "_data";

            public static Uri getPartUriForMessage(String messageId) {
                return Mms.CONTENT_URI.buildUpon()
                        .appendPath(String.valueOf(messageId)).appendPath("part").build();
            }
        }

        public static final class Rate {
            private Rate() { }
    public static final Uri CONTENT_URI = Uri.withAppendedPath(Mms.CONTENT_URI, "rate");
    public static final String SENT_TIME = "sent_time";
        }

        public static final class Intents {
            private Intents() { }
    public static final String CONTENT_CHANGED_ACTION = "android.intent.action.CONTENT_CHANGED";
    public static final String DELETED_CONTENTS = "deleted_contents";
        }
    }

    public static final class MmsSms implements BaseColumns {
        private MmsSms() { }
public static final Uri CONTENT_CONVERSATIONS_URI = Uri.parse("content://mms-sms/conversations");
public static final Uri CONTENT_DRAFT_URI = Uri.parse("content://mms-sms/draft");
public static final Uri CONTENT_FILTER_BYPHONE_URI = Uri.parse("content://mms-sms/messages/byphone");
public static final Uri CONTENT_LOCKED_URI = Uri.parse("content://mms-sms/locked");
public static final Uri CONTENT_UNDELIVERED_URI = Uri.parse("content://mms-sms/undelivered");
public static final Uri CONTENT_URI = Uri.parse("content://mms-sms/");
public static final int ERR_TYPE_GENERIC = 1;
public static final int ERR_TYPE_GENERIC_PERMANENT = 10;
public static final int ERR_TYPE_MMS_PROTO_PERMANENT = 12;
public static final int ERR_TYPE_MMS_PROTO_TRANSIENT = 3;
public static final int ERR_TYPE_SMS_PROTO_PERMANENT = 11;
public static final int ERR_TYPE_SMS_PROTO_TRANSIENT = 2;
public static final int ERR_TYPE_TRANSPORT_FAILURE = 4;
public static final int MMS_PROTO = 1;
public static final int NO_ERROR = 0;
public static final Uri SEARCH_URI = Uri.parse("content://mms-sms/search");
public static final int SMS_PROTO = 0;
public static final String TYPE_DISCRIMINATOR_COLUMN = "transport_type";

        public static final class PendingMessages implements BaseColumns {
            private PendingMessages() { }
    public static final Uri CONTENT_URI = Uri.withAppendedPath(MmsSms.CONTENT_URI, "pending");
    public static final String DUE_TIME = "due_time";
    public static final String ERROR_CODE = "err_code";
    public static final String ERROR_TYPE = "err_type";
    public static final String LAST_TRY = "last_try";
    public static final String MSG_ID = "msg_id";
    public static final String MSG_TYPE = "msg_type";
    public static final String PROTO_TYPE = "proto_type";
    public static final String RETRY_INDEX = "retry_index";
    public static final String SUBSCRIPTION_ID = "pending_sub_id";
        }
    }
}
