package com.google.firebase.inappmessaging;

// com.google.firebase.inappmessaging.FirebaseInAppMessaging -- entry point for
// Firebase In-App Messaging. Campaign messages are fetched from Google's servers;
// on a de-Googled device there is no campaign backend, so no message is ever
// displayed (NEVER fabricates a campaign). The local toggles (suppression,
// auto-data-collection) are honored, and triggerEvent is a local no-op that
// matches no campaign. Mirrors the published surface.
// Ref: firebase.google.com/docs/reference/android/.../FirebaseInAppMessaging
public class FirebaseInAppMessaging {

    private static final FirebaseInAppMessaging INSTANCE = new FirebaseInAppMessaging();

    private boolean messagesSuppressed = false;
    private boolean automaticDataCollectionEnabled = true;

    private FirebaseInAppMessaging() {}

    public static FirebaseInAppMessaging getInstance() { return INSTANCE; }

    public boolean areMessagesSuppressed() { return messagesSuppressed; }
    public void setMessagesSuppressed(Boolean suppressed) {
        this.messagesSuppressed = suppressed != null && suppressed;
    }

    public void setAutomaticDataCollectionEnabled(boolean enabled) {
        this.automaticDataCollectionEnabled = enabled;
    }
    public void setAutomaticDataCollectionEnabled(Boolean enabled) {
        this.automaticDataCollectionEnabled = enabled != null && enabled;
    }
    public boolean isAutomaticDataCollectionEnabled() { return automaticDataCollectionEnabled; }

    // Programmatic trigger: with no campaign backend nothing matches, so this is
    // a local no-op. Listener/clickListener registration is accepted but inert.
    public void triggerEvent(String eventName) {}

    public void addClickListener(Object clickListener) {}
    public void addClickListener(Object clickListener, java.util.concurrent.Executor executor) {}
    public void removeClickListener(Object clickListener) {}

    public void addDismissListener(Object dismissListener) {}
    public void removeDismissListener(Object dismissListener) {}

    public void addDisplayErrorListener(Object displayErrorListener) {}
    public void removeDisplayErrorListener(Object displayErrorListener) {}

    public void addImpressionListener(Object impressionListener) {}
    public void removeImpressionListener(Object impressionListener) {}
}
