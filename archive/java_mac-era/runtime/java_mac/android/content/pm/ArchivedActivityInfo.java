package android.content.pm;

/** AOSP android.content.pm.ArchivedActivityInfo (API 35 / Android 15,
 *  {@code @FlaggedApi(FLAG_ARCHIVING)}): one launcher activity of an ARCHIVED
 *  package -- an app that has been uninstalled but whose launcher entry, label
 *  and icon the system keeps so the user can restore it with one tap.
 *
 *  Source: frameworks/base core/java/android/content/pm/ArchivedActivityInfo.java,
 *  android15-release (a @DataClass with genSetters=true, hence the fluent
 *  setters returning `this`).
 *
 *  Mac.apk does not implement app archiving -- there is no macOS analogue and
 *  nothing in the runtime ever archives a package -- so no instance of this
 *  class is ever produced by the framework. It exists because
 *  ArchivedPackageInfo.getLauncherActivities() is declared to return
 *  List<ArchivedActivityInfo>, and a descriptor naming a class we do not ship
 *  is a NoClassDefFoundError waiting for the first caller that resolves it.
 *
 *  The static Bitmap helpers AOSP hangs here (drawableToBitmap, bytesFromBitmap)
 *  are the archive PARCEL's serialization path, reachable only from the system
 *  server side; they are deliberately not reproduced. */
public final class ArchivedActivityInfo {
    private CharSequence mLabel;
    private android.content.ComponentName mComponentName;
    private android.graphics.drawable.Drawable mIcon;
    private android.graphics.drawable.Drawable mMonochromeIcon;

    public ArchivedActivityInfo(CharSequence label,
                                android.content.ComponentName componentName) {
        mLabel = label;
        mComponentName = componentName;
    }

    public CharSequence getLabel() { return mLabel; }
    public android.content.ComponentName getComponentName() { return mComponentName; }
    public android.graphics.drawable.Drawable getIcon() { return mIcon; }
    public android.graphics.drawable.Drawable getMonochromeIcon() { return mMonochromeIcon; }

    public ArchivedActivityInfo setLabel(CharSequence value) { mLabel = value; return this; }
    public ArchivedActivityInfo setComponentName(android.content.ComponentName value) {
        mComponentName = value; return this;
    }
    public ArchivedActivityInfo setIcon(android.graphics.drawable.Drawable value) {
        mIcon = value; return this;
    }
    public ArchivedActivityInfo setMonochromeIcon(android.graphics.drawable.Drawable value) {
        mMonochromeIcon = value; return this;
    }
}
