// com.google.android.gms.location.ActivityRecognition -- the static factory for
// ActivityRecognitionClient. Ref: developers.google.com/android/reference/com/
// google/android/gms/location/ActivityRecognition.
package com.google.android.gms.location;

import android.app.Activity;
import android.content.Context;

import com.google.android.gms.common.api.Api;

public final class ActivityRecognition {
    private ActivityRecognition() {}

    public static final String ACTION_ACTIVITY_TRANSITION =
            "com.google.android.location.internal.EXTRA_ACTIVITY_TRANSITION_LIST";

    public static final Api<Api.ApiOptions.NoOptions> API = ActivityRecognitionClient.API;

    public static ActivityRecognitionClient getClient(Context context) {
        return new ActivityRecognitionClient(context);
    }

    public static ActivityRecognitionClient getClient(Activity activity) {
        return new ActivityRecognitionClient(activity);
    }
}
