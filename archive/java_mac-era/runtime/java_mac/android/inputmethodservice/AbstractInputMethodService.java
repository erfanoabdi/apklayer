package android.inputmethodservice;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.inputmethod.InputMethod;
import android.view.inputmethod.InputMethodSession;

/** Base for a service that provides an input method (soft keyboard). On real
 *  Android the InputMethodManagerService binds to this over IPC and drives the
 *  IME through the IInputMethod transport that onBind() hands back.
 *
 *  Mac.apk is a SINGLE-APP host: there is no system process, so nothing ever
 *  binds an IME that an APK declares, and host keyboard input is routed to the
 *  focused view directly rather than through the IME pipeline. That is a
 *  structural absence, not a stub -- we do not fabricate a system that binds
 *  and displays a keyboard.
 *
 *  What IS worth getting right, and what this file does, is the half of the
 *  contract that lives entirely inside the app's own process: the class
 *  hierarchy, the two inner base classes an IME subclasses, and the dispatch
 *  plumbing between a session and the service's key/motion callbacks. All of
 *  that is self-contained AOSP logic with no system dependency, so it behaves
 *  identically here -- and it means a bundled IME can be driven end to end by
 *  calling into its own InputMethodImpl, without a system service.
 *
 *  API 3. Source: frameworks/base
 *  core/java/android/inputmethodservice/AbstractInputMethodService.java
 *  (android16-release). */
public abstract class AbstractInputMethodService extends Service
        implements KeyEvent.Callback {

    /** AOSP holds the InputMethod created by onCreateInputMethodInterface() so
     *  a second bind reuses it. onBind() below keeps that memoization even
     *  though it cannot return a transport. */
    private InputMethod mInputMethod;

    /** AOSP: `final KeyEvent.DispatcherState mDispatcherState = new
     *  KeyEvent.DispatcherState();` -- shared long-press/tracking state across
     *  every event this service dispatches. Package-visible in AOSP; the
     *  session impl below reads it. */
    final KeyEvent.DispatcherState mDispatcherState = new KeyEvent.DispatcherState();

    public AbstractInputMethodService() { super(); }

    /** Base class for derived classes to implement their {@link InputMethod}
     *  interface. AOSP implements exactly these three; everything else on
     *  InputMethod is left abstract for the subclass. */
    public abstract class AbstractInputMethodImpl implements InputMethod {
        public AbstractInputMethodImpl() { }

        /** AOSP: `callback.sessionCreated(onCreateInputMethodSessionInterface());` */
        @Override public void createSession(SessionCallback callback) {
            callback.sessionCreated(onCreateInputMethodSessionInterface());
        }

        /** AOSP casts to AbstractInputMethodSessionImpl and calls setEnabled --
         *  the ClassCastException on a foreign session is AOSP behaviour. */
        @Override public void setSessionEnabled(InputMethodSession session, boolean enabled) {
            ((AbstractInputMethodSessionImpl) session).setEnabled(enabled);
        }

        @Override public void revokeSession(InputMethodSession session) {
            ((AbstractInputMethodSessionImpl) session).revokeSelf();
        }
    }

    /** Base class for derived classes to implement their
     *  {@link InputMethodSession} interface. Handles enable/revoke bookkeeping
     *  and routes dispatched events onto the service's callbacks. */
    public abstract class AbstractInputMethodSessionImpl implements InputMethodSession {
        boolean mEnabled = true;
        boolean mRevoked;

        public AbstractInputMethodSessionImpl() { }

        public boolean isEnabled() { return mEnabled; }

        public boolean isRevoked() { return mRevoked; }

        /** AOSP: a revoked session can never be re-enabled. */
        public void setEnabled(boolean enabled) {
            if (!mRevoked) {
                mEnabled = enabled;
            }
        }

        public void revokeSelf() {
            mRevoked = true;
            mEnabled = false;
        }

        /** AOSP routes the event through KeyEvent.dispatch() against the
         *  SERVICE as the KeyEvent.Callback, using the service-wide dispatcher
         *  state, then reports the handled flag back. This is pure in-process
         *  logic and is faithful here. */
        @Override public void dispatchKeyEvent(int seq, KeyEvent event, EventCallback callback) {
            boolean handled = event.dispatch(AbstractInputMethodService.this,
                    mDispatcherState, this);
            if (callback != null) {
                callback.finishedEvent(seq, handled);
            }
        }

        /** AOSP delegates to the service. Declared here (not @Override) because
         *  the flagged addition to InputMethodSession is not part of the
         *  interface this tree ships. */
        public boolean onShouldVerifyKeyEvent(KeyEvent event) {
            return AbstractInputMethodService.this.onShouldVerifyKeyEvent(event);
        }

        @Override public void dispatchTrackballEvent(int seq, MotionEvent event,
                EventCallback callback) {
            boolean handled = onTrackballEvent(event);
            if (callback != null) {
                callback.finishedEvent(seq, handled);
            }
        }

        @Override public void dispatchGenericMotionEvent(int seq, MotionEvent event,
                EventCallback callback) {
            boolean handled = onGenericMotionEvent(event);
            if (callback != null) {
                callback.finishedEvent(seq, handled);
            }
        }
    }

    /** AOSP returns the service-wide dispatcher state used when processing
     *  events from the target application. */
    public KeyEvent.DispatcherState getKeyDispatcherState() {
        return mDispatcherState;
    }

    /** Called by the framework when the InputMethod interface for this service
     *  needs to be created. Abstract in AOSP; InputMethodService supplies the
     *  default. */
    public abstract AbstractInputMethodImpl onCreateInputMethodInterface();

    /** Called by the framework when a new session is needed for a new client. */
    public abstract AbstractInputMethodSessionImpl onCreateInputMethodSessionInterface();

    /** AOSP's onBind() memoizes onCreateInputMethodInterface() and wraps it in
     *  an IInputMethodWrapper Binder. Mac.apk has no Binder transport and no
     *  system service to hand one to, so there is nothing to return -- but we
     *  keep the memoized construction so the IME's own interface object exists
     *  and is reachable through getInputMethodInternal()-style access from a
     *  subclass that overrides this. */
    @Override public IBinder onBind(Intent intent) {
        if (mInputMethod == null) {
            mInputMethod = onCreateInputMethodInterface();
        }
        return null;
    }

    /** AOSP: `protected void dump(FileDescriptor, PrintWriter, String[]) { }`
     *  -- an empty override of Service.dump so a Binder dump on the IME prints
     *  nothing rather than the Service default. Service.dump in this tree is
     *  also empty, so calling super would be equivalent; AOSP's own override
     *  is reproduced for shape. */
    @Override protected void dump(java.io.FileDescriptor fd, java.io.PrintWriter fout,
            String[] args) {
    }

    /** Implement this to handle trackball events on your input method. */
    public boolean onTrackballEvent(MotionEvent event) { return false; }

    /** Implement this to handle generic motion events on your input method. */
    public boolean onGenericMotionEvent(MotionEvent event) { return false; }

    /** AOSP default is false: key events are not verified against the
     *  system's own view of the event stream. */
    public boolean onShouldVerifyKeyEvent(KeyEvent event) { return false; }
}
