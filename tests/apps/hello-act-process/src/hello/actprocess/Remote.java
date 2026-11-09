package hello.actprocess;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Process;
import android.widget.TextView;
import java.io.FileWriter;
import java.io.IOException;

/** Runs in ":remote" (android:process). mode "answer": reports its process and finishes; "kill": Process.killProcess(myPid) without answering. */
public class Remote extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        TextView view = new TextView(this);
        view.setText("remote " + Process.myPid());
        setContentView(view);
        try (FileWriter pid = new FileWriter(getFilesDir() + "/remote.pid")) { pid.write(Integer.toString(Process.myPid())); }
        catch (IOException failure) { System.out.println("[ap-r] FAIL pid file " + failure); }
        if ("kill".equals(getIntent().getStringExtra("mode"))) {
            new Handler().postDelayed(() -> Process.killProcess(Process.myPid()), 500);
            return;
        }
        StringBuilder names = new StringBuilder();
        for (ActivityManager.RunningAppProcessInfo p : getSystemService(ActivityManager.class).getRunningAppProcesses()) names.append(p.processName).append(',');
        setResult(RESULT_OK, new Intent().putExtra("pid", Process.myPid()).putExtra("uid", Process.myUid()).putExtra("process", android.app.Application.getProcessName())
                .putExtra("app", (getApplication() instanceof App ? App.createdIn : "not App")).putExtra("running", names.toString()));
        finish();
    }
}
