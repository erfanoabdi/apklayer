package com.google.android.gms.nearby.connection;

import com.google.android.gms.tasks.Task;

// com.google.android.gms.nearby.connection.ConnectionsClient -- the Nearby
// Connections API. Peer-to-peer discovery/advertising needs the Nearby backend,
// which a de-Googled device lacks, so every operation fails cleanly
// (API_NOT_CONNECTED). The strategy/parameter model types are passed as Object so
// apps that call these methods link without us modeling the entire Connections
// surface (nothing here produces a populated one). Mirrors the published intent.
// Ref: developers.google.com/android/reference/.../connection/ConnectionsClient
public interface ConnectionsClient {

    Task<Void> startAdvertising(String name, String serviceId, Object connectionLifecycleCallback, Object advertisingOptions);

    Task<Void> startDiscovery(String serviceId, Object endpointDiscoveryCallback, Object discoveryOptions);

    void stopAdvertising();

    void stopDiscovery();

    void stopAllEndpoints();

    Task<Void> requestConnection(String name, String endpointId, Object connectionLifecycleCallback);

    Task<Void> acceptConnection(String endpointId, Object payloadCallback);

    Task<Void> rejectConnection(String endpointId);

    Task<Void> sendPayload(String endpointId, Object payload);

    void disconnectFromEndpoint(String endpointId);
}
