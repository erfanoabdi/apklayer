package android.service.autofill;

/**
 * API 31 summary a service returns from onSavedDatasetsInfoRequest: how many
 * saved entries it holds of a given kind, so Settings can show "12 passwords".
 *
 * Pure value object; nothing about it needs a bound service.
 *
 * Constants transcribed verbatim from /tmp/aosp_api/android16.txt:
 *   TYPE_PASSWORDS = "passwords", TYPE_OTHER = "other"
 *
 * Source: frameworks/base/core/java/android/service/autofill/SavedDatasetsInfo.java
 */
public final class SavedDatasetsInfo {

    public static final String TYPE_PASSWORDS = "passwords";
    public static final String TYPE_OTHER = "other";

    private final String mType;
    private final int mCount;

    public SavedDatasetsInfo(String type, int count) {
        if (type == null) throw new NullPointerException("type");
        if (count < 0) throw new IllegalArgumentException("count must not be negative");
        mType = type;
        mCount = count;
    }

    public String getType() { return mType; }

    public int getCount() { return mCount; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SavedDatasetsInfo)) return false;
        SavedDatasetsInfo other = (SavedDatasetsInfo) o;
        return mCount == other.mCount && mType.equals(other.mType);
    }

    @Override public int hashCode() { return mType.hashCode() * 31 + mCount; }

    @Override public String toString() {
        return "SavedDatasetsInfo[" + mType + "=" + mCount + "]";
    }
}
