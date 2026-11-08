package hello.stub;

import android.app.Activity;
import android.os.Bundle;

/** Calls into Victim: with Victim stubbed (a failed conversion) the call throws VerifyError naming it, the rest of the app runs. */
public final class Main extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            System.out.println("[stub] call returned " + Victim.hello());
        } catch (VerifyError e) {
            System.out.println("[stub] VerifyError " + e.getMessage());
        } catch (Throwable t) {
            System.out.println("[stub] FAIL " + t);
        }
        finish();
    }
}
