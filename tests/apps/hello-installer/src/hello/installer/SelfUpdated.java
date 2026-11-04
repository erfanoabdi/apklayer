package hello.installer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** MY_PACKAGE_REPLACED in the installer's second version (hello-installer2's manifest): the update the first one committed of itself took, and the system started the new one. */
public class SelfUpdated extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        long version;
        try { version = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).getLongVersionCode(); }
        catch (Exception unreadable) { version = -1; }
        System.out.println("[inst] " + (version == 2 ? "PASS self update" : "FAIL self update: version " + version + " after " + intent.getAction()));
    }
}
