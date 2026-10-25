// android.content.EntityIterator -- an Iterator<Entity> that owns a cursor and
// therefore must be closed, and can be rewound.
//
// AOSP ref: frameworks/base/core/java/android/content/EntityIterator.java
package android.content;

import java.util.Iterator;

public interface EntityIterator extends Iterator<Entity> {
    /** Reset the iterator back to the beginning. */
    void reset();

    /** Release the underlying resources; the iterator is unusable after. */
    void close();
}
