package android.service.autofill;

import java.util.Collections;
import java.util.List;

/**
 * How well one field matched the categories in the service's UserData, as
 * reported back through FillEventHistory.Event.getFieldsClassification().
 *
 * The platform builds these; a service only reads them. Mac.apk runs no
 * classification pass, so none are produced -- but the type is named in the
 * event's return signature and its Match list must resolve.
 *
 * Source: frameworks/base/core/java/android/service/autofill/FieldClassification.java
 */
public final class FieldClassification {

    private final List<Match> mMatches;

    FieldClassification(List<Match> matches) {
        mMatches = matches == null ? Collections.<Match>emptyList()
                : Collections.unmodifiableList(matches);
    }

    public List<Match> getMatches() { return mMatches; }

    /** One category id plus the 0..1 score the algorithm gave it. */
    public static final class Match {
        private final String mCategoryId;
        private final float mScore;

        Match(String categoryId, float score) {
            mCategoryId = categoryId;
            mScore = score;
        }

        public String getCategoryId() { return mCategoryId; }

        public float getScore() { return mScore; }

        @Override public String toString() {
            return "Match[" + mCategoryId + "=" + mScore + "]";
        }
    }
}
