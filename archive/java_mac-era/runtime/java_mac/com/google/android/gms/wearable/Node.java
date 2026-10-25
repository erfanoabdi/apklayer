package com.google.android.gms.wearable;

// com.google.android.gms.wearable.Node -- a Wear node (watch/phone) in the
// connected network. No node is ever connected on a de-Googled device, so
// NodeClient returns empty node lists; the type exists so apps that iterate them
// link. Ref: developers.google.com/android/reference/.../wearable/Node
public interface Node {
    String getId();
    String getDisplayName();
    boolean isNearby();
}
