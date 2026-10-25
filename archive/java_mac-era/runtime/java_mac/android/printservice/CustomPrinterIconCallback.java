// android.printservice.CustomPrinterIconCallback
//
// Handed to PrinterDiscoverySession.onRequestCustomPrinterIcon so a print
// service can deliver a printer's icon asynchronously.
//
// AOSP forwards the icon over IPC to the spooler and returns false when that
// call fails. There is no spooler in Mac.apk, so there is nowhere to deliver
// to: the callback records the icon (so the service can read it back and so a
// second call is correctly rejected) and returns FALSE, which is precisely
// AOSP's "the icon could not be delivered" answer. Returning true would be the
// lie -- it would tell the service the print UI now shows its icon.
//
// Mirrors frameworks/base/core/java/android/printservice/CustomPrinterIconCallback.java
// (android16-release).
package android.printservice;

import android.graphics.drawable.Icon;
import android.print.PrinterId;

public final class CustomPrinterIconCallback {

    private final PrinterId mPrinterId;

    private Icon mIcon;
    private boolean mCalled;

    /** @hide */
    public CustomPrinterIconCallback(PrinterId printerId) {
        mPrinterId = printerId;
    }

    /** @hide The printer this callback delivers an icon for. */
    public PrinterId getPrinterId() { return mPrinterId; }

    /** @hide The icon the service supplied, or null if it never did. */
    public Icon getIcon() { return mIcon; }

    public boolean onCustomPrinterIconLoaded(Icon icon) {
        if (mCalled) {
            return false;
        }
        mCalled = true;
        mIcon = icon;
        // No spooler to deliver to -- see the class comment. AOSP returns false
        // on a failed delivery, so false is the faithful answer here.
        return false;
    }
}
