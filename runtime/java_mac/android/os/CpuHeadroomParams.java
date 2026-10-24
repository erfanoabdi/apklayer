// android.os.CpuHeadroomParams (API 36) -- the query parameters for
// SystemHealthManager.getCpuHeadroom(): which calculation to run, over what
// window, and (optionally) for which threads.
//
// AOSP ref: frameworks/base/core/java/android/os/CpuHeadroomParams.java
// (android16-release). Ported verbatim apart from the storage: AOSP keeps the
// values in a `CpuHeadroomParamsInternal` AIDL parcelable so they can be sent
// to the power HAL. That type is @hide, not part of the API surface, and there
// is no HAL on this host, so the same four values live in plain fields here.
// The DEFAULTS are the AIDL's own defaults
// (core/java/android/os/CpuHeadroomParamsInternal.aidl):
//     int calculationWindowMillis = 1000;
//     CalculationType calculationType = MIN;   // == 0
// which matters, because getCalculationWindowMillis() on a freshly built
// params object must report 1000 and not 0.
package android.os;

import java.util.Arrays;

public final class CpuHeadroomParams {
    public static final int CPU_HEADROOM_CALCULATION_TYPE_MIN = 0;
    public static final int CPU_HEADROOM_CALCULATION_TYPE_AVERAGE = 1;

    private int mCalculationType = CPU_HEADROOM_CALCULATION_TYPE_MIN;
    private int mCalculationWindowMillis = 1000;
    private int[] mTids;

    private CpuHeadroomParams() { }

    public static final class Builder {
        private int mCalculationType = -1;
        private int mCalculationWindowMillis = -1;
        private int[] mTids = null;

        public Builder() { }

        public Builder(CpuHeadroomParams params) {
            if (params.mCalculationType >= 0) {
                mCalculationType = params.mCalculationType;
            }
            if (params.mCalculationWindowMillis >= 0) {
                mCalculationWindowMillis = params.mCalculationWindowMillis;
            }
            if (params.mTids != null) {
                mTids = Arrays.copyOf(params.mTids, params.mTids.length);
            }
        }

        public Builder setCalculationType(int calculationType) {
            switch (calculationType) {
                case CPU_HEADROOM_CALCULATION_TYPE_MIN:
                case CPU_HEADROOM_CALCULATION_TYPE_AVERAGE:
                    mCalculationType = calculationType;
                    return this;
                default:
                    break;
            }
            throw new IllegalArgumentException("Invalid calculation type: " + calculationType);
        }

        public Builder setCalculationWindowMillis(int windowMillis) {
            if (windowMillis <= 0) {
                throw new IllegalArgumentException("Invalid calculation window: " + windowMillis);
            }
            mCalculationWindowMillis = windowMillis;
            return this;
        }

        public Builder setTids(int... tids) {
            for (int tid : tids) {
                if (tid <= 0) {
                    throw new IllegalArgumentException("Invalid TID: " + tid);
                }
            }
            mTids = tids;
            return this;
        }

        public CpuHeadroomParams build() {
            CpuHeadroomParams params = new CpuHeadroomParams();
            if (mCalculationType >= 0) {
                params.mCalculationType = mCalculationType;
            }
            if (mCalculationWindowMillis >= 0) {
                params.mCalculationWindowMillis = mCalculationWindowMillis;
            }
            if (mTids != null) {
                params.mTids = mTids;
            }
            return params;
        }
    }

    public Builder toBuilder() { return new Builder(this); }

    /** AOSP validates on the way OUT, falling back to MIN for anything the
     *  HAL may have left unset. */
    public int getCalculationType() {
        switch (mCalculationType) {
            case CPU_HEADROOM_CALCULATION_TYPE_MIN:
            case CPU_HEADROOM_CALCULATION_TYPE_AVERAGE:
                return mCalculationType;
            default:
                return CPU_HEADROOM_CALCULATION_TYPE_MIN;
        }
    }

    public long getCalculationWindowMillis() { return mCalculationWindowMillis; }

    /** AOSP returns null (despite the @NonNull) when no tids were set. */
    public int[] getTids() {
        return mTids == null ? null : Arrays.copyOf(mTids, mTids.length);
    }

    @Override public String toString() {
        return "CpuHeadroomParams{"
                + "calculationType=" + mCalculationType
                + ", calculationWindowMillis=" + mCalculationWindowMillis
                + ", tids=" + Arrays.toString(mTids)
                + '}';
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CpuHeadroomParams that = (CpuHeadroomParams) o;
        return mCalculationType == that.mCalculationType
                && mCalculationWindowMillis == that.mCalculationWindowMillis
                && Arrays.equals(mTids, that.mTids);
    }

    @Override public int hashCode() {
        return 31 * (31 * mCalculationType + mCalculationWindowMillis) + Arrays.hashCode(mTids);
    }
}
