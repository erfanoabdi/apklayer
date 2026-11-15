package apklayer.host;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageParser;
import android.content.res.AssetManager;
import android.content.res.ApkAssets;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.hardware.usb.DeviceFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.xmlpull.v1.XmlPullParser;

/**
 * The instance's USB service, what UsbHostManager and UsbUserPermissionManager do on a device: per package device permissions (the person's answer in the hub's
 * dialog, until the device goes), ACTION_USB_DEVICE_ATTACHED/DETACHED broadcasts from udev's events, and on an attach the activity whose
 * USB_DEVICE_ATTACHED filter and device_filter meta-data match (UsbProfileGroupSettingsManager), after the person confirms it (UsbConfirmActivity).
 */
final class HubUsb {
    private final HubDirectory directory;
    private final HubPackages packages;
    private final Path apps;
    private final Set<String> grants = ConcurrentHashMap.newKeySet();  // "package\tdevice name"
    private final ExecutorService dialogs = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "hub-usb-dialogs"); t.setDaemon(true); return t; });  // one question at a time
    private volatile Map<String, UsbDevice> attached = Map.of();

    HubUsb(HubDirectory directory, HubPackages packages, Path apps) { this.directory = directory; this.packages = packages; this.apps = apps; }

    /** hasPermission ({@code ask} false) and requestPermission ({@code ask} true: the person decides now; this call waits for the answer). */
    boolean permission(String pkg, String device, boolean ask) {
        if (grants.contains(pkg + "\t" + device)) return true;
        UsbDevice usb = attached.get(device);
        if (!ask || usb == null) return false;
        String label = HubInstaller.labelOf(pkg, pkg), product = name(usb);
        boolean yes;
        try { yes = dialogs.submit(() -> HubInstallDialog.ask("Allow " + label + " to access " + product + "?", "The app gets the USB device " + device + " until it is unplugged.", "Allow", "Deny")).get(); }
        catch (Exception interrupted) { return false; }
        if (yes && attached.containsKey(device)) grants.add(pkg + "\t" + device);
        System.out.println("[apklayer-hub] usb: " + pkg + (yes ? " may use " : " may not use ") + device);
        return yes && attached.containsKey(device);
    }

    /** The node read-write as the user, for a package that holds the permission (ParcelFileDescriptor.open; the caller closes its copy); null otherwise or without access. */
    android.os.ParcelFileDescriptor open(String pkg, String device) {
        if (!grants.contains(pkg + "\t" + device) || !attached.containsKey(device)) return null;
        try { return android.os.ParcelFileDescriptor.open(new java.io.File(device), android.os.ParcelFileDescriptor.MODE_READ_WRITE); }
        catch (IOException noAccess) { System.out.println("[apklayer-hub] usb: cannot open " + device + ": " + noAccess.getMessage()); return null; }
    }

    /** udev's usb events (`udevadm monitor`, as HostBattery's): the device list again, broadcasts for what came and went. */
    void watch() {
        attached = HostUsb.devices();  // the devices present at start: no events, as after a device's boot
        try {
            Process monitor = new ProcessBuilder(HostProcess.dying("udevadm", "monitor", "--udev", "--subsystem-match=usb")).redirectErrorStream(true).start();
            Thread thread = new Thread(() -> {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(monitor.getInputStream()))) {
                    for (String line; (line = in.readLine()) != null; ) if (line.endsWith("(usb)")) changed();
                } catch (IOException ended) { /* stopped with the JVM */ }
            }, "hub-usb");
            thread.setDaemon(true);
            thread.start();
            Runtime.getRuntime().addShutdownHook(new Thread(monitor::destroy));
        } catch (IOException noUdev) { System.out.println("[apklayer-hub] usb: no udevadm, no attach events"); }
    }

    private synchronized void changed() {
        Map<String, UsbDevice> before = attached, now = HostUsb.devices();
        attached = now;
        for (UsbDevice gone : before.values()) {
            if (now.containsKey(gone.getDeviceName())) continue;
            grants.removeIf(grant -> grant.endsWith("\t" + gone.getDeviceName()));
            System.out.println("[apklayer-hub] usb: detached " + gone.getDeviceName() + " " + name(gone));
            directory.sendSystem(new Intent(UsbManager.ACTION_USB_DEVICE_DETACHED).putExtra(UsbManager.EXTRA_DEVICE, gone), false);
        }
        for (UsbDevice added : now.values()) {
            if (before.containsKey(added.getDeviceName())) continue;
            System.out.println("[apklayer-hub] usb: attached " + added.getDeviceName() + " " + name(added));
            Intent intent = new Intent(UsbManager.ACTION_USB_DEVICE_ATTACHED).putExtra(UsbManager.EXTRA_DEVICE, added).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            directory.sendSystem(intent, false);
            dialogs.execute(() -> offer(added, intent));
        }
    }

    /** The activities that asked for this device in their manifest: one asks as UsbConfirmActivity does, several share one chooser (UsbResolverActivity); the pick gets the permission and the intent. */
    private void offer(UsbDevice device, Intent intent) {
        java.util.List<String[]> found = new java.util.ArrayList<>();  // {package, activity class}
        for (String pkg : packages.installed()) {
            PackageParser.Package parsed;
            try { parsed = packages.get(pkg); } catch (RuntimeException unreadable) { continue; }
            if (parsed == null) continue;
            for (PackageParser.Activity activity : parsed.activities) {
                if (activity.intents.stream().noneMatch(filter -> filter.hasAction(UsbManager.ACTION_USB_DEVICE_ATTACHED))) continue;
                int xml = activity.metaData == null ? 0 : activity.metaData.getInt(UsbManager.ACTION_USB_DEVICE_ATTACHED);
                if (xml != 0 && matches(pkg, xml, device)) { found.add(new String[] {pkg, activity.info.name}); break; }  // one activity per app
            }
        }
        if (found.isEmpty() || !attached.containsKey(device.getDeviceName())) return;
        String[] pick;
        if (found.size() == 1) {
            String label = HubInstaller.labelOf(found.get(0)[0], found.get(0)[0]);
            pick = HubInstallDialog.ask("Open " + label + " to handle " + name(device) + "?", "The app gets the USB device " + device.getDeviceName() + ".", "OK", "Cancel") ? found.get(0) : null;
        } else {
            String[] labels = found.stream().map(f -> HubInstaller.labelOf(f[0], f[0])).toArray(String[]::new);
            int[] picked = {0};
            int button = HubInstallDialog.choose("Open " + name(device) + " with", "The app you pick gets the USB device " + device.getDeviceName() + ".", labels, picked,
                    new String[] {"OK", "Cancel"}, new String[] {"yes", "no"});
            pick = button == 0 ? found.get(picked[0]) : null;
        }
        if (pick == null || !attached.containsKey(device.getDeviceName())) return;
        grants.add(pick[0] + "\t" + device.getDeviceName());
        System.out.println("[apklayer-hub] usb: " + device.getDeviceName() + " opens " + pick[0] + "/" + pick[1]);
        directory.startSystem(new Intent(intent).setComponent(new ComponentName(pick[0], pick[1])));
    }

    /** The package's device_filter resource: any usb-device element that matches (DeviceFilter, as UsbProfileGroupSettingsManager.packageMatchesLocked). */
    private boolean matches(String pkg, int xml, UsbDevice device) {
        try {
            AssetManager assets = new AssetManager();
            ApkAssets[] system = AssetManager.getSystem().getApkAssets();
            ApkAssets[] all = java.util.Arrays.copyOf(system, system.length + 1);
            all[system.length] = ApkAssets.loadFromPath(apps.resolve(pkg).resolve("base.apk").toString());
            assets.setApkAssets(all, false);
            Resources res = new Resources(assets, Resources.getSystem().getDisplayMetrics(), Resources.getSystem().getConfiguration());
            try (XmlResourceParser parser = res.getXml(xml)) {
                for (int event = parser.next(); event != XmlPullParser.END_DOCUMENT; event = parser.next())
                    if (event == XmlPullParser.START_TAG && "usb-device".equals(parser.getName()) && DeviceFilter.read(parser).matches(device)) return true;
            }
        } catch (Exception unreadable) { System.out.println("[apklayer-hub] usb: device filter of " + pkg + " not readable: " + unreadable); }
        return false;
    }

    private static String name(UsbDevice device) {
        return device.getProductName() != null ? device.getProductName() : String.format("USB device %04x:%04x", device.getVendorId(), device.getProductId());
    }
}
