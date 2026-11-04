package hello.installer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** A manifest receiver of package broadcasts in an app targeting 34: Android never delivers them to it (only to apps targeting below 26). */
public class PackageReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        System.out.println("[inst] FAIL manifest receiver of a 26+ app got " + intent.getAction() + " " + intent.getData());
    }
}
