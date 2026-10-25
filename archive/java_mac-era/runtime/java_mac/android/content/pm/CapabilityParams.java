package android.content.pm;

/** android.content.pm.CapabilityParams (API 33) -- one named parameter of a
 *  shortcut CAPABILITY BINDING, e.g. the value "Bob" (plus its aliases) for the
 *  parameter "message.recipient.name" of the capability
 *  "actions.intent.SEND_MESSAGE".
 *
 *  App Actions / the assistant read these off a ShortcutInfo to match a spoken
 *  request to an in-app shortcut.
 *
 *  Mirrors frameworks/base/core/java/android/content/pm/CapabilityParams.java
 *  (android16-release): a name, a primary value, and an ordered alias list. */
public final class CapabilityParams implements android.os.Parcelable {
    private final String mName;
    private final String mPrimaryValue;
    private final java.util.List<String> mAliases;

    private CapabilityParams(String name, String primaryValue, java.util.List<String> aliases) {
        mName = name;
        mPrimaryValue = primaryValue;
        mAliases = (aliases == null)
                ? java.util.Collections.<String>emptyList()
                : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(aliases));
    }

    /** AOSP: "the name of this parameter." */
    public String getName() { return mName; }

    /** AOSP: "the primary value of this parameter." */
    public String getValue() { return mPrimaryValue; }

    /** AOSP: "the aliases of this parameter." @NonNull; empty when none. */
    public java.util.List<String> getAliases() { return mAliases; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeString(mName);
        dest.writeString(mPrimaryValue);
        dest.writeStringList(mAliases);
    }

    public static final android.os.Parcelable.Creator<CapabilityParams> CREATOR =
            new android.os.Parcelable.Creator<CapabilityParams>() {
        @Override public CapabilityParams createFromParcel(android.os.Parcel in) {
            String name = in.readString();
            String value = in.readString();
            java.util.List<String> aliases = new java.util.ArrayList<>();
            in.readStringList(aliases);
            return new CapabilityParams(name, value, aliases);
        }
        @Override public CapabilityParams[] newArray(int size) {
            return new CapabilityParams[size];
        }
    };

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CapabilityParams)) return false;
        CapabilityParams other = (CapabilityParams) o;
        return java.util.Objects.equals(mName, other.mName)
                && java.util.Objects.equals(mPrimaryValue, other.mPrimaryValue)
                && mAliases.equals(other.mAliases);
    }
    @Override public int hashCode() {
        return java.util.Objects.hash(mName, mPrimaryValue, mAliases);
    }

    /** AOSP CapabilityParams.Builder: name + primary value are required, and
     *  AOSP rejects a null/empty value at construction time. */
    public static final class Builder {
        private final String mName;
        private final String mPrimaryValue;
        private java.util.List<String> mAliases;

        public Builder(String name, String primaryValue) {
            if (name == null || name.isEmpty()) {
                throw new IllegalArgumentException("Parameter name cannot be empty");
            }
            if (primaryValue == null || primaryValue.isEmpty()) {
                throw new IllegalArgumentException("Parameter value cannot be empty");
            }
            mName = name;
            mPrimaryValue = primaryValue;
        }

        public Builder addAlias(String alias) {
            if (alias == null || alias.isEmpty()) {
                throw new IllegalArgumentException("Parameter value cannot be empty");
            }
            if (mAliases == null) mAliases = new java.util.ArrayList<>(1);
            mAliases.add(alias);
            return this;
        }

        public CapabilityParams build() {
            return new CapabilityParams(mName, mPrimaryValue, mAliases);
        }
    }
}
