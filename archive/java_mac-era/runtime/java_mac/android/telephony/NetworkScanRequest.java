package android.telephony;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

/**
 * Parameters for a manual network scan.
 *
 * Faithful value type. Mac.apk cannot run the scan (no radio), and reports
 * NetworkScan.ERROR_UNSUPPORTED rather than an empty successful result -- an
 * empty success would tell the app "there are no networks here", which is a
 * different and false claim.
 *
 * Source: frameworks/base/telephony/java/android/telephony/NetworkScanRequest.java
 */
public final class NetworkScanRequest implements Parcelable {

    public static final int SCAN_TYPE_ONE_SHOT = 0;
    public static final int SCAN_TYPE_PERIODIC = 1;

    private final int mScanType;
    private final RadioAccessSpecifier[] mSpecifiers;
    private final int mSearchPeriodicity;
    private final int mMaxSearchTime;
    private final boolean mIncrementalResults;
    private final int mIncrementalResultsPeriodicity;
    private final ArrayList<String> mMccMncs;

    public NetworkScanRequest(int scanType, RadioAccessSpecifier[] specifiers,
            int searchPeriodicity, int maxSearchTime, boolean incrementalResults,
            int incrementalResultsPeriodicity, ArrayList<String> mccMncs) {
        mScanType = scanType;
        mSpecifiers = specifiers == null ? null : specifiers.clone();
        mSearchPeriodicity = searchPeriodicity;
        mMaxSearchTime = maxSearchTime;
        mIncrementalResults = incrementalResults;
        mIncrementalResultsPeriodicity = incrementalResultsPeriodicity;
        mMccMncs = mccMncs == null ? new ArrayList<String>() : new ArrayList<>(mccMncs);
    }

    private NetworkScanRequest(Parcel in) {
        mScanType = in.readInt();
        int len = in.readInt();
        if (len < 0) {
            mSpecifiers = null;
        } else {
            mSpecifiers = new RadioAccessSpecifier[len];
            for (int i = 0; i < len; i++) {
                mSpecifiers[i] = RadioAccessSpecifier.CREATOR.createFromParcel(in);
            }
        }
        mSearchPeriodicity = in.readInt();
        mMaxSearchTime = in.readInt();
        mIncrementalResults = in.readBoolean();
        mIncrementalResultsPeriodicity = in.readInt();
        ArrayList<String> mccMncs = in.createStringArrayList();
        mMccMncs = mccMncs == null ? new ArrayList<String>() : mccMncs;
    }

    public int getScanType() { return mScanType; }
    public RadioAccessSpecifier[] getSpecifiers() {
        return mSpecifiers == null ? null : mSpecifiers.clone();
    }
    public int getSearchPeriodicity() { return mSearchPeriodicity; }
    public int getMaxSearchTime() { return mMaxSearchTime; }
    public boolean getIncrementalResults() { return mIncrementalResults; }
    public int getIncrementalResultsPeriodicity() { return mIncrementalResultsPeriodicity; }
    public ArrayList<String> getPlmns() { return new ArrayList<>(mMccMncs); }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mScanType);
        if (mSpecifiers == null) {
            dest.writeInt(-1);
        } else {
            dest.writeInt(mSpecifiers.length);
            for (RadioAccessSpecifier s : mSpecifiers) s.writeToParcel(dest, flags);
        }
        dest.writeInt(mSearchPeriodicity);
        dest.writeInt(mMaxSearchTime);
        dest.writeBoolean(mIncrementalResults);
        dest.writeInt(mIncrementalResultsPeriodicity);
        dest.writeStringList(mMccMncs);
    }

    public static final Creator<NetworkScanRequest> CREATOR = new Creator<NetworkScanRequest>() {
        @Override public NetworkScanRequest createFromParcel(Parcel in) {
            return new NetworkScanRequest(in);
        }
        @Override public NetworkScanRequest[] newArray(int size) {
            return new NetworkScanRequest[size];
        }
    };

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NetworkScanRequest)) return false;
        NetworkScanRequest nsr = (NetworkScanRequest) o;
        return mScanType == nsr.mScanType && Arrays.equals(mSpecifiers, nsr.mSpecifiers)
                && mSearchPeriodicity == nsr.mSearchPeriodicity
                && mMaxSearchTime == nsr.mMaxSearchTime
                && mIncrementalResults == nsr.mIncrementalResults
                && mIncrementalResultsPeriodicity == nsr.mIncrementalResultsPeriodicity
                && mMccMncs.equals(nsr.mMccMncs);
    }

    @Override public int hashCode() {
        return Objects.hash(mScanType, Arrays.hashCode(mSpecifiers), mSearchPeriodicity,
                mMaxSearchTime, mIncrementalResults, mIncrementalResultsPeriodicity, mMccMncs);
    }
}
