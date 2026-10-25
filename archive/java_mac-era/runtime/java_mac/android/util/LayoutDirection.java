// android.util.LayoutDirection -- the four layout-direction constants shared
// by View, Drawable and Configuration. AOSP:
// frameworks/base/core/java/android/util/LayoutDirection.java
package android.util;

public final class LayoutDirection {
    private LayoutDirection() {}

    /** Horizontal layout direction is from Left to Right. */
    public static final int LTR = 0;
    /** Horizontal layout direction is from Right to Left. */
    public static final int RTL = 1;
    /** Horizontal layout direction is inherited from the parent. */
    public static final int INHERIT = 2;
    /** Horizontal layout direction is deduced from the default language script for the locale. */
    public static final int LOCALE = 3;
}
