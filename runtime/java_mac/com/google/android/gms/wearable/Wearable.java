package com.google.android.gms.wearable;

import android.content.Context;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

// com.google.android.gms.wearable.Wearable -- entry point for the Wear OS data
// layer. On a de-Googled device no Wear node is ever connected, so the clients
// report empty (getConnectedNodes -> []) or fail cleanly (sendMessage ->
// API_NOT_CONNECTED). NEVER fabricates a node or a delivered message. Mirrors the
// published static factory. Ref: developers.google.com/android/reference/.../Wearable
public final class Wearable {
    private Wearable() {}

    public static MessageClient getMessageClient(Context context) { return MESSAGE; }
    public static DataClient getDataClient(Context context) { return DATA; }
    public static NodeClient getNodeClient(Context context) { return NODE; }
    public static CapabilityClient getCapabilityClient(Context context) { return CAPABILITY; }

    private static <T> Task<T> notConnected() {
        return Tasks.forException(new ApiException(
                new Status(CommonStatusCodes.API_NOT_CONNECTED, "No Wear node connected")));
    }

    private static final MessageClient MESSAGE = new MessageClient() {
        @Override public Task<Integer> sendMessage(String nodeId, String path, byte[] data) { return notConnected(); }
        @Override public Task<Void> addListener(OnMessageReceivedListener listener) { return Tasks.forResult(null); }
        @Override public Task<Boolean> removeListener(OnMessageReceivedListener listener) { return Tasks.forResult(Boolean.TRUE); }
    };

    private static final DataClient DATA = new DataClient() {
        @Override public Task<Object> putDataItem(Object request) { return notConnected(); }
        @Override public Task<Integer> deleteDataItems(android.net.Uri uri) { return Tasks.forResult(0); }
        @Override public Task<Void> addListener(OnDataChangedListener listener) { return Tasks.forResult(null); }
        @Override public Task<Boolean> removeListener(OnDataChangedListener listener) { return Tasks.forResult(Boolean.TRUE); }
    };

    private static final NodeClient NODE = new NodeClient() {
        @Override public Task<List<Node>> getConnectedNodes() { return Tasks.forResult((List<Node>) new ArrayList<Node>()); }
        @Override public Task<Node> getLocalNode() { return notConnected(); }
    };

    private static final CapabilityClient CAPABILITY = new CapabilityClient() {
        @Override public Task<Map<String, Object>> getAllCapabilities(int nodeFilter) {
            return Tasks.forResult((Map<String, Object>) Collections.<String, Object>emptyMap());
        }
        @Override public Task<Object> getCapability(String capability, int nodeFilter) { return notConnected(); }
        @Override public Task<Void> addListener(OnCapabilityChangedListener listener, String capability) { return Tasks.forResult(null); }
        @Override public Task<Boolean> removeListener(OnCapabilityChangedListener listener) { return Tasks.forResult(Boolean.TRUE); }
    };
}
