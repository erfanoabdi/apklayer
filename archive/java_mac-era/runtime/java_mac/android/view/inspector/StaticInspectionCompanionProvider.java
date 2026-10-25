// android.view.inspector.StaticInspectionCompanionProvider -- finds the
// companion the annotation processor GENERATED for a View class, by the
// documented naming convention: <ClassName>$InspectionCompanion, a nested class
// of the view itself. Source: frameworks/base
// core/java/android/view/inspector/StaticInspectionCompanionProvider.java
// (android16-release).
//
// Implemented for real rather than stubbed: the lookup is pure reflection with
// no platform dependency, so an app that ships generated companions gets the
// same result it would on a device. A class with no generated companion yields
// null, which is AOSP's answer and the common case.
package android.view.inspector;

public class StaticInspectionCompanionProvider implements InspectionCompanionProvider {
    private static final String COMPANION_SUFFIX = "$InspectionCompanion";

    public StaticInspectionCompanionProvider() {}

    @Override
    @SuppressWarnings("unchecked")
    public <T> InspectionCompanion<T> provide(Class<T> cls) {
        java.util.Objects.requireNonNull(cls);
        final String companionName = cls.getName() + COMPANION_SUFFIX;
        try {
            final Class<?> companionClass = cls.getClassLoader() != null
                    ? cls.getClassLoader().loadClass(companionName)
                    : Class.forName(companionName);
            if (InspectionCompanion.class.isAssignableFrom(companionClass)) {
                return (InspectionCompanion<T>) companionClass
                        .getDeclaredConstructor().newInstance();
            }
            // AOSP: a class by that name that is NOT an InspectionCompanion is a
            // hard error, not a silent miss -- it means the generator and the
            // runtime disagree.
            throw new RuntimeException(String.format(
                    "Class %s does not implement InspectionCompanion", companionName));
        } catch (ClassNotFoundException e) {
            return null;   // no generated companion; AOSP's normal case
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(String.format(
                    "Unable to instantiate %s", companionName), e);
        }
    }
}
