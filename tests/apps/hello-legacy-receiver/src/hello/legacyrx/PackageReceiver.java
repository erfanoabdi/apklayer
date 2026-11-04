package hello.legacyrx;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** An app targeting 25 (tests/vm/installer.py): its manifest receiver gets the package broadcasts, as Android delivers them to apps targeting below 26. */
public class PackageReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        System.out.println("[legacy] " + intent.getAction() + " " + intent.getData().getSchemeSpecificPart());
    }
}
