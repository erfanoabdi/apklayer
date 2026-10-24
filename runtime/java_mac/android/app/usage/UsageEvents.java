// android.app.usage.UsageEvents -- "A result returned from
// UsageStatsManager.queryEvents(long, long) from which to read
// UsageEvents.Event objects." API 21.
//
// AOSP ref: frameworks/base/core/java/android/app/usage/UsageEvents.java
// (android16-release). It is a CURSOR, not a list: the caller loops
// `while (events.hasNextEvent()) { events.getNextEvent(event); ... }`, reusing
// ONE Event instance that getNextEvent overwrites. That aliasing is part of the
// contract -- an app that stores the Event without copying it sees every slot
// change underneath it on a device too -- so it is reproduced here rather than
// "improved" into a list.
//
// Mac.apk records no usage events (see UsageStatsManager's class comment), so
// every UsageEvents the runtime hands out is empty and hasNextEvent() is false
// immediately -- which is what an app with no Usage Access permission also sees.
package android.app.usage;

import android.content.res.Configuration;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.PersistableBundle;

public final class UsageEvents implements Parcelable {

    /** AOSP UsageEvents.Event: one usage event. AOSP's fields are package-
     *  private and filled in by the system; the public surface is the getters
     *  plus a no-argument constructor, because the caller ALLOCATES the
     *  instance that getNextEvent then fills. */
    public static final class Event {

        /** AOSP event types. The numbering is platform-stable and apps switch
         *  on it, so the values are AOSP's verbatim. Note MOVE_TO_FOREGROUND /
         *  MOVE_TO_BACKGROUND are deprecated ALIASES of ACTIVITY_RESUMED /
         *  ACTIVITY_PAUSED, which is why each pair shares a value. */
        public static final int NONE = 0;
        public static final int ACTIVITY_RESUMED = 1;
        public static final int ACTIVITY_PAUSED = 2;
        @Deprecated public static final int MOVE_TO_FOREGROUND = ACTIVITY_RESUMED;
        @Deprecated public static final int MOVE_TO_BACKGROUND = ACTIVITY_PAUSED;
        public static final int CONFIGURATION_CHANGE = 5;
        public static final int USER_INTERACTION = 7;
        public static final int SHORTCUT_INVOCATION = 8;
        public static final int STANDBY_BUCKET_CHANGED = 11;
        public static final int SCREEN_INTERACTIVE = 15;
        public static final int SCREEN_NON_INTERACTIVE = 16;
        public static final int KEYGUARD_SHOWN = 17;
        public static final int KEYGUARD_HIDDEN = 18;
        public static final int FOREGROUND_SERVICE_START = 19;
        public static final int FOREGROUND_SERVICE_STOP = 20;
        public static final int ACTIVITY_STOPPED = 23;
        public static final int DEVICE_SHUTDOWN = 26;
        public static final int DEVICE_STARTUP = 27;

        String mPackage;
        String mClass;
        long mTimeStamp;
        int mEventType = NONE;
        Configuration mConfiguration;
        String mShortcutId;
        int mBucketAndReason;
        PersistableBundle mExtras;

        public Event() { }

        public String getPackageName() { return mPackage; }
        /** AOSP getClassName(): "the class name of the source of this event",
         *  null when the event has no component. */
        public String getClassName() { return mClass; }
        public long getTimeStamp() { return mTimeStamp; }
        public int getEventType() { return mEventType; }

        /** AOSP getConfiguration(): non-null only for a CONFIGURATION_CHANGE
         *  event. */
        public Configuration getConfiguration() {
            return mEventType == CONFIGURATION_CHANGE ? mConfiguration : null;
        }

        /** AOSP getShortcutId(): non-null only for a SHORTCUT_INVOCATION
         *  event. */
        public String getShortcutId() {
            return mEventType == SHORTCUT_INVOCATION ? mShortcutId : null;
        }

        /** AOSP getAppStandbyBucket(): "the standby bucket of the app, if the
         *  event is of type STANDBY_BUCKET_CHANGED, otherwise returns 0." AOSP
         *  packs the bucket into the high bits of mBucketAndReason. */
        public int getAppStandbyBucket() {
            return mEventType == STANDBY_BUCKET_CHANGED
                    ? (mBucketAndReason & 0xFFFF0000) >>> 16 : 0;
        }

        /** AOSP getExtras() (API 36): "the extras of this event", non-null --
         *  an empty PersistableBundle when the event carries none. */
        public PersistableBundle getExtras() {
            if (mExtras == null) mExtras = new PersistableBundle();
            return mExtras;
        }
    }

    private final java.util.List<Event> mEvents;
    private int mIndex;

    /** @hide -- AOSP's constructors are @hide too: only the system produces a
     *  populated UsageEvents. */
    UsageEvents(java.util.List<Event> events) {
        mEvents = (events != null) ? events : new java.util.ArrayList<Event>();
    }

    private UsageEvents(Parcel in) {
        mEvents = new java.util.ArrayList<Event>();
        int n = in.readInt();
        for (int i = 0; i < n; i++) {
            Event e = new Event();
            e.mPackage = in.readString();
            e.mClass = in.readString();
            e.mTimeStamp = in.readLong();
            e.mEventType = in.readInt();
            e.mShortcutId = in.readString();
            e.mBucketAndReason = in.readInt();
            e.mConfiguration = in.readTypedObject(Configuration.CREATOR);
            e.mExtras = in.readPersistableBundle();
            mEvents.add(e);
        }
    }

    /** AOSP hasNextEvent(): "@return true if there are more events to read." */
    public boolean hasNextEvent() { return mIndex < mEvents.size(); }

    /** AOSP getNextEvent(Event eventOut): "Retrieve the next
     *  UsageEvents.Event from the collection and put the resulting data into
     *  eventOut. @return true if an event was available, false if there are no
     *  more events." Note it FILLS the caller's object rather than returning
     *  one -- see the class comment. */
    public boolean getNextEvent(Event eventOut) {
        if (eventOut == null || !hasNextEvent()) return false;
        Event src = mEvents.get(mIndex++);
        eventOut.mPackage = src.mPackage;
        eventOut.mClass = src.mClass;
        eventOut.mTimeStamp = src.mTimeStamp;
        eventOut.mEventType = src.mEventType;
        eventOut.mConfiguration = src.mConfiguration;
        eventOut.mShortcutId = src.mShortcutId;
        eventOut.mBucketAndReason = src.mBucketAndReason;
        eventOut.mExtras = src.mExtras;
        return true;
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mEvents.size());
        for (int i = 0; i < mEvents.size(); i++) {
            Event e = mEvents.get(i);
            dest.writeString(e.mPackage);
            dest.writeString(e.mClass);
            dest.writeLong(e.mTimeStamp);
            dest.writeInt(e.mEventType);
            dest.writeString(e.mShortcutId);
            dest.writeInt(e.mBucketAndReason);
            dest.writeTypedObject(e.mConfiguration, flags);
            dest.writePersistableBundle(e.mExtras);
        }
    }

    public static final Parcelable.Creator<UsageEvents> CREATOR =
            new Parcelable.Creator<UsageEvents>() {
        @Override public UsageEvents createFromParcel(Parcel in) { return new UsageEvents(in); }
        @Override public UsageEvents[] newArray(int size) { return new UsageEvents[size]; }
    };
}
