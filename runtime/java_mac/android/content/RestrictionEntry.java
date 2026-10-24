// android.content.RestrictionEntry -- one managed-configuration restriction an
// app declares in its <restrictions> XML, or one a device-policy controller
// sets. A TOP-LEVEL class upstream, not nested in RestrictionsManager.
//
// AOSP ref: frameworks/base/core/java/android/content/RestrictionEntry.java
// (android16-release). Ported in full.
//
// ⚠ This tree previously carried a NESTED stub,
// RestrictionsManager.RestrictionEntry, whose binary name was
// android/content/RestrictionsManager$RestrictionEntry -- a different class
// from the one an app resolves. Any app referencing the real name got
// NoClassDefFoundError; anything reading a List from getManifestRestrictions()
// and casting got ClassCastException. The nested stub is gone; this is the
// class the API actually names.
package android.content;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Arrays;
import java.util.Objects;

public class RestrictionEntry implements Parcelable {
    public static final int TYPE_NULL         = 0;
    public static final int TYPE_BOOLEAN      = 1;
    public static final int TYPE_CHOICE       = 2;
    /** @hide upstream (public in bytecode); a choice list ordered by level. */
    public static final int TYPE_CHOICE_LEVEL = 3;
    public static final int TYPE_MULTI_SELECT = 4;
    public static final int TYPE_INTEGER      = 5;
    public static final int TYPE_STRING       = 6;
    public static final int TYPE_BUNDLE       = 7;
    public static final int TYPE_BUNDLE_ARRAY = 8;

    private int mType;
    private String mKey;
    private String mTitle;
    private String mDescription;
    private String[] mChoiceEntries;
    private String[] mChoiceValues;
    private String mCurrentValue;
    private String[] mCurrentValues;
    private RestrictionEntry[] mRestrictions;

    public RestrictionEntry(int type, String key) {
        mType = type;
        mKey = key;
    }

    public RestrictionEntry(String key, String selectedString) {
        this.mKey = key;
        this.mType = TYPE_CHOICE;
        this.mCurrentValue = selectedString;
    }

    public RestrictionEntry(String key, boolean selectedState) {
        this.mKey = key;
        this.mType = TYPE_BOOLEAN;
        setSelectedState(selectedState);
    }

    public RestrictionEntry(String key, String[] selectedStrings) {
        this.mKey = key;
        this.mType = TYPE_MULTI_SELECT;
        this.mCurrentValues = selectedStrings;
    }

    public RestrictionEntry(String key, int selectedInt) {
        mKey = key;
        mType = TYPE_INTEGER;
        setIntValue(selectedInt);
    }

    private RestrictionEntry(String key, RestrictionEntry[] restrictionEntries,
                             boolean isBundleArray) {
        mKey = key;
        if (isBundleArray) {
            mType = TYPE_BUNDLE_ARRAY;
            if (restrictionEntries != null) {
                for (RestrictionEntry restriction : restrictionEntries) {
                    if (restriction.getType() != TYPE_BUNDLE) {
                        throw new IllegalArgumentException("bundle_array restriction can only have "
                                + "nested restriction entries of type bundle");
                    }
                }
            }
        } else {
            mType = TYPE_BUNDLE;
        }
        setRestrictions(restrictionEntries);
    }

    public static RestrictionEntry createBundleEntry(String key,
                                                     RestrictionEntry[] restrictionEntries) {
        return new RestrictionEntry(key, restrictionEntries, false);
    }

    public static RestrictionEntry createBundleArrayEntry(String key,
                                                          RestrictionEntry[] restrictionEntries) {
        return new RestrictionEntry(key, restrictionEntries, true);
    }

    public void setType(int type) { this.mType = type; }
    public int getType() { return mType; }

    public String getSelectedString() { return mCurrentValue; }
    public String[] getAllSelectedStrings() { return mCurrentValues; }
    /** AOSP parses the single string form; an unset value parses as false. */
    public boolean getSelectedState() { return Boolean.parseBoolean(mCurrentValue); }
    /** AOSP: Integer.parseInt, so an unset value throws NumberFormatException
     *  here exactly as it does upstream. */
    public int getIntValue() { return Integer.parseInt(mCurrentValue); }

    public void setIntValue(int value) { mCurrentValue = Integer.toString(value); }
    public void setSelectedString(String selectedString) { mCurrentValue = selectedString; }
    public void setSelectedState(boolean state) { mCurrentValue = Boolean.toString(state); }
    public void setAllSelectedStrings(String[] allSelectedStrings) {
        mCurrentValues = allSelectedStrings;
    }

    public void setChoiceValues(String[] choiceValues) { mChoiceValues = choiceValues; }
    public void setChoiceValues(Context context, int stringArrayResId) {
        mChoiceValues = context.getResources().getStringArray(stringArrayResId);
    }
    public String[] getChoiceValues() { return mChoiceValues; }

    public void setChoiceEntries(String[] choiceEntries) { mChoiceEntries = choiceEntries; }
    public void setChoiceEntries(Context context, int stringArrayResId) {
        mChoiceEntries = context.getResources().getStringArray(stringArrayResId);
    }
    public String[] getChoiceEntries() { return mChoiceEntries; }

    public RestrictionEntry[] getRestrictions() { return mRestrictions; }
    public void setRestrictions(RestrictionEntry[] restrictions) { mRestrictions = restrictions; }

    public String getDescription() { return mDescription; }
    public void setDescription(String description) { this.mDescription = description; }

    public String getKey() { return mKey; }

    public String getTitle() { return mTitle; }
    public void setTitle(String title) { this.mTitle = title; }

    @Override public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof RestrictionEntry)) return false;
        final RestrictionEntry other = (RestrictionEntry) o;
        if (mType != other.mType || !mKey.equals(other.mKey)) {
            return false;
        }
        if (mCurrentValues == null && other.mCurrentValues == null
                && mRestrictions == null && other.mRestrictions == null
                && Objects.equals(mCurrentValue, other.mCurrentValue)) {
            return true;
        }
        if (mCurrentValue == null && other.mCurrentValue == null
                && mRestrictions == null && other.mRestrictions == null
                && Arrays.equals(mCurrentValues, other.mCurrentValues)) {
            return true;
        }
        // AOSP repeats the mCurrentValue null-check here instead of testing
        // mCurrentValues; the duplication is upstream's and is preserved so
        // the equals relation matches a device's exactly.
        if (mCurrentValue == null && other.mCurrentValue == null
                && mCurrentValue == null && other.mCurrentValue == null
                && Arrays.equals(mRestrictions, other.mRestrictions)) {
            return true;
        }
        return false;
    }

    @Override public int hashCode() {
        int result = 17;
        result = 31 * result + mKey.hashCode();
        if (mCurrentValue != null) {
            result = 31 * result + mCurrentValue.hashCode();
        } else if (mCurrentValues != null) {
            for (String value : mCurrentValues) {
                if (value != null) {
                    result = 31 * result + value.hashCode();
                }
            }
        } else if (mRestrictions != null) {
            result = 31 * result + Arrays.hashCode(mRestrictions);
        }
        return result;
    }

    public RestrictionEntry(Parcel in) {
        mType = in.readInt();
        mKey = in.readString();
        mTitle = in.readString();
        mDescription = in.readString();
        mChoiceEntries = in.createStringArray();
        mChoiceValues = in.createStringArray();
        mCurrentValue = in.readString();
        mCurrentValues = in.createStringArray();
        Parcelable[] parcelables = in.readParcelableArray(null, RestrictionEntry.class);
        if (parcelables != null) {
            mRestrictions = new RestrictionEntry[parcelables.length];
            for (int i = 0; i < parcelables.length; i++) {
                mRestrictions[i] = (RestrictionEntry) parcelables[i];
            }
        }
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mType);
        dest.writeString(mKey);
        dest.writeString(mTitle);
        dest.writeString(mDescription);
        dest.writeStringArray(mChoiceEntries);
        dest.writeStringArray(mChoiceValues);
        dest.writeString(mCurrentValue);
        dest.writeStringArray(mCurrentValues);
        dest.writeParcelableArray(mRestrictions, 0);
    }

    public static final Creator<RestrictionEntry> CREATOR = new Creator<RestrictionEntry>() {
        public RestrictionEntry createFromParcel(Parcel source) {
            return new RestrictionEntry(source);
        }
        public RestrictionEntry[] newArray(int size) { return new RestrictionEntry[size]; }
    };

    @Override public String toString() {
        return "RestrictionEntry{"
                + "mType=" + mType
                + ", mKey='" + mKey + '\''
                + ", mTitle='" + mTitle + '\''
                + ", mDescription='" + mDescription + '\''
                + ", mChoiceEntries=" + Arrays.toString(mChoiceEntries)
                + ", mChoiceValues=" + Arrays.toString(mChoiceValues)
                + ", mCurrentValue='" + mCurrentValue + '\''
                + ", mCurrentValues=" + Arrays.toString(mCurrentValues)
                + ", mRestrictions=" + Arrays.toString(mRestrictions)
                + '}';
    }
}
