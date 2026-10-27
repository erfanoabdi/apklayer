package hello.bcreceiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** goAsync(): the ordered result is finished later, from another thread, and the broadcast waits for it. */
public class AsyncReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        final PendingResult result = goAsync();
        System.out.println("[bcr] async started " + intent.getAction() + " in=" + result.getResultData());
        new Thread(() -> {
            try { Thread.sleep(500); } catch (InterruptedException ignored) { }
            result.setResultData(result.getResultData() + "|async");
            result.setResultCode(result.getResultCode() + 10);
            System.out.println("[bcr] async finishing");
            result.finish();
        }).start();
    }
}
