package hello.vulkan;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/** Native Vulkan image operations from a real app JVM; UI remains ordinary HWUI. */
public class Main extends Activity {
    private static native int probe();
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        System.loadLibrary("apklayer_vktest");
        int status = probe();
        if (status != 0) throw new AssertionError("Vulkan probe status " + status);
        TextView result = new TextView(this);
        result.setTextSize(22);
        result.setPadding(24, 24, 24, 24);
        result.setText("Vulkan readback PASS\n\n6 instance/device lifecycles\n256 checked image pixels per cycle\n\nUI: HWUI OpenGL\nVulkan window presentation: pending");
        setContentView(result);
        result.getViewTreeObserver().registerFrameCommitCallback(
            () -> System.out.println("[hello-vulkan] FRAME UI committed"));
        System.out.println("[hello-vulkan] PASS native image readback through JNI");
    }
}
