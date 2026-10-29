package hello.system;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;

/** CalendarProvider: a local calendar (as a sync adapter creates it), an event in it, the event queried back through Events and Instances, both removed. */
final class CalendarCheck implements Check {
    private static Uri asSyncAdapter(Uri uri) {
        return uri.buildUpon().appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true").appendQueryParameter(Calendars.ACCOUNT_NAME, "apklayer")
                .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL).build();
    }

    @Override public String run(Context context) throws Exception {
        ContentResolver resolver = context.getContentResolver();
        ContentValues calendar = new ContentValues();
        calendar.put(Calendars.ACCOUNT_NAME, "apklayer");
        calendar.put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL);
        calendar.put(Calendars.NAME, "check");
        calendar.put(Calendars.CALENDAR_DISPLAY_NAME, "Check");
        calendar.put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER);
        calendar.put(Calendars.OWNER_ACCOUNT, "apklayer");
        calendar.put(Calendars.VISIBLE, 1);
        calendar.put(Calendars.SYNC_EVENTS, 1);
        Uri calendarUri = resolver.insert(asSyncAdapter(Calendars.CONTENT_URI), calendar);
        Check.require(calendarUri != null, "no calendar uri");
        long calendarId = ContentUris.parseId(calendarUri);
        long start = System.currentTimeMillis() + 3600_000L;
        ContentValues event = new ContentValues();
        event.put(Events.CALENDAR_ID, calendarId);
        event.put(Events.TITLE, "Apklayer event");
        event.put(Events.DTSTART, start);
        event.put(Events.DTEND, start + 1800_000L);
        event.put(Events.EVENT_TIMEZONE, "UTC");
        Uri eventUri = resolver.insert(Events.CONTENT_URI, event);
        Check.require(eventUri != null, "no event uri");
        String title;
        try (Cursor c = resolver.query(eventUri, new String[] {Events.TITLE}, null, null, null)) {
            Check.require(c != null && c.moveToFirst(), "the event is not there");
            title = c.getString(0);
        }
        Uri window = CalendarContract.Instances.CONTENT_URI.buildUpon().appendPath(String.valueOf(start - 1000)).appendPath(String.valueOf(start + 3600_000L)).build();
        int instances;
        try (Cursor c = resolver.query(window, new String[] {CalendarContract.Instances.TITLE}, null, null, null)) {
            instances = c == null ? -1 : c.getCount();
        }
        Check.require(instances == 1, "instances in the window: " + instances);
        resolver.delete(asSyncAdapter(Calendars.CONTENT_URI), Calendars._ID + "=?", new String[] {String.valueOf(calendarId)});
        return title + ", " + instances + " instance";
    }
}
