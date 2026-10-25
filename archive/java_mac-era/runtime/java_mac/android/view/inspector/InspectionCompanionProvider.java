// android.view.inspector.InspectionCompanionProvider -- resolves the
// InspectionCompanion for a View class, for the layout inspector.
// Source: frameworks/base
// core/java/android/view/inspector/InspectionCompanionProvider.java.
package android.view.inspector;

public interface InspectionCompanionProvider {
    <T> InspectionCompanion<T> provide(Class<T> cls);
}
