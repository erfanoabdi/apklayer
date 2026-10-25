// android.provider.BlockedNumberContract -- the contract between the
// system's blocked-number list and applications (call-screening / dialer
// apps query it to decide whether to silently drop a call or message).
// Mac.apk hosts no blockednumber provider and no telecom stack behind it,
// so every number is honestly "not blocked" -- the same observable answer
// a real device gives when the provider exists but the list is empty,
// which is the common case there too.
//
// Source: frameworks/base/core/java/android/provider/BlockedNumberContract.java
// (android11-release). AOSP's METHOD_*/RES_* Bundle-call keys and the
// entire SystemContract inner class are @hide (stripped from the public
// SDK apps compile against, and gated by system permissions besides), so
// they are omitted; the public surface below -- AUTHORITY(_URI),
// BlockedNumbers' URI/column constants, and the three public static
// methods -- is everything a third-party app can actually see and call.
package android.provider;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

public class BlockedNumberContract {
    private BlockedNumberContract() { }

    private static final String LOG_TAG = "BlockedNumberContract";

    /** The authority for the blocked number provider. */
    public static final String AUTHORITY = "com.android.blockednumber";

    /** A content:// style uri to the authority for the blocked number provider. */
    public static final Uri AUTHORITY_URI = Uri.parse("content://" + AUTHORITY);

    // Bundle-call method/result keys used by isBlocked/unblock/
    // canCurrentUserBlockNumbers below. @hide in AOSP -- apps never
    // reference these by name, only through the three methods that use
    // them -- so they stay private here instead of public+hidden.
    private static final String METHOD_IS_BLOCKED = "is_blocked";
    private static final String METHOD_UNBLOCK = "unblock";
    private static final String RES_NUMBER_IS_BLOCKED = "blocked";
    private static final String RES_NUM_ROWS_DELETED = "num_deleted";
    private static final String METHOD_CAN_CURRENT_USER_BLOCK_NUMBERS =
            "can_current_user_block_numbers";
    private static final String RES_CAN_BLOCK_NUMBERS = "can_block";

    /**
     * Constants to interact with the blocked numbers list.
     */
    public static class BlockedNumbers {
        private BlockedNumbers() { }

        /**
         * Content URI for the blocked numbers.
         * Supported operations: query/delete/insert on {@code blocked};
         * query and delete (selection not supported) on {@code blocked/ID}.
         */
        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, "blocked");

        /** The MIME type of {@link #CONTENT_URI}: a directory of blocked phone numbers. */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/blocked_number";

        /** The MIME type of a single blocked phone number under {@link #CONTENT_URI}. */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/blocked_number";

        /**
         * Auto-generated ID field which monotonically increases.
         * <p>TYPE: long</p>
         */
        public static final String COLUMN_ID = "_id";

        /**
         * Phone number to block, exactly as given by the caller.
         * Must be specified on insert.
         * <p>TYPE: String</p>
         */
        public static final String COLUMN_ORIGINAL_NUMBER = "original_number";

        /**
         * Phone number to block, normalized to E.164 from
         * {@link #COLUMN_ORIGINAL_NUMBER}. Optional on insert; when not
         * specified the system tries to generate it for the current country
         * (still null if the number isn't a valid phone number, e.g. an email).
         * <p>TYPE: String</p>
         */
        public static final String COLUMN_E164_NUMBER = "e164_number";
    }

    /**
     * Returns whether a given number is in the blocked list.
     *
     * <p>Matches {@code phoneNumber} against
     * {@link BlockedNumbers#COLUMN_ORIGINAL_NUMBER} and its E164 form against
     * {@link BlockedNumbers#COLUMN_E164_NUMBER}. Mac.apk has no blocked-number
     * provider registered, so this always returns {@code false} -- the honest
     * answer for a device whose blocklist is empty.
     */
    public static boolean isBlocked(Context context, String phoneNumber) {
        try {
            final Bundle res = context.getContentResolver().call(
                    AUTHORITY_URI, METHOD_IS_BLOCKED, phoneNumber, null);
            return res != null && res.getBoolean(RES_NUMBER_IS_BLOCKED, false);
        } catch (NullPointerException | IllegalArgumentException ex) {
            android.util.Log.w(LOG_TAG, "isBlocked: provider not ready.");
            return false;
        }
    }

    /**
     * Unblocks {@code phoneNumber} if it is blocked (deletes every row whose
     * {@link BlockedNumbers#COLUMN_ORIGINAL_NUMBER} or E164 form matches).
     *
     * @return the number of rows deleted in the blocked number provider.
     */
    public static int unblock(Context context, String phoneNumber) {
        final Bundle res = context.getContentResolver().call(
                AUTHORITY_URI, METHOD_UNBLOCK, phoneNumber, null);
        // AOSP reads res.getInt(...) here unguarded, because the blockednumber
        // provider is mandatory on a real device (never absent) so res is never
        // null there. Mac.apk has no such provider, so unlike AOSP's literal
        // code this guards against null and returns "0 rows deleted" -- the
        // same honest-absence answer isBlocked()/canCurrentUserBlockNumbers()
        // already give, instead of a crash a real device would never produce.
        return res != null ? res.getInt(RES_NUM_ROWS_DELETED, 0) : 0;
    }

    /**
     * Checks if blocking numbers is supported for the current user. Typically
     * only one user at a time can block numbers.
     *
     * @return {@code true} if the current user can block numbers.
     */
    public static boolean canCurrentUserBlockNumbers(Context context) {
        try {
            final Bundle res = context.getContentResolver().call(
                    AUTHORITY_URI, METHOD_CAN_CURRENT_USER_BLOCK_NUMBERS, null, null);
            return res != null && res.getBoolean(RES_CAN_BLOCK_NUMBERS, false);
        } catch (NullPointerException | IllegalArgumentException ex) {
            android.util.Log.w(LOG_TAG, "canCurrentUserBlockNumbers: provider not ready.");
            return false;
        }
    }
}
