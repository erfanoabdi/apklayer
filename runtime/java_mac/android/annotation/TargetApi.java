package android.annotation;
import java.lang.annotation.*;
/** Target-API annotation. */
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR})
@Retention(RetentionPolicy.CLASS)
public @interface TargetApi { int value(); }
