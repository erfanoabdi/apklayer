// android.app.usage.UsageEventsQuery -- "An Object-Oriented representation for a
// UsageEvents query", built with UsageEventsQuery.Builder and handed to
// UsageStatsManager.queryEvents(UsageEventsQuery). API 35.
//
// AOSP ref: frameworks/base/core/java/android/app/usage/UsageEventsQuery.java
// (android16-release). A pure value object: a time range plus optional
// event-type and package-name filters.
package android.app.usage;

import android.os.Parcel;
import android.os.Parcelable;

public final class UsageEventsQuery implements Parcelable {

    private final long mBeginTimeMillis;
    private final long mEndTimeMillis;
    private final int[] mEventTypes;
    private final java.util.Set<String> mPackageNames;

    private UsageEventsQuery(Builder builder) {
        mBeginTimeMillis = builder.mBeginTimeMillis;
        mEndTimeMillis = builder.mEndTimeMillis;
        mEventTypes = builder.mEventTypes.clone();
        mPackageNames = new java.util.LinkedHashSet<String>(builder.mPackageNames);
    }

    private UsageEventsQuery(Parcel in) {
        mBeginTimeMillis = in.readLong();
        mEndTimeMillis = in.readLong();
        int[] types = in.createIntArray();
        mEventTypes = (types != null) ? types : new int[0];
        mPackageNames = new java.util.LinkedHashSet<String>();
        java.util.ArrayList<String> names = new java.util.ArrayList<String>();
        in.readStringList(names);
        mPackageNames.addAll(names);
    }

    /** AOSP getBeginTimeMillis(): "the inclusive timestamp to indicate the
     *  beginning of the range of events." */
    public long getBeginTimeMillis() { return mBeginTimeMillis; }
    /** AOSP getEndTimeMillis(): "the exclusive timestamp to indicate the end of
     *  the range of events." */
    public long getEndTimeMillis() { return mEndTimeMillis; }
    /** AOSP getEventTypes(): the event types to include; an EMPTY array means
     *  "all types", not "none". Never null. */
    public int[] getEventTypes() { return mEventTypes.clone(); }
    /** AOSP getPackageNames(): the packages to include; an EMPTY set means "all
     *  packages". Never null. */
    public java.util.Set<String> getPackageNames() {
        return java.util.Collections.unmodifiableSet(mPackageNames);
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(mBeginTimeMillis);
        dest.writeLong(mEndTimeMillis);
        dest.writeIntArray(mEventTypes);
        dest.writeStringList(new java.util.ArrayList<String>(mPackageNames));
    }

    public static final Parcelable.Creator<UsageEventsQuery> CREATOR =
            new Parcelable.Creator<UsageEventsQuery>() {
        @Override public UsageEventsQuery createFromParcel(Parcel in) {
            return new UsageEventsQuery(in);
        }
        @Override public UsageEventsQuery[] newArray(int size) {
            return new UsageEventsQuery[size];
        }
    };

    /** AOSP UsageEventsQuery.Builder. */
    public static final class Builder {
        private final long mBeginTimeMillis;
        private final long mEndTimeMillis;
        private int[] mEventTypes = new int[0];
        private final java.util.LinkedHashSet<String> mPackageNames =
                new java.util.LinkedHashSet<String>();

        /** AOSP Builder(long beginTimeMillis, long endTimeMillis): AOSP rejects
         *  a negative bound or an end before the begin with
         *  IllegalArgumentException. */
        public Builder(long beginTimeMillis, long endTimeMillis) {
            if (beginTimeMillis < 0 || endTimeMillis < beginTimeMillis) {
                throw new IllegalArgumentException("Invalid period start and end timestamp: "
                        + beginTimeMillis + ", " + endTimeMillis);
            }
            mBeginTimeMillis = beginTimeMillis;
            mEndTimeMillis = endTimeMillis;
        }

        /** AOSP setEventTypes(int... eventTypes): AOSP requires at least one and
         *  validates each is a known UsageEvents.Event type. */
        public Builder setEventTypes(int... eventTypes) {
            if (eventTypes == null || eventTypes.length == 0) {
                throw new IllegalArgumentException("Empty event types");
            }
            for (int type : eventTypes) {
                if (type < UsageEvents.Event.NONE || type > UsageEvents.Event.DEVICE_STARTUP) {
                    throw new IllegalArgumentException("Invalid event type: " + type);
                }
            }
            mEventTypes = eventTypes.clone();
            return this;
        }

        /** AOSP setPackageNames(String... packageNames): AOSP requires at least
         *  one non-null name. */
        public Builder setPackageNames(String... packageNames) {
            if (packageNames == null || packageNames.length == 0) {
                throw new IllegalArgumentException("Empty package names");
            }
            mPackageNames.clear();
            for (String name : packageNames) {
                if (name == null) throw new IllegalArgumentException("Null package name");
                mPackageNames.add(name);
            }
            return this;
        }

        public UsageEventsQuery build() { return new UsageEventsQuery(this); }
    }
}
