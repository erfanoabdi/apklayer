package android.service.credentials;

import android.content.Intent;
import android.os.CancellationSignal;
import android.os.IBinder;
import android.os.OutcomeReceiver;

/** The SPI an app implements to be a system credential provider. API 34.
 *  Source: frameworks/base/core/java/android/service/credentials/CredentialProviderService.java
 *
 *  Mac.apk is not the system credential manager, so nothing ever binds this
 *  service: onBind returns null, which is the documented outcome for a service
 *  the system declines to bind. That is honest absence -- we do NOT synthesize
 *  a picker, and we never invoke a provider's callbacks with a fabricated
 *  request. What the class buys is that an app SHIPPING a credential provider
 *  loads and links: the constants resolve, the abstract callbacks are the ones
 *  the app overrides, and its manifest <service> entry parses.
 *
 *  onBind is final, as on AOSP -- there the final method returns the provider
 *  stub, so a subclass must not be able to replace it. Our Service.onBind
 *  assigns no state, so returning null here discards nothing. */
public abstract class CredentialProviderService extends android.app.Service {

    public static final String SERVICE_INTERFACE =
            "android.service.credentials.CredentialProviderService";

    /** The <meta-data> name a provider's manifest <service> must carry. */
    public static final String SERVICE_META_DATA = "android.credentials.provider";

    public static final String EXTRA_BEGIN_GET_CREDENTIAL_REQUEST =
            "android.service.credentials.extra.BEGIN_GET_CREDENTIAL_REQUEST";
    public static final String EXTRA_BEGIN_GET_CREDENTIAL_RESPONSE =
            "android.service.credentials.extra.BEGIN_GET_CREDENTIAL_RESPONSE";
    public static final String EXTRA_GET_CREDENTIAL_REQUEST =
            "android.service.credentials.extra.GET_CREDENTIAL_REQUEST";
    public static final String EXTRA_GET_CREDENTIAL_RESPONSE =
            "android.service.credentials.extra.GET_CREDENTIAL_RESPONSE";
    public static final String EXTRA_GET_CREDENTIAL_EXCEPTION =
            "android.service.credentials.extra.GET_CREDENTIAL_EXCEPTION";
    public static final String EXTRA_CREATE_CREDENTIAL_REQUEST =
            "android.service.credentials.extra.CREATE_CREDENTIAL_REQUEST";
    public static final String EXTRA_CREATE_CREDENTIAL_RESPONSE =
            "android.service.credentials.extra.CREATE_CREDENTIAL_RESPONSE";
    public static final String EXTRA_CREATE_CREDENTIAL_EXCEPTION =
            "android.service.credentials.extra.CREATE_CREDENTIAL_EXCEPTION";

    public CredentialProviderService() {}

    public abstract void onBeginGetCredential(BeginGetCredentialRequest request,
            CancellationSignal cancellationSignal,
            OutcomeReceiver<BeginGetCredentialResponse,
                    android.credentials.GetCredentialException> callback);

    public abstract void onBeginCreateCredential(BeginCreateCredentialRequest request,
            CancellationSignal cancellationSignal,
            OutcomeReceiver<BeginCreateCredentialResponse,
                    android.credentials.CreateCredentialException> callback);

    public abstract void onClearCredentialState(ClearCredentialStateRequest request,
            CancellationSignal cancellationSignal,
            OutcomeReceiver<Void, android.credentials.ClearCredentialStateException> callback);

    @Override public final IBinder onBind(Intent intent) { return null; }
}
