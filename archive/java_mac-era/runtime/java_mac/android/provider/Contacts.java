package android.provider;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * The deprecated pre-ContactsContract Contacts provider surface.
 *
 * ⚠ Mac.apk deliberately registers NO contacts provider (that is a standing,
 * separately-tracked design decision, not an oversight). Everything in this
 * file is therefore CONSTANTS and CLIENT-SIDE HELPERS: the URI/column names old
 * apps compile against resolve, and the helper methods run AOSP's exact
 * client-side logic against the ContentResolver. With no provider registered
 * those queries come back null/empty, which is what AOSP's own code does on a
 * device where the contacts provider is disabled -- and which every one of
 * these helpers already handles (they null-check the cursor).
 *
 * Declaring these constants does NOT register a provider and does not change
 * ContentResolver's routing.
 *
 * AOSP-conformance (framework_lint vs android-30 stub jar): AOSP does not mark
 * this final. Final-when-AOSP-is-not is the dangerous direction -- an app
 * compiled against real Android that subclasses this fails to resolve against
 * our jar. See docs/aosp-conformance-audit_2026-08-23.md.
 *
 * @deprecated AOSP deprecated the whole class in API 5 in favour of
 * ContactsContract; still shipped, and still what long-tail apps link against.
 *
 * Source: frameworks/base/core/java/android/provider/Contacts.java
 */
@Deprecated
public class Contacts {

    public static final String AUTHORITY = "contacts";
    public static final Uri CONTENT_URI = Uri.parse("content://contacts");
    public static final int KIND_EMAIL = 1;
    public static final int KIND_IM = 3;
    public static final int KIND_ORGANIZATION = 4;
    public static final int KIND_PHONE = 5;
    public static final int KIND_POSTAL = 2;

    /** AOSP declares a public no-arg constructor on this deprecated class. */
    public Contacts() { }

    private static final String[] GROUPS_PROJECTION = new String[] { Groups._ID };

    /**
     * AOSP reads these from com.android.internal.R.array.phoneTypes /
     * emailAddressTypes / postalAddressTypes, which are platform string
     * resources Mac.apk does not ship. The English defaults are inlined below,
     * in AOSP's exact order, so getDisplayLabel returns the right LABEL for the
     * right type; only localisation is lost, and a wrong label would be worse
     * than an unlocalised one.
     */
    private static final String[] PHONE_TYPE_LABELS = {
        "Home", "Mobile", "Work", "Work Fax", "Home Fax", "Pager", "Other",
    };
    private static final String[] EMAIL_TYPE_LABELS = { "Home", "Work", "Other" };
    private static final String[] POSTAL_TYPE_LABELS = { "Home", "Work", "Other" };

    public interface PeopleColumns {
        String CUSTOM_RINGTONE = "custom_ringtone";
        String DISPLAY_NAME = "display_name";
        String LAST_TIME_CONTACTED = "last_time_contacted";
        String NAME = "name";
        String NOTES = "notes";
        String PHONETIC_NAME = "phonetic_name";
        String PHOTO_VERSION = "photo_version";
        String SEND_TO_VOICEMAIL = "send_to_voicemail";
        String STARRED = "starred";
        String TIMES_CONTACTED = "times_contacted";
    }

    public interface PhonesColumns {
        String ISPRIMARY = "isprimary";
        String LABEL = "label";
        String NUMBER = "number";
        String NUMBER_KEY = "number_key";
        String TYPE = "type";
        int TYPE_CUSTOM = 0;
        int TYPE_FAX_HOME = 5;
        int TYPE_FAX_WORK = 4;
        int TYPE_HOME = 1;
        int TYPE_MOBILE = 2;
        int TYPE_OTHER = 7;
        int TYPE_PAGER = 6;
        int TYPE_WORK = 3;
    }

    public interface PresenceColumns {
        int AVAILABLE = 5;
        int AWAY = 2;
        int DO_NOT_DISTURB = 4;
        int IDLE = 3;
        String IM_ACCOUNT = "im_account";
        String IM_HANDLE = "im_handle";
        String IM_PROTOCOL = "im_protocol";
        int INVISIBLE = 1;
        int OFFLINE = 0;
        String PRESENCE_CUSTOM_STATUS = "status";
        String PRESENCE_STATUS = "mode";
        String PRIORITY = "priority";
    }

    public interface ContactMethodsColumns {
        String AUX_DATA = "aux_data";
        String DATA = "data";
        String ISPRIMARY = "isprimary";
        String KIND = "kind";
        String LABEL = "label";
        String TYPE = "type";
        int TYPE_CUSTOM = 0;
        int TYPE_HOME = 1;
        int TYPE_OTHER = 3;
        int TYPE_WORK = 2;
    }

    public interface GroupsColumns {
        String NAME = "name";
        String NOTES = "notes";
        String SHOULD_SYNC = "should_sync";
        String SYSTEM_ID = "system_id";
    }

    public interface OrganizationColumns {
        String COMPANY = "company";
        String ISPRIMARY = "isprimary";
        String LABEL = "label";
        String PERSON_ID = "person";
        String TITLE = "title";
        String TYPE = "type";
        int TYPE_CUSTOM = 0;
        int TYPE_OTHER = 2;
        int TYPE_WORK = 1;
    }

    public interface PhotosColumns {
        String DATA = "data";
        String DOWNLOAD_REQUIRED = "download_required";
        String EXISTS_ON_SERVER = "exists_on_server";
        String LOCAL_VERSION = "local_version";
        String PERSON_ID = "person";
        String SYNC_ERROR = "sync_error";
    }

    public interface ExtensionsColumns {
        String NAME = "name";
        String VALUE = "value";
    }

    public interface SettingsColumns {
        String KEY = "key";
        String VALUE = "value";
        String _SYNC_ACCOUNT = "_sync_account";
        String _SYNC_ACCOUNT_TYPE = "_sync_account_type";
    }

    public static final class People implements BaseColumns, PeopleColumns, PhonesColumns,
            PresenceColumns {
        public static final Uri CONTENT_FILTER_URI = Uri.parse("content://contacts/people/filter");
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/person";
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/person";
        public static final Uri CONTENT_URI = Uri.parse("content://contacts/people");
        public static final String DEFAULT_SORT_ORDER = "name ASC";
        public static final Uri DELETED_CONTENT_URI = Uri.parse("content://contacts/deleted_people");
        public static final String PRIMARY_EMAIL_ID = "primary_email";
        public static final String PRIMARY_ORGANIZATION_ID = "primary_organization";
        public static final String PRIMARY_PHONE_ID = "primary_phone";

        private People() { }

        /** AOSP: "No longer supported." A genuine no-op on a real device too. */
        public static void markAsContacted(ContentResolver resolver, long personId) { }

        /** AOSP's client-side lookup of the "My Contacts" system group id. */
        public static long tryGetMyContactsGroupId(ContentResolver resolver) {
            Cursor groupsCursor = resolver.query(Groups.CONTENT_URI, GROUPS_PROJECTION,
                    Groups.SYSTEM_ID + "='" + Groups.GROUP_MY_CONTACTS + "'", null, null);
            if (groupsCursor != null) {
                try {
                    if (groupsCursor.moveToFirst()) return groupsCursor.getLong(0);
                } finally {
                    groupsCursor.close();
                }
            }
            return 0;
        }

        public static Uri addToMyContactsGroup(ContentResolver resolver, long personId) {
            long groupId = tryGetMyContactsGroupId(resolver);
            if (groupId == 0) {
                throw new IllegalStateException("Failed to find the My Contacts group");
            }
            return addToGroup(resolver, personId, groupId);
        }

        public static Uri addToGroup(ContentResolver resolver, long personId, String groupName) {
            long groupId = 0;
            Cursor groupsCursor = resolver.query(Groups.CONTENT_URI, GROUPS_PROJECTION,
                    Groups.NAME + "=?", new String[] { groupName }, null);
            if (groupsCursor != null) {
                try {
                    if (groupsCursor.moveToFirst()) groupId = groupsCursor.getLong(0);
                } finally {
                    groupsCursor.close();
                }
            }
            if (groupId == 0) {
                throw new IllegalStateException("Failed to find the My Contacts group");
            }
            return addToGroup(resolver, personId, groupId);
        }

        public static Uri addToGroup(ContentResolver resolver, long personId, long groupId) {
            ContentValues values = new ContentValues();
            values.put(GroupMembership.PERSON_ID, personId);
            values.put(GroupMembership.GROUP_ID, groupId);
            return resolver.insert(GroupMembership.CONTENT_URI, values);
        }

        public static Uri createPersonInMyContactsGroup(ContentResolver resolver,
                ContentValues values) {
            Uri contactUri = resolver.insert(People.CONTENT_URI, values);
            if (contactUri == null) return null;
            if (addToMyContactsGroup(resolver, ContentUris.parseId(contactUri)) == null) {
                resolver.delete(contactUri, null, null);
                return null;
            }
            return contactUri;
        }

        public static Cursor queryGroups(ContentResolver resolver, long person) {
            return resolver.query(GroupMembership.CONTENT_URI, null, "person=?",
                    new String[] { String.valueOf(person) }, Groups.DEFAULT_SORT_ORDER);
        }

        public static void setPhotoData(ContentResolver cr, Uri person, byte[] data) {
            Uri photoUri = Uri.withAppendedPath(person, Photos.CONTENT_DIRECTORY);
            ContentValues values = new ContentValues();
            values.put(Photos.DATA, data);
            cr.update(photoUri, values, null, null);
        }

        public static InputStream openContactPhotoInputStream(ContentResolver cr, Uri person) {
            Uri photoUri = Uri.withAppendedPath(person, Photos.CONTENT_DIRECTORY);
            Cursor cursor = cr.query(photoUri, new String[] { Photos.DATA }, null, null, null);
            try {
                if (cursor == null || !cursor.moveToNext()) return null;
                byte[] data = cursor.getBlob(0);
                if (data == null) return null;
                return new ByteArrayInputStream(data);
            } finally {
                if (cursor != null) cursor.close();
            }
        }

        public static Bitmap loadContactPhoto(Context context, Uri person,
                int placeholderImageResource, BitmapFactory.Options options) {
            if (person == null) {
                return loadPlaceholderPhoto(placeholderImageResource, context, options);
            }
            InputStream stream = openContactPhotoInputStream(context.getContentResolver(), person);
            Bitmap bm = stream != null ? BitmapFactory.decodeStream(stream, null, options) : null;
            if (bm == null) {
                bm = loadPlaceholderPhoto(placeholderImageResource, context, options);
            }
            return bm;
        }

        private static Bitmap loadPlaceholderPhoto(int placeholderImageResource, Context context,
                BitmapFactory.Options options) {
            if (placeholderImageResource == 0) return null;
            return BitmapFactory.decodeResource(context.getResources(),
                    placeholderImageResource, options);
        }

        public static final class Phones implements BaseColumns, PeopleColumns, PhonesColumns {
            public static final String CONTENT_DIRECTORY = "phones";
            public static final String DEFAULT_SORT_ORDER = "number ASC";
            private Phones() { }
        }

        public static final class ContactMethods
                implements BaseColumns, ContactMethodsColumns, PeopleColumns {
            public static final String CONTENT_DIRECTORY = "contact_methods";
            public static final String DEFAULT_SORT_ORDER = "data ASC";
            private ContactMethods() { }
        }

        public static final class Extensions implements BaseColumns, ExtensionsColumns {
            public static final String CONTENT_DIRECTORY = "extensions";
            public static final String DEFAULT_SORT_ORDER = "name ASC";
            public static final String PERSON_ID = "person";
            private Extensions() { }
        }
    }

    public static final class Phones implements BaseColumns, PeopleColumns, PhonesColumns {
        public static final Uri CONTENT_FILTER_URL = Uri.parse("content://contacts/phones/filter");
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/phone";
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/phone";
        public static final Uri CONTENT_URI = Uri.parse("content://contacts/phones");
        public static final String DEFAULT_SORT_ORDER = "name ASC";
        public static final String PERSON_ID = "person";

        private Phones() { }

        public static CharSequence getDisplayLabel(Context context, int type, CharSequence label,
                CharSequence[] labelArray) {
            CharSequence display = "";
            if (type != PhonesColumns.TYPE_CUSTOM) {
                CharSequence[] labels = labelArray != null ? labelArray : PHONE_TYPE_LABELS;
                try {
                    display = labels[type - 1];
                } catch (ArrayIndexOutOfBoundsException e) {
                    display = labels[PhonesColumns.TYPE_HOME - 1];
                }
            } else if (label != null && label.length() > 0) {
                display = label;
            }
            return display;
        }

        public static CharSequence getDisplayLabel(Context context, int type, CharSequence label) {
            return getDisplayLabel(context, type, label, null);
        }
    }

    public static final class ContactMethods
            implements BaseColumns, ContactMethodsColumns, PeopleColumns {
        public static final String CONTENT_EMAIL_ITEM_TYPE = "vnd.android.cursor.item/email";
        public static final String CONTENT_EMAIL_TYPE = "vnd.android.cursor.dir/email";
        public static final Uri CONTENT_EMAIL_URI = Uri.parse("content://contacts/contact_methods/email");
        public static final String CONTENT_IM_ITEM_TYPE = "vnd.android.cursor.item/jabber-im";
        public static final String CONTENT_POSTAL_ITEM_TYPE = "vnd.android.cursor.item/postal-address";
        public static final String CONTENT_POSTAL_TYPE = "vnd.android.cursor.dir/postal-address";
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/contact-methods";
        public static final Uri CONTENT_URI = Uri.parse("content://contacts/contact_methods");
        public static final String DEFAULT_SORT_ORDER = "name ASC";
        public static final String PERSON_ID = "person";
        public static final String POSTAL_LOCATION_LATITUDE = "data";
        public static final String POSTAL_LOCATION_LONGITUDE = "aux_data";
        public static final int PROTOCOL_AIM = 0;
        public static final int PROTOCOL_GOOGLE_TALK = 5;
        public static final int PROTOCOL_ICQ = 6;
        public static final int PROTOCOL_JABBER = 7;
        public static final int PROTOCOL_MSN = 1;
        public static final int PROTOCOL_QQ = 4;
        public static final int PROTOCOL_SKYPE = 3;
        public static final int PROTOCOL_YAHOO = 2;

        /** AOSP declares a public no-arg constructor (addPostalLocation is an
         *  INSTANCE method on this otherwise-static contract class). */
        public ContactMethods() { }

        public void addPostalLocation(Context context, long postalId, double latitude,
                double longitude) {
            final ContentResolver resolver = context.getContentResolver();
            ContentValues values = new ContentValues(2);
            values.put(POSTAL_LOCATION_LATITUDE, latitude);
            values.put(POSTAL_LOCATION_LONGITUDE, longitude);
            Uri loc = resolver.insert(CONTENT_URI, values);
            long locId = ContentUris.parseId(loc);

            values.clear();
            values.put(AUX_DATA, locId);
            resolver.update(ContentUris.withAppendedId(CONTENT_URI, postalId), values, null, null);
        }

        public static String encodePredefinedImProtocol(int protocol) {
            return "pre:" + protocol;
        }

        public static String encodeCustomImProtocol(String protocolString) {
            return "custom:" + protocolString;
        }

        /** Returns an Integer for "pre:N" and a String for "custom:X", per AOSP. */
        public static Object decodeImProtocol(String encodedString) {
            if (encodedString == null) return null;
            if (encodedString.startsWith("pre:")) {
                return Integer.parseInt(encodedString.substring(4));
            }
            if (encodedString.startsWith("custom:")) {
                return encodedString.substring(7);
            }
            throw new IllegalArgumentException(
                    "the value is not a valid encoded protocol, " + encodedString);
        }

        public static CharSequence getDisplayLabel(Context context, int kind, int type,
                CharSequence label) {
            CharSequence display = "";
            switch (kind) {
                case KIND_EMAIL: {
                    if (type != ContactMethodsColumns.TYPE_CUSTOM) {
                        try {
                            display = EMAIL_TYPE_LABELS[type - 1];
                        } catch (ArrayIndexOutOfBoundsException e) {
                            display = EMAIL_TYPE_LABELS[ContactMethodsColumns.TYPE_HOME - 1];
                        }
                    } else if (label != null && label.length() > 0) {
                        display = label;
                    }
                    break;
                }
                case KIND_POSTAL: {
                    if (type != ContactMethodsColumns.TYPE_CUSTOM) {
                        try {
                            display = POSTAL_TYPE_LABELS[type - 1];
                        } catch (ArrayIndexOutOfBoundsException e) {
                            display = POSTAL_TYPE_LABELS[ContactMethodsColumns.TYPE_HOME - 1];
                        }
                    } else if (label != null && label.length() > 0) {
                        display = label;
                    }
                    break;
                }
                default:
                    // AOSP falls through to "" for any other kind.
                    break;
            }
            return display;
        }
    }

    public static final class Groups implements BaseColumns, GroupsColumns {
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/contactsgroup";
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/contactsgroup";
        public static final Uri CONTENT_URI = Uri.parse("content://contacts/groups");
        public static final String DEFAULT_SORT_ORDER = "name ASC";
        public static final Uri DELETED_CONTENT_URI = Uri.parse("content://contacts/deleted_groups");
        public static final String GROUP_ANDROID_STARRED = "Starred in Android";
        public static final String GROUP_MY_CONTACTS = "Contacts";
        private Groups() { }
    }

    public static final class GroupMembership implements BaseColumns, GroupsColumns {
        public static final String CONTENT_DIRECTORY = "groupmembership";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/contactsgroupmembership";
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/contactsgroupmembership";
        public static final Uri CONTENT_URI = Uri.parse("content://contacts/groupmembership");
        public static final String DEFAULT_SORT_ORDER = "group_id ASC";
        public static final String GROUP_ID = "group_id";
        public static final String GROUP_SYNC_ACCOUNT = "group_sync_account";
        public static final String GROUP_SYNC_ACCOUNT_TYPE = "group_sync_account_type";
        public static final String GROUP_SYNC_ID = "group_sync_id";
        public static final String PERSON_ID = "person";
        public static final Uri RAW_CONTENT_URI = Uri.parse("content://contacts/groupmembershipraw");
        private GroupMembership() { }
    }

    public static final class Organizations implements BaseColumns, OrganizationColumns {
        public static final String CONTENT_DIRECTORY = "organizations";
        public static final Uri CONTENT_URI = Uri.parse("content://contacts/organizations");
        public static final String DEFAULT_SORT_ORDER = "company, title, isprimary ASC";

        private Organizations() { }

        public static CharSequence getDisplayLabel(Context context, int type,
                CharSequence label) {
            // AOSP reads com.android.internal.R.array.organizationTypes.
            final String[] labels = { "Work", "Other" };
            if (type != OrganizationColumns.TYPE_CUSTOM) {
                try {
                    return labels[type - 1];
                } catch (ArrayIndexOutOfBoundsException e) {
                    return labels[OrganizationColumns.TYPE_WORK - 1];
                }
            }
            return label != null ? label : "";
        }
    }

    public static final class Photos implements BaseColumns, PhotosColumns {
        public static final String CONTENT_DIRECTORY = "photo";
        public static final Uri CONTENT_URI = Uri.parse("content://contacts/photos");
        public static final String DEFAULT_SORT_ORDER = "person ASC";
        private Photos() { }
    }

    public static final class Extensions implements BaseColumns, ExtensionsColumns {
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/contact_extensions";
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/contact_extensions";
        public static final Uri CONTENT_URI = Uri.parse("content://contacts/extensions");
        public static final String DEFAULT_SORT_ORDER = "person, name ASC";
        public static final String PERSON_ID = "person";
        private Extensions() { }
    }

    public static final class Settings implements BaseColumns, SettingsColumns {
        public static final String CONTENT_DIRECTORY = "settings";
        public static final Uri CONTENT_URI = Uri.parse("content://contacts/settings");
        public static final String DEFAULT_SORT_ORDER = "key ASC";
        public static final String SYNC_EVERYTHING = "syncEverything";

        private Settings() { }

        /** Client-side helper; with no provider the query returns null and so do we. */
        public static String getSetting(ContentResolver cr, String account, String key) {
            Cursor c = cr.query(CONTENT_URI, new String[] { VALUE },
                    _SYNC_ACCOUNT + "=? AND " + KEY + "=?", new String[] { account, key }, null);
            if (c == null) return null;
            try {
                return c.moveToNext() ? c.getString(0) : null;
            } finally {
                c.close();
            }
        }

        public static void setSetting(ContentResolver cr, String account, String key,
                String value) {
            ContentValues values = new ContentValues();
            values.put(_SYNC_ACCOUNT, account);
            values.put(KEY, key);
            values.put(VALUE, value);
            cr.update(CONTENT_URI, values, null, null);
        }
    }

    public static final class Intents {
        public static final String ATTACH_IMAGE = "com.android.contacts.action.ATTACH_IMAGE";
        public static final String EXTRA_CREATE_DESCRIPTION = "com.android.contacts.action.CREATE_DESCRIPTION";
        public static final String EXTRA_FORCE_CREATE = "com.android.contacts.action.FORCE_CREATE";
        public static final String SEARCH_SUGGESTION_CLICKED = "android.provider.Contacts.SEARCH_SUGGESTION_CLICKED";
        public static final String SEARCH_SUGGESTION_CREATE_CONTACT_CLICKED = "android.provider.Contacts.SEARCH_SUGGESTION_CREATE_CONTACT_CLICKED";
        public static final String SEARCH_SUGGESTION_DIAL_NUMBER_CLICKED = "android.provider.Contacts.SEARCH_SUGGESTION_DIAL_NUMBER_CLICKED";
        public static final String SHOW_OR_CREATE_CONTACT = "com.android.contacts.action.SHOW_OR_CREATE_CONTACT";

        /** AOSP declares a public no-arg constructor. */
        public Intents() { }

        public static final class Insert {
            public static final String ACTION = "android.intent.action.INSERT";
            public static final String COMPANY = "company";
            public static final String EMAIL = "email";
            public static final String EMAIL_ISPRIMARY = "email_isprimary";
            public static final String EMAIL_TYPE = "email_type";
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
            /** AOSP declares a public no-arg constructor. */
            public Insert() { }
        }

        public static final class UI {
            public static final String FILTER_CONTACTS_ACTION = "com.android.contacts.action.FILTER_CONTACTS";
            public static final String FILTER_TEXT_EXTRA_KEY = "com.android.contacts.extra.FILTER_TEXT";
            public static final String GROUP_NAME_EXTRA_KEY = "com.android.contacts.extra.GROUP";
            public static final String LIST_ALL_CONTACTS_ACTION = "com.android.contacts.action.LIST_ALL_CONTACTS";
            public static final String LIST_CONTACTS_WITH_PHONES_ACTION = "com.android.contacts.action.LIST_CONTACTS_WITH_PHONES";
            public static final String LIST_DEFAULT = "com.android.contacts.action.LIST_DEFAULT";
            public static final String LIST_FREQUENT_ACTION = "com.android.contacts.action.LIST_FREQUENT";
            public static final String LIST_GROUP_ACTION = "com.android.contacts.action.LIST_GROUP";
            public static final String LIST_STARRED_ACTION = "com.android.contacts.action.LIST_STARRED";
            public static final String LIST_STREQUENT_ACTION = "com.android.contacts.action.LIST_STREQUENT";
            public static final String TITLE_EXTRA_KEY = "com.android.contacts.extra.TITLE_EXTRA";
            /** AOSP declares a public no-arg constructor. */
            public UI() { }
        }
    }
}
