package hello.usb;

import android.content.Context;
import android.content.res.XmlResourceParser;
import android.hardware.usb.UsbConfiguration;
import android.hardware.usb.UsbDevice;
import java.lang.reflect.Method;
import org.xmlpull.v1.XmlPullParser;

/** device_filter matching on interfaces, with no hardware: a fake descriptor (a mass-storage interface under a device of class 0) built by the host's own parser, each element of res/xml/interface_filter.xml read by the framework's DeviceFilter. */
final class Filter {
    private Filter() {}

    /** The host's own class: not on the app's loader, found through the others. */
    private static Class<?> hostUsb() throws ClassNotFoundException {
        for (ClassLoader loader : new ClassLoader[] {Thread.currentThread().getContextClassLoader(), ClassLoader.getSystemClassLoader(), Context.class.getClassLoader()}) {
            try { if (loader != null) return Class.forName("apklayer.host.HostUsb", false, loader); } catch (ClassNotFoundException next) { }
        }
        throw new ClassNotFoundException("apklayer.host.HostUsb");
    }

    static void run(Context context) {
        try {
            byte[] raw = {9, 2, 32, 0, 1, 1, 0, (byte) 0x80, 50,  9, 4, 0, 0, 2, 8, 6, 0x50, 0,  7, 5, (byte) 0x81, 2, 0, 2, 0,  7, 5, 2, 2, 0, 2, 0};
            Class<?> host = hostUsb();
            Method configurations = host.getDeclaredMethod("configurations", byte[].class);
            configurations.setAccessible(true);
            Method create = host.getDeclaredMethod("create", String.class, int.class, int.class, int.class, int.class, int.class, String.class, String.class, String.class, String.class, UsbConfiguration[].class);
            create.setAccessible(true);
            UsbDevice device = (UsbDevice) create.invoke(null, "/dev/bus/usb/001/099", 0x1234, 0x5678, 0, 0, 0, "m", "p", "1", "s", configurations.invoke(null, (Object) raw));
            boolean[] want = {true, true, false, false, true};
            Class<?> filter = Class.forName("android.hardware.usb.DeviceFilter");
            Method matches = filter.getMethod("matches", UsbDevice.class), read = filter.getMethod("read", XmlPullParser.class);
            int i = 0;
            boolean ok = device.getInterfaceCount() == 1 && device.getInterface(0).getInterfaceClass() == 8 && device.getInterface(0).getEndpointCount() == 2;
            try (XmlResourceParser xml = context.getResources().getXml(context.getResources().getIdentifier("interface_filter", "xml", context.getPackageName()))) {
                for (int event = xml.next(); event != XmlPullParser.END_DOCUMENT; event = xml.next()) {
                    if (event != XmlPullParser.START_TAG || !"usb-device".equals(xml.getName())) continue;
                    boolean got = (Boolean) matches.invoke(read.invoke(null, xml), device);
                    if (got != want[i]) { System.out.println("[usb] interface filter element " + i + " matched=" + got); ok = false; }
                    i++;
                }
            }
            Main.report("interface filter", ok && i == want.length, "elements " + i + ", interfaces " + device.getInterfaceCount());
        } catch (Throwable failure) { Main.report("interface filter", false, failure.toString()); }
    }
}
