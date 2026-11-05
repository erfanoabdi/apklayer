package hello.push;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Process;

/** LOCKED_BOOT_COMPLETED and BOOT_COMPLETED: what microG's boot receiver wakes on to start its push service. */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        System.out.println("[push] BOOT " + intent.getAction() + " pid=" + Process.myPid() + " user=" + intent.getIntExtra("android.intent.extra.user_handle", -1) + " flags=0x" + Integer.toHexString(intent.getFlags()));
    }
}
