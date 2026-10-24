// android.app.AutomaticZenRule -- "Rule instance information for zen mode"
// (do-not-disturb). An app that owns a DND schedule creates one and hands it to
// NotificationManager.addAutomaticZenRule. API 24.
//
// AOSP ref: frameworks/base/core/java/android/app/AutomaticZenRule.java
// (android16-release). It is a plain mutable value object -- name, owner
// component, configuration activity, condition Uri, interruption filter, enabled
// flag, creation time, plus the API-34 type/icon/trigger-description/
// manual-invocation set.
//
// ⚠ PARTIAL, and deliberately so: AOSP additionally exposes
// getZenPolicy/setZenPolicy (android.service.notification.ZenPolicy) and
// getDeviceEffects/setDeviceEffects (android.service.notification.ZenDeviceEffects),
// plus a constructor and Builder methods taking them. NEITHER TYPE EXISTS in
// this tree, and android.service.notification is not this lane's package, so
// those members cannot be declared here yet -- see the lane report. Everything
// that does not name one of those two types is present, which is what lets
// NotificationManager's add/get/update/removeAutomaticZenRule exist at all.
package android.app;

import android.content.ComponentName;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

public final class AutomaticZenRule implements Parcelable {

    /** AOSP TYPE_* (API 34): what kind of rule this is, used by Settings to
     *  group and describe it. */
    public static final int TYPE_UNKNOWN = -1;
    public static final int TYPE_OTHER = 0;
    public static final int TYPE_SCHEDULE_TIME = 1;
    public static final int TYPE_SCHEDULE_CALENDAR = 2;
    public static final int TYPE_BEDTIME = 3;
    public static final int TYPE_DRIVING = 4;
    public static final int TYPE_IMMERSIVE = 5;
    public static final int TYPE_THEATER = 6;
    public static final int TYPE_MANAGED = 7;

    private String mName;
    private ComponentName mOwner;
    private ComponentName mConfigurationActivity;
    private Uri mConditionId;
    private int mInterruptionFilter;
    private boolean mEnabled;
    private long mCreationTime;
    private int mType = TYPE_UNKNOWN;
    private int mIconResId;
    private String mTriggerDescription;
    private boolean mManualInvocationAllowed;

    /** AOSP AutomaticZenRule(String, ComponentName owner, Uri conditionId, int
     *  interruptionFilter, boolean enabled). @deprecated matches AOSP
     *  (superseded by the configuration-activity form below). */
    @Deprecated
    public AutomaticZenRule(String name, ComponentName owner, Uri conditionId,
            int interruptionFilter, boolean enabled) {
        this(name, owner, null, conditionId, interruptionFilter, enabled);
    }

    /** AOSP's API-29 constructor minus the ZenPolicy argument (see the class
     *  comment for why that argument cannot be declared yet). */
    public AutomaticZenRule(String name, ComponentName owner,
            ComponentName configurationActivity, Uri conditionId, int interruptionFilter,
            boolean enabled) {
        mName = name;
        mOwner = owner;
        mConfigurationActivity = configurationActivity;
        mConditionId = conditionId;
        mInterruptionFilter = interruptionFilter;
        mEnabled = enabled;
        mCreationTime = System.currentTimeMillis();
    }

    /** AOSP AutomaticZenRule(Parcel) -- PUBLIC in AOSP, not just a CREATOR
     *  hook. */
    public AutomaticZenRule(Parcel source) {
        if (source == null) return;
        mEnabled = source.readInt() == 1;
        if (source.readInt() == 1) mName = source.readString();
        mInterruptionFilter = source.readInt();
        mConditionId = source.readParcelable(Uri.class.getClassLoader(), Uri.class);
        mOwner = source.readParcelable(ComponentName.class.getClassLoader(), ComponentName.class);
        mConfigurationActivity =
                source.readParcelable(ComponentName.class.getClassLoader(), ComponentName.class);
        mCreationTime = source.readLong();
        mType = source.readInt();
        mIconResId = source.readInt();
        mTriggerDescription = source.readString();
        mManualInvocationAllowed = source.readInt() == 1;
    }

    public String getName() { return mName; }
    public void setName(String name) { mName = name; }

    /** AOSP getOwner(): "the component that owns/manages this rule"
     *  (a ConditionProviderService). */
    public ComponentName getOwner() { return mOwner; }

    /** AOSP getConfigurationActivity(): the activity that lets the user
     *  configure this rule, or null. */
    public ComponentName getConfigurationActivity() { return mConfigurationActivity; }
    public void setConfigurationActivity(ComponentName componentName) {
        mConfigurationActivity = componentName;
    }

    /** AOSP getConditionId(): "the representation of the state that causes this
     *  rule to become active." */
    public Uri getConditionId() { return mConditionId; }
    public void setConditionId(Uri conditionId) { mConditionId = conditionId; }

    /** AOSP getInterruptionFilter(): the NotificationManager.INTERRUPTION_FILTER_*
     *  applied while the rule is active. */
    public int getInterruptionFilter() { return mInterruptionFilter; }
    public void setInterruptionFilter(int interruptionFilter) {
        mInterruptionFilter = interruptionFilter;
    }

    public boolean isEnabled() { return mEnabled; }
    public void setEnabled(boolean enabled) { mEnabled = enabled; }

    /** AOSP getCreationTime(): "the time this rule was created, represented as
     *  milliseconds since the epoch." */
    public long getCreationTime() { return mCreationTime; }

    /** AOSP getType() (API 34): one of the TYPE_* values; TYPE_UNKNOWN unless
     *  the Builder set one. */
    public int getType() { return mType; }

    /** AOSP getIconResId() (API 34): the rule's icon, 0 when unset. */
    public int getIconResId() { return mIconResId; }

    /** AOSP getTriggerDescription() (API 34): a user-visible description of what
     *  triggers this rule, null when unset. */
    public String getTriggerDescription() { return mTriggerDescription; }

    /** AOSP isManualInvocationAllowed() (API 34): whether the user may turn the
     *  rule on by hand. */
    public boolean isManualInvocationAllowed() { return mManualInvocationAllowed; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mEnabled ? 1 : 0);
        if (mName != null) {
            dest.writeInt(1);
            dest.writeString(mName);
        } else {
            dest.writeInt(0);
        }
        dest.writeInt(mInterruptionFilter);
        dest.writeParcelable(mConditionId, 0);
        dest.writeParcelable(mOwner, 0);
        dest.writeParcelable(mConfigurationActivity, 0);
        dest.writeLong(mCreationTime);
        dest.writeInt(mType);
        dest.writeInt(mIconResId);
        dest.writeString(mTriggerDescription);
        dest.writeInt(mManualInvocationAllowed ? 1 : 0);
    }

    @Override public String toString() {
        return "AutomaticZenRule[name=" + mName + ", enabled=" + mEnabled
                + ", interruptionFilter=" + mInterruptionFilter
                + ", conditionId=" + mConditionId + ", owner=" + mOwner + "]";
    }

    @Override public boolean equals(Object o) {
        if (!(o instanceof AutomaticZenRule)) return false;
        AutomaticZenRule other = (AutomaticZenRule) o;
        return mEnabled == other.mEnabled
                && mInterruptionFilter == other.mInterruptionFilter
                && mType == other.mType
                && mIconResId == other.mIconResId
                && mManualInvocationAllowed == other.mManualInvocationAllowed
                && java.util.Objects.equals(mName, other.mName)
                && java.util.Objects.equals(mOwner, other.mOwner)
                && java.util.Objects.equals(mConfigurationActivity, other.mConfigurationActivity)
                && java.util.Objects.equals(mConditionId, other.mConditionId)
                && java.util.Objects.equals(mTriggerDescription, other.mTriggerDescription);
    }

    @Override public int hashCode() {
        return java.util.Objects.hash(mName, mOwner, mConfigurationActivity, mConditionId,
                mInterruptionFilter, mEnabled, mType, mIconResId, mTriggerDescription,
                mManualInvocationAllowed);
    }

    public static final Parcelable.Creator<AutomaticZenRule> CREATOR =
            new Parcelable.Creator<AutomaticZenRule>() {
        @Override public AutomaticZenRule createFromParcel(Parcel source) {
            return new AutomaticZenRule(source);
        }
        @Override public AutomaticZenRule[] newArray(int size) {
            return new AutomaticZenRule[size];
        }
    };

    /** AOSP AutomaticZenRule.Builder (API 34). setZenPolicy / setDeviceEffects
     *  are the two members omitted here; see the class comment. */
    public static final class Builder {
        private final AutomaticZenRule mRule;

        /** AOSP Builder(String name, Uri conditionId). */
        public Builder(String name, Uri conditionId) {
            mRule = new AutomaticZenRule(name, null, null, conditionId,
                    NotificationManager.INTERRUPTION_FILTER_PRIORITY, true);
        }

        /** AOSP Builder(AutomaticZenRule rule): seeds from an existing rule. */
        public Builder(AutomaticZenRule rule) {
            mRule = new AutomaticZenRule(rule.getName(), rule.getOwner(),
                    rule.getConfigurationActivity(), rule.getConditionId(),
                    rule.getInterruptionFilter(), rule.isEnabled());
            mRule.mCreationTime = rule.mCreationTime;
            mRule.mType = rule.mType;
            mRule.mIconResId = rule.mIconResId;
            mRule.mTriggerDescription = rule.mTriggerDescription;
            mRule.mManualInvocationAllowed = rule.mManualInvocationAllowed;
        }

        public Builder setName(String name) { mRule.mName = name; return this; }
        public Builder setOwner(ComponentName owner) { mRule.mOwner = owner; return this; }
        public Builder setConfigurationActivity(ComponentName configurationActivity) {
            mRule.mConfigurationActivity = configurationActivity;
            return this;
        }
        public Builder setConditionId(Uri conditionId) {
            mRule.mConditionId = conditionId;
            return this;
        }
        public Builder setInterruptionFilter(int interruptionFilter) {
            mRule.mInterruptionFilter = interruptionFilter;
            return this;
        }
        public Builder setEnabled(boolean enabled) { mRule.mEnabled = enabled; return this; }
        public Builder setType(int type) { mRule.mType = type; return this; }
        public Builder setIconResId(int iconResId) { mRule.mIconResId = iconResId; return this; }
        public Builder setTriggerDescription(String triggerDescription) {
            mRule.mTriggerDescription = triggerDescription;
            return this;
        }
        public Builder setManualInvocationAllowed(boolean allowed) {
            mRule.mManualInvocationAllowed = allowed;
            return this;
        }
        public AutomaticZenRule build() { return mRule; }
    }
}
