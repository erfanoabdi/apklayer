// android.app.admin.PreferentialNetworkServiceConfig -- "Network configuration
// to be set for the user profile", describing which uids route over an
// enterprise (preferential) network. API 33.
//
// AOSP ref: frameworks/base/core/java/android/app/admin/
// PreferentialNetworkServiceConfig.java (android16-release).
package android.app.admin;

import android.os.Parcel;
import android.os.Parcelable;

public final class PreferentialNetworkServiceConfig implements Parcelable {

    /** AOSP PREFERENTIAL_NETWORK_ID_*: which enterprise slice this config
     *  targets. */
    public static final int PREFERENTIAL_NETWORK_ID_1 = 1;
    public static final int PREFERENTIAL_NETWORK_ID_2 = 2;
    public static final int PREFERENTIAL_NETWORK_ID_3 = 3;
    public static final int PREFERENTIAL_NETWORK_ID_4 = 4;
    public static final int PREFERENTIAL_NETWORK_ID_5 = 5;

    private final boolean mIsEnabled;
    private final int mNetworkId;
    private final boolean mAllowFallbackToDefaultConnection;
    private final boolean mShouldBlockNonMatchingNetworks;
    private final int[] mIncludedUids;
    private final int[] mExcludedUids;

    private PreferentialNetworkServiceConfig(Builder b) {
        mIsEnabled = b.mIsEnabled;
        mNetworkId = b.mNetworkId;
        mAllowFallbackToDefaultConnection = b.mAllowFallbackToDefaultConnection;
        mShouldBlockNonMatchingNetworks = b.mShouldBlockNonMatchingNetworks;
        mIncludedUids = b.mIncludedUids.clone();
        mExcludedUids = b.mExcludedUids.clone();
    }

    private PreferentialNetworkServiceConfig(Parcel in) {
        mIsEnabled = in.readBoolean();
        mNetworkId = in.readInt();
        mAllowFallbackToDefaultConnection = in.readBoolean();
        mShouldBlockNonMatchingNetworks = in.readBoolean();
        int[] inc = in.createIntArray();
        int[] exc = in.createIntArray();
        mIncludedUids = (inc != null) ? inc : new int[0];
        mExcludedUids = (exc != null) ? exc : new int[0];
    }

    public boolean isEnabled() { return mIsEnabled; }
    public int getNetworkId() { return mNetworkId; }
    /** AOSP isFallbackToDefaultConnectionAllowed(): AOSP's Builder default is
     *  TRUE -- traffic falls back to the default network when the preferential
     *  one is unavailable. */
    public boolean isFallbackToDefaultConnectionAllowed() {
        return mAllowFallbackToDefaultConnection;
    }
    public boolean shouldBlockNonMatchingNetworks() { return mShouldBlockNonMatchingNetworks; }
    /** AOSP getIncludedUids(): never null; an empty array means "all uids". */
    public int[] getIncludedUids() { return mIncludedUids.clone(); }
    /** AOSP getExcludedUids(): never null. */
    public int[] getExcludedUids() { return mExcludedUids.clone(); }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeBoolean(mIsEnabled);
        dest.writeInt(mNetworkId);
        dest.writeBoolean(mAllowFallbackToDefaultConnection);
        dest.writeBoolean(mShouldBlockNonMatchingNetworks);
        dest.writeIntArray(mIncludedUids);
        dest.writeIntArray(mExcludedUids);
    }

    public static final Parcelable.Creator<PreferentialNetworkServiceConfig> CREATOR =
            new Parcelable.Creator<PreferentialNetworkServiceConfig>() {
        @Override public PreferentialNetworkServiceConfig createFromParcel(Parcel in) {
            return new PreferentialNetworkServiceConfig(in);
        }
        @Override public PreferentialNetworkServiceConfig[] newArray(int size) {
            return new PreferentialNetworkServiceConfig[size];
        }
    };

    /** AOSP PreferentialNetworkServiceConfig.Builder. */
    public static final class Builder {
        boolean mIsEnabled;
        int mNetworkId = PREFERENTIAL_NETWORK_ID_1;
        boolean mAllowFallbackToDefaultConnection = true;
        boolean mShouldBlockNonMatchingNetworks;
        int[] mIncludedUids = new int[0];
        int[] mExcludedUids = new int[0];

        public Builder() { }
        public Builder setEnabled(boolean isEnabled) { mIsEnabled = isEnabled; return this; }
        /** AOSP setNetworkId(int): validates the id is one of the five slices. */
        public Builder setNetworkId(int preferenceId) {
            if (preferenceId < PREFERENTIAL_NETWORK_ID_1
                    || preferenceId > PREFERENTIAL_NETWORK_ID_5) {
                throw new IllegalArgumentException("Invalid preference identifier: "
                        + preferenceId);
            }
            mNetworkId = preferenceId;
            return this;
        }
        public Builder setFallbackToDefaultConnectionAllowed(boolean allow) {
            mAllowFallbackToDefaultConnection = allow;
            return this;
        }
        public Builder setShouldBlockNonMatchingNetworks(boolean blockNonMatching) {
            mShouldBlockNonMatchingNetworks = blockNonMatching;
            return this;
        }
        /** AOSP setIncludedUids(int[]): AOSP rejects setting BOTH an included
         *  and an excluded uid list at build() time. */
        public Builder setIncludedUids(int[] uids) {
            if (uids == null) throw new NullPointerException("uids cannot be null");
            mIncludedUids = uids.clone();
            return this;
        }
        public Builder setExcludedUids(int[] uids) {
            if (uids == null) throw new NullPointerException("uids cannot be null");
            mExcludedUids = uids.clone();
            return this;
        }
        public PreferentialNetworkServiceConfig build() {
            if (mIncludedUids.length > 0 && mExcludedUids.length > 0) {
                throw new IllegalStateException(
                        "Both includedUids and excludedUids cannot be nonempty");
            }
            return new PreferentialNetworkServiceConfig(this);
        }
    }
}
