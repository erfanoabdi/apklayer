package hello.actcaller;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

/** Scenario "up": Activity.navigateUpTo as Android's task manager answers it. "orphan": the parent (UpOther) is not in the task, so the call
 *  returns false and only this activity finishes. "clear": the parent Main is below, the up-intent has CLEAR_TOP: true, and Main (standard
 *  launch mode) is recreated with the up-intent (Main checks that). */
public class UpChild extends Activity {
    static boolean orphanDone;
    static Activity last;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        last = this;
        String scenario = getIntent().getStringExtra("case");
        new Handler().post(() -> {
            if ("orphan".equals(scenario)) {
                boolean up = navigateUpTo(new Intent(this, UpOther.class));
                if (up || !isFinishing()) { System.out.println("[act] FAIL up: no parent in the task gave " + up + ", finishing " + isFinishing()); return; }
                orphanDone = true;
                System.out.println("[act] up: no parent in the task: false, finished");
            } else {
                boolean up = navigateUpTo(new Intent(this, Main.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP).putExtra("up", true));
                if (!up || !isFinishing()) System.out.println("[act] FAIL up: the parent in the task gave " + up + ", finishing " + isFinishing());
            }
        });
    }
}
