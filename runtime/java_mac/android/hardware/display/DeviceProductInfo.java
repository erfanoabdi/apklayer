// android.hardware.display.DeviceProductInfo
//
// EDID-derived identity of the physical panel behind a Display: who made it,
// which model, and when. Reached through Display.getDeviceProductInfo(); apps
// use it to recognise a particular external screen (TV apps and casting/
// presentation code, mostly).
//
// Mirrors frameworks/base/core/java/android/hardware/display/DeviceProductInfo.java
// (android16-release). This is a pure value carrier, so it is a faithful
// reimplementation rather than a stand-in -- an app that CONSTRUCTS one (the
// public constructor is API, and tests do) gets exactly AOSP's behaviour.
//
// The public constructor takes a model year and NO manufacture date, which is
// why getManufactureYear()/getManufactureWeek() answer -1 for anything built
// through it: AOSP's own :149-166 return -1 when mManufactureDate is null, and
// only the hidden 6-arg constructor can set one. That is AOSP's documented
// "typically exactly one of model year or manufacture year is available", not
// a gap here.
package android.hardware.display;

import java.util.Objects;

public final class DeviceProductInfo implements android.os.Parcelable {

    public static final int CONNECTION_TO_SINK_UNKNOWN = 0;
    public static final int CONNECTION_TO_SINK_BUILT_IN = 1;
    public static final int CONNECTION_TO_SINK_DIRECT = 2;
    public static final int CONNECTION_TO_SINK_TRANSITIVE = 3;

    private final String mName;
    private final String mManufacturerPnpId;
    private final String mProductId;
    private final int mModelYear;
    private final int mManufactureYear;
    private final int mManufactureWeek;
    private final int mConnectionToSinkType;

    /** AOSP DeviceProductInfo.java:85 -- the public constructor. The two
     *  requireNonNull calls are AOSP's, and they are observable: passing a null
     *  manufacturerPnpId throws there and must throw here. `name` is @Nullable
     *  and is deliberately NOT checked. */
    public DeviceProductInfo(String name, String manufacturerPnpId, String productId,
            int modelYear, int connectionToSinkType) {
        mName = name;
        mManufacturerPnpId = Objects.requireNonNull(manufacturerPnpId);
        mProductId = Objects.requireNonNull(productId);
        mModelYear = modelYear;
        mManufactureYear = -1;
        mManufactureWeek = -1;
        mConnectionToSinkType = connectionToSinkType;
    }

    private DeviceProductInfo(String name, String manufacturerPnpId, String productId,
            int modelYear, int manufactureYear, int manufactureWeek,
            int connectionToSinkType) {
        mName = name;
        mManufacturerPnpId = manufacturerPnpId;
        mProductId = productId;
        mModelYear = modelYear;
        mManufactureYear = manufactureYear;
        mManufactureWeek = manufactureWeek;
        mConnectionToSinkType = connectionToSinkType;
    }

    public String getName() { return mName; }
    public String getManufacturerPnpId() { return mManufacturerPnpId; }
    public String getProductId() { return mProductId; }
    public int getModelYear() { return mModelYear; }
    public int getManufactureYear() { return mManufactureYear; }
    public int getManufactureWeek() { return mManufactureWeek; }
    public int getConnectionToSinkType() { return mConnectionToSinkType; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeString(mName);
        dest.writeString(mManufacturerPnpId);
        dest.writeString(mProductId);
        dest.writeInt(mModelYear);
        dest.writeInt(mManufactureYear);
        dest.writeInt(mManufactureWeek);
        dest.writeInt(mConnectionToSinkType);
    }

    @Override public boolean equals(Object o) {
        if (!(o instanceof DeviceProductInfo)) return false;
        DeviceProductInfo that = (DeviceProductInfo) o;
        return Objects.equals(mName, that.mName)
                && Objects.equals(mManufacturerPnpId, that.mManufacturerPnpId)
                && Objects.equals(mProductId, that.mProductId)
                && mModelYear == that.mModelYear
                && mManufactureYear == that.mManufactureYear
                && mManufactureWeek == that.mManufactureWeek
                && mConnectionToSinkType == that.mConnectionToSinkType;
    }

    @Override public int hashCode() {
        return Objects.hash(mName, mManufacturerPnpId, mProductId, mModelYear,
                mManufactureYear, mManufactureWeek, mConnectionToSinkType);
    }

    @Override public String toString() {
        return "DeviceProductInfo{name=" + mName
                + ", manufacturerPnpId=" + mManufacturerPnpId
                + ", productId=" + mProductId
                + ", modelYear=" + mModelYear
                + ", connectionToSinkType=" + mConnectionToSinkType + "}";
    }

    public static final Creator<DeviceProductInfo> CREATOR = new Creator<DeviceProductInfo>() {
        @Override public DeviceProductInfo createFromParcel(android.os.Parcel in) {
            return new DeviceProductInfo(in.readString(), in.readString(), in.readString(),
                    in.readInt(), in.readInt(), in.readInt(), in.readInt());
        }
        @Override public DeviceProductInfo[] newArray(int size) {
            return new DeviceProductInfo[size];
        }
    };
}
