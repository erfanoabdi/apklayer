package android.service.autofill;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The service's own labelled sample values, used by the platform's field
 * classification pass to guess what an unfamiliar field holds.
 *
 * ⚠ The five static limits are NOT free-choice numbers. On a device they come
 * from DeviceConfig (autofill_user_data_max_*), which is why AOSP exposes them
 * as methods rather than constants -- a service reads them at runtime and sizes
 * its data accordingly. There is no DeviceConfig here, so we return AOSP's
 * shipped defaults, which is what a device with no override reports:
 *   maxUserDataSize=50, maxFieldClassificationIdsSize=10, maxCategoryCount=10,
 *   minValueLength=3, maxValueLength=100.
 * The BUILDER ENFORCES THEM, exactly as AOSP does; a builder that accepted
 * anything would let a service ship data a real device rejects.
 *
 * Source: frameworks/base/core/java/android/service/autofill/UserData.java
 */
public final class UserData implements Parcelable {

    // AOSP defaults, frameworks/base/core/java/android/service/autofill/UserData.java
    // (DEFAULT_MAX_USER_DATA_SIZE and friends).
    private static final int DEFAULT_MAX_USER_DATA_SIZE = 50;
    private static final int DEFAULT_MAX_CATEGORY_COUNT = 10;
    private static final int DEFAULT_MAX_FIELD_CLASSIFICATION_IDS_SIZE = 10;
    private static final int DEFAULT_MIN_VALUE_LENGTH = 3;
    private static final int DEFAULT_MAX_VALUE_LENGTH = 100;

    private final String mId;
    private final String[] mCategoryIds;
    private final String[] mValues;
    private final String mDefaultAlgorithm;
    private final Bundle mDefaultArgs;
    private final Map<String, String> mCategoryAlgorithms;
    private final Map<String, Bundle> mCategoryArgs;

    private UserData(Builder builder) {
        mId = builder.mId;
        mCategoryIds = builder.mCategoryIds.toArray(new String[0]);
        mValues = builder.mValues.toArray(new String[0]);
        mDefaultAlgorithm = builder.mDefaultAlgorithm;
        mDefaultArgs = builder.mDefaultArgs;
        mCategoryAlgorithms = builder.mCategoryAlgorithms;
        mCategoryArgs = builder.mCategoryArgs;
    }

    public String getId() { return mId; }

    public String getFieldClassificationAlgorithm() { return mDefaultAlgorithm; }

    /** API 29. AOSP falls back to the default algorithm when the category has
     *  no specific one, which is the answer a caller expects. */
    public String getFieldClassificationAlgorithmForCategory(String categoryId) {
        if (categoryId == null) throw new NullPointerException("categoryId");
        final String algorithm = mCategoryAlgorithms.get(categoryId);
        return algorithm != null ? algorithm : mDefaultAlgorithm;
    }

    public static int getMaxUserDataSize() { return DEFAULT_MAX_USER_DATA_SIZE; }

    public static int getMaxFieldClassificationIdsSize() {
        return DEFAULT_MAX_FIELD_CLASSIFICATION_IDS_SIZE;
    }

    public static int getMaxCategoryCount() { return DEFAULT_MAX_CATEGORY_COUNT; }

    public static int getMinValueLength() { return DEFAULT_MIN_VALUE_LENGTH; }

    public static int getMaxValueLength() { return DEFAULT_MAX_VALUE_LENGTH; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeString(mId);
        parcel.writeStringArray(mCategoryIds);
        parcel.writeStringArray(mValues);
        parcel.writeString(mDefaultAlgorithm);
        parcel.writeBundle(mDefaultArgs);
        parcel.writeInt(mCategoryAlgorithms.size());
        for (Map.Entry<String, String> e : mCategoryAlgorithms.entrySet()) {
            parcel.writeString(e.getKey());
            parcel.writeString(e.getValue());
            parcel.writeBundle(mCategoryArgs.get(e.getKey()));
        }
    }

    @Override public String toString() {
        return "UserData[id=" + mId + ", entries=" + mValues.length + "]";
    }

    public static final Creator<UserData> CREATOR = new Creator<UserData>() {
        @Override public UserData createFromParcel(Parcel parcel) {
            final String id = parcel.readString();
            final String[] categoryIds = parcel.createStringArray();
            final String[] values = parcel.createStringArray();
            final Builder builder = new Builder(id, values[0], categoryIds[0]);
            for (int i = 1; i < categoryIds.length; i++) {
                builder.add(values[i], categoryIds[i]);
            }
            final String defaultAlgorithm = parcel.readString();
            final Bundle defaultArgs = parcel.readBundle();
            if (defaultAlgorithm != null || defaultArgs != null) {
                builder.setFieldClassificationAlgorithm(defaultAlgorithm, defaultArgs);
            }
            final int n = parcel.readInt();
            for (int i = 0; i < n; i++) {
                final String categoryId = parcel.readString();
                final String algorithm = parcel.readString();
                final Bundle args = parcel.readBundle();
                builder.setFieldClassificationAlgorithmForCategory(categoryId, algorithm, args);
            }
            return builder.build();
        }
        @Override public UserData[] newArray(int size) { return new UserData[size]; }
    };

    public static final class Builder {
        private final String mId;
        private final List<String> mCategoryIds = new ArrayList<String>();
        private final List<String> mValues = new ArrayList<String>();
        private final Map<String, String> mCategoryAlgorithms =
                new HashMap<String, String>();
        private final Map<String, Bundle> mCategoryArgs = new HashMap<String, Bundle>();
        private String mDefaultAlgorithm;
        private Bundle mDefaultArgs;
        private boolean mDestroyed;

        public Builder(String id, String value, String categoryId) {
            if (id == null || id.length() == 0) {
                throw new IllegalArgumentException("id cannot be empty");
            }
            mId = id;
            addInternal(value, categoryId);
        }

        public Builder add(String value, String categoryId) {
            throwIfDestroyed();
            addInternal(value, categoryId);
            return this;
        }

        public Builder setFieldClassificationAlgorithm(String name, Bundle args) {
            throwIfDestroyed();
            mDefaultAlgorithm = name;
            mDefaultArgs = args;
            return this;
        }

        /** API 29. */
        public Builder setFieldClassificationAlgorithmForCategory(String categoryId,
                String name, Bundle args) {
            throwIfDestroyed();
            if (categoryId == null) throw new NullPointerException("categoryId");
            if (name != null) mCategoryAlgorithms.put(categoryId, name);
            if (args != null) mCategoryArgs.put(categoryId, args);
            return this;
        }

        public UserData build() {
            throwIfDestroyed();
            mDestroyed = true;
            return new UserData(this);
        }

        // AOSP's checks, reproduced: a value outside [min,max] length, an empty
        // category id, more than maxUserDataSize entries, or more than
        // maxCategoryCount distinct categories all throw.
        private void addInternal(String value, String categoryId) {
            if (value == null) throw new NullPointerException("value");
            if (categoryId == null) throw new NullPointerException("categoryId");
            if (categoryId.length() == 0) {
                throw new IllegalArgumentException("categoryId cannot be empty");
            }
            final int len = value.length();
            if (len < getMinValueLength()) {
                throw new IllegalArgumentException("value must have at least "
                        + getMinValueLength() + " chars");
            }
            if (len > getMaxValueLength()) {
                throw new IllegalArgumentException("value must have at most "
                        + getMaxValueLength() + " chars");
            }
            if (mValues.size() >= getMaxUserDataSize()) {
                throw new IllegalStateException("already added " + mValues.size()
                        + " elements");
            }
            if (!mCategoryIds.contains(categoryId)
                    && distinctCategoryCount() >= getMaxCategoryCount()) {
                throw new IllegalStateException("already added " + distinctCategoryCount()
                        + " categories");
            }
            mValues.add(value);
            mCategoryIds.add(categoryId);
        }

        private int distinctCategoryCount() {
            return new java.util.HashSet<String>(mCategoryIds).size();
        }

        private void throwIfDestroyed() {
            if (mDestroyed) throw new IllegalStateException("Already called build()");
        }
    }
}
