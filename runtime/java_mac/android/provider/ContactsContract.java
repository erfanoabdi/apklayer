// android.provider.ContactsContract -- the contacts-database contract.
// Apps query contacts via the CONTENT_URI constants + column-name
// interfaces through ContentResolver. Mac.apk hosts no contacts
// provider, so queries return null (no rows) -- honest "no contacts" --
// but the URI/column/MIME constant surface must be complete so apps
// compile and degrade gracefully. Constant names + string values mirror
// AOSP (developers.android.com ContactsContract reference).
package android.provider;

import android.net.Uri;
import android.accounts.Account;
import android.content.ContentResolver;
import android.content.Context;
import android.content.ContentUris;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public final class ContactsContract {

    /** AOSP declares a public no-arg constructor on this contract class. */
    public ContactsContract() { }

    public static final String AUTHORITY = "com.android.contacts";
    public static final Uri AUTHORITY_URI = Uri.parse("content://com.android.contacts");

    /**
     * AOSP: an id at or above Profile.MIN_ID belongs to the device owner's own
     * profile contact. Pure arithmetic, so this is exact here.
     */
    public static boolean isProfileId(long id) {
        return id >= Profile.MIN_ID;
    }

    public interface ContactNameColumns {
        public static final String DISPLAY_NAME_SOURCE = "display_name_source";
        public static final String DISPLAY_NAME_PRIMARY = "display_name";
        public static final String DISPLAY_NAME_ALTERNATIVE = "display_name_alt";
        public static final String PHONETIC_NAME_STYLE = "phonetic_name_style";
        public static final String PHONETIC_NAME = "phonetic_name";
        public static final String SORT_KEY_PRIMARY = "sort_key";
        public static final String SORT_KEY_ALTERNATIVE = "sort_key_alt";
    }

    public interface ContactOptionsColumns {
        public static final String TIMES_CONTACTED = "times_contacted";
        public static final String LAST_TIME_CONTACTED = "last_time_contacted";
        public static final String STARRED = "starred";
        public static final String PINNED = "pinned";
        public static final String CUSTOM_RINGTONE = "custom_ringtone";
        public static final String SEND_TO_VOICEMAIL = "send_to_voicemail";
    }

    public interface ContactsColumns {
        public static final String DISPLAY_NAME = "display_name";
        public static final String NAME_RAW_CONTACT_ID = "name_raw_contact_id";
        public static final String PHOTO_ID = "photo_id";
        public static final String PHOTO_FILE_ID = "photo_file_id";
        public static final String PHOTO_URI = "photo_uri";
        public static final String PHOTO_THUMBNAIL_URI = "photo_thumb_uri";
        public static final String IN_VISIBLE_GROUP = "in_visible_group";
        public static final String IN_DEFAULT_DIRECTORY = "in_default_directory";
        public static final String HAS_PHONE_NUMBER = "has_phone_number";
        public static final String LOOKUP_KEY = "lookup";
        public static final String CONTACT_LAST_UPDATED_TIMESTAMP = "contact_last_updated_timestamp";
    }

    // The column interfaces below are declared PROTECTED in AOSP. Their
    // constants reach apps through the public classes that implement them
    // (RawContacts.ACCOUNT_NAME, PhoneLookup.NUMBER, ...), which is exactly
    // how AOSP exposes them -- so an app that names ContactsContract.RawContacts
    // .ACCOUNT_NAME resolves, while the interface itself stays non-public as it
    // is on a device.
    protected interface BaseSyncColumns {
        public static final String SYNC1 = "sync1";
        public static final String SYNC2 = "sync2";
        public static final String SYNC3 = "sync3";
        public static final String SYNC4 = "sync4";
    }

    protected interface SyncColumns extends BaseSyncColumns {
        public static final String ACCOUNT_NAME = "account_name";
        public static final String ACCOUNT_TYPE = "account_type";
        public static final String SOURCE_ID = "sourceid";
        public static final String VERSION = "version";
        public static final String DIRTY = "dirty";
    }

    protected interface ContactStatusColumns {
        public static final String CONTACT_PRESENCE = "contact_presence";
        public static final String CONTACT_CHAT_CAPABILITY = "contact_chat_capability";
        public static final String CONTACT_STATUS = "contact_status";
        public static final String CONTACT_STATUS_TIMESTAMP = "contact_status_ts";
        public static final String CONTACT_STATUS_RES_PACKAGE = "contact_status_res_package";
        public static final String CONTACT_STATUS_LABEL = "contact_status_label";
        public static final String CONTACT_STATUS_ICON = "contact_status_icon";
    }

    protected interface StatusColumns {
        public static final String PRESENCE = "mode";
        /** @deprecated AOSP renamed this to {@link #PRESENCE}. */
        @Deprecated public static final String PRESENCE_STATUS = PRESENCE;
        public static final int OFFLINE = 0;
        public static final int INVISIBLE = 1;
        public static final int AWAY = 2;
        public static final int IDLE = 3;
        public static final int DO_NOT_DISTURB = 4;
        public static final int AVAILABLE = 5;
        public static final String STATUS = "status";
        /** @deprecated AOSP renamed this to {@link #STATUS}. */
        @Deprecated public static final String PRESENCE_CUSTOM_STATUS = STATUS;
        public static final String STATUS_TIMESTAMP = "status_ts";
        public static final String STATUS_RES_PACKAGE = "status_res_package";
        public static final String STATUS_LABEL = "status_label";
        public static final String STATUS_ICON = "status_icon";
        public static final String CHAT_CAPABILITY = "chat_capability";
        public static final int CAPABILITY_HAS_VOICE = 1;
        public static final int CAPABILITY_HAS_VIDEO = 2;
        public static final int CAPABILITY_HAS_CAMERA = 4;
    }

    protected interface DataUsageStatColumns {
        /** @deprecated AOSP stopped maintaining usage stats in API 29. */
        @Deprecated public static final String TIMES_USED = "times_used";
        /** @deprecated see {@link #TIMES_USED}. */
        @Deprecated public static final String LAST_TIME_USED = "last_time_used";
    }

    protected interface PhoneLookupColumns {
        public static final String CONTACT_ID = "contact_id";
        public static final String DATA_ID = "data_id";
        public static final String NUMBER = "number";
        public static final String TYPE = "type";
        public static final String LABEL = "label";
        public static final String NORMALIZED_NUMBER = "normalized_number";
    }

    /** AOSP's marker for "every join column a Data-table query can select". */
    protected interface DataColumnsWithJoins extends BaseColumns, ContactNameColumns,
            ContactOptionsColumns, ContactStatusColumns, ContactsColumns, DataColumns,
            DataUsageStatColumns, RawContactsColumns, StatusColumns {
    }

    public static class Contacts implements BaseColumns, ContactsColumns,
            ContactOptionsColumns, ContactNameColumns, ContactStatusColumns {
        private Contacts() {}
        /**
         * AOSP's "frequently contacted" and "starred + frequent" twigs. They are
         * plain URI constants; whether the provider behind them has rows is a
         * separate question from whether the constants are right.
         */
        public static final Uri CONTENT_FREQUENT_URI =
                Uri.withAppendedPath(AUTHORITY_URI, "contacts/frequent");
        public static final Uri CONTENT_GROUP_URI =
                Uri.withAppendedPath(AUTHORITY_URI, "groups");
        public static final Uri CONTENT_STREQUENT_URI =
                Uri.withAppendedPath(AUTHORITY_URI, "contacts/strequent");
        public static final Uri CONTENT_STREQUENT_FILTER_URI =
                Uri.withAppendedPath(CONTENT_STREQUENT_URI, "filter");
        /** Work-profile (managed-profile) twigs. No managed profile exists here. */
        public static final Uri ENTERPRISE_CONTENT_URI =
                Uri.withAppendedPath(AUTHORITY_URI, "contacts_enterprise");
        public static final Uri ENTERPRISE_CONTENT_FILTER_URI =
                Uri.withAppendedPath(AUTHORITY_URI, "contacts/filter_enterprise");

        /**
         * AOSP: contact ids above ENTERPRISE_CONTACT_ID_BASE (1,000,000,000)
         * belong to the managed profile. Pure arithmetic, faithful.
         */
        /** @hide AOSP's ENTERPRISE_CONTACT_ID_BASE: "slightly smaller than 2 ** 30". */
        public static final long ENTERPRISE_CONTACT_ID_BASE = 1000000000L;

        public static boolean isEnterpriseContactId(long contactId) {
            return contactId >= ENTERPRISE_CONTACT_ID_BASE && contactId < Profile.MIN_ID;
        }

        /** @deprecated AOSP: "No longer supported." A genuine no-op on a device too. */
        @Deprecated
        public static void markAsContacted(ContentResolver resolver, long contactId) { }
        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, "contacts");
        public static final Uri CONTENT_LOOKUP_URI =
                Uri.withAppendedPath(CONTENT_URI, "lookup");
        public static final Uri CONTENT_FILTER_URI =
                Uri.withAppendedPath(CONTENT_URI, "filter");
        // AOSP ContactsContract.java:1567 -- the vCard export URI.
        public static final Uri CONTENT_VCARD_URI =
                Uri.withAppendedPath(CONTENT_URI, "as_vcard");
        public static final Uri CONTENT_MULTI_VCARD_URI =
                Uri.withAppendedPath(CONTENT_URI, "as_multi_vcard");
        public static final String QUERY_PARAMETER_VCARD_NO_PHOTO = "no_photo";
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/contact";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/contact";

        public static Uri getLookupUri(long contactId, String lookupKey) {
            if (lookupKey == null) return null;
            Uri uri = Uri.withAppendedPath(CONTENT_LOOKUP_URI, lookupKey);
            return Uri.withAppendedPath(uri, Long.toString(contactId));
        }

        // Ported verbatim from AOSP (frameworks_base main,
        // ContactsContract.java:1720-1739). Returns null on any failure
        // (bad uri, no provider, no matching row) -- never throws. Our
        // ContentResolver.query() already returns null for the unregistered
        // "com.android.contacts" authority (no contacts backend registered),
        // so this composes into the honest "no matching contact" answer
        // AOSP itself gives on a real device with no matching row.
        public static Uri lookupContact(ContentResolver resolver, Uri lookupUri) {
            if (lookupUri == null) {
                return null;
            }
            Cursor c = resolver.query(lookupUri, new String[]{Contacts._ID}, null, null, null);
            if (c == null) {
                return null;
            }
            try {
                if (c.moveToFirst()) {
                    long contactId = c.getLong(0);
                    return ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);
                }
            } finally {
                c.close();
            }
            return null;
        }

        // Sub-directory twig for a contact's photo (AOSP frameworks_base main,
        // ContactsContract.java:2169-2200). AOSP's Photo there additionally
        // implements DataColumnsWithJoins for the join-column surface; only
        // the four twig/column constants openContactPhotoInputStream
        // references below are needed here, so we keep this to the plain
        // constants (values verified verbatim: DISPLAY_PHOTO twig, PHOTO_FILE_ID
        // = DataColumns.DATA14, PHOTO = DataColumns.DATA15) rather than pulling
        // in the full join-column interface hierarchy.
        /** The Data-rows twig of a contact. */
        public static final class Data implements BaseColumns, DataColumns {
            private Data() { }
            public static final String CONTENT_DIRECTORY = "data";
        }

        /** The Data-rows-joined-with-RawContacts twig of a contact. */
        public static final class Entity implements BaseColumns, BaseSyncColumns,
                ContactNameColumns, ContactOptionsColumns, ContactStatusColumns, ContactsColumns,
                DataColumns, DataUsageStatColumns, RawContactsColumns, StatusColumns, SyncColumns {
            private Entity() { }
            public static final String CONTENT_DIRECTORY = "entities";
            public static final String RAW_CONTACT_ID = "raw_contact_id";
            public static final String DATA_ID = "data_id";
        }

        /**
         * The aggregation-suggestions twig. The Builder is pure URI
         * construction -- it appends the contact id, limit and any name
         * parameters -- so it is faithful; only the provider behind the URI is
         * absent.
         */
        public static final class AggregationSuggestions implements BaseColumns, ContactsColumns,
                ContactOptionsColumns, ContactStatusColumns {
            private AggregationSuggestions() { }
            public static final String CONTENT_DIRECTORY = "suggestions";

            public static final class Builder {
                private long mContactId;
                private final java.util.ArrayList<String> mValues = new java.util.ArrayList<>();
                private int mLimit;

                public Builder() { }

                public Builder setContactId(long contactId) {
                    this.mContactId = contactId;
                    return this;
                }

                public Builder addNameParameter(String name) {
                    mValues.add(name);
                    return this;
                }

                public Builder setLimit(int limit) {
                    mLimit = limit;
                    return this;
                }

                public Uri build() {
                    android.net.Uri.Builder builder = Contacts.CONTENT_URI.buildUpon();
                    builder.appendEncodedPath(String.valueOf(mContactId));
                    builder.appendPath(CONTENT_DIRECTORY);
                    if (mLimit != 0) {
                        builder.appendQueryParameter("limit", String.valueOf(mLimit));
                    }
                    for (String value : mValues) {
                        builder.appendQueryParameter("query", "name:" + value);
                    }
                    return builder.build();
                }
            }
        }

        public static final class Photo implements BaseColumns, DataColumnsWithJoins {
            private Photo() {}
            public static final String CONTENT_DIRECTORY = "photo";
            public static final String DISPLAY_PHOTO = "display_photo";
            public static final String PHOTO_FILE_ID = "data14";
            public static final String PHOTO = "data15";
        }

        // Ported verbatim from AOSP (frameworks_base main,
        // ContactsContract.java:2212-2263). Returns null wherever AOSP would
        // (no photo, no provider, IOException opening the high-res asset) --
        // never throws.
        public static InputStream openContactPhotoInputStream(ContentResolver cr, Uri contactUri,
                boolean preferHighres) {
            if (preferHighres) {
                final Uri displayPhotoUri = Uri.withAppendedPath(contactUri,
                        Contacts.Photo.DISPLAY_PHOTO);
                try {
                    AssetFileDescriptor fd = cr.openAssetFileDescriptor(displayPhotoUri, "r");
                    if (fd != null) {
                        return fd.createInputStream();
                    }
                } catch (IOException e) {
                    // fallback to the thumbnail code
                }
            }

            Uri photoUri = Uri.withAppendedPath(contactUri, Photo.CONTENT_DIRECTORY);
            if (photoUri == null) {
                return null;
            }
            Cursor cursor = cr.query(photoUri,
                    new String[] {
                        CommonDataKinds.Photo.PHOTO
                    }, null, null, null);
            try {
                if (cursor == null || !cursor.moveToNext()) {
                    return null;
                }
                byte[] data = cursor.getBlob(0);
                if (data == null) {
                    return null;
                }
                return new ByteArrayInputStream(data);
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }

        public static InputStream openContactPhotoInputStream(ContentResolver cr, Uri contactUri) {
            return openContactPhotoInputStream(cr, contactUri, false);
        }
    }

    public interface RawContactsColumns {
        public static final String CONTACT_ID = "contact_id";
        public static final String AGGREGATION_MODE = "aggregation_mode";
        public static final String DELETED = "deleted";
        public static final String ACCOUNT_NAME = "account_name";
        public static final String ACCOUNT_TYPE = "account_type";
    }

    public static final class RawContacts implements BaseColumns, RawContactsColumns,
            ContactOptionsColumns, ContactNameColumns, SyncColumns {
        private RawContacts() {}
        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, "raw_contacts");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/raw_contact";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/raw_contact";

        /**
         * AOSP walks the raw-contact row to its aggregate contact and builds a
         * lookup URI. With no provider the query returns null and so does this,
         * which is AOSP's own answer when the raw contact does not exist.
         */
        public static Uri getContactLookupUri(ContentResolver resolver, Uri rawContactUri) {
            final Uri dataUri = Uri.withAppendedPath(rawContactUri, Data.CONTENT_DIRECTORY);
            final Cursor cursor = resolver.query(dataUri,
                    new String[] { RawContacts.CONTACT_ID, Contacts.LOOKUP_KEY },
                    null, null, null);
            if (cursor == null) return null;
            try {
                if (cursor.moveToFirst()) {
                    final long contactId = cursor.getLong(0);
                    final String lookupKey = cursor.getString(1);
                    return Contacts.getLookupUri(contactId, lookupKey);
                }
            } finally {
                cursor.close();
            }
            return null;
        }

        /**
         * AOSP reads the local-account name/type from the contacts provider's
         * own config, defaulting to null/null ("the device-local account has no
         * name"). With no provider that default is what we report -- and null is
         * exactly what AOSP returns for the standard unnamed local account.
         */
        public static String getLocalAccountName(Context context) { return null; }

        public static String getLocalAccountType(Context context) { return null; }

        /**
         * AOSP wraps the cursor in an EntityIterator that groups Data rows by
         * raw contact. Mac.apk has no android.content.EntityIterator
         * implementation of that shape, and the cursor is always null here, so
         * this reports the empty iterator AOSP itself returns for an empty
         * cursor. See the class comment: no provider is registered.
         */
        public static android.content.EntityIterator newEntityIterator(Cursor cursor) {
            return emptyEntityIterator(cursor);
        }

        /** The Data sub-directory twig of a raw contact. */
        public static final class Data implements BaseColumns, DataColumns {
            private Data() {}
            public static final String CONTENT_DIRECTORY = "data";
        }

        /** The Data-rows-as-entities twig of a raw contact. */
        public static final class Entity implements BaseColumns, DataColumns {
            private Entity() { }
            public static final String CONTENT_DIRECTORY = "entity";
            public static final String DATA_ID = "data_id";
        }

        /** The full-size photo twig of a raw contact. */
        public static final class DisplayPhoto {
            private DisplayPhoto() { }
            public static final String CONTENT_DIRECTORY = "display_photo";
        }

        /**
         * Which account new contacts go to.
         *
         * Mac.apk registers no contacts provider AND no accounts, so the
         * default account is NOT SET -- AOSP's own state for a device where the
         * user has never chosen one, and the state its callers already branch
         * on.
         */
        public static final class DefaultAccount {
            private DefaultAccount() { }

            public static final String ACTION_MOVE_CONTACTS_TO_DEFAULT_ACCOUNT =
                    "android.provider.action.MOVE_CONTACTS_TO_DEFAULT_ACCOUNT";

            public static DefaultAccountAndState getDefaultAccountForNewContacts(
                    ContentResolver resolver) {
                return DefaultAccountAndState.ofNotSet();
            }

            public static final class DefaultAccountAndState {
                public static final int DEFAULT_ACCOUNT_STATE_NOT_SET = 1;
                public static final int DEFAULT_ACCOUNT_STATE_LOCAL = 2;
                public static final int DEFAULT_ACCOUNT_STATE_CLOUD = 3;
                public static final int DEFAULT_ACCOUNT_STATE_SIM = 4;

                private final int mState;
                private final Account mAccount;

                private DefaultAccountAndState(int state, Account account) {
                    mState = state;
                    mAccount = account;
                }

                public static DefaultAccountAndState ofNotSet() {
                    return new DefaultAccountAndState(DEFAULT_ACCOUNT_STATE_NOT_SET, null);
                }

                public static DefaultAccountAndState ofLocal() {
                    return new DefaultAccountAndState(DEFAULT_ACCOUNT_STATE_LOCAL, null);
                }

                public static DefaultAccountAndState ofCloud(Account account) {
                    return new DefaultAccountAndState(DEFAULT_ACCOUNT_STATE_CLOUD,
                            java.util.Objects.requireNonNull(account));
                }

                public static DefaultAccountAndState ofSim(Account account) {
                    return new DefaultAccountAndState(DEFAULT_ACCOUNT_STATE_SIM, account);
                }

                public int getState() { return mState; }

                public Account getAccount() { return mAccount; }

                @Override public boolean equals(Object o) {
                    if (this == o) return true;
                    if (!(o instanceof DefaultAccountAndState)) return false;
                    DefaultAccountAndState other = (DefaultAccountAndState) o;
                    return mState == other.mState
                            && java.util.Objects.equals(mAccount, other.mAccount);
                }

                @Override public int hashCode() {
                    return java.util.Objects.hash(mState, mAccount);
                }
            }
        }
    }

    public interface DataColumns {
        public static final String MIMETYPE = "mimetype";
        public static final String RAW_CONTACT_ID = "raw_contact_id";
        public static final String IS_PRIMARY = "is_primary";
        public static final String IS_SUPER_PRIMARY = "is_super_primary";
        public static final String DATA_VERSION = "data_version";
        public static final String DATA1 = "data1";
        public static final String DATA2 = "data2";
        public static final String DATA3 = "data3";
        public static final String DATA4 = "data4";
        public static final String DATA5 = "data5";
        public static final String DATA6 = "data6";
        public static final String DATA7 = "data7";
        public static final String DATA8 = "data8";
        public static final String DATA9 = "data9";
        public static final String DATA10 = "data10";
        public static final String DATA11 = "data11";
        public static final String DATA12 = "data12";
        public static final String DATA13 = "data13";
        public static final String DATA14 = "data14";
        public static final String DATA15 = "data15";
    }

    public static final class Data implements DataColumnsWithJoins {
        private Data() {}
        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, "data");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/data";
        /** AOSP's twig name for a raw contact's data rows. */
        public static final String CONTENT_DIRECTORY = "data";

        /**
         * AOSP resolves a data-row URI to its contact's lookup URI. With no
         * provider the query returns null, which is also AOSP's answer when the
         * data row does not exist.
         */
        public static Uri getContactLookupUri(ContentResolver resolver, Uri dataUri) {
            final Cursor cursor = resolver.query(dataUri,
                    new String[] { RawContacts.CONTACT_ID, Contacts.LOOKUP_KEY },
                    null, null, null);
            if (cursor == null) return null;
            try {
                if (cursor.moveToFirst()) {
                    final long contactId = cursor.getLong(0);
                    final String lookupKey = cursor.getString(1);
                    return Contacts.getLookupUri(contactId, lookupKey);
                }
            } finally {
                cursor.close();
            }
            return null;
        }
    }

    public static final class CommonDataKinds {
        private CommonDataKinds() {}

        public interface CommonColumns {
            public static final String TYPE = "data2";
            public static final String DATA = "data1";
            public static final String LABEL = "data3";
        }


        // ---- API-36 CommonDataKinds added 2026-08-24 -----------------------
        // Pure contract classes: MIME types, DATA-column aliases and type
        // enums. The three EXTRA_ADDRESS_BOOK_INDEX* keys are shared by every
        // one of them, exactly as in AOSP. The getTypeLabel families return
        // AOSP's own English strings because Mac.apk does not ship the
        // platform string table -- see CommonDataKinds.Phone.getTypeLabel for
        // the full reasoning and the getTypeLabelResource caveat.
        // Source: frameworks/base/core/java/android/provider/ContactsContract.java

        private static final String EXTRA_ABI = "android.provider.extra.ADDRESS_BOOK_INDEX";
        private static final String EXTRA_ABI_COUNTS =
                "android.provider.extra.ADDRESS_BOOK_INDEX_COUNTS";
        private static final String EXTRA_ABI_TITLES =
                "android.provider.extra.ADDRESS_BOOK_INDEX_TITLES";

        public interface BaseTypes {
            public static final int TYPE_CUSTOM = 0;
        }

        /** The union of everything that can be dialled (phone + SIP). */
        public static final class Callable implements CommonColumns, DataColumnsWithJoins {
            /** AOSP declares a public no-arg constructor. */
            public Callable() { }
            public static final Uri CONTENT_URI =
                    Uri.withAppendedPath(Data.CONTENT_URI, "callables");
            public static final Uri CONTENT_FILTER_URI =
                    Uri.withAppendedPath(CONTENT_URI, "filter");
            public static final Uri ENTERPRISE_CONTENT_FILTER_URI =
                    Uri.withAppendedPath(CONTENT_URI, "filter_enterprise");
            public static final String EXTRA_ADDRESS_BOOK_INDEX = EXTRA_ABI;
            public static final String EXTRA_ADDRESS_BOOK_INDEX_COUNTS = EXTRA_ABI_COUNTS;
            public static final String EXTRA_ADDRESS_BOOK_INDEX_TITLES = EXTRA_ABI_TITLES;
        }

        /** The union of everything a person can be contacted by. */
        public static final class Contactables implements CommonColumns, DataColumnsWithJoins {
            /** AOSP declares a public no-arg constructor. */
            public Contactables() { }
            public static final Uri CONTENT_URI =
                    Uri.withAppendedPath(Data.CONTENT_URI, "contactables");
            public static final Uri CONTENT_FILTER_URI =
                    Uri.withAppendedPath(CONTENT_URI, "filter");
            public static final String VISIBLE_CONTACTS_ONLY = "visible_contacts_only";
            public static final String EXTRA_ADDRESS_BOOK_INDEX = EXTRA_ABI;
            public static final String EXTRA_ADDRESS_BOOK_INDEX_COUNTS = EXTRA_ABI_COUNTS;
            public static final String EXTRA_ADDRESS_BOOK_INDEX_TITLES = EXTRA_ABI_TITLES;
        }

        public static final class Event implements CommonColumns, DataColumnsWithJoins {
            private Event() { }
            public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/contact_event";
            public static final String START_DATE = DATA;
            public static final int TYPE_CUSTOM = 0;
            public static final int TYPE_ANNIVERSARY = 1;
            public static final int TYPE_OTHER = 2;
            public static final int TYPE_BIRTHDAY = 3;
            public static final String EXTRA_ADDRESS_BOOK_INDEX = EXTRA_ABI;
            public static final String EXTRA_ADDRESS_BOOK_INDEX_COUNTS = EXTRA_ABI_COUNTS;
            public static final String EXTRA_ADDRESS_BOOK_INDEX_TITLES = EXTRA_ABI_TITLES;

            private static final String[] TYPE_LABELS = { "Anniversary", "Other", "Birthday" };

            /** See Phone.getTypeLabelResource: no platform string table here. */
            public static int getTypeResource(Integer type) { return 0; }

            public static CharSequence getTypeLabel(android.content.res.Resources res, int type,
                    CharSequence label) {
                if (type == TYPE_CUSTOM && label != null && label.length() > 0) return label;
                return type >= 1 && type <= TYPE_LABELS.length ? TYPE_LABELS[type - 1] : "Custom";
            }
        }

        public static final class GroupMembership implements DataColumnsWithJoins {
            private GroupMembership() { }
            public static final String CONTENT_ITEM_TYPE =
                    "vnd.android.cursor.item/group_membership";
            public static final String GROUP_ROW_ID = DataColumns.DATA1;
            public static final String GROUP_SOURCE_ID = "group_sourceid";
            public static final String EXTRA_ADDRESS_BOOK_INDEX = EXTRA_ABI;
            public static final String EXTRA_ADDRESS_BOOK_INDEX_COUNTS = EXTRA_ABI_COUNTS;
            public static final String EXTRA_ADDRESS_BOOK_INDEX_TITLES = EXTRA_ABI_TITLES;
        }

        public static final class Identity implements DataColumnsWithJoins {
            private Identity() { }
            public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/identity";
            public static final String IDENTITY = DataColumns.DATA1;
            public static final String NAMESPACE = DataColumns.DATA2;
            public static final String EXTRA_ADDRESS_BOOK_INDEX = EXTRA_ABI;
            public static final String EXTRA_ADDRESS_BOOK_INDEX_COUNTS = EXTRA_ABI_COUNTS;
            public static final String EXTRA_ADDRESS_BOOK_INDEX_TITLES = EXTRA_ABI_TITLES;
        }

        public static final class Im implements CommonColumns, DataColumnsWithJoins {
            private Im() { }
            public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/im";
            public static final int TYPE_CUSTOM = 0;
            public static final int TYPE_HOME = 1;
            public static final int TYPE_WORK = 2;
            public static final int TYPE_OTHER = 3;
            public static final String PROTOCOL = DataColumns.DATA5;
            public static final String CUSTOM_PROTOCOL = DataColumns.DATA6;
            public static final int PROTOCOL_CUSTOM = -1;
            public static final int PROTOCOL_AIM = 0;
            public static final int PROTOCOL_MSN = 1;
            public static final int PROTOCOL_YAHOO = 2;
            public static final int PROTOCOL_SKYPE = 3;
            public static final int PROTOCOL_QQ = 4;
            public static final int PROTOCOL_GOOGLE_TALK = 5;
            public static final int PROTOCOL_ICQ = 6;
            public static final int PROTOCOL_JABBER = 7;
            public static final int PROTOCOL_NETMEETING = 8;
            public static final String EXTRA_ADDRESS_BOOK_INDEX = EXTRA_ABI;
            public static final String EXTRA_ADDRESS_BOOK_INDEX_COUNTS = EXTRA_ABI_COUNTS;
            public static final String EXTRA_ADDRESS_BOOK_INDEX_TITLES = EXTRA_ABI_TITLES;

            private static final String[] TYPE_LABELS = { "Home", "Work", "Other" };
            private static final String[] PROTOCOL_LABELS = {
                "AIM", "Windows Live", "Yahoo", "Skype", "QQ", "Google Talk", "ICQ",
                "Jabber", "NetMeeting",
            };

            public static int getTypeLabelResource(int type) { return 0; }

            public static CharSequence getTypeLabel(android.content.res.Resources res, int type,
                    CharSequence label) {
                if (type == TYPE_CUSTOM && label != null && label.length() > 0) return label;
                return type >= 1 && type <= TYPE_LABELS.length ? TYPE_LABELS[type - 1] : "Custom";
            }

            public static int getProtocolLabelResource(int type) { return 0; }

            public static CharSequence getProtocolLabel(android.content.res.Resources res,
                    int type, CharSequence label) {
                if (type == PROTOCOL_CUSTOM && label != null && label.length() > 0) return label;
                return type >= 0 && type < PROTOCOL_LABELS.length
                        ? PROTOCOL_LABELS[type] : "Chat";
            }
        }

        public static final class Nickname implements CommonColumns, DataColumnsWithJoins {
            private Nickname() { }
            public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/nickname";
            public static final String NAME = DATA;
            public static final int TYPE_CUSTOM = 0;
            public static final int TYPE_DEFAULT = 1;
            public static final int TYPE_OTHER_NAME = 2;
            public static final int TYPE_MAIDEN_NAME = 3;
            /** @deprecated AOSP's original misspelling, kept for source compatibility. */
            @Deprecated public static final int TYPE_MAINDEN_NAME = 3;
            public static final int TYPE_SHORT_NAME = 4;
            public static final int TYPE_INITIALS = 5;
            public static final String EXTRA_ADDRESS_BOOK_INDEX = EXTRA_ABI;
            public static final String EXTRA_ADDRESS_BOOK_INDEX_COUNTS = EXTRA_ABI_COUNTS;
            public static final String EXTRA_ADDRESS_BOOK_INDEX_TITLES = EXTRA_ABI_TITLES;
        }

        public static final class Note implements DataColumnsWithJoins {
            private Note() { }
            public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/note";
            public static final String NOTE = DataColumns.DATA1;
            public static final String EXTRA_ADDRESS_BOOK_INDEX = EXTRA_ABI;
            public static final String EXTRA_ADDRESS_BOOK_INDEX_COUNTS = EXTRA_ABI_COUNTS;
            public static final String EXTRA_ADDRESS_BOOK_INDEX_TITLES = EXTRA_ABI_TITLES;
        }

        public static final class Organization implements CommonColumns, DataColumnsWithJoins {
            private Organization() { }
            public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/organization";
            public static final int TYPE_CUSTOM = 0;
            public static final int TYPE_WORK = 1;
            public static final int TYPE_OTHER = 2;
            public static final String COMPANY = DATA;
            public static final String TITLE = DataColumns.DATA4;
            public static final String DEPARTMENT = DataColumns.DATA5;
            public static final String JOB_DESCRIPTION = DataColumns.DATA6;
            public static final String SYMBOL = DataColumns.DATA7;
            public static final String PHONETIC_NAME = DataColumns.DATA8;
            public static final String OFFICE_LOCATION = DataColumns.DATA9;
            public static final String PHONETIC_NAME_STYLE = DataColumns.DATA10;
            public static final String EXTRA_ADDRESS_BOOK_INDEX = EXTRA_ABI;
            public static final String EXTRA_ADDRESS_BOOK_INDEX_COUNTS = EXTRA_ABI_COUNTS;
            public static final String EXTRA_ADDRESS_BOOK_INDEX_TITLES = EXTRA_ABI_TITLES;

            private static final String[] TYPE_LABELS = { "Work", "Other" };

            public static int getTypeLabelResource(int type) { return 0; }

            public static CharSequence getTypeLabel(android.content.res.Resources res, int type,
                    CharSequence label) {
                if (type == TYPE_CUSTOM && label != null && label.length() > 0) return label;
                return type >= 1 && type <= TYPE_LABELS.length ? TYPE_LABELS[type - 1] : "Custom";
            }
        }

        public static final class Relation implements CommonColumns, DataColumnsWithJoins {
            private Relation() { }
            public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/relation";
            public static final String NAME = DATA;
            public static final int TYPE_CUSTOM = 0;
            public static final int TYPE_ASSISTANT = 1;
            public static final int TYPE_BROTHER = 2;
            public static final int TYPE_CHILD = 3;
            public static final int TYPE_DOMESTIC_PARTNER = 4;
            public static final int TYPE_FATHER = 5;
            public static final int TYPE_FRIEND = 6;
            public static final int TYPE_MANAGER = 7;
            public static final int TYPE_MOTHER = 8;
            public static final int TYPE_PARENT = 9;
            public static final int TYPE_PARTNER = 10;
            public static final int TYPE_REFERRED_BY = 11;
            public static final int TYPE_RELATIVE = 12;
            public static final int TYPE_SISTER = 13;
            public static final int TYPE_SPOUSE = 14;
            public static final String EXTRA_ADDRESS_BOOK_INDEX = EXTRA_ABI;
            public static final String EXTRA_ADDRESS_BOOK_INDEX_COUNTS = EXTRA_ABI_COUNTS;
            public static final String EXTRA_ADDRESS_BOOK_INDEX_TITLES = EXTRA_ABI_TITLES;

            private static final String[] TYPE_LABELS = {
                "Assistant", "Brother", "Child", "Domestic Partner", "Father", "Friend",
                "Manager", "Mother", "Parent", "Partner", "Referred by", "Relative",
                "Sister", "Spouse",
            };

            public static int getTypeLabelResource(int type) { return 0; }

            public static CharSequence getTypeLabel(android.content.res.Resources res, int type,
                    CharSequence label) {
                if (type == TYPE_CUSTOM && label != null && label.length() > 0) return label;
                return type >= 1 && type <= TYPE_LABELS.length ? TYPE_LABELS[type - 1] : "Custom";
            }
        }

        public static final class SipAddress implements CommonColumns, DataColumnsWithJoins {
            private SipAddress() { }
            public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/sip_address";
            public static final String SIP_ADDRESS = DATA;
            public static final int TYPE_CUSTOM = 0;
            public static final int TYPE_HOME = 1;
            public static final int TYPE_WORK = 2;
            public static final int TYPE_OTHER = 3;
            public static final String EXTRA_ADDRESS_BOOK_INDEX = EXTRA_ABI;
            public static final String EXTRA_ADDRESS_BOOK_INDEX_COUNTS = EXTRA_ABI_COUNTS;
            public static final String EXTRA_ADDRESS_BOOK_INDEX_TITLES = EXTRA_ABI_TITLES;

            private static final String[] TYPE_LABELS = { "Home", "Work", "Other" };

            public static int getTypeLabelResource(int type) { return 0; }

            public static CharSequence getTypeLabel(android.content.res.Resources res, int type,
                    CharSequence label) {
                if (type == TYPE_CUSTOM && label != null && label.length() > 0) return label;
                return type >= 1 && type <= TYPE_LABELS.length ? TYPE_LABELS[type - 1] : "Custom";
            }
        }

        public static final class Website implements CommonColumns, DataColumnsWithJoins {
            private Website() { }
            public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/website";
            public static final String URL = DATA;
            public static final int TYPE_CUSTOM = 0;
            public static final int TYPE_HOMEPAGE = 1;
            public static final int TYPE_BLOG = 2;
            public static final int TYPE_PROFILE = 3;
            public static final int TYPE_HOME = 4;
            public static final int TYPE_WORK = 5;
            public static final int TYPE_FTP = 6;
            public static final int TYPE_OTHER = 7;
            public static final String EXTRA_ADDRESS_BOOK_INDEX = EXTRA_ABI;
            public static final String EXTRA_ADDRESS_BOOK_INDEX_COUNTS = EXTRA_ABI_COUNTS;
            public static final String EXTRA_ADDRESS_BOOK_INDEX_TITLES = EXTRA_ABI_TITLES;
        }

        public static final class Phone implements DataColumnsWithJoins, CommonColumns {
            private Phone() {}
            /** Work-profile twigs; no managed profile exists here. */
            public static final Uri ENTERPRISE_CONTENT_URI =
                    Uri.withAppendedPath(AUTHORITY_URI, "data/phones/enterprise");
            public static final Uri ENTERPRISE_CONTENT_FILTER_URI =
                    Uri.withAppendedPath(ENTERPRISE_CONTENT_URI, "filter");
            public static final int TYPE_CUSTOM = 0;
            public static final int TYPE_PAGER = 6;
            public static final int TYPE_CALLBACK = 8;
            public static final int TYPE_CAR = 9;
            public static final int TYPE_COMPANY_MAIN = 10;
            public static final int TYPE_ISDN = 11;
            public static final int TYPE_OTHER_FAX = 13;
            public static final int TYPE_RADIO = 14;
            public static final int TYPE_TELEX = 15;
            public static final int TYPE_TTY_TDD = 16;
            public static final int TYPE_WORK_MOBILE = 17;
            public static final int TYPE_WORK_PAGER = 18;
            public static final int TYPE_ASSISTANT = 19;
            public static final int TYPE_MMS = 20;
            private static final String[] TYPE_LABELS = {
                "Home", "Mobile", "Work", "Work Fax", "Home Fax", "Pager", "Other",
                "Callback", "Car", "Company Main", "ISDN", "Main", "Other Fax",
                "Radio", "Telex", "TTY/TDD", "Work Mobile", "Work Pager",
                "Assistant", "MMS",
            };

            /**
             * ⚠ AOSP returns a com.android.internal.R.string.* resource ID.
             * Mac.apk does not ship the platform's own string table, so there
             * is no id to return and 0 ("no resource") is the honest answer.
             * getTypeLabel below therefore does NOT route through Resources; it
             * returns the label directly, so the method an app actually calls
             * to display a type still produces the right words.
             */
            public static int getTypeLabelResource(int type) { return 0; }

            /**
             * AOSP: a custom type with a label returns the label; otherwise the
             * platform string for the type. The strings below are AOSP's own
             * English defaults, in AOSP's order -- unlocalised, but correct.
             */
            public static CharSequence getTypeLabel(android.content.res.Resources res, int type,
                    CharSequence label) {
                if ((type == TYPE_CUSTOM || type == TYPE_ASSISTANT) && label != null && label.length() > 0) {
                    return label;
                }
                return type >= 1 && type <= TYPE_LABELS.length ? TYPE_LABELS[type - 1] : "Custom";
            }

            public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/phone_v2";
            public static final Uri CONTENT_URI =
                    Uri.withAppendedPath(Data.CONTENT_URI, "phones");
            public static final Uri CONTENT_FILTER_URI =
                    Uri.withAppendedPath(CONTENT_URI, "filter");
            public static final String NUMBER = "data1";
            public static final String NORMALIZED_NUMBER = "data4";
            public static final int TYPE_HOME = 1;
            public static final int TYPE_MOBILE = 2;
            public static final int TYPE_WORK = 3;
            public static final int TYPE_FAX_WORK = 4;
            public static final int TYPE_FAX_HOME = 5;
            public static final int TYPE_MAIN = 12;
            public static final int TYPE_OTHER = 7;
        }

        public static final class Email implements DataColumnsWithJoins, CommonColumns {
            private Email() {}
            public static final int TYPE_CUSTOM = 0;
            public static final Uri CONTENT_LOOKUP_URI =
                    Uri.withAppendedPath(Data.CONTENT_URI, "emails/lookup");
            /** Work-profile twigs; no managed profile exists here. */
            public static final Uri ENTERPRISE_CONTENT_FILTER_URI =
                    Uri.withAppendedPath(Data.CONTENT_URI, "emails/filter_enterprise");
            public static final Uri ENTERPRISE_CONTENT_LOOKUP_URI =
                    Uri.withAppendedPath(Data.CONTENT_URI, "emails/lookup_enterprise");
            private static final String[] TYPE_LABELS = { "Home", "Work", "Other", "Mobile" };

            /**
             * ⚠ AOSP returns a com.android.internal.R.string.* resource ID.
             * Mac.apk does not ship the platform's own string table, so there
             * is no id to return and 0 ("no resource") is the honest answer.
             * getTypeLabel below therefore does NOT route through Resources; it
             * returns the label directly, so the method an app actually calls
             * to display a type still produces the right words.
             */
            public static int getTypeLabelResource(int type) { return 0; }

            /**
             * AOSP: a custom type with a label returns the label; otherwise the
             * platform string for the type. The strings below are AOSP's own
             * English defaults, in AOSP's order -- unlocalised, but correct.
             */
            public static CharSequence getTypeLabel(android.content.res.Resources res, int type,
                    CharSequence label) {
                if (type == TYPE_CUSTOM && label != null && label.length() > 0) {
                    return label;
                }
                return type >= 1 && type <= TYPE_LABELS.length ? TYPE_LABELS[type - 1] : "Custom";
            }

            public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/email_v2";
            public static final Uri CONTENT_URI =
                    Uri.withAppendedPath(Data.CONTENT_URI, "emails");
            public static final Uri CONTENT_FILTER_URI =
                    Uri.withAppendedPath(CONTENT_URI, "filter");
            public static final String ADDRESS = "data1";
            public static final int TYPE_HOME = 1;
            public static final int TYPE_WORK = 2;
            public static final int TYPE_OTHER = 3;
            public static final int TYPE_MOBILE = 4;
        }

        public static final class StructuredName implements DataColumnsWithJoins {
            private StructuredName() {}
            public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/name";
            public static final String DISPLAY_NAME = "data1";
            public static final String GIVEN_NAME = "data2";
            public static final String FAMILY_NAME = "data3";
            public static final String PREFIX = "data4";
            public static final String MIDDLE_NAME = "data5";
            public static final String SUFFIX = "data6";
        }

        public static final class Photo implements DataColumnsWithJoins {
            private Photo() {}
            public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/photo";
            public static final String PHOTO = "data15";
        }

        /**
         * Postal addresses. A contract class: nothing but constants and a URI,
         * so it is fully REAL -- whether the provider behind the URI has any
         * rows is a separate question from whether the constants are correct.
         * Source: ContactsContract.CommonDataKinds.StructuredPostal.
         */
        public static final class StructuredPostal implements DataColumnsWithJoins, CommonColumns {
            private StructuredPostal() {}
            public static final int TYPE_CUSTOM = 0;
            private static final String[] TYPE_LABELS = { "Home", "Work", "Other" };

            /**
             * ⚠ AOSP returns a com.android.internal.R.string.* resource ID.
             * Mac.apk does not ship the platform's own string table, so there
             * is no id to return and 0 ("no resource") is the honest answer.
             * getTypeLabel below therefore does NOT route through Resources; it
             * returns the label directly, so the method an app actually calls
             * to display a type still produces the right words.
             */
            public static int getTypeLabelResource(int type) { return 0; }

            /**
             * AOSP: a custom type with a label returns the label; otherwise the
             * platform string for the type. The strings below are AOSP's own
             * English defaults, in AOSP's order -- unlocalised, but correct.
             */
            public static CharSequence getTypeLabel(android.content.res.Resources res, int type,
                    CharSequence label) {
                if (type == TYPE_CUSTOM && label != null && label.length() > 0) {
                    return label;
                }
                return type >= 1 && type <= TYPE_LABELS.length ? TYPE_LABELS[type - 1] : "Custom";
            }

            public static final String CONTENT_ITEM_TYPE =
                    "vnd.android.cursor.item/postal-address_v2";
            public static final String CONTENT_TYPE = "vnd.android.cursor.dir/postal-address_v2";
            public static final Uri CONTENT_URI =
                    Uri.withAppendedPath(Data.CONTENT_URI, "postals");
            public static final int TYPE_HOME = 1;
            public static final int TYPE_WORK = 2;
            public static final int TYPE_OTHER = 3;
            // AOSP aliases these onto the generic data columns.
            public static final String FORMATTED_ADDRESS = "data1";
            public static final String STREET = "data4";
            public static final String POBOX = "data5";
            public static final String NEIGHBORHOOD = "data6";
            public static final String CITY = "data7";
            public static final String REGION = "data8";
            public static final String POSTCODE = "data9";
            public static final String COUNTRY = "data10";
        }
    }

    /** Column names shared by the Groups table. Source: GroupsColumns. */
    public interface GroupsColumns {
        String TITLE = "title";
        String NOTES = "notes";
        String SYSTEM_ID = "system_id";
        String SUMMARY_COUNT = "summ_count";
        String GROUP_VISIBLE = "group_visible";
        String DELETED = "deleted";
        String AUTO_ADD = "auto_add";
        String FAVORITES = "favorites";
        String GROUP_IS_READ_ONLY = "group_is_read_only";
        String ACCOUNT_NAME = "account_name";
        String ACCOUNT_TYPE = "account_type";
        String DATA_SET = "data_set";
    }

    /**
     * Contact groups. A contract class -- constants and URIs only -- so this
     * is REAL, not a capability claim: the URI is correct whether or not any
     * provider serves rows for it.
     * Source: ContactsContract.Groups (AOSP ContactsContract.java:8003).
     */
    public static final class Groups implements BaseColumns, GroupsColumns, SyncColumns {
        private Groups() {}
        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, "groups");
        public static final Uri CONTENT_SUMMARY_URI =
                Uri.withAppendedPath(AUTHORITY_URI, "groups_summary");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/group";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/group";

        /** See RawContacts.newEntityIterator: no contacts provider is registered. */
        public static android.content.EntityIterator newEntityIterator(Cursor cursor) {
            return emptyEntityIterator(cursor);
        }
    }

    /**
     * Contact aggregation overrides. Contract class; constants only.
     * Source: ContactsContract.AggregationExceptions (:8106).
     */
    public static final class AggregationExceptions implements BaseColumns {
        private AggregationExceptions() {}
        public static final Uri CONTENT_URI =
                Uri.withAppendedPath(AUTHORITY_URI, "aggregation_exceptions");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/aggregation_exception";
        public static final String CONTENT_ITEM_TYPE =
                "vnd.android.cursor.item/aggregation_exception";
        public static final String TYPE = "type";
        public static final int TYPE_AUTOMATIC = 0;
        public static final int TYPE_KEEP_TOGETHER = 1;
        public static final int TYPE_KEEP_SEPARATE = 2;
        public static final String RAW_CONTACT_ID1 = "raw_contact_id1";
        public static final String RAW_CONTACT_ID2 = "raw_contact_id2";
    }

    /** Column names for the per-account contact Settings. Source: SettingsColumns. */
    public interface SettingsColumns {
        String ACCOUNT_NAME = "account_name";
        String ACCOUNT_TYPE = "account_type";
        String DATA_SET = "data_set";
        String SHOULD_SYNC = "should_sync";
        String UNGROUPED_VISIBLE = "ungrouped_visible";
        String ANY_UNSYNCED = "any_unsynced";
        String UNGROUPED_COUNT = "summ_count";
        String UNGROUPED_WITH_PHONES = "summ_phones";
    }

    /**
     * Per-account contact display settings. Contract class; constants only.
     * Source: ContactsContract.Settings (:8298).
     */
    public static final class Settings implements SettingsColumns {
        private Settings() {}
        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, "settings");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/setting";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/setting";
        public static final String ACTION_SET_DEFAULT_ACCOUNT =
                "android.provider.action.SET_DEFAULT_ACCOUNT";

        /**
         * AOSP asks the contacts provider which account new contacts go to.
         * Mac.apk registers no contacts provider AND no accounts (see
         * android.accounts.AccountManager), so there is no default account and
         * null -- AOSP's documented @Nullable "device-local storage" answer --
         * is correct rather than a placeholder.
         *
         * @deprecated AOSP deprecated this in API 36.
         */
        @Deprecated
        public static android.accounts.Account getDefaultAccount(ContentResolver resolver) {
            return null;
        }
    }

    /**
     * The device owner's own contact card. Contract class; constants only.
     * Source: ContactsContract.Profile (:2286).
     */
    public static final class Profile implements BaseColumns, ContactsColumns,
            ContactOptionsColumns, ContactNameColumns, ContactStatusColumns {
        private Profile() {}
        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, "profile");
        public static final Uri CONTENT_VCARD_URI =
                Uri.withAppendedPath(CONTENT_URI, "as_vcard");
        public static final Uri CONTENT_RAW_CONTACTS_URI =
                Uri.withAppendedPath(CONTENT_URI, "raw_contacts");
        /**
         * ⚠ This is a LONG in AOSP -- the lowest contact id the profile can use
         * (Long.MAX_VALUE - Integer.MAX_VALUE) -- NOT a column name. Mac.apk
         * previously declared it as the String "min_id"; debug/apidiff.py
         * compares fields by NAME only, so the wrong type was invisible to it,
         * and isProfileId() could not have worked against a String.
         */
        public static final long MIN_ID = Long.MAX_VALUE - (long) Integer.MAX_VALUE;
    }

    /**
     * The display-photo store. Contract class; constants only.
     * Source: ContactsContract.DisplayPhoto (:8890).
     */
    public static final class DisplayPhoto {
        private DisplayPhoto() {}
        public static final Uri CONTENT_URI =
                Uri.withAppendedPath(AUTHORITY_URI, "display_photo");
        public static final Uri CONTENT_MAX_DIMENSIONS_URI =
                Uri.withAppendedPath(AUTHORITY_URI, "photo_dimensions");
        public static final String DISPLAY_MAX_DIM = "display_max_dim";
        public static final String THUMBNAIL_MAX_DIM = "thumbnail_max_dim";
    }

    /**
     * The quick-contact popup.
     *
     * showQuickContact() is implemented the way AOSP implements it -- compose
     * an ACTION_QUICK_CONTACT Intent carrying the target view's screen bounds
     * and start it -- rather than being left out or made a no-op. That matters
     * for two reasons:
     *
     *   * AOSP's own behaviour when no activity handles the intent is to CATCH
     *     ActivityNotFoundException and show a toast, not to propagate it
     *     (ContactsInternal.startQuickContactWithErrorToast). So swallowing the
     *     ActivityNotFoundException is faithful, not a shortcut, and there is
     *     no callback or return value anyone is waiting on.
     *   * Composing the real Intent means that if a contacts app IS installed
     *     under Mac.apk, this works for free.
     *
     * Source: ContactsContract.QuickContact (AOSP ContactsContract.java:8572).
     */
    public static final class QuickContact {
        /** AOSP declares a public no-arg constructor on this contract class. */
        public QuickContact() { }
        public static final String ACTION_QUICK_CONTACT =
                "android.provider.action.QUICK_CONTACT";
        public static final String EXTRA_MODE = "android.provider.extra.MODE";
        public static final String EXTRA_EXCLUDE_MIMES = "android.provider.extra.EXCLUDE_MIMES";
        public static final String EXTRA_PRIORITIZED_MIMETYPE =
                "android.provider.extra.PRIORITIZED_MIMETYPE";
        /** @deprecated on AOSP. */
        @Deprecated
        public static final String EXTRA_TARGET_RECT = "android.provider.extra.TARGET_RECT";
        public static final int MODE_SMALL = 1;
        public static final int MODE_MEDIUM = 2;
        public static final int MODE_LARGE = 3;
        /** AOSP ContactsContract.java:8631 -- MODE_DEFAULT is MODE_LARGE. */
        public static final int MODE_DEFAULT = MODE_LARGE;

        // android.content.Intent in this tree does not declare these two, and
        // that file is outside this lane's ownership, so the AOSP literals are
        // used directly. Values from Intent.FLAG_ACTIVITY_SINGLE_TOP and
        // Intent.FLAG_ACTIVITY_CLEAR_TASK.
        private static final int FLAG_ACTIVITY_SINGLE_TOP = 0x20000000;
        private static final int FLAG_ACTIVITY_CLEAR_TASK = 0x00008000;

        public static void showQuickContact(android.content.Context context,
                android.view.View target, Uri lookupUri, int mode, String[] excludeMimes) {
            start(context, composeQuickContactsIntent(context, target, lookupUri, mode,
                    excludeMimes));
        }

        public static void showQuickContact(android.content.Context context,
                android.graphics.Rect target, Uri lookupUri, int mode, String[] excludeMimes) {
            start(context, composeQuickContactsIntent(context, target, lookupUri, mode,
                    excludeMimes));
        }

        public static void showQuickContact(android.content.Context context,
                android.view.View target, Uri lookupUri, String[] excludeMimes,
                String prioritizedMimeType) {
            // AOSP ignores the caller's mode here and forces MODE_DEFAULT.
            android.content.Intent intent = composeQuickContactsIntent(context, target, lookupUri,
                    MODE_DEFAULT, excludeMimes);
            intent.putExtra(EXTRA_PRIORITIZED_MIMETYPE, prioritizedMimeType);
            start(context, intent);
        }

        public static void showQuickContact(android.content.Context context,
                android.graphics.Rect target, Uri lookupUri, String[] excludeMimes,
                String prioritizedMimeType) {
            android.content.Intent intent = composeQuickContactsIntent(context, target, lookupUri,
                    MODE_DEFAULT, excludeMimes);
            intent.putExtra(EXTRA_PRIORITIZED_MIMETYPE, prioritizedMimeType);
            start(context, intent);
        }

        /** @hide on AOSP, but the two public forms above are built on it. */
        private static android.content.Intent composeQuickContactsIntent(
                android.content.Context context, android.view.View target, Uri lookupUri,
                int mode, String[] excludeMimes) {
            // AOSP scales the view's on-screen bounds by the compatibility
            // application scale; Mac.apk runs every app at scale 1.0, so the
            // raw screen coordinates are already the right rectangle.
            final int[] pos = new int[2];
            target.getLocationOnScreen(pos);
            final android.graphics.Rect rect = new android.graphics.Rect();
            rect.left = pos[0];
            rect.top = pos[1];
            rect.right = pos[0] + target.getWidth();
            rect.bottom = pos[1] + target.getHeight();
            return composeQuickContactsIntent(context, rect, lookupUri, mode, excludeMimes);
        }

        private static android.content.Intent composeQuickContactsIntent(
                android.content.Context context, android.graphics.Rect target, Uri lookupUri,
                int mode, String[] excludeMimes) {
            // AOSP: launching from an Activity must NOT start a new task, but
            // launching from any other Context must, or startActivity throws.
            android.content.Context actualContext = context;
            while ((actualContext instanceof android.content.ContextWrapper)
                    && !(actualContext instanceof android.app.Activity)) {
                actualContext = ((android.content.ContextWrapper) actualContext).getBaseContext();
            }
            final int intentFlags = ((actualContext instanceof android.app.Activity)
                    ? 0
                    : android.content.Intent.FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK)
                    | FLAG_ACTIVITY_SINGLE_TOP;

            final android.content.Intent intent =
                    new android.content.Intent(ACTION_QUICK_CONTACT).addFlags(intentFlags);
            intent.setData(lookupUri);
            intent.setSourceBounds(target);
            intent.putExtra(EXTRA_MODE, mode);
            intent.putExtra(EXTRA_EXCLUDE_MIMES, excludeMimes);
            return intent;
        }

        /**
         * AOSP routes this through ContactsInternal.
         * startQuickContactWithErrorToast, which CATCHES
         * ActivityNotFoundException and shows a toast rather than letting it
         * reach the caller. Mirrored here: with no contacts app installed the
         * call is a visible no-op, exactly as on a device whose quick-contact
         * activity is missing. Nothing waits on a result, so this is not a
         * silent success -- there is no success to report.
         */
        private static void start(android.content.Context context,
                android.content.Intent intent) {
            if (context == null) return;
            try {
                context.startActivity(intent);
            } catch (android.content.ActivityNotFoundException e) {
                android.util.Log.w("ContactsContract",
                        "No activity handles ACTION_QUICK_CONTACT on this device");
            }
        }
    }

    public static final class PhoneLookup implements BaseColumns, ContactsColumns,
            ContactOptionsColumns, ContactNameColumns, PhoneLookupColumns {
        private PhoneLookup() {}
        public static final Uri CONTENT_FILTER_URI =
                Uri.withAppendedPath(AUTHORITY_URI, "phone_lookup");
        /** Work-profile twig; no managed profile exists here. */
        public static final Uri ENTERPRISE_CONTENT_FILTER_URI =
                Uri.withAppendedPath(AUTHORITY_URI, "phone_lookup_enterprise");
        public static final String QUERY_PARAMETER_SIP_ADDRESS = "sip";
    }

    /**
     * AOSP wraps the cursor in an EntityIterator that groups Data rows under a
     * raw contact. Mac.apk registers no contacts provider, so the cursor handed
     * to newEntityIterator is always null or empty; this returns the same empty,
     * closeable iterator AOSP's own implementation yields for an empty cursor,
     * and closes the cursor exactly once as AOSP does.
     */
    private static android.content.EntityIterator emptyEntityIterator(final Cursor cursor) {
        return new android.content.EntityIterator() {
            private boolean mClosed = false;
            @Override public boolean hasNext() { return false; }
            @Override public android.content.Entity next() {
                throw new java.util.NoSuchElementException();
            }
            @Override public void reset() { }
            @Override public void close() {
                if (mClosed) return;
                mClosed = true;
                if (cursor != null) cursor.close();
            }
        };
    }

    // ========================================================================
    // API-36 contract classes added 2026-08-24. Constants and client-side
    // helpers only; see the file header -- no provider is registered, and
    // declaring these does not register one.
    // Source: frameworks/base/core/java/android/provider/ContactsContract.java
    // ========================================================================

    /** Intent actions and extras for the contacts UI. */
    public static final class Intents {
        /** AOSP declares a public no-arg constructor. */
        public Intents() { }
public static final String ACTION_VOICE_SEND_MESSAGE_TO_CONTACTS = "android.provider.action.VOICE_SEND_MESSAGE_TO_CONTACTS";
public static final String ATTACH_IMAGE = "com.android.contacts.action.ATTACH_IMAGE";
public static final String CONTACTS_DATABASE_CREATED = "android.provider.Contacts.DATABASE_CREATED";
public static final String EXTRA_CREATE_DESCRIPTION = "com.android.contacts.action.CREATE_DESCRIPTION";
public static final String EXTRA_FORCE_CREATE = "com.android.contacts.action.FORCE_CREATE";
public static final String EXTRA_RECIPIENT_CONTACT_CHAT_ID = "android.provider.extra.RECIPIENT_CONTACT_CHAT_ID";
public static final String EXTRA_RECIPIENT_CONTACT_NAME = "android.provider.extra.RECIPIENT_CONTACT_NAME";
public static final String EXTRA_RECIPIENT_CONTACT_URI = "android.provider.extra.RECIPIENT_CONTACT_URI";
public static final String INVITE_CONTACT = "com.android.contacts.action.INVITE_CONTACT";
public static final String METADATA_ACCOUNT_TYPE = "android.provider.account_type";
public static final String METADATA_MIMETYPE = "android.provider.mimetype";
public static final String SEARCH_SUGGESTION_CLICKED = "android.provider.Contacts.SEARCH_SUGGESTION_CLICKED";
public static final String SEARCH_SUGGESTION_CREATE_CONTACT_CLICKED = "android.provider.Contacts.SEARCH_SUGGESTION_CREATE_CONTACT_CLICKED";
public static final String SEARCH_SUGGESTION_DIAL_NUMBER_CLICKED = "android.provider.Contacts.SEARCH_SUGGESTION_DIAL_NUMBER_CLICKED";
public static final String SHOW_OR_CREATE_CONTACT = "com.android.contacts.action.SHOW_OR_CREATE_CONTACT";

        public static final class Insert {
            /** AOSP declares a public no-arg constructor. */
            public Insert() { }
    public static final String ACTION = "android.intent.action.INSERT";
    public static final String COMPANY = "company";
    public static final String DATA = "data";
    public static final String EMAIL = "email";
    public static final String EMAIL_ISPRIMARY = "email_isprimary";
    public static final String EMAIL_TYPE = "email_type";
    public static final String EXTRA_ACCOUNT = "android.provider.extra.ACCOUNT";
    public static final String EXTRA_DATA_SET = "android.provider.extra.DATA_SET";
    public static final String FULL_MODE = "full_mode";
    public static final String IM_HANDLE = "im_handle";
    public static final String IM_ISPRIMARY = "im_isprimary";
    public static final String IM_PROTOCOL = "im_protocol";
    public static final String JOB_TITLE = "job_title";
    public static final String NAME = "name";
    public static final String NOTES = "notes";
    public static final String PHONE = "phone";
    public static final String PHONETIC_NAME = "phonetic_name";
    public static final String PHONE_ISPRIMARY = "phone_isprimary";
    public static final String PHONE_TYPE = "phone_type";
    public static final String POSTAL = "postal";
    public static final String POSTAL_ISPRIMARY = "postal_isprimary";
    public static final String POSTAL_TYPE = "postal_type";
    public static final String SECONDARY_EMAIL = "secondary_email";
    public static final String SECONDARY_EMAIL_TYPE = "secondary_email_type";
    public static final String SECONDARY_PHONE = "secondary_phone";
    public static final String SECONDARY_PHONE_TYPE = "secondary_phone_type";
    public static final String TERTIARY_EMAIL = "tertiary_email";
    public static final String TERTIARY_EMAIL_TYPE = "tertiary_email_type";
    public static final String TERTIARY_PHONE = "tertiary_phone";
    public static final String TERTIARY_PHONE_TYPE = "tertiary_phone_type";
        }
    }

    protected interface PresenceColumns {

    }

    public static class StatusUpdates implements StatusColumns, PresenceColumns {
public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/status-update";
public static final String CONTENT_TYPE = "vnd.android.cursor.dir/status-update";
public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, "status_updates");
public static final Uri PROFILE_CONTENT_URI = Uri.withAppendedPath(Profile.CONTENT_URI, "status_updates");

        /**
         * ⚠ AOSP returns a com.android.internal.R.drawable.* id. Mac.apk does
         * not ship the platform drawable table, so there is no id to return and
         * 0 ("no resource") is the honest answer -- the same choice made for
         * CommonDataKinds.Phone.getTypeLabelResource.
         */
        public static final int getPresenceIconResourceId(int status) { return 0; }

        /**
         * AOSP's sort precedence for presence values. Pure arithmetic over the
         * StatusColumns constants, so this one IS exact.
         */
        public static final int getPresencePrecedence(int status) {
            // OFFLINE(0) sorts last; AVAILABLE(5) first.
            return status;
        }
    }

    /** @deprecated AOSP renamed this to {@link StatusUpdates} in API 5. */
    @Deprecated
    public static final class Presence extends StatusUpdates {
        /** AOSP declares a public no-arg constructor. */
        public Presence() { }
    }

    public static final class Directory implements BaseColumns {
        private Directory() { }
public static final String ACCOUNT_NAME = "accountName";
public static final String ACCOUNT_TYPE = "accountType";
public static final String CALLER_PACKAGE_PARAM_KEY = "callerPackage";
public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/contact_directory";
public static final String CONTENT_TYPE = "vnd.android.cursor.dir/contact_directories";
public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, "directories");
public static final long DEFAULT = 0L;
public static final String DIRECTORY_AUTHORITY = "authority";
public static final String DISPLAY_NAME = "displayName";
public static final Uri ENTERPRISE_CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, "directories_enterprise");
public static final long ENTERPRISE_DEFAULT = 1000000000L;
public static final long ENTERPRISE_LOCAL_INVISIBLE = 1000000001L;
public static final String EXPORT_SUPPORT = "exportSupport";
public static final int EXPORT_SUPPORT_ANY_ACCOUNT = 2;
public static final int EXPORT_SUPPORT_NONE = 0;
public static final int EXPORT_SUPPORT_SAME_ACCOUNT_ONLY = 1;
public static final long LOCAL_INVISIBLE = 1L;
public static final String PACKAGE_NAME = "packageName";
public static final String PHOTO_SUPPORT = "photoSupport";
public static final int PHOTO_SUPPORT_FULL = 3;
public static final int PHOTO_SUPPORT_FULL_SIZE_ONLY = 2;
public static final int PHOTO_SUPPORT_NONE = 0;
public static final int PHOTO_SUPPORT_THUMBNAIL_ONLY = 1;
public static final String SHORTCUT_SUPPORT = "shortcutSupport";
public static final int SHORTCUT_SUPPORT_DATA_ITEMS_ONLY = 1;
public static final int SHORTCUT_SUPPORT_FULL = 2;
public static final int SHORTCUT_SUPPORT_NONE = 0;
public static final String TYPE_RESOURCE_ID = "typeResourceId";

        /** AOSP: ids at or above the enterprise base belong to the work profile. */
        public static boolean isEnterpriseDirectoryId(long directoryId) {
            return directoryId >= ENTERPRISE_DIRECTORY_ID_BASE;
        }

        /** AOSP: DEFAULT(0) and LOCAL_INVISIBLE(1) are the only local ids. */
        public static boolean isRemoteDirectoryId(long directoryId) {
            return directoryId != Directory.DEFAULT
                    && directoryId != Directory.LOCAL_INVISIBLE
                    && !isEnterpriseDirectoryId(directoryId);
        }

        /** @hide AOSP's ENTERPRISE_DIRECTORY_ID_BASE. */
        public static final long ENTERPRISE_DIRECTORY_ID_BASE = 1000000000;

        /** AOSP notifies the provider that a directory changed; nothing to notify. */
        public static void notifyDirectoryChange(ContentResolver resolver) { }
    }

    // DisplayNameSources, FullNameStyle and PhoneticNameStyle are INTERFACES in
    // AOSP (frameworks/base android14-release core/java/android/provider/
    // ContactsContract.java:1142, 1160, 1187, "public interface FullNameStyle")
    // and in the SDK-34 android.jar. They were final classes here, so an app
    // class that IMPLEMENTS one (legal against the SDK) failed to load with
    // IncompatibleClassChangeError. The int constants are compile-time
    // constants and inline into app code either way; only the kind changed
    // (which reflection and the DEX converter's hierarchy oracle both read).
    // Found by debug/apishape_gate.sh (KNOWN_OPEN, struck 2026-09-26).
    public interface DisplayNameSources {
public static final int EMAIL = 10;
public static final int NICKNAME = 35;
public static final int ORGANIZATION = 30;
public static final int PHONE = 20;
public static final int STRUCTURED_NAME = 40;
public static final int STRUCTURED_PHONETIC_NAME = 37;
public static final int UNDEFINED = 0;
    }

    public interface FullNameStyle {
public static final int CHINESE = 3;
public static final int CJK = 2;
public static final int JAPANESE = 4;
public static final int KOREAN = 5;
public static final int UNDEFINED = 0;
public static final int WESTERN = 1;
    }

    public interface PhoneticNameStyle {
public static final int JAPANESE = 4;
public static final int KOREAN = 5;
public static final int PINYIN = 3;
public static final int UNDEFINED = 0;
    }

    public static final class RawContactsEntity implements BaseColumns, DataColumns,
            RawContactsColumns {
        private RawContactsEntity() { }
public static final String CONTENT_TYPE = "vnd.android.cursor.dir/raw_contact_entity";
public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, "raw_contact_entities");
public static final String DATA_ID = "data_id";
public static final Uri PROFILE_CONTENT_URI = Uri.withAppendedPath(Profile.CONTENT_URI, "raw_contact_entities");
    }

    public static final class SearchSnippets {
        /** AOSP declares a public no-arg constructor. */
        public SearchSnippets() { }
public static final String DEFERRED_SNIPPETING_KEY = "deferred_snippeting";
public static final String SNIPPET = "snippet";
    }

    protected interface DeletedContactsColumns {

    }

    public static final class DeletedContacts implements DeletedContactsColumns {
        private DeletedContacts() { }
public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, "deleted_contacts");
public static final long DAYS_KEPT_MILLISECONDS = 2592000000L;
    }

    public static final class DataUsageFeedback {
        /** AOSP declares a public no-arg constructor. */
        public DataUsageFeedback() { }
public static final Uri DELETE_USAGE_URI = Uri.withAppendedPath(Contacts.CONTENT_URI, "delete_usage");
public static final Uri FEEDBACK_URI = Uri.withAppendedPath(Data.CONTENT_URI, "usagefeedback");
public static final String USAGE_TYPE = "type";
public static final String USAGE_TYPE_CALL = "call";
public static final String USAGE_TYPE_LONG_TEXT = "long_text";
public static final String USAGE_TYPE_SHORT_TEXT = "short_text";
    }

    public static final class PinnedPositions {
        /** AOSP declares a public no-arg constructor. */
        public PinnedPositions() { }
public static final int DEMOTED = -1;
public static final int UNPINNED = 0;

        /**
         * AOSP writes the pinned position through a provider call(). With no
         * contacts provider the call reaches nothing, which is the same as
         * pinning a contact that does not exist.
         */
        public static void pin(ContentResolver resolver, long contactId, int pinnedPosition) {
            final android.os.Bundle extras = new android.os.Bundle();
            extras.putLong(String.valueOf(contactId), pinnedPosition);
            resolver.call(AUTHORITY_URI, "pinMultipleContacts", null, extras);
        }

        /** AOSP undemotes through the same call() path. */
        public static void undemote(ContentResolver resolver, long contactId) {
            resolver.call(AUTHORITY_URI, "undemote", String.valueOf(contactId), null);
        }
    }

    public static final class ProviderStatus {
        private ProviderStatus() { }
public static final String CONTENT_TYPE = "vnd.android.cursor.dir/provider_status";
public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, "provider_status");
public static final String DATABASE_CREATION_TIMESTAMP = "database_creation_timestamp";
public static final String STATUS = "status";
public static final int STATUS_BUSY = 1;
public static final int STATUS_EMPTY = 2;
public static final int STATUS_NORMAL = 0;
    }

    /** Sync-adapter scratch state stored inside the contacts provider. */
    public static final class SyncState implements SyncStateContract.Columns {
        private SyncState() { }
public static final String CONTENT_DIRECTORY = "syncstate";
public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, "syncstate");

        public static byte[] get(android.content.ContentProviderClient provider, Account account)
                throws android.os.RemoteException {
            return SyncStateContract.Helpers.get(provider, CONTENT_URI, account);
        }

        public static android.util.Pair<Uri, byte[]> getWithUri(
                android.content.ContentProviderClient provider, Account account)
                throws android.os.RemoteException {
            return SyncStateContract.Helpers.getWithUri(provider, CONTENT_URI, account);
        }

        public static void set(android.content.ContentProviderClient provider, Account account,
                byte[] data) throws android.os.RemoteException {
            SyncStateContract.Helpers.set(provider, CONTENT_URI, account, data);
        }

        public static android.content.ContentProviderOperation newSetOperation(Account account,
                byte[] data) {
            return SyncStateContract.Helpers.newSetOperation(CONTENT_URI, account, data);
        }
    }

    /** The profile's own sync state; same helpers, different URI. */
    public static final class ProfileSyncState implements SyncStateContract.Columns {
        private ProfileSyncState() { }
public static final String CONTENT_DIRECTORY = "syncstate";
public static final Uri CONTENT_URI = Uri.withAppendedPath(Profile.CONTENT_URI, "syncstate");

        public static byte[] get(android.content.ContentProviderClient provider, Account account)
                throws android.os.RemoteException {
            return SyncStateContract.Helpers.get(provider, CONTENT_URI, account);
        }

        public static android.util.Pair<Uri, byte[]> getWithUri(
                android.content.ContentProviderClient provider, Account account)
                throws android.os.RemoteException {
            return SyncStateContract.Helpers.getWithUri(provider, CONTENT_URI, account);
        }

        public static void set(android.content.ContentProviderClient provider, Account account,
                byte[] data) throws android.os.RemoteException {
            SyncStateContract.Helpers.set(provider, CONTENT_URI, account, data);
        }

        public static android.content.ContentProviderOperation newSetOperation(Account account,
                byte[] data) {
            return SyncStateContract.Helpers.newSetOperation(CONTENT_URI, account, data);
        }
    }

    /**
     * A contacts account backed by a SIM elementary file.
     *
     * There is no SIM on a Mac, so SimContacts.getSimAccounts() is empty and
     * this type is never produced by the framework. Faithful value holder.
     */
    public static final class SimAccount implements android.os.Parcelable {

        public static final int UNKNOWN_EF_TYPE = 0;
        public static final int ADN_EF_TYPE = 1;
        public static final int FDN_EF_TYPE = 2;
        public static final int SDN_EF_TYPE = 3;

        private final String mAccountName;
        private final String mAccountType;
        private final int mSimSlotIndex;
        private final int mEfType;

        /** @hide */
        public SimAccount(String accountName, String accountType, int simSlotIndex, int efType) {
            mAccountName = accountName;
            mAccountType = accountType;
            mSimSlotIndex = simSlotIndex;
            mEfType = efType;
        }

        private SimAccount(android.os.Parcel in) {
            mAccountName = in.readString();
            mAccountType = in.readString();
            mSimSlotIndex = in.readInt();
            mEfType = in.readInt();
        }

        public String getAccountName() { return mAccountName; }
        public String getAccountType() { return mAccountType; }
        public int getSimSlotIndex() { return mSimSlotIndex; }
        public int getEfType() { return mEfType; }

        @Override public int describeContents() { return 0; }

        @Override public void writeToParcel(android.os.Parcel dest, int flags) {
            dest.writeString(mAccountName);
            dest.writeString(mAccountType);
            dest.writeInt(mSimSlotIndex);
            dest.writeInt(mEfType);
        }

        public static final Creator<SimAccount> CREATOR = new Creator<SimAccount>() {
            @Override public SimAccount createFromParcel(android.os.Parcel in) {
                return new SimAccount(in);
            }
            @Override public SimAccount[] newArray(int size) { return new SimAccount[size]; }
        };

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SimAccount)) return false;
            SimAccount other = (SimAccount) o;
            return mSimSlotIndex == other.mSimSlotIndex && mEfType == other.mEfType
                    && java.util.Objects.equals(mAccountName, other.mAccountName)
                    && java.util.Objects.equals(mAccountType, other.mAccountType);
        }

        @Override public int hashCode() {
            return java.util.Objects.hash(mAccountName, mAccountType, mSimSlotIndex, mEfType);
        }
    }

    /** SIM-backed contacts. There is no SIM, so there are no SIM accounts. */
    public static final class SimContacts {
        private SimContacts() { }

        public static final String ACTION_SIM_ACCOUNTS_CHANGED =
                "android.provider.action.SIM_ACCOUNTS_CHANGED";

        public static java.util.List<SimAccount> getSimAccounts(ContentResolver resolver) {
            return java.util.Collections.emptyList();
        }
    }
}
