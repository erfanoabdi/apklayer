// android.app.WallpaperInfo -- "This class is used to specify meta information of
// a wallpaper service", parsed from a live wallpaper's
// <wallpaper> meta-data. Returned by WallpaperManager.getWallpaperInfo(), which
// answers null unless a LIVE wallpaper is currently set.
//
// AOSP ref: frameworks/base/core/java/android/app/WallpaperInfo.java
// (android16-release). AOSP's constructor parses the service's
// android.service.wallpaper meta-data XML and THROWS XmlPullParserException when
// the meta-data is absent or malformed -- that is the behaviour a caller handles,
// and it is the honest one here: Mac.apk hosts no WallpaperService, so no
// ResolveInfo handed to this constructor can carry the meta-data AOSP requires.
//
// The class exists (rather than being omitted) because WallpaperManager's
// getWallpaperInfo() signature names it: without the type, that method cannot be
// declared at all and an app referencing it fails to link.
package android.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Printer;

public final class WallpaperInfo implements Parcelable {

    private final ServiceInfo mService;

    /** AOSP's public constructor: parses the service's
     *  "android.service.wallpaper" meta-data and throws
     *  XmlPullParserException("No android.service.wallpaper meta-data") when it
     *  is missing. Mac.apk has no WallpaperService implementations to describe,
     *  so that is always the case -- raising AOSP's own exception is the honest
     *  answer, and it is a CHECKED exception the caller already handles. */
    public WallpaperInfo(Context context, ResolveInfo service)
            throws java.io.IOException, org.xmlpull.v1.XmlPullParserException {
        mService = service != null ? service.serviceInfo : null;
        throw new org.xmlpull.v1.XmlPullParserException(
                "No android.service.wallpaper meta-data");
    }

    private WallpaperInfo(Parcel source) {
        mService = null;
    }

    /** AOSP getServiceName(): "Return the .apk package that implements this
     *  wallpaper." (AOSP's javadoc says package; the value is the service's
     *  class name.) */
    public String getServiceName() { return mService != null ? mService.name : null; }
    /** AOSP getPackageName(): the package implementing this wallpaper. */
    public String getPackageName() { return mService != null ? mService.packageName : null; }
    /** AOSP getServiceInfo(): "Return the raw information about the Service
     *  implementing this wallpaper." */
    public ServiceInfo getServiceInfo() { return mService; }
    /** AOSP getComponent(): the ComponentName of the wallpaper service. */
    public ComponentName getComponent() {
        return mService != null ? new ComponentName(mService.packageName, mService.name) : null;
    }

    public CharSequence loadLabel(PackageManager pm) { return null; }
    public Drawable loadIcon(PackageManager pm) { return null; }
    public Drawable loadThumbnail(PackageManager pm) { return null; }
    public CharSequence loadAuthor(PackageManager pm) throws Resources.NotFoundException {
        return null;
    }
    public CharSequence loadDescription(PackageManager pm) throws Resources.NotFoundException {
        return null;
    }
    public CharSequence loadContextDescription(PackageManager pm)
            throws Resources.NotFoundException {
        return null;
    }
    public Uri loadContextUri(PackageManager pm) throws Resources.NotFoundException {
        return null;
    }

    /** AOSP getSettingsActivity(): "the activity to use for settings for this
     *  wallpaper", null when the wallpaper declares none. */
    public String getSettingsActivity() { return null; }
    /** AOSP getSettingsSliceUri(): the Slice uri for inline settings, null when
     *  the wallpaper declares none. */
    public Uri getSettingsSliceUri() { return null; }
    /** AOSP getShowMetadataInPreview(): defaults to false when unspecified. */
    public boolean getShowMetadataInPreview() { return false; }
    /** AOSP shouldUseDefaultUnfoldTransition(): AOSP's default is TRUE
     *  ("android:shouldUseDefaultUnfoldTransition" defaults to true). */
    public boolean shouldUseDefaultUnfoldTransition() { return true; }
    /** AOSP supportsMultipleDisplays(): defaults to false when unspecified. */
    public boolean supportsMultipleDisplays() { return false; }

    public void dump(Printer pw, String prefix) {
        if (pw != null) pw.println(prefix + "Service:" + getServiceName());
    }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) { }

    public static final Parcelable.Creator<WallpaperInfo> CREATOR =
            new Parcelable.Creator<WallpaperInfo>() {
        @Override public WallpaperInfo createFromParcel(Parcel source) {
            return new WallpaperInfo(source);
        }
        @Override public WallpaperInfo[] newArray(int size) { return new WallpaperInfo[size]; }
    };
}
