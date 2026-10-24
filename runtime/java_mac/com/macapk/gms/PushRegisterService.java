// com.macapk.gms.PushRegisterService -- the com.google.android.gms IID/register
// service an app's Firebase SDK binds to for a push token.
//
// WHAT THIS IS. When an app calls FirebaseMessaging.getToken(), its OWN bundled
// SDK (our com.google.firebase.* shadows never load for a bundling app -- they
// are child-first) does: bindService(Intent("com.google.android.c2dm.intent.
// REGISTER").setPackage("com.google.android.gms")), reads a Messenger off the
// returned binder, and sends a Message. This service is what answers on the
// other end -- AS com.google.android.gms, in-process. It runs the real
// GcmRegisterClient (register3, checkin creds) and replies with the token.
//
// ⛔ IT MUST ALWAYS REPLY, PROMPTLY. The whole reason com.google.android.gms is
// kept absent by default is that a present-but-silent GMS makes Firebase's Rpc
// time out and retry forever (the "Rpc: No response" loop that hung Minecraft
// Bedrock). So every request gets a reply within the client's ~30s window:
// success carries registration_id, failure carries a well-formed error string.
// A reply that says "error" cleanly is correct; a reply that never comes is the
// bug this service exists to avoid.
//
// THE MESSAGE PROTOCOL (from the real SDK's Rpc + microG's PushRegisterHandler):
//   request  Message{ what=0|1, arg1=<id>, replyTo=<client Messenger>,
//                     data=Bundle{ oneWay:bool, pkg:<app>, data:<register bundle> } }
//              what 0 = legacy (reply an Intent in obj), what 1 = modern (reply a
//              data bundle). what 2 = ACK a delivered message (feeds the MCS
//              StreamAck once the receive channel exists).
//   reply    Message{ what=1, arg1=<same id>, data=Bundle{ data:Bundle{
//                     registration_id:<token> | error:<code> } } }
//   The register bundle carries sender/subtype (the sender id), scope, and the
//   X- attributes; `delete` on it means unregister.
//
// Gated at the seam: it only exists as a bound target when
// PlayServices.pushBackendEnabled() (MacApkPackageManager resolves it only
// then), so with the flag off this class is never instantiated.
//
// Reference: docs/gms-fcm-mcs-wire_2026-09-05.md (Part C).
package com.macapk.gms;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

public final class PushRegisterService extends android.app.Service {

    private Messenger mMessenger;
    private HandlerThread mThread;

    @Override public void onCreate() {
        // A dedicated thread: the register call is a blocking HTTP round-trip and
        // must not run on the app's main thread (which is where it binds from).
        mThread = new HandlerThread("macapk-gms-register");
        mThread.start();
        mMessenger = new Messenger(new Handler(mThread.getLooper(), this::handle));
    }

    @Override public IBinder onBind(Intent intent) {
        return mMessenger == null ? null : mMessenger.getBinder();
    }

    @Override public void onDestroy() {
        if (mThread != null) { try { mThread.quitSafely(); } catch (Throwable ignored) {} }
    }

    // ===================================================================

    private boolean handle(Message msg) {
        final int what = msg.what;
        final int id = msg.arg1;
        final Messenger reply = msg.replyTo;
        Bundle data = msg.getData();

        // what=2 is an ACK of a delivered push. Until the MCS receive channel
        // exists there is nothing to ack to; reply {ack:true} so the SDK's
        // one-way ack does not hang, and hand the id off for when it does.
        if (what == 2) {
            String messageId = innerData(data) != null
                    ? innerData(data).getString("google.message_id") : null;
            McsClient.ackMessage(messageId);        // no-op until the socket is up
            replyBundle(reply, id, 1, ackBundle());
            return true;
        }

        // Register (what 0/1) runs the HTTP call, then replies. We are already on
        // the HandlerThread, so blocking here is fine.
        Bundle inner = innerData(data);
        String pkg = data != null ? data.getString("pkg") : null;
        if (pkg == null && inner != null) pkg = inner.getString("pkg");

        Bundle result = doRegister(pkg, inner);

        if (what == 0) {
            // Legacy path: the SDK expects an Intent in Message.obj with action
            // REGISTRATION carrying registration_id/error.
            Intent regIntent = new Intent("com.google.android.c2dm.intent.REGISTRATION");
            if (result.getString("registration_id") != null) {
                regIntent.putExtra("registration_id", result.getString("registration_id"));
            }
            if (result.getString("error") != null) {
                regIntent.putExtra("error", result.getString("error"));
            }
            Message m = Message.obtain();
            m.what = 0; m.arg1 = id; m.obj = regIntent;
            send(reply, m);
        } else {
            // Modern path: reply a data bundle.
            replyBundle(reply, id, 1, result);
        }
        return true;
    }

    /** Run the real registration and shape the result the SDK reads. Never
     *  throws to the caller: any failure becomes a well-formed error reply. */
    private Bundle doRegister(String pkg, Bundle registerBundle) {
        Bundle out = new Bundle();
        try {
            if (pkg == null || pkg.isEmpty()) {
                out.putString("error", "INVALID_PARAMETERS");
                return out;
            }
            String sender = senderOf(registerBundle);
            boolean delete = registerBundle != null
                    && ("1".equals(registerBundle.getString("delete"))
                        || registerBundle.getBoolean("delete", false));
            String cert = certSha1Of(pkg);
            int versionCode = versionCodeOf(pkg);
            int targetSdk = targetSdkOf(pkg);

            java.util.Map<String, String> extras = extrasFrom(registerBundle);
            GcmRegisterClient.Result r = GcmRegisterClient.register(
                    pkg, sender, cert, versionCode, targetSdk, extras, delete);
            if (r.ok()) {
                out.putString("registration_id", r.token);
                // The app now has a token, so it expects delivery THIS run. Boot
                // defers the channel for a package that has never registered, so
                // this is the other half of that: bring it up now rather than at
                // the next launch. start() is idempotent (compareAndSet on
                // `running`), so a second registration is a no-op.
                try {
                    McsClient.start();
                } catch (Throwable st) {
                    log("could not start MCS channel after register: " + st);
                }
            } else if (delete && (r.error == null || r.error.contains("token"))) {
                out.putString("unregistered", pkg);
            } else {
                out.putString("error", r.error != null ? r.error : "SERVICE_NOT_AVAILABLE");
            }
        } catch (Throwable t) {
            out.putString("error", "SERVICE_NOT_AVAILABLE");
            log("register threw: " + t);
        }
        return out;
    }

    // ===================================================================

    private static Bundle innerData(Bundle data) {
        if (data == null) return null;
        try { return data.getBundle("data"); } catch (Throwable t) { return null; }
    }

    private static String senderOf(Bundle b) {
        if (b == null) return null;
        String s = b.getString("sender");
        if (s == null) s = b.getString("X-subtype");
        if (s == null) s = b.getString("subtype");
        return s;
    }

    /** The X- attributes the SDK put in the register bundle, passed through to
     *  register3. We forward every String extra except the ones GcmRegisterClient
     *  sets itself (app/sender/delete/device). */
    private static java.util.Map<String, String> extrasFrom(Bundle b) {
        java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
        if (b == null) return m;
        for (String key : b.keySet()) {
            if (key == null) continue;
            if ("pkg".equals(key) || "sender".equals(key) || "delete".equals(key)) continue;
            Object v = b.get(key);
            if (v instanceof String) m.put(key, (String) v);
        }
        return m;
    }

    private String certSha1Of(String pkg) {
        try {
            android.content.pm.PackageInfo pi = getPackageManager().getPackageInfo(
                    pkg, android.content.pm.PackageManager.GET_SIGNATURES);
            android.content.pm.Signature[] sigs = (pi != null) ? pi.signatures : null;
            if (sigs != null && sigs.length > 0 && sigs[0] != null) {
                byte[] der = sigs[0].toByteArray();
                if (der != null && der.length > 0) {
                    byte[] sha1 = java.security.MessageDigest.getInstance("SHA-1").digest(der);
                    StringBuilder sb = new StringBuilder(40);
                    for (byte x : sha1) sb.append(String.format("%02x", x));
                    return sb.toString();
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private int versionCodeOf(String pkg) {
        try {
            android.content.pm.PackageInfo pi = getPackageManager().getPackageInfo(pkg, 0);
            if (pi != null && pi.versionCode > 0) return pi.versionCode;
        } catch (Throwable ignored) {}
        return 1;
    }

    private int targetSdkOf(String pkg) {
        try {
            android.content.pm.ApplicationInfo ai = getPackageManager().getApplicationInfo(pkg, 0);
            if (ai != null && ai.targetSdkVersion > 0) return ai.targetSdkVersion;
        } catch (Throwable ignored) {}
        return 0;
    }

    private static Bundle ackBundle() {
        Bundle b = new Bundle();
        b.putBoolean("ack", true);
        return b;
    }

    /** Reply Message{what, arg1=id, data={data:<result>}}. */
    private void replyBundle(Messenger reply, int id, int what, Bundle result) {
        Bundle outer = new Bundle();
        outer.putBundle("data", result);
        Message m = Message.obtain();
        m.what = what; m.arg1 = id; m.setData(outer);
        send(reply, m);
    }

    private void send(Messenger reply, Message m) {
        if (reply == null) return;
        try {
            reply.send(m);
        } catch (RemoteException e) {
            log("reply failed: " + e);
        }
    }

    static void log(String s) {
        try { com.macapk.loader.NativeLog.i("Mac.apk.GMS", "register-svc: " + s); }
        catch (Throwable t) { System.err.println("[register-svc] " + s); }
    }
}
