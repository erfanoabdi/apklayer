package com.google.android.gms.wearable;

// com.google.android.gms.wearable.MessageEvent -- a received Wear message. No Wear
// node connects on a de-Googled device, so this is never delivered; the type
// exists so OnMessageReceivedListener implementations link. Mirrors the published
// accessors. Ref: developers.google.com/android/reference/.../wearable/MessageEvent
public interface MessageEvent {
    int getRequestId();
    String getPath();
    byte[] getData();
    String getSourceNodeId();
}
