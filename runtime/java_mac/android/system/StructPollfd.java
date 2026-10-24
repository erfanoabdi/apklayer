// android.system.StructPollfd -- the in/out parameter of Os.poll, C's struct
// pollfd from <poll.h> (API 21).
//   AOSP: libcore/luni/src/main/java/android/system/StructPollfd.java
//         (android14-release), mirrored member for member. Note toString()
//         throws NullPointerException while fd or userData is null, exactly as
//         on a device (libcore.util.Objects has no null check).
// Our Os has no poll(StructPollfd[], int) yet; see
// docs/fwgap-new-platform-classes_2026-09-26.md.
package android.system;

import java.io.FileDescriptor;
import libcore.util.Objects;

/**
 * Used as an in/out parameter to {@link Os#poll}.
 * Corresponds to C's {@code struct pollfd} from {@code <poll.h>}.
 */
public final class StructPollfd {
    /** The file descriptor to poll. */
    public FileDescriptor fd;

    /**
     * The events we're interested in. POLLIN corresponds to being in select(2)'s read fd set,
     * POLLOUT to the write fd set.
     */
    public short events;

    /** The events that actually happened. */
    public short revents;

    /**
     * A non-standard extension that lets callers conveniently map back to the object
     * their fd belongs to. This is used by Selector, for example, to associate each
     * FileDescriptor with the corresponding SelectionKey.
     */
    public Object userData;

    @Override public String toString() {
        return Objects.toString(this);
    }
}
