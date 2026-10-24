package android.content.pm;

/** android.content.pm.CrossProfileApps (API 28) -- the system service an app
 *  uses to launch its own components in ANOTHER profile of the same user
 *  (personal &lt;-&gt; work). Obtained via
 *  Context.getSystemService(CROSS_PROFILE_APPS_SERVICE).
 *
 *  Mirrors frameworks/base/core/java/android/content/pm/CrossProfileApps.java
 *  (android16-release).
 *
 *  ⚠ MAC.APK HAS EXACTLY ONE PROFILE. There is no work profile, no private
 *  space, no secondary user -- UserHandle.SYSTEM is the only one that exists.
 *  So getTargetUserProfiles() is empty and every "can I interact across
 *  profiles" question answers false. That is not a stub: it is precisely what a
 *  single-profile Android device reports, and it is the state the vast majority
 *  of real devices are in. Every AOSP caller already handles it, because on a
 *  personal-only phone it is the only answer they ever get.
 *
 *  The startActivity/startMainActivity family therefore always throws
 *  SecurityException, which is AOSP's own documented behaviour for a target
 *  user that is not in getTargetUserProfiles() ("@throws SecurityException if
 *  target user is not a profile of caller"). Silently starting the activity in
 *  OUR profile instead would be the comfortable lie: the caller asked to cross
 *  a boundary that does not exist here, and pretending it crossed would put the
 *  activity somewhere the caller did not ask for. */
public class CrossProfileApps {
    /** AOSP CrossProfileApps.ACTION_CAN_INTERACT_ACROSS_PROFILES_CHANGED --
     *  broadcast when the app's cross-profile permission changes. Value
     *  verbatim; nothing ever sends it here because the answer never changes. */
    public static final String ACTION_CAN_INTERACT_ACROSS_PROFILES_CHANGED =
            "android.content.pm.action.CAN_INTERACT_ACROSS_PROFILES_CHANGED";

    public CrossProfileApps() {}
    public CrossProfileApps(android.content.Context context) {}

    /** AOSP: "the list of user profiles that the calling app can use
     *  CrossProfileApps with", @NonNull. Empty here -- see the class note. */
    public java.util.List<android.os.UserHandle> getTargetUserProfiles() {
        return java.util.Collections.emptyList();
    }

    /** AOSP: "whether the calling package can request to interact across
     *  profiles." False: there is no other profile to request access to. */
    public boolean canRequestInteractAcrossProfiles() { return false; }

    /** AOSP: "whether the calling package can interact across profiles." */
    public boolean canInteractAcrossProfiles() { return false; }

    /** AOSP: the Intent that opens Settings' cross-profile consent screen, or
     *  null when canRequestInteractAcrossProfiles() is false -- which is
     *  exactly the null branch AOSP documents ("@return null if the calling
     *  package cannot request to interact across profiles"). */
    public android.content.Intent createRequestInteractAcrossProfilesIntent() {
        return null;
    }

    /** AOSP: "whether the given user is a profile" -- i.e. a managed/cloned
     *  profile rather than a full user. Our only user is the full system
     *  user. */
    public boolean isProfile(android.os.UserHandle userHandle) { return false; }

    /** AOSP: "whether the given user is a managed profile." */
    public boolean isManagedProfile(android.os.UserHandle userHandle) { return false; }

    /** AOSP: the label of the "switch to work" button for the target profile.
     *  AOSP throws SecurityException when the target is not a profile of the
     *  caller; here nothing is, so it always throws. */
    public CharSequence getProfileSwitchingLabel(android.os.UserHandle userHandle) {
        throw notAProfile(userHandle);
    }

    /** AOSP: the icon of the "switch to work" button. Same contract. */
    public android.graphics.drawable.Drawable getProfileSwitchingIconDrawable(
            android.os.UserHandle userHandle) {
        throw notAProfile(userHandle);
    }

    public void startMainActivity(android.content.ComponentName component,
            android.os.UserHandle targetUser) {
        throw notAProfile(targetUser);
    }

    public void startMainActivity(android.content.ComponentName component,
            android.os.UserHandle targetUser, android.app.Activity callingActivity,
            android.os.Bundle options) {
        throw notAProfile(targetUser);
    }

    public void startActivity(android.content.Intent intent,
            android.os.UserHandle targetUser, android.app.Activity callingActivity) {
        throw notAProfile(targetUser);
    }

    public void startActivity(android.content.Intent intent,
            android.os.UserHandle targetUser, android.app.Activity callingActivity,
            android.os.Bundle options) {
        throw notAProfile(targetUser);
    }

    /** AOSP's own message shape for this failure
     *  (CrossProfileApps.verifyCanAccessUser: "Not allowed to access " + user). */
    private static SecurityException notAProfile(android.os.UserHandle user) {
        return new SecurityException("Not allowed to access " + user);
    }
}
