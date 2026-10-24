package android.service.notification;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationChannelGroup;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.UserHandle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base for a service that observes every notification posted on the device.
 *
 * WHAT MAC.APK CAN AND CANNOT DO HERE. This is the archetype of a service that
 * cannot function on a single-app host: its whole purpose is to see OTHER
 * apps' notifications, and there are none. Mac.apk never binds it, so
 * onNotificationPosted / onNotificationRemoved / onListenerConnected never
 * fire.
 *
 * That is a state real listeners ALREADY handle: on a device, a listener is
 * unbound until the user grants notification access in Settings, and every
 * accessor below is documented to be safe (returning empty) before
 * onListenerConnected. So each one returns the AOSP not-connected answer --
 * empty array, empty list, INTERRUPTION_FILTER_UNKNOWN, hints 0 -- rather than
 * throwing or pretending. What we do NOT do is fabricate notifications for it
 * to see.
 *
 * Constant values transcribed VERBATIM from /tmp/aosp_api/android16.txt
 * (signature format 2.0). The REASON_* block in particular is a dense run of
 * small integers where a single transposition is invisible; every one below was
 * copied from that file, not recalled.
 *
 * Source: frameworks/base/core/java/android/service/notification/NotificationListenerService.java
 */
public abstract class NotificationListenerService extends android.app.Service {

    public static final String SERVICE_INTERFACE =
            "android.service.notification.NotificationListenerService";
    /** API 34. */
    public static final String ACTION_SETTINGS_HOME =
            "android.service.notification.action.SETTINGS_HOME";
    public static final String META_DATA_DEFAULT_AUTOBIND =
            "android.service.notification.default_autobind_listenerservice";
    public static final String META_DATA_DEFAULT_FILTER_TYPES =
            "android.service.notification.default_filter_types";
    public static final String META_DATA_DISABLED_FILTER_TYPES =
            "android.service.notification.disabled_filter_types";

    // ---- onNotificationRemoved reasons -------------------------------------
    public static final int REASON_CLICK = 1;
    public static final int REASON_CANCEL = 2;
    public static final int REASON_CANCEL_ALL = 3;
    public static final int REASON_ERROR = 4;
    public static final int REASON_PACKAGE_CHANGED = 5;
    public static final int REASON_USER_STOPPED = 6;
    public static final int REASON_PACKAGE_BANNED = 7;
    public static final int REASON_APP_CANCEL = 8;
    public static final int REASON_APP_CANCEL_ALL = 9;
    public static final int REASON_LISTENER_CANCEL = 10;
    public static final int REASON_LISTENER_CANCEL_ALL = 11;
    public static final int REASON_GROUP_SUMMARY_CANCELED = 12;
    public static final int REASON_GROUP_OPTIMIZATION = 13;
    public static final int REASON_PACKAGE_SUSPENDED = 14;
    public static final int REASON_PROFILE_TURNED_OFF = 15;
    public static final int REASON_UNAUTOBUNDLED = 16;
    public static final int REASON_CHANNEL_BANNED = 17;
    public static final int REASON_SNOOZED = 18;
    public static final int REASON_TIMEOUT = 19;
    public static final int REASON_CHANNEL_REMOVED = 20;
    public static final int REASON_CLEAR_DATA = 21;
    public static final int REASON_ASSISTANT_CANCEL = 22;
    public static final int REASON_LOCKDOWN = 23;

    // ---- listener hints -----------------------------------------------------
    public static final int HINT_HOST_DISABLE_EFFECTS = 1;
    public static final int HINT_HOST_DISABLE_NOTIFICATION_EFFECTS = 2;
    public static final int HINT_HOST_DISABLE_CALL_EFFECTS = 4;

    // ---- interruption filters ----------------------------------------------
    public static final int INTERRUPTION_FILTER_UNKNOWN = 0;
    public static final int INTERRUPTION_FILTER_ALL = 1;
    public static final int INTERRUPTION_FILTER_PRIORITY = 2;
    public static final int INTERRUPTION_FILTER_NONE = 3;
    public static final int INTERRUPTION_FILTER_ALARMS = 4;

    // ---- migrateNotificationFilter types (API 33) ---------------------------
    public static final int FLAG_FILTER_TYPE_CONVERSATIONS = 1;
    public static final int FLAG_FILTER_TYPE_ALERTING = 2;
    public static final int FLAG_FILTER_TYPE_SILENT = 4;
    public static final int FLAG_FILTER_TYPE_ONGOING = 8;

    /** @deprecated in AOSP at API 26; superseded by NotificationChannel. */
    @Deprecated public static final int SUPPRESSED_EFFECT_SCREEN_OFF = 1;
    /** @deprecated in AOSP at API 26. */
    @Deprecated public static final int SUPPRESSED_EFFECT_SCREEN_ON = 2;

    private static final StatusBarNotification[] NO_NOTIFICATIONS =
            new StatusBarNotification[0];

    private int mRequestedHints;

    public NotificationListenerService() { }

    /** AOSP returns the listener binder. Service.onBind already returns null
     *  in this tree, so nothing is lost. NOT final in AOSP. */
    @Override public IBinder onBind(Intent intent) { return null; }

    // ---- callbacks the platform would deliver ------------------------------
    // Empty in AOSP too -- a listener overrides only the ones it wants -- so
    // these are override POINTS, not stubbed-out behaviour.
    public void onNotificationPosted(StatusBarNotification sbn) { }

    /** AOSP's default forwards to the single-argument form so a listener that
     *  overrode only that one still receives the notification. Mirror the
     *  forwarding exactly, or overriding the simpler method would silently
     *  stop working. */
    public void onNotificationPosted(StatusBarNotification sbn, RankingMap rankingMap) {
        onNotificationPosted(sbn);
    }

    public void onNotificationRemoved(StatusBarNotification sbn) { }

    public void onNotificationRemoved(StatusBarNotification sbn, RankingMap rankingMap) {
        onNotificationRemoved(sbn);
    }

    /** API 26 reason-carrying overload; AOSP forwards to the two-argument form. */
    public void onNotificationRemoved(StatusBarNotification sbn, RankingMap rankingMap,
            int reason) {
        onNotificationRemoved(sbn, rankingMap);
    }

    public void onListenerConnected() { }

    public void onListenerDisconnected() { }

    public void onNotificationRankingUpdate(RankingMap rankingMap) { }

    public void onListenerHintsChanged(int hints) { }

    public void onInterruptionFilterChanged(int interruptionFilter) { }

    public void onNotificationChannelModified(String pkg, UserHandle user,
            NotificationChannel channel, int modificationType) { }

    public void onNotificationChannelGroupModified(String pkg, UserHandle user,
            NotificationChannelGroup group, int modificationType) { }

    /** API 29. */
    public void onSilentStatusBarIconsVisibilityChanged(boolean hideSilentStatusIcons) { }

    // ---- channel-modification types ----------------------------------------
    public static final int NOTIFICATION_CHANNEL_OR_GROUP_ADDED = 1;
    public static final int NOTIFICATION_CHANNEL_OR_GROUP_UPDATED = 2;
    public static final int NOTIFICATION_CHANNEL_OR_GROUP_DELETED = 3;

    // ---- queries ------------------------------------------------------------
    /** AOSP returns an EMPTY ARRAY (never null) when the listener is not
     *  connected, so a caller's for-each is safe. */
    public StatusBarNotification[] getActiveNotifications() { return NO_NOTIFICATIONS; }

    public StatusBarNotification[] getActiveNotifications(String[] keys) {
        return NO_NOTIFICATIONS;
    }

    /** API 26. */
    public final StatusBarNotification[] getSnoozedNotifications() {
        return NO_NOTIFICATIONS;
    }

    /** AOSP returns an empty RankingMap when not connected, not null. */
    public RankingMap getCurrentRanking() { return new RankingMap(); }

    public final int getCurrentListenerHints() { return mRequestedHints; }

    /** Nothing suppresses anything here, so no filter is in force. AOSP
     *  returns INTERRUPTION_FILTER_UNKNOWN whenever it cannot tell -- including
     *  before the listener is connected. */
    public final int getCurrentInterruptionFilter() { return INTERRUPTION_FILTER_UNKNOWN; }

    public final List<NotificationChannel> getNotificationChannels(String pkg,
            UserHandle user) {
        return Collections.emptyList();
    }

    public final List<NotificationChannelGroup> getNotificationChannelGroups(String pkg,
            UserHandle user) {
        return Collections.emptyList();
    }

    // ---- requests -----------------------------------------------------------
    // Every one of these asks the notification manager to do something on the
    // listener's behalf. There is no notification manager listening, so they do
    // nothing -- which is AOSP's own behaviour for an unbound listener.
    public final void cancelAllNotifications() { }

    /** @deprecated at API 21 in favour of cancelNotification(String key). */
    @Deprecated
    public final void cancelNotification(String pkg, String tag, int id) { }

    public final void cancelNotification(String key) { }

    public final void cancelNotifications(String[] keys) { }

    public final void setNotificationsShown(String[] keys) { }

    /** API 26. */
    public final void snoozeNotification(String key, long durationMs) { }

    /** ⚠ ASSIGNS the requested hints so getCurrentListenerHints() round-trips.
     *  AOSP's hints come back from the system, but the request is the only half
     *  observable here, and dropping it would make the getter contradict the
     *  setter. */
    public final void requestListenerHints(int hints) { mRequestedHints = hints; }

    public final void clearRequestedListenerHints() { mRequestedHints = 0; }

    public final void requestInterruptionFilter(int interruptionFilter) { }

    public final void requestUnbind() { }

    public static void requestRebind(ComponentName componentName) { }

    /** API 34 static overload. */
    public static void requestUnbind(ComponentName componentName) { }

    /** API 26. */
    public final void updateNotificationChannel(String pkg, UserHandle user,
            NotificationChannel channel) { }

    /** API 36. AOSP creates the channel on the target app's behalf and returns
     *  it, or null on failure. There is no other app here, so null -- the
     *  documented failure answer. */
    public final NotificationChannel createConversationNotificationChannelForPackage(
            String pkg, UserHandle user, String parentChannelId, String conversationId) {
        return null;
    }

    /** API 33. */
    public final void migrateNotificationFilter(int defaultTypes,
            List<String> disallowedPkgs) { }

    // =========================================================================
    // Ranking / RankingMap
    // =========================================================================

    /**
     * Where one notification sits in the shade's ordering, and what the ranker
     * decided about it. A mutable holder in AOSP too: getRanking() POPULATES a
     * caller-supplied Ranking rather than allocating one, which is why the
     * public no-arg constructor exists.
     */
    public static class Ranking {

        public static final int USER_SENTIMENT_NEGATIVE = -1;
        public static final int USER_SENTIMENT_NEUTRAL = 0;
        public static final int USER_SENTIMENT_POSITIVE = 1;
        /** AOSP: -1000 (0xfffffc18), meaning "no lockscreen override". NOT -1. */
        public static final int VISIBILITY_NO_OVERRIDE = -1000;

        private String mKey;
        private int mRank = -1;
        private boolean mIsAmbient;
        private boolean mMatchesInterruptionFilter = true;
        private int mVisibilityOverride = VISIBILITY_NO_OVERRIDE;
        private int mSuppressedVisualEffects;
        private int mImportance;
        private CharSequence mImportanceExplanation;
        private String mOverrideGroupKey;
        private NotificationChannel mChannel;
        private List<CharSequence> mSmartReplies = new ArrayList<CharSequence>();
        private List<Notification.Action> mSmartActions =
                new ArrayList<Notification.Action>();
        private boolean mCanShowBadge;
        private int mUserSentiment = USER_SENTIMENT_NEUTRAL;
        private boolean mIsSuspended;
        private long mLastAudiblyAlertedMs;
        private boolean mCanBubble;
        private boolean mIsConversation;
        private ShortcutInfo mConversationShortcutInfo;
        private String mSummarization;

        public Ranking() { }

        public String getKey() { return mKey; }

        /** AOSP returns -1 for a Ranking that was never populated. */
        public int getRank() { return mRank; }

        public boolean isAmbient() { return mIsAmbient; }

        public boolean matchesInterruptionFilter() { return mMatchesInterruptionFilter; }

        public int getLockscreenVisibilityOverride() { return mVisibilityOverride; }

        public int getSuppressedVisualEffects() { return mSuppressedVisualEffects; }

        public int getImportance() { return mImportance; }

        public CharSequence getImportanceExplanation() { return mImportanceExplanation; }

        public String getOverrideGroupKey() { return mOverrideGroupKey; }

        public NotificationChannel getChannel() { return mChannel; }

        public List<CharSequence> getSmartReplies() { return mSmartReplies; }

        public List<Notification.Action> getSmartActions() { return mSmartActions; }

        public boolean canShowBadge() { return mCanShowBadge; }

        public int getUserSentiment() { return mUserSentiment; }

        public boolean isSuspended() { return mIsSuspended; }

        public long getLastAudiblyAlertedMillis() { return mLastAudiblyAlertedMs; }

        public boolean canBubble() { return mCanBubble; }

        /** API 30. */
        public boolean isConversation() { return mIsConversation; }

        public ShortcutInfo getConversationShortcutInfo() { return mConversationShortcutInfo; }

        /** API 36. Null when the assistant produced no summary -- which is the
         *  normal answer on a device with no notification assistant. */
        public String getSummarization() { return mSummarization; }

        /** AOSP's @hide populate(); package-visible so RankingMap can fill a
         *  caller's Ranking without leaking a setter into the public API. */
        void populate(Ranking other) {
            mKey = other.mKey;
            mRank = other.mRank;
            mIsAmbient = other.mIsAmbient;
            mMatchesInterruptionFilter = other.mMatchesInterruptionFilter;
            mVisibilityOverride = other.mVisibilityOverride;
            mSuppressedVisualEffects = other.mSuppressedVisualEffects;
            mImportance = other.mImportance;
            mImportanceExplanation = other.mImportanceExplanation;
            mOverrideGroupKey = other.mOverrideGroupKey;
            mChannel = other.mChannel;
            mSmartReplies = other.mSmartReplies;
            mSmartActions = other.mSmartActions;
            mCanShowBadge = other.mCanShowBadge;
            mUserSentiment = other.mUserSentiment;
            mIsSuspended = other.mIsSuspended;
            mLastAudiblyAlertedMs = other.mLastAudiblyAlertedMs;
            mCanBubble = other.mCanBubble;
            mIsConversation = other.mIsConversation;
            mConversationShortcutInfo = other.mConversationShortcutInfo;
            mSummarization = other.mSummarization;
        }
    }

    /**
     * The full ordering, keyed by notification key. Empty here because nothing
     * is posted; getRanking() therefore returns false and leaves the caller's
     * Ranking untouched, which is exactly AOSP's contract for an unknown key.
     */
    public static class RankingMap implements Parcelable {

        private final java.util.LinkedHashMap<String, Ranking> mRankings =
                new java.util.LinkedHashMap<String, Ranking>();

        public RankingMap() { }

        public String[] getOrderedKeys() {
            return mRankings.keySet().toArray(new String[0]);
        }

        /** ⚠ POPULATES `outRanking` and returns whether the key was known. A
         *  version that returned true without populating would hand the caller
         *  a stale Ranking it believes is fresh. */
        public boolean getRanking(String key, Ranking outRanking) {
            Ranking r = key == null ? null : mRankings.get(key);
            if (r == null || outRanking == null) return false;
            outRanking.populate(r);
            return true;
        }

        @Override public int describeContents() { return 0; }

        @Override public void writeToParcel(Parcel dest, int flags) {
            // AOSP writes a ParceledListSlice of NotificationRankingUpdate,
            // which is @hide. The observable public shape is the key order, so
            // that is what crosses the parcel.
            dest.writeStringArray(getOrderedKeys());
        }

        public static final Creator<RankingMap> CREATOR = new Creator<RankingMap>() {
            @Override public RankingMap createFromParcel(Parcel source) {
                RankingMap map = new RankingMap();
                String[] keys = source.createStringArray();
                if (keys != null) {
                    for (String k : keys) {
                        Ranking r = new Ranking();
                        r.mKey = k;
                        map.mRankings.put(k, r);
                    }
                }
                return map;
            }
            @Override public RankingMap[] newArray(int size) { return new RankingMap[size]; }
        };
    }
}
