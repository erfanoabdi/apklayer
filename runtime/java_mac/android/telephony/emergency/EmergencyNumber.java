package android.telephony.emergency;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * One emergency number as reported by the modem / SIM / carrier database.
 *
 * Mac.apk has no modem, no SIM and no carrier database, so
 * TelephonyManager.getEmergencyNumberList() returns an empty map. That is the
 * honest answer: emergency numbers are country- and network-specific, and
 * inventing "911" would be actively dangerous -- an app that dials what we
 * claim is an emergency number on a machine that cannot place calls is worse
 * than an app that discovers there is no list.
 *
 * The value type is faithful, including compareTo's ordering by source priority.
 *
 * Source: frameworks/base/telephony/java/android/telephony/emergency/EmergencyNumber.java
 */
public final class EmergencyNumber implements Parcelable, Comparable<EmergencyNumber> {

    public static final int EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED = 0;
    public static final int EMERGENCY_SERVICE_CATEGORY_POLICE = 1 << 0;
    public static final int EMERGENCY_SERVICE_CATEGORY_AMBULANCE = 1 << 1;
    public static final int EMERGENCY_SERVICE_CATEGORY_FIRE_BRIGADE = 1 << 2;
    public static final int EMERGENCY_SERVICE_CATEGORY_MARINE_GUARD = 1 << 3;
    public static final int EMERGENCY_SERVICE_CATEGORY_MOUNTAIN_RESCUE = 1 << 4;
    public static final int EMERGENCY_SERVICE_CATEGORY_MIEC = 1 << 5;
    public static final int EMERGENCY_SERVICE_CATEGORY_AIEC = 1 << 6;

    public static final int EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING = 1 << 0;
    public static final int EMERGENCY_NUMBER_SOURCE_SIM = 1 << 1;
    public static final int EMERGENCY_NUMBER_SOURCE_MODEM_CONFIG = 1 << 2;
    public static final int EMERGENCY_NUMBER_SOURCE_DEFAULT = 1 << 3;
    public static final int EMERGENCY_NUMBER_SOURCE_DATABASE = 1 << 4;

    public static final int EMERGENCY_CALL_ROUTING_UNKNOWN = 0;
    public static final int EMERGENCY_CALL_ROUTING_EMERGENCY = 1;
    public static final int EMERGENCY_CALL_ROUTING_NORMAL = 2;

    private final String mNumber;
    private final String mCountryIso;
    private final String mMnc;
    private final int mEmergencyServiceCategoryBitmask;
    private final List<String> mEmergencyUrns;
    private final int mEmergencyNumberSourceBitmask;
    private final int mEmergencyCallRouting;

    /** @hide */
    public EmergencyNumber(String number, String countryIso, String mnc,
            int emergencyServiceCategories, List<String> emergencyUrns,
            int emergencyNumberSources, int emergencyCallRouting) {
        mNumber = number;
        mCountryIso = countryIso;
        mMnc = mnc;
        mEmergencyServiceCategoryBitmask = emergencyServiceCategories;
        mEmergencyUrns = emergencyUrns == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(emergencyUrns));
        mEmergencyNumberSourceBitmask = emergencyNumberSources;
        mEmergencyCallRouting = emergencyCallRouting;
    }

    private EmergencyNumber(Parcel source) {
        mNumber = source.readString();
        mCountryIso = source.readString();
        mMnc = source.readString();
        mEmergencyServiceCategoryBitmask = source.readInt();
        List<String> urns = source.createStringArrayList();
        mEmergencyUrns = urns == null
                ? Collections.<String>emptyList() : Collections.unmodifiableList(urns);
        mEmergencyNumberSourceBitmask = source.readInt();
        mEmergencyCallRouting = source.readInt();
    }

    public String getNumber() { return mNumber; }

    public String getCountryIso() { return mCountryIso; }

    public String getMnc() { return mMnc; }

    /** AOSP decomposes the bitmask into the individual category flags. */
    public List<Integer> getEmergencyServiceCategories() {
        List<Integer> categories = new ArrayList<>();
        if (mEmergencyServiceCategoryBitmask == EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED) {
            categories.add(EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED);
            return categories;
        }
        int[] all = {
            EMERGENCY_SERVICE_CATEGORY_POLICE, EMERGENCY_SERVICE_CATEGORY_AMBULANCE,
            EMERGENCY_SERVICE_CATEGORY_FIRE_BRIGADE, EMERGENCY_SERVICE_CATEGORY_MARINE_GUARD,
            EMERGENCY_SERVICE_CATEGORY_MOUNTAIN_RESCUE, EMERGENCY_SERVICE_CATEGORY_MIEC,
            EMERGENCY_SERVICE_CATEGORY_AIEC };
        for (int c : all) {
            if ((mEmergencyServiceCategoryBitmask & c) != 0) categories.add(c);
        }
        return categories;
    }

    public List<String> getEmergencyUrns() { return mEmergencyUrns; }

    public boolean isInEmergencyServiceCategories(int categories) {
        if (categories == EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED) {
            return mEmergencyServiceCategoryBitmask == EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED;
        }
        if (mEmergencyServiceCategoryBitmask == EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED) {
            return true;
        }
        return (mEmergencyServiceCategoryBitmask & categories) == categories;
    }

    public List<Integer> getEmergencyNumberSources() {
        List<Integer> sources = new ArrayList<>();
        int[] all = {
            EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING, EMERGENCY_NUMBER_SOURCE_SIM,
            EMERGENCY_NUMBER_SOURCE_MODEM_CONFIG, EMERGENCY_NUMBER_SOURCE_DEFAULT,
            EMERGENCY_NUMBER_SOURCE_DATABASE };
        for (int s : all) {
            if ((mEmergencyNumberSourceBitmask & s) != 0) sources.add(s);
        }
        return sources;
    }

    public boolean isFromSources(int sources) {
        return (mEmergencyNumberSourceBitmask & sources) == sources;
    }

    public int getEmergencyCallRouting() { return mEmergencyCallRouting; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mNumber);
        dest.writeString(mCountryIso);
        dest.writeString(mMnc);
        dest.writeInt(mEmergencyServiceCategoryBitmask);
        dest.writeStringList(mEmergencyUrns);
        dest.writeInt(mEmergencyNumberSourceBitmask);
        dest.writeInt(mEmergencyCallRouting);
    }

    public static final Creator<EmergencyNumber> CREATOR = new Creator<EmergencyNumber>() {
        @Override public EmergencyNumber createFromParcel(Parcel in) {
            return new EmergencyNumber(in);
        }
        @Override public EmergencyNumber[] newArray(int size) { return new EmergencyNumber[size]; }
    };

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EmergencyNumber)) return false;
        EmergencyNumber other = (EmergencyNumber) o;
        return Objects.equals(mNumber, other.mNumber)
                && Objects.equals(mCountryIso, other.mCountryIso)
                && Objects.equals(mMnc, other.mMnc)
                && mEmergencyServiceCategoryBitmask == other.mEmergencyServiceCategoryBitmask
                && mEmergencyUrns.equals(other.mEmergencyUrns)
                && mEmergencyNumberSourceBitmask == other.mEmergencyNumberSourceBitmask
                && mEmergencyCallRouting == other.mEmergencyCallRouting;
    }

    @Override public int hashCode() {
        return Objects.hash(mNumber, mCountryIso, mMnc, mEmergencyServiceCategoryBitmask,
                mEmergencyUrns, mEmergencyNumberSourceBitmask, mEmergencyCallRouting);
    }

    @Override public String toString() {
        return "EmergencyNumber:" + mNumber + "|" + mCountryIso + "|" + mMnc;
    }

    /**
     * AOSP orders by source priority (DATABASE highest), so a number known from
     * more authoritative sources sorts first.
     */
    @Override public int compareTo(EmergencyNumber emergencyNumber) {
        if (this.getNumber().compareTo(emergencyNumber.getNumber()) != 0) {
            return this.getNumber().compareTo(emergencyNumber.getNumber());
        }
        if (this.getCountryIso().compareTo(emergencyNumber.getCountryIso()) != 0) {
            return this.getCountryIso().compareTo(emergencyNumber.getCountryIso());
        }
        if (this.getMnc().compareTo(emergencyNumber.getMnc()) != 0) {
            return this.getMnc().compareTo(emergencyNumber.getMnc());
        }
        if (this.mEmergencyServiceCategoryBitmask
                != emergencyNumber.mEmergencyServiceCategoryBitmask) {
            return this.mEmergencyServiceCategoryBitmask
                    > emergencyNumber.mEmergencyServiceCategoryBitmask ? -1 : 1;
        }
        if (this.mEmergencyNumberSourceBitmask != emergencyNumber.mEmergencyNumberSourceBitmask) {
            return this.mEmergencyNumberSourceBitmask
                    > emergencyNumber.mEmergencyNumberSourceBitmask ? -1 : 1;
        }
        return 0;
    }
}
