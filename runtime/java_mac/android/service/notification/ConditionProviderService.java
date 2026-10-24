package android.service.notification;

import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;

/**
 * Deprecated (API 29) base class for a service that tells the platform when an
 * AutomaticZenRule's condition becomes true or false.
 *
 * Deprecated but NOT gone: apps that still ship one subclass this, and the
 * class must load. Mac.apk has no zen subsystem to subscribe the provider, so
 * onSubscribe/onUnsubscribe/onConnected never fire and notifyCondition has
 * nowhere to report -- exactly the state a provider is in on a device before
 * the user grants it access, which is a state the provider already handles.
 *
 * Constants transcribed VERBATIM from /tmp/aosp_api/android16.txt.
 *
 * Source: frameworks/base/core/java/android/service/notification/ConditionProviderService.java
 * @deprecated matching AOSP.
 */
@Deprecated
public abstract class ConditionProviderService extends android.app.Service {

    public static final String SERVICE_INTERFACE =
            "android.service.notification.ConditionProviderService";
    public static final String EXTRA_RULE_ID = "android.service.notification.extra.RULE_ID";
    public static final String META_DATA_RULE_TYPE = "android.service.zen.automatic.ruleType";
    public static final String META_DATA_CONFIGURATION_ACTIVITY =
            "android.service.zen.automatic.configurationActivity";
    public static final String META_DATA_RULE_INSTANCE_LIMIT =
            "android.service.zen.automatic.ruleInstanceLimit";

    public ConditionProviderService() { }

    public abstract void onConnected();

    public abstract void onSubscribe(Uri conditionId);

    public abstract void onUnsubscribe(Uri conditionId);

    /** @deprecated in AOSP; the platform stopped calling it at API 24. Its body
     *  is empty THERE too, so this override loses nothing. */
    @Deprecated
    public void onRequestConditions(int relevance) { }

    /** AOSP forwards to the system over the provider binder. With no binder
     *  and no zen subsystem there is nothing to notify; AOSP itself silently
     *  drops the call when the provider is not bound. */
    public final void notifyCondition(Condition condition) {
        if (condition == null) return;
        notifyConditions(new Condition[] { condition });
    }

    public final void notifyConditions(Condition... conditions) { }

    /** AOSP asks the system to unbind the provider. Nothing bound it. */
    public final void requestUnbind() { }

    /** AOSP asks the system to rebind the named provider. Static, so it is
     *  callable without an instance -- and a caller must not get a
     *  NoSuchMethodError for a method whose only effect is a rebind. */
    public static final void requestRebind(ComponentName componentName) { }

    /** AOSP returns the provider's binder. android.app.Service.onBind already
     *  returns null in this tree, so overriding loses nothing the superclass
     *  would have assigned. NOT final in AOSP. */
    @Override public IBinder onBind(Intent intent) { return null; }
}
