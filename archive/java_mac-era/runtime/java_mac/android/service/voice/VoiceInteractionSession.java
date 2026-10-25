package android.service.voice;

import android.app.Dialog;
import android.app.DirectAction;
import android.app.assist.AssistContent;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.Region;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * One invocation of the assistant: its window, the assist data it was handed,
 * and the request objects an app's VoiceInteractor sends it.
 *
 * PACKAGE NOTE -- see VoiceInteractionService. Nothing binds an assistant here,
 * so no session is ever created by the system and none of the on* callbacks
 * fires. Everything a session does to the SYSTEM (show, hide, start an
 * assistant activity, ask an activity for direct actions) has nothing to act
 * on. The parts that need no system -- the state round-trips, the Insets value
 * object, the Request accessors -- are faithful.
 *
 * ⚠ INCOMPLETE, and deliberately so, in two places whose types are absent from
 * this tree and outside this lane:
 *   - the two deprecated onHandleAssist / onHandleAssistSecondary overloads and
 *     AssistState.getAssistStructure(), which name android.app.assist.
 *     AssistStructure (android/app/assist/ holds only AssistContent);
 *   - every member naming VoiceInteractor.Prompt or
 *     VoiceInteractor.PickOptionRequest.Option, which android.app
 *     .VoiceInteractor does not declare in this tree.
 * They are reported as residual gaps rather than faked with a wrong type.
 *
 * Constant values transcribed VERBATIM from /tmp/aosp_api/android16.txt:
 *   SHOW_WITH_ASSIST=1, SHOW_WITH_SCREENSHOT=2, SHOW_SOURCE_ASSIST_GESTURE=4,
 *   SHOW_SOURCE_APPLICATION=8, SHOW_SOURCE_ACTIVITY=16 (0x10),
 *   SHOW_SOURCE_PUSH_TO_TALK=32 (0x20), SHOW_SOURCE_NOTIFICATION=64 (0x40),
 *   SHOW_SOURCE_AUTOMOTIVE_SYSTEM_UI=128 (0x80).
 * These are a BITMASK, not ordinals.
 *
 * Source: frameworks/base/core/java/android/service/voice/VoiceInteractionSession.java
 */
public class VoiceInteractionSession
        implements ComponentCallbacks2, KeyEvent.Callback {

    public static final int SHOW_WITH_ASSIST = 1 << 0;
    public static final int SHOW_WITH_SCREENSHOT = 1 << 1;
    public static final int SHOW_SOURCE_ASSIST_GESTURE = 1 << 2;
    public static final int SHOW_SOURCE_APPLICATION = 1 << 3;
    public static final int SHOW_SOURCE_ACTIVITY = 1 << 4;
    public static final int SHOW_SOURCE_PUSH_TO_TALK = 1 << 5;
    public static final int SHOW_SOURCE_NOTIFICATION = 1 << 6;
    public static final int SHOW_SOURCE_AUTOMOTIVE_SYSTEM_UI = 1 << 7;

    public static final String KEY_SHOW_SESSION_ID = "android.service.voice.SHOW_SESSION_ID";
    /** API 35. */
    public static final String KEY_FOREGROUND_ACTIVITIES =
            "android.service.voice.FOREGROUND_ACTIVITIES";

    private final Context mContext;
    private final Handler mHandler;
    private int mTheme;
    private boolean mUiEnabled = true;
    private int mDisabledShowContext;
    private View mContentView;

    public VoiceInteractionSession(Context context) {
        this(context, new Handler());
    }

    public VoiceInteractionSession(Context context, Handler handler) {
        mContext = context;
        mHandler = handler;
    }

    public Context getContext() { return mContext; }

    public LayoutInflater getLayoutInflater() {
        return mContext == null ? null : LayoutInflater.from(mContext);
    }

    /** AOSP returns the session's own Dialog. No session window is ever
     *  created here, and AOSP also returns null before onCreate. */
    public Dialog getWindow() { return null; }

    public void setTheme(int theme) { mTheme = theme; }

    public void setUiEnabled(boolean enabled) { mUiEnabled = enabled; }

    /** ⚠ ASSIGNS state so getDisabledShowContext() round-trips. */
    public void setDisabledShowContext(int flags) { mDisabledShowContext = flags; }

    public int getDisabledShowContext() { return mDisabledShowContext; }

    /** AOSP reports what the USER disabled in Settings, which is separate from
     *  what this session disabled. Nothing is disabled here. */
    public int getUserDisabledShowContext() { return 0; }

    public void setContentView(View view) { mContentView = view; }

    /** AOSP's default returns null and the session runs with no content. */
    public View onCreateContentView() { return null; }

    /** AOSP asks the system to keep the screen on for the session. */
    public void setKeepAwake(boolean keepAwake) { }

    public void closeSystemDialogs() { }

    /** AOSP asks the system to show/hide the session UI. There is no system
     *  and no session window; the hooks below are still called so a session
     *  driving itself (as its own tests do) sees a coherent lifecycle. */
    public void show(Bundle args, int showFlags) { onShow(args, showFlags); }

    public void hide() { onHide(); }

    public void finish() { onDestroy(); }

    // ---- lifecycle / callbacks (empty in AOSP too; the session overrides) ---
    public void onCreate() { }

    public void onDestroy() { }

    public void onPrepareShow(Bundle args, int showFlags) { }

    public void onShow(Bundle args, int showFlags) { }

    public void onHide() { }

    public void onBackPressed() { }

    public void onCloseSystemDialogs() { }

    public void onLockscreenShown() { }

    public void onComputeInsets(Insets outInsets) {
        // AOSP fills the insets from the session's own decor. With no window
        // there is nothing to measure, so the defaults set by Insets' own
        // constructor stand -- an all-zero frame, which is what a session with
        // no content occupies.
    }

    public void onTaskStarted(Intent intent, int taskId) { }

    public void onTaskFinished(Intent intent, int taskId) { }

    public void onHandleAssist(AssistState state) { }

    public void onHandleScreenshot(Bitmap screenshot) { }

    public void onAssistStructureFailure(Throwable failure) { }

    /** API 29. */
    public void onDirectActionsInvalidated(ActivityId activityId) { }

    public void onRequestConfirmation(ConfirmationRequest request) { }

    public void onRequestPickOption(PickOptionRequest request) { }

    public void onRequestCompleteVoice(CompleteVoiceRequest request) { }

    public void onRequestAbortVoice(AbortVoiceRequest request) { }

    public void onRequestCommand(CommandRequest request) { }

    public void onCancelRequest(Request request) { }

    /** AOSP's default reports that NO command is supported, so an app's
     *  VoiceInteractor falls back rather than waiting on a command the session
     *  never handles. An array of the right length is required -- returning
     *  null would NPE the caller. */
    public boolean[] onGetSupportedCommands(String[] commands) {
        return new boolean[commands == null ? 0 : commands.length];
    }

    // ---- system requests ----------------------------------------------------
    public void startVoiceActivity(Intent intent) { }

    public void startAssistantActivity(Intent intent) { }

    /** API 29. */
    public void startAssistantActivity(Intent intent, Bundle bundle) { }

    /**
     * API 29. AOSP asks the named activity for its direct actions and delivers
     * them to the consumer. There is no activity registry to ask, and a
     * consumer that is never called hangs the session -- so the EMPTY LIST is
     * delivered, which is exactly what AOSP delivers for an activity that
     * publishes none.
     */
    public final void requestDirectActions(ActivityId activityId,
            CancellationSignal cancellationSignal, Executor resultExecutor,
            Consumer<List<DirectAction>> callback) {
        deliver(resultExecutor, callback, Collections.<DirectAction>emptyList());
    }

    /** API 29. AOSP performs the action in the owning activity and delivers its
     *  result Bundle. Nothing can perform it, and AOSP delivers null when the
     *  action could not be performed. */
    public final void performDirectAction(DirectAction action, Bundle extras,
            CancellationSignal cancellationSignal, Executor resultExecutor,
            Consumer<Bundle> resultListener) {
        deliver(resultExecutor, resultListener, null);
    }

    /** API 33. No activity ever becomes visible to an assistant here, so the
     *  callback is remembered but never invoked. */
    public final void registerVisibleActivityCallback(Executor executor,
            VisibleActivityCallback callback) { }

    public final void unregisterVisibleActivityCallback(VisibleActivityCallback callback) { }

    private static <T> void deliver(Executor executor, final Consumer<T> consumer,
            final T value) {
        if (consumer == null) return;
        Runnable r = new Runnable() {
            @Override public void run() { consumer.accept(value); }
        };
        if (executor != null) executor.execute(r); else r.run();
    }

    // ---- KeyEvent.Callback --------------------------------------------------
    // AOSP's bodies return false (unhandled) unless the session overrides.
    @Override public boolean onKeyDown(int keyCode, KeyEvent event) { return false; }

    @Override public boolean onKeyUp(int keyCode, KeyEvent event) { return false; }

    @Override public boolean onKeyLongPress(int keyCode, KeyEvent event) { return false; }

    @Override public boolean onKeyMultiple(int keyCode, int count, KeyEvent event) {
        return false;
    }

    // ---- ComponentCallbacks2 ------------------------------------------------
    @Override public void onConfigurationChanged(Configuration newConfig) { }

    @Override public void onLowMemory() { }

    @Override public void onTrimMemory(int level) { }

    public void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        writer.print(prefix);
        writer.println("VoiceInteractionSession:");
        writer.print(prefix);
        writer.println("  mUiEnabled=" + mUiEnabled + " mTheme=" + mTheme);
    }

    // =========================================================================
    // Nested types
    // =========================================================================

    /** An opaque handle to one activity. AOSP's public API declares NO members
     *  on it at all -- it exists only to be passed back. */
    public static class ActivityId {
        public ActivityId() { }
    }

    /** API 33 callback pair for activity visibility. Both are default methods
     *  in AOSP, so a listener implements only the half it cares about. */
    public interface VisibleActivityCallback {
        default void onVisible(VisibleActivityInfo activityInfo) { }

        default void onInvisible(ActivityId activityId) { }
    }

    /**
     * API 30 bundle of everything the platform gathered for one activity.
     *
     * ⚠ getAssistStructure() is MISSING -- android.app.assist.AssistStructure
     * does not exist in this tree. See the class note.
     */
    public static final class AssistState {
        private final ActivityId mActivityId;
        private final Bundle mAssistData;
        private final AssistContent mAssistContent;
        private final int mIndex;
        private final int mCount;

        AssistState(ActivityId activityId, Bundle assistData, AssistContent assistContent,
                int index, int count) {
            mActivityId = activityId;
            mAssistData = assistData;
            mAssistContent = assistContent;
            mIndex = index;
            mCount = count;
        }

        public ActivityId getActivityId() { return mActivityId; }

        public Bundle getAssistData() { return mAssistData; }

        public AssistContent getAssistContent() { return mAssistContent; }

        /** -1 when the state carries no activity, per AOSP's @IntRange. */
        public int getIndex() { return mIndex; }

        public int getCount() { return mCount; }

        /** AOSP: true for the activity that had focus. */
        public boolean isFocused() { return mIndex == 0; }
    }

    /**
     * The insets a session reports so touches outside its UI fall through.
     * Pure value object; the two final fields are allocated by the constructor
     * exactly as AOSP does, so a session can mutate them in place.
     *
     * Constants transcribed verbatim: TOUCHABLE_INSETS_FRAME=0,
     * TOUCHABLE_INSETS_CONTENT=1, TOUCHABLE_INSETS_REGION=3. ⚠ There is no
     * public value 2 -- the run jumps 1 -> 3.
     */
    public static final class Insets {
        public static final int TOUCHABLE_INSETS_FRAME = 0;
        public static final int TOUCHABLE_INSETS_CONTENT = 1;
        public static final int TOUCHABLE_INSETS_REGION = 3;

        public final Rect contentInsets = new Rect();
        public final Region touchableRegion = new Region();
        public int touchableInsets;

        public Insets() { }
    }

    /**
     * A pending request from an app's VoiceInteractor.
     *
     * ⚠ Every subclass's getVoicePrompt() / Option-typed member is MISSING --
     * android.app.VoiceInteractor in this tree declares neither Prompt nor
     * PickOptionRequest.Option. See the class note.
     */
    public static class Request {
        private final String mCallingPackage;
        private final int mCallingUid;
        private final Bundle mExtras;
        private boolean mActive = true;

        Request(String callingPackage, int callingUid, Bundle extras) {
            mCallingPackage = callingPackage;
            mCallingUid = callingUid;
            mExtras = extras;
        }

        public String getCallingPackage() { return mCallingPackage; }

        public int getCallingUid() { return mCallingUid; }

        public Bundle getExtras() { return mExtras; }

        /** AOSP: false once the request has been answered or cancelled. */
        public boolean isActive() { return mActive; }

        /** ⚠ ASSIGNS state -- a cancel that left isActive() true would let a
         *  session answer a request the app already gave up on. */
        public void cancel() { mActive = false; }
    }

    public static final class ConfirmationRequest extends Request {
        ConfirmationRequest(String callingPackage, int callingUid, Bundle extras) {
            super(callingPackage, callingUid, extras);
        }

        /** @deprecated in AOSP in favour of getVoicePrompt(). */
        @Deprecated public CharSequence getPrompt() { return null; }

        public void sendConfirmationResult(boolean confirmed, Bundle result) { cancel(); }
    }

    public static final class PickOptionRequest extends Request {
        PickOptionRequest(String callingPackage, int callingUid, Bundle extras) {
            super(callingPackage, callingUid, extras);
        }

        /** @deprecated in AOSP in favour of getVoicePrompt(). */
        @Deprecated public CharSequence getPrompt() { return null; }
    }

    public static final class CompleteVoiceRequest extends Request {
        CompleteVoiceRequest(String callingPackage, int callingUid, Bundle extras) {
            super(callingPackage, callingUid, extras);
        }

        /** @deprecated in AOSP in favour of getVoicePrompt(). */
        @Deprecated public CharSequence getMessage() { return null; }

        public void sendCompleteResult(Bundle result) { cancel(); }
    }

    public static final class AbortVoiceRequest extends Request {
        AbortVoiceRequest(String callingPackage, int callingUid, Bundle extras) {
            super(callingPackage, callingUid, extras);
        }

        /** @deprecated in AOSP in favour of getVoicePrompt(). */
        @Deprecated public CharSequence getMessage() { return null; }

        public void sendAbortResult(Bundle result) { cancel(); }
    }

    public static final class CommandRequest extends Request {
        private final String mCommand;

        CommandRequest(String callingPackage, int callingUid, Bundle extras,
                String command) {
            super(callingPackage, callingUid, extras);
            mCommand = command;
        }

        public String getCommand() { return mCommand; }

        /** AOSP keeps the request ACTIVE for an intermediate result and closes
         *  it only on the final one; a version that closed on both would drop
         *  every later chunk. */
        public void sendIntermediateResult(Bundle result) { }

        public void sendResult(Bundle result) { cancel(); }
    }
}
