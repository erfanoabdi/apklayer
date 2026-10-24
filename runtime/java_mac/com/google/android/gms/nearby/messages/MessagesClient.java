package com.google.android.gms.nearby.messages;

import com.google.android.gms.tasks.Task;

// com.google.android.gms.nearby.messages.MessagesClient -- the (deprecated)
// Nearby Messages API. Needs the Nearby backend, absent on a de-Googled device,
// so publish/subscribe fail cleanly. Message/MessageListener model types are
// passed as Object to avoid modeling the full surface (nothing here produces a
// populated one). Mirrors the published intent.
// Ref: developers.google.com/android/reference/.../messages/MessagesClient
public interface MessagesClient {

    Task<Void> publish(Object message);

    Task<Void> unpublish(Object message);

    Task<Void> subscribe(Object messageListener);

    Task<Void> unsubscribe(Object messageListener);
}
