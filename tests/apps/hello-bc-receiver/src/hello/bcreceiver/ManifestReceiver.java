package hello.bcreceiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Process;

/** Manifest receiver: prints what it got; for ordered broadcasts appends to the result, and aborts hello.bc.ABORT. */
public class ManifestReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        System.out.println("[bcr] manifest " + action + " pid=" + Process.myPid() + " ordered=" + isOrderedBroadcast() + " in=" + getResultData() + "/" + getResultCode()
                + " extra=" + intent.getStringExtra("note"));
        if (isOrderedBroadcast()) {
            if ("hello.bc.ABORT".equals(action)) { setResultData("aborted by manifest"); abortBroadcast(); return; }
            setResultData(getResultData() + "|M");
            setResultCode(getResultCode() + 1);
        }
    }
}
