// android.telecom.ConnectionService -- the Service an app publishes so Telecom
// can ask it to create and manage Connections.
//
// The SERVICE half is real: it extends android.app.Service, so an app's
// subclass is a normal component and its onCreate/onDestroy run as usual.
// Signal and Telegram both subclass it and rely on nothing more than that.
//
// onBind() returns NULL, deliberately. On Android it returns the binder
// Telecom uses to drive the service. Here there is no Telecom service to bind
// it, and handing back a binder nothing is connected to would be the inert
// success this package avoids: the app would believe Telecom had attached and
// wait for onCreateOutgoingConnection callbacks that cannot arrive. Null is
// also what android.app.Service returns by default for a service that offers
// no binding, so callers already handle it.
//
// Consequently NONE of the onCreate*Connection / onCreate*Failed callbacks ever
// fire. They exist to be OVERRIDDEN -- that is the entire reason the class had
// to link -- and an app finds out that system-integrated calling is
// unavailable from TelecomManager, which refuses the registration outright
// rather than leaving the app waiting on this service.
//
// Mirrors telecomm/java/android/telecom/ConnectionService.java; public member
// list confirmed against frameworks/base/core/api/current.txt.
//
// THE CONFERENCE AND REMOTE FAMILIES, added when the package was completed.
// Both follow the same rule as the rest of the file. The conference half is
// local bookkeeping (see Conference), so addConference/getAllConferences do
// what AOSP does minus the Telecom notify, and the onCreate*Conference*
// callbacks are inbound and therefore never fire. The REMOTE half is the one
// worth reading twice: createRemoteOutgoingConnection() and its three
// siblings return NULL. That is not a shortcut -- on AOSP they are the
// connection-manager path, they require Telecom to have bound a remote
// ConnectionService, and every one of them is @Nullable in
// frameworks/base/core/api/current.txt precisely because AOSP returns null
// when there is no remote service to ask. Handing back an inert
// RemoteConnection instead would be the "accepted, then silent" failure this
// package exists to avoid: the caller would drive a call through an object
// wired to nothing.
package android.telecom;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import java.util.Collection;
import java.util.Collections;

public abstract class ConnectionService extends Service {

    public static final String SERVICE_INTERFACE = "android.telecom.ConnectionService";

    /** Null: nothing can bind this, so claiming otherwise would mislead. */
    @Override
    public final IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * Empty: connections are tracked by Telecom, which never hands us any.
     * Empty is the same answer AOSP gives before Telecom adds a call, so a
     * caller iterating it behaves correctly rather than seeing a fabricated
     * connection.
     */
    public final Collection<Connection> getAllConnections() {
        return Collections.emptyList();
    }

    /**
     * Empty for the same reason getAllConnections() is: conferences are
     * tracked by Telecom, which never hands us any. AOSP returns the same
     * empty collection before Telecom adds one.
     */
    public final Collection<Conference> getAllConferences() {
        return Collections.emptyList();
    }

    /**
     * AOSP registers the Conference with Telecom so it appears in the in-call
     * UI. There is no Telecom service to register it with, so the call is
     * inert HERE -- but nothing is waiting on a result: the app already holds
     * the Conference object it built, keeps driving it locally (Conference is
     * a real state machine), and reads its own state back. Contrast
     * TelecomManager.addNewIncomingCall, which THROWS because the app would
     * otherwise be waiting for a callback.
     */
    public final void addConference(Conference conference) {
    }

    /**
     * The connection-manager factories. Null on a device with no Telecom
     * service -- AOSP's own documented answer, see the header.
     */
    public final RemoteConnection createRemoteOutgoingConnection(
            PhoneAccountHandle connectionManagerPhoneAccount, ConnectionRequest request) {
        return null;
    }

    /** @see #createRemoteOutgoingConnection */
    public final RemoteConnection createRemoteIncomingConnection(
            PhoneAccountHandle connectionManagerPhoneAccount, ConnectionRequest request) {
        return null;
    }

    /** @see #createRemoteOutgoingConnection */
    public final RemoteConference createRemoteOutgoingConference(
            PhoneAccountHandle connectionManagerPhoneAccount, ConnectionRequest request) {
        return null;
    }

    /** @see #createRemoteOutgoingConnection */
    public final RemoteConference createRemoteIncomingConference(
            PhoneAccountHandle connectionManagerPhoneAccount, ConnectionRequest request) {
        return null;
    }

    /**
     * Asks the REMOTE ConnectionService to merge two of its connections. No
     * RemoteConnection can exist here (the factories above return null), so
     * there is nothing this could act on; AOSP also no-ops when the remote
     * connection is not one it owns.
     */
    public final void conferenceRemoteConnections(RemoteConnection a, RemoteConnection b) {
    }

    /**
     * @hide on AOSP. Telecom calls this to hand an app back a connection it
     * created out-of-band; without Telecom there is nothing to add it to.
     */
    public final void addExistingConnection(PhoneAccountHandle phoneAccountHandle,
            Connection connection) {
    }

    /** @hide on AOSP: part of the focus protocol Telecom drives. */
    public final void connectionServiceFocusReleased() {
    }

    // Inbound from Telecom. Never invoked here -- see the header. Present so an
    // app's overrides compile and link.

    public Connection onCreateOutgoingConnection(PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
        return null;
    }

    public Connection onCreateIncomingConnection(PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
        return null;
    }

    public void onCreateOutgoingConnectionFailed(PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
    }

    public void onCreateIncomingConnectionFailed(PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
    }

    public Connection onCreateIncomingHandoverConnection(
            PhoneAccountHandle fromPhoneAccountHandle, ConnectionRequest request) {
        return null;
    }

    public Connection onCreateOutgoingHandoverConnection(
            PhoneAccountHandle fromPhoneAccountHandle, ConnectionRequest request) {
        return null;
    }

    public void onCreateConnectionComplete(Connection connection) {
    }

    public Conference onCreateOutgoingConference(PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
        return null;
    }

    public Conference onCreateIncomingConference(PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
        return null;
    }

    public void onCreateOutgoingConferenceFailed(PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
    }

    public void onCreateIncomingConferenceFailed(PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
    }

    public void onCreateConferenceComplete(Conference conference) {
    }

    /** Telecom asks the service to merge two of its own Connections. */
    public void onConference(Connection connection1, Connection connection2) {
    }

    public void onRemoteConferenceAdded(RemoteConference conference) {
    }

    public void onRemoteExistingConnectionAdded(RemoteConnection connection) {
    }

    public void onHandoverFailed(ConnectionRequest request, int error) {
    }

    public void onConnectionServiceFocusGained() {
    }

    public void onConnectionServiceFocusLost() {
    }
}
