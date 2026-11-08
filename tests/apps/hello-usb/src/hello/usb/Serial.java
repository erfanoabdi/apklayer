package hello.usb;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import java.nio.charset.StandardCharsets;

/**
 * A USB serial port as serial apps drive one (usb-serial-for-android's CdcAcmSerialDriver): a CDC ACM device (Linux's g_serial, 0525:a4a7, which the
 * host's cdc_acm driver holds), both interfaces claimed with force (the kernel driver detached), SET_LINE_CODING 115200 8N1, DTR and RTS, a line written
 * and the far side's answer read. Prints `[usb] PASS|FAIL serial <step>` and `[usb] SERIAL DONE`.
 */
final class Serial {
    static final int VENDOR = 0x0525, PRODUCT = 0xa4a7;
    private static final int SET_LINE_CODING = 0x20, SET_CONTROL_LINE_STATE = 0x22, CLASS_OUT = 0x21;

    private Serial() {}

    static boolean acm(UsbDevice d) { return d.getVendorId() == VENDOR && d.getProductId() == PRODUCT; }

    static void exercise(UsbManager usb, UsbDevice d) {
        UsbDeviceConnection c = usb.openDevice(d);
        Main.report("serial open", c != null, "openDevice returned null");
        if (c == null) return;
        try {
            UsbInterface control = null, data = null;
            for (int i = 0; i < d.getInterfaceCount(); i++) {
                UsbInterface intf = d.getInterface(i);
                if (intf.getInterfaceClass() == UsbConstants.USB_CLASS_COMM && control == null) control = intf;
                if (intf.getInterfaceClass() == UsbConstants.USB_CLASS_CDC_DATA && data == null) data = intf;
            }
            Main.report("serial interfaces", control != null && data != null, "control=" + control + " data=" + data);
            if (control == null || data == null) return;
            Main.report("serial claim (kernel driver detached)", c.claimInterface(control, true) && c.claimInterface(data, true), "claimInterface failed");
            byte[] coding = {0x00, (byte) 0xc2, 0x01, 0x00, 0, 0, 8};  // 115200 baud, 1 stop bit, no parity, 8 data bits
            int r = c.controlTransfer(CLASS_OUT, SET_LINE_CODING, 0, control.getId(), coding, coding.length, 1000);
            int s = c.controlTransfer(CLASS_OUT, SET_CONTROL_LINE_STATE, 0x03, control.getId(), null, 0, 1000);  // DTR and RTS
            Main.report("serial line coding", r == coding.length && s == 0, "SET_LINE_CODING " + r + ", SET_CONTROL_LINE_STATE " + s);
            UsbEndpoint in = null, out = null;
            for (int i = 0; i < data.getEndpointCount(); i++) {
                UsbEndpoint e = data.getEndpoint(i);
                if (e.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) { if (e.getDirection() == UsbConstants.USB_DIR_IN) in = e; else out = e; }
            }
            byte[] line = "hello serial\n".getBytes(StandardCharsets.US_ASCII);
            int w = out == null ? -1 : c.bulkTransfer(out, line, line.length, 2000);
            Main.report("serial write", w == line.length, "wrote " + w);
            StringBuilder answer = new StringBuilder();
            byte[] buf = new byte[in == null ? 0 : in.getMaxPacketSize()];
            for (int tries = 0; in != null && tries < 20 && answer.indexOf("\n") < 0; tries++) {  // the far side answers once it read the line
                int n = c.bulkTransfer(in, buf, buf.length, 1000);
                if (n > 0) answer.append(new String(buf, 0, n, StandardCharsets.US_ASCII));
            }
            Main.report("serial read", answer.toString().trim().equals("pong: hello serial"), "read '" + answer.toString().trim() + "'");
            c.releaseInterface(data);
            c.releaseInterface(control);
        } catch (Exception failure) {
            Main.report("serial exception", false, String.valueOf(failure));
            failure.printStackTrace(System.out);
        } finally {
            c.close();
            System.out.println("[usb] SERIAL DONE");
        }
    }
}
