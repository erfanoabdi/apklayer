package apklayer.host;

/**
 * Template of {@code sun.reflect.annotation.ApklayerEnums}, which JdkIdentityPatch copies into java.base beside AnnotationParser (package-private
 * there, as the parser is): an annotation's enum element is the static field it names, as ART resolves dex's encoded enum (a field reference),
 * else {@code Enum.valueOf}. R8 renames an enum's fields but keeps the names its constants were made with, so valueOf by the field's name fails:
 * Play services' OnboardingNode default threw "Invalid default" in its account authenticator's addAccount (Play Store's Sign in did nothing).
 * Only java.base API here.
 */
final class JdkAnnotationEnums {
    private JdkAnnotationEnums() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    static Enum enumConstant(Class type, String name) {
        try {
            java.lang.reflect.Field field = type.getDeclaredField(name);
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) && field.getType() == type) {
                field.setAccessible(true);
                Object value = field.get(null);
                if (type.isInstance(value)) return (Enum) value;
            }
        } catch (ReflectiveOperationException | RuntimeException notAField) { }
        return Enum.valueOf(type, name);
    }
}
