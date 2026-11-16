package hello.neondiff;

import android.app.Activity;
import android.os.Bundle;

/** Instruction-form differential test (neondiff.c): one "[nd] <case> <hash>" line per form, compared between translator modes and real arm64 (compare.sh). */
public class Main extends Activity {
    private static native void run();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        System.loadLibrary("hello");
        run();
        System.out.println("[nd] DONE");
        finish();
    }
}
