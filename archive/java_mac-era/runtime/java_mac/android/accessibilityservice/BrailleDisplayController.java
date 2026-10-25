package android.accessibilityservice;

import android.bluetooth.BluetoothDevice;
import android.hardware.usb.UsbDevice;

import java.io.IOException;
import java.util.concurrent.Executor;

/**
 * API 35+ interface an accessibility service uses to talk HID to a connected
 * Braille display over Bluetooth or USB.
 *
 * This is an INTERFACE in AOSP, and the platform supplies the implementation
 * via AccessibilityService.getBrailleDisplayController(). Mac.apk has no HID
 * transport for a guest, so our implementation reports every connect attempt as
 * FLAG_ERROR_BRAILLE_DISPLAY_NOT_FOUND -- an outcome AOSP produces whenever the
 * named device is not a Braille display, so a service's own error path runs
 * instead of a crash.
 *
 * Constant values transcribed verbatim from /tmp/aosp_api/android16.txt
 * (signature format 2.0): FLAG_ERROR_CANNOT_ACCESS=1 (0x1),
 * FLAG_ERROR_BRAILLE_DISPLAY_NOT_FOUND=2 (0x2).
 *
 * Source: frameworks/base/core/java/android/accessibilityservice/BrailleDisplayController.java
 */
public interface BrailleDisplayController {

    void connect(BluetoothDevice bluetoothDevice, BrailleDisplayCallback callback);

    void connect(BluetoothDevice bluetoothDevice, Executor callbackExecutor,
            BrailleDisplayCallback callback);

    void connect(UsbDevice usbDevice, BrailleDisplayCallback callback);

    void connect(UsbDevice usbDevice, Executor callbackExecutor,
            BrailleDisplayCallback callback);

    boolean isConnected();

    void write(byte[] buffer) throws IOException;

    void disconnect();

    interface BrailleDisplayCallback {
        int FLAG_ERROR_CANNOT_ACCESS = 1;
        int FLAG_ERROR_BRAILLE_DISPLAY_NOT_FOUND = 2;

        void onConnected(byte[] hidDescriptor);

        void onConnectionFailed(int error);

        void onInput(byte[] input);

        void onDisconnected();
    }
}
