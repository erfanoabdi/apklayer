// android.view.textclassifier.TextClassificationSessionFactory -- lets an app
// supply its own classifier for each session, via
// TextClassificationManager.setTextClassificationSessionFactory.
// Source: frameworks/base
// core/java/android/view/textclassifier/TextClassificationSessionFactory.java.
package android.view.textclassifier;

public interface TextClassificationSessionFactory {
    TextClassifier createTextClassificationSession(TextClassificationContext context);
}
