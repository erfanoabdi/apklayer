package hello.actrestart;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Process;
import android.widget.TextView;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Main starts Second; Second ends its own process (Process.killProcess(myPid)): the hub starts Main again in a new process (ActivityManagerService restarts the
 * activity below the top one) with the instance state it saved before (HubProtocol.RESTART_ON_DEATH, ActivityTask.SAVED_STATE). The new process prints the verdict.
 */
public class Main extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        TextView view = new TextView(this);
        view.setText("act restart");
        setContentView(view);
        Path pid = Path.of(getFilesDir() + "/first.pid");
        try {
            if (state == null) {
                Files.writeString(pid, Integer.toString(Process.myPid()));
                startActivity(new Intent(this, Second.class));
                return;
            }
            int first = Integer.parseInt(Files.readString(pid).trim());
            if (first == Process.myPid() || !"saved".equals(state.getString("k"))) throw new AssertionError("pid " + first + "/" + Process.myPid() + ", state " + state);
            System.out.println("[ar] PASS restart: new process " + Process.myPid() + " (was " + first + ") got its saved state " + state.getString("k"));
        } catch (Throwable failure) { failure.printStackTrace(System.out); System.out.println("[ar] FAIL " + failure); }
    }

    @Override protected void onSaveInstanceState(Bundle state) { super.onSaveInstanceState(state); state.putString("k", "saved"); }
}
