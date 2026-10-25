// com.google.android.gms.location.GeofencingRequest -- a batch of geofences
// plus the initial-trigger flags, passed to GeofencingClient.addGeofences.
// Ref: developers.google.com/android/reference/com/google/android/gms/location/
// GeofencingRequest.
package com.google.android.gms.location;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class GeofencingRequest {
    public static final int INITIAL_TRIGGER_ENTER = 1;
    public static final int INITIAL_TRIGGER_EXIT = 2;
    public static final int INITIAL_TRIGGER_DWELL = 4;

    private final List<Geofence> geofences;
    private final int initialTrigger;

    private GeofencingRequest(List<Geofence> geofences, int initialTrigger) {
        this.geofences = Collections.unmodifiableList(new ArrayList<>(geofences));
        this.initialTrigger = initialTrigger;
    }

    public List<Geofence> getGeofences() { return geofences; }
    public int getInitialTrigger() { return initialTrigger; }

    public static final class Builder {
        private final List<Geofence> geofences = new ArrayList<>();
        private int initialTrigger = INITIAL_TRIGGER_ENTER | INITIAL_TRIGGER_DWELL;

        public Builder addGeofence(Geofence geofence) {
            if (geofence != null) geofences.add(geofence);
            return this;
        }

        public Builder addGeofences(List<Geofence> geofences) {
            if (geofences != null) this.geofences.addAll(geofences);
            return this;
        }

        public Builder setInitialTrigger(int initialTrigger) {
            this.initialTrigger = initialTrigger;
            return this;
        }

        public GeofencingRequest build() {
            return new GeofencingRequest(geofences, initialTrigger);
        }
    }
}
