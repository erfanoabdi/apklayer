// com.google.firebase.messaging.FirebaseMessagingService -- the Service base
// class an app extends to receive FCM pushes and token-refresh callbacks.
//
// On a real device the GMS FirebaseMessaging component binds this service and
// delivers messages by starting it with an Intent whose action is
// "com.google.android.c2dm.intent.RECEIVE"; the service decodes the intent and
// fans out to onMessageReceived / onDeletedMessages / onMessageSent /
// onSendError, while a separate "com.google.firebase.messaging.NEW_TOKEN"
// intent drives onNewToken.
//
// Mac.apk is a de-Googled device: there is no FCM channel, so NONE of these
// callbacks are ever auto-invoked (no push will arrive). The class exists so
// push-consumer apps that SUBCLASS it compile against our shadow and boot
// without NoClassDefFoundError. handleIntent() is still implemented faithfully
// so that if an app (or our own framework, in the future) hands the service a
// well-formed FCM intent, it decodes and dispatches exactly like the SDK.
//
// In the real SDK this extends the internal EnhancedIntentService (which
// extends android.app.Service and runs intents on a background executor).
// EnhancedIntentService is not part of the app-facing compile surface and is
// not on our classpath, so we extend android.app.Service directly. The
// observable consumer contract -- "it is a Service, you override
// onMessageReceived/onNewToken/etc." -- is preserved exactly.
//
// Faithful to firebase-android-sdk
// firebase-messaging/.../FirebaseMessagingService.java public surface.
package com.google.firebase.messaging;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

public class FirebaseMessagingService extends Service {

    // Public action constant from the SDK (direct-boot receive path).
    public static final String ACTION_DIRECT_BOOT_REMOTE_INTENT =
        "com.google.firebase.messaging.RECEIVE_DIRECT_BOOT";

    // Package-private wire actions, mirroring the SDK's (non-public) constants
    // so handleIntent() can recognize the intents the GMS component would send.
    static final String ACTION_REMOTE_INTENT = "com.google.android.c2dm.intent.RECEIVE";
    static final String ACTION_NEW_TOKEN     = "com.google.firebase.messaging.NEW_TOKEN";

    // FCM message_type values carried in the receive intent's extras.
    private static final String MESSAGE_TYPE_DELETED   = "deleted_messages";
    private static final String MESSAGE_TYPE_SEND_EVENT = "send_event";
    private static final String MESSAGE_TYPE_SEND_ERROR = "send_error";
    private static final String EXTRA_TOKEN = "token";

    // ---- Consumer callbacks (apps override these) ----

    // Called when a data message is received. Default no-op; apps override.
    public void onMessageReceived(RemoteMessage message) {}

    // Called when the FCM server deletes pending messages (queue overflow).
    public void onDeletedMessages() {}

    // Called when an upstream message was successfully sent.
    public void onMessageSent(String msgId) {}

    // Called when an upstream message failed to send.
    public void onSendError(String msgId, Exception exception) {}

    // Called when a new registration token is generated. Never fires on
    // Mac.apk (no FCM registration), but apps override it to upload the token.
    public void onNewToken(String token) {}

    // ---- Intent dispatch (the SDK's EnhancedIntentService entry point) ----

    // handleIntent: decode an FCM intent and fan out to the right callback.
    // Faithful to the SDK's dispatch: a RECEIVE intent inspects the
    // "message_type" extra; absence (or "gcm") means a data/notification
    // message -> onMessageReceived; "deleted_messages" -> onDeletedMessages;
    // "send_event" -> onMessageSent; "send_error" -> onSendError. A NEW_TOKEN
    // intent -> onNewToken.
    public void handleIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();

        if (ACTION_NEW_TOKEN.equals(action)) {
            onNewToken(intent.getStringExtra(EXTRA_TOKEN));
            return;
        }

        if (ACTION_REMOTE_INTENT.equals(action)
                || ACTION_DIRECT_BOOT_REMOTE_INTENT.equals(action)) {
            dispatchMessage(intent);
        }
        // Unknown actions are ignored, mirroring the SDK's silent fall-through.
    }

    private void dispatchMessage(Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras == null) extras = new Bundle();
        // The SDK strips the framework's own internal extras before building
        // the message; we pass the bundle through (single-VM, no IPC keys).
        extras.remove("androidx.contentpager.content.wakelockid");

        String messageType = extras.getString("message_type");
        if (messageType == null) {
            messageType = "gcm";
        }

        switch (messageType) {
            case "gcm":
                onMessageReceived(new RemoteMessage(extras));
                break;
            case MESSAGE_TYPE_DELETED:
                onDeletedMessages();
                break;
            case MESSAGE_TYPE_SEND_EVENT:
                onMessageSent(extras.getString("google.message_id"));
                break;
            case MESSAGE_TYPE_SEND_ERROR:
                onSendError(
                    extras.getString("google.message_id"),
                    new SendException(extras.getString("error")));
                break;
            default:
                // Unrecognized message_type: the SDK logs and drops it.
                break;
        }
    }

    // getStartCommandIntent: the SDK's hook to remap the intent that started
    // the service. Default returns the intent unchanged.
    protected Intent getStartCommandIntent(Intent originalIntent) {
        return originalIntent;
    }

    // Bound by the GMS component in the real SDK; on Mac.apk nothing binds it.
    // Service.onBind is abstract, so we must provide a concrete impl. Returning
    // null is the documented "do not allow binding" behavior for a Service that
    // is started rather than bound.
    @Override public IBinder onBind(Intent intent) {
        return null;
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        // The SDK routes the start intent through handleIntent on a worker.
        // We are single-process with no background executor here; dispatch
        // synchronously. No push ever arrives on Mac.apk, so this only fires
        // if an app self-starts the service with a crafted intent.
        if (intent != null) {
            handleIntent(getStartCommandIntent(intent));
        }
        // START_NOT_STICKY: do not recreate after kill (matches the SDK).
        return 2;
    }
}
