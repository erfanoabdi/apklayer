// android.app.ActivityOptions -- options for how an Activity launch is
// animated/placed. On real Android these drive window transitions (scale-up,
// scene/shared-element, clip-reveal). Mac.apk has a single fixed window with
// no activity transition animations, so every factory returns a no-op options
// object and toBundle() returns an empty Bundle. The methods MUST exist,
// though: apps build options before startActivity(intent, options.toBundle())
// and an absent factory throws NoSuchMethodError mid-click, dropping the
// navigation (e.g. F-Droid's app-list row opening AppDetails via
// makeSceneTransitionAnimation). Signatures mirror AOSP frameworks/base
// core/java/android/app/ActivityOptions.java.
package android.app;

import android.content.Context;
import android.os.Bundle;
import android.view.View;

public class ActivityOptions {
    // AOSP Display.INVALID_DISPLAY (-1); android.view.Display in our tree
    // has no multi-display support and doesn't declare the constant, so
    // the literal is inlined per the verified AOSP source value.
    private int mLaunchDisplayId = -1;

    public ActivityOptions() {}

    public static ActivityOptions makeBasic() { return new ActivityOptions(); }

    // AOSP: launch the new task behind the current one, affiliated with
    // the launching activity's task. We have one fixed window and no task
    // stack, so this is the same no-op options object as every other
    // factory here -- the launch still happens, only the "behind" framing
    // (which requires a real task/window stack) is unobservable.
    public static ActivityOptions makeTaskLaunchBehind() { return new ActivityOptions(); }

    public static ActivityOptions makeCustomAnimation(Context context,
            int enterResId, int exitResId) {
        return new ActivityOptions();
    }

    public static ActivityOptions makeScaleUpAnimation(View source,
            int startX, int startY, int width, int height) {
        return new ActivityOptions();
    }

    public static ActivityOptions makeClipRevealAnimation(View source,
            int startX, int startY, int width, int height) {
        return new ActivityOptions();
    }

    public static ActivityOptions makeThumbnailScaleUpAnimation(View source,
            android.graphics.Bitmap thumbnail, int startX, int startY) {
        return new ActivityOptions();
    }

    // Shared-element (scene) transitions. The varargs Pair<View,String>...
    // form erases to (Activity, android.util.Pair[]) -- the signature F-Droid
    // invokes. Both forms just return a no-op options object; the launch still
    // happens, only the cross-activity animation is skipped.
    public static ActivityOptions makeSceneTransitionAnimation(Activity activity,
            View sharedElement, String sharedElementName) {
        return new ActivityOptions();
    }

    @SafeVarargs
    public static ActivityOptions makeSceneTransitionAnimation(Activity activity,
            android.util.Pair<View, String>... sharedElements) {
        return new ActivityOptions();
    }

    public Bundle toBundle() { return new Bundle(); }

    public void update(ActivityOptions otherOptions) {}

    public android.graphics.Rect getLaunchBounds() { return null; }

    public ActivityOptions setLaunchBounds(android.graphics.Rect screenSpacePixelRect) {
        return this;
    }

    // API 31+ (S). Whether the launched activity's identity (launching
    // package/uid) is shared with it via getLaunchedFromPackage/Uid. No
    // cross-activity identity channel exists in our single-window model,
    // so this is a pure no-op fluent setter (matches this file's existing
    // setLaunchBounds precedent, which also doesn't round-trip its value).
    public ActivityOptions setShareIdentityEnabled(boolean shareIdentity) {
        mShareIdentity = shareIdentity;
        return this;
    }

    // API 26+ (O). Paired get/set for which display to launch on; real
    // storage since both halves of the pair are added together here and
    // AOSP itself round-trips this field (ActivityOptions.java mLaunchDisplayId).
    public int getLaunchDisplayId() {
        return mLaunchDisplayId;
    }
    public ActivityOptions setLaunchDisplayId(int launchDisplayId) {
        mLaunchDisplayId = launchDisplayId;
        return this;
    }

    // API 35+ (V, FLAG_TOUCH_PASS_THROUGH_OPT_IN). Opts the source
    // activity in to letting touches on the overlaying to-be-launched
    // activity's window pass through to it. We have one activity visible
    // at a time (no overlay compositing), so there is nothing to opt in.
    public void setAllowPassThroughOnTouchOutside(boolean allowed) {
        mAllowPassThroughOnTouchOutside = allowed;
    }

    // API 21+ (L). Asks the system to time-track the user's session and
    // report back via the given PendingIntent. We have no cross-task usage
    // tracker to attach a report to, so the request is accepted and dropped
    // (the caller's flow keeps working; the report simply never fires).
    public void requestUsageTimeReport(PendingIntent receiver) {}

    // API 31+ (S). Sets how much background-activity-start privilege a
    // PendingIntent's sender grants its creator. No background-activity
    // restriction engine exists to consult this, so it's accepted and
    // dropped like the other launch-configuration knobs on this class.
    public ActivityOptions setPendingIntentBackgroundActivityStartMode(int state) {
        mPendingIntentBalStartMode = state;
        return this;
    }

    // API 33 (Deprecated in favor of the int-mode form above, which
    // supersedes it), still real corpus demand from apps built before S.
    @Deprecated
    public void setPendingIntentBackgroundActivityLaunchAllowed(boolean allowed) {
        mPendingIntentBalStartMode = allowed ? MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                                            : MODE_BACKGROUND_ACTIVITY_START_DENIED;
    }

    // ================================================================
    // AOSP API-36 surface completion.
    // AOSP ref: frameworks/base/core/java/android/app/ActivityOptions.java
    // (android16-release). ActivityOptions is a VALUE OBJECT: the system reads
    // the fields when it performs the launch, and the app reads them back to
    // check what it configured. Mac.apk's single fixed window cannot honour the
    // launch-time EFFECT of most of them (see the class comment), but the
    // round-trip is a separate contract from the effect, and AOSP's own getters
    // read the stored field -- so the setters below really store. Several of
    // these getters did not exist at all, which meant a setter here was a
    // one-way drop: an app could not even discover its own configuration.
    // ================================================================

    /** AOSP MODE_BACKGROUND_ACTIVITY_START_*: how much background-activity-start
     *  privilege a PendingIntent grants. */
    public static final int MODE_BACKGROUND_ACTIVITY_START_COMPAT = -1;
    public static final int MODE_BACKGROUND_ACTIVITY_START_SYSTEM_DEFINED = 0;
    public static final int MODE_BACKGROUND_ACTIVITY_START_ALLOWED = 1;
    public static final int MODE_BACKGROUND_ACTIVITY_START_DENIED = 2;
    public static final int MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS = 3;
    public static final int MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE = 4;

    private boolean mLockTaskMode;
    private boolean mShareIdentity;
    private boolean mAllowPassThroughOnTouchOutside;
    private int mSplashScreenStyle;
    private int mPendingIntentBalStartMode = MODE_BACKGROUND_ACTIVITY_START_SYSTEM_DEFINED;
    private int mPendingIntentCreatorBalStartMode = MODE_BACKGROUND_ACTIVITY_START_SYSTEM_DEFINED;
    private Bundle mAppVerificationBundle;

    /** AOSP makeCustomAnimation(Context, int, int, int backgroundColor): the
     *  3-arg form above plus a colour painted behind the transition. Same no-op
     *  options object: there is no cross-activity animation to colour. */
    public static ActivityOptions makeCustomAnimation(Context context,
            int enterResId, int exitResId, int backgroundColor) {
        return new ActivityOptions();
    }

    /** AOSP makeLaunchIntoPip(PictureInPictureParams): "Creates an ActivityOptions
     *  instance that launches the activity into picture-in-picture upon its
     *  creation." AOSP re-derives the params with setIsLaunchIntoPip(true). We
     *  never enter PiP (Activity.enterPictureInPictureMode returns false with
     *  the reason), so the returned options launch normally -- but AOSP's
     *  argument requirement is kept, since a null here is a caller bug. */
    public static ActivityOptions makeLaunchIntoPip(
            PictureInPictureParams pictureInPictureParams) {
        if (pictureInPictureParams == null) {
            throw new NullPointerException("pictureInPictureParams cannot be null");
        }
        return new ActivityOptions();
    }

    /** AOSP setLockTaskEnabled(boolean) / getLockTaskMode(): whether the activity
     *  is launched into LockTask ("screen pinning") mode. There is no pinning on
     *  macOS -- Activity.startLockTask above says the same -- but the flag round
     *  trips. */
    public ActivityOptions setLockTaskEnabled(boolean lockTaskMode) {
        mLockTaskMode = lockTaskMode;
        return this;
    }
    public boolean getLockTaskMode() { return mLockTaskMode; }

    /** AOSP isShareIdentityEnabled(): "whether the launching app has opted-in to
     *  sharing its identity with the launched activity" -- the read-back of
     *  setShareIdentityEnabled above, which previously dropped its argument. */
    public boolean isShareIdentityEnabled() { return mShareIdentity; }

    /** AOSP isAllowPassThroughOnTouchOutside(): the read-back of
     *  setAllowPassThroughOnTouchOutside above. */
    public boolean isAllowPassThroughOnTouchOutside() {
        return mAllowPassThroughOnTouchOutside;
    }

    /** AOSP setSplashScreenStyle(int) / getSplashScreenStyle(): which splash
     *  screen the launched activity shows (SPLASH_SCREEN_STYLE_ICON /
     *  SPLASH_SCREEN_STYLE_SOLID_COLOR on android.window.SplashScreen). */
    public ActivityOptions setSplashScreenStyle(int style) {
        mSplashScreenStyle = style;
        return this;
    }
    public int getSplashScreenStyle() { return mSplashScreenStyle; }

    /** AOSP getPendingIntentBackgroundActivityStartMode(): the read-back of
     *  setPendingIntentBackgroundActivityStartMode above, which previously
     *  dropped its argument. */
    public int getPendingIntentBackgroundActivityStartMode() {
        return mPendingIntentBalStartMode;
    }

    /** AOSP setPendingIntentCreatorBackgroundActivityStartMode(int) /
     *  getPendingIntentCreatorBackgroundActivityStartMode(): the CREATOR-side
     *  counterpart of the sender-side pair. YouTube calls the setter. */
    public ActivityOptions setPendingIntentCreatorBackgroundActivityStartMode(int state) {
        mPendingIntentCreatorBalStartMode = state;
        return this;
    }
    public int getPendingIntentCreatorBackgroundActivityStartMode() {
        return mPendingIntentCreatorBalStartMode;
    }

    /** AOSP isPendingIntentBackgroundActivityLaunchAllowed(): the boolean
     *  read-back paired with the deprecated setter above. AOSP's base
     *  implementation reports true unless the mode was set to DENIED.
     *  @deprecated matches AOSP. */
    @Deprecated
    public boolean isPendingIntentBackgroundActivityLaunchAllowed() {
        return mPendingIntentBalStartMode != MODE_BACKGROUND_ACTIVITY_START_DENIED;
    }

    /** AOSP setAppVerificationBundle(Bundle): "Set the Bundle that is provided to
     *  the app installer for additional verification if the call to
     *  Context.startActivity results in an app being installed. This Bundle is
     *  not provided to any other app besides the installer." Mac.apk installs no
     *  apps from an activity launch, so nothing consumes it; stored so the
     *  object is not lost mid-chain. */
    public ActivityOptions setAppVerificationBundle(Bundle bundle) {
        mAppVerificationBundle = bundle;
        return this;
    }

    // ---- AOSP API-36 constant completion (android.app.ActivityOptions) ----
    // Values extracted verbatim from the AOSP source for this class
    // (android16-release). javac INLINES a compile-time constant into the app at
    // ITS build time, so a shipped APK cannot observe a missing one at runtime --
    // these are here for our own framework code, for anything compiled against
    // THIS jar, and so the surface is complete rather than nearly complete.
    public static final String EXTRA_USAGE_TIME_REPORT = "android.activity.usage_time";
    public static final String EXTRA_USAGE_TIME_REPORT_PACKAGES = "android.usage_time_packages";
}
