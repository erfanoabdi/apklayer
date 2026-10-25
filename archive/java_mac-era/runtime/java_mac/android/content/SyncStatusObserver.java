// android.content.SyncStatusObserver -- registered with
// ContentResolver.addStatusChangeListener(int, SyncStatusObserver).
//
// AOSP ref: frameworks/base/core/java/android/content/SyncStatusObserver.java
package android.content;

public interface SyncStatusObserver {
    /**
     * @param which the mask of ContentResolver.SYNC_OBSERVER_TYPE_* bits that
     *              changed.
     */
    void onStatusChanged(int which);
}
