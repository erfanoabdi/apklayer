// android.view.inspector.IntFlagMapping -- the flag-name table a generated
// InspectionCompanion uses to turn an int bitmask into the set of flag names
// the layout inspector shows. Source: frameworks/base
// core/java/android/view/inspector/IntFlagMapping.java (android16-release).
//
// Mac.apk runs no layout inspector, so nothing consumes a mapping -- but this is
// pure arithmetic with no platform dependency, so it is implemented for real
// rather than stubbed: a generated companion that builds one and reads it back
// gets device-identical answers.
package android.view.inspector;

public final class IntFlagMapping {
    private final java.util.ArrayList<Flag> mFlags = new java.util.ArrayList<Flag>();

    public IntFlagMapping() {}

    /** AOSP IntFlagMapping.add(int mask, int target, String name): declare that
     *  `name` is present when `value & mask == target`. A mask of ~0 makes it an
     *  exact match; a mask equal to the target makes it a simple bit test. */
    public void add(int mask, int target, String name) {
        mFlags.add(new Flag(mask, target, java.util.Objects.requireNonNull(name)));
    }

    /** AOSP IntFlagMapping.get(int value): the names whose (mask, target) the
     *  value satisfies. AOSP's own rule, verbatim: a MUTUALLY-EXCLUSIVE flag
     *  (mask != target) matches on equality, and a plain bit flag matches when
     *  its bit is set. */
    public java.util.Set<String> get(int value) {
        final java.util.Set<String> enabled = new java.util.HashSet<String>();
        for (Flag flag : mFlags) {
            if (flag.isEnabledFor(value)) enabled.add(flag.mName);
        }
        return enabled;
    }

    private static final class Flag {
        private final int mMask;
        private final int mTarget;
        private final String mName;
        Flag(int mask, int target, String name) {
            mTarget = target;
            mMask = mask;
            mName = name;
        }
        boolean isEnabledFor(int value) { return (value & mMask) == mTarget; }
    }
}
