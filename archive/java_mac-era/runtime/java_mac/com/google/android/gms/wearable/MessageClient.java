package com.google.android.gms.wearable;

import com.google.android.gms.tasks.Task;

// com.google.android.gms.wearable.MessageClient -- sends messages to a connected
// Wear node. No Wear device is ever connected on a de-Googled device, so
// sendMessage fails cleanly (API_NOT_CONNECTED) and listener registration is
// inert. Mirrors the published method set. Ref: developers.google.com/android/reference
public interface MessageClient {

    Task<Integer> sendMessage(String nodeId, String path, byte[] data);

    Task<Void> addListener(OnMessageReceivedListener listener);

    Task<Boolean> removeListener(OnMessageReceivedListener listener);

    interface OnMessageReceivedListener {
        void onMessageReceived(MessageEvent messageEvent);
    }
}
