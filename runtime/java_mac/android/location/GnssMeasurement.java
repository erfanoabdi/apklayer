package android.location;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * One raw GNSS measurement.
 *
 * A Mac has no GNSS receiver, so the framework never produces one of these (see
 * GnssMeasurementsEvent, whose callback reports STATUS_NOT_SUPPORTED). The class
 * is nevertheless a faithful data holder: AOSP's contract is a flags word plus
 * per-field has/get/set/reset triples, and a default-constructed measurement
 * reports every optional field ABSENT with the documented sentinel value -- the
 * same thing a real device reports for a field its chipset does not populate.
 *
 * The HAS_* bit positions come from the GNSS HAL, not from this file: AOSP
 * static-imports most of them from
 * android.hardware.gnss.V2_1.IGnssMeasurementCallback.GnssMeasurementFlags.
 * Values verified against
 * hardware/interfaces/gnss/1.0/IGnssMeasurementCallback.hal (HAS_SNR 1<<0,
 * HAS_CARRIER_FREQUENCY 1<<9, HAS_CARRIER_CYCLES 1<<10, HAS_CARRIER_PHASE 1<<11,
 * HAS_CARRIER_PHASE_UNCERTAINTY 1<<12, HAS_AUTOMATIC_GAIN_CONTROL 1<<13) and
 * hardware/interfaces/gnss/2.1/IGnssMeasurementCallback.hal (HAS_FULL_ISB 1<<16,
 * HAS_FULL_ISB_UNCERTAINTY 1<<17, HAS_SATELLITE_ISB 1<<18,
 * HAS_SATELLITE_ISB_UNCERTAINTY 1<<19). They are load-bearing because they are
 * what writeToParcel round-trips.
 *
 * Source: frameworks/base/location/java/android/location/GnssMeasurement.java
 * https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android16-release/location/java/android/location/GnssMeasurement.java
 */
public final class GnssMeasurement implements Parcelable {

    private int mFlags;
    private int mSvid;
    private int mConstellationType;
    private double mTimeOffsetNanos;
    private int mState;
    private long mReceivedSvTimeNanos;
    private long mReceivedSvTimeUncertaintyNanos;
    private double mCn0DbHz;
    private double mBasebandCn0DbHz;
    private double mPseudorangeRateMetersPerSecond;
    private double mPseudorangeRateUncertaintyMetersPerSecond;
    private int mAccumulatedDeltaRangeState;
    private double mAccumulatedDeltaRangeMeters;
    private double mAccumulatedDeltaRangeUncertaintyMeters;
    private float mCarrierFrequencyHz;
    private long mCarrierCycles;
    private double mCarrierPhase;
    private double mCarrierPhaseUncertainty;
    private int mMultipathIndicator;
    private double mSnrInDb;
    private double mAutomaticGainControlLevelInDb;
    private String mCodeType;
    private double mFullInterSignalBiasNanos;
    private double mFullInterSignalBiasUncertaintyNanos;
    private double mSatelliteInterSignalBiasNanos;
    private double mSatelliteInterSignalBiasUncertaintyNanos;

    // Must stay in sync with the GNSS HAL; see the class comment.
    private static final int HAS_NO_FLAGS = 0;
    private static final int HAS_SNR = (1 << 0);
    private static final int HAS_CARRIER_FREQUENCY = (1 << 9);
    private static final int HAS_CARRIER_CYCLES = (1 << 10);
    private static final int HAS_CARRIER_PHASE = (1 << 11);
    private static final int HAS_CARRIER_PHASE_UNCERTAINTY = (1 << 12);
    private static final int HAS_AUTOMATIC_GAIN_CONTROL = (1 << 13);
    private static final int HAS_CODE_TYPE = (1 << 14);
    private static final int HAS_BASEBAND_CN0 = (1 << 15);
    private static final int HAS_FULL_ISB = (1 << 16);
    private static final int HAS_FULL_ISB_UNCERTAINTY = (1 << 17);
    private static final int HAS_SATELLITE_ISB = (1 << 18);
    private static final int HAS_SATELLITE_ISB_UNCERTAINTY = (1 << 19);

    public static final int MULTIPATH_INDICATOR_UNKNOWN = 0;
    public static final int MULTIPATH_INDICATOR_DETECTED = 1;
    public static final int MULTIPATH_INDICATOR_NOT_DETECTED = 2;

    public static final int STATE_UNKNOWN = 0;
    public static final int STATE_CODE_LOCK = (1 << 0);
    public static final int STATE_BIT_SYNC = (1 << 1);
    public static final int STATE_SUBFRAME_SYNC = (1 << 2);
    public static final int STATE_TOW_DECODED = (1 << 3);
    public static final int STATE_MSEC_AMBIGUOUS = (1 << 4);
    public static final int STATE_SYMBOL_SYNC = (1 << 5);
    public static final int STATE_GLO_STRING_SYNC = (1 << 6);
    public static final int STATE_GLO_TOD_DECODED = (1 << 7);
    public static final int STATE_BDS_D2_BIT_SYNC = (1 << 8);
    public static final int STATE_BDS_D2_SUBFRAME_SYNC = (1 << 9);
    public static final int STATE_GAL_E1BC_CODE_LOCK = (1 << 10);
    public static final int STATE_GAL_E1C_2ND_CODE_LOCK = (1 << 11);
    public static final int STATE_GAL_E1B_PAGE_SYNC = (1 << 12);
    public static final int STATE_SBAS_SYNC = (1 << 13);
    public static final int STATE_TOW_KNOWN = (1 << 14);
    public static final int STATE_GLO_TOD_KNOWN = (1 << 15);
    public static final int STATE_2ND_CODE_LOCK = (1 << 16);

    public static final int ADR_STATE_UNKNOWN = 0;
    public static final int ADR_STATE_VALID = (1 << 0);
    public static final int ADR_STATE_RESET = (1 << 1);
    public static final int ADR_STATE_CYCLE_SLIP = (1 << 2);
    public static final int ADR_STATE_HALF_CYCLE_RESOLVED = (1 << 3);
    public static final int ADR_STATE_HALF_CYCLE_REPORTED = (1 << 4);

    /** AOSP marks this @hide @TestApi. */
    public GnssMeasurement() { initialize(); }

    public void set(GnssMeasurement measurement) {
        mFlags = measurement.mFlags;
        mSvid = measurement.mSvid;
        mConstellationType = measurement.mConstellationType;
        mTimeOffsetNanos = measurement.mTimeOffsetNanos;
        mState = measurement.mState;
        mReceivedSvTimeNanos = measurement.mReceivedSvTimeNanos;
        mReceivedSvTimeUncertaintyNanos = measurement.mReceivedSvTimeUncertaintyNanos;
        mCn0DbHz = measurement.mCn0DbHz;
        mBasebandCn0DbHz = measurement.mBasebandCn0DbHz;
        mPseudorangeRateMetersPerSecond = measurement.mPseudorangeRateMetersPerSecond;
        mPseudorangeRateUncertaintyMetersPerSecond =
                measurement.mPseudorangeRateUncertaintyMetersPerSecond;
        mAccumulatedDeltaRangeState = measurement.mAccumulatedDeltaRangeState;
        mAccumulatedDeltaRangeMeters = measurement.mAccumulatedDeltaRangeMeters;
        mAccumulatedDeltaRangeUncertaintyMeters =
                measurement.mAccumulatedDeltaRangeUncertaintyMeters;
        mCarrierFrequencyHz = measurement.mCarrierFrequencyHz;
        mCarrierCycles = measurement.mCarrierCycles;
        mCarrierPhase = measurement.mCarrierPhase;
        mCarrierPhaseUncertainty = measurement.mCarrierPhaseUncertainty;
        mMultipathIndicator = measurement.mMultipathIndicator;
        mSnrInDb = measurement.mSnrInDb;
        mAutomaticGainControlLevelInDb = measurement.mAutomaticGainControlLevelInDb;
        mCodeType = measurement.mCodeType;
        mFullInterSignalBiasNanos = measurement.mFullInterSignalBiasNanos;
        mFullInterSignalBiasUncertaintyNanos = measurement.mFullInterSignalBiasUncertaintyNanos;
        mSatelliteInterSignalBiasNanos = measurement.mSatelliteInterSignalBiasNanos;
        mSatelliteInterSignalBiasUncertaintyNanos =
                measurement.mSatelliteInterSignalBiasUncertaintyNanos;
    }

    public void reset() { initialize(); }

    public int getSvid() { return mSvid; }
    public void setSvid(int value) { mSvid = value; }

    public int getConstellationType() { return mConstellationType; }
    public void setConstellationType(int value) { mConstellationType = value; }

    public double getTimeOffsetNanos() { return mTimeOffsetNanos; }
    public void setTimeOffsetNanos(double value) { mTimeOffsetNanos = value; }

    public int getState() { return mState; }
    public void setState(int value) { mState = value; }

    public long getReceivedSvTimeNanos() { return mReceivedSvTimeNanos; }
    public void setReceivedSvTimeNanos(long value) { mReceivedSvTimeNanos = value; }

    public long getReceivedSvTimeUncertaintyNanos() { return mReceivedSvTimeUncertaintyNanos; }
    public void setReceivedSvTimeUncertaintyNanos(long value) {
        mReceivedSvTimeUncertaintyNanos = value;
    }

    public double getCn0DbHz() { return mCn0DbHz; }
    public void setCn0DbHz(double value) { mCn0DbHz = value; }

    public boolean hasBasebandCn0DbHz() { return isFlagSet(HAS_BASEBAND_CN0); }
    public double getBasebandCn0DbHz() { return mBasebandCn0DbHz; }
    public void setBasebandCn0DbHz(double value) {
        setFlag(HAS_BASEBAND_CN0);
        mBasebandCn0DbHz = value;
    }
    public void resetBasebandCn0DbHz() { resetFlag(HAS_BASEBAND_CN0); }

    public double getPseudorangeRateMetersPerSecond() { return mPseudorangeRateMetersPerSecond; }
    public void setPseudorangeRateMetersPerSecond(double value) {
        mPseudorangeRateMetersPerSecond = value;
    }

    public double getPseudorangeRateUncertaintyMetersPerSecond() {
        return mPseudorangeRateUncertaintyMetersPerSecond;
    }
    public void setPseudorangeRateUncertaintyMetersPerSecond(double value) {
        mPseudorangeRateUncertaintyMetersPerSecond = value;
    }

    public int getAccumulatedDeltaRangeState() { return mAccumulatedDeltaRangeState; }
    public void setAccumulatedDeltaRangeState(int value) { mAccumulatedDeltaRangeState = value; }

    public double getAccumulatedDeltaRangeMeters() { return mAccumulatedDeltaRangeMeters; }
    public void setAccumulatedDeltaRangeMeters(double value) {
        mAccumulatedDeltaRangeMeters = value;
    }

    public double getAccumulatedDeltaRangeUncertaintyMeters() {
        return mAccumulatedDeltaRangeUncertaintyMeters;
    }
    public void setAccumulatedDeltaRangeUncertaintyMeters(double value) {
        mAccumulatedDeltaRangeUncertaintyMeters = value;
    }

    public boolean hasCarrierFrequencyHz() { return isFlagSet(HAS_CARRIER_FREQUENCY); }
    public float getCarrierFrequencyHz() { return mCarrierFrequencyHz; }
    public void setCarrierFrequencyHz(float carrierFrequencyHz) {
        setFlag(HAS_CARRIER_FREQUENCY);
        mCarrierFrequencyHz = carrierFrequencyHz;
    }
    public void resetCarrierFrequencyHz() {
        resetFlag(HAS_CARRIER_FREQUENCY);
        mCarrierFrequencyHz = Float.NaN;
    }

    /** @deprecated AOSP deprecated this in API 30; no chipset reports it. */
    @Deprecated public boolean hasCarrierCycles() { return isFlagSet(HAS_CARRIER_CYCLES); }
    /** @deprecated see {@link #hasCarrierCycles()}. */
    @Deprecated public long getCarrierCycles() { return mCarrierCycles; }
    @Deprecated public void setCarrierCycles(long value) {
        setFlag(HAS_CARRIER_CYCLES);
        mCarrierCycles = value;
    }
    @Deprecated public void resetCarrierCycles() {
        resetFlag(HAS_CARRIER_CYCLES);
        mCarrierCycles = Long.MIN_VALUE;
    }

    /** @deprecated AOSP deprecated this in API 30. */
    @Deprecated public boolean hasCarrierPhase() { return isFlagSet(HAS_CARRIER_PHASE); }
    /** @deprecated see {@link #hasCarrierPhase()}. */
    @Deprecated public double getCarrierPhase() { return mCarrierPhase; }
    @Deprecated public void setCarrierPhase(double value) {
        setFlag(HAS_CARRIER_PHASE);
        mCarrierPhase = value;
    }
    @Deprecated public void resetCarrierPhase() { resetFlag(HAS_CARRIER_PHASE); }

    /** @deprecated AOSP deprecated this in API 30. */
    @Deprecated public boolean hasCarrierPhaseUncertainty() {
        return isFlagSet(HAS_CARRIER_PHASE_UNCERTAINTY);
    }
    /** @deprecated see {@link #hasCarrierPhaseUncertainty()}. */
    @Deprecated public double getCarrierPhaseUncertainty() { return mCarrierPhaseUncertainty; }
    @Deprecated public void setCarrierPhaseUncertainty(double value) {
        setFlag(HAS_CARRIER_PHASE_UNCERTAINTY);
        mCarrierPhaseUncertainty = value;
    }
    @Deprecated public void resetCarrierPhaseUncertainty() {
        resetFlag(HAS_CARRIER_PHASE_UNCERTAINTY);
    }

    public int getMultipathIndicator() { return mMultipathIndicator; }
    public void setMultipathIndicator(int value) { mMultipathIndicator = value; }

    public boolean hasSnrInDb() { return isFlagSet(HAS_SNR); }
    public double getSnrInDb() { return mSnrInDb; }
    public void setSnrInDb(double snrInDb) {
        setFlag(HAS_SNR);
        mSnrInDb = snrInDb;
    }
    public void resetSnrInDb() { resetFlag(HAS_SNR); }

    /** @deprecated AOSP moved AGC reporting to GnssMeasurementsEvent in API 34. */
    @Deprecated public boolean hasAutomaticGainControlLevelDb() {
        return isFlagSet(HAS_AUTOMATIC_GAIN_CONTROL);
    }
    /** @deprecated see {@link #hasAutomaticGainControlLevelDb()}. */
    @Deprecated public double getAutomaticGainControlLevelDb() {
        return mAutomaticGainControlLevelInDb;
    }
    @Deprecated public void setAutomaticGainControlLevelInDb(double value) {
        setFlag(HAS_AUTOMATIC_GAIN_CONTROL);
        mAutomaticGainControlLevelInDb = value;
    }
    @Deprecated public void resetAutomaticGainControlLevel() {
        resetFlag(HAS_AUTOMATIC_GAIN_CONTROL);
    }

    public boolean hasCodeType() { return isFlagSet(HAS_CODE_TYPE); }
    public String getCodeType() { return mCodeType; }
    public void setCodeType(String codeType) {
        setFlag(HAS_CODE_TYPE);
        mCodeType = codeType;
    }
    public void resetCodeType() {
        resetFlag(HAS_CODE_TYPE);
        // AOSP resets to the literal "UNKNOWN", not null; getCodeType() is @NonNull.
        mCodeType = "UNKNOWN";
    }

    public boolean hasFullInterSignalBiasNanos() { return isFlagSet(HAS_FULL_ISB); }
    public double getFullInterSignalBiasNanos() { return mFullInterSignalBiasNanos; }
    public void setFullInterSignalBiasNanos(double value) {
        setFlag(HAS_FULL_ISB);
        mFullInterSignalBiasNanos = value;
    }
    public void resetFullInterSignalBiasNanos() { resetFlag(HAS_FULL_ISB); }

    public boolean hasFullInterSignalBiasUncertaintyNanos() {
        return isFlagSet(HAS_FULL_ISB_UNCERTAINTY);
    }
    public double getFullInterSignalBiasUncertaintyNanos() {
        return mFullInterSignalBiasUncertaintyNanos;
    }
    public void setFullInterSignalBiasUncertaintyNanos(double value) {
        setFlag(HAS_FULL_ISB_UNCERTAINTY);
        mFullInterSignalBiasUncertaintyNanos = value;
    }
    public void resetFullInterSignalBiasUncertaintyNanos() {
        resetFlag(HAS_FULL_ISB_UNCERTAINTY);
    }

    public boolean hasSatelliteInterSignalBiasNanos() { return isFlagSet(HAS_SATELLITE_ISB); }
    public double getSatelliteInterSignalBiasNanos() { return mSatelliteInterSignalBiasNanos; }
    public void setSatelliteInterSignalBiasNanos(double value) {
        setFlag(HAS_SATELLITE_ISB);
        mSatelliteInterSignalBiasNanos = value;
    }
    public void resetSatelliteInterSignalBiasNanos() { resetFlag(HAS_SATELLITE_ISB); }

    public boolean hasSatelliteInterSignalBiasUncertaintyNanos() {
        return isFlagSet(HAS_SATELLITE_ISB_UNCERTAINTY);
    }
    public double getSatelliteInterSignalBiasUncertaintyNanos() {
        return mSatelliteInterSignalBiasUncertaintyNanos;
    }
    public void setSatelliteInterSignalBiasUncertaintyNanos(double value) {
        setFlag(HAS_SATELLITE_ISB_UNCERTAINTY);
        mSatelliteInterSignalBiasUncertaintyNanos = value;
    }
    public void resetSatelliteInterSignalBiasUncertaintyNanos() {
        resetFlag(HAS_SATELLITE_ISB_UNCERTAINTY);
    }

    public static final Creator<GnssMeasurement> CREATOR = new Creator<GnssMeasurement>() {
        @Override public GnssMeasurement createFromParcel(Parcel parcel) {
            GnssMeasurement m = new GnssMeasurement();
            m.mFlags = parcel.readInt();
            m.mSvid = parcel.readInt();
            m.mConstellationType = parcel.readInt();
            m.mTimeOffsetNanos = parcel.readDouble();
            m.mState = parcel.readInt();
            m.mReceivedSvTimeNanos = parcel.readLong();
            m.mReceivedSvTimeUncertaintyNanos = parcel.readLong();
            m.mCn0DbHz = parcel.readDouble();
            m.mPseudorangeRateMetersPerSecond = parcel.readDouble();
            m.mPseudorangeRateUncertaintyMetersPerSecond = parcel.readDouble();
            m.mAccumulatedDeltaRangeState = parcel.readInt();
            m.mAccumulatedDeltaRangeMeters = parcel.readDouble();
            m.mAccumulatedDeltaRangeUncertaintyMeters = parcel.readDouble();
            m.mCarrierFrequencyHz = parcel.readFloat();
            m.mCarrierCycles = parcel.readLong();
            m.mCarrierPhase = parcel.readDouble();
            m.mCarrierPhaseUncertainty = parcel.readDouble();
            m.mMultipathIndicator = parcel.readInt();
            m.mSnrInDb = parcel.readDouble();
            m.mAutomaticGainControlLevelInDb = parcel.readDouble();
            m.mCodeType = parcel.readString();
            m.mBasebandCn0DbHz = parcel.readDouble();
            m.mFullInterSignalBiasNanos = parcel.readDouble();
            m.mFullInterSignalBiasUncertaintyNanos = parcel.readDouble();
            m.mSatelliteInterSignalBiasNanos = parcel.readDouble();
            m.mSatelliteInterSignalBiasUncertaintyNanos = parcel.readDouble();
            return m;
        }

        @Override public GnssMeasurement[] newArray(int size) { return new GnssMeasurement[size]; }
    };

    @Override public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(mFlags);
        parcel.writeInt(mSvid);
        parcel.writeInt(mConstellationType);
        parcel.writeDouble(mTimeOffsetNanos);
        parcel.writeInt(mState);
        parcel.writeLong(mReceivedSvTimeNanos);
        parcel.writeLong(mReceivedSvTimeUncertaintyNanos);
        parcel.writeDouble(mCn0DbHz);
        parcel.writeDouble(mPseudorangeRateMetersPerSecond);
        parcel.writeDouble(mPseudorangeRateUncertaintyMetersPerSecond);
        parcel.writeInt(mAccumulatedDeltaRangeState);
        parcel.writeDouble(mAccumulatedDeltaRangeMeters);
        parcel.writeDouble(mAccumulatedDeltaRangeUncertaintyMeters);
        parcel.writeFloat(mCarrierFrequencyHz);
        parcel.writeLong(mCarrierCycles);
        parcel.writeDouble(mCarrierPhase);
        parcel.writeDouble(mCarrierPhaseUncertainty);
        parcel.writeInt(mMultipathIndicator);
        parcel.writeDouble(mSnrInDb);
        parcel.writeDouble(mAutomaticGainControlLevelInDb);
        parcel.writeString(mCodeType);
        parcel.writeDouble(mBasebandCn0DbHz);
        parcel.writeDouble(mFullInterSignalBiasNanos);
        parcel.writeDouble(mFullInterSignalBiasUncertaintyNanos);
        parcel.writeDouble(mSatelliteInterSignalBiasNanos);
        parcel.writeDouble(mSatelliteInterSignalBiasUncertaintyNanos);
    }

    @Override public int describeContents() { return 0; }

    @Override public String toString() {
        return "GnssMeasurement: svid=" + mSvid + " constellation=" + mConstellationType
                + " state=" + mState;
    }

    private void initialize() {
        mFlags = HAS_NO_FLAGS;
        setSvid(0);
        setTimeOffsetNanos(Long.MIN_VALUE);
        setState(STATE_UNKNOWN);
        setReceivedSvTimeNanos(Long.MIN_VALUE);
        setReceivedSvTimeUncertaintyNanos(Long.MAX_VALUE);
        setCn0DbHz(Double.MIN_VALUE);
        setPseudorangeRateMetersPerSecond(Double.MIN_VALUE);
        setPseudorangeRateUncertaintyMetersPerSecond(Double.MIN_VALUE);
        setAccumulatedDeltaRangeState(ADR_STATE_UNKNOWN);
        setAccumulatedDeltaRangeMeters(Double.MIN_VALUE);
        setAccumulatedDeltaRangeUncertaintyMeters(Double.MIN_VALUE);
        resetCarrierFrequencyHz();
        resetCarrierCycles();
        resetCarrierPhase();
        resetCarrierPhaseUncertainty();
        setMultipathIndicator(MULTIPATH_INDICATOR_UNKNOWN);
        resetSnrInDb();
        resetAutomaticGainControlLevel();
        resetCodeType();
        resetBasebandCn0DbHz();
        resetFullInterSignalBiasNanos();
        resetFullInterSignalBiasUncertaintyNanos();
        resetSatelliteInterSignalBiasNanos();
        resetSatelliteInterSignalBiasUncertaintyNanos();
    }

    private void setFlag(int flag) { mFlags |= flag; }
    private void resetFlag(int flag) { mFlags &= ~flag; }
    private boolean isFlagSet(int flag) { return (mFlags & flag) == flag; }
}
