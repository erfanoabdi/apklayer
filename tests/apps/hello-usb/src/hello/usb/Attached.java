package hello.usb;

import android.app.Activity;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;

/** Started by the system for the device its device_filter names (ACTION_USB_DEVICE_ATTACHED), with the permission the person gave by confirming. */
public class Attached extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        UsbDevice d = getIntent().getParcelableExtra(UsbManager.EXTRA_DEVICE);
        boolean ok = d != null && Main.zero(d) && getSystemService(UsbManager.class).hasPermission(d);
        Main.report("attach launch", ok, "device " + (d == null ? null : Main.id(d)) + " action " + getIntent().getAction());
        finish();
    }
}
