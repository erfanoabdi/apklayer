// android.app.admin.FactoryResetProtectionPolicy -- "The factory reset
// protection policy determines which accounts can unlock a device that has gone
// through untrusted factory reset." Set by a device owner. API 30.
//
// AOSP ref: frameworks/base/core/java/android/app/admin/
// FactoryResetProtectionPolicy.java (android16-release).
package android.app.admin;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class FactoryResetProtectionPolicy implements Parcelable {

    private final List<String> mFactoryResetProtectionAccounts;
    private final boolean mFactoryResetProtectionEnabled;

    private FactoryResetProtectionPolicy(List<String> accounts, boolean enabled) {
        mFactoryResetProtectionAccounts = accounts;
        mFactoryResetProtectionEnabled = enabled;
    }

    private FactoryResetProtectionPolicy(Parcel in) {
        mFactoryResetProtectionAccounts = new ArrayList<String>();
        in.readStringList(mFactoryResetProtectionAccounts);
        mFactoryResetProtectionEnabled = in.readBoolean();
    }

    /** AOSP getFactoryResetProtectionAccounts(): "the list of accounts that can
     *  unlock a factory-reset-protected device", never null. */
    public List<String> getFactoryResetProtectionAccounts() {
        return Collections.unmodifiableList(mFactoryResetProtectionAccounts);
    }

    /** AOSP isFactoryResetProtectionEnabled(): AOSP's Builder default is TRUE
     *  -- an FRP policy with no accounts still keeps FRP on unless the admin
     *  explicitly disables it. */
    public boolean isFactoryResetProtectionEnabled() { return mFactoryResetProtectionEnabled; }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeStringList(mFactoryResetProtectionAccounts);
        dest.writeBoolean(mFactoryResetProtectionEnabled);
    }

    public static final Parcelable.Creator<FactoryResetProtectionPolicy> CREATOR =
            new Parcelable.Creator<FactoryResetProtectionPolicy>() {
        @Override public FactoryResetProtectionPolicy createFromParcel(Parcel in) {
            return new FactoryResetProtectionPolicy(in);
        }
        @Override public FactoryResetProtectionPolicy[] newArray(int size) {
            return new FactoryResetProtectionPolicy[size];
        }
    };

    /** AOSP FactoryResetProtectionPolicy.Builder. */
    public static class Builder {
        private List<String> mAccounts = new ArrayList<String>();
        private boolean mEnabled = true;
        public Builder() { }
        public Builder setFactoryResetProtectionAccounts(List<String> accounts) {
            mAccounts = new ArrayList<String>(accounts);
            return this;
        }
        public Builder setFactoryResetProtectionEnabled(boolean enabled) {
            mEnabled = enabled;
            return this;
        }
        public FactoryResetProtectionPolicy build() {
            return new FactoryResetProtectionPolicy(mAccounts, mEnabled);
        }
    }
}
