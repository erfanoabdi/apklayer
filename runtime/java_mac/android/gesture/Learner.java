// android.gesture.Learner -- the abstract instance store the recognizer
// classifies against. Mirrors AOSP
// frameworks/base/core/java/android/gesture/Learner.java verbatim
// (aosp-mirror android16-release, fetched 2026-08-24).
package android.gesture;

import java.util.ArrayList;

abstract class Learner {
    private final ArrayList<Instance> mInstances = new ArrayList<Instance>();

    void addInstance(Instance instance) {
        mInstances.add(instance);
    }

    ArrayList<Instance> getInstances() {
        return mInstances;
    }

    void removeInstance(long id) {
        ArrayList<Instance> instances = mInstances;
        int count = instances.size();
        for (int i = 0; i < count; i++) {
            Instance instance = instances.get(i);
            if (id == instance.id) {
                instances.remove(instance);
                return;
            }
        }
    }

    void removeInstances(String name) {
        final ArrayList<Instance> toDelete = new ArrayList<Instance>();
        final ArrayList<Instance> instances = mInstances;
        final int count = instances.size();

        for (int i = 0; i < count; i++) {
            final Instance instance = instances.get(i);
            // the label can be null, as specified in Instance
            if ((instance.label == null && name == null)
                    || (instance.label != null && instance.label.equals(name))) {
                toDelete.add(instance);
            }
        }
        instances.removeAll(toDelete);
    }

    abstract ArrayList<Prediction> classify(int sequenceType, int orientationType, float[] vector);
}
