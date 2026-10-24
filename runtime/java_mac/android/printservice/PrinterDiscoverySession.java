// android.printservice.PrinterDiscoverySession
//
// The session through which a PrintService reports the printers it finds. The
// spooler creates one, starts/stops discovery on it, and tracks individual
// printers; the service answers by calling addPrinters/removePrinters.
//
// The bookkeeping half of this class is FULLY REAL: the printer map, the
// tracked-printer list, the destroyed/started flags, the add/remove semantics
// (including AOSP's asymmetry -- inside a discovery period an add REPLACES an
// existing entry, outside one it only fills a GAP) all behave exactly as they
// do on a device, so a service that reads back getPrinters()/getTrackedPrinters()
// sees the right thing. What is missing is the spooler-side observer: AOSP
// pushes each change over IPC to the print UI, and there is no print UI here.
// Nothing is silently swallowed -- there is simply no consumer, and
// PrintService documents why.
//
// Mirrors frameworks/base/core/java/android/printservice/PrinterDiscoverySession.java
// (android16-release).
package android.printservice;

import android.os.CancellationSignal;
import android.print.PrinterId;
import android.print.PrinterInfo;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class PrinterDiscoverySession {

    private static final String LOG_TAG = "PrinterDiscoverySession";

    private static int sIdCounter = 0;

    private final int mId;

    private final Map<PrinterId, PrinterInfo> mPrinters =
            new LinkedHashMap<PrinterId, PrinterInfo>();

    private final List<PrinterId> mTrackedPrinters = new ArrayList<PrinterId>();

    private boolean mIsDestroyed;

    private boolean mIsDiscoveryStarted;

    public PrinterDiscoverySession() {
        mId = sIdCounter++;
    }

    /** @hide */
    public int getId() { return mId; }

    public final List<PrinterInfo> getPrinters() {
        PrintService.throwIfNotCalledOnMainThread();
        if (mIsDestroyed) {
            return Collections.emptyList();
        }
        return new ArrayList<PrinterInfo>(mPrinters.values());
    }

    /**
     * AOSP's exact semantics, and the asymmetry is deliberate on their side:
     * DURING a discovery period an added printer overwrites any existing entry
     * with the same id (that is how a status change is published); OUTSIDE one,
     * an add only fills a gap and never overwrites, so a stale entry cannot
     * clobber a fresher one that arrived while discovery was running.
     */
    public final void addPrinters(List<PrinterInfo> printers) {
        PrintService.throwIfNotCalledOnMainThread();
        if (mIsDestroyed) {
            Log.w(LOG_TAG, "Not adding printers - session destroyed.");
            return;
        }
        final int addedPrinterCount = printers.size();
        if (mIsDiscoveryStarted) {
            for (int i = 0; i < addedPrinterCount; i++) {
                PrinterInfo addedPrinter = printers.get(i);
                mPrinters.put(addedPrinter.getId(), addedPrinter);
            }
        } else {
            for (int i = 0; i < addedPrinterCount; i++) {
                PrinterInfo addedPrinter = printers.get(i);
                if (mPrinters.get(addedPrinter.getId()) == null) {
                    mPrinters.put(addedPrinter.getId(), addedPrinter);
                }
            }
        }
    }

    public final void removePrinters(List<PrinterId> printerIds) {
        PrintService.throwIfNotCalledOnMainThread();
        if (mIsDestroyed) {
            Log.w(LOG_TAG, "Not removing printers - session destroyed.");
            return;
        }
        final int removedPrinterIdCount = printerIds.size();
        for (int i = 0; i < removedPrinterIdCount; i++) {
            mPrinters.remove(printerIds.get(i));
        }
    }

    public abstract void onStartPrinterDiscovery(List<PrinterId> priorityList);

    public abstract void onStopPrinterDiscovery();

    public abstract void onValidatePrinters(List<PrinterId> printerIds);

    public abstract void onStartPrinterStateTracking(PrinterId printerId);

    /**
     * AOSP's base implementation does nothing -- a service that has no custom
     * printer icon simply never calls the callback. Empty here for the same
     * reason, NOT as a stub.
     */
    public void onRequestCustomPrinterIcon(PrinterId printerId,
            CancellationSignal cancellationSignal, CustomPrinterIconCallback callback) {
    }

    public abstract void onStopPrinterStateTracking(PrinterId printerId);

    public final List<PrinterId> getTrackedPrinters() {
        PrintService.throwIfNotCalledOnMainThread();
        if (mIsDestroyed) {
            return Collections.emptyList();
        }
        return new ArrayList<PrinterId>(mTrackedPrinters);
    }

    public abstract void onDestroy();

    public final boolean isDestroyed() {
        PrintService.throwIfNotCalledOnMainThread();
        return mIsDestroyed;
    }

    public final boolean isPrinterDiscoveryStarted() {
        PrintService.throwIfNotCalledOnMainThread();
        return mIsDiscoveryStarted;
    }

    // ---- driven by the spooler on a device; kept package-private so the
    // ---- flags stay honest rather than app-settable.

    void startPrinterDiscovery(List<PrinterId> priorityList) {
        if (mIsDestroyed) return;
        mIsDiscoveryStarted = true;
        onStartPrinterDiscovery(priorityList != null
                ? priorityList : Collections.<PrinterId>emptyList());
    }

    void stopPrinterDiscovery() {
        if (mIsDestroyed) return;
        mIsDiscoveryStarted = false;
        onStopPrinterDiscovery();
    }

    void startPrinterStateTracking(PrinterId printerId) {
        if (mIsDestroyed || mTrackedPrinters.contains(printerId)) return;
        mTrackedPrinters.add(printerId);
        onStartPrinterStateTracking(printerId);
    }

    void stopPrinterStateTracking(PrinterId printerId) {
        if (mIsDestroyed || !mTrackedPrinters.remove(printerId)) return;
        onStopPrinterStateTracking(printerId);
    }

    void destroy() {
        if (mIsDestroyed) return;
        mIsDestroyed = true;
        mIsDiscoveryStarted = false;
        mTrackedPrinters.clear();
        mPrinters.clear();
        onDestroy();
    }
}
