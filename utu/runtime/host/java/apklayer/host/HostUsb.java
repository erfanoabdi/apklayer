package apklayer.host;

import android.app.Application;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.robolectric.Shadows;

/** The host's USB devices (sysfs, /sys/bus/usb/devices) as UsbManager's device list; hubs stay out of the list as on Android. A snapshot at start. */
final class HostUsb {
    private HostUsb() {}

    private static String attribute(File dir, String name) {
        try { return Files.readString(Path.of(dir.getPath(), name)).trim(); } catch (IOException none) { return null; }
    }

    private static int number(String text, int radix) {
        try { return text == null ? 0 : Integer.parseInt(text, radix); } catch (NumberFormatException bad) { return 0; }
    }

    static void install(Application app) {
        UsbManager manager = app.getSystemService(UsbManager.class);
        File[] entries = new File("/sys/bus/usb/devices").listFiles();
        if (manager == null || entries == null) return;
        int count = 0;
        for (File entry : entries) {
            String vendor = attribute(entry, "idVendor");
            if (vendor == null || entry.getName().contains(":") || number(attribute(entry, "bDeviceClass"), 16) == 9) continue;  // an interface, or a hub
            int bus = number(attribute(entry, "busnum"), 10), device = number(attribute(entry, "devnum"), 10);
            UsbDevice usb = create(String.format("/dev/bus/usb/%03d/%03d", bus, device), number(vendor, 16), number(attribute(entry, "idProduct"), 16),
                    number(attribute(entry, "bDeviceClass"), 16), number(attribute(entry, "bDeviceSubClass"), 16), number(attribute(entry, "bDeviceProtocol"), 16),
                    attribute(entry, "manufacturer"), attribute(entry, "product"), attribute(entry, "version"), attribute(entry, "serial"));
            if (usb == null) return;
            Shadows.shadowOf(manager).addOrUpdateUsbDevice(usb, true);
            count++;
        }
        System.out.println("[apklayer] usb: " + count + " devices");
    }

    /** UsbDevice's constructor is hidden and differs between releases: fill its parameters by type and order (name, ids, class, strings, flags). */
    private static UsbDevice create(String name, int vendor, int product, int clazz, int subclass, int protocol, String manufacturer, String productName, String version, String serial) {
        Constructor<?> best = null;
        for (Constructor<?> c : UsbDevice.class.getDeclaredConstructors()) if (best == null || c.getParameterCount() > best.getParameterCount()) best = c;
        if (best == null) return null;
        int[] ints = {vendor, product, clazz, subclass, protocol};
        String[] strings = {name, manufacturer, productName, version};
        Object[] args = new Object[best.getParameterCount()];
        int i = 0, s = 0, n = 0;
        for (Parameter p : best.getParameters()) {
            Class<?> t = p.getType();
            if (t == String.class) args[i] = s < strings.length ? strings[s++] : null;
            else if (t == int.class) args[i] = n < ints.length ? ints[n++] : 0;
            else if (t == boolean.class) args[i] = false;
            else if (t.isArray()) args[i] = java.lang.reflect.Array.newInstance(t.getComponentType(), 0);
            else if (t == android.hardware.usb.IUsbSerialReader.class) args[i] = new android.hardware.usb.IUsbSerialReader.Stub() {  // the serial number, read on demand
                @Override public String getSerial(String packageName) { return serial == null ? "" : serial; }
            };
            else args[i] = null;
            i++;
        }
        try { best.setAccessible(true); return (UsbDevice) best.newInstance(args); }
        catch (ReflectiveOperationException failure) { System.out.println("[apklayer] usb: cannot build a UsbDevice: " + (failure.getCause() != null ? failure.getCause() : failure)); return null; }
    }
}
