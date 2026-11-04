package hello.actcaller;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;
import android.widget.TextView;

/** Starts activities of hello.acttarget (another app, started on demand by the hub). The scenario comes from -Dhello.act=... */
public class Main extends Activity {
    private static void require(boolean yes, String detail) { if (!yes) throw new AssertionError(detail); }
    private static Main first;  // scenario "up": the instance UpChild navigates up to
    private static int upStage;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        TextView view = new TextView(this);
        view.setText("act caller");
        setContentView(view);
        if (getIntent().getBooleanExtra("up", false)) { upArrived(); return; }
        try { start(System.getProperty("hello.act", "result")); }
        catch (Throwable failure) { failure.printStackTrace(System.out); System.out.println("[act] FAIL " + failure); }
    }

    private Intent pick(String text) {
        Bundle nested = new Bundle();
        nested.putString("k", "v");
        nested.putLong("l", 1L << 40);
        return new Intent("hello.act.PICK").putExtra("text", text).putExtra("numbers", new int[] {1, 2, 3, 4}).putExtra("uri", Uri.parse("content://x/y?z=1"))
                .putExtra("nested", nested);
    }

    private void start(String scenario) {
        switch (scenario) {
            case "result" -> startActivityForResult(pick("hello"), 7);
            case "explicit" -> startActivityForResult(pick("explicit").setComponent(new ComponentName("hello.acttarget", "hello.acttarget.PickActivity")), 7);
            case "again" -> {
                startActivity(pick("first").putExtra("stay", true)); // the target app starts and stays
                new android.os.Handler().postDelayed(() -> startActivityForResult(pick("second"), 7), 6000); // running now: onNewIntent
            }
            case "private" -> {
                startActivityForResult(new Intent().setComponent(new ComponentName("hello.acttarget", "hello.acttarget.PrivateActivity")), 9);
                new android.os.Handler().postDelayed(() -> System.out.println("[act] PASS private: refused without starting"), 5000);
            }
            case "up" -> { first = this; startActivity(new Intent(this, UpChild.class).putExtra("case", "orphan")); }
            default -> System.out.println("[act] unknown scenario");
        }
    }

    /** "up", back from the orphan child: the parent case (CLEAR_TOP up-intent to this standard activity) */
    @Override protected void onResume() {
        super.onResume();
        if (this == first && UpChild.orphanDone && upStage++ == 0) startActivity(new Intent(this, UpChild.class).putExtra("case", "clear"));
    }

    /** The new Main the CLEAR_TOP up-intent created: the old one and the child are gone. */
    private void upArrived() {
        new android.os.Handler().postDelayed(() -> {
            try {
                require(first != null && first != this && first.isDestroyed(), "the old parent was not replaced");
                require(UpChild.last != null && UpChild.last.isDestroyed(), "the child was not destroyed");
                // the app's own task as ActivityManager reports it (Meet's TaskMonitor ends the call when its task is missing)
                android.app.ActivityManager am = getSystemService(android.app.ActivityManager.class);
                java.util.List<android.app.ActivityManager.RunningTaskInfo> running = am.getRunningTasks(5);
                require(running.size() == 1 && getComponentName().equals(running.get(0).topActivity) && running.get(0).taskId == getTaskId(), "running tasks " + running);
                java.util.List<android.app.ActivityManager.AppTask> tasks = am.getAppTasks();
                require(tasks.size() == 1 && tasks.get(0).getTaskInfo().taskId == getTaskId(), "app tasks " + tasks);
                System.out.println("[act] PASS up: false without a parent in the task; CLEAR_TOP recreated the standard parent with the up-intent; the task is reported running");
            } catch (Throwable failure) { System.out.println("[act] FAIL " + failure); }
        }, 1000);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        try {
            require(requestCode == 7 && resultCode == RESULT_OK && data != null, "result " + requestCode + "/" + resultCode + "/" + data);
            Bundle nested = data.getBundleExtra("nested");
            require(data.getIntExtra("pid", -1) != Process.myPid() && data.getIntExtra("sum", 0) == 10 && data.getStringExtra("echo") != null && data.getStringExtra("echo").endsWith("!"), "extras");
            require(Uri.parse("content://x/y?z=1").equals(data.getParcelableExtra("uri", Uri.class)), "uri");
            require(nested != null && "v".equals(nested.getString("k")) && nested.getLong("l") == (1L << 40), "nested bundle");
            if (getIntent() != null && "again".equals(System.getProperty("hello.act"))) require("newintent".equals(data.getStringExtra("how")), "how " + data.getStringExtra("how"));
            System.out.println("[act] PASS result: other app's activity answered pid=" + data.getIntExtra("pid", -1) + " echo=" + data.getStringExtra("echo"));
        } catch (Throwable failure) { failure.printStackTrace(System.out); System.out.println("[act] FAIL " + failure); }
    }
}
