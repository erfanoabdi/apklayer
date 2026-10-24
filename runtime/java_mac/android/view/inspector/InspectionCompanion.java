package android.view.inspector;
/** Generated per-View descriptor that maps + reads a view's inspectable
 *  properties. Apps' generated *$InspectionCompanion classes implement this; it
 *  only needs to exist and compile. API 29.
 *  Source: frameworks/base/core/java/android/view/inspector/InspectionCompanion.java */
public interface InspectionCompanion<T> {
    void mapProperties(PropertyMapper propertyMapper);
    void readProperties(T inspectable, PropertyReader propertyReader);

    /** Thrown if readProperties runs before mapProperties. */
    class UninitializedPropertyMapException extends RuntimeException {
        public UninitializedPropertyMapException() { super(); }
    }
}
