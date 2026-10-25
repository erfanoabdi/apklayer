// android.hardware.usb.UsbManager
//
// AOSP's USB host/accessory service. Apps fetch it via getSystemService(
// USB_SERVICE) then enumerate devices. Mac.apk does not expose host USB to the
// guest, so getDeviceList() is an empty map and getAccessoryList() is null (the
// two different "nothing attached" answers AOSP itself gives) -- the honest
// "no USB peripherals" result. Mirrors
// frameworks/base/core/java/android/hardware/usb/UsbManager.java.
package android.hardware.usb;

import android.content.Context;

// AOSP-conformance (framework_lint vs android-30 stub jar): AOSP does not
// mark this final. Final-when-AOSP-is-not is the dangerous direction -- an
// app compiled against real Android that subclasses this fails to resolve
// against our jar. See docs/aosp-conformance-audit_2026-08-23.md.
public class UsbManager {

    /** AOSP broadcast/permission action constants real apps reference. */
    public static final String ACTION_USB_DEVICE_ATTACHED =
        "android.hardware.usb.action.USB_DEVICE_ATTACHED";
    public static final String ACTION_USB_DEVICE_DETACHED =
        "android.hardware.usb.action.USB_DEVICE_DETACHED";
    public static final String ACTION_USB_ACCESSORY_ATTACHED =
        "android.hardware.usb.action.USB_ACCESSORY_ATTACHED";
    public static final String ACTION_USB_ACCESSORY_DETACHED =
        "android.hardware.usb.action.USB_ACCESSORY_DETACHED";
    public static final String EXTRA_DEVICE = "device";
    public static final String EXTRA_ACCESSORY = "accessory";
    public static final String EXTRA_PERMISSION_GRANTED = "permission";

    public UsbManager() {}
    public UsbManager(Context context) {}

    /** AOSP getDeviceList(): name -> UsbDevice. Empty (no host USB exposed). */
    public java.util.HashMap<String, UsbDevice> getDeviceList() {
        return new java.util.HashMap<>();
    }

    /** AOSP getAccessoryList(): null when no accessory is attached (AOSP returns
     *  null, NOT an empty array, when none present -- mirror that exactly).
     *
     *  The RETURN TYPE is load-bearing, not cosmetic: an app compiled against the
     *  SDK emits the call site descriptor
     *  getAccessoryList()[Landroid/hardware/usb/UsbAccessory;, and JVM
     *  invokevirtual resolution matches the whole descriptor including the return
     *  type. Declaring Object[] here therefore produced a NoSuchMethodError at the
     *  call site instead of the graceful null the accessory contract promises. */
    public UsbAccessory[] getAccessoryList() {
        return null;
    }

    public boolean hasPermission(UsbDevice device) { return false; }

    /** AOSP javadoc (frameworks/base/core/java/android/hardware/usb/UsbManager.java
     *  requestPermission(UsbDevice, PendingIntent)): "Success or failure is
     *  returned via the PendingIntent pi... The following extras will be
     *  added to pi: EXTRA_DEVICE containing the device passed into this
     *  call, EXTRA_PERMISSION_GRANTED containing boolean indicating whether
     *  permission was granted by the user." That contract is unconditional --
     *  the intent is ALWAYS sent, granted or not -- so a bare no-op left any
     *  app awaiting the result (the documented async idiom: register a
     *  receiver for the pi's action, call requestPermission, wait) hanging
     *  forever. No USB host is ever exposed, so permission is always denied,
     *  but the honest denial still has to arrive. Mutate the PendingIntent's
     *  OWN wrapped Intent rather than passing an override to
     *  send(Context,int,Intent) -- that REPLACES the whole Intent including
     *  its action, so the receiver's IntentFilter (keyed on the action the
     *  app itself chose) would stop matching. Posted async (never called
     *  inline) for the same re-entrancy reason as the rest of this audit's
     *  fixes: a caller that writes state right after requestPermission()
     *  returns must not observe the callback nested inside that same call. */
    public void requestPermission(UsbDevice device, android.app.PendingIntent pi) {
        sendPermissionResult(pi, EXTRA_DEVICE, device);
    }

    /** AOSP hasPermission(UsbAccessory)/requestPermission(UsbAccessory,
     *  PendingIntent). Same descriptor-matching reason as getAccessoryList: the
     *  parameter must be the real UsbAccessory type, not Object. We hold no
     *  accessory permission because no accessory can be attached. Same
     *  always-send contract as the UsbDevice overload above. */
    public boolean hasPermission(UsbAccessory accessory) { return false; }
    public void requestPermission(UsbAccessory accessory, android.app.PendingIntent pi) {
        sendPermissionResult(pi, EXTRA_ACCESSORY, accessory);
    }

    private static void sendPermissionResult(android.app.PendingIntent pi,
            String extraKey, android.os.Parcelable extraValue) {
        if (pi == null) return;
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            android.content.Intent i = pi.getIntent();
            if (i != null) {
                i.putExtra(extraKey, extraValue);
                i.putExtra(EXTRA_PERMISSION_GRANTED, false);
            }
            try { pi.send(); } catch (android.app.PendingIntent.CanceledException ignored) {}
        });
    }

    /** AOSP openDevice(): returns null if the device cannot be opened. We never
     *  expose host USB, so there is no device to open -- honest null. */
    public UsbDeviceConnection openDevice(UsbDevice device) { return null; }

    /** AOSP openAccessory(): a ParcelFileDescriptor for reading/writing the
     *  accessory, or null if it could not be opened. No accessory can be
     *  attached here, so null is the honest answer. */
    public android.os.ParcelFileDescriptor openAccessory(UsbAccessory accessory) {
        return null;
    }

    /** AOSP UsbManager.java:1176 openAccessoryInputStream / :1196
     *  openAccessoryOutputStream (API 36, behind FLAG_ENABLE_ACCESSORY_STREAM_API).
     *  Both are declared @NonNull, so neither may answer null the way
     *  openAccessory() above does -- the stream form has no "there is nothing to
     *  open" return value at all.
     *
     *  What AOSP itself does in this state is an accident rather than a contract:
     *  openHandleForAccessory (:848) hands mService.openAccessory's result
     *  straight into the AccessoryAutoClose*Stream constructor, so a null
     *  ParcelFileDescriptor surfaces as a bare NullPointerException from inside
     *  the stream. We raise IllegalStateException instead -- same unchecked
     *  "you cannot proceed", but it names the reason, which a bare NPE from
     *  three frames deep does not.
     *
     *  Deliberately NOT an empty/discarding stream: an OutputStream that
     *  swallows writes would report success for bytes that reached no
     *  accessory, which is precisely the comfortable lie this project forbids.
     *  In practice these are unreachable -- an app opens accessory streams only
     *  after ACTION_USB_ACCESSORY_ATTACHED, which never fires because
     *  getAccessoryList() is permanently null. */
    public java.io.InputStream openAccessoryInputStream(UsbAccessory accessory) {
        throw new IllegalStateException(
                "no USB accessory is attached, so it cannot be opened for reading");
    }

    public java.io.OutputStream openAccessoryOutputStream(UsbAccessory accessory) {
        throw new IllegalStateException(
                "no USB accessory is attached, so it cannot be opened for writing");
    }
}
