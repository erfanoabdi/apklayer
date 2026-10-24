package com.google.android.play.core.review;

import android.app.Activity;
import com.google.android.play.core.tasks.Task;

// com.google.android.play.core.review.ReviewManager -- the entry point for the
// Play in-app review flow. On a de-Googled device there is no Play Store review
// UI to show, so requestReviewFlow() resolves with an inert ReviewInfo and
// launchReviewFlow() resolves immediately without displaying anything (exactly
// what real Play Core does when it decides not to show the dialog -- the app is
// contractually told NOT to assume the dialog appeared, and to continue). This
// lets the app's standard "request -> launch -> continue" sequence run instead
// of crashing on a missing class. Signatures mirror the published interface.
public interface ReviewManager {

    Task<ReviewInfo> requestReviewFlow();

    Task<Void> launchReviewFlow(Activity activity, ReviewInfo reviewInfo);
}
