// com.macapk.gms.GmsLocationService -- com.google.android.gms's fused location
// service (action "com.google.android.location.internal.GoogleLocationManagerService
// .START"), in-process, answered from the PLATFORM fused provider, which is macOS
// Location Services (android.location.LocationManager over CoreLocation, v1.0.1712).
//
// WHY IT EXISTS. Mac.apk tells GMS-aware apps that Play services is available
// (PlayServices.availability() == SUCCESS), so an app with play-services-location
// -- Google's library, or microG's client re-implementation (Organic Maps ships
// that one) -- never falls back to LocationManager: it binds this action through
// IGmsServiceBroker.getService and talks IGoogleLocationManagerService to the
// binder it is handed. Nothing answered, so those apps never saw a location even
// once the platform had one. Same seam as IdentitySignInService and
// MeasurementBrokerService.
//
// THE WIRE. Transaction codes are the AIDL index + 1, from microG's
// IGoogleLocationManagerService.aidl (which mirrors GMS's numbering so real
// clients work against it; Organic Maps' bundled Stub agrees, read with
// debug/jadx_app.sh on 2026-10-05):
//    7 getLastLocation()                         -> Location
//    8 requestLocationUpdatesWithListener(LocationRequest, ILocationListener)
//    9 requestLocationUpdatesWithIntent(LocationRequest, PendingIntent)
//   10 removeLocationUpdatesWithListener(ILocationListener)
//   11 removeLocationUpdatesWithIntent(PendingIntent)
//   12 setMockMode(boolean)  13 setMockLocation(Location)      (accepted, ignored)
//   20 requestLocationUpdatesWithPackage(LocationRequest, ILocationListener, String)
//   21 getLastLocationWithPackage(String)        -> Location
//   34 getLocationAvailabilityWithPackage(String) -> LocationAvailability
//   52 requestLocationUpdatesInternalWithListener(LocationRequestInternal, ILocationListener)
//   53 requestLocationUpdatesInternalWithIntent(LocationRequestInternal, PendingIntent)
//   59 updateLocationRequest(LocationRequestUpdateData)
//   63 requestLocationSettingsDialog(LocationSettingsRequest, ISettingsCallbacks, String)
//   67 flushLocations(IFusedLocationProviderCallback)
//   80 getLastLocationWith(String)               -> Location
//   82 getLastLocationWithRequest(LastLocationRequest, ILocationStatusCallback)
//   87 getCurrentLocation(CurrentLocationRequest, ILocationStatusCallback) -> ICancelToken
//   88 requestLocationUpdatesWithCallback(LocationReceiver, LocationRequest, IStatusCallback)
//   89 removeLocationUpdatesWithCallback(LocationReceiver, IStatusCallback)
//   90 getLastLocationWithReceiver(LastLocationRequest, LocationReceiver)
//   91 getLocationAvailabilityWithReceiver(LocationAvailabilityRequest, LocationReceiver)
//   92 getCurrentLocationWithReceiver(CurrentLocationRequest, LocationReceiver) -> ICancelToken
// The SafeParcel field ids are microG's (play-services-location/src/main/java/
// com/google/android/gms/location/*.java), which match the Google client's:
//   LocationRequest {1 priority, 2 interval, 3 minUpdateInterval, 6 maxUpdates,
//     7 minUpdateDistance (float), 10 duration}
//   LocationRequestInternal {1 LocationRequest}
//   LocationRequestUpdateData {1 opCode (1 request, 2 remove), 2 LocationRequestInternal,
//     3 ILocationListener, 4 PendingIntent, 5 ILocationCallback, 6 IFusedLocationProviderCallback}
//   LocationReceiver {1 type (1 listener, 2 callback, 3 PendingIntent, 4 status
//     callback, 5 availability status callback), 2 old binder, 3 binder, 4 PendingIntent}
//   CurrentLocationRequest {4 duration}; LocationResult {1 List<Location>};
//   LocationAvailability {1 cell, 2 wifi, 3 elapsedRealtimeNs, 4 locationStatus
//     (< 1000 = available)}; LocationSettingsResult {1 Status, 2 LocationSettingsStates
//     {1 gpsUsable, 2 networkUsable, 3 bleUsable, 4 gpsPresent, 5 networkPresent, 6 blePresent}};
//   FusedLocationProviderResult {1 Status}.
// Callbacks (AIDL index + 1): ILocationListener.onLocationChanged(Location) 1;
// ILocationCallback.onLocationResult(LocationResult) 1, onLocationAvailability 2;
// ILocationStatusCallback.onLocationStatus(Status, Location) 1;
// ILocationAvailabilityStatusCallback.onLocationAvailabilityStatus(Status, LA) 1;
// IFusedLocationProviderCallback.onFusedLocationProviderResult 1; IStatusCallback.onResult 1;
// ISettingsCallbacks.onLocationSettingsResult 1; ICancelToken.cancel 1.
//
// WHAT IT DECIDES: nothing about location. Every call goes to the platform
// LocationManager's fused provider (passive for PRIORITY_PASSIVE), so the
// permission rules, the per-app dialog, the macOS gate, the interval/distance
// filtering and getCurrentLocation's age/timeout are the platform's -- one
// implementation, not two. A caller without a location permission gets the
// platform's SecurityException, which is what GMS throws too. Mock mode and
// the geofence / activity-recognition APIs are not served (their features are
// not declared, so the client reports them unavailable itself).
//
// Resolved by MacApkPackageManager.macapkResolveGmsLocationService while we
// claim Play services and the host location bridge is bound; MACAPK_GMS_LOCATION=0
// turns it off (the bind then fails as before).
package com.macapk.gms;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.CancellationSignal;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.SystemClock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class GmsLocationService extends android.app.Service {

    public static final String ACTION =
            "com.google.android.location.internal.GoogleLocationManagerService.START";

    static final String SERVICE_DESC = "com.google.android.gms.location.internal.IGoogleLocationManagerService";
    static final String LISTENER_DESC = "com.google.android.gms.location.ILocationListener";
    static final String CALLBACK_DESC = "com.google.android.gms.location.ILocationCallback";
    static final String STATUS_LOC_DESC = "com.google.android.gms.location.internal.ILocationStatusCallback";
    static final String AVAIL_DESC = "com.google.android.gms.location.internal.ILocationAvailabilityStatusCallback";
    static final String FUSED_DESC = "com.google.android.gms.location.internal.IFusedLocationProviderCallback";
    static final String SETTINGS_DESC = "com.google.android.gms.location.internal.ISettingsCallbacks";
    static final String CANCEL_DESC = "com.google.android.gms.common.internal.ICancelToken";

    /** Features this service answers (GMS / microG names and versions). The
     *  client checks each call's required feature against this list, so
     *  anything not served stays undeclared and fails at the client. */
    static final String[] FEATURE_NAMES = {
        "get_current_location", "get_last_location_with_request", "location_updates_with_callback" };
    static final long[] FEATURE_VERSIONS = { 2L, 1L, 1L };

    static final int STATUS_SUCCESS = 0, STATUS_ERROR = 13, STATUS_CANCELED = 16;
    /** LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE. */
    static final int SETTINGS_CHANGE_UNAVAILABLE = 8502;
    /** Google's LocationRequest.PRIORITY_PASSIVE. */
    static final int PRIORITY_PASSIVE = 105;

    static final int RECEIVER_LISTENER = 1, RECEIVER_CALLBACK = 2, RECEIVER_PENDING_INTENT = 3,
            RECEIVER_STATUS = 4, RECEIVER_AVAILABILITY = 5;

    /** Callbacks into the app run here, never on the AppKit thread that
     *  delivers host fixes or on the caller's binder thread. */
    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "macapk-gms-location");
        t.setDaemon(true);
        return t;
    });

    /** Live update registrations by the app's callback binder (or PendingIntent). */
    private static final Map<Object, LocationListener> sRegs = new ConcurrentHashMap<>();

    /** Served while we claim Play services (a client told GMS is absent never
     *  binds), the host location bridge is bound, and MACAPK_GMS_LOCATION is
     *  not 0. */
    public static boolean enabled() {
        if ("0".equals(System.getenv("MACAPK_GMS_LOCATION"))) return false;
        try {
            return com.macapk.loader.PlayServices.availability() == 0
                    && com.macapk.loader.LocationBridge.available();
        } catch (Throwable t) {
            return false;
        }
    }

    @Override public IBinder onBind(Intent intent) { return newBroker(this); }

    // ------------------------------------------------------------------ broker

    static IBinder newBroker(final Context ctx) {
        final IBinder service = newLocationService(ctx);
        return new Binder(IdentitySignInService.BROKER_DESC) {
            @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                    throws RemoteException {
                if (code != IdentitySignInService.TX_GET_SERVICE) {
                    return super.onTransact(code, data, reply, flags);
                }
                data.enforceInterface(IdentitySignInService.BROKER_DESC);
                IBinder callbacks = data.readStrongBinder();
                if (callbacks != null) {
                    Parcel d = Parcel.obtain();
                    Parcel r = Parcel.obtain();
                    try {
                        d.writeInterfaceToken(IdentitySignInService.CALLBACKS_DESC);
                        d.writeInt(STATUS_SUCCESS);
                        d.writeStrongBinder(service);
                        d.writeInt(1);
                        writeConnectionInfo(d);
                        callbacks.transact(IdentitySignInService.TX_POST_INIT_WITH_INFO, d, r, 0);
                    } finally {
                        d.recycle();
                        r.recycle();
                    }
                }
                if (reply != null) reply.writeNoException();
                GoogleSignInBroker.log("location: getService for " + ctx.getPackageName());
                return true;
            }
        };
    }

    static void writeConnectionInfo(Parcel p) {
        int o = SafeParcel.beginObject(p);
        List<SafeParcel.Body> features = new ArrayList<>();
        for (int i = 0; i < FEATURE_NAMES.length; i++) {
            final String name = FEATURE_NAMES[i];
            final long version = FEATURE_VERSIONS[i];
            features.add(q -> IdentitySignInService.writeFeature(q, name, version));
        }
        SafeParcel.writeTypedList(p, 2, features);
        SafeParcel.writeInt(p, 3, 0);
        SafeParcel.finishObject(p, o);
    }

    // ------------------------------------------------------------------ service

    static IBinder newLocationService(final Context ctx) {
        return new Binder(SERVICE_DESC) {
            @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                    throws RemoteException {
                if (code < 1 || code > 0xFFFFFF) return super.onTransact(code, data, reply, flags);
                data.enforceInterface(SERVICE_DESC);
                LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
                switch (code) {
                    case 7: case 21: case 80: {           // getLastLocation*
                        Location l = lm.getLastKnownLocation(LocationManager.FUSED_PROVIDER);
                        if (reply != null) { reply.writeNoException(); writeTypedLocation(reply, l); }
                        return true;
                    }
                    case 8: case 20: case 52: {           // request with ILocationListener
                        Map<Integer, SafeParcel.Field> req = readTyped(data);
                        if (code == 52 && req != null) req = SafeParcel.readNestedObject(data, req, 1);
                        IBinder listener = data.readStrongBinder();
                        register(lm, listener, RECEIVER_LISTENER, null, data, req, null);
                        if (reply != null) reply.writeNoException();
                        return true;
                    }
                    case 9: case 53: {                    // request with PendingIntent
                        Map<Integer, SafeParcel.Field> req = readTyped(data);
                        if (code == 53 && req != null) req = SafeParcel.readNestedObject(data, req, 1);
                        PendingIntent pi = data.readInt() != 0 ? PendingIntent.CREATOR.createFromParcel(data) : null;
                        register(lm, null, RECEIVER_PENDING_INTENT, pi, data, req, null);
                        if (reply != null) reply.writeNoException();
                        return true;
                    }
                    case 10: {                            // removeLocationUpdatesWithListener
                        unregister(lm, data.readStrongBinder());
                        if (reply != null) reply.writeNoException();
                        return true;
                    }
                    case 11: {                            // removeLocationUpdatesWithIntent
                        PendingIntent pi = data.readInt() != 0 ? PendingIntent.CREATOR.createFromParcel(data) : null;
                        unregister(lm, pi);
                        if (reply != null) reply.writeNoException();
                        return true;
                    }
                    case 12: case 13: {                   // setMockMode / setMockLocation
                        if (reply != null) reply.writeNoException();
                        return true;
                    }
                    case 34: {                            // getLocationAvailabilityWithPackage
                        if (reply != null) {
                            reply.writeNoException();
                            reply.writeInt(1);
                            writeAvailability(reply, available(lm));
                        }
                        return true;
                    }
                    case 59: {                            // updateLocationRequest
                        updateLocationRequest(lm, data);
                        if (reply != null) reply.writeNoException();
                        return true;
                    }
                    case 63: {                            // requestLocationSettingsDialog
                        if (data.readInt() != 0) SafeParcel.readObject(data);
                        IBinder cb = data.readStrongBinder();
                        settingsResult(lm, cb);
                        if (reply != null) reply.writeNoException();
                        return true;
                    }
                    case 67: {                            // flushLocations: nothing batches
                        IBinder cb = data.readStrongBinder();
                        callFused(cb, STATUS_SUCCESS, null);
                        if (reply != null) reply.writeNoException();
                        return true;
                    }
                    case 82: {                            // getLastLocationWithRequest
                        readTyped(data);
                        IBinder cb = data.readStrongBinder();
                        lastLocation(lm, cb);
                        if (reply != null) reply.writeNoException();
                        return true;
                    }
                    case 87: {                            // getCurrentLocation
                        Map<Integer, SafeParcel.Field> req = readTyped(data);
                        long duration = req != null ? SafeParcel.readLong(data, req, 4, Long.MAX_VALUE) : Long.MAX_VALUE;
                        IBinder cb = data.readStrongBinder();
                        IBinder token = currentLocation(lm, duration, cb);
                        if (reply != null) { reply.writeNoException(); reply.writeStrongBinder(token); }
                        return true;
                    }
                    case 88: {                            // requestLocationUpdatesWithCallback
                        Receiver rcv = readReceiver(data);
                        Map<Integer, SafeParcel.Field> req = readTyped(data);
                        IBinder status = data.readStrongBinder();
                        if (rcv == null) {
                            callStatus(status, STATUS_ERROR, "missing location receiver");
                        } else {
                            if (rcv.oldBinder != null) unregister(lm, rcv.oldBinder);
                            register(lm, rcv.binder, rcv.type, rcv.pendingIntent, data, req, status);
                        }
                        if (reply != null) reply.writeNoException();
                        return true;
                    }
                    case 89: {                            // removeLocationUpdatesWithCallback
                        Receiver rcv = readReceiver(data);
                        IBinder status = data.readStrongBinder();
                        if (rcv != null) unregister(lm, rcv.pendingIntent != null ? rcv.pendingIntent : rcv.binder);
                        callStatus(status, STATUS_SUCCESS, null);
                        if (reply != null) reply.writeNoException();
                        return true;
                    }
                    case 90: {                            // getLastLocationWithReceiver
                        readTyped(data);
                        Receiver rcv = readReceiver(data);
                        if (rcv != null) lastLocation(lm, rcv.binder);
                        if (reply != null) reply.writeNoException();
                        return true;
                    }
                    case 91: {                            // getLocationAvailabilityWithReceiver
                        readTyped(data);
                        Receiver rcv = readReceiver(data);
                        if (rcv != null) {
                            final IBinder cb = rcv.binder;
                            final boolean ok = available(lm);
                            EXEC.execute(() -> callAvailabilityStatus(cb, ok));
                        }
                        if (reply != null) reply.writeNoException();
                        return true;
                    }
                    case 92: {                            // getCurrentLocationWithReceiver
                        Map<Integer, SafeParcel.Field> req = readTyped(data);
                        long duration = req != null ? SafeParcel.readLong(data, req, 4, Long.MAX_VALUE) : Long.MAX_VALUE;
                        Receiver rcv = readReceiver(data);
                        IBinder token = rcv != null ? currentLocation(lm, duration, rcv.binder) : null;
                        if (reply != null) { reply.writeNoException(); reply.writeStrongBinder(token); }
                        return true;
                    }
                    default:
                        GoogleSignInBroker.log("location: unserved transaction " + code);
                        return super.onTransact(code, data, reply, flags);
                }
            }
        };
    }

    // ------------------------------------------------------------------ calls

    /** A GMS LocationRequest as a platform request on the fused provider (or
     *  passive), registered with a listener that relays to the app's receiver. */
    static void register(LocationManager lm, final IBinder binder, final int type,
            final PendingIntent pi, Parcel data, Map<Integer, SafeParcel.Field> req,
            final IBinder statusCb) {
        final Object key = pi != null ? pi : binder;
        if (key == null) {
            callStatus(statusCb, STATUS_ERROR, "missing location receiver");
            return;
        }
        int priority = req != null ? SafeParcel.readInt(data, req, 1, 102) : 102;
        long interval = req != null ? SafeParcel.readLong(data, req, 2, 3_600_000L) : 3_600_000L;
        long minInterval = req != null ? SafeParcel.readLong(data, req, 3, interval) : interval;
        int maxUpdates = req != null ? SafeParcel.readInt(data, req, 6, Integer.MAX_VALUE) : Integer.MAX_VALUE;
        float minDistance = req != null ? readFloat(data, req, 7, 0f) : 0f;
        long duration = req != null ? SafeParcel.readLong(data, req, 10, Long.MAX_VALUE) : Long.MAX_VALUE;
        android.location.LocationRequest.Builder b =
                new android.location.LocationRequest.Builder(Math.max(0L, interval))
                        .setMinUpdateIntervalMillis(Math.max(0L, Math.min(minInterval, interval)))
                        .setMinUpdateDistanceMeters(Math.max(0f, minDistance))
                        .setQuality(priority);
        if (maxUpdates > 0 && maxUpdates < Integer.MAX_VALUE) b.setMaxUpdates(maxUpdates);
        if (duration > 0 && duration < Long.MAX_VALUE) b.setDurationMillis(duration);
        String provider = priority == PRIORITY_PASSIVE
                ? LocationManager.PASSIVE_PROVIDER : LocationManager.FUSED_PROVIDER;

        LocationListener relay = new LocationListener() {
            @Override public void onLocationChanged(Location l) {
                if (type == RECEIVER_LISTENER) callListener(binder, l);
                else if (type == RECEIVER_CALLBACK) callLocationResult(binder, l);
                else if (type == RECEIVER_PENDING_INTENT) sendPendingIntent(pi, l);
            }
            @Override public void onProviderEnabled(String p) {
                if (type == RECEIVER_CALLBACK) callAvailability(binder, true);
            }
            @Override public void onProviderDisabled(String p) {
                if (type == RECEIVER_CALLBACK) callAvailability(binder, false);
            }
        };
        LocationListener old = sRegs.put(key, relay);
        if (old != null) lm.removeUpdates(old);
        try {
            lm.requestLocationUpdates(provider, b.build(), EXEC, relay);
        } catch (SecurityException e) {
            sRegs.remove(key);
            if (statusCb != null) {
                callStatus(statusCb, STATUS_ERROR, e.getMessage());
                return;
            }
            throw e;   // the legacy calls report it the way GMS does: thrown at the caller
        }
        GoogleSignInBroker.log("location: updates for " + key.getClass().getSimpleName()
                + " priority=" + priority + " interval=" + interval + "ms");
        callStatus(statusCb, STATUS_SUCCESS, null);
    }

    static void unregister(LocationManager lm, Object key) {
        if (key == null) return;
        LocationListener l = sRegs.remove(key);
        if (l != null) lm.removeUpdates(l);
    }

    /** LocationRequestUpdateData: opCode 1 requests, 2 removes; the answer goes
     *  to its IFusedLocationProviderCallback. */
    static void updateLocationRequest(LocationManager lm, Parcel data) {
        if (data.readInt() == 0) return;
        Map<Integer, SafeParcel.Field> u = SafeParcel.readObject(data);
        int op = SafeParcel.readInt(data, u, 1, 0);
        IBinder listener = readBinder(data, u, 3);
        PendingIntent pi = readPendingIntent(data, u, 4);
        IBinder callback = readBinder(data, u, 5);
        IBinder fused = readBinder(data, u, 6);
        IBinder binder = listener != null ? listener : callback;
        int type = listener != null ? RECEIVER_LISTENER
                 : callback != null ? RECEIVER_CALLBACK : RECEIVER_PENDING_INTENT;
        if (op == 1) {
            Map<Integer, SafeParcel.Field> internal = SafeParcel.readNestedObject(data, u, 2);
            Map<Integer, SafeParcel.Field> req = internal != null
                    ? SafeParcel.readNestedObject(data, internal, 1) : null;
            try {
                register(lm, binder, type, pi, data, req, null);
                callFused(fused, STATUS_SUCCESS, null);
            } catch (SecurityException e) {
                callFused(fused, STATUS_ERROR, e.getMessage());
            }
        } else if (op == 2) {
            unregister(lm, pi != null ? pi : binder);
            callFused(fused, STATUS_SUCCESS, null);
        } else {
            callFused(fused, STATUS_ERROR, "invalid location request update operation: " + op);
        }
    }

    static void lastLocation(LocationManager lm, final IBinder cb) {
        Location l;
        try {
            l = lm.getLastKnownLocation(LocationManager.FUSED_PROVIDER);
        } catch (SecurityException e) {
            final String msg = e.getMessage();
            EXEC.execute(() -> callLocationStatus(cb, STATUS_ERROR, msg, null));
            return;
        }
        final Location out = l;
        EXEC.execute(() -> callLocationStatus(cb, STATUS_SUCCESS, null, out));
    }

    /** One fix (or null on timeout / disabled), through the platform's
     *  getCurrentLocation. Returns the ICancelToken the client may cancel with. */
    static IBinder currentLocation(LocationManager lm, long durationMillis, final IBinder cb) {
        final CancellationSignal signal = new CancellationSignal();
        android.location.LocationRequest.Builder b = new android.location.LocationRequest.Builder(0);
        if (durationMillis > 0 && durationMillis < Long.MAX_VALUE) b.setDurationMillis(durationMillis);
        try {
            lm.getCurrentLocation(LocationManager.FUSED_PROVIDER, b.build(), signal, EXEC,
                    l -> callLocationStatus(cb, STATUS_SUCCESS, null, l));
        } catch (SecurityException e) {
            final String msg = e.getMessage();
            EXEC.execute(() -> callLocationStatus(cb, STATUS_ERROR, msg, null));
        }
        return new Binder(CANCEL_DESC) {
            @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                    throws RemoteException {
                if (code != 1) return super.onTransact(code, data, reply, flags);
                data.enforceInterface(CANCEL_DESC);
                signal.cancel();
                EXEC.execute(() -> callLocationStatus(cb, STATUS_CANCELED, null, null));
                if (reply != null) reply.writeNoException();
                return true;
            }
        };
    }

    static boolean available(LocationManager lm) {
        try {
            return lm.isProviderEnabled(LocationManager.FUSED_PROVIDER)
                    && !lm.getProviders(true).isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    /** SettingsClient.checkLocationSettings: satisfied when location is on;
     *  otherwise SETTINGS_CHANGE_UNAVAILABLE, since the switch lives in macOS
     *  System Settings and no in-app dialog can flip it. */
    static void settingsResult(LocationManager lm, final IBinder cb) {
        if (cb == null) return;
        final boolean on = lm.isLocationEnabled();
        EXEC.execute(() -> {
            Parcel d = Parcel.obtain();
            Parcel r = Parcel.obtain();
            try {
                d.writeInterfaceToken(SETTINGS_DESC);
                d.writeInt(1);
                int o = SafeParcel.beginObject(d);
                SafeParcel.writeObject(d, 1, q -> IdentitySignInService.writeStatus(q,
                        on ? STATUS_SUCCESS : SETTINGS_CHANGE_UNAVAILABLE,
                        on ? null : "Location Services is off in macOS System Settings"));
                SafeParcel.writeObject(d, 2, q -> {
                    int s = SafeParcel.beginObject(q);
                    SafeParcel.writeBoolean(q, 1, on);
                    SafeParcel.writeBoolean(q, 2, on);
                    SafeParcel.writeBoolean(q, 3, false);
                    SafeParcel.writeBoolean(q, 4, true);
                    SafeParcel.writeBoolean(q, 5, true);
                    SafeParcel.writeBoolean(q, 6, false);
                    SafeParcel.finishObject(q, s);
                });
                SafeParcel.finishObject(d, o);
                cb.transact(1, d, r, 0);
            } catch (Throwable t) {
                GoogleSignInBroker.log("location: settings callback: " + t);
            } finally {
                d.recycle();
                r.recycle();
            }
        });
    }

    // ------------------------------------------------------------------ callbacks

    static void callListener(IBinder b, Location l) {
        transact(b, LISTENER_DESC, 1, p -> writeTypedLocation(p, l));
    }

    static void callLocationResult(IBinder b, Location l) {
        transact(b, CALLBACK_DESC, 1, p -> {
            p.writeInt(1);
            int o = SafeParcel.beginObject(p);
            List<SafeParcel.Body> items = Collections.singletonList(q -> l.writeToParcel(q, 0));
            SafeParcel.writeTypedList(p, 1, items);
            SafeParcel.finishObject(p, o);
        });
    }

    static void callAvailability(IBinder b, boolean ok) {
        transact(b, CALLBACK_DESC, 2, p -> { p.writeInt(1); writeAvailability(p, ok); });
    }

    static void callLocationStatus(IBinder b, int code, String msg, Location l) {
        transact(b, STATUS_LOC_DESC, 1, p -> {
            p.writeInt(1);
            IdentitySignInService.writeStatus(p, code, msg);
            writeTypedLocation(p, l);
        });
    }

    static void callAvailabilityStatus(IBinder b, boolean ok) {
        transact(b, AVAIL_DESC, 1, p -> {
            p.writeInt(1);
            IdentitySignInService.writeStatus(p, STATUS_SUCCESS, null);
            p.writeInt(1);
            writeAvailability(p, ok);
        });
    }

    static void callStatus(IBinder b, int code, String msg) {
        transact(b, IdentitySignInService.STATUS_CB_DESC, 1, p -> {
            p.writeInt(1);
            IdentitySignInService.writeStatus(p, code, msg);
        });
    }

    static void callFused(IBinder b, int code, String msg) {
        transact(b, FUSED_DESC, 1, p -> {
            p.writeInt(1);
            int o = SafeParcel.beginObject(p);
            SafeParcel.writeObject(p, 1, q -> IdentitySignInService.writeStatus(q, code, msg));
            SafeParcel.finishObject(p, o);
        });
    }

    /** The app's own LocationResult for a PendingIntent extra: built by the
     *  app's CREATOR from the wire bytes, since the class is the app's. */
    static void sendPendingIntent(PendingIntent pi, Location l) {
        if (pi == null) return;
        try {
            Intent i = pi.getIntent();
            if (i == null) return;
            Parcel p = Parcel.obtain();
            try {
                int o = SafeParcel.beginObject(p);
                SafeParcel.writeTypedList(p, 1, Collections.singletonList(q -> l.writeToParcel(q, 0)));
                SafeParcel.finishObject(p, o);
                p.setDataPosition(0);
                ClassLoader cl = Thread.currentThread().getContextClassLoader();
                android.app.Application app = android.app.ActivityThread.currentApplication();
                if (app != null) cl = app.getClassLoader();
                Class<?> c = Class.forName("com.google.android.gms.location.LocationResult", true, cl);
                Object creator = c.getField("CREATOR").get(null);
                Object result = ((android.os.Parcelable.Creator<?>) creator).createFromParcel(p);
                i.putExtra("com.google.android.gms.location.EXTRA_LOCATION_RESULT",
                        (android.os.Parcelable) result);
            } finally {
                p.recycle();
            }
            pi.send();
        } catch (Throwable t) {
            GoogleSignInBroker.log("location: PendingIntent delivery failed: " + t);
        }
    }

    interface Writer { void write(Parcel p); }

    static void transact(IBinder b, String desc, int code, Writer w) {
        if (b == null) return;
        Parcel d = Parcel.obtain();
        Parcel r = Parcel.obtain();
        try {
            d.writeInterfaceToken(desc);
            w.write(d);
            b.transact(code, d, r, IBinder.FLAG_ONEWAY);
        } catch (Throwable t) {
            GoogleSignInBroker.log("location: callback " + desc + "#" + code + " failed: " + t);
        } finally {
            d.recycle();
            r.recycle();
        }
    }

    // ------------------------------------------------------------------ wire helpers

    static void writeTypedLocation(Parcel p, Location l) {
        if (l == null) { p.writeInt(0); return; }
        p.writeInt(1);
        l.writeToParcel(p, 0);
    }

    static void writeAvailability(Parcel p, boolean ok) {
        int o = SafeParcel.beginObject(p);
        SafeParcel.writeInt(p, 1, ok ? 0 : 1);
        SafeParcel.writeInt(p, 2, ok ? 0 : 1);
        SafeParcel.writeLong(p, 3, SystemClock.elapsedRealtimeNanos());
        SafeParcel.writeInt(p, 4, ok ? 0 : 1000);
        SafeParcel.finishObject(p, o);
    }

    /** A typed SafeParcelable argument: 0 = null, else 1 + the object. */
    static Map<Integer, SafeParcel.Field> readTyped(Parcel data) {
        return data.readInt() != 0 ? SafeParcel.readObject(data) : null;
    }

    static float readFloat(Parcel p, Map<Integer, SafeParcel.Field> f, int id, float def) {
        SafeParcel.Field x = f.get(id);
        if (x == null) return def;
        int save = p.dataPosition();
        p.setDataPosition(x.start);
        float v = p.readFloat();
        p.setDataPosition(save);
        return v;
    }

    static IBinder readBinder(Parcel p, Map<Integer, SafeParcel.Field> f, int id) {
        SafeParcel.Field x = f.get(id);
        if (x == null || x.size == 0) return null;
        int save = p.dataPosition();
        p.setDataPosition(x.start);
        IBinder b = p.readStrongBinder();
        p.setDataPosition(save);
        return b;
    }

    static PendingIntent readPendingIntent(Parcel p, Map<Integer, SafeParcel.Field> f, int id) {
        SafeParcel.Field x = f.get(id);
        if (x == null || x.size == 0) return null;
        int save = p.dataPosition();
        try {
            p.setDataPosition(x.start);
            return PendingIntent.CREATOR.createFromParcel(p);
        } catch (Throwable t) {
            return null;
        } finally {
            p.setDataPosition(save);
        }
    }

    static final class Receiver {
        int type;
        IBinder oldBinder, binder;
        PendingIntent pendingIntent;
    }

    static Receiver readReceiver(Parcel data) {
        Map<Integer, SafeParcel.Field> f = readTyped(data);
        if (f == null) return null;
        Receiver r = new Receiver();
        r.type = SafeParcel.readInt(data, f, 1, 0);
        r.oldBinder = readBinder(data, f, 2);
        r.binder = readBinder(data, f, 3);
        r.pendingIntent = readPendingIntent(data, f, 4);
        return r;
    }
}
