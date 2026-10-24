// com.google.android.gms.location.LocationSettingsResponse -- the success
// payload of SettingsClient.checkLocationSettings. Ref: developers.google.com/
// android/reference/com/google/android/gms/location/LocationSettingsResponse.
// It carries a Result (Status) and the LocationSettingsStates. We report a
// SUCCESS status with usable platform providers so apps proceed past the
// "is location enabled?" gate without a resolution dialog.
package com.google.android.gms.location;

import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.Result;
import com.google.android.gms.common.api.Status;

public final class LocationSettingsResponse implements Result {
    private final Status status;
    private final LocationSettingsStates states;

    LocationSettingsResponse(Status status, LocationSettingsStates states) {
        this.status = status;
        this.states = states;
    }

    static LocationSettingsResponse satisfied() {
        return new LocationSettingsResponse(
                new Status(CommonStatusCodes.SUCCESS),
                new LocationSettingsStates(true, true));
    }

    public LocationSettingsStates getLocationSettingsStates() { return states; }

    @Override
    public Status getStatus() { return status; }
}
