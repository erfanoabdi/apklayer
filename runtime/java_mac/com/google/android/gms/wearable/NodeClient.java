package com.google.android.gms.wearable;

import com.google.android.gms.tasks.Task;
import java.util.List;

// com.google.android.gms.wearable.NodeClient -- queries the connected Wear nodes.
// No node is ever connected on a de-Googled device, so getConnectedNodes returns
// an empty list and getLocalNode reports a benign local node. NEVER fabricates a
// remote node. Mirrors the published method set.
// Ref: developers.google.com/android/reference/.../wearable/NodeClient
public interface NodeClient {

    Task<List<Node>> getConnectedNodes();

    Task<Node> getLocalNode();
}
