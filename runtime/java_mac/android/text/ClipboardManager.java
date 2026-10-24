package android.text;

/**
 * AOSP's legacy text-only clipboard interface.
 *
 * WHY THIS EXISTS. `android.content.ClipboardManager` already implements this contract's
 * three methods, but it did not EXTEND this type, because this type did not exist. That is
 * invisible until an app written against the pre-API-11 clipboard does
 *
 *     android.text.ClipboardManager cm =
 *         (android.text.ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
 *
 * which is still the documented way to reach the old API and is what OpenTTD does. Without
 * the supertype that cast cannot succeed, no matter how faithfully the methods themselves
 * behave. Measured references: OpenTTD and klooni.
 *
 * Mirrors frameworks/base/core/java/android/text/ClipboardManager.java (android13-release)
 * exactly: a `public abstract class` (NOT an interface -- getting that wrong produces
 * `IncompatibleClassChangeError: class X can not implement Y, because it is not an
 * interface`, which is the same trap that removed a whole media3 subsystem in v1.0.834),
 * carrying exactly these three abstract methods and nothing else.
 *
 * @deprecated Old text-only interface to the clipboard. See
 *             {@link android.content.ClipboardManager} for the modern API.
 */
@Deprecated
public abstract class ClipboardManager {
    public abstract CharSequence getText();
    public abstract void setText(CharSequence text);
    public abstract boolean hasText();
}
