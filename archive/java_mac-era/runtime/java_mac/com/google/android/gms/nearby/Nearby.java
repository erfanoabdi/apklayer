package com.google.android.gms.nearby;

import android.content.Context;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.messages.MessagesClient;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

// com.google.android.gms.nearby.Nearby -- entry point for Nearby Connections /
// Messages. Both subsystems need the Nearby backend, which a de-Googled device
// lacks, so every returned client fails its operations cleanly
// (API_NOT_CONNECTED). NEVER fabricates a peer or a delivered message. Mirrors
// the published static factory. Ref: developers.google.com/android/reference/.../Nearby
public final class Nearby {
    private Nearby() {}

    public static ConnectionsClient getConnectionsClient(Context context) { return CONNECTIONS; }
    public static MessagesClient getMessagesClient(Context context) { return MESSAGES; }

    private static <T> Task<T> notConnected() {
        return Tasks.forException(new ApiException(
                new Status(CommonStatusCodes.API_NOT_CONNECTED, "Nearby unavailable")));
    }

    private static final ConnectionsClient CONNECTIONS = new ConnectionsClient() {
        @Override public Task<Void> startAdvertising(String name, String serviceId, Object connectionLifecycleCallback, Object advertisingOptions) { return notConnected(); }
        @Override public Task<Void> startDiscovery(String serviceId, Object endpointDiscoveryCallback, Object discoveryOptions) { return notConnected(); }
        @Override public void stopAdvertising() {}
        @Override public void stopDiscovery() {}
        @Override public void stopAllEndpoints() {}
        @Override public Task<Void> requestConnection(String name, String endpointId, Object connectionLifecycleCallback) { return notConnected(); }
        @Override public Task<Void> acceptConnection(String endpointId, Object payloadCallback) { return notConnected(); }
        @Override public Task<Void> rejectConnection(String endpointId) { return notConnected(); }
        @Override public Task<Void> sendPayload(String endpointId, Object payload) { return notConnected(); }
        @Override public void disconnectFromEndpoint(String endpointId) {}
    };

    private static final MessagesClient MESSAGES = new MessagesClient() {
        @Override public Task<Void> publish(Object message) { return notConnected(); }
        @Override public Task<Void> unpublish(Object message) { return notConnected(); }
        @Override public Task<Void> subscribe(Object messageListener) { return notConnected(); }
        @Override public Task<Void> unsubscribe(Object messageListener) { return notConnected(); }
    };
}
