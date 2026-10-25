package android.accounts;

/**
 * Callback for changes to the set of accounts on the device.
 *
 * ⚠ This is a TOP-LEVEL interface in AOSP (android.accounts.OnAccountsUpdateListener),
 * NOT a nested AccountManager.OnAccountsUpdateListener. Mac.apk previously declared
 * only a nested copy, so AccountManager.addOnAccountsUpdatedListener carried the
 * descriptor Landroid/accounts/AccountManager$OnAccountsUpdateListener; and any app
 * calling it -- every app does, the real one is the only type javac will emit --
 * hit NoSuchMethodError. debug/apidiff.py compares SIMPLE type names, so it could
 * not see the mismatch; this comment is here so the nested form does not come back.
 *
 * Source: frameworks/base/core/java/android/accounts/OnAccountsUpdateListener.java
 */
public interface OnAccountsUpdateListener {
    void onAccountsUpdated(Account[] accounts);
}
