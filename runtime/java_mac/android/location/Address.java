// android.location.Address -- a postal-address result from a Geocoder
// lookup. Full data-holder surface per AOSP. Mac.apk never produces
// Address results (no geocoding backend), but maps/forms code constructs
// and reads Address instances, so the type must be complete.
package android.location;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class Address implements Parcelable {
    private Locale mLocale;
    private HashMap<Integer, String> mAddressLines = new HashMap<>();
    private int mMaxAddressLineIndex = -1;
    private String mFeatureName;
    private String mAdminArea;
    private String mSubAdminArea;
    private String mLocality;
    private String mSubLocality;
    private String mThoroughfare;
    private String mSubThoroughfare;
    private String mPremises;
    private String mPostalCode;
    private String mCountryCode;
    private String mCountryName;
    private double mLatitude;
    private double mLongitude;
    private boolean mHasLatitude = false;
    private boolean mHasLongitude = false;
    private String mPhone;
    private String mUrl;
    private android.os.Bundle mExtras = null;

    public Address(Locale locale) { mLocale = locale; }

    public Locale getLocale() { return mLocale; }

    public int getMaxAddressLineIndex() { return mMaxAddressLineIndex; }
    public String getAddressLine(int index) {
        if (index < 0) throw new IllegalArgumentException("index = " + index + " < 0");
        // AOSP tolerates a null map: unparcelling an address with no lines sets
        // mAddressLines to null rather than to an empty map.
        return mAddressLines == null ? null : mAddressLines.get(index);
    }
    public void setAddressLine(int index, String line) {
        if (index < 0) throw new IllegalArgumentException("index = " + index + " < 0");
        if (mAddressLines == null) {
            mAddressLines = new HashMap<Integer, String>();
        }
        mAddressLines.put(index, line);
        if (line == null) {
            // AOSP: a null line eliminates that line, so the max index is recomputed
            // rather than left stale.
            mMaxAddressLineIndex = -1;
            for (Integer i : mAddressLines.keySet()) {
                mMaxAddressLineIndex = Math.max(mMaxAddressLineIndex, i);
            }
        } else {
            mMaxAddressLineIndex = Math.max(mMaxAddressLineIndex, index);
        }
    }

    public String getFeatureName() { return mFeatureName; }
    public void setFeatureName(String featureName) { mFeatureName = featureName; }

    public String getAdminArea() { return mAdminArea; }
    public void setAdminArea(String adminArea) { mAdminArea = adminArea; }

    public String getSubAdminArea() { return mSubAdminArea; }
    public void setSubAdminArea(String subAdminArea) { mSubAdminArea = subAdminArea; }

    public String getLocality() { return mLocality; }
    public void setLocality(String locality) { mLocality = locality; }

    public String getSubLocality() { return mSubLocality; }
    public void setSubLocality(String subLocality) { mSubLocality = subLocality; }

    public String getThoroughfare() { return mThoroughfare; }
    public void setThoroughfare(String thoroughfare) { mThoroughfare = thoroughfare; }

    public String getSubThoroughfare() { return mSubThoroughfare; }
    public void setSubThoroughfare(String subThoroughfare) { mSubThoroughfare = subThoroughfare; }

    public String getPremises() { return mPremises; }
    public void setPremises(String premises) { mPremises = premises; }

    public String getPostalCode() { return mPostalCode; }
    public void setPostalCode(String postalCode) { mPostalCode = postalCode; }

    public String getCountryCode() { return mCountryCode; }
    public void setCountryCode(String countryCode) { mCountryCode = countryCode; }

    public String getCountryName() { return mCountryName; }
    public void setCountryName(String countryName) { mCountryName = countryName; }

    public boolean hasLatitude() { return mHasLatitude; }
    public double getLatitude() {
        if (mHasLatitude) return mLatitude;
        throw new IllegalStateException();
    }
    public void setLatitude(double latitude) { mLatitude = latitude; mHasLatitude = true; }
    public void clearLatitude() { mHasLatitude = false; }

    public boolean hasLongitude() { return mHasLongitude; }
    public double getLongitude() {
        if (mHasLongitude) return mLongitude;
        throw new IllegalStateException();
    }
    public void setLongitude(double longitude) { mLongitude = longitude; mHasLongitude = true; }
    public void clearLongitude() { mHasLongitude = false; }

    public String getPhone() { return mPhone; }
    public void setPhone(String phone) { mPhone = phone; }

    public String getUrl() { return mUrl; }
    public void setUrl(String url) { mUrl = url; }

    public android.os.Bundle getExtras() { return mExtras; }
    public void setExtras(android.os.Bundle extras) {
        mExtras = (extras == null) ? null : new android.os.Bundle(extras);
    }

    @Override public String toString() {
        StringBuilder sb = new StringBuilder("Address[addressLines=[");
        for (int i = 0; i <= mMaxAddressLineIndex; i++) {
            if (i > 0) sb.append(',');
            sb.append(i).append(':').append(mAddressLines == null ? null : mAddressLines.get(i));
        }
        sb.append("],feature=").append(mFeatureName);
        sb.append(",admin=").append(mAdminArea);
        sb.append(",locality=").append(mLocality);
        sb.append(",postalCode=").append(mPostalCode);
        sb.append(",countryCode=").append(mCountryCode);
        sb.append(']');
        return sb.toString();
    }

    // === Parcelable ========================================================
    // AOSP Address implements Parcelable because Geocoder results cross a
    // process boundary (the geocoder runs in the system server) and because
    // apps put an Address into an Intent/Bundle. Field order below is AOSP's
    // verbatim -- note the two conditional doubles: latitude/longitude are
    // only written when the corresponding has* flag is set, so a reader that
    // unconditionally reads them desynchronises the whole parcel.
    // Source: frameworks/base/location/java/android/location/Address.java
    public static final Creator<Address> CREATOR = new Creator<Address>() {
        @Override public Address createFromParcel(Parcel in) {
            String language = in.readString();
            String country = in.readString();
            Locale locale = country.length() > 0
                    ? new Locale(language, country)
                    : new Locale(language);
            Address a = new Address(locale);

            int n = in.readInt();
            if (n > 0) {
                a.mAddressLines = new HashMap<Integer, String>(n);
                for (int i = 0; i < n; i++) {
                    int index = in.readInt();
                    String line = in.readString();
                    a.mAddressLines.put(index, line);
                    a.mMaxAddressLineIndex = Math.max(a.mMaxAddressLineIndex, index);
                }
            } else {
                a.mAddressLines = null;
                a.mMaxAddressLineIndex = -1;
            }
            a.mFeatureName = in.readString();
            a.mAdminArea = in.readString();
            a.mSubAdminArea = in.readString();
            a.mLocality = in.readString();
            a.mSubLocality = in.readString();
            a.mThoroughfare = in.readString();
            a.mSubThoroughfare = in.readString();
            a.mPremises = in.readString();
            a.mPostalCode = in.readString();
            a.mCountryCode = in.readString();
            a.mCountryName = in.readString();
            a.mHasLatitude = in.readInt() != 0;
            if (a.mHasLatitude) {
                a.mLatitude = in.readDouble();
            }
            a.mHasLongitude = in.readInt() != 0;
            if (a.mHasLongitude) {
                a.mLongitude = in.readDouble();
            }
            a.mPhone = in.readString();
            a.mUrl = in.readString();
            a.mExtras = in.readBundle();
            return a;
        }

        @Override public Address[] newArray(int size) { return new Address[size]; }
    };

    /** AOSP delegates to the extras bundle rather than returning a constant 0. */
    @Override public int describeContents() {
        return (mExtras != null) ? mExtras.describeContents() : 0;
    }

    @Override public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeString(mLocale.getLanguage());
        parcel.writeString(mLocale.getCountry());
        if (mAddressLines == null) {
            parcel.writeInt(0);
        } else {
            Set<Map.Entry<Integer, String>> entries = mAddressLines.entrySet();
            parcel.writeInt(entries.size());
            for (Map.Entry<Integer, String> e : entries) {
                parcel.writeInt(e.getKey());
                parcel.writeString(e.getValue());
            }
        }
        parcel.writeString(mFeatureName);
        parcel.writeString(mAdminArea);
        parcel.writeString(mSubAdminArea);
        parcel.writeString(mLocality);
        parcel.writeString(mSubLocality);
        parcel.writeString(mThoroughfare);
        parcel.writeString(mSubThoroughfare);
        parcel.writeString(mPremises);
        parcel.writeString(mPostalCode);
        parcel.writeString(mCountryCode);
        parcel.writeString(mCountryName);
        parcel.writeInt(mHasLatitude ? 1 : 0);
        if (mHasLatitude) {
            parcel.writeDouble(mLatitude);
        }
        parcel.writeInt(mHasLongitude ? 1 : 0);
        if (mHasLongitude) {
            parcel.writeDouble(mLongitude);
        }
        parcel.writeString(mPhone);
        parcel.writeString(mUrl);
        parcel.writeBundle(mExtras);
    }
}
