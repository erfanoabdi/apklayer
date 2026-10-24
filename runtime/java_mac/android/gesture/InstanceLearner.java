// android.gesture.InstanceLearner -- the instance-based (nearest-neighbour)
// classifier: score every learned instance by 1/distance, keep the best per
// label, sort descending. This IS the recognizer. Mirrors AOSP
// frameworks/base/core/java/android/gesture/InstanceLearner.java verbatim
// (aosp-mirror android16-release, fetched 2026-08-24), including the fact that
// scores are NOT normalized (AOSP's normalization is commented out upstream, and
// apps threshold on the raw weight -- the common idiom is
// `if (predictions.get(0).score > 1.0)`).
//
// TWO AOSP EDGE CASES this port reproduces on purpose -- both were measured
// here on 2026-08-24 and are NOT Mac.apk bugs; "fixing" either would make our
// answers differ from a device's:
//
//  1. minimumCosineDistance can return NaN. For two nearly IDENTICAL normalized
//     vectors the accumulated float dot product `a` lands a hair above 1.0, and
//     Math.acos(>1) is NaN -- so the weight is NaN, every comparison against it
//     is false, and the label ends up scored NaN rather than "best". The
//     distance==0 guard only catches an EXACT tie. Real hand-drawn input never
//     duplicates a template closely enough to hit this; synthetic near-duplicate
//     test vectors do.
//  2. With ORIENTATION_SENSITIVE (== 2) the `numOrientations > 2` branch is
//     never taken, so the metric is the rotation-OPTIMAL cosine, i.e. fully
//     rotation invariant. That is what AOSP's own comment means by "at most 2
//     directions can be recognized": a rightward line and a downward line are
//     genuinely indistinguishable in this mode. Measured: correct labels on
//     circle / vee / swoosh, ambiguous on right-vs-down. Use SEQUENCE_INVARIANT
//     (spatial patch + Euclidean) when direction must be distinguished.
package android.gesture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.TreeMap;

class InstanceLearner extends Learner {
    private static final Comparator<Prediction> sComparator = new Comparator<Prediction>() {
        public int compare(Prediction object1, Prediction object2) {
            double score1 = object1.score;
            double score2 = object2.score;
            if (score1 > score2) {
                return -1;
            } else if (score1 < score2) {
                return 1;
            } else {
                return 0;
            }
        }
    };

    @Override
    ArrayList<Prediction> classify(int sequenceType, int orientationType, float[] vector) {
        ArrayList<Prediction> predictions = new ArrayList<Prediction>();
        ArrayList<Instance> instances = getInstances();
        int count = instances.size();
        TreeMap<String, Double> label2score = new TreeMap<String, Double>();
        for (int i = 0; i < count; i++) {
            Instance sample = instances.get(i);
            if (sample.vector.length != vector.length) {
                continue;
            }
            double distance;
            if (sequenceType == GestureStore.SEQUENCE_SENSITIVE) {
                distance = GestureUtils.minimumCosineDistance(sample.vector, vector,
                        orientationType);
            } else {
                distance = GestureUtils.squaredEuclideanDistance(sample.vector, vector);
            }
            double weight;
            if (distance == 0) {
                weight = Double.MAX_VALUE;
            } else {
                weight = 1 / distance;
            }
            Double score = label2score.get(sample.label);
            if (score == null || weight > score) {
                label2score.put(sample.label, weight);
            }
        }

        for (String name : label2score.keySet()) {
            double score = label2score.get(name);
            predictions.add(new Prediction(name, score));
        }

        Collections.sort(predictions, sComparator);

        return predictions;
    }
}
