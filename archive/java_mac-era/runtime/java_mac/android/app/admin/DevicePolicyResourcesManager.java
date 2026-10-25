// android.app.admin.DevicePolicyResourcesManager -- "Class containing required
// APIs to set, reset, and get device policy related resources", i.e. the
// strings and drawables an OEM or a device-policy role holder overrides so the
// system UI can say "work profile" in the enterprise's own words. API 31.
//
// AOSP ref: frameworks/base/core/java/android/app/admin/
// DevicePolicyResourcesManager.java (android16-release). Every getter takes a
// DEFAULT SUPPLIER and returns the override only when one exists -- so the
// honest implementation for a device with no policy resources installed is to
// invoke the caller's own default, which is exactly what AOSP does when the
// resource id is not overridden.
//
// Mac.apk has no device-policy role holder and no resource-override store (see
// DevicePolicyManager's class comment), so every lookup takes the default path.
// That is a REAL answer, not a stub: the caller gets its own fallback, which is
// what it would render on an unmanaged device.
package android.app.admin;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;

import java.util.function.Supplier;

public class DevicePolicyResourcesManager {

    /** @hide -- AOSP's constructor is @hide; the instance comes from
     *  DevicePolicyManager.getResources(). */
    DevicePolicyResourcesManager() { }

    /** AOSP getDrawable(String drawableId, String drawableStyle,
     *  Supplier<Drawable> defaultDrawableLoader): "@return the drawable...
     *  or the default drawable if the updated drawable was not set." */
    public Drawable getDrawable(String drawableId, String drawableStyle,
            Supplier<Drawable> defaultDrawableLoader) {
        return defaultDrawableLoader != null ? defaultDrawableLoader.get() : null;
    }

    /** AOSP getDrawable(String, String, String drawableSource,
     *  Supplier<Drawable>): the source-qualified form. */
    public Drawable getDrawable(String drawableId, String drawableStyle, String drawableSource,
            Supplier<Drawable> defaultDrawableLoader) {
        return defaultDrawableLoader != null ? defaultDrawableLoader.get() : null;
    }

    /** AOSP getDrawableForDensity(String, String, int density,
     *  Supplier<Drawable>). */
    public Drawable getDrawableForDensity(String drawableId, String drawableStyle, int density,
            Supplier<Drawable> defaultDrawableLoader) {
        return defaultDrawableLoader != null ? defaultDrawableLoader.get() : null;
    }

    /** AOSP getDrawableForDensity(String, String, String, int,
     *  Supplier<Drawable>). */
    public Drawable getDrawableForDensity(String drawableId, String drawableStyle,
            String drawableSource, int density, Supplier<Drawable> defaultDrawableLoader) {
        return defaultDrawableLoader != null ? defaultDrawableLoader.get() : null;
    }

    /** AOSP getDrawableAsIcon(String, String, Icon defaultIcon): "@return the
     *  drawable as an Icon, or defaultIcon if not set." Note the default here
     *  is a VALUE, not a supplier. */
    public Icon getDrawableAsIcon(String drawableId, String drawableStyle, Icon defaultIcon) {
        return defaultIcon;
    }

    /** AOSP getDrawableAsIcon(String, String, String, Icon). */
    public Icon getDrawableAsIcon(String drawableId, String drawableStyle, String drawableSource,
            Icon defaultIcon) {
        return defaultIcon;
    }

    /** AOSP getString(String stringId, Supplier<String> defaultStringLoader):
     *  "@return the string... or the default string if the updated string was
     *  not set." */
    public String getString(String stringId, Supplier<String> defaultStringLoader) {
        return defaultStringLoader != null ? defaultStringLoader.get() : null;
    }

    /** AOSP getString(String, Supplier<String>, Object... formatArgs): the
     *  format-argument form. AOSP applies String.format to whichever string it
     *  resolved, so the arguments are honoured on the default path too --
     *  omitting that would silently hand the caller a raw "%1$s". */
    public String getString(String stringId, Supplier<String> defaultStringLoader,
            Object... formatArgs) {
        String s = defaultStringLoader != null ? defaultStringLoader.get() : null;
        if (s == null || formatArgs == null || formatArgs.length == 0) return s;
        try {
            return String.format(s, formatArgs);
        } catch (Throwable t) {
            return s;
        }
    }
}
