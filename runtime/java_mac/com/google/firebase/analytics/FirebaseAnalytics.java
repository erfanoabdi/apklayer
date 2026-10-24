package com.google.firebase.analytics;

import android.content.Context;
import android.os.Bundle;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import java.util.Map;
import java.util.UUID;

// FirebaseAnalytics no-op stub. All telemetry methods do nothing;
// callers can't tell the difference from a real SDK that's been
// silenced via setAnalyticsCollectionEnabled(false), which is the
// most charitable interpretation of "Firebase APIs work but nothing
// gets sent." No network traffic, no persistent state beyond the
// in-memory App Instance ID we mint per-process.
public final class FirebaseAnalytics {
    private static volatile FirebaseAnalytics sInstance;

    /** Stable per-process App Instance ID. Real Firebase persists this
     *  across launches in shared preferences; we don't bother because
     *  apps that read it use it as an opaque identifier, not a stable-
     *  across-installs one. A fresh UUID per process is enough. */
    private final String appInstanceId = UUID.randomUUID().toString();

    private FirebaseAnalytics() {}

    public static FirebaseAnalytics getInstance(Context context) {
        if (sInstance == null) {
            synchronized (FirebaseAnalytics.class) {
                if (sInstance == null) sInstance = new FirebaseAnalytics();
            }
        }
        return sInstance;
    }

    public void logEvent(String name, Bundle params) {
        // no-op. Per the Mac.apk Firebase strategy: never log to
        // network. We deliberately don't even log to host stderr by
        // default to keep boot logs readable; turn on verbose tracing
        // (todo: MACAPK_FIREBASE_TRACE=1) when debugging.
    }
    public void setUserProperty(String name, String value) {}
    public void setUserId(String id) {}
    public void setAnalyticsCollectionEnabled(boolean enabled) {}
    public void setSessionTimeoutDuration(long milliseconds) {}
    public void setDefaultEventParameters(Bundle parameters) {}

    // Consent Mode v2. The real Firebase API is
    // setConsent(Map<ConsentType, ConsentStatus>); the Firebase C++ SDK
    // (analytics_android.cc) caches the JNI method id for
    // "setConsent","(Ljava/util/Map;)V" and the ConsentType/ConsentStatus
    // static enum fields, then builds the map and calls this. Without the
    // method (and the nested enums it looks up the keys/values from), the
    // C++ side's GetMethodID throws NoSuchMethodError on the Unity main
    // thread. We mirror the real public surface and no-op the body --
    // consistent with our "analytics silenced" stance. App-agnostic.
    public void setConsent(Map<ConsentType, ConsentStatus> consentSettings) {}

    // Real Firebase nested enums. The Firebase C++ SDK resolves these via
    // GetStaticFieldID on FirebaseAnalytics$ConsentType / $ConsentStatus
    // to construct the consent map keys/values before the setConsent call,
    // so the enum constant names + signatures must match the platform API
    // exactly (AOSP: com.google.firebase.analytics.FirebaseAnalytics).
    public enum ConsentType {
        AD_STORAGE,
        ANALYTICS_STORAGE,
        AD_USER_DATA,
        AD_PERSONALIZATION
    }
    public enum ConsentStatus {
        GRANTED,
        DENIED
    }

    public Task<String> getAppInstanceId() {
        return Tasks.forResult(appInstanceId);
    }
    // Real Firebase API: `public Task<Long> getSessionId()` -- returns the
    // current analytics session id (or null if unavailable). Crossy's
    // GameSceneBoot analytics setup awaits this via JNI during nativeRender;
    // a missing method throws NoSuchMethodError on the Unity main thread and
    // stalls the boot at the blue title screen. We mint a stable non-null
    // per-process session id so callers that key off it don't NPE.
    private final long sessionId =
        (appInstanceId.hashCode() & 0xffffffffL) | 0x100000000L;
    public Task<Long> getSessionId() {
        return Tasks.forResult(Long.valueOf(sessionId));
    }
    // Real Firebase API: `public void resetAnalyticsData()` -- signature ()V,
    // NOT Task<Void>. The Firebase C++ SDK (analytics_android.cc) caches the
    // JNI method id for "resetAnalyticsData","()V"; a Task<Void> return here
    // makes GetMethodID throw NoSuchMethodError on the Unity main thread during
    // nativeRender (observed: Crossy GameSceneBoot init stalls the whole boot
    // at the blue title screen). Match the platform signature exactly; the body
    // stays a no-op consistent with our "analytics silenced" stance.
    public void resetAnalyticsData() {}

    // Standard event-name constants Firebase ships -- apps reference
    // them via FirebaseAnalytics.Event.LOGIN etc. We model the
    // commonly-referenced ones; obscure events the app would just
    // hand us a literal string for, no constant needed.
    public static final class Event {
        public static final String AD_IMPRESSION         = "ad_impression";
        public static final String APP_OPEN              = "app_open";
        public static final String EARN_VIRTUAL_CURRENCY = "earn_virtual_currency";
        public static final String GENERATE_LEAD         = "generate_lead";
        public static final String JOIN_GROUP            = "join_group";
        public static final String LEVEL_END             = "level_end";
        public static final String LEVEL_START           = "level_start";
        public static final String LEVEL_UP              = "level_up";
        public static final String LOGIN                 = "login";
        public static final String POST_SCORE            = "post_score";
        public static final String PURCHASE              = "purchase";
        public static final String REFUND                = "refund";
        public static final String SCREEN_VIEW           = "screen_view";
        public static final String SEARCH                = "search";
        public static final String SELECT_CONTENT        = "select_content";
        public static final String SELECT_ITEM           = "select_item";
        public static final String SHARE                 = "share";
        public static final String SIGN_UP               = "sign_up";
        public static final String SPEND_VIRTUAL_CURRENCY= "spend_virtual_currency";
        public static final String TUTORIAL_BEGIN        = "tutorial_begin";
        public static final String TUTORIAL_COMPLETE     = "tutorial_complete";
        public static final String UNLOCK_ACHIEVEMENT    = "unlock_achievement";
        public static final String VIEW_ITEM             = "view_item";
        private Event() {}
    }
    public static final class Param {
        public static final String AD_FORMAT     = "ad_format";
        public static final String AD_NETWORK    = "ad_network";
        public static final String ACHIEVEMENT_ID= "achievement_id";
        public static final String CHARACTER     = "character";
        public static final String CONTENT       = "content";
        public static final String CONTENT_TYPE  = "content_type";
        public static final String CURRENCY      = "currency";
        public static final String GROUP_ID      = "group_id";
        public static final String ITEM_ID       = "item_id";
        public static final String ITEM_NAME     = "item_name";
        public static final String LEVEL         = "level";
        public static final String LEVEL_NAME    = "level_name";
        public static final String METHOD        = "method";
        public static final String PRICE         = "price";
        public static final String SCORE         = "score";
        public static final String SUCCESS       = "success";
        public static final String VALUE         = "value";
        public static final String VIRTUAL_CURRENCY_NAME = "virtual_currency_name";
        private Param() {}
    }
    public static final class UserProperty {
        public static final String ALLOW_AD_PERSONALIZATION_SIGNALS =
            "allow_personalized_ads";
        public static final String SIGN_UP_METHOD = "sign_up_method";
        private UserProperty() {}
    }
}
