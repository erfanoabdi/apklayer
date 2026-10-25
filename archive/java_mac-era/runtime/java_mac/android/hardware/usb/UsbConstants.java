// android.hardware.usb.UsbConstants
//
// Pure constant container mirrored from the USB spec / AOSP
// frameworks/base/core/java/android/hardware/usb/UsbConstants.java. No
// hardware is required for these to be correct -- apps reference them when
// inspecting (the always-empty) device list, so the literal values must
// match AOSP exactly or descriptor parsing diverges.
package android.hardware.usb;

public final class UsbConstants {
    public static final int USB_DIR_OUT = 0;
    public static final int USB_DIR_IN = 0x80;

    public static final int USB_TYPE_MASK = (0x03 << 5);
    public static final int USB_TYPE_STANDARD = (0x00 << 5);
    public static final int USB_TYPE_CLASS = (0x01 << 5);
    public static final int USB_TYPE_VENDOR = (0x02 << 5);
    public static final int USB_TYPE_RESERVED = (0x03 << 5);

    public static final int USB_ENDPOINT_NUMBER_MASK = 0x0f;
    public static final int USB_ENDPOINT_DIR_MASK = 0x80;

    public static final int USB_ENDPOINT_XFERTYPE_MASK = 0x03;
    public static final int USB_ENDPOINT_XFER_CONTROL = 0;
    public static final int USB_ENDPOINT_XFER_ISOC = 1;
    public static final int USB_ENDPOINT_XFER_BULK = 2;
    public static final int USB_ENDPOINT_XFER_INT = 3;

    public static final int USB_ENDPOINT_DIR_OUT = 0;
    public static final int USB_ENDPOINT_DIR_IN = 0x80;

    public static final int USB_CLASS_PER_INTERFACE = 0;
    public static final int USB_CLASS_AUDIO = 1;
    public static final int USB_CLASS_COMM = 2;
    public static final int USB_CLASS_HID = 3;
    public static final int USB_CLASS_PHYSICA = 5;
    public static final int USB_CLASS_STILL_IMAGE = 6;
    public static final int USB_CLASS_PRINTER = 7;
    public static final int USB_CLASS_MASS_STORAGE = 8;
    public static final int USB_CLASS_HUB = 9;
    public static final int USB_CLASS_CDC_DATA = 0x0a;
    public static final int USB_CLASS_CSCID = 0x0b;
    public static final int USB_CLASS_CONTENT_SEC = 0x0d;
    public static final int USB_CLASS_VIDEO = 0x0e;
    public static final int USB_CLASS_WIRELESS_CONTROLLER = 0xe0;
    public static final int USB_CLASS_MISC = 0xef;
    public static final int USB_CLASS_APP_SPEC = 0xfe;
    public static final int USB_CLASS_VENDOR_SPEC = 0xff;

    public static final int USB_INTERFACE_SUBCLASS_BOOT = 1;
    public static final int USB_SUBCLASS_VENDOR_SPEC = 0xff;

    // AOSP declares NO constructor on this class, so javac synthesises the
    // implicit PUBLIC one -- and it is part of the published surface:
    // android16 current.txt line 18921 carries `ctor public UsbConstants();`.
    // A private ctor here (the natural instinct for a constants holder) is
    // therefore an API divergence, not a tidy-up: `new UsbConstants()` compiles
    // against the SDK and would have failed to resolve against our jar.
    public UsbConstants() {}
}
