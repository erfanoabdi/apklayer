package hello.bcreceiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ProtectedReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) { System.out.println("[bcr] protected " + intent.getAction()); }
}
