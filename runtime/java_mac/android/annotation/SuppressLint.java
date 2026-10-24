package android.annotation;
import java.lang.annotation.*;
/** Lint-suppression annotation (source-retained on-device). */
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER,
         ElementType.CONSTRUCTOR, ElementType.LOCAL_VARIABLE})
@Retention(RetentionPolicy.CLASS)
public @interface SuppressLint { String[] value(); }
