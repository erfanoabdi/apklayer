// com.google.android.gms.location.LocationSettingsRequest -- describes the set
// of LocationRequests an app needs satisfied; passed to
// SettingsClient.checkLocationSettings. Ref: developers.google.com/android/
// reference/com/google/android/gms/location/LocationSettingsRequest. We retain
// the request list and the alwaysShow / needBle flags for fidelity; the
// SettingsClient ignores them (settings are reported satisfiable).
package com.google.android.gms.location;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class LocationSettingsRequest {
    private final List<LocationRequest> requests;
    private final boolean alwaysShow;
    private final boolean needBle;

    private LocationSettingsRequest(List<LocationRequest> requests,
                                    boolean alwaysShow, boolean needBle) {
        this.requests = Collections.unmodifiableList(new ArrayList<>(requests));
        this.alwaysShow = alwaysShow;
        this.needBle = needBle;
    }

    public boolean isAlwaysShow() { return alwaysShow; }
    public boolean isNeedBle() { return needBle; }

    public static final class Builder {
        private final List<LocationRequest> requests = new ArrayList<>();
        private boolean alwaysShow = false;
        private boolean needBle = false;

        public Builder addLocationRequest(LocationRequest request) {
            if (request != null) requests.add(request);
            return this;
        }

        public Builder addAllLocationRequests(Collection<LocationRequest> requests) {
            if (requests != null) this.requests.addAll(requests);
            return this;
        }

        public Builder setAlwaysShow(boolean show) { this.alwaysShow = show; return this; }
        public Builder setNeedBle(boolean needBle) { this.needBle = needBle; return this; }

        public LocationSettingsRequest build() {
            return new LocationSettingsRequest(requests, alwaysShow, needBle);
        }
    }
}
