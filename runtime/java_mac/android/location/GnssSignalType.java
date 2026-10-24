package android.location;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * Identifies a GNSS signal by constellation, carrier frequency and code type.
 *
 * Pure immutable value type -- no hardware is involved, so this is faithful on
 * a Mac as-is. A Mac reports no signal types (GnssCapabilities returns an empty
 * list); the class exists so an app that constructs or reads one links.
 *
 * Source: frameworks/base/location/java/android/location/GnssSignalType.java
 */
public final class GnssSignalType implements Parcelable {

    private final int mConstellationType;
    private final double mCarrierFrequencyHz;
    private final String mCodeType;

    private GnssSignalType(int constellationType, double carrierFrequencyHz, String codeType) {
        mConstellationType = constellationType;
        mCarrierFrequencyHz = carrierFrequencyHz;
        mCodeType = codeType;
    }

    public static GnssSignalType create(int constellationType, double carrierFrequencyHz,
            String codeType) {
        // AOSP: Preconditions.checkArgument(carrierFrequencyHz > 0) then
        // Objects.requireNonNull(codeType). Mirror both, including the message.
        if (!(carrierFrequencyHz > 0)) {
            throw new IllegalArgumentException("carrierFrequencyHz must be greater than 0.");
        }
        Objects.requireNonNull(codeType);
        return new GnssSignalType(constellationType, carrierFrequencyHz, codeType);
    }

    public int getConstellationType() { return mConstellationType; }

    public double getCarrierFrequencyHz() { return mCarrierFrequencyHz; }

    public String getCodeType() { return mCodeType; }

    public static final Creator<GnssSignalType> CREATOR = new Creator<GnssSignalType>() {
        @Override public GnssSignalType createFromParcel(Parcel parcel) {
            return new GnssSignalType(parcel.readInt(), parcel.readDouble(), parcel.readString());
        }
        @Override public GnssSignalType[] newArray(int i) { return new GnssSignalType[i]; }
    };

    @Override public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(mConstellationType);
        parcel.writeDouble(mCarrierFrequencyHz);
        parcel.writeString(mCodeType);
    }

    @Override public int describeContents() { return 0; }

    @Override public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof GnssSignalType)) return false;
        GnssSignalType o = (GnssSignalType) obj;
        return mConstellationType == o.mConstellationType
                && Double.compare(mCarrierFrequencyHz, o.mCarrierFrequencyHz) == 0
                && mCodeType.equals(o.mCodeType);
    }

    @Override public int hashCode() {
        return Objects.hash(mConstellationType, mCarrierFrequencyHz, mCodeType);
    }

    @Override public String toString() {
        return "GnssSignalType[Constellation=" + mConstellationType
                + ", CarrierFrequencyHz=" + mCarrierFrequencyHz
                + ", CodeType=" + mCodeType + ']';
    }
}
