package com.google.android.gms.common.api;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;

import com.google.android.gms.common.ConnectionResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

// com.google.android.gms.common.api.GoogleApiClient -- the legacy (pre-GoogleApi)
// entry point classic GMS apps build to talk to Play Services. On a de-Googled
// device there is no Play Services system app to bind to, so a real connection
// attempt fails: connect() asynchronously delivers onConnectionFailed with
// ConnectionResult.SERVICE_MISSING, blockingConnect() returns that same
// ConnectionResult, and isConnected() stays false. This lets classic GMS apps
// init and take their "services unavailable" degrade branch instead of throwing
// NoClassDefFound. Mirrors the GMS public API (Builder + the two listener
// interfaces). NO impersonation: we never report a successful connection.
public abstract class GoogleApiClient {

    // Cause codes passed to ConnectionCallbacks.onConnectionSuspended.
    public static final int CAUSE_SERVICE_DISCONNECTED = 1;
    public static final int CAUSE_NETWORK_LOST = 2;

    public abstract void connect();
    public abstract ConnectionResult blockingConnect();
    public abstract ConnectionResult blockingConnect(long timeout, TimeUnit unit);
    public abstract void disconnect();
    public abstract void reconnect();
    public abstract boolean isConnected();
    public abstract boolean isConnecting();

    public abstract void registerConnectionCallbacks(ConnectionCallbacks listener);
    public abstract void unregisterConnectionCallbacks(ConnectionCallbacks listener);
    public abstract boolean isConnectionCallbacksRegistered(ConnectionCallbacks listener);
    public abstract void registerConnectionFailedListener(OnConnectionFailedListener listener);
    public abstract void unregisterConnectionFailedListener(OnConnectionFailedListener listener);
    public abstract boolean isConnectionFailedListenerRegistered(OnConnectionFailedListener listener);

    public abstract void stopAutoManage(android.app.Activity activity);

    public interface ConnectionCallbacks {
        int CAUSE_SERVICE_DISCONNECTED = 1;
        int CAUSE_NETWORK_LOST = 2;
        void onConnected(Bundle connectionHint);
        void onConnectionSuspended(int cause);
    }

    public interface OnConnectionFailedListener {
        void onConnectionFailed(ConnectionResult result);
    }

    // --- Builder -----------------------------------------------------------
    public static final class Builder {
        private final Context context;
        private final List<ConnectionCallbacks> connectionCallbacks =
                new ArrayList<ConnectionCallbacks>();
        private final List<OnConnectionFailedListener> connectionFailedListeners =
                new ArrayList<OnConnectionFailedListener>();
        private Handler handler;

        public Builder(Context context) { this.context = context; }

        public Builder(Context context, ConnectionCallbacks connectedListener,
                       OnConnectionFailedListener connectionFailedListener) {
            this.context = context;
            if (connectedListener != null) connectionCallbacks.add(connectedListener);
            if (connectionFailedListener != null) connectionFailedListeners.add(connectionFailedListener);
        }

        public <O extends Api.ApiOptions.NotRequiredOptions> Builder addApi(Api<O> api) { return this; }
        public <O extends Api.ApiOptions.HasOptions> Builder addApi(Api<O> api, O options) { return this; }
        public Builder addApiIfAvailable(Api<? extends Api.ApiOptions.NotRequiredOptions> api,
                                         Scope... scopes) { return this; }

        public Builder addConnectionCallbacks(ConnectionCallbacks listener) {
            if (listener != null) connectionCallbacks.add(listener);
            return this;
        }
        public Builder addOnConnectionFailedListener(OnConnectionFailedListener listener) {
            if (listener != null) connectionFailedListeners.add(listener);
            return this;
        }

        public Builder addScope(Scope scope) { return this; }
        public Builder setAccountName(String accountName) { return this; }
        public Builder setGravityForPopups(int gravityForPopups) { return this; }
        public Builder setViewForPopups(android.view.View viewForPopups) { return this; }
        public Builder setHandler(Handler handler) { this.handler = handler; return this; }

        public Builder enableAutoManage(android.app.Activity activity, int clientId,
                                        OnConnectionFailedListener unresolvedConnectionFailedListener) {
            if (unresolvedConnectionFailedListener != null)
                connectionFailedListeners.add(unresolvedConnectionFailedListener);
            return this;
        }
        public Builder enableAutoManage(android.app.Activity activity,
                                        OnConnectionFailedListener unresolvedConnectionFailedListener) {
            return enableAutoManage(activity, 0, unresolvedConnectionFailedListener);
        }

        public GoogleApiClient build() {
            return new UnavailableGoogleApiClient(context, handler,
                    connectionCallbacks, connectionFailedListeners);
        }
    }

    // The shipped implementation: every connection attempt resolves to
    // SERVICE_MISSING (de-Googled device). Listener dispatch goes through the
    // build-time Handler if one was supplied, otherwise the main thread, so the
    // callback runs where the app expects (it registered listeners to update UI).
    private static final class UnavailableGoogleApiClient extends GoogleApiClient {
        private final Handler handler;
        private final List<ConnectionCallbacks> connectionCallbacks;
        private final List<OnConnectionFailedListener> connectionFailedListeners;

        UnavailableGoogleApiClient(Context context, Handler handler,
                                   List<ConnectionCallbacks> cc,
                                   List<OnConnectionFailedListener> cf) {
            this.handler = (handler != null) ? handler
                    : new Handler(android.os.Looper.getMainLooper());
            this.connectionCallbacks = new ArrayList<ConnectionCallbacks>(cc);
            this.connectionFailedListeners = new ArrayList<OnConnectionFailedListener>(cf);
        }

        private static final ConnectionResult MISSING =
                new ConnectionResult(ConnectionResult.SERVICE_MISSING);

        @Override public void connect() {
            handler.post(new Runnable() {
                @Override public void run() {
                    for (OnConnectionFailedListener l : connectionFailedListeners) {
                        try { l.onConnectionFailed(MISSING); } catch (RuntimeException ignored) {}
                    }
                }
            });
        }
        @Override public ConnectionResult blockingConnect() { return MISSING; }
        @Override public ConnectionResult blockingConnect(long timeout, TimeUnit unit) { return MISSING; }
        @Override public void disconnect() {}
        @Override public void reconnect() { connect(); }
        @Override public boolean isConnected() { return false; }
        @Override public boolean isConnecting() { return false; }

        @Override public void registerConnectionCallbacks(ConnectionCallbacks l) {
            if (l != null && !connectionCallbacks.contains(l)) connectionCallbacks.add(l);
        }
        @Override public void unregisterConnectionCallbacks(ConnectionCallbacks l) {
            connectionCallbacks.remove(l);
        }
        @Override public boolean isConnectionCallbacksRegistered(ConnectionCallbacks l) {
            return connectionCallbacks.contains(l);
        }
        @Override public void registerConnectionFailedListener(OnConnectionFailedListener l) {
            if (l != null && !connectionFailedListeners.contains(l)) connectionFailedListeners.add(l);
        }
        @Override public void unregisterConnectionFailedListener(OnConnectionFailedListener l) {
            connectionFailedListeners.remove(l);
        }
        @Override public boolean isConnectionFailedListenerRegistered(OnConnectionFailedListener l) {
            return connectionFailedListeners.contains(l);
        }
        @Override public void stopAutoManage(Activity activity) {}
    }
}
