package hello.cameranative;

import android.app.Activity;
import android.os.Bundle;

/** The camera NDK (libcamera2ndk) against the stack's fake camera (tests/vm/camera-ndk.sh): the id list, LENS_FACING, open, a YUV frame. */
public final class Main extends Activity {
    static { System.loadLibrary("hello"); }

    private static native String run();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        new Thread(() -> {
            String result = run();  // "" = passed
            System.out.println(result.isEmpty() ? "[ndkcam] PASS" : "[ndkcam] FAIL " + result);
            finishAndRemoveTask();
        }).start();
    }
}
