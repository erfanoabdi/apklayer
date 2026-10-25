// android.view.textclassifier.TextClassificationManager --
// Context.TEXT_CLASSIFICATION_SERVICE.
// Source: frameworks/base
// core/java/android/view/textclassifier/TextClassificationManager.java
// (android16-release).
//
// Mac.apk has no on-device model, so the DEFAULT classifier is
// TextClassifier.NO_OP -- which is AOSP's own answer when no classifier service
// is available. An APP can still install its own via setTextClassifier /
// setTextClassificationSessionFactory, and those really take effect here.
package android.view.textclassifier;

public class TextClassificationManager {
    private TextClassifier mCustomTextClassifier;
    private TextClassificationSessionFactory mSessionFactory;

    public TextClassificationManager() {}

    /** AOSP getTextClassifier(): the app's classifier when it set one, else the
     *  platform's -- which here is NO_OP. */
    public TextClassifier getTextClassifier() {
        return mCustomTextClassifier != null ? mCustomTextClassifier : TextClassifier.NO_OP;
    }

    /** AOSP setTextClassifier(TextClassifier): install the app's own classifier;
     *  null restores the platform default. Honoured for real -- this is the
     *  supported way an app supplies smart-linkify behaviour Mac.apk cannot. */
    public void setTextClassifier(TextClassifier textClassifier) {
        mCustomTextClassifier = textClassifier;
    }

    /** AOSP setTextClassificationSessionFactory(TextClassificationSessionFactory)
     *  (API 29): the app supplies a fresh classifier per session. */
    public void setTextClassificationSessionFactory(TextClassificationSessionFactory factory) {
        mSessionFactory = factory;
    }

    /** AOSP createTextClassificationSession(TextClassificationContext): a
     *  session-scoped classifier. AOSP consults the session factory first, then
     *  falls back to the current classifier -- reproduced, so an app's factory
     *  is not silently ignored. */
    public TextClassifier createTextClassificationSession(TextClassificationContext context) {
        java.util.Objects.requireNonNull(context);
        if (mSessionFactory != null) {
            TextClassifier session = mSessionFactory.createTextClassificationSession(context);
            if (session != null) return session;
        }
        return getTextClassifier();
    }
}
