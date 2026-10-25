// android.os.GpuHeadroomParams (API 36) -- query parameters for
// SystemHealthManager.getGpuHeadroom().
//
// AOSP ref: frameworks/base/core/java/android/os/GpuHeadroomParams.java
// (android16-release). Same porting note as CpuHeadroomParams: AOSP's storage
// is the @hide GpuHeadroomParamsInternal AIDL parcelable, whose declared
// defaults (core/java/android/os/GpuHeadroomParamsInternal.aidl) are
// calculationWindowMillis = 1000 and calculationType = MIN. Note this class
// has NO toBuilder() -- the CPU one does; that asymmetry is AOSP's.
package android.os;

public final class GpuHeadroomParams {
    public static final int GPU_HEADROOM_CALCULATION_TYPE_MIN = 0;
    public static final int GPU_HEADROOM_CALCULATION_TYPE_AVERAGE = 1;
    public static final int GPU_HEADROOM_CALCULATION_WINDOW_MILLIS_MIN = 50;
    public static final int GPU_HEADROOM_CALCULATION_WINDOW_MILLIS_MAX = 10000;

    private int mCalculationType = GPU_HEADROOM_CALCULATION_TYPE_MIN;
    private int mCalculationWindowMillis = 1000;

    private GpuHeadroomParams() { }

    public static final class Builder {
        private int mCalculationType = -1;
        private int mCalculationWindowMillis = -1;

        public Builder() { }

        public Builder(GpuHeadroomParams params) {
            if (params.mCalculationType >= 0) {
                mCalculationType = params.mCalculationType;
            }
            if (params.mCalculationWindowMillis >= 0) {
                mCalculationWindowMillis = params.mCalculationWindowMillis;
            }
        }

        public Builder setCalculationType(int calculationType) {
            switch (calculationType) {
                case GPU_HEADROOM_CALCULATION_TYPE_MIN:
                case GPU_HEADROOM_CALCULATION_TYPE_AVERAGE:
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

        public GpuHeadroomParams build() {
            GpuHeadroomParams params = new GpuHeadroomParams();
            if (mCalculationType >= 0) {
                params.mCalculationType = mCalculationType;
            }
            if (mCalculationWindowMillis >= 0) {
                params.mCalculationWindowMillis = mCalculationWindowMillis;
            }
            return params;
        }
    }

    public int getCalculationType() {
        switch (mCalculationType) {
            case GPU_HEADROOM_CALCULATION_TYPE_MIN:
            case GPU_HEADROOM_CALCULATION_TYPE_AVERAGE:
                return mCalculationType;
            default:
                return GPU_HEADROOM_CALCULATION_TYPE_MIN;
        }
    }

    public int getCalculationWindowMillis() { return mCalculationWindowMillis; }

    @Override public String toString() {
        return "GpuHeadroomParams{"
                + "calculationType=" + mCalculationType
                + ", calculationWindowMillis=" + mCalculationWindowMillis
                + '}';
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GpuHeadroomParams that = (GpuHeadroomParams) o;
        return mCalculationType == that.mCalculationType
                && mCalculationWindowMillis == that.mCalculationWindowMillis;
    }

    @Override public int hashCode() {
        return 31 * mCalculationType + mCalculationWindowMillis;
    }
}
