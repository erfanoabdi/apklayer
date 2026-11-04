package hello.usb;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConfiguration;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbRequest;
import android.os.Bundle;
import android.system.Os;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * USB as apps use it, against Linux's Gadget Zero (0525:a4a0): the device list, ACTION_USB_DEVICE_ATTACHED/DETACHED, hasPermission/requestPermission with
 * the answer through a PendingIntent, openDevice, the connection's descriptor, GET_DESCRIPTOR on endpoint 0, the loopback configuration, claimInterface,
 * a bulk round trip and a UsbRequest. Prints `[usb] PASS|FAIL <step>`.
 */
public class Main extends Activity {
    static final String PERMISSION = "hello.usb.PERMISSION";
    static final int VENDOR = 0x0525, PRODUCT = 0xa4a0;
    private UsbManager usb;
    private boolean asked;  // the first attach asks; the next one is the system's offer (Attached)

    static String id(UsbDevice d) { return String.format("%04x:%04x", d.getVendorId(), d.getProductId()); }
    static boolean zero(UsbDevice d) { return d.getVendorId() == VENDOR && d.getProductId() == PRODUCT; }
    static void report(String step, boolean ok, String detail) { System.out.println("[usb] " + (ok ? "PASS " : "FAIL ") + step + (ok ? "" : ": " + detail)); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        usb = getSystemService(UsbManager.class);
        StringBuilder list = new StringBuilder();
        for (UsbDevice d : usb.getDeviceList().values()) list.append(' ').append(d.getDeviceName()).append('=').append(id(d)).append("/configs:").append(d.getConfigurationCount());
        System.out.println("[usb] list" + list);
        registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                UsbDevice d = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (d == null) { report(intent.getAction(), false, "no EXTRA_DEVICE"); return; }
                boolean listed = usb.getDeviceList().containsKey(d.getDeviceName());
                switch (intent.getAction()) {
                    case UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                        System.out.println("[usb] attached " + d.getDeviceName() + " " + id(d) + " listed=" + listed);
                        if (zero(d)) { report("attach broadcast", listed, "not in getDeviceList"); if (!asked) { asked = true; ask(d); } }
                    }
                    case UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                        System.out.println("[usb] detached " + d.getDeviceName() + " " + id(d) + " listed=" + listed);
                        if (zero(d)) report("detach broadcast", !listed && !usb.hasPermission(d), "still listed or permitted");
                    }
                    case PERMISSION -> {
                        boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                        report("permission result", granted && usb.hasPermission(d), "granted=" + granted);
                        if (granted) new Thread(() -> exercise(d), "usb-test").start();
                    }
                    default -> { }
                }
            }
        }, filter(), Context.RECEIVER_EXPORTED);
        System.out.println("[usb] ready");
    }

    private static IntentFilter filter() {
        IntentFilter filter = new IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction(PERMISSION);
        return filter;
    }

    private void ask(UsbDevice d) {
        report("no permission before asking", !usb.hasPermission(d) && usb.openDevice(d) == null, "had it already");
        PendingIntent answer = PendingIntent.getBroadcast(this, 0, new Intent(PERMISSION).setPackage(getPackageName()), PendingIntent.FLAG_MUTABLE);
        usb.requestPermission(d, answer);
    }

    private void exercise(UsbDevice d) {
        UsbDeviceConnection c = usb.openDevice(d);
        report("open", c != null, "openDevice returned null");
        if (c == null) return;
        try {
            int fd = c.getFileDescriptor();
            String node = fd < 0 ? "" : Os.readlink("/proc/self/fd/" + fd);
            report("file descriptor", node.equals(d.getDeviceName()), "fd " + fd + " is " + node);
            byte[] desc = new byte[18];
            int n = c.controlTransfer(UsbConstants.USB_DIR_IN, 6 /* GET_DESCRIPTOR */, 0x0100 /* device */, 0, desc, desc.length, 1000);
            int vendor = (desc[8] & 0xff) | (desc[9] & 0xff) << 8, product = (desc[10] & 0xff) | (desc[11] & 0xff) << 8;
            report("control GET_DESCRIPTOR", n == 18 && desc[1] == 1 && vendor == VENDOR && product == PRODUCT, "got " + n + " bytes " + Arrays.toString(desc));
            byte[] raw = c.getRawDescriptors();
            report("raw descriptors", raw != null && raw.length > 18 && raw[1] == 1, "got " + (raw == null ? "null" : raw.length + " bytes"));
            UsbConfiguration loopback = null;
            for (int i = 0; i < d.getConfigurationCount(); i++) if (d.getConfiguration(i).getId() == 2) loopback = d.getConfiguration(i);  // Gadget Zero: 2 is loopback, 3 source/sink
            report("configurations", loopback != null && loopback.getInterfaceCount() > 0, d.getConfigurationCount() + " configurations");
            if (loopback == null) return;
            report("setConfiguration", c.setConfiguration(loopback), "USBDEVFS_SETCONFIGURATION failed");
            UsbInterface intf = loopback.getInterface(0);
            UsbEndpoint in = null, out = null;
            for (int i = 0; i < intf.getEndpointCount(); i++) {
                UsbEndpoint e = intf.getEndpoint(i);
                if (e.getType() != UsbConstants.USB_ENDPOINT_XFER_BULK) continue;
                if (e.getDirection() == UsbConstants.USB_DIR_IN) in = e; else out = e;
            }
            report("claimInterface", in != null && out != null && c.claimInterface(intf, true), "endpoints in=" + in + " out=" + out);
            // whole 4096-byte transfers: the gadget completes an OUT request at its length or at a short packet (512 is a full high-speed packet)
            byte[] sent = new byte[4096], got = new byte[4096];
            for (int i = 0; i < sent.length; i++) sent[i] = (byte) (i * 7);
            int w = c.bulkTransfer(out, sent, sent.length, 2000), r = c.bulkTransfer(in, got, got.length, 2000);
            report("bulk loopback", w == 4096 && r == 4096 && Arrays.equals(sent, got), "wrote " + w + " read " + r);
            UsbRequest request = new UsbRequest();
            ByteBuffer buffer = ByteBuffer.allocate(4096);
            boolean queued = request.initialize(c, in) && request.queue(buffer);
            for (int i = 0; i < sent.length; i++) sent[i] = (byte) (255 - i);
            int w2 = c.bulkTransfer(out, sent, sent.length, 2000);
            UsbRequest done = queued ? c.requestWait(3000) : null;
            byte[] back = Arrays.copyOf(buffer.array(), buffer.position());
            report("UsbRequest", done == request && w2 == 4096 && Arrays.equals(sent, back), "queued=" + queued + " done=" + done + " got " + back.length + " bytes");
            request.close();
            report("releaseInterface", c.releaseInterface(intf), "USBDEVFS_RELEASEINTERFACE failed");
        } catch (Exception failure) {
            report("exception", false, String.valueOf(failure));
            failure.printStackTrace(System.out);
        } finally {
            c.close();
            System.out.println("[usb] DONE");
        }
    }
}
