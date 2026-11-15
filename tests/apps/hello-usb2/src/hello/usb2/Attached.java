package hello.usb2;

import android.app.Activity;
import android.os.Bundle;

/** A second app with the same device_filter as hello.usb: tests/vm/usb.py picks hello.usb in the one chooser; this one must not start. */
public class Attached extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        System.out.println("[usb2] STARTED");
        finish();
    }
}
