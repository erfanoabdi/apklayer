// com.google.android.gms.location.LocationResult -- the batch of locations
// delivered to a LocationCallback. Ref: developers.google.com/android/
// reference/com/google/android/gms/location/LocationResult. Wraps a list of
// android.location.Location and exposes the last (newest) one. We provide the
// public create() factory and getters; the no-Play device never constructs a
// non-empty result organically, but apps may build one for tests.
package com.google.android.gms.location;

import android.location.Location;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LocationResult {
    public static final String EXTRA_LOCATION_RESULT =
            "com.google.android.gms.location.EXTRA_LOCATION_RESULT";

    private final List<Location> locations;

    private LocationResult(List<Location> locations) {
        this.locations = locations == null
                ? Collections.<Location>emptyList()
                : new ArrayList<>(locations);
    }

    public static LocationResult create(List<Location> locations) {
        return new LocationResult(locations);
    }

    // Newest location in the batch, or null if the batch is empty. The real SDK
    // returns the last element of the (chronologically ordered) list.
    public Location getLastLocation() {
        return locations.isEmpty() ? null : locations.get(locations.size() - 1);
    }

    public List<Location> getLocations() {
        return Collections.unmodifiableList(locations);
    }

    @Override
    public String toString() {
        return "LocationResult[locations: " + locations + "]";
    }
}
