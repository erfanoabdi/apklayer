package javax.annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.annotation.meta.TypeQualifierDefault;
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@TypeQualifierDefault(ElementType.PARAMETER)
public @interface ParametersAreNonnullByDefault { }
