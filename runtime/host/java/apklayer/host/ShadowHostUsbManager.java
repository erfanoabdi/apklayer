package apklayer.host;

import android.app.PendingIntent;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.util.HashMap;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowUsbManager;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

/**
 * UsbManager on the host (no USB HAL, no UsbService): the device list from sysfs (HostUsb), permissions from the instance's hub (HubUsb: the person's answer),
 * openDevice on the device node itself: a real UsbDeviceConnection over libusbhost (usbdevfs ioctls), so its file descriptor serves native code too.
 * Robolectric's test helpers (addOrUpdateUsbDevice, ports, accessories) stay.
 */
@Implements(value = UsbManager.class, isInAndroidSdk = false)
public class ShadowHostUsbManager extends ShadowUsbManager {
    @Implementation @Override protected HashMap<String, UsbDevice> getDeviceList() {
        HashMap<String, UsbDevice> devices = new HashMap<>(HostUsb.devices());
        devices.putAll(super.getDeviceList());
        return devices;
    }

    @Implementation @Override protected boolean hasPermission(UsbDevice device) {
        return super.hasPermission(device) || (HubClient.connected() && HubClient.current().usbPermission(device.getDeviceName(), false));
    }

    /** As UsbUserPermissionManager.requestPermission: the answer (an immediate yes when already granted) goes to the PendingIntent with the device and EXTRA_PERMISSION_GRANTED. */
    @Implementation protected void requestPermission(UsbDevice device, PendingIntent pi) {
        Thread asking = new Thread(() -> {
            boolean granted = super.hasPermission(device) || (HubClient.connected() && HubClient.current().usbPermission(device.getDeviceName(), true));
            try { pi.send(RuntimeEnvironment.getApplication(), 0, new Intent().putExtra(UsbManager.EXTRA_DEVICE, device).putExtra(UsbManager.EXTRA_PERMISSION_GRANTED, granted)); }
            catch (PendingIntent.CanceledException cancelled) { System.out.println("[apklayer] usb: the permission result's PendingIntent was cancelled"); }
        }, "usb-permission");
        asking.setDaemon(true);
        asking.start();
    }

    /** The node read-write (the person's own access: uaccess or a udev rule), handed to UsbDeviceConnection.open as UsbService does; null without permission or access. */
    @Implementation @Override protected UsbDeviceConnection openDevice(UsbDevice device) {
        if (!hasPermission(device)) {
            System.out.println("[apklayer] usb: no permission for " + device.getDeviceName() + " (UsbManager.requestPermission first)");
            return null;
        }
        try (ParcelFileDescriptor pfd = ParcelFileDescriptor.open(new File(device.getDeviceName()), ParcelFileDescriptor.MODE_READ_WRITE)) {
            UsbDeviceConnection connection = new UsbDeviceConnection(device);
            boolean opened = ReflectionHelpers.callInstanceMethod(connection, "open", ClassParameter.from(String.class, device.getDeviceName()),
                    ClassParameter.from(ParcelFileDescriptor.class, pfd), ClassParameter.from(android.content.Context.class, RuntimeEnvironment.getApplication()));
            return opened ? connection : null;
        } catch (java.io.IOException noAccess) {
            System.out.println("[apklayer] usb: cannot open " + device.getDeviceName() + ": " + noAccess.getMessage() + " (the user needs read-write access: docs/INSTALL.md, USB devices)");
            return null;
        }
    }
}
