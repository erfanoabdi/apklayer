// android.app.DialogFragment -- a Fragment that hosts a Dialog.
// Deprecated in API-28 in favor of androidx.fragment.app.DialogFragment,
// but legacy apps subclass the platform version. AOSP API-11+.
//
// AOSP ref: frameworks/base/core/java/android/app/DialogFragment.java
// (extends Fragment implements DialogInterface.OnCancelListener,
// DialogInterface.OnDismissListener). We mirror the public surface:
// onCreateDialog, show/dismiss, the STYLE_* constants, and the dialog
// accessors. The dialog itself uses our non-rendering android.app.Dialog,
// so callback chains (onCancel/onDismiss) still fire.
package android.app;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class DialogFragment extends Fragment
        implements DialogInterface.OnCancelListener, DialogInterface.OnDismissListener {

    public static final int STYLE_NORMAL       = 0;
    public static final int STYLE_NO_TITLE     = 1;
    public static final int STYLE_NO_FRAME     = 2;
    public static final int STYLE_NO_INPUT     = 3;

    private int mStyle = STYLE_NORMAL;
    private int mTheme = 0;
    private boolean mCancelable = true;
    private boolean mShowsDialog = true;
    private Dialog mDialog;
    private boolean mViewDestroyed;
    private boolean mDismissed;
    private boolean mShownByMe;

    public DialogFragment() {}

    public void setStyle(int style, int theme) { mStyle = style; if (theme != 0) mTheme = theme; }

    /** AOSP show(FragmentManager,String): "adds the fragment to the given
     *  FragmentManager" via a real transaction (`ft.add(this, tag);
     *  ft.commit();`), so isAdded()/getFragmentManager() are correct for
     *  the fragment's whole visible lifetime. Our FragmentManagerImpl
     *  (runtime/java/android/app/FragmentManagerImpl.java) DOES run a real
     *  add-transaction (onAttach/onCreate/... through the host's current
     *  lifecycle state), unlike the previous version of this method which
     *  discarded `manager` entirely -- see showNow() below. */
    public void show(FragmentManager manager, String tag) {
        mDismissed = false;
        mShownByMe = true;
        showNow(manager, tag);
    }
    public int show(FragmentTransaction transaction, String tag) {
        mDismissed = false;
        mShownByMe = true;
        boolean registered = false;
        if (transaction != null) {
            try {
                transaction.add(this, tag);
                registered = true;
            } catch (Throwable ignored) {}
        }
        createAndShowDialog(registered);
        if (transaction != null) {
            try { return transaction.commit(); } catch (Throwable ignored) {}
        }
        return -1;
    }
    /** Registers this fragment with `manager` (so isAdded()/
     *  getFragmentManager() become correct, matching real AOSP show()),
     *  then builds and displays the actual Dialog. commitNow() is used
     *  (synchronous) rather than AOSP's async commit() so isAdded() is
     *  already true by the time this method returns -- a deliberate,
     *  disclosed deviation from AOSP's exact transaction timing, chosen
     *  because app code commonly checks isAdded() right after calling
     *  show(). */
    public void showNow(FragmentManager manager, String tag) {
        boolean registered = false;
        if (manager != null) {
            try {
                manager.beginTransaction().add(this, tag).commitNow();
                registered = true;
            } catch (Throwable ignored) {}
        }
        createAndShowDialog(registered);
    }

    /** Builds and displays this fragment's Dialog. AOSP drives this via
     *  the DialogFragment-specific onGetLayoutInflater() hook, called from
     *  within the normal Fragment view-creation step; our simplified
     *  FragmentManagerImpl doesn't know about DialogFragment at all -- it
     *  only drives onAttach/onCreate/onActivityCreated/onStart/onResume --
     *  so the dialog still has to be created and shown here regardless of
     *  whether the fragment was successfully registered with a manager.
     *  `alreadyCreated` is true when a transaction already ran onCreate
     *  (and possibly onStart, depending on the host's lifecycle state) for
     *  us, so those aren't invoked a second time. */
    private void createAndShowDialog(boolean alreadyCreated) {
        try {
            if (!alreadyCreated) onCreate(null);
            mDialog = onCreateDialog(null);
            if (mDialog == null) {
                // No custom dialog: build a default empty one so the
                // lifecycle still completes.
                mDialog = new Dialog(getContext());
            }
            mDialog.setOnCancelListener(this);
            mDialog.setOnDismissListener(this);
            mDialog.setCancelable(mCancelable);
            if (!alreadyCreated) onStart();
            mDialog.show();
        } catch (Throwable ignored) {}
    }

    /** Override point: returns the Dialog this fragment displays. AOSP
     *  default returns a plain Dialog with the chosen theme. */
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new Dialog(getContext(), mTheme);
    }

    public void dismiss() { dismissInternal(false); }
    public void dismissAllowingStateLoss() { dismissInternal(true); }
    private void dismissInternal(boolean allowStateLoss) {
        if (mDismissed) return;
        mDismissed = true;
        mShownByMe = false;
        if (mDialog != null) {
            try { mDialog.dismiss(); } catch (Throwable ignored) {}
        }
        mViewDestroyed = true;
        // AOSP dismissInternal: pop the back-stack entry if show() pushed
        // one, else commit a remove() transaction so isAdded()/
        // getFragmentManager() clear correctly. showNow()/show() here
        // never call addToBackStack (our FragmentManagerImpl's back stack
        // is an already-disclosed no-op -- see that file's header), so
        // remove() is always the right branch: without this, a fragment
        // registered by the showNow() fix above would stay isAdded()==true
        // forever after being dismissed.
        final FragmentManager fm = getFragmentManager();
        if (fm != null) {
            try {
                final FragmentTransaction ft = fm.beginTransaction().remove(this);
                if (allowStateLoss) ft.commitAllowingStateLoss(); else ft.commit();
            } catch (Throwable ignored) {}
        }
    }

    public Dialog getDialog() { return mDialog; }
    public boolean getShowsDialog() { return mShowsDialog; }
    public void setShowsDialog(boolean showsDialog) { mShowsDialog = showsDialog; }
    public boolean isCancelable() { return mCancelable; }
    public void setCancelable(boolean cancelable) {
        mCancelable = cancelable;
        if (mDialog != null) mDialog.setCancelable(cancelable);
    }
    public int getTheme() { return mTheme; }

    @Override public void onCancel(DialogInterface dialog) {}
    @Override public void onDismiss(DialogInterface dialog) {
        if (!mViewDestroyed && !mDismissed) dismissInternal(true);
    }

    @Override public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return null;
    }
}
