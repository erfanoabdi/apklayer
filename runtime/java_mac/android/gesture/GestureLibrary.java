// android.gesture.GestureLibrary -- the persistent face of a GestureStore: the
// same learn / recognize surface plus save() / load() backed by a file, an fd,
// or a raw resource. Mirrors AOSP
// frameworks/base/core/java/android/gesture/GestureLibrary.java verbatim
// (aosp-mirror android16-release, fetched 2026-08-24).
//
// `mStore` is `protected final` and is part of the public API signature, so
// app subclasses of GestureLibrary can and do reach it directly.
package android.gesture;

import java.util.ArrayList;
import java.util.Set;

public abstract class GestureLibrary {
    protected final GestureStore mStore;

    protected GestureLibrary() {
        mStore = new GestureStore();
    }

    public abstract boolean save();

    public abstract boolean load();

    public boolean isReadOnly() {
        return false;
    }

    /** AOSP @hide. */
    public Learner getLearner() {
        return mStore.getLearner();
    }

    public void setOrientationStyle(int style) {
        mStore.setOrientationStyle(style);
    }

    public int getOrientationStyle() {
        return mStore.getOrientationStyle();
    }

    public void setSequenceType(int type) {
        mStore.setSequenceType(type);
    }

    public int getSequenceType() {
        return mStore.getSequenceType();
    }

    public Set<String> getGestureEntries() {
        return mStore.getGestureEntries();
    }

    public ArrayList<Prediction> recognize(Gesture gesture) {
        return mStore.recognize(gesture);
    }

    public void addGesture(String entryName, Gesture gesture) {
        mStore.addGesture(entryName, gesture);
    }

    public void removeGesture(String entryName, Gesture gesture) {
        mStore.removeGesture(entryName, gesture);
    }

    public void removeEntry(String entryName) {
        mStore.removeEntry(entryName);
    }

    public ArrayList<Gesture> getGestures(String entryName) {
        return mStore.getGestures(entryName);
    }
}
