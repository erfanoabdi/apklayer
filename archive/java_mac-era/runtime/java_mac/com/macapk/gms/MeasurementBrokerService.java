// com.macapk.gms.MeasurementBrokerService -- com.google.android.gms's measurement
// service (action "com.google.android.gms.measurement.START"), in-process.
//
// WHY IT EXISTS. PlayServices.availability() answers SUCCESS, so an app's own
// bundled play-services-measurement (Firebase Analytics) concludes the remote
// service is there ("Service available"), persists use_service=true, and from
// then on reaches its measurement engine ONLY through a BaseGmsClient bound to
// this action on com.google.android.gms. Nothing answered that bind, so
// bindService returned false ("GmsClient: unable to connect to service:
// com.google.android.gms.measurement.START on com.google.android.gms"), the
// IMeasurementService never connected, and every call that needs a reply came
// back empty -- getAppInstanceId() returned null forever ("FA: Failed to get
// app instance id"). The availability contract says each service must then
// fail honestly at its own call site, and this one did; the trouble is that a
// real device never has THIS service missing while availability is SUCCESS,
// so apps are entitled to depend on it. Measured consequence: Temple Run 2
// starts its entire boot coroutine only in the continuation of
// FirebaseAnalytics.GetAnalyticsInstanceIdAsync(), which the Firebase C++ SDK
// fails on a null id, so the game sat on its loading screen forever.
//
// WHAT IT ANSWERS WITH. The broker protocol every GMS service speaks, and as
// the service binder the app's OWN
// com.google.android.gms.measurement.AppMeasurementService -- the in-app
// implementation of the same IMeasurementService interface, which the SDK
// itself binds on a device without Play Services. It is built from the same
// SDK as the client, so the interface and every parcel layout match by
// construction; implementing IMeasurementService here instead would mean
// chasing the AppMetadata/EventParcel layouts across SDK versions. microG
// answers this action with its own no-op implementation
// (play-services-core/src/main/kotlin/org/microg/gms/measurement/
// MeasurementService.kt: onPostInitComplete(SUCCESS, MeasurementServiceImpl(),
// Bundle()), where every send is a log line). Delegating keeps the app's
// events flowing the way a real device does, where one of the two
// implementations always stores and uploads them.
//
// THE WIRE, read from the play-services-basement the apps actually ship
// (Temple Run 2's classes.jar, via debug/appclass.py):
//   IGmsServiceBroker.getService   transaction 46
//     data  = token, writeStrongBinder(IGmsCallbacks), 1 + GetServiceRequest
//     (proxy com.google.android.gms.common.internal.zzaa.getService)
//   IGmsCallbacks.onPostInitComplete  transaction 1
//     data  = token, int statusCode, writeStrongBinder(service), Bundle as
//             0 (null) | 1 + bundle; the stub then rejects any unread bytes
//     (proxy ...internal.zzy, stub ...internal.zzz)
// BaseGmsClient only posts that callback to its own Handler, so answering it
// synchronously from inside getService is safe; no client lock is held
// across the call.
//
// Resolved only when the hosted app declares AppMeasurementService
// (MacApkPackageManager.macapkResolveGmsMeasurementService); an app that does
// not still gets the failed bind it got before.
package com.macapk.gms;

import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

public final class MeasurementBrokerService extends android.app.Service {

    /** The action play-services-measurement binds on com.google.android.gms. */
    public static final String ACTION = "com.google.android.gms.measurement.START";
    /** The SDK's own in-app service. The SDK names it by this literal too
     *  (new ComponentName(context, ...AppMeasurementService)), so it is part of
     *  the library's contract rather than any one app's layout. */
    public static final String LOCAL_SERVICE =
            "com.google.android.gms.measurement.AppMeasurementService";

    private static final String BROKER_DESCRIPTOR =
            "com.google.android.gms.common.internal.IGmsServiceBroker";
    private static final String CALLBACKS_DESCRIPTOR =
            "com.google.android.gms.common.internal.IGmsCallbacks";
    private static final int TRANSACTION_GET_SERVICE = 46;
    private static final int TRANSACTION_ON_POST_INIT_COMPLETE = 1;
    // CommonStatusCodes.SUCCESS / INTERNAL_ERROR.
    private static final int STATUS_SUCCESS = 0;
    private static final int STATUS_INTERNAL_ERROR = 8;

    private final Binder mBroker = new Binder(BROKER_DESCRIPTOR) {
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            if (code != TRANSACTION_GET_SERVICE) return super.onTransact(code, data, reply, flags);
            data.enforceInterface(BROKER_DESCRIPTOR);
            IBinder callbacks = data.readStrongBinder();
            // The GetServiceRequest that follows carries the caller's package,
            // scopes and account. A measurement bind needs none of them.
            IBinder service = bindLocalMeasurementService();
            if (callbacks != null) {
                Parcel d = Parcel.obtain();
                Parcel r = Parcel.obtain();
                try {
                    d.writeInterfaceToken(CALLBACKS_DESCRIPTOR);
                    d.writeInt(service != null ? STATUS_SUCCESS : STATUS_INTERNAL_ERROR);
                    d.writeStrongBinder(service);
                    d.writeInt(0);   // no connection-hint Bundle
                    callbacks.transact(TRANSACTION_ON_POST_INIT_COMPLETE, d, r, 0);
                } finally {
                    d.recycle();
                    r.recycle();
                }
            }
            if (reply != null) reply.writeNoException();
            return true;
        }
    };

    @Override public IBinder onBind(Intent intent) { return mBroker; }

    /** The app's own AppMeasurementService binder, through the same in-process
     *  bind path any Context.bindService takes. Its onBind answers only the
     *  START action, so the Intent carries it. */
    private IBinder bindLocalMeasurementService() {
        Thread t = Thread.currentThread();
        ClassLoader old = t.getContextClassLoader();
        try {
            // ServiceRegistry instantiates by the thread's context loader, and
            // the class lives in the APP's loader; the calling thread is
            // whichever one the client connected from.
            t.setContextClassLoader(getClassLoader());
            IBinder b = android.app.ServiceRegistry.bind(this,
                    new Intent(ACTION).setClassName(getPackageName(), LOCAL_SERVICE));
            com.macapk.loader.NativeLog.i("gms-measurement",
                    "getService -> " + (b != null ? LOCAL_SERVICE : "no local service (INTERNAL_ERROR)"));
            return b;
        } catch (Throwable e) {
            com.macapk.loader.NativeLog.w("gms-measurement", "local bind threw " + e);
            return null;
        } finally {
            t.setContextClassLoader(old);
        }
    }
}
