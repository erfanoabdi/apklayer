package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Range;

import java.time.Period;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * A carrier data plan (billing cycle, data cap, usage).
 *
 * Mac.apk has no carrier and no subscription, so
 * SubscriptionManager.getSubscriptionPlans() is empty. The value type and its
 * cycle iterator are pure date arithmetic and are faithful, so an app that
 * builds a plan for its own bookkeeping behaves identically.
 *
 * Source: frameworks/base/telephony/java/android/telephony/SubscriptionPlan.java
 */
public final class SubscriptionPlan implements Parcelable {

    public static final long BYTES_UNKNOWN = -1;
    public static final long BYTES_UNLIMITED = Long.MAX_VALUE;
    public static final long TIME_UNKNOWN = -1;

    public static final int LIMIT_BEHAVIOR_UNKNOWN = -1;
    public static final int LIMIT_BEHAVIOR_DISABLED = 0;
    public static final int LIMIT_BEHAVIOR_BILLED = 1;
    public static final int LIMIT_BEHAVIOR_THROTTLED = 2;

    public static final int SUBSCRIPTION_STATUS_UNKNOWN = 0;
    public static final int SUBSCRIPTION_STATUS_ACTIVE = 1;
    public static final int SUBSCRIPTION_STATUS_INACTIVE = 2;
    public static final int SUBSCRIPTION_STATUS_TRIAL = 3;
    public static final int SUBSCRIPTION_STATUS_SUSPENDED = 4;

    private final ZonedDateTime mCycleStart;
    private final ZonedDateTime mCycleEnd;
    private final Period mCyclePeriod;
    private final CharSequence mTitle;
    private final CharSequence mSummary;
    private final long mDataLimitBytes;
    private final int mDataLimitBehavior;
    private final long mDataUsageBytes;
    private final long mDataUsageTime;
    private final int[] mNetworkTypes;
    private final int mSubscriptionStatus;

    private SubscriptionPlan(Builder b) {
        mCycleStart = b.mCycleStart;
        mCycleEnd = b.mCycleEnd;
        mCyclePeriod = b.mCyclePeriod;
        mTitle = b.mTitle;
        mSummary = b.mSummary;
        mDataLimitBytes = b.mDataLimitBytes;
        mDataLimitBehavior = b.mDataLimitBehavior;
        mDataUsageBytes = b.mDataUsageBytes;
        mDataUsageTime = b.mDataUsageTime;
        mNetworkTypes = b.mNetworkTypes;
        mSubscriptionStatus = b.mSubscriptionStatus;
    }

    public CharSequence getTitle() { return mTitle; }
    public CharSequence getSummary() { return mSummary; }
    public long getDataLimitBytes() { return mDataLimitBytes; }
    public int getDataLimitBehavior() { return mDataLimitBehavior; }
    public long getDataUsageBytes() { return mDataUsageBytes; }
    public long getDataUsageTime() { return mDataUsageTime; }
    public int[] getNetworkTypes() { return mNetworkTypes.clone(); }
    public int getSubscriptionStatus() { return mSubscriptionStatus; }
    public ZonedDateTime getPlanEndDate() { return mCycleEnd; }

    /**
     * AOSP walks the billing cycle backwards from the most recent boundary.
     * Recurring plans step by mCyclePeriod; non-recurring plans yield the one
     * [start, end) range.
     */
    public Iterator<Range<ZonedDateTime>> cycleIterator() {
        final List<Range<ZonedDateTime>> ranges = new ArrayList<>();
        if (mCyclePeriod == null) {
            ranges.add(new Range<>(mCycleStart, mCycleEnd));
        } else {
            // Bounded so a caller that drains the iterator cannot spin forever;
            // AOSP's own iterator stops once it walks past the epoch.
            ZonedDateTime cursor = mCycleStart;
            for (int i = 0; i < 120; i++) {
                ZonedDateTime next = cursor.plus(mCyclePeriod);
                ranges.add(new Range<>(cursor, next));
                cursor = next;
            }
        }
        return ranges.iterator();
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mCycleStart == null ? null : mCycleStart.toString());
        dest.writeString(mCycleEnd == null ? null : mCycleEnd.toString());
        dest.writeString(mCyclePeriod == null ? null : mCyclePeriod.toString());
        dest.writeString(mTitle == null ? null : mTitle.toString());
        dest.writeString(mSummary == null ? null : mSummary.toString());
        dest.writeLong(mDataLimitBytes);
        dest.writeInt(mDataLimitBehavior);
        dest.writeLong(mDataUsageBytes);
        dest.writeLong(mDataUsageTime);
        dest.writeIntArray(mNetworkTypes);
        dest.writeInt(mSubscriptionStatus);
    }

    private SubscriptionPlan(Parcel in) {
        String cs = in.readString();
        mCycleStart = cs == null ? null : ZonedDateTime.parse(cs);
        String ce = in.readString();
        mCycleEnd = ce == null ? null : ZonedDateTime.parse(ce);
        String cp = in.readString();
        mCyclePeriod = cp == null ? null : Period.parse(cp);
        mTitle = in.readString();
        mSummary = in.readString();
        mDataLimitBytes = in.readLong();
        mDataLimitBehavior = in.readInt();
        mDataUsageBytes = in.readLong();
        mDataUsageTime = in.readLong();
        int[] nt = in.createIntArray();
        mNetworkTypes = nt == null ? new int[0] : nt;
        mSubscriptionStatus = in.readInt();
    }

    public static final Creator<SubscriptionPlan> CREATOR = new Creator<SubscriptionPlan>() {
        @Override public SubscriptionPlan createFromParcel(Parcel in) {
            return new SubscriptionPlan(in);
        }
        @Override public SubscriptionPlan[] newArray(int size) { return new SubscriptionPlan[size]; }
    };

    @Override public int hashCode() {
        return Objects.hash(mCycleStart, mCycleEnd, mCyclePeriod, mTitle, mSummary,
                mDataLimitBytes, mDataLimitBehavior, mDataUsageBytes, mDataUsageTime,
                Arrays.hashCode(mNetworkTypes), mSubscriptionStatus);
    }

    @Override public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof SubscriptionPlan)) return false;
        SubscriptionPlan other = (SubscriptionPlan) obj;
        return Objects.equals(mCycleStart, other.mCycleStart)
                && Objects.equals(mCycleEnd, other.mCycleEnd)
                && Objects.equals(mCyclePeriod, other.mCyclePeriod)
                && mDataLimitBytes == other.mDataLimitBytes
                && mDataLimitBehavior == other.mDataLimitBehavior
                && mDataUsageBytes == other.mDataUsageBytes
                && mDataUsageTime == other.mDataUsageTime
                && Arrays.equals(mNetworkTypes, other.mNetworkTypes)
                && mSubscriptionStatus == other.mSubscriptionStatus;
    }

    @Override public String toString() {
        return "SubscriptionPlan{title=" + mTitle + " limit=" + mDataLimitBytes + "}";
    }

    /** AOSP declares Builder as a plain (non-final) static class with static factories. */
    public static class Builder {
        private ZonedDateTime mCycleStart;
        private ZonedDateTime mCycleEnd;
        private Period mCyclePeriod;
        private CharSequence mTitle;
        private CharSequence mSummary;
        private long mDataLimitBytes = BYTES_UNKNOWN;
        private int mDataLimitBehavior = LIMIT_BEHAVIOR_UNKNOWN;
        private long mDataUsageBytes = BYTES_UNKNOWN;
        private long mDataUsageTime = TIME_UNKNOWN;
        private int[] mNetworkTypes = new int[0];
        private int mSubscriptionStatus = SUBSCRIPTION_STATUS_UNKNOWN;

        private Builder(ZonedDateTime start, ZonedDateTime end, Period period) {
            mCycleStart = start;
            mCycleEnd = end;
            mCyclePeriod = period;
        }

        public static Builder createNonrecurring(ZonedDateTime start, ZonedDateTime end) {
            if (start != null && end != null && !end.isAfter(start)) {
                throw new IllegalArgumentException("Start " + start + " must be before end " + end);
            }
            return new Builder(start, end, null);
        }

        public static Builder createRecurring(ZonedDateTime start, Period period) {
            if (period != null && period.isZero() || period != null && period.isNegative()) {
                throw new IllegalArgumentException("Period " + period + " must be positive");
            }
            return new Builder(start, null, period);
        }

        public Builder setTitle(CharSequence title) { mTitle = title; return this; }
        public Builder setSummary(CharSequence summary) { mSummary = summary; return this; }

        public Builder setDataLimit(long dataLimitBytes, int dataLimitBehavior) {
            if (dataLimitBytes < 0) {
                throw new IllegalArgumentException("Limit bytes must be positive");
            }
            mDataLimitBytes = dataLimitBytes;
            mDataLimitBehavior = dataLimitBehavior;
            return this;
        }

        public Builder setDataUsage(long dataUsageBytes, long dataUsageTime) {
            if (dataUsageBytes < 0) {
                throw new IllegalArgumentException("Usage bytes must be positive");
            }
            if (dataUsageTime < 0) {
                throw new IllegalArgumentException("Usage time must be positive");
            }
            mDataUsageBytes = dataUsageBytes;
            mDataUsageTime = dataUsageTime;
            return this;
        }

        public Builder setNetworkTypes(int[] networkTypes) {
            mNetworkTypes = Objects.requireNonNull(networkTypes).clone();
            return this;
        }

        public Builder resetNetworkTypes() {
            mNetworkTypes = new int[0];
            return this;
        }

        public Builder setSubscriptionStatus(int subscriptionStatus) {
            mSubscriptionStatus = subscriptionStatus;
            return this;
        }

        public SubscriptionPlan build() { return new SubscriptionPlan(this); }
    }
}
