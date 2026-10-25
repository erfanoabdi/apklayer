// com.google.android.gms.location.GeofencingClient -- registers geofences.
// Ref: developers.google.com/android/reference/com/google/android/gms/location/
// GeofencingClient. On the no-Play-backend device there is no fix source to
// evaluate geofence transitions, so registration succeeds (Task<Void> completes)
// but no transition broadcasts ever fire -- the honest behavior of a device
// whose location provider yields no fixes.
package com.google.android.gms.location;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;

import com.google.android.gms.common.api.Api;
import com.google.android.gms.common.api.GoogleApi;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

import java.util.List;

public class GeofencingClient extends GoogleApi<Api.ApiOptions.NoOptions> {

    static final Api<Api.ApiOptions.NoOptions> API =
            new Api<>("LocationServices.GeofencingApi");

    GeofencingClient(Context context) {
        super(context, API, Api.ApiOptions.NO_OPTIONS);
    }

    GeofencingClient(Activity activity) {
        super(activity, API, Api.ApiOptions.NO_OPTIONS, Settings.DEFAULT_SETTINGS);
    }

    public Task<Void> addGeofences(GeofencingRequest geofencingRequest, PendingIntent pendingIntent) {
        return Tasks.forResult(null);
    }

    public Task<Void> removeGeofences(PendingIntent pendingIntent) {
        return Tasks.forResult(null);
    }

    public Task<Void> removeGeofences(List<String> requestIds) {
        return Tasks.forResult(null);
    }
}
