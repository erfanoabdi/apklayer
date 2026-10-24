// android.app.ComponentCaller -- "Represents the app that launched the
// component." Added in API 35 behind the
// android.security.content_uri_permission_apis flag and finalised for API 36;
// an Activity hands one out from getInitialCaller() / getCurrentCaller() /
// getCaller() so the launched component can identify who started it and
// validate any content: URI it was handed.
//
// AOSP ref: frameworks/base/core/java/android/app/ComponentCaller.java
// (android16-release). AOSP's instance is a pair of binder tokens and every
// accessor is a round trip to ActivityClient, which asks the ActivityManager
// about the caller recorded on the ActivityRecord.
//
// Mac.apk hosts exactly one app in one process: every activity here is started
// by that app itself or by our own loader, never by a second uid. So the honest
// answers are the ones a device gives when the caller IS the launched app --
// the app's own uid and package -- rather than AOSP's "identity withheld" pair
// (-1 / null), which describes a DIFFERENT app declining to share identity and
// would make an app that validates the caller reject its own intent. Note this
// is a statement about our process model, not a forged identity: there is no
// other caller to name.
package android.app;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

import java.util.Objects;

public final class ComponentCaller {

    // The component that produced this caller. Only ever the receiving
    // Activity in this tree (see the class comment); kept so getPackage() can
    // answer from the real Context rather than from a cached string.
    private final Context mContext;

    /** @hide -- our construction path; AOSP's @hide ctor takes the two binder
     *  tokens the system server uses to look the caller up. */
    public ComponentCaller(Context context) {
        mContext = context;
    }

    /** AOSP getUid(): "Returns the uid of this component caller", or -1 when
     *  the launched component is not permitted to learn it. Here the caller is
     *  the app itself, so this is the process uid -- the same number
     *  android.os.Process.myUid() reports. */
    public int getUid() {
        try {
            return android.os.Process.myUid();
        } catch (Throwable t) {
            return -1;
        }
    }

    /** AOSP getPackage(): "Returns the package name of this component caller",
     *  or null when the launched component is not permitted to learn it. Here
     *  the caller is the app itself. */
    public String getPackage() {
        try {
            return mContext != null ? mContext.getPackageName() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** AOSP checkContentUriPermission(Uri, int): "Determines whether this
     *  component caller had access to a specific content URI at launch time."
     *  AOSP asks the ActivityManager, which answers PERMISSION_GRANTED /
     *  PERMISSION_DENIED, and throws IllegalArgumentException for a non-content
     *  URI or an invalid mode -- both of those checks are the observable half,
     *  so keep them. The verdict itself: the caller is this app, and an app
     *  always has access to a content URI it produced, which is
     *  PERMISSION_GRANTED. */
    public int checkContentUriPermission(Uri uri, int modeFlags) {
        if (uri == null || !"content".equals(uri.getScheme())) {
            throw new IllegalArgumentException("Uri " + uri + " must be a content URI");
        }
        // Intent.FLAG_GRANT_READ_URI_PERMISSION (0x00000001) |
        // Intent.FLAG_GRANT_WRITE_URI_PERMISSION (0x00000002). Spelled as
        // literals because android.content.Intent in this tree does not
        // declare them yet (reported to the android.content lane).
        final int both = 0x00000001 | 0x00000002;
        if ((modeFlags & both) == 0 || (modeFlags & ~both) != 0) {
            throw new IllegalArgumentException("Mode flags must contain either "
                    + "FLAG_GRANT_READ_URI_PERMISSION or FLAG_GRANT_WRITE_URI_PERMISSION");
        }
        return PackageManager.PERMISSION_GRANTED;
    }

    /** AOSP equals: identity of the two tokens; ours is identity of the
     *  originating component. */
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ComponentCaller)) return false;
        return this.mContext == ((ComponentCaller) obj).mContext;
    }

    /** AOSP hashCode: 17/31 accumulation over the tokens. */
    @Override
    public int hashCode() {
        int result = 17;
        result = 31 * result + Objects.hashCode(mContext);
        return result;
    }
}
