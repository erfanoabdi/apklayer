package hello.actprocess;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Process;
import android.widget.TextView;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * An activity of another android:process (":remote"): it runs in a process of its own (another pid, the same uid, its own Application), answers
 * startActivityForResult across processes; Process.killProcess(myPid) there ends only that process: this one gets RESULT_CANCELED and is resumed.
 */
public class Main extends Activity {
    private static void require(boolean yes, String detail) { if (!yes) throw new AssertionError(detail); }
    private int resumes, stops, remotePid;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        TextView view = new TextView(this);
        view.setText("act process");
        setContentView(view);
        require(getPackageName().equals(android.app.Application.getProcessName()), "main process name " + android.app.Application.getProcessName());
        startActivityForResult(new Intent(this, Remote.class).putExtra("mode", "answer"), 1);
    }

    @Override protected void onResume() { super.onResume(); resumes++; }
    @Override protected void onStop() { super.onStop(); stops++; }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        try {
            if (requestCode == 1) {
                require(resultCode == RESULT_OK && data != null, "result " + resultCode + "/" + data);
                remotePid = data.getIntExtra("pid", -1);
                require(remotePid > 0 && remotePid != Process.myPid(), "remote pid " + remotePid + ", mine " + Process.myPid());
                require(data.getIntExtra("uid", -1) == Process.myUid(), "remote uid " + data.getIntExtra("uid", -1));
                require("hello.actprocess:remote".equals(data.getStringExtra("process")), "remote process " + data.getStringExtra("process"));
                require("hello.actprocess:remote".equals(data.getStringExtra("app")), "remote Application " + data.getStringExtra("app"));
                String running = data.getStringExtra("running");
                require(running.contains("hello.actprocess,") && running.contains("hello.actprocess:remote,"), "running processes " + running);
                require(stops == 1, "covered by the remote activity: stops " + stops);
                System.out.println("[ap] remote answered: pid " + remotePid + " (mine " + Process.myPid() + "), running " + running);
                startActivityForResult(new Intent(this, Remote.class).putExtra("mode", "kill"), 2);
            } else {
                require(requestCode == 2 && resultCode == RESULT_CANCELED, "killed remote: result " + requestCode + "/" + resultCode);
                int killed = Integer.parseInt(Files.readString(Path.of(getFilesDir() + "/remote.pid")).trim());
                require(killed > 0 && killed != Process.myPid(), "killed remote pid " + killed);  // the first one, still running (Android reuses it), or a new one
                new Handler().postDelayed(() -> afterKill(killed), 1000);
            }
        } catch (Throwable failure) { failure.printStackTrace(System.out); System.out.println("[ap] FAIL " + failure); }
    }

    private void afterKill(int killed) {
        try {
            require(resumes == 3 && stops == 2, "resumed after each remote: resumes " + resumes + ", stops " + stops);
            require(!ProcessHandle.of(killed).map(ProcessHandle::isAlive).orElse(false), "the killed process " + killed + " still runs");
            for (ActivityManager.RunningAppProcessInfo p : getSystemService(ActivityManager.class).getRunningAppProcesses())
                require(p.pid != killed, "the killed process is still listed: " + p.processName);
            System.out.println("[ap] PASS process: :remote ran as pid " + remotePid + " and " + killed + ", killProcess ended only it, this activity resumed");
        } catch (Throwable failure) { System.out.println("[ap] FAIL " + failure); }
    }
}
