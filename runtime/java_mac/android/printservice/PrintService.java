// android.printservice.PrintService
//
// The Service an app implements to BE a printer provider (Mopria, HP Print
// Service, a PDF printer, a label-printer vendor app...). The system print
// spooler binds it, drives printer discovery through it, and hands it queued
// print jobs.
//
// WHAT IS REAL HERE, AND WHAT IS NOT
// ----------------------------------
// Mac.apk has no system print spooler, so nothing ever binds a PrintService and
// none of the abstract callbacks below are ever invoked by the framework. What
// this class does provide, and provides for real, is:
//   - the abstract type + the exact SDK constants, so an app that ships a print
//     service class LOADS, LINKS and passes verification instead of dying with
//     NoClassDefFoundError the moment its manifest component is resolved;
//   - generatePrinterId(), which is pure local computation and returns exactly
//     what a device returns;
//   - attachBaseContext(), which chains to ContextWrapper so the service has a
//     working Context (see the comment on it -- getting this wrong silently
//     leaves every getSystemService/getPackageName call broken);
//   - getActivePrintJobs(), which returns the empty list -- AOSP's OWN answer
//     when no spooler client is attached, not an invented one.
// It does NOT provide: a bound IBinder (there is no IPrintService AIDL stub and
// nothing to talk to), printer discovery driven by a real spooler, or job
// delivery. android.print.PrintManager.isPrintServiceEnabled() answers false
// for the same reason, so an app that checks first is told the truth.
//
// Mirrors frameworks/base/core/java/android/printservice/PrintService.java
// (android16-release).
package android.printservice;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.Looper;
import android.print.PrinterId;

import java.util.Collections;
import java.util.List;

public abstract class PrintService extends Service {

    private static final String LOG_TAG = "PrintService";

    public static final String SERVICE_INTERFACE = "android.printservice.PrintService";

    public static final String SERVICE_META_DATA = "android.printservice";

    public static final String EXTRA_PRINT_JOB_INFO = "android.intent.extra.print.PRINT_JOB_INFO";

    public static final String EXTRA_PRINTER_INFO = "android.intent.extra.print.EXTRA_PRINTER_INFO";

    public static final String EXTRA_PRINT_DOCUMENT_INFO =
            "android.printservice.extra.PRINT_DOCUMENT_INFO";

    public static final String EXTRA_CAN_SELECT_PRINTER =
            "android.printservice.extra.CAN_SELECT_PRINTER";

    public static final String EXTRA_SELECT_PRINTER =
            "android.printservice.extra.SELECT_PRINTER";

    public PrintService() {}

    /**
     * AOSP seals this and uses it to build its ServiceHandler before chaining
     * up. We have no handler to build, but the chain-up is NOT optional:
     * ContextWrapper.attachBaseContext is what ASSIGNS baseContext, and
     * Service.macapkAttach() routes through this override, so skipping super
     * would leave the service with a null base context and break every
     * getSystemService / getPackageName / getResources call on it -- silently,
     * because nothing throws until something dereferences the result.
     */
    @Override
    protected final void attachBaseContext(Context base) {
        super.attachBaseContext(base);
    }

    /** Called when the spooler connects. Empty in AOSP too. */
    protected void onConnected() {}

    /** Called when the spooler disconnects. Empty in AOSP too. */
    protected void onDisconnected() {}

    protected abstract PrinterDiscoverySession onCreatePrinterDiscoverySession();

    protected abstract void onRequestCancelPrintJob(PrintJob printJob);

    protected abstract void onPrintJobQueued(PrintJob printJob);

    /**
     * The jobs the spooler currently has assigned to this service. AOSP returns
     * Collections.emptyList() whenever its spooler client is null; ours is
     * always null because nothing binds the service, so this is AOSP's own
     * code path rather than a substitute for it.
     */
    public final List<PrintJob> getActivePrintJobs() {
        throwIfNotCalledOnMainThread();
        return Collections.emptyList();
    }

    /**
     * Fully real: a printer id is (this service's ComponentName, localId), all
     * of which we have locally. Identical to what a device produces.
     */
    public final PrinterId generatePrinterId(String localId) {
        throwIfNotCalledOnMainThread();
        if (localId == null) {
            throw new NullPointerException("localId cannot be null");
        }
        return new PrinterId(new ComponentName(getPackageName(), getClass().getName()), localId);
    }

    /**
     * AOSP returns an IPrintService.Stub for the spooler to drive. There is no
     * spooler here and no AIDL stub to hand out, so binding is not possible and
     * null -- Service.onBind's own default, meaning "this service cannot be
     * bound" -- is the truthful answer. Sealed as AOSP seals it.
     */
    @Override
    public final IBinder onBind(Intent intent) {
        android.util.Log.i(LOG_TAG, getClass().getName()
                + ": Mac.apk has no print spooler, so this print service cannot be bound"
                + " (PrintManager.isPrintServiceEnabled() reports false for the same reason)");
        return null;
    }

    /**
     * AOSP throws IllegalAccessError off the main thread. Reproduced, including
     * the error type -- a print service that violates it fails here exactly as
     * it would on a device. Guarded so that a null main looper degrades to
     * "allow" rather than to a spurious throw.
     */
    static void throwIfNotCalledOnMainThread() {
        Looper main = Looper.getMainLooper();
        if (main != null && !main.isCurrentThread()) {
            throw new IllegalAccessError("must be called from the main thread");
        }
    }
}
