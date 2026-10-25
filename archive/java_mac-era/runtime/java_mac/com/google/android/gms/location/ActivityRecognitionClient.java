// com.google.android.gms.location.ActivityRecognitionClient -- subscribes to
// detected-activity (walking/driving/still) and activity-transition updates.
// Ref: developers.google.com/android/reference/com/google/android/gms/location/
// ActivityRecognitionClient.
//
// Activity recognition is a Google-services classifier we do NOT run. The
// honest behavior of a device without the classifier is: registration succeeds
// (Task<Void> completes) but no activity updates are ever delivered. Apps that
// gate on the registration Task proceed; apps that wait for an activity update
// simply never receive one (same as a stationary device the classifier hasn't
// resolved). No fabricated activity is reported.
package com.google.android.gms.location;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;

import com.google.android.gms.common.api.Api;
import com.google.android.gms.common.api.GoogleApi;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;

public class ActivityRecognitionClient extends GoogleApi<Api.ApiOptions.NoOptions> {

    static final Api<Api.ApiOptions.NoOptions> API =
            new Api<>("ActivityRecognition.API");

    public ActivityRecognitionClient(Context context) {
        super(context, API, Api.ApiOptions.NO_OPTIONS);
    }

    public ActivityRecognitionClient(Activity activity) {
        super(activity, API, Api.ApiOptions.NO_OPTIONS, Settings.DEFAULT_SETTINGS);
    }

    public Task<Void> requestActivityUpdates(long detectionIntervalMillis, PendingIntent callbackIntent) {
        return Tasks.forResult(null);
    }

    public Task<Void> removeActivityUpdates(PendingIntent callbackIntent) {
        return Tasks.forResult(null);
    }

    public Task<Void> requestActivityTransitionUpdates(Object request, PendingIntent pendingIntent) {
        return Tasks.forResult(null);
    }

    public Task<Void> removeActivityTransitionUpdates(PendingIntent pendingIntent) {
        return Tasks.forResult(null);
    }

    public Task<Void> requestSleepSegmentUpdates(PendingIntent pendingIntent, Object request) {
        return Tasks.forResult(null);
    }

    public Task<Void> removeSleepSegmentUpdates(PendingIntent pendingIntent) {
        return Tasks.forResult(null);
    }
}
