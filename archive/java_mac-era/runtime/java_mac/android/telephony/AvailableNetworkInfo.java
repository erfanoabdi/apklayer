package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * One candidate network for opportunistic-subscription selection.
 *
 * Faithful immutable value type; Mac.apk has no opportunistic subscriptions to
 * apply it to (updateAvailableNetworks reports
 * UPDATE_AVAILABLE_NETWORKS_NO_OPPORTUNISTIC_SUB_AVAILABLE).
 *
 * Source: frameworks/base/telephony/java/android/telephony/AvailableNetworkInfo.java
 */
public final class AvailableNetworkInfo implements Parcelable {

    public static final int PRIORITY_HIGH = 1;
    public static final int PRIORITY_MED = 2;
    public static final int PRIORITY_LOW = 3;

    private final int mSubId;
    private final int mPriority;
    private final List<String> mMccMncs;
    private final List<RadioAccessSpecifier> mRadioAccessSpecifiers;

    public AvailableNetworkInfo(int subId, int priority, List<String> mccMncs,
            List<Integer> bands) {
        this(subId, priority, mccMncs, bandsToSpecifiers(bands), true);
    }

    /** Distinguished from the public (bands) constructor by a marker arg: the
     *  two List parameters erase to the same signature otherwise. */
    private AvailableNetworkInfo(int subId, int priority, List<String> mccMncs,
            List<RadioAccessSpecifier> radioAccessSpecifiers, boolean unusedTypeMarker) {
        mSubId = subId;
        mPriority = priority;
        mMccMncs = mccMncs == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(mccMncs));
        mRadioAccessSpecifiers = radioAccessSpecifiers == null
                ? Collections.<RadioAccessSpecifier>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(radioAccessSpecifiers));
    }

    private static List<RadioAccessSpecifier> bandsToSpecifiers(List<Integer> bands) {
        // AOSP's deprecated band-list constructor maps the bands onto a single
        // UNKNOWN-RAN specifier; getBands() then reads them back out of it.
        if (bands == null || bands.isEmpty()) return Collections.emptyList();
        int[] arr = new int[bands.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = bands.get(i);
        List<RadioAccessSpecifier> out = new ArrayList<>();
        out.add(new RadioAccessSpecifier(AccessNetworkConstants.AccessNetworkType.UNKNOWN,
                arr, null));
        return out;
    }

    private AvailableNetworkInfo(Parcel in) {
        mSubId = in.readInt();
        mPriority = in.readInt();
        List<String> mccMncs = in.createStringArrayList();
        mMccMncs = mccMncs == null
                ? Collections.<String>emptyList() : Collections.unmodifiableList(mccMncs);
        List<RadioAccessSpecifier> specs =
                in.createTypedArrayList(RadioAccessSpecifier.CREATOR);
        mRadioAccessSpecifiers = specs == null
                ? Collections.<RadioAccessSpecifier>emptyList()
                : Collections.unmodifiableList(specs);
    }

    public int getSubId() { return mSubId; }

    public int getPriority() { return mPriority; }

    public List<String> getMccMncs() { return mMccMncs; }

    public List<RadioAccessSpecifier> getRadioAccessSpecifiers() {
        return mRadioAccessSpecifiers;
    }

    /** @deprecated AOSP replaced the band list by RadioAccessSpecifiers in API 31. */
    @Deprecated public List<Integer> getBands() {
        List<Integer> bands = new ArrayList<>();
        for (RadioAccessSpecifier spec : mRadioAccessSpecifiers) {
            int[] b = spec.getBands();
            if (b == null) continue;
            for (int band : b) bands.add(band);
        }
        return bands;
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mSubId);
        dest.writeInt(mPriority);
        dest.writeStringList(mMccMncs);
        dest.writeTypedList(mRadioAccessSpecifiers);
    }

    public static final Creator<AvailableNetworkInfo> CREATOR =
            new Creator<AvailableNetworkInfo>() {
        @Override public AvailableNetworkInfo createFromParcel(Parcel in) {
            return new AvailableNetworkInfo(in);
        }
        @Override public AvailableNetworkInfo[] newArray(int size) {
            return new AvailableNetworkInfo[size];
        }
    };

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AvailableNetworkInfo)) return false;
        AvailableNetworkInfo ani = (AvailableNetworkInfo) o;
        return mSubId == ani.mSubId && mPriority == ani.mPriority
                && mMccMncs.equals(ani.mMccMncs)
                && mRadioAccessSpecifiers.equals(ani.mRadioAccessSpecifiers);
    }

    @Override public int hashCode() {
        return Objects.hash(mSubId, mPriority, mMccMncs, mRadioAccessSpecifiers);
    }

    @Override public String toString() {
        return "AvailableNetworkInfo{mSubId=" + mSubId + " mPriority=" + mPriority + "}";
    }

    public static final class Builder {
        private final int mSubId;
        private int mPriority = PRIORITY_LOW;
        private List<String> mMccMncs = Collections.emptyList();
        private List<RadioAccessSpecifier> mRadioAccessSpecifiers = Collections.emptyList();

        public Builder(int subId) { mSubId = subId; }

        public Builder setPriority(int priority) {
            if (priority != PRIORITY_HIGH && priority != PRIORITY_MED
                    && priority != PRIORITY_LOW) {
                throw new IllegalArgumentException("A valid priority must be set");
            }
            mPriority = priority;
            return this;
        }

        public Builder setMccMncs(List<String> mccMncs) {
            mMccMncs = new ArrayList<>(Objects.requireNonNull(mccMncs, "mccMncs must not be null"));
            return this;
        }

        public Builder setRadioAccessSpecifiers(List<RadioAccessSpecifier> specs) {
            mRadioAccessSpecifiers = new ArrayList<>(
                    Objects.requireNonNull(specs, "specifiers must not be null"));
            return this;
        }

        public AvailableNetworkInfo build() {
            return new AvailableNetworkInfo(mSubId, mPriority, mMccMncs, mRadioAccessSpecifiers,
                    true);
        }
    }
}
