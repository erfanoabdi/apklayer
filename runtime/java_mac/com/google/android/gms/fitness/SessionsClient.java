package com.google.android.gms.fitness;

import com.google.android.gms.tasks.Task;

// com.google.android.gms.fitness.SessionsClient -- manages workout sessions. With
// no Google Fit store, start/stop/read fail cleanly. Request/response model types
// are passed as Object to avoid modeling the full Fit session surface. Mirrors
// the published intent. Ref: developers.google.com/android/reference/.../SessionsClient
public interface SessionsClient {

    Task<Void> startSession(Object session);

    Task<Object> stopSession(String identifier);

    Task<Object> readSession(Object sessionReadRequest);

    Task<Void> insertSession(Object sessionInsertRequest);
}
