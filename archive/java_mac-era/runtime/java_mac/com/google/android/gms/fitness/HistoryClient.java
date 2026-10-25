package com.google.android.gms.fitness;

import com.google.android.gms.tasks.Task;

// com.google.android.gms.fitness.HistoryClient -- reads/writes historical fitness
// data. Mac.apk has no Google Fit store, so every read returns empty / every
// write fails cleanly (API_NOT_CONNECTED). NEVER fabricates fitness data. The
// request/response model types are passed as Object to avoid modeling the entire
// Fit data surface (nothing here produces a populated one). Mirrors the published
// intent. Ref: developers.google.com/android/reference/.../fitness/HistoryClient
public interface HistoryClient {

    Task<Object> readData(Object dataReadRequest);

    Task<Object> readDailyTotal(com.google.android.gms.fitness.data.DataType dataType);

    Task<Void> insertData(Object dataSet);

    Task<Void> deleteData(Object dataDeleteRequest);
}
