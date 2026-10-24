package android.service.wallpaper;

/** Base for a live-wallpaper service.
 *
 *  ⚠ STRUCTURAL LIMIT: Mac.apk runs ONE app and has no home screen, no
 *  wallpaper picker and no WallpaperManagerService, so an APK that IS a live
 *  wallpaper is never bound, never given a surface and never displayed.
 *  Nothing here fabricates that -- onBind() returns null (as it already did),
 *  and no Engine is ever constructed by the framework.
 *
 *  What IS implemented is everything the Engine owns by itself: its visibility
 *  and preview state, the surface holder it was handed, the requested wallpaper
 *  size, the wallpaper flags, the offset/touch notification switches, and
 *  notifyColorsChanged()'s rate limit and onComputeColors() call. All of that
 *  is in-process AOSP logic, so an app that constructs its own Engine (which is
 *  legal -- the constructor is public API) and drives it sees AOSP behaviour.
 *
 *  API 7. Source: frameworks/base
 *  core/java/android/service/wallpaper/WallpaperService.java (android16-release). */
public abstract class WallpaperService extends android.app.Service {

    /** The Intent that must be declared as handled by the service. */
    public static final String SERVICE_INTERFACE = "android.service.wallpaper.WallpaperService";

    /** Name under which a WallpaperService component publishes information
     *  about itself, in a &lt;meta-data&gt; tag. */
    public static final String SERVICE_META_DATA = "android.service.wallpaper";

    // AOSP rate-limits notifyColorsChanged() to one call per second and defers
    // the rest; WallpaperService.java NOTIFY_COLORS_RATE_LIMIT_MS.
    private static final long NOTIFY_COLORS_RATE_LIMIT_MS = 1000;

    public WallpaperService() {}

    @Override public void onCreate() { super.onCreate(); }

    @Override public void onDestroy() { super.onDestroy(); }

    /** AOSP hands back an IWallpaperServiceWrapper Binder that the wallpaper
     *  system uses to create engines. There is no wallpaper system and no
     *  Binder transport here, so there is nothing to return. Final in AOSP. */
    @Override public final android.os.IBinder onBind(android.content.Intent intent) {
        return null;
    }

    /** Must be implemented to return a new instance of the wallpaper's engine. */
    public abstract Engine onCreateEngine();

    // ⚠ GAP THIS LANE CANNOT CLOSE: onCreateEngine(WallpaperDescription) needs
    // android.app.wallpaper.WallpaperDescription, which this tree does not ship
    // and which lives in a package this lane does not own. AOSP's default body
    // is `return onCreateEngine();`, so it lands the moment that Parcelable
    // exists. The same blocker applies to Engine.onApplyWallpaper(int), whose
    // return type is the same class.

    /** The actual implementation of a live wallpaper. Provides access to the
     *  surface it draws on and callbacks for lifecycle and visibility. */
    public class Engine {

        private android.view.SurfaceHolder mSurfaceHolder;
        private boolean mVisible;
        private boolean mIsPreview;
        private boolean mDestroyed;
        private boolean mOffsetNotificationsEnabled;
        private boolean mTouchEventsEnabled = true;
        private int mWhich = android.app.WallpaperManager.FLAG_SYSTEM;
        private long mLastColorInvalidation;

        public Engine() {}

        /** Called once to initialize the engine. */
        public void onCreate(android.view.SurfaceHolder surfaceHolder) {
            mSurfaceHolder = surfaceHolder;
        }

        /** Called right before the engine is going away. */
        public void onDestroy() {
            mDestroyed = true;
        }

        /** Called to inform you of the wallpaper becoming visible or hidden. */
        public void onVisibilityChanged(boolean visible) {}

        /** Called by the system when it needs the wallpaper's primary colors.
         *  AOSP's default returns null, meaning "no opinion". */
        public android.app.WallpaperColors onComputeColors() { return null; }

        public android.os.Bundle onCommand(String action, int x, int y, int z,
                android.os.Bundle extras, boolean resultRequested) { return null; }

        public void onSurfaceCreated(android.view.SurfaceHolder holder) {}

        public void onSurfaceChanged(android.view.SurfaceHolder holder, int format,
                int width, int height) {}

        public void onSurfaceDestroyed(android.view.SurfaceHolder holder) {}

        /** Called when the surface must be redrawn even though nothing about it
         *  changed (a resume from a state where its contents were discarded).
         *  AOSP's body is empty; an engine overrides it to repaint. */
        public void onSurfaceRedrawNeeded(android.view.SurfaceHolder holder) {}

        public void onOffsetsChanged(float xOffset, float yOffset, float xOffsetStep,
                float yOffsetStep, int xPixelOffset, int yPixelOffset) {}

        /** Called when the zoom level of the wallpaper changes (0 = fully
         *  zoomed out, 1 = fully zoomed in). AOSP's body is empty. */
        public void onZoomChanged(float zoom) {}

        /** Called when the system's desired wallpaper size changes. AOSP's body
         *  is empty; getDesiredMinimumWidth/Height carry the new values. */
        public void onDesiredSizeChanged(int desiredWidth, int desiredHeight) {}

        /** Called when the window insets change, if the engine asked for them.
         *  AOSP's body is empty. */
        public void onApplyWindowInsets(android.view.WindowInsets insets) {}

        /** Called when the destinations this engine is serving change (home
         *  screen, lock screen, or both). AOSP's body is empty;
         *  getWallpaperFlags() carries the new value. */
        public void onWallpaperFlagsChanged(int which) {
            mWhich = which;
        }

        public void onTouchEvent(android.view.MotionEvent event) {}

        public android.view.SurfaceHolder getSurfaceHolder() { return mSurfaceHolder; }

        public boolean isVisible() { return mVisible; }

        public boolean isPreview() { return mIsPreview; }

        /** AOSP: `return mWhich;` -- FLAG_SYSTEM, FLAG_LOCK, or both, telling
         *  the engine which wallpaper destinations it is drawing. Nothing can
         *  change it here except onWallpaperFlagsChanged(), so it stays at
         *  AOSP's initial FLAG_SYSTEM. */
        public int getWallpaperFlags() { return mWhich; }

        /** AOSP: the minimum width the system wants the wallpaper bitmap to be,
         *  which is `mIWallpaperEngine.mReqWidth` -- the value the wallpaper
         *  system requested when it created the engine. With no stored desired
         *  dimensions the system requests the DISPLAY size, and this host has
         *  exactly one display, so the display width is the honest answer.
         *  Returning 0 (the "unset" sentinel) would be worse: an engine sizing
         *  its bitmap off it would allocate nothing. */
        public int getDesiredMinimumWidth() {
            android.util.DisplayMetrics dm = displayMetrics();
            return dm != null ? dm.widthPixels : 0;
        }

        public int getDesiredMinimumHeight() {
            android.util.DisplayMetrics dm = displayMetrics();
            return dm != null ? dm.heightPixels : 0;
        }

        private android.util.DisplayMetrics displayMetrics() {
            android.content.res.Resources r = WallpaperService.this.getResources();
            return r != null ? r.getDisplayMetrics() : null;
        }

        /** AOSP: the Context bound to the DISPLAY this engine draws on, which
         *  differs from the service's Context only on a multi-display device.
         *  Mac.apk presents one display, so the service Context is that
         *  context. @Nullable in the SDK, and AOSP returns null before the
         *  engine is attached. */
        public android.content.Context getDisplayContext() {
            return WallpaperService.this;
        }

        /** Control whether the engine is told about offset changes. AOSP flips
         *  a window private flag and re-lays-out the surface; there is no
         *  window here, so the switch is recorded and onOffsetsChanged simply
         *  never fires -- which is also true when the flag is off on a device. */
        public void setOffsetNotificationsEnabled(boolean enabled) {
            mOffsetNotificationsEnabled = enabled;
        }

        /** Control whether the engine receives touch events. Same reasoning as
         *  setOffsetNotificationsEnabled: AOSP toggles FLAG_NOT_TOUCHABLE on
         *  its window. */
        public void setTouchEventsEnabled(boolean enabled) {
            mTouchEventsEnabled = enabled;
        }

        /** Notify the system that the wallpaper's colors changed.
         *
         *  AOSP does three things: ignore the call after onDestroy(),
         *  rate-limit to one notification per second, and call
         *  onComputeColors() before pushing the result at the wallpaper
         *  connection. The first two are pure in-process logic and are kept.
         *  The push has nowhere to go -- AOSP logs "Can't notify system because
         *  wallpaper connection was not established" in exactly that case and
         *  carries on -- but it still CALLS onComputeColors(), so an engine
         *  that overrides it observes the same call pattern here.
         *
         *  The deferral AOSP does with a posted Runnable is dropped: with no
         *  connection, a deferred notification would deliver nothing, and
         *  posting one would keep a Handler callback alive against a destroyed
         *  engine. */
        public void notifyColorsChanged() {
            if (mDestroyed) {
                return;
            }
            final long now = android.os.SystemClock.uptimeMillis();
            if (now - mLastColorInvalidation < NOTIFY_COLORS_RATE_LIMIT_MS) {
                return;
            }
            mLastColorInvalidation = now;
            onComputeColors();
        }

        /** AOSP dumps the engine's window/surface geometry, which does not
         *  exist here; what does exist is the engine state above, so that is
         *  what is printed. Same prefix-and-key-value shape AOSP uses, so a
         *  bug report reads the same way. */
        protected void dump(String prefix, java.io.FileDescriptor fd, java.io.PrintWriter out,
                String[] args) {
            if (out == null) return;
            out.print(prefix); out.print("mDestroyed="); out.println(mDestroyed);
            out.print(prefix); out.print("mVisible="); out.print(mVisible);
                    out.print(" mIsPreview="); out.println(mIsPreview);
            out.print(prefix); out.print("mWhich="); out.println(mWhich);
            out.print(prefix); out.print("mSurfaceHolder="); out.println(mSurfaceHolder);
            out.print(prefix); out.print("mOffsetNotificationsEnabled=");
                    out.print(mOffsetNotificationsEnabled);
                    out.print(" mTouchEventsEnabled="); out.println(mTouchEventsEnabled);
            out.print(prefix); out.print("desiredMinimum=");
                    out.print(getDesiredMinimumWidth());
                    out.print("x"); out.println(getDesiredMinimumHeight());
        }
    }
}
