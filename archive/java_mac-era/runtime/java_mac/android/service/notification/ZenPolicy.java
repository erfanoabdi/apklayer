package android.service.notification;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * The per-category rules of a Do-Not-Disturb (zen) mode: which senders may
 * still ring, which visual effects are still shown.
 *
 * An app builds one of these to attach to an AutomaticZenRule
 * (NotificationManager.addAutomaticZenRule). Mac.apk enforces no zen mode --
 * nothing here is muted on our behalf -- but the policy is a pure value object
 * and its builder round-trip is exact, so an app that constructs a policy,
 * reads its own getters back, and stores it behaves as it does on a device.
 *
 * Every field defaults to STATE_UNSET / PEOPLE_TYPE_UNSET, which in AOSP means
 * "inherit from the global policy"; that is the correct default for a policy
 * whose builder was not told otherwise.
 *
 * Constant values transcribed VERBATIM from /tmp/aosp_api/android16.txt
 * (signature format 2.0):
 *   STATE_UNSET=0, STATE_ALLOW=1, STATE_DISALLOW=2
 *   PEOPLE_TYPE_UNSET=0, PEOPLE_TYPE_ANYONE=1, PEOPLE_TYPE_CONTACTS=2,
 *   PEOPLE_TYPE_STARRED=3, PEOPLE_TYPE_NONE=4
 *   CONVERSATION_SENDERS_UNSET=0, CONVERSATION_SENDERS_ANYONE=1,
 *   CONVERSATION_SENDERS_IMPORTANT=2, CONVERSATION_SENDERS_NONE=3
 * Note PEOPLE_TYPE_NONE is 4, NOT 0 -- an easy transcription slip because
 * "none" reads like a zero.
 *
 * Source: frameworks/base/core/java/android/service/notification/ZenPolicy.java
 */
public final class ZenPolicy implements Parcelable {

    public static final int STATE_UNSET = 0;
    public static final int STATE_ALLOW = 1;
    public static final int STATE_DISALLOW = 2;

    public static final int PEOPLE_TYPE_UNSET = 0;
    public static final int PEOPLE_TYPE_ANYONE = 1;
    public static final int PEOPLE_TYPE_CONTACTS = 2;
    public static final int PEOPLE_TYPE_STARRED = 3;
    public static final int PEOPLE_TYPE_NONE = 4;

    public static final int CONVERSATION_SENDERS_UNSET = 0;
    public static final int CONVERSATION_SENDERS_ANYONE = 1;
    public static final int CONVERSATION_SENDERS_IMPORTANT = 2;
    public static final int CONVERSATION_SENDERS_NONE = 3;

    private int mPriorityCategoryReminders = STATE_UNSET;
    private int mPriorityCategoryEvents = STATE_UNSET;
    private int mPriorityCategoryMessages = STATE_UNSET;
    private int mPriorityCategoryCalls = STATE_UNSET;
    private int mPriorityCategoryRepeatCallers = STATE_UNSET;
    private int mPriorityCategoryAlarms = STATE_UNSET;
    private int mPriorityCategoryMedia = STATE_UNSET;
    private int mPriorityCategorySystem = STATE_UNSET;
    private int mPriorityCategoryConversations = STATE_UNSET;
    private int mPriorityCallSenders = PEOPLE_TYPE_UNSET;
    private int mPriorityMessageSenders = PEOPLE_TYPE_UNSET;
    private int mPriorityConversationSenders = CONVERSATION_SENDERS_UNSET;
    private int mPriorityChannelsAllowed = STATE_UNSET;

    private int mVisualEffectFullScreenIntent = STATE_UNSET;
    private int mVisualEffectLights = STATE_UNSET;
    private int mVisualEffectPeek = STATE_UNSET;
    private int mVisualEffectStatusBar = STATE_UNSET;
    private int mVisualEffectBadge = STATE_UNSET;
    private int mVisualEffectAmbient = STATE_UNSET;
    private int mVisualEffectNotificationList = STATE_UNSET;

    // AOSP's constructor is package-private; a policy comes from the Builder or
    // from the CREATOR.
    private ZenPolicy() { }

    public int getPriorityCategoryReminders() { return mPriorityCategoryReminders; }
    public int getPriorityCategoryEvents() { return mPriorityCategoryEvents; }
    public int getPriorityCategoryMessages() { return mPriorityCategoryMessages; }
    public int getPriorityCategoryCalls() { return mPriorityCategoryCalls; }
    public int getPriorityCategoryRepeatCallers() { return mPriorityCategoryRepeatCallers; }
    public int getPriorityCategoryAlarms() { return mPriorityCategoryAlarms; }
    public int getPriorityCategoryMedia() { return mPriorityCategoryMedia; }
    public int getPriorityCategorySystem() { return mPriorityCategorySystem; }
    public int getPriorityCategoryConversations() { return mPriorityCategoryConversations; }
    public int getPriorityCallSenders() { return mPriorityCallSenders; }
    public int getPriorityMessageSenders() { return mPriorityMessageSenders; }
    public int getPriorityConversationSenders() { return mPriorityConversationSenders; }
    /** API 35. */
    public int getPriorityChannelsAllowed() { return mPriorityChannelsAllowed; }

    public int getVisualEffectFullScreenIntent() { return mVisualEffectFullScreenIntent; }
    public int getVisualEffectLights() { return mVisualEffectLights; }
    public int getVisualEffectPeek() { return mVisualEffectPeek; }
    public int getVisualEffectStatusBar() { return mVisualEffectStatusBar; }
    public int getVisualEffectBadge() { return mVisualEffectBadge; }
    public int getVisualEffectAmbient() { return mVisualEffectAmbient; }
    public int getVisualEffectNotificationList() { return mVisualEffectNotificationList; }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        for (int v : snapshot()) dest.writeInt(v);
    }

    private int[] snapshot() {
        return new int[] {
            mPriorityCategoryReminders, mPriorityCategoryEvents,
            mPriorityCategoryMessages, mPriorityCategoryCalls,
            mPriorityCategoryRepeatCallers, mPriorityCategoryAlarms,
            mPriorityCategoryMedia, mPriorityCategorySystem,
            mPriorityCategoryConversations, mPriorityCallSenders,
            mPriorityMessageSenders, mPriorityConversationSenders,
            mPriorityChannelsAllowed, mVisualEffectFullScreenIntent,
            mVisualEffectLights, mVisualEffectPeek, mVisualEffectStatusBar,
            mVisualEffectBadge, mVisualEffectAmbient, mVisualEffectNotificationList
        };
    }

    private void restore(int[] v) {
        int i = 0;
        mPriorityCategoryReminders = v[i++];
        mPriorityCategoryEvents = v[i++];
        mPriorityCategoryMessages = v[i++];
        mPriorityCategoryCalls = v[i++];
        mPriorityCategoryRepeatCallers = v[i++];
        mPriorityCategoryAlarms = v[i++];
        mPriorityCategoryMedia = v[i++];
        mPriorityCategorySystem = v[i++];
        mPriorityCategoryConversations = v[i++];
        mPriorityCallSenders = v[i++];
        mPriorityMessageSenders = v[i++];
        mPriorityConversationSenders = v[i++];
        mPriorityChannelsAllowed = v[i++];
        mVisualEffectFullScreenIntent = v[i++];
        mVisualEffectLights = v[i++];
        mVisualEffectPeek = v[i++];
        mVisualEffectStatusBar = v[i++];
        mVisualEffectBadge = v[i++];
        mVisualEffectAmbient = v[i++];
        mVisualEffectNotificationList = v[i++];
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ZenPolicy)) return false;
        return java.util.Arrays.equals(snapshot(), ((ZenPolicy) o).snapshot());
    }

    @Override public int hashCode() { return java.util.Arrays.hashCode(snapshot()); }

    @Override public String toString() {
        return "ZenPolicy" + java.util.Arrays.toString(snapshot());
    }

    public static final Creator<ZenPolicy> CREATOR = new Creator<ZenPolicy>() {
        @Override public ZenPolicy createFromParcel(Parcel source) {
            ZenPolicy p = new ZenPolicy();
            int[] v = new int[p.snapshot().length];
            for (int i = 0; i < v.length; i++) v[i] = source.readInt();
            p.restore(v);
            return p;
        }
        @Override public ZenPolicy[] newArray(int size) { return new ZenPolicy[size]; }
    };

    /** Fields live on the builder; build() snapshots into a fresh policy so a
     *  later setter cannot mutate a policy already handed out. */
    public static final class Builder {
        private final ZenPolicy mPolicy = new ZenPolicy();

        public Builder() { }

        public Builder allowReminders(boolean allow) {
            mPolicy.mPriorityCategoryReminders = state(allow);
            return this;
        }

        public Builder allowEvents(boolean allow) {
            mPolicy.mPriorityCategoryEvents = state(allow);
            return this;
        }

        /** AOSP also clears the sender type when messages are disallowed, so a
         *  policy cannot say "no messages, but from contacts". */
        public Builder allowMessages(int senders) {
            if (senders == PEOPLE_TYPE_UNSET) {
                mPolicy.mPriorityCategoryMessages = STATE_UNSET;
                mPolicy.mPriorityMessageSenders = PEOPLE_TYPE_UNSET;
            } else if (senders == PEOPLE_TYPE_NONE) {
                mPolicy.mPriorityCategoryMessages = STATE_DISALLOW;
                mPolicy.mPriorityMessageSenders = PEOPLE_TYPE_NONE;
            } else {
                mPolicy.mPriorityCategoryMessages = STATE_ALLOW;
                mPolicy.mPriorityMessageSenders = senders;
            }
            return this;
        }

        public Builder allowCalls(int senders) {
            if (senders == PEOPLE_TYPE_UNSET) {
                mPolicy.mPriorityCategoryCalls = STATE_UNSET;
                mPolicy.mPriorityCallSenders = PEOPLE_TYPE_UNSET;
            } else if (senders == PEOPLE_TYPE_NONE) {
                mPolicy.mPriorityCategoryCalls = STATE_DISALLOW;
                mPolicy.mPriorityCallSenders = PEOPLE_TYPE_NONE;
            } else {
                mPolicy.mPriorityCategoryCalls = STATE_ALLOW;
                mPolicy.mPriorityCallSenders = senders;
            }
            return this;
        }

        public Builder allowConversations(int senders) {
            if (senders == CONVERSATION_SENDERS_UNSET) {
                mPolicy.mPriorityCategoryConversations = STATE_UNSET;
                mPolicy.mPriorityConversationSenders = CONVERSATION_SENDERS_UNSET;
            } else if (senders == CONVERSATION_SENDERS_NONE) {
                mPolicy.mPriorityCategoryConversations = STATE_DISALLOW;
                mPolicy.mPriorityConversationSenders = CONVERSATION_SENDERS_NONE;
            } else {
                mPolicy.mPriorityCategoryConversations = STATE_ALLOW;
                mPolicy.mPriorityConversationSenders = senders;
            }
            return this;
        }

        public Builder allowRepeatCallers(boolean allow) {
            mPolicy.mPriorityCategoryRepeatCallers = state(allow);
            return this;
        }

        public Builder allowAlarms(boolean allow) {
            mPolicy.mPriorityCategoryAlarms = state(allow);
            return this;
        }

        public Builder allowMedia(boolean allow) {
            mPolicy.mPriorityCategoryMedia = state(allow);
            return this;
        }

        public Builder allowSystem(boolean allow) {
            mPolicy.mPriorityCategorySystem = state(allow);
            return this;
        }

        /** API 35. */
        public Builder allowPriorityChannels(boolean allow) {
            mPolicy.mPriorityChannelsAllowed = state(allow);
            return this;
        }

        public Builder allowAllSounds() {
            allowAlarms(true);
            allowMedia(true);
            allowSystem(true);
            allowReminders(true);
            allowEvents(true);
            allowRepeatCallers(true);
            allowConversations(CONVERSATION_SENDERS_ANYONE);
            allowCalls(PEOPLE_TYPE_ANYONE);
            allowMessages(PEOPLE_TYPE_ANYONE);
            return this;
        }

        public Builder disallowAllSounds() {
            allowAlarms(false);
            allowMedia(false);
            allowSystem(false);
            allowReminders(false);
            allowEvents(false);
            allowRepeatCallers(false);
            allowConversations(CONVERSATION_SENDERS_NONE);
            allowCalls(PEOPLE_TYPE_NONE);
            allowMessages(PEOPLE_TYPE_NONE);
            return this;
        }

        public Builder showFullScreenIntent(boolean show) {
            mPolicy.mVisualEffectFullScreenIntent = state(show);
            return this;
        }

        public Builder showLights(boolean show) {
            mPolicy.mVisualEffectLights = state(show);
            return this;
        }

        public Builder showPeeking(boolean show) {
            mPolicy.mVisualEffectPeek = state(show);
            return this;
        }

        public Builder showStatusBarIcons(boolean show) {
            mPolicy.mVisualEffectStatusBar = state(show);
            return this;
        }

        public Builder showBadges(boolean show) {
            mPolicy.mVisualEffectBadge = state(show);
            return this;
        }

        public Builder showInAmbientDisplay(boolean show) {
            mPolicy.mVisualEffectAmbient = state(show);
            return this;
        }

        public Builder showInNotificationList(boolean show) {
            mPolicy.mVisualEffectNotificationList = state(show);
            return this;
        }

        public Builder showAllVisualEffects() {
            showFullScreenIntent(true);
            showLights(true);
            showPeeking(true);
            showStatusBarIcons(true);
            showBadges(true);
            showInAmbientDisplay(true);
            showInNotificationList(true);
            return this;
        }

        public Builder hideAllVisualEffects() {
            showFullScreenIntent(false);
            showLights(false);
            showPeeking(false);
            showStatusBarIcons(false);
            showBadges(false);
            showInAmbientDisplay(false);
            showInNotificationList(false);
            return this;
        }

        public ZenPolicy build() {
            ZenPolicy copy = new ZenPolicy();
            copy.restore(mPolicy.snapshot());
            return copy;
        }

        private static int state(boolean allow) {
            return allow ? STATE_ALLOW : STATE_DISALLOW;
        }
    }
}
