// android.util.Dumpable -- an object that can dump its state into a
// PrintWriter, registered with a DumpableContainer (Activity/Application
// implement one). AOSP:
// frameworks/base/core/java/android/util/Dumpable.java
package android.util;

import java.io.PrintWriter;

public interface Dumpable {
    /**
     * Dumps the internal state into the given writer.
     *
     * @param writer writer to dump the state
     * @param args optional list of arguments (typically passed by {@code dumpsys})
     */
    void dump(PrintWriter writer, String[] args);

    /**
     * Gets the name of the {@code Dumpable}.
     *
     * <p>By default returns the {@code simple class name}, but subclasses
     * hosting more than one Dumpable should override it.
     */
    default String getDumpableName() {
        return getClass().getSimpleName();
    }
}
