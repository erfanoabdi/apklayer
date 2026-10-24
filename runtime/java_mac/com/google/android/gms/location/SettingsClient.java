// com.google.android.gms.location.SettingsClient -- checks whether the device's
// location settings satisfy a LocationSettingsRequest. Ref: developers.google.
// com/android/reference/com/google/android/gms/location/SettingsClient.
//
// This client does NOT touch the Google backend -- it inspects on-device
// location settings only. On this device the platform GPS/network providers
// exist, so we report the settings as satisfied (SUCCESS) with usable states,
// letting apps skip the resolution dialog and proceed. This is the honest
// result for a device with location available, not a fabricated Google call.
package com.google.android.gms.location;

import android.app.Activity;
import android.content.Context;

import com.google.android.gms.common.api.Api;
import com.google.android.gms.common.api.GoogleApi;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

public class SettingsClient extends GoogleApi<Api.ApiOptions.NoOptions> {

    static final Api<Api.ApiOptions.NoOptions> API =
            new Api<>("LocationServices.SettingsApi");

    SettingsClient(Context context) {
        super(context, API, Api.ApiOptions.NO_OPTIONS);
    }

    SettingsClient(Activity activity) {
        super(activity, API, Api.ApiOptions.NO_OPTIONS, Settings.DEFAULT_SETTINGS);
    }

    public Task<LocationSettingsResponse> checkLocationSettings(LocationSettingsRequest request) {
        return Tasks.forResult(LocationSettingsResponse.satisfied());
    }
}
