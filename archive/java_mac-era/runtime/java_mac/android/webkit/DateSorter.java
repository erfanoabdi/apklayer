package android.webkit;

import android.content.Context;

// android.webkit.DateSorter (AOSP API-1).
// Buckets timestamps into "Today / Yesterday / Older" labels for history UIs.
// Fully self-contained (no engine), so this is a real implementation. Ref:
// https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/webkit/DateSorter.java
public class DateSorter {

    public static final int DAY_COUNT = 5;

    private final long[] mBins = new long[DAY_COUNT];
    private final String[] mLabels = new String[DAY_COUNT];

    public DateSorter(Context context) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        beginningOfDay(c);

        // Today
        mBins[0] = c.getTimeInMillis();
        // Yesterday
        c.add(java.util.Calendar.DAY_OF_YEAR, -1);
        mBins[1] = c.getTimeInMillis();
        // Seven days ago
        c.add(java.util.Calendar.DAY_OF_YEAR, -6);
        mBins[2] = c.getTimeInMillis();
        // One month ago
        c.add(java.util.Calendar.DAY_OF_YEAR, -23);
        mBins[3] = c.getTimeInMillis();

        mLabels[0] = "Today";
        mLabels[1] = "Yesterday";
        mLabels[2] = "Last 7 days";
        mLabels[3] = "Last month";
        mLabels[4] = "Older";
    }

    public int getIndex(long time) {
        int lastDay = DAY_COUNT - 1;
        for (int i = 0; i < lastDay; i++) {
            if (time > mBins[i]) return i;
        }
        return lastDay;
    }

    public String getLabel(int index) {
        if (index < 0 || index >= DAY_COUNT) return "";
        return mLabels[index];
    }

    public long getBoundary(int index) {
        int lastDay = DAY_COUNT - 1;
        // Older has no real boundary; AOSP returns the oldest bin.
        if (index < 0) index = 0;
        if (index == lastDay) index = lastDay - 1;
        return mBins[index];
    }

    private void beginningOfDay(java.util.Calendar c) {
        c.set(java.util.Calendar.HOUR_OF_DAY, 0);
        c.set(java.util.Calendar.MINUTE, 0);
        c.set(java.util.Calendar.SECOND, 0);
        c.set(java.util.Calendar.MILLISECOND, 0);
    }
}
