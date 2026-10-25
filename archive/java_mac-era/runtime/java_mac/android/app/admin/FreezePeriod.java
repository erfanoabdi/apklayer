// android.app.admin.FreezePeriod -- "A class that represents one freeze period
// which repeats annually", during which a device owner blocks OTA system
// updates. Part of SystemUpdatePolicy. API 28.
//
// AOSP ref: frameworks/base/core/java/android/app/admin/FreezePeriod.java
// (android16-release). A pure value object over two java.time.MonthDay bounds;
// AOSP's validation (the period may not exceed 90 days) is reproduced because
// it is the one thing a caller can observe without a device owner.
package android.app.admin;

import java.time.MonthDay;

public class FreezePeriod {

    private final MonthDay mStart;
    private final MonthDay mEnd;

    /** AOSP FreezePeriod(MonthDay start, MonthDay end): "Creates a freeze
     *  period. A freeze period starts and ends at midnight." AOSP validates
     *  that the resulting period is at most MAX_LENGTH_DAYS (90) long,
     *  measured on a non-leap year, and throws IllegalArgumentException
     *  otherwise. */
    public FreezePeriod(MonthDay start, MonthDay end) {
        if (start == null || end == null) {
            throw new NullPointerException("start and end must not be null");
        }
        mStart = start;
        mEnd = end;
        // AOSP MAX_LENGTH_DAYS = 90; the day-of-year arithmetic uses a
        // NON-leap reference year (2001) so that Feb 29 does not shift the
        // bounds -- AOSP does the same.
        int startDay = start.atYear(2001).getDayOfYear();
        int endDay = end.atYear(2001).getDayOfYear();
        int length = (endDay >= startDay) ? (endDay - startDay + 1)
                                          : (365 - startDay + endDay + 1);
        if (length > 90) {
            throw new IllegalArgumentException("Freeze period cannot be longer than 90 days");
        }
    }

    /** AOSP getStart(): "the start date (inclusive) of this freeze period." */
    public MonthDay getStart() { return mStart; }

    /** AOSP getEnd(): "the end date (inclusive) of this freeze period." */
    public MonthDay getEnd() { return mEnd; }

    @Override public String toString() { return mStart + " - " + mEnd; }
}
