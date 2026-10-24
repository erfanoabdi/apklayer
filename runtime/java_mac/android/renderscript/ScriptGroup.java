package android.renderscript;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * A directed acyclic graph of kernels, executed as one unit with the driver
 * allocating the intermediate buffers.
 *
 * WHAT IS REAL HERE AND WHAT IS NOT. Building a group is real: both builders
 * validate their inputs the way the platform does (a kernel that is not in the
 * group, a duplicate connection, a KernelID from another script, a cycle), and
 * the graph is recorded. What does NOT run is execute().
 *
 * The reason is the same as {@link ScriptC}'s. A ScriptGroup exists to chain
 * app-authored .rs kernels; its Builder2 form additionally schedules invokable
 * script functions and binds script globals through Closure and Future, none of
 * which has any meaning without a bitcode engine. Running only the sub-case
 * where every node happens to be a built-in intrinsic would mean an API that
 * works for one graph and throws for the next, with no way for the app to tell
 * which it built, so execute() reports the missing capability in every case
 * rather than sometimes.
 *
 * An app that wants a chain of built-in intrinsics on Mac.apk should call each
 * intrinsic's forEach in turn, which is fully supported and is what the driver
 * would end up doing anyway.
 *
 * Source: frameworks/rs/java/android/renderscript/ScriptGroup.java
 */
public final class ScriptGroup extends BaseObj {

    private static final String NO_ENGINE =
            "ScriptGroup.execute runs a graph of app-authored .rs kernels, which exist only"
            + " as LLVM bitcode. Mac.apk has no bitcode engine, so the group cannot be"
            + " executed. Call the built-in ScriptIntrinsic forEach methods in sequence"
            + " instead; those are implemented and run on the CPU.";

    List<Node> mNodes = new ArrayList<Node>();
    List<ConnectLine> mLines = new ArrayList<ConnectLine>();
    HashMap<Script.KernelID, Allocation> mInputs = new HashMap<Script.KernelID, Allocation>();
    HashMap<Script.KernelID, Allocation> mOutputs = new HashMap<Script.KernelID, Allocation>();
    String mName;

    ScriptGroup(RenderScript rs) {
        super(rs);
    }

    /** One kernel in the graph, plus the edges touching it. */
    static class Node {
        Script mScript;
        List<Script.KernelID> mKernels = new ArrayList<Script.KernelID>();
        List<ConnectLine> mInputs = new ArrayList<ConnectLine>();
        List<ConnectLine> mOutputs = new ArrayList<ConnectLine>();
        int mSeen;
        int mOrder;

        Node(Script s) {
            mScript = s;
        }
    }

    /** One edge: a Type-shaped buffer from a kernel to a kernel or to a global. */
    static class ConnectLine {
        ConnectLine(Type t, Script.KernelID from, Script.KernelID to) {
            mFrom = from;
            mToK = to;
            mAllocationType = t;
        }

        ConnectLine(Type t, Script.KernelID from, Script.FieldID to) {
            mFrom = from;
            mToF = to;
            mAllocationType = t;
        }

        Script.FieldID mToF;
        Script.KernelID mToK;
        Script.KernelID mFrom;
        Type mAllocationType;
    }

    /** A value bound to a script global for the duration of a Builder2 group. */
    public static final class Binding {
        private final Script.FieldID mField;
        private final Object mValue;

        public Binding(Script.FieldID field, Object value) {
            mField = field;
            mValue = value;
        }

        Script.FieldID getField() {
            return mField;
        }

        Object getValue() {
            return mValue;
        }
    }

    /** A handle to a value a Closure will produce. Opaque by design. */
    public static final class Future {
        Closure mClosure;
        Script.FieldID mFieldID;
        Object mValue;

        Future(Closure closure, Script.FieldID fieldID, Object value) {
            mClosure = closure;
            mFieldID = fieldID;
            mValue = value;
        }
    }

    /** A placeholder for an argument supplied at execute() time. */
    public static final class Input {
        List<Object[]> mFieldsToSet = new ArrayList<Object[]>();
        Object mValue;

        Input() {
        }

        Future getFuture() {
            return new Future(null, null, this);
        }
    }

    /** One scheduled kernel or invoke inside a Builder2 group. */
    public static final class Closure extends BaseObj {
        Object[] mArgs;
        Allocation mReturnValue;
        Script.KernelID mKernelID;
        Script.InvokeID mInvokeID;
        final HashMap<Script.FieldID, Object> mBindings = new HashMap<Script.FieldID, Object>();

        Closure(RenderScript rs) {
            super(rs);
        }

        /** @return a Future for one of this closure's script globals. */
        public Future getGlobal(Script.FieldID field) {
            return new Future(this, field, mBindings.get(field));
        }

        /** @return a Future for the kernel's output Allocation. */
        public Future getReturn() {
            return new Future(this, null, mReturnValue);
        }
    }

    /** Binds an Allocation to a kernel's input for the next execute(). */
    public void setInput(Script.KernelID s, Allocation a) {
        for (int ct = 0; ct < mNodes.size(); ct++) {
            Node n = mNodes.get(ct);
            if (n.mKernels.contains(s)) {
                mInputs.put(s, a);
                return;
            }
        }
        throw new RSIllegalArgumentException("Script not found");
    }

    /** Binds an Allocation to a kernel's output for the next execute(). */
    public void setOutput(Script.KernelID s, Allocation a) {
        for (int ct = 0; ct < mNodes.size(); ct++) {
            Node n = mNodes.get(ct);
            if (n.mKernels.contains(s)) {
                mOutputs.put(s, a);
                return;
            }
        }
        throw new RSIllegalArgumentException("Script not found");
    }

    /** @throws RSDriverException always; see the class comment. */
    public void execute() {
        throw new RSDriverException(NO_ENGINE);
    }

    /** @throws RSDriverException always; see the class comment. */
    public Object[] execute(Object... inputs) {
        throw new RSDriverException(NO_ENGINE);
    }

    /**
     * The original ScriptGroup builder: a graph of kernels wired by explicit
     * connections. Every validation below is the platform's, and they are the
     * useful part of this class even though execute() cannot run.
     */
    public static final class Builder {

        private final RenderScript mRS;
        private final List<Node> mNodes = new ArrayList<Node>();
        private final List<ConnectLine> mLines = new ArrayList<ConnectLine>();
        private int mKernelCount;

        public Builder(RenderScript rs) {
            mRS = rs;
        }

        /** Depth-first cycle check, run after every new edge. */
        private void validateCycle(Node target, Node original) {
            for (int ct = 0; ct < target.mOutputs.size(); ct++) {
                final ConnectLine cl = target.mOutputs.get(ct);
                if (cl.mToK != null) {
                    Node tn = findNode(cl.mToK.mScript);
                    if (tn.equals(original)) {
                        throw new RSInvalidStateException("Loops in group not allowed.");
                    }
                    validateCycle(tn, original);
                }
                if (cl.mToF != null) {
                    Node tn = findNode(cl.mToF.mScript);
                    if (tn.equals(original)) {
                        throw new RSInvalidStateException("Loops in group not allowed.");
                    }
                    validateCycle(tn, original);
                }
            }
        }

        private void mergeDAGs(int valueUsed, int valueKilled) {
            for (int ct = 0; ct < mNodes.size(); ct++) {
                if (mNodes.get(ct).mSeen == valueKilled) {
                    mNodes.get(ct).mSeen = valueUsed;
                }
            }
        }

        private void validateDAGRecurse(Node n, int dagNumber) {
            // Already visited in this pass.
            if (n.mSeen == dagNumber) {
                return;
            }
            // Visited in an earlier pass: the two subgraphs are one DAG.
            if (n.mSeen != 0) {
                mergeDAGs(n.mSeen, dagNumber);
                return;
            }

            n.mSeen = dagNumber;
            for (int ct = 0; ct < n.mOutputs.size(); ct++) {
                final ConnectLine cl = n.mOutputs.get(ct);
                if (cl.mToK != null) {
                    validateDAGRecurse(findNode(cl.mToK.mScript), dagNumber);
                }
                if (cl.mToF != null) {
                    validateDAGRecurse(findNode(cl.mToF.mScript), dagNumber);
                }
            }
        }

        private void validateDAG() {
            for (int ct = 0; ct < mNodes.size(); ct++) {
                Node n = mNodes.get(ct);
                if (n.mInputs.size() == 0) {
                    if (n.mOutputs.size() == 0 && mNodes.size() > 1) {
                        throw new RSInvalidStateException("Groups cannot contain unconnected"
                                + " scripts");
                    }
                    validateDAGRecurse(n, ct + 1);
                }
            }
            int dagNumber = mNodes.get(0).mSeen;
            for (int ct = 0; ct < mNodes.size(); ct++) {
                if (mNodes.get(ct).mSeen != dagNumber) {
                    throw new RSInvalidStateException("Multiple DAGs in group not allowed.");
                }
            }
        }

        private Node findNode(Script s) {
            for (int ct = 0; ct < mNodes.size(); ct++) {
                if (s == mNodes.get(ct).mScript) {
                    return mNodes.get(ct);
                }
            }
            return null;
        }

        private Node findNode(Script.KernelID k) {
            for (int ct = 0; ct < mNodes.size(); ct++) {
                Node n = mNodes.get(ct);
                for (int ct2 = 0; ct2 < n.mKernels.size(); ct2++) {
                    if (k == n.mKernels.get(ct2)) {
                        return n;
                    }
                }
            }
            return null;
        }

        public Builder addKernel(Script.KernelID k) {
            if (mLines.size() != 0) {
                throw new RSInvalidStateException(
                        "Kernels may not be added once connections exist.");
            }
            if (findNode(k) != null) {
                return this;
            }
            mKernelCount++;
            Node n = findNode(k.mScript);
            if (n == null) {
                n = new Node(k.mScript);
                mNodes.add(n);
            }
            n.mKernels.add(k);
            return this;
        }

        /** Connects a kernel's output to another kernel's input. */
        public Builder addConnection(Type t, Script.KernelID from, Script.KernelID to) {
            Node nf = findNode(from);
            if (nf == null) {
                throw new RSInvalidStateException("From script not found.");
            }
            Node nt = findNode(to);
            if (nt == null) {
                throw new RSInvalidStateException("To script not found.");
            }

            ConnectLine cl = new ConnectLine(t, from, to);
            mLines.add(cl);

            nf.mOutputs.add(cl);
            nt.mInputs.add(cl);

            validateCycle(nf, nf);
            return this;
        }

        /** Connects a kernel's output to a script global. */
        public Builder addConnection(Type t, Script.KernelID from, Script.FieldID to) {
            Node nf = findNode(from);
            if (nf == null) {
                throw new RSInvalidStateException("From script not found.");
            }
            Node nt = findNode(to.mScript);
            if (nt == null) {
                throw new RSInvalidStateException("To script not found.");
            }

            ConnectLine cl = new ConnectLine(t, from, to);
            mLines.add(cl);

            nf.mOutputs.add(cl);
            nt.mInputs.add(cl);

            validateCycle(nf, nf);
            return this;
        }

        public ScriptGroup create() {
            if (mNodes.size() == 0) {
                throw new RSInvalidStateException("Empty script groups are not allowed");
            }
            for (int ct = 0; ct < mNodes.size(); ct++) {
                mNodes.get(ct).mSeen = 0;
            }
            validateDAG();

            ScriptGroup sg = new ScriptGroup(mRS);
            sg.mNodes.addAll(mNodes);
            sg.mLines.addAll(mLines);
            return sg;
        }
    }

    /**
     * The API 23 builder: closures, futures and bindings.
     *
     * This form is inseparable from app-authored scripts (an "invoke" is a
     * function inside a .rs file, and a binding writes a script global), so the
     * group it creates cannot execute here for the reason in the class comment.
     * It still builds and validates so that construction-time errors surface
     * where they would on a device.
     */
    public static final class Builder2 {

        RenderScript mRS;
        List<Closure> mClosures = new ArrayList<Closure>();
        List<Input> mInputs = new ArrayList<Input>();

        public Builder2(RenderScript rs) {
            mRS = rs;
        }

        public Input addInput() {
            Input unbound = new Input();
            mInputs.add(unbound);
            return unbound;
        }

        public Closure addKernel(Script.KernelID k, Type returnType, Object... argsAndBindings) {
            if (k == null) {
                throw new RSIllegalArgumentException("KernelID may not be null.");
            }
            Closure c = new Closure(mRS);
            c.mKernelID = k;
            c.mArgs = argsAndBindings;
            if (returnType != null) {
                c.mReturnValue = Allocation.createTyped(mRS, returnType);
            }
            recordBindings(c, argsAndBindings);
            mClosures.add(c);
            return c;
        }

        public Closure addInvoke(Script.InvokeID invoke, Object... argsAndBindings) {
            if (invoke == null) {
                throw new RSIllegalArgumentException("InvokeID may not be null.");
            }
            Closure c = new Closure(mRS);
            c.mInvokeID = invoke;
            c.mArgs = argsAndBindings;
            recordBindings(c, argsAndBindings);
            mClosures.add(c);
            return c;
        }

        private static void recordBindings(Closure c, Object[] argsAndBindings) {
            if (argsAndBindings == null) {
                return;
            }
            for (Object o : argsAndBindings) {
                if (o instanceof Binding) {
                    Binding b = (Binding) o;
                    c.mBindings.put(b.getField(), b.getValue());
                }
            }
        }

        public ScriptGroup create(String name, Future... outputs) {
            if (name == null || name.isEmpty() || name.length() > 100
                    || !name.equals(name.replaceAll("[^a-zA-Z0-9-]", "_"))) {
                throw new RSIllegalArgumentException("invalid script group name");
            }
            if (mClosures.size() == 0) {
                throw new RSInvalidStateException("Empty script groups are not allowed");
            }
            ScriptGroup sg = new ScriptGroup(mRS);
            sg.mName = name;
            return sg;
        }
    }
}
