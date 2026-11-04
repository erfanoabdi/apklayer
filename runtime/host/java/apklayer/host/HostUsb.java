package apklayer.host;

import android.hardware.usb.UsbConfiguration;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.os.Parcelable;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The host's USB devices (sysfs, /sys/bus/usb/devices) as UsbManager's device list, read anew on each call; hubs stay out of the list as on Android.
 * Configurations, interfaces and endpoints come from the device's raw descriptors (sysfs "descriptors": the device descriptor, then every configuration's),
 * as UsbHostManager's UsbDescriptorParser builds them on a device.
 */
final class HostUsb {
    private HostUsb() {}

    private static final Path SYSFS = Path.of("/sys/bus/usb/devices");

    private static String attribute(File dir, String name) {
        try { return Files.readString(Path.of(dir.getPath(), name)).trim(); } catch (IOException none) { return null; }
    }

    private static int number(String text, int radix) {
        try { return text == null ? 0 : Integer.parseInt(text, radix); } catch (NumberFormatException bad) { return 0; }
    }

    /** The attached devices by name (/dev/bus/usb/BBB/DDD). */
    static Map<String, UsbDevice> devices() {
        Map<String, UsbDevice> found = new HashMap<>();
        File[] entries = SYSFS.toFile().listFiles();
        if (entries == null) return found;
        for (File entry : entries) {
            UsbDevice usb = device(entry);
            if (usb != null) found.put(usb.getDeviceName(), usb);
        }
        return found;
    }

    /** One sysfs device directory as a UsbDevice; null for an interface, a hub or a device gone meanwhile. */
    static UsbDevice device(File entry) {
        String vendor = attribute(entry, "idVendor");
        if (vendor == null || entry.getName().contains(":") || number(attribute(entry, "bDeviceClass"), 16) == 9) return null;  // an interface, or a hub
        int bus = number(attribute(entry, "busnum"), 10), device = number(attribute(entry, "devnum"), 10);
        UsbConfiguration[] configurations;
        try { configurations = configurations(Files.readAllBytes(Path.of(entry.getPath(), "descriptors"))); }
        catch (IOException gone) { return null; }
        return create(String.format("/dev/bus/usb/%03d/%03d", bus, device), number(vendor, 16), number(attribute(entry, "idProduct"), 16),
                number(attribute(entry, "bDeviceClass"), 16), number(attribute(entry, "bDeviceSubClass"), 16), number(attribute(entry, "bDeviceProtocol"), 16),
                attribute(entry, "manufacturer"), attribute(entry, "product"), attribute(entry, "version"), attribute(entry, "serial"), configurations);
    }

    /** Configuration (2), interface (4) and endpoint (5) descriptors in their order; others (class-specific ones) are skipped. */
    static UsbConfiguration[] configurations(byte[] raw) {
        List<UsbConfiguration> configs = new ArrayList<>();
        List<UsbInterface> interfaces = new ArrayList<>();
        List<UsbEndpoint> endpoints = new ArrayList<>();
        UsbConfiguration config = null;
        UsbInterface intf = null;
        for (int at = 0; at + 2 <= raw.length; ) {
            int length = raw[at] & 0xff, type = raw[at + 1] & 0xff;
            if (length < 2 || at + length > raw.length) break;
            if (type == 2 && length >= 9) {
                close(intf, endpoints, interfaces); close(config, interfaces);
                intf = null;
                config = new UsbConfiguration(raw[at + 5] & 0xff, null, raw[at + 7] & 0xff, raw[at + 8] & 0xff);
                configs.add(config);
            } else if (type == 4 && length >= 9 && config != null) {
                close(intf, endpoints, interfaces);
                intf = new UsbInterface(raw[at + 2] & 0xff, raw[at + 3] & 0xff, null, raw[at + 5] & 0xff, raw[at + 6] & 0xff, raw[at + 7] & 0xff);
            } else if (type == 5 && length >= 7 && intf != null) {
                endpoints.add(new UsbEndpoint(raw[at + 2] & 0xff, raw[at + 3] & 0xff, (raw[at + 4] & 0xff) | (raw[at + 5] & 0xff) << 8, raw[at + 6] & 0xff));
            }
            at += length;
        }
        close(intf, endpoints, interfaces); close(config, interfaces);
        return configs.toArray(new UsbConfiguration[0]);
    }

    private static void close(UsbInterface intf, List<UsbEndpoint> endpoints, List<UsbInterface> into) {
        if (intf == null) return;
        intf.setEndpoints(endpoints.toArray(new Parcelable[0]));
        endpoints.clear();
        into.add(intf);
    }

    private static void close(UsbConfiguration config, List<UsbInterface> interfaces) {
        if (config == null) return;
        config.setInterfaces(interfaces.toArray(new Parcelable[0]));
        interfaces.clear();
    }

    /** UsbDevice's constructor is hidden and differs between releases: fill its parameters by type and order (name, ids, class, strings, flags). */
    private static UsbDevice create(String name, int vendor, int product, int clazz, int subclass, int protocol, String manufacturer, String productName, String version, String serial,
            UsbConfiguration[] configurations) {
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
            else if (t == UsbConfiguration[].class) args[i] = configurations;
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
