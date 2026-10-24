package android.renderscript;

import android.content.Context;

import java.util.HashMap;

/**
 * RenderScript context.
 *
 * WHAT THIS ACTUALLY IS ON MAC.APK. RenderScript's compute backend on a device
 * is a native driver: app-authored .rs kernels are compiled by slang to LLVM
 * bitcode and JIT-ed by bcc, and the built-in intrinsics run on GPU, DSP or the
 * CPU reference path depending on the SoC. Google removed the GPU/DSP paths and
 * then deprecated the whole API at API 31; current AOSP services the intrinsics
 * from the CPU reference implementation in frameworks/rs/cpu_ref.
 *
 * Mac.apk implements that same CPU reference behaviour in Java, over real
 * Allocation bytes. So:
 *
 *   - The DATA MODEL is real: Element, Type and Allocation carry actual bytes
 *     with the platform's layout rules, and every copy*() method moves them.
 *   - The INTRINSICS are real: Blur, ColorMatrix, LUT, 3DLUT, Blend,
 *     Convolve3x3/5x5, YuvToRGB, Resize and Histogram compute their documented
 *     output. That is the part apps overwhelmingly use, and returning the input
 *     unprocessed would be a silent lie that surfaces as a garbled image far
 *     from here.
 *   - App-authored KERNELS are not real and cannot be: ScriptC needs an LLVM
 *     bitcode engine we do not have. It throws RSDriverException, which is a
 *     failure shape the platform already defines, instead of pretending.
 *
 * The context object itself carries the Element singleton cache (AOSP keeps one
 * cached Element per builtin type per context, and Element.isCompatible leans on
 * that identity), the handlers, and the alive flag.
 *
 * Source: frameworks/rs/java/android/renderscript/RenderScript.java
 * https://developer.android.com/reference/android/renderscript/RenderScript
 */
public class RenderScript {

    /** No special context behaviour requested. */
    public static final int CREATE_FLAG_NONE = 0x0000;
    /** Prefer low latency over throughput. */
    public static final int CREATE_FLAG_LOW_LATENCY = 0x0002;
    /** Prefer low power over throughput. */
    public static final int CREATE_FLAG_LOW_POWER = 0x0004;
    /** @hide Wait for a debugger to attach. */
    public static final int CREATE_FLAG_WAIT_FOR_ATTACH = 0x0008;

    /**
     * Size of a native pointer in the RenderScript driver. Mac.apk is
     * 64-bit-only (Apple Silicon), so this is 8, and the opaque RS_* Element
     * types are 32 bytes rather than 4. Element's constructor keys off it.
     */
    static final int sPointerSize = 8;

    /** AOSP pins this at 1; getMinorVersion() reports it. */
    static final long sMinorVersion = 1;

    public enum ContextType {
        /** Normal RenderScript context, no extra debug or profiling. */
        NORMAL(0),
        /** Context that checks for errors at runtime. */
        DEBUG(1),
        /** Context that collects performance metrics. */
        PROFILE(2);

        int mID;

        ContextType(int id) {
            mID = id;
        }
    }

    public enum Priority {
        /** Lowest thread priority. */
        LOW(15),
        /** Normal thread priority. */
        NORMAL(-8);

        int mID;

        Priority(int id) {
            mID = id;
        }
    }

    /**
     * Base class for an app's runtime error handler. AOSP fills the fields and
     * calls run() from the message thread when the driver reports an error.
     * Nothing in Mac.apk reports asynchronous driver errors (failures are
     * thrown synchronously instead), so an installed handler is simply never
     * invoked, which is the same as a healthy device run.
     */
    public static class RSErrorHandler implements Runnable {
        protected String mErrorMessage;
        protected int mErrorNum;

        public void run() {
        }
    }

    /**
     * Base class for an app's script-to-Java message handler. Only ScriptC
     * kernels can call rsSendToClient, and Mac.apk cannot execute those, so
     * this is likewise never invoked.
     */
    public static class RSMessageHandler implements Runnable {
        protected int[] mData;
        protected int mID;
        protected int mLength;

        public void run() {
        }
    }

    private Context mApplicationContext;
    private boolean mAlive = true;
    private RSMessageHandler mMessageCallback = new RSMessageHandler();
    private RSErrorHandler mErrorCallback = new RSErrorHandler();

    /**
     * Per-context cache of the builtin Elements. AOSP holds one field per
     * builtin (mElement_U8, mElement_F32_4, ...) so that repeated
     * Element.U8_4(rs) calls return the SAME object; a map keyed by the same
     * names is the identical contract with less boilerplate. It matters because
     * Element.isCompatible short-circuits on equals(), and equals() compares
     * object handles.
     */
    final HashMap<String, Element> mElementCache = new HashMap<String, Element>();

    protected RenderScript(Context ctx) {
        if (ctx != null) {
            mApplicationContext = ctx.getApplicationContext();
        }
    }

    protected RenderScript() {
        this(null);
    }

    public static RenderScript create(Context ctx) {
        return create(ctx, ContextType.NORMAL);
    }

    public static RenderScript create(Context ctx, ContextType ct) {
        return create(ctx, ct, CREATE_FLAG_NONE);
    }

    public static RenderScript create(Context ctx, ContextType ct, int flags) {
        if ((flags & ~(CREATE_FLAG_LOW_LATENCY | CREATE_FLAG_LOW_POWER
                       | CREATE_FLAG_WAIT_FOR_ATTACH)) != 0) {
            throw new RSIllegalArgumentException("Invalid flags passed.");
        }
        return new RenderScript(ctx);
    }

    /**
     * AOSP hides this overload but the v8 support library and a fair amount of
     * copied sample code still calls it, so it stays.
     */
    public static RenderScript create(Context ctx, int sdkVersion) {
        return create(ctx, sdkVersion, ContextType.NORMAL, CREATE_FLAG_NONE);
    }

    public static RenderScript create(Context ctx, int sdkVersion, ContextType ct) {
        return create(ctx, sdkVersion, ct, CREATE_FLAG_NONE);
    }

    public static RenderScript create(Context ctx, int sdkVersion, ContextType ct, int flags) {
        return create(ctx, ct, flags);
    }

    /**
     * Creates a context separate from the per-process one. Mac.apk gives every
     * caller its own object anyway, so this is the same as create().
     */
    public static RenderScript createMultiContext(Context ctx, ContextType ct,
                                                  int flags, int api) {
        return create(ctx, ct, flags);
    }

    /** @return the version of the RenderScript driver, AOSP pins this at 1. */
    public static long getMinorVersion() {
        return sMinorVersion;
    }

    /**
     * Throws once the context has been destroyed. Every Allocation / Element /
     * Type entry point routes through here, because on a device using a dead
     * context is a hard error and apps that recycle contexts depend on seeing
     * it.
     */
    void validate() {
        if (!mAlive) {
            throw new RSInvalidStateException("Calling RS with no Context active.");
        }
    }

    public void destroy() {
        validate();
        mAlive = false;
        mElementCache.clear();
    }

    boolean isAlive() {
        return mAlive;
    }

    long safeID(BaseObj o) {
        if (o != null) {
            return o.getID(this);
        }
        return 0;
    }

    /** Releases the process-wide context. Mac.apk has no process context. */
    public static void releaseAllContexts() {
    }

    /**
     * Worker-thread priority. Mac.apk runs the intrinsics synchronously on the
     * calling thread (there is no driver worker pool to reprioritise), so this
     * records the request and does nothing else. Validation still runs, because
     * a call on a dead context must throw either way.
     */
    public void setPriority(Priority p) {
        validate();
    }

    public void setMessageHandler(RSMessageHandler msg) {
        mMessageCallback = msg;
    }

    public RSMessageHandler getMessageHandler() {
        return mMessageCallback;
    }

    public RSErrorHandler getErrorHandler() {
        return mErrorCallback;
    }

    public void setErrorHandler(RSErrorHandler msg) {
        mErrorCallback = msg;
    }

    /**
     * Sends a message to a running script. Only a ScriptC kernel can receive
     * one (via rsSendToClient's counterpart), and Mac.apk cannot execute those,
     * so there is nothing on the other end. Validation still applies; the
     * message is dropped exactly as it would be on a device with no script
     * listening.
     */
    public void sendMessage(int id, int[] data) {
        validate();
    }

    /**
     * Dumps the context's object graph to the log on a device. Mac.apk keeps no
     * driver-side object table to dump, so this validates and returns rather
     * than inventing output.
     */
    public void contextDump() {
        validate();
    }

    /**
     * Waits for pending asynchronous work. Every Mac.apk intrinsic runs
     * synchronously on the calling thread, so by the time finish() is reachable
     * there is nothing outstanding. This is a real "already finished", not a
     * skipped wait.
     */
    public void finish() {
        validate();
    }

    public final Context getApplicationContext() {
        return mApplicationContext;
    }
}
