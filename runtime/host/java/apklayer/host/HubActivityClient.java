package apklayer.host;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;
import java.util.function.BiConsumer;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadow.api.Shadow;

/** startActivity to another app and its result, app side: the hub resolves and starts the target, the result comes back through a ResultReceiver binder. */
final class HubActivityClient {
    private HubActivityClient() {}

    /** Set by the AppHost: starts an activity for an intent that came through the hub. */
    static volatile BiConsumer<Intent, ResultReceiver> starter;
    private static ResultReceiver launchReceiver;

    /** Intent extra naming the app that started an activity for a result (AppHost sets it as the activity's calling package). */
    static final String CALLER = "apklayer.callingPackage";

    static boolean enabled() { return HubClient.connected(); }

    /** Sends the intent to the hub; false when the hub is not used or no installed app handles it. */
    static boolean route(Intent intent, Activity requester, Intent request) {
        if (!enabled()) return false;
        ResultReceiver receiver = null;
        if (requester != null) {
            intent.putExtra(CALLER, requester.getPackageName());
            ShadowActivity shadow = Shadow.extract(requester);
            receiver = new ResultReceiver(new Handler(Looper.getMainLooper())) {
                @Override protected void onReceiveResult(int code, Bundle data) {
                    Intent result = data == null ? null : data.getParcelable("intent", Intent.class);
                    if (!requester.isDestroyed()) shadow.receiveResult(request, code, result);
                }
            };
        }
        ResultReceiver resultTo = receiver;
        int found = HubProtocol.call(HubClient.current().binder(), HubProtocol.DESCRIPTOR, HubProtocol.START_ACTIVITY,
                parcel -> { parcel.writeTypedObject(intent, 0); parcel.writeTypedObject(resultTo, 0); }, reply -> reply.readInt());
        if (found != 0) System.out.println("[apklayer] no installed app handles " + intent);
        return true;
    }

    /** A freshly started app process: the intent another app sent for it before it existed. */
    static Intent incoming() {
        if (!enabled()) return null;
        return HubProtocol.call(HubClient.current().binder(), HubProtocol.DESCRIPTOR, HubProtocol.TAKE_LAUNCH, parcel -> {}, reply -> {
            Intent intent = reply.readTypedObject(Intent.CREATOR);
            launchReceiver = reply.readTypedObject(ResultReceiver.CREATOR);
            return intent;
        });
    }

    static ResultReceiver takeReceiver() { ResultReceiver receiver = launchReceiver; launchReceiver = null; return receiver; }

    /** From the hub, in a running app. */
    static void deliver(Intent intent, ResultReceiver resultTo) {
        BiConsumer<Intent, ResultReceiver> start = starter;
        if (start != null) start.accept(intent, resultTo);
    }

    static void answer(ResultReceiver resultTo, int code, Intent data) {
        Bundle bundle = new Bundle();
        if (data != null) bundle.putParcelable("intent", data);
        // ResultReceiver.send would do this, but under Robolectric it goes to a stand-in for ResultReceivers made from a Parcel
        try {
            com.android.internal.os.IResultReceiver remote = org.robolectric.util.ReflectionHelpers.getField(resultTo, "mReceiver");
            remote.send(code, bundle);
        } catch (android.os.RemoteException gone) { System.out.println("[apklayer] the app that started this activity is gone"); }
    }
}
