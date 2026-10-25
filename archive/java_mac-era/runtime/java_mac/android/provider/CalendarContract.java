// android.provider.CalendarContract -- the calendar-database contract. Apps
// insert/query calendar events via the CONTENT_URI constants + column
// interfaces through ContentResolver, exactly as they do for
// ContactsContract. Mac.apk hosts no calendar provider, so a query/insert
// against any of these URIs finds no registered authority and our existing
// ContentResolver honestly returns null (no rows) -- the same "no provider
// found" degrade ContactsContract/MediaStore already rely on. The constant
// and column surface below must still exist and be complete enough that any
// app code building a calendar-reminder feature compiles and degrades
// gracefully instead of NoClassDefFoundError at the first touch. Constant
// names + string/int values verified against AOSP source (see fix citation).
package android.provider;

import android.net.Uri;

public final class CalendarContract {
    private CalendarContract() {}

    public static final String AUTHORITY = "com.android.calendar";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY);

    // --- shared column groups, mixed into each table class below (AOSP
    // declares these as package-protected interfaces; public here since our
    // ContactsContract sibling in this package does the same). ---

    public interface SyncColumns {
        String ACCOUNT_NAME = "account_name";
        String ACCOUNT_TYPE = "account_type";
        String _SYNC_ID = "_sync_id";
        String DIRTY = "dirty";
        String MUTATORS = "mutators";
        String DELETED = "deleted";
        String CAN_PARTIALLY_UPDATE = "canPartiallyUpdate";
    }

    public interface CalendarColumns {
        String CALENDAR_COLOR = "calendar_color";
        String CALENDAR_COLOR_KEY = "calendar_color_index";
        String CALENDAR_DISPLAY_NAME = "calendar_displayName";
        String CALENDAR_ACCESS_LEVEL = "calendar_access_level";
        int CAL_ACCESS_NONE = 0;
        int CAL_ACCESS_FREEBUSY = 100;
        int CAL_ACCESS_READ = 200;
        int CAL_ACCESS_RESPOND = 300;
        int CAL_ACCESS_OVERRIDE = 400;
        int CAL_ACCESS_CONTRIBUTOR = 500;
        int CAL_ACCESS_EDITOR = 600;
        int CAL_ACCESS_OWNER = 700;
        int CAL_ACCESS_ROOT = 800;
        String VISIBLE = "visible";
        String CALENDAR_TIME_ZONE = "calendar_timezone";
        String SYNC_EVENTS = "sync_events";
        String OWNER_ACCOUNT = "ownerAccount";
        String CAN_ORGANIZER_RESPOND = "canOrganizerRespond";
        String CAN_MODIFY_TIME_ZONE = "canModifyTimeZone";
        String MAX_REMINDERS = "maxReminders";
        String ALLOWED_REMINDERS = "allowedReminders";
        String ALLOWED_AVAILABILITY = "allowedAvailability";
        String ALLOWED_ATTENDEE_TYPES = "allowedAttendeeTypes";
        String IS_PRIMARY = "isPrimary";
    }

    // Columns from the Events table that other tables (Reminders, Attendees)
    // join into themselves, matching AOSP's own EventsColumns layout.
    public interface EventsColumns {
        String CALENDAR_ID = "calendar_id";
        String TITLE = "title";
        String DESCRIPTION = "description";
        String EVENT_LOCATION = "eventLocation";
        String EVENT_COLOR = "eventColor";
        String EVENT_COLOR_KEY = "eventColor_index";
        String DISPLAY_COLOR = "displayColor";
        String STATUS = "eventStatus";
        int STATUS_TENTATIVE = 0;
        int STATUS_CONFIRMED = 1;
        int STATUS_CANCELED = 2;
        String SELF_ATTENDEE_STATUS = "selfAttendeeStatus";
        String DTSTART = "dtstart";
        String DTEND = "dtend";
        String DURATION = "duration";
        String EVENT_TIMEZONE = "eventTimezone";
        String EVENT_END_TIMEZONE = "eventEndTimezone";
        String ALL_DAY = "allDay";
        String ACCESS_LEVEL = "accessLevel";
        int ACCESS_DEFAULT = 0;
        int ACCESS_CONFIDENTIAL = 1;
        int ACCESS_PRIVATE = 2;
        int ACCESS_PUBLIC = 3;
        String AVAILABILITY = "availability";
        String HAS_ALARM = "hasAlarm";
        String HAS_EXTENDED_PROPERTIES = "hasExtendedProperties";
        String RRULE = "rrule";
        String RDATE = "rdate";
        String EXRULE = "exrule";
        String EXDATE = "exdate";
        String ORGANIZER = "organizer";
        String IS_ORGANIZER = "isOrganizer";
        String GUESTS_CAN_MODIFY = "guestsCanModify";
        String GUESTS_CAN_INVITE_OTHERS = "guestsCanInviteOthers";
        String GUESTS_CAN_SEE_GUESTS = "guestsCanSeeGuests";
        String CUSTOM_APP_PACKAGE = "customAppPackage";
        String CUSTOM_APP_URI = "customAppUri";
    }

    public interface RemindersColumns {
        String EVENT_ID = "event_id";
        String MINUTES = "minutes";
        int MINUTES_DEFAULT = -1;
        String METHOD = "method";
        int METHOD_DEFAULT = 0;
        int METHOD_ALERT = 1;
        int METHOD_EMAIL = 2;
        int METHOD_SMS = 3;
        int METHOD_ALARM = 4;
    }

    public interface AttendeesColumns {
        String EVENT_ID = "event_id";
        String ATTENDEE_NAME = "attendeeName";
        String ATTENDEE_EMAIL = "attendeeEmail";
        String ATTENDEE_RELATIONSHIP = "attendeeRelationship";
        int RELATIONSHIP_NONE = 0;
        int RELATIONSHIP_ATTENDEE = 1;
        int RELATIONSHIP_ORGANIZER = 2;
        int RELATIONSHIP_PERFORMER = 3;
        int RELATIONSHIP_SPEAKER = 4;
        String ATTENDEE_TYPE = "attendeeType";
        int TYPE_NONE = 0;
        int TYPE_REQUIRED = 1;
        int TYPE_OPTIONAL = 2;
        int TYPE_RESOURCE = 3;
        String ATTENDEE_STATUS = "attendeeStatus";
        int ATTENDEE_STATUS_NONE = 0;
        int ATTENDEE_STATUS_ACCEPTED = 1;
        int ATTENDEE_STATUS_DECLINED = 2;
        int ATTENDEE_STATUS_INVITED = 3;
        int ATTENDEE_STATUS_TENTATIVE = 4;
        String ATTENDEE_IDENTITY = "attendeeIdentity";
        String ATTENDEE_ID_NAMESPACE = "attendeeIdNamespace";
    }

    /**
     * AOSP asks DevicePolicyManager to launch the event in the work profile.
     * There is no managed profile on a Mac (DevicePolicyManager reports no
     * admin), so nothing can be launched and false -- AOSP's own "the intent
     * could not be started" answer -- is returned.
     */
    public static boolean startViewCalendarEventInManagedProfile(android.content.Context context,
            long eventId, long startMs, long endMs, boolean allDay, int flags) {
        if (context == null) throw new NullPointerException("Context is null");
        return false;
    }

    public static final class Calendars implements BaseColumns, SyncColumns, CalendarColumns {
        private Calendars() {}
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/calendars");
        /** Work-profile twig; no managed profile exists here. */
        public static final Uri ENTERPRISE_CONTENT_URI =
                Uri.parse("content://" + AUTHORITY + "/enterprise/calendars");
        public static final String NAME = "name";
        public static final String CALENDAR_LOCATION = "calendar_location";
    }

    // No CONTENT_TYPE / CONTENT_ITEM_TYPE here: verified against AOSP source
    // that (unlike ContactsContract's Contacts/Data tables) Events/Reminders/
    // Attendees do not declare MIME-type constants -- do not add invented ones.
    public static final class Events implements BaseColumns, SyncColumns, EventsColumns,
            CalendarColumns {
        private Events() {}
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/events");
        /** Recurring-event exceptions; insertions require an appended event id. */
        public static final Uri CONTENT_EXCEPTION_URI =
                Uri.parse("content://" + AUTHORITY + "/exception");
        /** Work-profile twig; no managed profile exists here. */
        public static final Uri ENTERPRISE_CONTENT_URI =
                Uri.parse("content://" + AUTHORITY + "/enterprise/events");
    }

    public static final class Reminders implements BaseColumns, RemindersColumns, EventsColumns {
        private Reminders() {}
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/reminders");

        private static final String REMINDERS_WHERE = Reminders.EVENT_ID + "=?";

        /**
         * AOSP's client-side convenience query for one event's reminders. Runs
         * the exact same selection AOSP does; with no calendar provider
         * registered the resolver returns null, which is what AOSP returns when
         * the query fails too.
         */
        public static final android.database.Cursor query(
                android.content.ContentResolver cr, long eventId, String[] projection) {
            String[] remArgs = { Long.toString(eventId) };
            return cr.query(CONTENT_URI, projection, REMINDERS_WHERE, remArgs, null);
        }
    }

    public static final class Attendees implements BaseColumns, AttendeesColumns, EventsColumns {
        private Attendees() {}
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/attendees");

        private static final String ATTENDEES_WHERE = Attendees.EVENT_ID + "=?";

        /** See Reminders.query: AOSP's exact selection, run through the resolver. */
        public static final android.database.Cursor query(
                android.content.ContentResolver cr, long eventId, String[] projection) {
            String[] attArgs = { Long.toString(eventId) };
            return cr.query(CONTENT_URI, projection, ATTENDEES_WHERE, attArgs, null);
        }
    }

    // ========================================================================
    // API-36 contract classes added 2026-08-24. Constants plus client-side
    // query helpers; nothing registers a provider. With no calendar provider
    // registered every query below returns null, which is what AOSP's own
    // helpers return when the query fails.
    // Source: frameworks/base/core/java/android/provider/CalendarContract.java
    // ========================================================================

    protected interface CalendarSyncColumns {

    }

    protected interface ColorsColumns extends SyncColumns {

    }

    public static final class Colors implements ColorsColumns {
        private Colors() { }
public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/colors");
    }

    protected interface CalendarAlertsColumns {

    }

    public static final class CalendarAlerts implements BaseColumns, CalendarAlertsColumns,
            EventsColumns, CalendarColumns {
        private CalendarAlerts() { }
public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/calendar_alerts");
public static final Uri CONTENT_URI_BY_INSTANCE = Uri.parse("content://" + AUTHORITY + "/calendar_alerts/by_instance");
    }

    protected interface CalendarCacheColumns {

    }

    public static final class CalendarCache implements CalendarCacheColumns {
        private CalendarCache() { }
public static final String KEY_TIMEZONE_INSTANCES = "timezoneInstances";
public static final String KEY_TIMEZONE_INSTANCES_PREVIOUS = "timezoneInstancesPrevious";
public static final String KEY_TIMEZONE_TYPE = "timezoneType";
public static final String TIMEZONE_TYPE_AUTO = "auto";
public static final String TIMEZONE_TYPE_HOME = "home";
public static final Uri URI = Uri.parse("content://" + AUTHORITY + "/properties");
    }

    protected interface EventDaysColumns {
        public static final String STARTDAY = "startDay";
        public static final String ENDDAY = "endDay";
    }

    public static final class EventDays implements EventDaysColumns {
        private EventDays() { }
public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/instances/groupbyday");

        /**
         * AOSP asks the provider which of a Julian-day range contain events.
         * Client-side URI construction plus a resolver query, verbatim.
         */
        public static final android.database.Cursor query(
                android.content.ContentResolver cr, int startDay, int numDays,
                String[] projection) {
            if (numDays < 1) return null;
            int endDay = startDay + numDays - 1;
            Uri.Builder builder = CONTENT_URI.buildUpon();
            android.content.ContentUris.appendId(builder, startDay);
            android.content.ContentUris.appendId(builder, endDay);
            return cr.query(builder.build(), projection, SELECTION,
                    new String[] { "1" }, EventDaysColumns.STARTDAY);
        }

        private static final String SELECTION = "selected=1";
    }

    protected interface ExtendedPropertiesColumns {

    }

    public static final class ExtendedProperties implements BaseColumns,
            ExtendedPropertiesColumns, EventsColumns {
        private ExtendedProperties() { }
public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/extendedproperties");
    }

    public static final class Instances implements BaseColumns, EventsColumns, CalendarColumns {
        private Instances() { }
public static final String BEGIN = "begin";
public static final Uri CONTENT_BY_DAY_URI = Uri.parse("content://" + AUTHORITY + "/instances/whenbyday");
public static final Uri CONTENT_SEARCH_BY_DAY_URI = Uri.parse("content://" + AUTHORITY + "/instances/searchbyday");
public static final Uri CONTENT_SEARCH_URI = Uri.parse("content://" + AUTHORITY + "/instances/search");
public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/instances/when");
public static final String END = "end";
public static final String END_DAY = "endDay";
public static final String END_MINUTE = "endMinute";
public static final Uri ENTERPRISE_CONTENT_BY_DAY_URI = Uri.parse("content://" + AUTHORITY + "/enterprise/instances/whenbyday");
public static final Uri ENTERPRISE_CONTENT_SEARCH_BY_DAY_URI = Uri.parse("content://" + AUTHORITY + "/enterprise/instances/searchbyday");
public static final Uri ENTERPRISE_CONTENT_SEARCH_URI = Uri.parse("content://" + AUTHORITY + "/enterprise/instances/search");
public static final Uri ENTERPRISE_CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/enterprise/instances/when");
public static final String EVENT_ID = "event_id";
public static final String START_DAY = "startDay";
public static final String START_MINUTE = "startMinute";

        /**
         * AOSP expands recurrences over [begin, end] by appending the two
         * millisecond bounds to the URI. Client-side, verbatim.
         */
        public static final android.database.Cursor query(
                android.content.ContentResolver cr, String[] projection, long begin, long end) {
            Uri.Builder builder = CONTENT_URI.buildUpon();
            android.content.ContentUris.appendId(builder, begin);
            android.content.ContentUris.appendId(builder, end);
            return cr.query(builder.build(), projection, DEFAULT_SORT_ORDER_SELECTION, null,
                    DEFAULT_SORT_ORDER);
        }

        /** The search variant; the query string is appended after the bounds. */
        public static final android.database.Cursor query(
                android.content.ContentResolver cr, String[] projection, long begin, long end,
                String searchQuery) {
            Uri.Builder builder = CONTENT_SEARCH_URI.buildUpon();
            android.content.ContentUris.appendId(builder, begin);
            android.content.ContentUris.appendId(builder, end);
            builder = builder.appendPath(searchQuery);
            return cr.query(builder.build(), projection, DEFAULT_SORT_ORDER_SELECTION, null,
                    DEFAULT_SORT_ORDER);
        }

        private static final String DEFAULT_SORT_ORDER_SELECTION = "visible=1";
        private static final String DEFAULT_SORT_ORDER = "begin ASC";
    }

    /** Sync-adapter scratch state stored inside the calendar provider. */
    public static final class SyncState implements SyncStateContract.Columns {
        private SyncState() { }
public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/syncstate");
    }

    /**
     * AOSP wraps a Calendars cursor in an EntityIterator. Mac.apk registers no
     * calendar provider, so the cursor is always empty; the empty iterator is
     * what AOSP's own implementation yields for one.
     */
    public static final class CalendarEntity implements BaseColumns, SyncColumns,
            CalendarColumns {
        private CalendarEntity() { }
public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/calendar_entities");

        public static android.content.EntityIterator newEntityIterator(
                android.database.Cursor cursor) {
            return emptyEntityIterator(cursor);
        }
    }

    /** See CalendarEntity.newEntityIterator. */
    public static final class EventsEntity implements BaseColumns, SyncColumns, EventsColumns {
        private EventsEntity() { }
public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/event_entities");

        public static android.content.EntityIterator newEntityIterator(
                android.database.Cursor cursor, android.content.ContentResolver resolver) {
            return emptyEntityIterator(cursor);
        }

        public static android.content.EntityIterator newEntityIterator(
                android.database.Cursor cursor,
                android.content.ContentProviderClient provider) {
            return emptyEntityIterator(cursor);
        }
    }

    /** Shared empty, closeable EntityIterator; see ContactsContract's twin. */
    private static android.content.EntityIterator emptyEntityIterator(
            final android.database.Cursor cursor) {
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
}
