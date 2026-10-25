package android.renderscript;

/**
 * Base class for RenderScript kernels: both the built-in {@link ScriptIntrinsic}
 * family and the app-authored {@link ScriptC} subclasses the tool chain
 * generates.
 *
 * WHAT RUNS AND WHAT DOES NOT. The global-variable side of this class is real:
 * setVar/getVar keep an actual slot table, so a value pushed in comes back out.
 * The KERNEL LAUNCH side (the protected forEach / invoke / reduce overloads) is
 * the part that needs a bitcode engine, since a ScriptC kernel only exists as
 * LLVM bitcode compiled from .rs source. Mac.apk has no such engine, so those
 * throw RSDriverException rather than returning quietly and leaving the output
 * Allocation untouched: an unprocessed buffer is indistinguishable from a
 * processed one to the caller, and the resulting corruption surfaces far away
 * from the real cause.
 *
 * The intrinsics do NOT come through here. Each one overrides its own public
 * forEach and computes the result on the CPU, mirroring AOSP's cpu_ref
 * implementation.
 *
 * Source: frameworks/rs/java/android/renderscript/Script.java
 */
public class Script extends BaseObj {

    private final java.util.HashMap<Integer, Object> mVars =
            new java.util.HashMap<Integer, Object>();
    private final java.util.HashMap<Integer, Allocation> mBoundAllocations =
            new java.util.HashMap<Integer, Allocation>();

    private KernelID[] mKIDs = new KernelID[0];
    private InvokeID[] mIIDs = new InvokeID[0];
    private FieldID[] mFIDs = new FieldID[0];

    String mTimeZone;

    Script(RenderScript rs) {
        super(rs);
    }

    /**
     * Identifies a kernel within a script, for use in a {@link ScriptGroup}.
     */
    public static final class KernelID extends BaseObj {
        Script mScript;
        int mSlot;
        int mSig;

        KernelID(RenderScript rs, Script s, int slot, int sig) {
            super(rs);
            mScript = s;
            mSlot = slot;
            mSig = sig;
        }
    }

    /** Identifies an invokable function within a script. */
    public static final class InvokeID extends BaseObj {
        Script mScript;
        int mSlot;

        InvokeID(RenderScript rs, Script s, int slot) {
            super(rs);
            mScript = s;
            mSlot = slot;
        }
    }

    /** Identifies a global variable within a script. */
    public static final class FieldID extends BaseObj {
        Script mScript;
        int mSlot;

        FieldID(RenderScript rs, Script s, int slot) {
            super(rs);
            mScript = s;
            mSlot = slot;
        }
    }

    /**
     * Only the reflected ScriptC_* subclasses call this. IDs are cached per
     * slot so repeated calls return the same object, which ScriptGroup's
     * connection map relies on.
     */
    protected KernelID createKernelID(int slot, int sig, Element ein, Element eout) {
        if (slot >= mKIDs.length) {
            KernelID[] t = new KernelID[slot + 1];
            System.arraycopy(mKIDs, 0, t, 0, mKIDs.length);
            mKIDs = t;
        }
        if (mKIDs[slot] != null) {
            return mKIDs[slot];
        }
        KernelID k = new KernelID(mRS, this, slot, sig);
        mKIDs[slot] = k;
        return k;
    }

    protected InvokeID createInvokeID(int slot) {
        if (slot >= mIIDs.length) {
            InvokeID[] t = new InvokeID[slot + 1];
            System.arraycopy(mIIDs, 0, t, 0, mIIDs.length);
            mIIDs = t;
        }
        if (mIIDs[slot] != null) {
            return mIIDs[slot];
        }
        InvokeID i = new InvokeID(mRS, this, slot);
        mIIDs[slot] = i;
        return i;
    }

    protected FieldID createFieldID(int slot, Element e) {
        if (slot >= mFIDs.length) {
            FieldID[] t = new FieldID[slot + 1];
            System.arraycopy(mFIDs, 0, t, 0, mFIDs.length);
            mFIDs = t;
        }
        if (mFIDs[slot] != null) {
            return mFIDs[slot];
        }
        FieldID f = new FieldID(mRS, this, slot);
        mFIDs[slot] = f;
        return f;
    }

    /**
     * Only a ScriptC has invokable functions, and executing one needs the
     * bitcode engine described in the class comment.
     */
    protected void invoke(int slot) {
        throw new RSDriverException(noKernelEngine("invoke"));
    }

    protected void invoke(int slot, FieldPacker v) {
        throw new RSDriverException(noKernelEngine("invoke"));
    }

    protected void forEach(int slot, Allocation ain, Allocation aout, FieldPacker v) {
        throw new RSDriverException(noKernelEngine("forEach"));
    }

    protected void forEach(int slot, Allocation ain, Allocation aout, FieldPacker v,
                           LaunchOptions sc) {
        throw new RSDriverException(noKernelEngine("forEach"));
    }

    protected void forEach(int slot, Allocation[] ains, Allocation aout, FieldPacker v) {
        throw new RSDriverException(noKernelEngine("forEach"));
    }

    protected void forEach(int slot, Allocation[] ains, Allocation aout, FieldPacker v,
                           LaunchOptions sc) {
        throw new RSDriverException(noKernelEngine("forEach"));
    }

    protected void reduce(int slot, Allocation[] ains, Allocation aout, LaunchOptions sc) {
        throw new RSDriverException(noKernelEngine("reduce"));
    }

    private static String noKernelEngine(String what) {
        return "Script." + what + " runs an app-authored .rs kernel, which exists only as LLVM"
                + " bitcode compiled by slang. Mac.apk has no bitcode engine, so the kernel"
                + " cannot be executed. The built-in ScriptIntrinsic classes ARE implemented"
                + " and run on the CPU.";
    }

    /**
     * Binds an Allocation to a pointer global. This is pure bookkeeping and is
     * therefore real: the binding is recorded and readable, even though only a
     * ScriptC kernel could dereference it.
     */
    public void bindAllocation(Allocation va, int slot) {
        mRS.validate();
        if (va != null) {
            mBoundAllocations.put(slot, va);
        } else {
            mBoundAllocations.remove(slot);
        }
    }

    Allocation getBoundAllocation(int slot) {
        return mBoundAllocations.get(slot);
    }

    // ---- global variables ---------------------------------------------------
    //
    // Real storage. AOSP pushes these into driver memory and reads them back
    // out of it; a slot table is observationally identical for every access an
    // app can make through this API.

    public void setVar(int index, float v) {
        mVars.put(index, v);
    }

    public void setVar(int index, double v) {
        mVars.put(index, v);
    }

    public void setVar(int index, int v) {
        mVars.put(index, v);
    }

    public void setVar(int index, long v) {
        mVars.put(index, v);
    }

    public void setVar(int index, boolean v) {
        mVars.put(index, v);
    }

    public void setVar(int index, BaseObj o) {
        mVars.put(index, o);
    }

    public void setVar(int index, FieldPacker v) {
        mVars.put(index, v.getData().clone());
    }

    public void setVar(int index, FieldPacker v, Element e, int[] dims) {
        mVars.put(index, v.getData().clone());
    }

    public float getVarF(int index) {
        Object o = mVars.get(index);
        return o instanceof Number ? ((Number) o).floatValue() : 0f;
    }

    public double getVarD(int index) {
        Object o = mVars.get(index);
        return o instanceof Number ? ((Number) o).doubleValue() : 0.0;
    }

    public int getVarI(int index) {
        Object o = mVars.get(index);
        return o instanceof Number ? ((Number) o).intValue() : 0;
    }

    public long getVarJ(int index) {
        Object o = mVars.get(index);
        return o instanceof Number ? ((Number) o).longValue() : 0L;
    }

    public boolean getVarB(int index) {
        Object o = mVars.get(index);
        return o instanceof Boolean && (Boolean) o;
    }

    /** Reads a struct-valued global back into the packer's buffer. */
    public void getVarV(int index, FieldPacker v) {
        Object o = mVars.get(index);
        if (o instanceof byte[]) {
            byte[] src = (byte[]) o;
            byte[] dst = v.getData();
            System.arraycopy(src, 0, dst, 0, Math.min(src.length, dst.length));
        }
    }

    /**
     * Sets the time zone RS_TIME_* built-ins report. Only a ScriptC kernel can
     * observe it, so this records the value and nothing more.
     */
    public void setTimeZone(String timeZone) {
        mRS.validate();
        mTimeZone = timeZone;
    }

    /**
     * Base class for the reflected ScriptField_* wrappers. It owns the backing
     * Allocation for one script global of struct type.
     */
    public static class FieldBase {
        protected Element mElement;
        protected Allocation mAllocation;

        protected FieldBase() {
        }

        protected void init(RenderScript rs, int dimx) {
            mAllocation = Allocation.createSized(rs, mElement, dimx, Allocation.USAGE_SCRIPT);
        }

        protected void init(RenderScript rs, int dimx, int usages) {
            mAllocation = Allocation.createSized(rs, mElement, dimx,
                    Allocation.USAGE_SCRIPT | usages);
        }

        public Element getElement() {
            return mElement;
        }

        public Type getType() {
            return mAllocation.getType();
        }

        public Allocation getAllocation() {
            return mAllocation;
        }

        /** AOSP declares this and leaves the body to the generated subclass. */
        public void updateAllocation() {
        }
    }

    /**
     * Restricts a kernel launch to a sub-range of the output Allocation.
     *
     * The ranges are HALF OPEN: setX(0, 4) covers x in 0..3. An end that is not
     * greater than the start is rejected, which is how a caller that mixes up
     * "end" and "count" finds out.
     */
    public static final class LaunchOptions {

        private int xstart = 0;
        private int ystart = 0;
        private int xend = 0;
        private int yend = 0;
        private int zstart = 0;
        private int zend = 0;
        private int strategy;

        public LaunchOptions setX(int xstartArg, int xendArg) {
            if (xstartArg < 0 || xendArg <= xstartArg) {
                throw new RSIllegalArgumentException("Invalid dimensions");
            }
            xstart = xstartArg;
            xend = xendArg;
            return this;
        }

        public LaunchOptions setY(int ystartArg, int yendArg) {
            if (ystartArg < 0 || yendArg <= ystartArg) {
                throw new RSIllegalArgumentException("Invalid dimensions");
            }
            ystart = ystartArg;
            yend = yendArg;
            return this;
        }

        public LaunchOptions setZ(int zstartArg, int zendArg) {
            if (zstartArg < 0 || zendArg <= zstartArg) {
                throw new RSIllegalArgumentException("Invalid dimensions");
            }
            zstart = zstartArg;
            zend = zendArg;
            return this;
        }

        public int getXStart() {
            return xstart;
        }

        public int getXEnd() {
            return xend;
        }

        public int getYStart() {
            return ystart;
        }

        public int getYEnd() {
            return yend;
        }

        public int getZStart() {
            return zstart;
        }

        public int getZEnd() {
            return zend;
        }
    }

    /**
     * Present in the public API with no members. AOSP declares it empty and
     * gives it only a package-private constructor, so an app can name the type
     * but cannot instantiate it; that is reproduced rather than "improved",
     * because a public constructor here would be API surface no device has.
     */
    public static class Builder {
        RenderScript mRS;

        Builder(RenderScript rs) {
            mRS = rs;
        }
    }
}
