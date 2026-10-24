package android.location;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

/**
 * Modern GNSS status snapshot (API 24+ replacement for GpsStatus).
 *
 * A Mac has no GNSS receiver, so LocationManager never delivers one of these.
 * The class itself is a faithful data holder over AOSP's packed parallel-array
 * representation -- an app that builds one with GnssStatus.Builder reads back
 * exactly what it put in, and the per-index accessors index the arrays the way
 * AOSP does. That means an out-of-range index throws
 * ArrayIndexOutOfBoundsException here as it does on a device, rather than
 * silently returning 0 and letting a buggy caller believe it read a real
 * measurement.
 *
 * Source: frameworks/base/location/java/android/location/GnssStatus.java
 */
public final class GnssStatus implements Parcelable {

    // These must match the definitions in the GNSS HAL.
    public static final int CONSTELLATION_UNKNOWN = 0;
    public static final int CONSTELLATION_GPS = 1;
    public static final int CONSTELLATION_SBAS = 2;
    public static final int CONSTELLATION_GLONASS = 3;
    public static final int CONSTELLATION_QZSS = 4;
    public static final int CONSTELLATION_BEIDOU = 5;
    public static final int CONSTELLATION_GALILEO = 6;
    public static final int CONSTELLATION_IRNSS = 7;
    /** @hide in AOSP, but load-bearing for anything iterating constellations. */
    public static final int CONSTELLATION_COUNT = 8;

    private static final int SVID_FLAGS_NONE = 0;
    private static final int SVID_FLAGS_HAS_EPHEMERIS_DATA = (1 << 0);
    private static final int SVID_FLAGS_HAS_ALMANAC_DATA = (1 << 1);
    private static final int SVID_FLAGS_USED_IN_FIX = (1 << 2);
    private static final int SVID_FLAGS_HAS_CARRIER_FREQUENCY = (1 << 3);
    private static final int SVID_FLAGS_HAS_BASEBAND_CN0 = (1 << 4);

    private static final int SVID_SHIFT_WIDTH = 12;
    private static final int CONSTELLATION_TYPE_SHIFT_WIDTH = 8;
    private static final int CONSTELLATION_TYPE_MASK = 0xf;

    private final int mSvCount;
    private final int[] mSvidWithFlags;
    private final float[] mCn0DbHzs;
    private final float[] mElevations;
    private final float[] mAzimuths;
    private final float[] mCarrierFrequencies;
    private final float[] mBasebandCn0DbHzs;

    private GnssStatus(int svCount, int[] svidWithFlags, float[] cn0DbHzs, float[] elevations,
            float[] azimuths, float[] carrierFrequencies, float[] basebandCn0DbHzs) {
        mSvCount = svCount;
        mSvidWithFlags = svidWithFlags;
        mCn0DbHzs = cn0DbHzs;
        mElevations = elevations;
        mAzimuths = azimuths;
        mCarrierFrequencies = carrierFrequencies;
        mBasebandCn0DbHzs = basebandCn0DbHzs;
    }

    /** AOSP marks this @hide; the framework uses it to wrap HAL arrays. */
    public static GnssStatus wrap(int svCount, int[] svidWithFlags, float[] cn0DbHzs,
            float[] elevations, float[] azimuths, float[] carrierFrequencies,
            float[] basebandCn0DbHzs) {
        if (svCount < 0 || svidWithFlags.length < svCount || elevations.length < svCount
                || azimuths.length < svCount || carrierFrequencies.length < svCount
                || basebandCn0DbHzs.length < svCount) {
            throw new IllegalStateException("array shorter than svCount");
        }
        return new GnssStatus(svCount, svidWithFlags, cn0DbHzs, elevations, azimuths,
                carrierFrequencies, basebandCn0DbHzs);
    }

    public int getSatelliteCount() { return mSvCount; }

    public int getConstellationType(int satelliteIndex) {
        return (mSvidWithFlags[satelliteIndex] >> CONSTELLATION_TYPE_SHIFT_WIDTH)
                & CONSTELLATION_TYPE_MASK;
    }

    public int getSvid(int satelliteIndex) {
        return mSvidWithFlags[satelliteIndex] >> SVID_SHIFT_WIDTH;
    }

    public float getCn0DbHz(int satelliteIndex) { return mCn0DbHzs[satelliteIndex]; }

    public float getElevationDegrees(int satelliteIndex) { return mElevations[satelliteIndex]; }

    public float getAzimuthDegrees(int satelliteIndex) { return mAzimuths[satelliteIndex]; }

    public boolean hasEphemerisData(int satelliteIndex) {
        return (mSvidWithFlags[satelliteIndex] & SVID_FLAGS_HAS_EPHEMERIS_DATA) != 0;
    }

    public boolean hasAlmanacData(int satelliteIndex) {
        return (mSvidWithFlags[satelliteIndex] & SVID_FLAGS_HAS_ALMANAC_DATA) != 0;
    }

    public boolean usedInFix(int satelliteIndex) {
        return (mSvidWithFlags[satelliteIndex] & SVID_FLAGS_USED_IN_FIX) != 0;
    }

    public boolean hasCarrierFrequencyHz(int satelliteIndex) {
        return (mSvidWithFlags[satelliteIndex] & SVID_FLAGS_HAS_CARRIER_FREQUENCY) != 0;
    }

    public float getCarrierFrequencyHz(int satelliteIndex) {
        return mCarrierFrequencies[satelliteIndex];
    }

    public boolean hasBasebandCn0DbHz(int satelliteIndex) {
        return (mSvidWithFlags[satelliteIndex] & SVID_FLAGS_HAS_BASEBAND_CN0) != 0;
    }

    public float getBasebandCn0DbHz(int satelliteIndex) {
        return mBasebandCn0DbHzs[satelliteIndex];
    }

    /** AOSP marks this @hide, but it is widely mirrored; keep the exact strings. */
    public static String constellationTypeToString(int constellationType) {
        switch (constellationType) {
            case CONSTELLATION_UNKNOWN: return "UNKNOWN";
            case CONSTELLATION_GPS: return "GPS";
            case CONSTELLATION_SBAS: return "SBAS";
            case CONSTELLATION_GLONASS: return "GLONASS";
            case CONSTELLATION_QZSS: return "QZSS";
            case CONSTELLATION_BEIDOU: return "BEIDOU";
            case CONSTELLATION_GALILEO: return "GALILEO";
            case CONSTELLATION_IRNSS: return "IRNSS";
            default: return Integer.toString(constellationType);
        }
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GnssStatus)) return false;
        GnssStatus that = (GnssStatus) o;
        return mSvCount == that.mSvCount
                && Arrays.equals(mSvidWithFlags, that.mSvidWithFlags)
                && Arrays.equals(mCn0DbHzs, that.mCn0DbHzs)
                && Arrays.equals(mElevations, that.mElevations)
                && Arrays.equals(mAzimuths, that.mAzimuths)
                && Arrays.equals(mCarrierFrequencies, that.mCarrierFrequencies)
                && Arrays.equals(mBasebandCn0DbHzs, that.mBasebandCn0DbHzs);
    }

    @Override public int hashCode() {
        int result = Objects.hash(mSvCount);
        result = 31 * result + Arrays.hashCode(mSvidWithFlags);
        result = 31 * result + Arrays.hashCode(mCn0DbHzs);
        return result;
    }

    public static final Creator<GnssStatus> CREATOR = new Creator<GnssStatus>() {
        @Override public GnssStatus createFromParcel(Parcel in) {
            int svCount = in.readInt();
            int[] svidWithFlags = new int[svCount];
            float[] cn0DbHzs = new float[svCount];
            float[] elevations = new float[svCount];
            float[] azimuths = new float[svCount];
            float[] carrierFrequencies = new float[svCount];
            float[] basebandCn0DbHzs = new float[svCount];
            for (int i = 0; i < svCount; i++) {
                svidWithFlags[i] = in.readInt();
                cn0DbHzs[i] = in.readFloat();
                elevations[i] = in.readFloat();
                azimuths[i] = in.readFloat();
                carrierFrequencies[i] = in.readFloat();
                basebandCn0DbHzs[i] = in.readFloat();
            }
            return new GnssStatus(svCount, svidWithFlags, cn0DbHzs, elevations, azimuths,
                    carrierFrequencies, basebandCn0DbHzs);
        }
        @Override public GnssStatus[] newArray(int size) { return new GnssStatus[size]; }
    };

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(mSvCount);
        for (int i = 0; i < mSvCount; i++) {
            parcel.writeInt(mSvidWithFlags[i]);
            parcel.writeFloat(mCn0DbHzs[i]);
            parcel.writeFloat(mElevations[i]);
            parcel.writeFloat(mAzimuths[i]);
            parcel.writeFloat(mCarrierFrequencies[i]);
            parcel.writeFloat(mBasebandCn0DbHzs[i]);
        }
    }

    /** AOSP declares Callback as an abstract class, not an interface. */
    public abstract static class Callback {
        public void onStarted() { }
        public void onStopped() { }
        public void onFirstFix(int ttffMillis) { }
        public void onSatelliteStatusChanged(GnssStatus status) { }
    }

    public static final class Builder {

        private final ArrayList<GnssSvInfo> mSatellites = new ArrayList<>();

        public Builder addSatellite(int constellationType, int svid, float cn0DbHz,
                float elevation, float azimuth, boolean hasEphemeris, boolean hasAlmanac,
                boolean usedInFix, boolean hasCarrierFrequency, float carrierFrequency,
                boolean hasBasebandCn0DbHz, float basebandCn0DbHz) {
            mSatellites.add(new GnssSvInfo(constellationType, svid, cn0DbHz, elevation, azimuth,
                    hasEphemeris, hasAlmanac, usedInFix, hasCarrierFrequency, carrierFrequency,
                    hasBasebandCn0DbHz, basebandCn0DbHz));
            return this;
        }

        public Builder clearSatellites() {
            mSatellites.clear();
            return this;
        }

        public GnssStatus build() {
            int svCount = mSatellites.size();
            int[] svidWithFlags = new int[svCount];
            float[] cn0DbHzs = new float[svCount];
            float[] elevations = new float[svCount];
            float[] azimuths = new float[svCount];
            float[] carrierFrequencies = new float[svCount];
            float[] basebandCn0DbHzs = new float[svCount];
            for (int i = 0; i < svCount; i++) {
                GnssSvInfo info = mSatellites.get(i);
                svidWithFlags[i] = info.mSvidWithFlags;
                cn0DbHzs[i] = info.mCn0DbHz;
                elevations[i] = info.mElevation;
                azimuths[i] = info.mAzimuth;
                carrierFrequencies[i] = info.mCarrierFrequency;
                basebandCn0DbHzs[i] = info.mBasebandCn0DbHz;
            }
            return new GnssStatus(svCount, svidWithFlags, cn0DbHzs, elevations, azimuths,
                    carrierFrequencies, basebandCn0DbHzs);
        }
    }

    private static class GnssSvInfo {

        private final int mSvidWithFlags;
        private final float mCn0DbHz;
        private final float mElevation;
        private final float mAzimuth;
        private final float mCarrierFrequency;
        private final float mBasebandCn0DbHz;

        private GnssSvInfo(int constellationType, int svid, float cn0DbHz, float elevation,
                float azimuth, boolean hasEphemeris, boolean hasAlmanac, boolean usedInFix,
                boolean hasCarrierFrequency, float carrierFrequency, boolean hasBasebandCn0DbHz,
                float basebandCn0DbHz) {
            mSvidWithFlags = (svid << SVID_SHIFT_WIDTH)
                    | ((constellationType & CONSTELLATION_TYPE_MASK)
                            << CONSTELLATION_TYPE_SHIFT_WIDTH)
                    | (hasEphemeris ? SVID_FLAGS_HAS_EPHEMERIS_DATA : SVID_FLAGS_NONE)
                    | (hasAlmanac ? SVID_FLAGS_HAS_ALMANAC_DATA : SVID_FLAGS_NONE)
                    | (usedInFix ? SVID_FLAGS_USED_IN_FIX : SVID_FLAGS_NONE)
                    | (hasCarrierFrequency ? SVID_FLAGS_HAS_CARRIER_FREQUENCY : SVID_FLAGS_NONE)
                    | (hasBasebandCn0DbHz ? SVID_FLAGS_HAS_BASEBAND_CN0 : SVID_FLAGS_NONE);
            mCn0DbHz = cn0DbHz;
            mElevation = elevation;
            mAzimuth = azimuth;
            mCarrierFrequency = hasCarrierFrequency ? carrierFrequency : 0;
            mBasebandCn0DbHz = hasBasebandCn0DbHz ? basebandCn0DbHz : 0;
        }
    }
}
