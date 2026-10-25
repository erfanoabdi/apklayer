package com.google.android.gms.common.api;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.IntentSender;

// com.google.android.gms.common.api.ResolvableApiException -- an ApiException
// whose Status carries a resolution the user could act on (e.g. a
// RESOLUTION_REQUIRED for location settings). On Mac.apk there is no Play
// backend that ever produces a resolution PendingIntent, so getResolution is
// null and startResolutionForResult is a clean no-op, matching a de-Googled
// device. Type kept exact so apps that catch it compile.
public class ResolvableApiException extends ApiException {

    public ResolvableApiException(Status status) {
        super(status);
    }

    public PendingIntent getResolution() { return mStatus.getResolution(); }

    public void startResolutionForResult(Activity activity, int requestCode)
            throws IntentSender.SendIntentException {
        mStatus.startResolutionForResult(activity, requestCode);
    }
}
