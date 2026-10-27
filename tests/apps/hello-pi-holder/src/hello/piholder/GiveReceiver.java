package hello.piholder;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Process;

/** Gets PendingIntents of another app and sends them (twice for the first: one-shot), then reports to the creator what happened. */
public class GiveReceiver extends BroadcastReceiver {
    private static String creatorOf(PendingIntent pi) {
        try { return pi.getCreatorPackage(); } catch (RuntimeException unknown) { return null; }  // a canceled PendingIntent has no info
    }

    @Override public void onReceive(Context context, Intent intent) {
        String creator = intent.getStringExtra("creator");
        PendingIntent first = intent.getParcelableExtra("pi1", PendingIntent.class), second = intent.getParcelableExtra("pi2", PendingIntent.class);
        boolean canceled = false, firstCanceled = false;
        try { first.send(context, 5, new Intent().putExtra("fill", "x")); } catch (PendingIntent.CanceledException e) { firstCanceled = true; }
        if (!firstCanceled) {
            try { first.send(context, 6, new Intent().putExtra("fill", "y")); } catch (PendingIntent.CanceledException expected) { canceled = true; }
        }
        try { if (second != null) second.send(context, 7, new Intent().putExtra("fill", "z")); } catch (PendingIntent.CanceledException e) { System.out.println("[pi-h] second canceled"); }
        System.out.println("[pi-h] sent, holder pid=" + Process.myPid() + " firstCanceled=" + firstCanceled);
        context.sendBroadcast(new Intent("hello.pi.REPORT").setPackage(creator).putExtra("canceled", canceled).putExtra("firstCanceled", firstCanceled).putExtra("holderPid", Process.myPid())
                .putExtra("creatorPackage", creatorOf(first)));
    }
}
