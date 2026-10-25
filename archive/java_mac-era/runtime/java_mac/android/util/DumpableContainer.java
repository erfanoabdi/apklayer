// android.util.DumpableContainer -- something that holds Dumpables, keyed by
// Dumpable.getDumpableName(). AOSP:
// frameworks/base/core/java/android/util/DumpableContainer.java
package android.util;

public interface DumpableContainer {
    /**
     * Adds the given {@link Dumpable} to the container.
     *
     * @return {@code true} if it was added, {@code false} if the container
     *         already had a Dumpable with the same {@link Dumpable#getDumpableName() name}.
     */
    boolean addDumpable(Dumpable dumpable);

    /**
     * Removes the given {@code dumpable} from the container.
     *
     * @return {@code true} if it was removed, {@code false} if it was not present.
     */
    boolean removeDumpable(Dumpable dumpable);
}
