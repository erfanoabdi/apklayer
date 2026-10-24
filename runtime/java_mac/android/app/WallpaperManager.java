// android.app.WallpaperManager
//
// AOSP's wallpaper service. Apps fetch it via getSystemService(WALLPAPER_SERVICE)
// to read the current wallpaper or its desired dimensions. Mac.apk has no
// wallpaper SURFACE, so the drawable getters return null and dimension queries
// report 0 -- the honest "no wallpaper set" result rather than a null service
// that NPEs. The wallpaper's COLOURS are the exception (getWallpaperColors):
// since 2026-09-26 they come from the Mac desktop picture, the same colours the
// Material You system palette is seeded from (com.macapk.loader.monet).
// Mirrors frameworks/base/core/java/android/app/WallpaperManager.java.
package android.app;

import android.content.Context;
import android.graphics.drawable.Drawable;

public class WallpaperManager {

    public static final String COMMAND_TAP = "android.wallpaper.tap";
    public static final String COMMAND_SECONDARY_TAP = "android.wallpaper.secondaryTap";
    public static final int FLAG_SYSTEM = 0x01;
    public static final int FLAG_LOCK   = 0x02;

    private final Context mContext;

    public WallpaperManager() { mContext = null; }
    public WallpaperManager(Context context) { mContext = context; }

    /** AOSP getInstance(Context): the per-context WallpaperManager. */
    public static WallpaperManager getInstance(Context context) {
        return new WallpaperManager(context);
    }

    public Drawable getDrawable() { return null; }
    public Drawable peekDrawable() { return null; }
    public Drawable getFastDrawable() { return null; }
    public Drawable peekFastDrawable() { return null; }
    public android.graphics.Bitmap getBitmap() { return null; }

    /** AOSP WallpaperManager.setBitmap(Bitmap) (frameworks/base/core/java/
     *  android/app/WallpaperManager.java, android11-release:1317-1319),
     *  fetched 2026-08-12:
     *      @RequiresPermission(android.Manifest.permission.SET_WALLPAPER)
     *      public void setBitmap(Bitmap bitmap) throws IOException {
     *          setBitmap(bitmap, null, true);
     *      }
     *  Mirrored verbatim -- a bare delegate that discards the assigned-id
     *  return value. See the 3-arg overload below for what "no wallpaper
     *  surface" means for the actual behavior underneath it. */
    public void setBitmap(android.graphics.Bitmap bitmap) throws java.io.IOException {
        setBitmap(bitmap, null, true);
    }

    /** AOSP WallpaperManager.setBitmap(Bitmap, Rect, boolean) (same file,
     *  android11-release:1350-1353), fetched 2026-08-12:
     *      @RequiresPermission(android.Manifest.permission.SET_WALLPAPER)
     *      public int setBitmap(Bitmap fullImage, Rect visibleCropHint, boolean allowBackup)
     *              throws IOException {
     *          return setBitmap(fullImage, visibleCropHint, allowBackup, FLAG_SYSTEM | FLAG_LOCK);
     *      }
     *  which itself falls through two more overloads (:1376, :1390 -- the
     *  last @hide/@UnsupportedAppUsage) to the real implementation:
     *  validate the crop rect (validateRect, :1421 -- "must be valid and
     *  non-empty"), open a ParcelFileDescriptor from WallpaperManagerService
     *  over Binder, PNG-compress the bitmap into it, wait for completion,
     *  and return the new wallpaper's assigned id -- "or zero on failure"
     *  per AOSP's own javadoc (the int return is a documented failure
     *  channel, not just a success id).
     *
     *  Mac.apk has no wallpaper-rendering surface at all -- no home screen,
     *  no lock screen, nothing that would ever paint a system wallpaper --
     *  which is exactly why isWallpaperSupported()/isSetWallpaperAllowed()
     *  above already answer false. There is therefore no
     *  WallpaperManagerService to round-trip through and no real id to
     *  mint. We keep the one piece of the public contract a caller can
     *  observe independent of a backing service -- AOSP's validateRect:
     *  an empty, non-null crop hint is a caller bug on real Android and
     *  here alike -- and otherwise report the documented failure sentinel
     *  rather than fabricating a successful set that never actually
     *  broadcasts ACTION_WALLPAPER_CHANGED (nothing would receive it: no
     *  home screen is listening either). */
    public int setBitmap(android.graphics.Bitmap fullImage, android.graphics.Rect visibleCropHint,
            boolean allowBackup) throws java.io.IOException {
        if (visibleCropHint != null && visibleCropHint.isEmpty()) {
            throw new IllegalArgumentException("visibleCrop rectangle must be valid and non-empty");
        }
        return 0;
    }

    public int getDesiredMinimumWidth() { return 0; }
    public int getDesiredMinimumHeight() { return 0; }
    public int getWallpaperId(int which) { return -1; }
    public boolean isWallpaperSupported() { return false; }
    public boolean isSetWallpaperAllowed() { return false; }
    public void suggestDesiredDimensions(int minimumWidth, int minimumHeight) {}
    public void forgetLoadedWallpaper() {}
    public void setWallpaperOffsets(android.os.IBinder windowToken, float xOffset, float yOffset) {}
    public void setWallpaperOffsetSteps(float xStep, float yStep) {}
    public void sendWallpaperCommand(android.os.IBinder windowToken, String action,
            int x, int y, int z, android.os.Bundle extras) {}

    // ================================================================
    // AOSP API-36 surface completion.
    // AOSP ref: frameworks/base/core/java/android/app/WallpaperManager.java
    // (android16-release). The premise for every answer below is the one
    // already stated at the top of this file and by isWallpaperSupported():
    // Mac.apk has no home screen, no lock screen and no wallpaper-rendering
    // surface, so there is no wallpaper set and nothing to set one on. Each
    // method therefore gives AOSP's own answer for "no wallpaper" rather than a
    // fabricated success.
    // ================================================================

    /** AOSP ACTION_CROP_AND_SET_WALLPAPER / ACTION_LIVE_WALLPAPER_CHOOSER /
     *  ACTION_CHANGE_LIVE_WALLPAPER / EXTRA_LIVE_WALLPAPER_COMPONENT /
     *  WALLPAPER_PREVIEW_META_DATA / COMMAND_DROP. */
    public static final String ACTION_CROP_AND_SET_WALLPAPER =
            "android.service.wallpaper.CROP_AND_SET_WALLPAPER";
    public static final String ACTION_LIVE_WALLPAPER_CHOOSER =
            "android.service.wallpaper.LIVE_WALLPAPER_CHOOSER";
    public static final String ACTION_CHANGE_LIVE_WALLPAPER =
            "android.service.wallpaper.CHANGE_LIVE_WALLPAPER";
    public static final String EXTRA_LIVE_WALLPAPER_COMPONENT =
            "android.service.wallpaper.extra.LIVE_WALLPAPER_COMPONENT";
    public static final String WALLPAPER_PREVIEW_META_DATA = "android.wallpaper.preview";
    public static final String COMMAND_DROP = "android.home.drop";

    /** AOSP WallpaperManager.OnColorsChangedListener: "Callback to receive a
     *  wallpaper's colors" when they change. */
    public interface OnColorsChangedListener {
        void onColorsChanged(WallpaperColors colors, int which);
    }

    /** AOSP addOnColorsChangedListener(OnColorsChangedListener, Handler):
     *  registers for wallpaper-colour changes. No wallpaper ever changes here,
     *  so the listener registers and never fires -- the same as on a device
     *  whose wallpaper the user never touches. The list is kept so
     *  removeOnColorsChangedListener really removes, rather than the pair
     *  silently disagreeing. */
    public void addOnColorsChangedListener(OnColorsChangedListener listener,
            android.os.Handler handler) {
        if (listener != null) mColorListeners.add(listener);
    }
    /** AOSP removeOnColorsChangedListener(OnColorsChangedListener). */
    public void removeOnColorsChangedListener(OnColorsChangedListener listener) {
        mColorListeners.remove(listener);
    }
    private final java.util.List<OnColorsChangedListener> mColorListeners =
            new java.util.concurrent.CopyOnWriteArrayList<OnColorsChangedListener>();

    /** AOSP getWallpaperColors(int which): "Get the primary colors of a
     *  wallpaper... @return {@link WallpaperColors} or null if colors are
     *  unknown." AOSP validates `which` first -- it must be FLAG_SYSTEM or
     *  FLAG_LOCK -- so keep that.
     *
     *  SINCE 2026-09-26 (MACAPK_MONET_WALLPAPER_SEED, default ON) the colours are
     *  KNOWN: they are the Mac desktop picture's, extracted by AOSP's own
     *  WallpaperColors.fromBitmap maths (com.macapk.loader.monet) -- the very
     *  WallpaperColors the system palette Mac.apk serves was derived from, which
     *  is the relationship an app sees on a device (SystemUI seeds the palette
     *  from exactly what this method returns). FLAG_LOCK answers the same
     *  colours: WallpaperManagerService.getWallpaperColors falls back to the
     *  system wallpaper when no separate lock wallpaper is set, and the macOS
     *  lock screen shows the desktop picture. Null, as before, with the gate off
     *  or when the host shows no readable picture.
     *
     *  Shape: WallpaperColors(primary, secondary, tertiary, hints) with the first
     *  three main colours and fromBitmap's hints (HINT_FROM_BITMAP included, as
     *  AOSP's getColorHints() reports it). Our WallpaperColors keeps three named
     *  colours rather than AOSP's full main-colour list and population map, so
     *  getMainColors()/getAllColors() (both @hide) are not carried. */
    public WallpaperColors getWallpaperColors(int which) {
        checkExactlyOneWallpaperFlagSet(which);
        try {
            com.macapk.loader.monet.Monet.State s = com.macapk.loader.monet.Monet.state();
            if (s == null || s.mainColors.length == 0) return null;
            int[] m = s.mainColors;
            android.graphics.Color primary = android.graphics.Color.valueOf(m[0]);
            android.graphics.Color secondary = m.length > 1 ? android.graphics.Color.valueOf(m[1]) : null;
            android.graphics.Color tertiary = m.length > 2 ? android.graphics.Color.valueOf(m[2]) : null;
            return new WallpaperColors(primary, secondary, tertiary, s.hints);
        } catch (LinkageError e) {
            return null;
        }
    }

    /** AOSP's own argument check, used by every which-taking entry point:
     *  "Must specify exactly one kind of wallpaper to read". */
    private static void checkExactlyOneWallpaperFlagSet(int which) {
        if (which != FLAG_SYSTEM && which != FLAG_LOCK) {
            throw new IllegalArgumentException("Must specify exactly one kind of wallpaper");
        }
    }

    /** AOSP getWallpaperFile(int which): "Get an open, readable file descriptor
     *  to the given wallpaper image file... @return null if no such wallpaper is
     *  configured." */
    public android.os.ParcelFileDescriptor getWallpaperFile(int which) {
        checkExactlyOneWallpaperFlagSet(which);
        return null;
    }

    /** AOSP getWallpaperInfo(): "Returns the information about the wallpaper if
     *  the current wallpaper is a live wallpaper component. Otherwise, if the
     *  wallpaper is a static image, this returns null." Ours is neither, and
     *  null is the not-a-live-wallpaper answer. */
    public WallpaperInfo getWallpaperInfo() { return null; }
    /** AOSP getWallpaperInfo(int which): the per-destination form. */
    public WallpaperInfo getWallpaperInfo(int which) {
        checkExactlyOneWallpaperFlagSet(which);
        return null;
    }

    /** AOSP getDrawable(int which) / getFastDrawable(int) / peekDrawable(int) /
     *  peekFastDrawable(int): the which-taking forms of the four already above.
     *  AOSP returns null when no wallpaper is set (and peek* additionally return
     *  null rather than falling back to the built-in default). */
    public Drawable getDrawable(int which) {
        checkExactlyOneWallpaperFlagSet(which);
        return null;
    }
    public Drawable getFastDrawable(int which) {
        checkExactlyOneWallpaperFlagSet(which);
        return null;
    }
    public Drawable peekDrawable(int which) {
        checkExactlyOneWallpaperFlagSet(which);
        return null;
    }
    public Drawable peekFastDrawable(int which) {
        checkExactlyOneWallpaperFlagSet(which);
        return null;
    }

    /** AOSP getBuiltInDrawable() and its three overloads: "Returns a drawable
     *  for the system built-in static wallpaper", and AOSP itself returns null
     *  when the system has no built-in wallpaper resource. macOS ships no
     *  Android system wallpaper, so null is the answer for all four. */
    public Drawable getBuiltInDrawable() { return null; }
    public Drawable getBuiltInDrawable(int which) { return null; }
    public Drawable getBuiltInDrawable(int outWidth, int outHeight, boolean scaleToFit,
            float horizontalAlignment, float verticalAlignment) {
        return null;
    }
    public Drawable getBuiltInDrawable(int outWidth, int outHeight, boolean scaleToFit,
            float horizontalAlignment, float verticalAlignment, int which) {
        return null;
    }

    /** AOSP hasResourceWallpaper(int resid): "Return whether any users are
     *  currently set to use the wallpaper with the given resource ID." Nobody
     *  is: no wallpaper is set. */
    public boolean hasResourceWallpaper(int resid) { return false; }

    /** AOSP getCropAndSetWallpaperIntent(Uri): builds an
     *  ACTION_CROP_AND_SET_WALLPAPER intent aimed at whichever installed
     *  activity can crop-and-set, and -- when NONE can -- ends with
     *  {@code throw new IllegalArgumentException("Cannot use passed URI to set
     *  wallpaper; check that the type returned by ContentProvider matches
     *  image/*")}. Mac.apk installs one app and no wallpaper cropper, so we
     *  reach that final throw. The two argument checks that precede it are
     *  reproduced verbatim because they are the ones a caller is most likely to
     *  trip. */
    public android.content.Intent getCropAndSetWallpaperIntent(android.net.Uri imageUri) {
        if (imageUri == null) {
            throw new IllegalArgumentException("Image URI must not be null");
        }
        if (!"content".equals(imageUri.getScheme())) {
            throw new IllegalArgumentException(
                    "Image URI must be of the content scheme type");
        }
        throw new IllegalArgumentException("Cannot use passed URI to set wallpaper; "
                + "check that the type returned by ContentProvider matches image/*");
    }

    // ---- Setters ----
    // Every one of these ends, on AOSP, in a Binder round trip to
    // WallpaperManagerService that returns the new wallpaper id "or zero on
    // failure". There is no such service here (see setBitmap(Bitmap, Rect,
    // boolean) above for the full reasoning), so they report the documented
    // failure sentinel rather than claiming a wallpaper was set.

    /** AOSP setBitmap(Bitmap, Rect, boolean, int which): the destination-taking
     *  form; the 3-arg version above is {@code setBitmap(b, crop, backup,
     *  FLAG_SYSTEM | FLAG_LOCK)}. Fossify Gallery calls this one. */
    public int setBitmap(android.graphics.Bitmap fullImage, android.graphics.Rect visibleCropHint,
            boolean allowBackup, int which) throws java.io.IOException {
        if (visibleCropHint != null && visibleCropHint.isEmpty()) {
            throw new IllegalArgumentException("visibleCrop rectangle must be valid and non-empty");
        }
        return 0;
    }

    /** AOSP setResource(int resid): {@code setResource(resid, FLAG_SYSTEM |
     *  FLAG_LOCK);} with the id discarded. */
    public void setResource(int resid) throws java.io.IOException {
        setResource(resid, FLAG_SYSTEM | FLAG_LOCK);
    }
    /** AOSP setResource(int resid, int which). */
    public int setResource(int resid, int which) throws java.io.IOException { return 0; }

    /** AOSP setStream(InputStream): {@code setStream(bitmapData, null, true);}
     *  with the id discarded. */
    public void setStream(java.io.InputStream bitmapData) throws java.io.IOException {
        setStream(bitmapData, null, true);
    }
    /** AOSP setStream(InputStream, Rect, boolean): {@code setStream(data, crop,
     *  backup, FLAG_SYSTEM | FLAG_LOCK);}. */
    public int setStream(java.io.InputStream bitmapData, android.graphics.Rect visibleCropHint,
            boolean allowBackup) throws java.io.IOException {
        return setStream(bitmapData, visibleCropHint, allowBackup, FLAG_SYSTEM | FLAG_LOCK);
    }
    /** AOSP setStream(InputStream, Rect, boolean, int): same crop validation as
     *  setBitmap. */
    public int setStream(java.io.InputStream bitmapData, android.graphics.Rect visibleCropHint,
            boolean allowBackup, int which) throws java.io.IOException {
        if (visibleCropHint != null && visibleCropHint.isEmpty()) {
            throw new IllegalArgumentException("visibleCrop rectangle must be valid and non-empty");
        }
        return 0;
    }

    /** AOSP clear(): {@code setStream(openDefaultWallpaper(...), null, false);}
     *  -- "Remove any currently set system wallpaper, reverting to the system's
     *  built-in wallpaper." Nothing is set, so there is nothing to remove and
     *  the post-condition ("no user wallpaper") already holds. */
    public void clear() throws java.io.IOException { }
    /** AOSP clear(int which): the destination-taking form. AOSP validates that
     *  `which` names at least one real destination. */
    public void clear(int which) throws java.io.IOException {
        if ((which & (FLAG_SYSTEM | FLAG_LOCK)) == 0) {
            throw new IllegalArgumentException("Must specify a valid wallpaper category to clear");
        }
    }
    /** AOSP clearWallpaper(): the older, exception-free spelling of clear().
     *  AOSP swallows the RemoteException, so it never throws. */
    public void clearWallpaper() { }

    /** AOSP clearWallpaperOffsets(IBinder windowToken): "Clear the offsets
     *  previously associated with this window through
     *  setWallpaperOffsets(IBinder, float, float)". setWallpaperOffsets above
     *  is already a sink (no wallpaper surface to offset), so there is nothing
     *  stored to clear. */
    public void clearWallpaperOffsets(android.os.IBinder windowToken) { }

    /** AOSP setDisplayPadding(Rect padding): "Specify extra padding that the
     *  wallpaper should have outside of the display." AOSP's service-side check
     *  rejects negative padding with IllegalArgumentException("padding must be
     *  positive"); keep that, since it is the one thing a caller can observe
     *  without a wallpaper surface. */
    public void setDisplayPadding(android.graphics.Rect padding) {
        if (padding == null) throw new IllegalArgumentException("padding must not be null");
        if (padding.left < 0 || padding.top < 0 || padding.right < 0 || padding.bottom < 0) {
            throw new IllegalArgumentException("padding must be positive: " + padding);
        }
    }
}
