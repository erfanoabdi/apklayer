// android.app.UiAutomation -- "Class for interacting with the device's UI by
// simulation user actions and introspection of the screen content", handed to a
// test by Instrumentation.getUiAutomation(). API 18.
//
// AOSP ref: frameworks/base/core/java/android/app/UiAutomation.java
// (android16-release). Every method there is a round trip over the
// UiAutomationConnection to the system server / accessibility service.
//
// Mac.apk runs no instrumentation and hosts no UiAutomationConnection: there is
// no test harness that could have obtained a connected instance in the first
// place (Instrumentation.getUiAutomation() is the only public source of one).
// So each method gives AOSP's answer for a connection that yields nothing --
// null / false / empty -- rather than fabricating screen content. Nothing is
// silently "succeeded": the boolean-returning actions report false, which is
// what AOSP returns when the connection call fails.
package android.app;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.Bitmap;
import android.os.ParcelFileDescriptor;
import android.os.UserHandle;
import android.util.SparseArray;
import android.view.InputEvent;
import android.view.Window;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

public final class UiAutomation {

    /** AOSP rotation constants for setRotation. */
    public static final int ROTATION_UNFREEZE = -2;
    public static final int ROTATION_FREEZE_CURRENT = -1;
    public static final int ROTATION_FREEZE_0 = 0;
    public static final int ROTATION_FREEZE_90 = 1;
    public static final int ROTATION_FREEZE_180 = 2;
    public static final int ROTATION_FREEZE_270 = 3;

    /** AOSP connect-flags for Instrumentation.getUiAutomation(int). */
    public static final int FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES = 0x1;
    public static final int FLAG_DONT_USE_ACCESSIBILITY = 0x2;

    /** AOSP UiAutomation.OnAccessibilityEventListener: "Listener for observing
     *  the AccessibilityEvent stream." */
    public interface OnAccessibilityEventListener {
        void onAccessibilityEvent(AccessibilityEvent event);
    }

    /** AOSP UiAutomation.AccessibilityEventFilter: "Listener for filtering
     *  accessibility events." */
    public interface AccessibilityEventFilter {
        boolean accept(AccessibilityEvent event);
    }

    UiAutomation() {}

    public Bitmap takeScreenshot() { return null; }

    /** AOSP takeScreenshot(Window) (API 31): "Takes a screenshot of the
     *  specified window... @return The screenshot bitmap on success, null
     *  otherwise." */
    public Bitmap takeScreenshot(Window window) { return null; }

    public boolean injectInputEvent(InputEvent event, boolean sync) { return false; }

    // AOSP core/java/android/app/UiAutomation.java (android13-release) :864
    // returns boolean (true on success via the automation IPC connection,
    // false on RemoteException). We previously returned void -- a descriptor
    // mismatch that throws NoSuchMethodError for any real caller. Fixed the
    // descriptor; returns false like this class's other no-op automation
    // hooks (injectInputEvent/performGlobalAction above/below), since there
    // is no real automation connection here to report success from.
    public boolean setRotation(int rotation) { return false; }
    public boolean performGlobalAction(int action) { return false; }

    // ---- Accessibility introspection ----

    /** AOSP setOnAccessibilityEventListener(OnAccessibilityEventListener): the
     *  listener is stored and fed from the accessibility event stream. No
     *  accessibility service is connected here, so no event is ever produced --
     *  the same as a device where nothing on screen fires one. */
    public void setOnAccessibilityEventListener(OnAccessibilityEventListener listener) {
        mOnAccessibilityEventListener = listener;
    }
    private OnAccessibilityEventListener mOnAccessibilityEventListener;

    /** AOSP getRootInActiveWindow(): "@return The root node info" or null.
     *  AOSP itself returns null when the connection produces no node. */
    public AccessibilityNodeInfo getRootInActiveWindow() { return null; }

    /** AOSP findFocus(int focus): "Find the view that has the specified focus
     *  type", null when there is none. */
    public AccessibilityNodeInfo findFocus(int focus) { return null; }

    /** AOSP getWindows(): "@return The windows if there are windows such, otherwise
     *  an empty list." Empty, not null, so a caller iterating does not NPE. */
    public List<AccessibilityWindowInfo> getWindows() {
        return new ArrayList<AccessibilityWindowInfo>();
    }

    /** AOSP getWindowsOnAllDisplays(): a display-id -> window-list map; empty
     *  here for the same reason getWindows() is. */
    public SparseArray<List<AccessibilityWindowInfo>> getWindowsOnAllDisplays() {
        return new SparseArray<List<AccessibilityWindowInfo>>();
    }

    /** AOSP getServiceInfo(): "Gets the an AccessibilityServiceInfo describing
     *  this UiAutomation", null "if the UiAutomation is not connected". */
    public AccessibilityServiceInfo getServiceInfo() { return mServiceInfo; }
    /** AOSP setServiceInfo(AccessibilityServiceInfo): stored, then pushed to the
     *  connection. Keep the round trip so a test's get-after-set agrees. */
    public void setServiceInfo(AccessibilityServiceInfo info) { mServiceInfo = info; }
    private AccessibilityServiceInfo mServiceInfo;

    /** AOSP executeAndWaitForEvent(Runnable, AccessibilityEventFilter, long):
     *  runs the command and blocks until a matching AccessibilityEvent arrives,
     *  throwing TimeoutException if none does within timeoutMillis. No event
     *  stream exists here, so the command still runs -- that half is real and
     *  observable -- and then AOSP's documented timeout is raised rather than
     *  returning a fabricated event. TimeoutException is checked, so every
     *  caller already handles it. */
    public AccessibilityEvent executeAndWaitForEvent(Runnable command,
            AccessibilityEventFilter filter, long timeoutMillis) throws TimeoutException {
        if (command != null) command.run();
        throw new TimeoutException("Expected event not received within: "
                + timeoutMillis + " ms.");
    }

    /** AOSP waitForIdle(long idleTimeoutMillis, long globalTimeoutMillis):
     *  "Waits for the accessibility event stream to become idle". With no event
     *  stream the queue is already idle, so this returns immediately -- AOSP's
     *  behaviour when no events have been seen for idleTimeoutMillis. */
    public void waitForIdle(long idleTimeoutMillis, long globalTimeoutMillis)
            throws TimeoutException { }

    /** AOSP clearCache(): "Clears the accessibility cache. @return true if the
     *  cache was cleared successfully." There is no cache to clear, so nothing
     *  was cleared. */
    public boolean clearCache() { return false; }

    // ---- Frame stats ----
    // AOSP also declares getWindowAnimationFrameStats() /
    // getWindowContentFrameStats(int), returning android.view.WindowAnimationFrameStats
    // and android.view.WindowContentFrameStats. Neither TYPE exists in this tree
    // and android.view is not this lane's package, so the two getters cannot be
    // declared here yet -- reported to the android.view lane. The clear* halves
    // below need no such type and are present.

    /** AOSP clearWindowAnimationFrameStats(): resets the window-animation frame
     *  statistics. Nothing collects them here.
     *  @deprecated matches AOSP. */
    @Deprecated
    public void clearWindowAnimationFrameStats() { }

    /** AOSP clearWindowContentFrameStats(int windowId): "@return Whether the
     *  window is present and its frame statistics were cleared." No window is
     *  registered with an automation connection, so false. */
    public boolean clearWindowContentFrameStats(int windowId) { return false; }

    // ---- Shell identity / permissions ----
    // AOSP's implementations reach UiAutomationConnection, which is only
    // reachable from an instrumented test process holding the shell uid. Mac.apk
    // has no shell uid and no second process to adopt one from.

    /** AOSP adoptShellPermissionIdentity(): "Executes all following calls on the
     *  UI automation in a manner that they can only be executed by the shell
     *  user." There is no shell identity here to adopt, and no permission gate
     *  it would open: Mac.apk auto-grants runtime permissions already (see
     *  Activity.requestPermissions), so the post-condition the caller wants
     *  already holds. */
    public void adoptShellPermissionIdentity() { }
    /** AOSP adoptShellPermissionIdentity(String...): the subset form. */
    public void adoptShellPermissionIdentity(String... permissions) { }
    /** AOSP dropShellPermissionIdentity(): the inverse; none was adopted. */
    public void dropShellPermissionIdentity() { }

    /** AOSP grantRuntimePermission(String, String): grants a runtime permission
     *  to a package. Mac.apk hosts exactly one package and already answers
     *  PERMISSION_GRANTED for every runtime permission it is asked about, so the
     *  requested post-condition holds without a permission database to write. */
    public void grantRuntimePermission(String packageName, String permission) { }
    public void grantRuntimePermissionAsUser(String packageName, String permission,
            UserHandle userHandle) { }

    /** AOSP revokeRuntimePermission(String, String): the inverse. Mac.apk cannot
     *  make a permission check answer DENIED -- there is no permission database
     *  behind PackageManager.checkPermission to write to -- so this cannot be
     *  honoured. AOSP's own failure path swallows the RemoteException, which is
     *  the same nothing-happens outcome; a caller that wants the revocation
     *  observed would need real permission storage (reported). */
    public void revokeRuntimePermission(String packageName, String permission) { }
    public void revokeRuntimePermissionAsUser(String packageName, String permission,
            UserHandle userHandle) { }

    // ---- Shell command execution ----
    // AOSP pipes an `adb shell`-style command through the shell uid. Running an
    // arbitrary command against the HOST would not be an Android shell at all
    // (different filesystem, different binaries, different uid), so answering it
    // would be a lie about what the caller is talking to. AOSP returns a pipe;
    // ours is an EMPTY pipe -- readable, immediately at EOF -- which is what a
    // command producing no output looks like.

    /** AOSP executeShellCommand(String): "@return A file descriptor to the
     *  standard output stream." */
    public ParcelFileDescriptor executeShellCommand(String command) {
        return emptyReadPipe();
    }
    /** AOSP executeShellCommandRw(String): "@return File descriptors (out, in)"
     *  -- a two-element array. */
    public ParcelFileDescriptor[] executeShellCommandRw(String command) {
        ParcelFileDescriptor[] rw = emptyPipePair();
        return rw;
    }
    /** AOSP executeShellCommandRwe(String): "@return File descriptors (out, in,
     *  err)" -- a three-element array. */
    public ParcelFileDescriptor[] executeShellCommandRwe(String command) {
        ParcelFileDescriptor[] rw = emptyPipePair();
        return new ParcelFileDescriptor[] { rw[0], rw[1], emptyReadPipe() };
    }

    private static ParcelFileDescriptor emptyReadPipe() {
        try {
            ParcelFileDescriptor[] p = ParcelFileDescriptor.createPipe();
            // Close the write end immediately: the reader sees EOF at once,
            // which is a command that produced no output.
            p[1].close();
            return p[0];
        } catch (Throwable t) {
            return null;
        }
    }

    private static ParcelFileDescriptor[] emptyPipePair() {
        try {
            ParcelFileDescriptor[] out = ParcelFileDescriptor.createPipe();
            out[1].close();
            ParcelFileDescriptor[] in = ParcelFileDescriptor.createPipe();
            in[0].close();
            return new ParcelFileDescriptor[] { out[0], in[1] };
        } catch (Throwable t) {
            return new ParcelFileDescriptor[] { null, null };
        }
    }

    /** AOSP setRunAsMonkey(boolean): marks the process as running under the
     *  Monkey stress tool, which ActivityManager.isUserAMonkey() reports. */
    public void setRunAsMonkey(boolean enable) { sRunAsMonkey = enable; }
    /** Read by ActivityManager.isUserAMonkey(), the one public observer of the
     *  flag AOSP keeps in the activity manager. */
    static boolean sRunAsMonkey;

    /** AOSP setAnimationScale(float scale): sets the three global animator
     *  scales at once. Mac.apk runs no window transitions to scale (see
     *  Activity.overridePendingTransition), so the scale has nothing to apply
     *  to. */
    public void setAnimationScale(float scale) { }
}
