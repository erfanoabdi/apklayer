// android.print.PrintManager
//
// AOSP's printing entry point: apps fetch it via getSystemService(PRINT_SERVICE)
// and call print(jobName, adapter, attributes).
//
// WHAT ACTUALLY EXECUTES HERE, stated plainly
// -------------------------------------------
// This is NOT a stub that swallows the job. print() genuinely runs the AOSP
// adapter protocol -- onStart, onLayout, onWrite, onFinish -- against the app's
// own PrintDocumentAdapter, on the main looper, exactly as the system print
// spooler does. The app writes its document (in practice a PDF, via
// PrintedPdfDocument) into a real ParcelFileDescriptor backed by a real file,
// and that file is a real, openable PDF at the end.
//
// What is NOT here: a print-preview UI, and any binding to an
// android.printservice.PrintService (nothing in Mac.apk can host one, and
// isPrintServiceEnabled therefore honestly answers false). Because there is no
// UI, the delivery destination is chosen by environment rather than by the
// user, and this is the ONE place where we depart from AOSP's flow:
//
//   MACAPK_PRINT_DESTINATION unset (the default)
//       The document is written to <filesDir>/print/<label>-<timestamp>.pdf and
//       the job completes. This is AOSP's own built-in "Save as PDF"
//       destination, which is always offered on a device and does complete a
//       job -- so STATE_COMPLETED is not a lie, and the path is logged at INFO
//       so nothing is silently discarded. Nothing is sent to a printer, so an
//       app cannot make paper come out of the host by surprise.
//   MACAPK_PRINT_DESTINATION=<cups-queue>
//       Spooled for real through macOS's own CUPS (/usr/bin/lp), which is rung
//       1 of the host-first decision ladder. Media size, duplex mode, colour
//       mode and copy count are mapped to their IPP option names. Completes on
//       lp success; FAILS, with lp's own message as the job status, otherwise.
//   MACAPK_PRINT_DESTINATION=default
//       Same, against the CUPS default queue. If the host has no default queue
//       the job FAILS and says so -- it does not quietly fall back to a file.
//   MACAPK_PRINT_DESTINATION=none
//       Delivery is skipped and the job FAILS with "printing disabled". The
//       document is still produced, for an app that only wants the callbacks.
//
// In every path the job's real outcome is recorded in its PrintJobInfo, so
// PrintJob.isCompleted()/isFailed()/getInfo().getStatus() report what happened
// rather than a fixed answer.
//
// Mirrors frameworks/base/core/java/android/print/PrintManager.java
// (android16-release).
package android.print;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PrintManager {

    private static final String LOG_TAG = "PrintManager";

    /** @hide AOSP's key for the PrintJobInfo returned by the spooler. */
    public static final String EXTRA_PRINT_JOB = "android.print.intent.extra.EXTRA_PRINT_JOB";
    /** @hide */
    public static final String EXTRA_PRINT_DOCUMENT_ADAPTER =
            "android.print.intent.extra.EXTRA_PRINT_DOCUMENT_ADAPTER";
    /** @hide */
    public static final String EXTRA_PRINT_DIALOG_INTENT =
            "android.print.intent.extra.EXTRA_PRINT_DIALOG_INTENT";

    /** AOSP's own name for the always-present save-to-file destination. */
    private static final String SAVE_AS_PDF = "Save as PDF";

    // One registry per process, keyed by job id. AOSP keeps this in the print
    // spooler; there is no spooler here, so the manager owns it and every
    // PrintManager instance in the process sees the same jobs -- which is what
    // an app expects, since a device has one spooler per user.
    private static final Map<PrintJobId, JobRecord> sJobs =
            new LinkedHashMap<PrintJobId, JobRecord>();

    private final Context mContext;

    /** @hide Kept for callers that construct one directly. */
    public PrintManager() { this(null); }

    /** @hide */
    public PrintManager(Context context) { mContext = context; }

    /**
     * Starts a print job for the given adapter.
     *
     * Deviation from AOSP, deliberate: AOSP throws IllegalStateException unless
     * the context is an Activity. Mac.apk's getSystemService is reached through
     * ContextWrapper, which forwards to the BASE context, so the PrintManager an
     * Activity hands out is constructed with the base context and would fail
     * that check for every app. That is our context plumbing differing from
     * AOSP's, not the app misusing the API, so we log it instead of throwing.
     */
    public PrintJob print(String printJobName, PrintDocumentAdapter documentAdapter,
            PrintAttributes attributes) {
        if (printJobName == null || printJobName.isEmpty()) {
            throw new IllegalArgumentException("printJobName cannot be empty");
        }
        if (documentAdapter == null) {
            throw new IllegalArgumentException("documentAdapter cannot be null");
        }

        PrintJobInfo info = new PrintJobInfo();
        info.setId(new PrintJobId());
        info.setLabel(printJobName);
        info.setState(PrintJobInfo.STATE_CREATED);
        info.setCopies(1);
        info.setCreationTime(System.currentTimeMillis());
        info.setPages(new PageRange[] { PageRange.ALL_PAGES });
        info.setAttributes(attributes != null ? attributes : defaultAttributes());

        JobRecord record = new JobRecord(info, documentAdapter, mContext);
        synchronized (sJobs) {
            sJobs.put(info.getId(), record);
        }

        // AOSP's spooler drives the adapter ON THE APP'S MAIN THREAD, and the
        // PrintDocumentAdapter contract says its callbacks arrive there. So:
        //   - already on the main thread (the normal case -- print() must be
        //     called from an Activity) -> drive it DIRECTLY. The contract is
        //     satisfied, and the job is guaranteed to actually run rather than
        //     depending on somebody pumping the loop. A queued Runnable that is
        //     never dispatched would leave the job silently in STATE_CREATED
        //     with the adapter never called, which is exactly the silent no-op
        //     this class exists to avoid.
        //   - on another thread -> post, so the adapter still lands on the main
        //     thread as AOSP requires.
        // DEVIATION, deliberate: on the direct path print() returns after the
        // synchronous part of the flow, so an app can see an already-finished
        // job where a device would still show it running. Nothing in the API
        // promises otherwise (a device's job can finish before the app looks),
        // and an adapter that answers its callbacks asynchronously is unaffected
        // -- the state machine below is callback-driven, never blocking.
        Runnable drive = new Runnable() {
            @Override public void run() {
                // Never let a throw from the app's adapter (or from our own
                // delivery path) escape -- inside a posted Runnable an escape
                // does not just lose this job, it can stop the main loop and
                // every later job then silently stays in STATE_CREATED.
                try {
                    record.start();
                } catch (Throwable t) {
                    record.failJob("print job aborted: " + t, t);
                }
            }
        };
        Handler handler = onMainThread() ? null : mainHandler();
        if (handler != null) {
            handler.post(drive);
        } else {
            drive.run();
        }

        return new PrintJob(info, this);
    }

    /**
     * The jobs this process has started. AOSP returns the user's spooled jobs;
     * with no spooler, the process's own jobs are the complete truthful answer.
     */
    public List<PrintJob> getPrintJobs() {
        List<PrintJob> jobs = new ArrayList<PrintJob>();
        synchronized (sJobs) {
            for (JobRecord record : sJobs.values()) {
                jobs.add(new PrintJob(record.info, this));
            }
        }
        return jobs;
    }

    /**
     * Mac.apk cannot host an android.printservice.PrintService -- there is no
     * system print spooler to bind one to -- so no print service is enabled.
     * This is the honest answer, not a placeholder: an app that gates a feature
     * on it correctly takes its "no service" branch.
     */
    public boolean isPrintServiceEnabled(ComponentName service) {
        if (service == null) {
            throw new NullPointerException("service cannot be null");
        }
        return false;
    }

    /**
     * @hide
     * The custom icon a print service supplied for a printer. No print service
     * can be bound (see isPrintServiceEnabled), so there is genuinely none.
     */
    public Icon getCustomPrinterIcon(PrinterId printerId) {
        return null;
    }

    // ---- package-private, used by PrintJob --------------------------------

    PrintJobInfo getPrintJobInfo(PrintJobId printJobId) {
        if (printJobId == null) return null;
        synchronized (sJobs) {
            JobRecord record = sJobs.get(printJobId);
            return record != null ? record.info : null;
        }
    }

    void cancelPrintJob(PrintJobId printJobId) {
        JobRecord record;
        synchronized (sJobs) {
            record = sJobs.get(printJobId);
        }
        if (record != null) record.cancel();
    }

    void restartPrintJob(PrintJobId printJobId) {
        JobRecord record;
        synchronized (sJobs) {
            record = sJobs.get(printJobId);
        }
        if (record == null) return;
        // AOSP re-queues a failed job for the spooler to run again. We re-run
        // the adapter, which is the same observable effect.
        record.restart();
    }

    // ---- internals --------------------------------------------------------

    private static boolean onMainThread() {
        try {
            Looper main = Looper.getMainLooper();
            return main != null && main.isCurrentThread();
        } catch (Throwable t) {
            // No usable looper at all: treat it as "drive it here", which is
            // the only option that still runs the job.
            return true;
        }
    }

    private static Handler mainHandler() {
        try {
            Looper looper = Looper.getMainLooper();
            return looper != null ? new Handler(looper) : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * The attributes used when an app passes null. AOSP lets the user pick in
     * the print dialog and defaults to the printer's own defaults; with no
     * dialog we take the host's locale as the signal, which is the same choice
     * every desktop print stack makes: Letter in the US/Canada/Mexico, A4
     * elsewhere. Resolution is the 300dpi that CUPS itself defaults to.
     */
    private static PrintAttributes defaultAttributes() {
        String country = java.util.Locale.getDefault().getCountry();
        boolean letterCountry = "US".equals(country) || "CA".equals(country)
                || "MX".equals(country) || "PH".equals(country) || "CL".equals(country);
        PrintAttributes.MediaSize media = letterCountry
                ? PrintAttributes.MediaSize.NA_LETTER
                : PrintAttributes.MediaSize.ISO_A4;
        return new PrintAttributes.Builder()
                .setMediaSize(media)
                .setResolution(new PrintAttributes.Resolution("default", "300dpi", 300, 300))
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .setDuplexMode(PrintAttributes.DUPLEX_MODE_NONE)
                .build();
    }

    /**
     * Drives one job through AOSP's adapter protocol and records what happened.
     * All state transitions run on whichever thread the app's callback arrives
     * on; the callbacks are one-shot, as the spooler's are.
     */
    private static final class JobRecord {
        final PrintJobInfo info;
        private final PrintDocumentAdapter mAdapter;
        private final Context mContext;
        private final CancellationSignal mSignal = new CancellationSignal();

        private File mFile;
        private ParcelFileDescriptor mFd;
        private boolean mLayoutSettled;
        private boolean mWriteSettled;
        private boolean mFinished;

        JobRecord(PrintJobInfo info, PrintDocumentAdapter adapter, Context context) {
            this.info = info;
            mAdapter = adapter;
            mContext = context;
        }

        void start() {
            try {
                mAdapter.onStart();
            } catch (Throwable t) {
                failJob("onStart threw: " + t, t);
                return;
            }
            layout();
        }

        void restart() {
            if (info.getState() != PrintJobInfo.STATE_FAILED) return;
            mLayoutSettled = false;
            mWriteSettled = false;
            mFinished = false;
            info.setState(PrintJobInfo.STATE_CREATED);
            info.setStatus(null);
            start();
        }

        void cancel() {
            info.setCancelling(true);
            mSignal.cancel();
        }

        private void layout() {
            Bundle extras = new Bundle();
            // Not a preview: this run produces the real document.
            extras.putBoolean(PrintDocumentAdapter.EXTRA_PRINT_PREVIEW, false);
            try {
                mAdapter.onLayout(null, info.getAttributes(), mSignal,
                        new PrintDocumentAdapter.LayoutResultCallback() {
                            @Override
                            public void onLayoutFinished(PrintDocumentInfo docInfo,
                                    boolean changed) {
                                if (mLayoutSettled) return;
                                mLayoutSettled = true;
                                info.setDocumentInfo(docInfo);
                                info.setState(PrintJobInfo.STATE_STARTED);
                                write();
                            }

                            @Override
                            public void onLayoutFailed(CharSequence error) {
                                if (mLayoutSettled) return;
                                mLayoutSettled = true;
                                failJob("onLayout failed: " + error, null);
                            }

                            @Override
                            public void onLayoutCancelled() {
                                if (mLayoutSettled) return;
                                mLayoutSettled = true;
                                cancelJob();
                            }
                        }, extras);
            } catch (Throwable t) {
                if (!mLayoutSettled) {
                    mLayoutSettled = true;
                    failJob("onLayout threw: " + t, t);
                }
            }
        }

        private void write() {
            try {
                mFile = newDocumentFile();
                mFd = ParcelFileDescriptor.open(mFile,
                        ParcelFileDescriptor.MODE_CREATE
                                | ParcelFileDescriptor.MODE_TRUNCATE
                                | ParcelFileDescriptor.MODE_READ_WRITE);
            } catch (Throwable t) {
                failJob("could not open the document file: " + t, t);
                return;
            }

            PageRange[] pages = info.getPages();
            if (pages == null || pages.length == 0) {
                pages = new PageRange[] { PageRange.ALL_PAGES };
            }

            try {
                mAdapter.onWrite(pages, mFd, mSignal,
                        new PrintDocumentAdapter.WriteResultCallback() {
                            @Override
                            public void onWriteFinished(PageRange[] written) {
                                if (mWriteSettled) return;
                                mWriteSettled = true;
                                if (written != null && written.length > 0) {
                                    info.setPages(written);
                                }
                                closeFd();
                                deliver();
                            }

                            @Override
                            public void onWriteFailed(CharSequence error) {
                                if (mWriteSettled) return;
                                mWriteSettled = true;
                                closeFd();
                                failJob("onWrite failed: " + error, null);
                            }

                            @Override
                            public void onWriteCancelled() {
                                if (mWriteSettled) return;
                                mWriteSettled = true;
                                closeFd();
                                cancelJob();
                            }
                        });
            } catch (Throwable t) {
                if (!mWriteSettled) {
                    mWriteSettled = true;
                    closeFd();
                    failJob("onWrite threw: " + t, t);
                }
            }
        }

        private void deliver() {
            long size = (mFile != null && mFile.exists()) ? mFile.length() : 0L;
            PrintDocumentInfo docInfo = info.getDocumentInfo();
            if (docInfo != null) docInfo.setDataSize(size);

            if (size == 0L) {
                failJob("the adapter wrote 0 bytes to the print document", null);
                return;
            }

            String destination = System.getenv("MACAPK_PRINT_DESTINATION");

            if ("none".equalsIgnoreCase(destination)) {
                info.setPrinterName("none");
                failJob("printing disabled by MACAPK_PRINT_DESTINATION=none; the "
                        + size + "-byte document is at " + mFile.getAbsolutePath(), null);
                return;
            }

            if (destination == null || destination.isEmpty()) {
                // AOSP's built-in save-to-file destination.
                info.setPrinterName(SAVE_AS_PDF);
                info.setState(PrintJobInfo.STATE_COMPLETED);
                info.setStatus("Saved to " + mFile.getAbsolutePath());
                logi("print job \"" + info.getLabel() + "\" saved " + size
                        + " bytes to " + mFile.getAbsolutePath()
                        + " (set MACAPK_PRINT_DESTINATION=<cups queue> to spool it instead)");
                finish();
                return;
            }

            spool(destination, size);
        }

        /** Hands the document to macOS's own CUPS via /usr/bin/lp. */
        private void spool(String destination, long size) {
            String queue = destination;
            if ("default".equalsIgnoreCase(destination)) {
                queue = cupsDefaultQueue();
                if (queue == null) {
                    failJob("MACAPK_PRINT_DESTINATION=default but the host has no CUPS "
                            + "default queue (lpstat -d); the document is at "
                            + mFile.getAbsolutePath(), null);
                    return;
                }
            }
            info.setPrinterName(queue);

            List<String> cmd = new ArrayList<String>();
            cmd.add("/usr/bin/lp");
            cmd.add("-d");
            cmd.add(queue);
            cmd.add("-t");
            cmd.add(info.getLabel());
            if (info.getCopies() > 1) {
                cmd.add("-n");
                cmd.add(Integer.toString(info.getCopies()));
            }
            PrintAttributes attrs = info.getAttributes();
            if (attrs != null) {
                // IPP option names, which is what CUPS speaks. The media keyword
                // is the PWG name; the AOSP id is close enough for the standard
                // sizes and CUPS resolves unknown names to the queue default
                // rather than failing.
                PrintAttributes.MediaSize media = attrs.getMediaSize();
                if (media != null) {
                    String spec = cupsMediaSpec(media);
                    if (spec != null) { cmd.add("-o"); cmd.add("media=" + spec); }
                }
                switch (attrs.getDuplexMode()) {
                    case PrintAttributes.DUPLEX_MODE_LONG_EDGE:
                        cmd.add("-o"); cmd.add("sides=two-sided-long-edge"); break;
                    case PrintAttributes.DUPLEX_MODE_SHORT_EDGE:
                        cmd.add("-o"); cmd.add("sides=two-sided-short-edge"); break;
                    case PrintAttributes.DUPLEX_MODE_NONE:
                        cmd.add("-o"); cmd.add("sides=one-sided"); break;
                    default: break;
                }
                switch (attrs.getColorMode()) {
                    case PrintAttributes.COLOR_MODE_MONOCHROME:
                        cmd.add("-o"); cmd.add("print-color-mode=monochrome"); break;
                    case PrintAttributes.COLOR_MODE_COLOR:
                        cmd.add("-o"); cmd.add("print-color-mode=color"); break;
                    default: break;
                }
            }
            cmd.add(mFile.getAbsolutePath());

            info.setState(PrintJobInfo.STATE_QUEUED);
            String output;
            int rc;
            try {
                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.redirectErrorStream(true);
                Process proc = pb.start();
                output = readAll(proc.getInputStream());
                rc = proc.waitFor();
            } catch (Throwable t) {
                // ONLY the lp invocation is inside the try. An earlier revision
                // wrapped the success path too, so a throw from the LOG call
                // after a successful spool was reported as "could not run lp"
                // and turned a completed job into a failed one.
                failJob("could not run /usr/bin/lp: " + t
                        + "; the document is at " + mFile.getAbsolutePath(), t);
                return;
            }

            if (rc == 0) {
                info.setState(PrintJobInfo.STATE_COMPLETED);
                info.setStatus(output.trim().isEmpty() ? "queued" : output.trim());
                logi("print job \"" + info.getLabel() + "\" spooled " + size
                        + " bytes to CUPS queue \"" + queue + "\": " + output.trim());
                finish();
            } else {
                failJob("lp exited " + rc + ": " + output.trim()
                        + "; the document is at " + mFile.getAbsolutePath(), null);
            }
        }

        private static String cupsDefaultQueue() {
            try {
                ProcessBuilder pb = new ProcessBuilder("/usr/bin/lpstat", "-d");
                pb.redirectErrorStream(true);
                Process proc = pb.start();
                String out = readAll(proc.getInputStream());
                proc.waitFor();
                // "system default destination: <name>" or "no system default destination"
                int colon = out.indexOf(':');
                if (colon >= 0) {
                    String name = out.substring(colon + 1).trim();
                    if (!name.isEmpty()) return name;
                }
            } catch (Throwable ignored) {
            }
            return null;
        }

        /**
         * The CUPS -o media= value for a MediaSize.
         *
         * We pass EXPLICIT DIMENSIONS (CUPS's documented
         * "Custom.WIDTHxHEIGHTunits" form) rather than a media NAME, and that is
         * a deliberate choice: mapping AOSP's media ids onto PWG/CUPS media
         * names needs a name table we have no authoritative source for, and a
         * name that is subtly wrong does not fail -- it silently prints at the
         * wrong page geometry, which is the exact failure this whole file is
         * written to avoid. Dimensions taken straight from the AOSP mils cannot
         * be wrong that way. If a queue cannot do custom sizes it substitutes
         * its own default, and the PDF the app produced still carries the right
         * page box because PrintedPdfDocument sized it from these same mils.
         *
         * Returns null for the orientation-only UNKNOWN_* sizes (whose
         * dimensions are 1 x Integer.MAX_VALUE and are documented as not to be
         * used), leaving the queue's default in place.
         */
        private static String cupsMediaSpec(PrintAttributes.MediaSize media) {
            int w = media.getWidthMils();
            int h = media.getHeightMils();
            // No real medium is over 200 inches; that range is the UNKNOWN_*
            // sentinels, which AOSP itself says carry no usable dimensions.
            if (w <= 0 || h <= 0 || w > 200000 || h > 200000) return null;
            return "Custom." + milsToInches(w) + "x" + milsToInches(h) + "in";
        }

        /** Mils to a trimmed decimal inch string; exact, no rounding beyond 3dp. */
        private static String milsToInches(int mils) {
            String s = java.math.BigDecimal.valueOf(mils, 3)
                    .stripTrailingZeros().toPlainString();
            return s;
        }

        private File newDocumentFile() throws java.io.IOException {
            File base = null;
            if (mContext != null) {
                try { base = mContext.getFilesDir(); } catch (Throwable ignored) { }
            }
            if (base == null) {
                base = new File(System.getProperty("java.io.tmpdir", "/tmp"));
            }
            File dir = new File(base, "print");
            if (!dir.isDirectory() && !dir.mkdirs()) {
                throw new java.io.IOException("could not create " + dir);
            }
            String label = info.getLabel() == null ? "document" : info.getLabel();
            StringBuilder safe = new StringBuilder();
            for (int i = 0; i < label.length() && safe.length() < 64; i++) {
                char c = label.charAt(i);
                safe.append(Character.isLetterOrDigit(c) || c == '-' || c == '_' ? c : '_');
            }
            if (safe.length() == 0) safe.append("document");
            return new File(dir, safe + "-" + System.currentTimeMillis() + ".pdf");
        }

        /**
         * Removes the document file when it is empty. A cancelled or
         * failed-before-write job otherwise leaves a 0-byte .pdf behind on every
         * attempt (measured: write-cancelled produced one). A file with CONTENT
         * is never removed -- the failure status names its path, and deleting it
         * would throw away the app's work.
         */
        private void discardEmptyFile() {
            if (mFile != null && mFile.isFile() && mFile.length() == 0L) {
                if (!mFile.delete()) {
                    logw("could not remove the empty print document "
                            + mFile.getAbsolutePath(), null);
                }
            }
        }

        private void closeFd() {
            if (mFd != null) {
                try { mFd.close(); } catch (Throwable ignored) { }
                mFd = null;
            }
        }

        void failJob(String reason, Throwable t) {
            closeFd();
            discardEmptyFile();
            info.setState(PrintJobInfo.STATE_FAILED);
            info.setStatus(reason);
            logw("print job \"" + info.getLabel() + "\" failed: " + reason, t);
            finish();
        }

        private void cancelJob() {
            closeFd();
            discardEmptyFile();
            info.setCancelling(false);
            info.setState(PrintJobInfo.STATE_CANCELED);
            logi("print job \"" + info.getLabel() + "\" was cancelled by the adapter");
            finish();
        }

        private void finish() {
            if (mFinished) return;
            mFinished = true;
            try {
                mAdapter.onFinish();
            } catch (Throwable t) {
                logw("onFinish threw", t);
            }
        }

        /**
         * Logging must never be able to change the job's outcome. An earlier
         * revision called Log directly from deliver(): when the log write threw
         * (it can -- Log routes to a NATIVE sink), the throw escaped after the
         * state had been set but BEFORE finish(), so the app's onFinish() was
         * never called and, inside the posted Runnable, the escape took the
         * main looper down with it and every later job silently stayed in
         * STATE_CREATED. Found by driving four jobs through the real path.
         */
        private static void logi(String msg) {
            try { Log.i(LOG_TAG, msg); } catch (Throwable ignored) { }
        }

        private static void logw(String msg, Throwable t) {
            try {
                if (t != null) Log.w(LOG_TAG, msg, t); else Log.w(LOG_TAG, msg);
            } catch (Throwable ignored) { }
        }

        private static String readAll(java.io.InputStream in) throws java.io.IOException {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        }
    }
}
