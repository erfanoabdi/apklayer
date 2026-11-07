package hello.permissions;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

/**
 * Runtime permissions and roles as the system's permission screen answers them (the hub's prompt, tests/vm/permissions.py answers it): CAMERA
 * allowed, the contacts group denied twice (rationale after the first), a third request denied with no question, RECORD_AUDIO granted in the
 * manager before the start (no question), CAMERA not asked again, the SMS role refused without telephony, the browser role given.
 * The main looper keeps running while a question is up. Prints `[perm] PASS|FAIL <step>`, then `[perm] DONE`.
 */
public class Main extends Activity {
    private static final String CAMERA = "android.permission.CAMERA", READ = "android.permission.READ_CONTACTS", WRITE = "android.permission.WRITE_CONTACTS",
            AUDIO = "android.permission.RECORD_AUDIO";
    private final Handler main = new Handler(Looper.getMainLooper());
    private int ticks;

    private void tick() { ticks++; main.postDelayed(this::tick, 100); }

    private static void check(boolean ok, String step) { System.out.println("[perm] " + (ok ? "PASS " : "FAIL ") + step); }

    private boolean held(String permission) { return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED; }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        tick();
        check(!held(CAMERA), "unasked camera not held");
        check(held(AUDIO), "manager grant held at start");
        ticks = 0;
        requestPermissions(new String[] {CAMERA}, 1);
    }

    @Override public void onRequestPermissionsResult(int code, String[] names, int[] results) {
        check(Looper.myLooper() == Looper.getMainLooper(), "result " + code + " on the main thread");
        boolean granted = results.length > 0, denied = results.length > 0;
        for (int r : results) { granted &= r == PackageManager.PERMISSION_GRANTED; denied &= r == PackageManager.PERMISSION_DENIED; }
        switch (code) {
            case 1 -> {
                check(granted && held(CAMERA), "allow: camera granted");
                check(ticks > 2, "looper ran during the dialog (" + ticks + " ticks)");
                requestPermissions(new String[] {READ, WRITE}, 2);
            }
            case 2 -> { check(denied && names.length == 2 && shouldShowRequestPermissionRationale(READ), "deny once: denied, rationale"); requestPermissions(new String[] {READ, WRITE}, 3); }
            case 3 -> { check(denied && !shouldShowRequestPermissionRationale(READ), "deny twice: denied, no rationale"); requestPermissions(new String[] {READ}, 4); }
            case 4 -> { check(denied && !held(READ), "third request denied"); requestPermissions(new String[] {AUDIO}, 5); }
            case 5 -> { check(granted, "manager grant: no question"); requestPermissions(new String[] {CAMERA}, 6); }
            case 6 -> {
                check(granted, "granted: not asked again");
                startActivityForResult(getSystemService(RoleManager.class).createRequestRoleIntent(RoleManager.ROLE_SMS), 7);
            }
            default -> check(false, "unexpected request " + code);
        }
    }

    @Override protected void onActivityResult(int code, int result, Intent data) {
        RoleManager roles = getSystemService(RoleManager.class);
        if (code == 7) {
            check(result == RESULT_CANCELED, "sms role refused without telephony");
            startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_BROWSER), 8);
        } else if (code == 8) {
            check(result == RESULT_OK && roles.isRoleHeld(RoleManager.ROLE_BROWSER), "browser role given");
            System.out.println("[perm] DONE");
        }
    }
}
