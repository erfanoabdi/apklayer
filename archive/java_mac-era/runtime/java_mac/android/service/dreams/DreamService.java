package android.service.dreams;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.IBinder;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SearchEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

/**
 * Base for a screensaver ("dream"). API 17.
 *
 * WHAT MAC.APK CAN AND CANNOT DO HERE. A dream is started by the system when
 * the device docks or idles. Mac.apk runs one app in one window and has no
 * idle/dock notion, so a declared dream is never started and never attached to
 * a window. What must work is everything the CLASS promises: the subclass has
 * to load, its lifecycle hooks have to be callable in a coherent order, and --
 * because AOSP declares DreamService `implements Window.Callback` -- every
 * Window.Callback method has to be present with the right descriptor. Omitting
 * the interface is what apidiff flagged: a dream that hands `this` to something
 * expecting a Window.Callback got a ClassCastException, and a dream that
 * declared @Override on dispatchKeyEvent did not compile against us.
 *
 * WHAT IS FAITHFUL RATHER THAN STUBBED. AOSP's Window.Callback bodies have a
 * real, observable rule that does not need a window: a NON-INTERACTIVE dream
 * wakes up on any input and consumes the event; an interactive one forwards to
 * the window. We implement exactly that. With no window attached the forward
 * has nothing to hand the event to and returns false, which is also what
 * Window.superDispatch* returns for a window with no content.
 *
 * getWindow() stays null, because that is what AOSP reports before
 * onAttachedToWindow and we never attach. The content-view calls therefore
 * cannot delegate to a window -- but they do NOT throw either: AOSP would NPE
 * on getWindow().setContentView() pre-attach, and since a dream here is ALWAYS
 * pre-attach that would abort every dream's onCreate. We keep the view instead,
 * so findViewById()/requireViewById() search the tree the dream built and give
 * the AOSP answer for the part we can honour.
 *
 * Source: frameworks/base/core/java/android/service/dreams/DreamService.java
 * Constants transcribed verbatim from /tmp/aosp_api/android16.txt:
 *   DREAM_META_DATA = "android.service.dream"
 *   SERVICE_INTERFACE = "android.service.dreams.DreamService"
 */
public class DreamService extends android.app.Service implements Window.Callback {

    public static final String SERVICE_INTERFACE = "android.service.dreams.DreamService";
    /** The <meta-data> name pointing at the dream's settings/preview XML. */
    public static final String DREAM_META_DATA = "android.service.dream";

    // AOSP defaults: frameworks/base/.../DreamService.java:261-263. mScreenBright
    // starts TRUE, the other two FALSE -- a dream that never calls the setters
    // must read those values back.
    private boolean mInteractive;
    private boolean mFullscreen;
    private boolean mScreenBright = true;
    private boolean mFinished;
    private View mContentView;

    public DreamService() { }

    /** AOSP declares this final; android.app.Service.onBind already returns
     *  null in this tree, so nothing the superclass assigns is lost. */
    @Override public final IBinder onBind(Intent intent) { return null; }

    // ---- dream lifecycle ----------------------------------------------------
    public void onDreamingStarted() { }

    public void onDreamingStopped() { }

    /** API 33. AOSP's body tears the dream down; the subclass usually calls
     *  super. Do the same so an override that calls super still finishes. */
    public void onWakeUp() { finish(); }

    /** AOSP: posts to the dream's handler, then stops dreaming. There is no
     *  dream to stop here, so we only record that finish happened -- calling
     *  it twice must stay idempotent, which is what AOSP's mFinished guard
     *  gives. */
    public final void finish() { mFinished = true; }

    /** API 33. AOSP routes through onWakeUp() so an override runs. */
    public final void wakeUp() { onWakeUp(); }

    // ---- window / content ---------------------------------------------------
    /** Null until the dream is attached to a window, exactly as AOSP. Mac.apk
     *  never attaches one. */
    public Window getWindow() { return null; }

    /** API 33. AOSP returns the window's WindowManager; null before attach. */
    public WindowManager getWindowManager() {
        Window w = getWindow();
        return w == null ? null : w.getWindowManager();
    }

    /** No window to inflate into. AOSP would NPE pre-attach; a dream here is
     *  always pre-attach, so throwing would abort every dream's onCreate. */
    public void setContentView(int layoutResID) { mContentView = null; }

    public void setContentView(View view) { mContentView = view; }

    public void setContentView(View view, ViewGroup.LayoutParams params) {
        mContentView = view;
    }

    /** AOSP adds the view alongside existing content. With no decor to add to,
     *  the first added view becomes the searchable root. */
    public void addContentView(View view, ViewGroup.LayoutParams params) {
        if (mContentView == null) mContentView = view;
    }

    /** Searches whatever the dream actually set, so a dream that built its own
     *  hierarchy in onAttachedToWindow gets the real answer. Null when nothing
     *  was set, which is AOSP's answer for an id that is not present. */
    public <T extends View> T findViewById(int id) {
        if (mContentView == null) return null;
        return mContentView.findViewById(id);
    }

    /** API 28. AOSP throws IllegalArgumentException when the id is not found;
     *  mirror the exception, it is the whole point of the method. */
    public final <T extends View> T requireViewById(int id) {
        T view = findViewById(id);
        if (view == null) {
            throw new IllegalArgumentException(
                    "ID does not reference a View inside this DreamService");
        }
        return view;
    }

    // ---- dream attributes ---------------------------------------------------
    // These ASSIGN state and the getters read it back: the round-trip is the
    // only half of the contract observable without a system to honour it, and
    // discarding the argument would make setInteractive/isInteractive disagree.
    public void setInteractive(boolean interactive) { mInteractive = interactive; }

    public boolean isInteractive() { return mInteractive; }

    public void setFullscreen(boolean fullscreen) { mFullscreen = fullscreen; }

    public boolean isFullscreen() { return mFullscreen; }

    public void setScreenBright(boolean screenBright) { mScreenBright = screenBright; }

    public boolean isScreenBright() { return mScreenBright; }

    // ---- Window.Callback ----------------------------------------------------
    // AOSP's rule, reproduced: a non-interactive dream wakes on ANY input and
    // consumes the event; an interactive one forwards to the window (which,
    // unattached, handles nothing). frameworks/base/.../DreamService.java:549-604.
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (!mInteractive) { wakeUp(); return true; }
        // AOSP also wakes an interactive dream on BACK.
        if (event != null && event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            wakeUp();
            return true;
        }
        return false;
    }

    @Override public boolean dispatchKeyShortcutEvent(KeyEvent event) {
        if (!mInteractive) { wakeUp(); return true; }
        return false;
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (!mInteractive && event != null
                && event.getActionMasked() == MotionEvent.ACTION_UP) {
            wakeUp();
            return true;
        }
        return false;
    }

    @Override public boolean dispatchTrackballEvent(MotionEvent event) {
        if (!mInteractive) { wakeUp(); return true; }
        return false;
    }

    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (!mInteractive) { wakeUp(); return true; }
        return false;
    }

    @Override public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        return false;
    }

    // The panel/menu half of Window.Callback. A dream has no options menu at
    // all in AOSP -- these bodies are empty/false THERE too, not just here.
    @Override public View onCreatePanelView(int featureId) { return null; }

    @Override public boolean onCreatePanelMenu(int featureId, Menu menu) { return false; }

    @Override public boolean onPreparePanel(int featureId, View view, Menu menu) {
        return false;
    }

    @Override public boolean onMenuOpened(int featureId, Menu menu) { return false; }

    @Override public boolean onMenuItemSelected(int featureId, MenuItem item) {
        return false;
    }

    @Override public void onPanelClosed(int featureId, Menu menu) { }

    @Override public boolean onSearchRequested() { return false; }

    @Override public boolean onSearchRequested(SearchEvent searchEvent) {
        return onSearchRequested();
    }

    @Override public ActionMode onWindowStartingActionMode(ActionMode.Callback callback) {
        return null;
    }

    @Override public ActionMode onWindowStartingActionMode(ActionMode.Callback callback,
            int type) {
        return null;
    }

    @Override public void onActionModeStarted(ActionMode mode) { }

    @Override public void onActionModeFinished(ActionMode mode) { }

    @Override public void onWindowAttributesChanged(WindowManager.LayoutParams attrs) { }

    @Override public void onContentChanged() { }

    @Override public void onWindowFocusChanged(boolean hasFocus) { }

    @Override public void onAttachedToWindow() { }

    @Override public void onDetachedFromWindow() { }

    // ---- ComponentCallbacks -------------------------------------------------
    /** android.app.Service implements ComponentCallbacks2; its own body is
     *  empty, so this override loses nothing. Declared because AOSP's
     *  DreamService declares it and subclasses @Override it. */
    @Override public void onConfigurationChanged(Configuration newConfig) { }
}
