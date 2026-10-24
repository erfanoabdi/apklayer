package com.google.android.gms.wearable;

import com.google.android.gms.tasks.Task;

// com.google.android.gms.wearable.DataClient -- syncs the Wear Data Layer. No
// Wear device is connected on a de-Googled device, so put/delete operations fail
// cleanly and listener registration is inert. The DataItem-level types
// (PutDataRequest/DataItem) are not modeled because nothing produces them here;
// methods take/return Object so apps that call them still link without us pulling
// in the entire Data Layer model. Mirrors the published method intent.
// Ref: developers.google.com/android/reference/.../wearable/DataClient
public interface DataClient {

    Task<Object> putDataItem(Object request);

    Task<Integer> deleteDataItems(android.net.Uri uri);

    Task<Void> addListener(OnDataChangedListener listener);

    Task<Boolean> removeListener(OnDataChangedListener listener);

    interface OnDataChangedListener {
        void onDataChanged(Object dataEventBuffer);
    }
}
