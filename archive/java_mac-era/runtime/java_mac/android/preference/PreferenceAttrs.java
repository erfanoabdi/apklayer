// android.preference.PreferenceAttrs -- package-private helper that reads the
// `android:*` attributes of a preference XML tag off a raw AttributeSet and
// resolves any `@type/name` reference against the app's own Resources.
//
// NOT an AOSP class. It exists because of one structural difference:
//
//   AOSP's Preference constructors read their attributes through
//   context.obtainStyledAttributes(attrs, com.android.internal.R.styleable.X,
//   ...), keyed by the FRAMEWORK's compiled attribute ids. Mac.apk has no
//   com.android.internal.R, so there is no id array to hand that call; and
//   Mac.apk's TypedArray resolves an attribute by NAME anyway (see
//   Resources.Theme.obtainStyledAttributes), so the id round-trip buys nothing
//   here even where it would resolve.
//
// So the Preference subclasses in this package read the SAME attribute names
// through this helper, in the SAME place AOSP reads them (their own
// constructor), with the SAME defaults. Every method returns the caller's
// default when the attribute is absent or unresolvable, which is what an empty
// TypedArray slot does on AOSP.
//
// This is generic: nothing here knows any app, and the names are the platform's
// public attribute names, frozen by the same compatibility rules as the ids.
//
// KNOWN LIMIT, stated rather than hidden: a CUSTOM app Preference subclass reads
// its OWN declared attributes through its own obtainStyledAttributes call, which
// is framework code outside this package. Whatever that call resolves is what
// the subclass sees; this helper covers the platform attributes only.
package android.preference;

import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

final class PreferenceAttrs {
    static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    private PreferenceAttrs() {}

    /** The literal attribute text, or null. Never throws. */
    static String raw(AttributeSet attrs, String name) {
        if (attrs == null || name == null) return null;
        try {
            String v = attrs.getAttributeValue(ANDROID_NS, name);
            if (v == null || v.isEmpty()) return null;
            return v;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Split "@[pkg:]type/name" into {pkg, type, name}; null when not a reference. */
    private static String[] ref(String v) {
        if (v == null || v.length() < 3 || v.charAt(0) != '@') return null;
        String body = v.substring(1);
        if (body.startsWith("+")) body = body.substring(1);   // "@+id/foo"
        int slash = body.indexOf('/');
        if (slash <= 0) return null;
        String typePart = body.substring(0, slash);
        String name = body.substring(slash + 1);
        String pkg = null;
        int colon = typePart.indexOf(':');
        if (colon >= 0) {
            pkg = typePart.substring(0, colon);
            typePart = typePart.substring(colon + 1);
        }
        if (typePart.isEmpty() || name.isEmpty()) return null;
        return new String[] { pkg, typePart, name };
    }

    /** Resolve a reference to its numeric id, or 0. */
    static int resolveId(Context ctx, String v) {
        String[] r = ref(v);
        if (r == null || ctx == null) return 0;
        try {
            Resources res = ctx.getResources();
            if (res == null) return 0;
            String pkg = r[0] != null ? r[0] : ctx.getPackageName();
            return res.getIdentifier(r[2], r[1], pkg);
        } catch (Throwable t) {
            return 0;
        }
    }

    /** A resource-id-valued attribute (`android:layout`, `android:icon`, ...).
     *  Also accepts an already-numeric value, which is what a programmatically
     *  built AttributeSet can carry. */
    static int resId(Context ctx, AttributeSet attrs, String name, int def) {
        String v = raw(attrs, name);
        if (v == null) return def;
        if (v.charAt(0) == '@') {
            int id = resolveId(ctx, v);
            return id != 0 ? id : def;
        }
        try {
            return Integer.decode(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** A string-valued attribute, with `@string/x` resolved to its text. */
    static String string(Context ctx, AttributeSet attrs, String name) {
        String v = raw(attrs, name);
        if (v == null) return null;
        if (v.charAt(0) != '@') return v;
        int id = resolveId(ctx, v);
        if (id == 0) return null;   // unresolved reference is NOT a literal
        try {
            String s = ctx.getResources().getString(id);
            return s;
        } catch (Throwable t) {
            return null;
        }
    }

    static CharSequence text(Context ctx, AttributeSet attrs, String name) {
        return string(ctx, attrs, name);
    }

    static boolean bool(Context ctx, AttributeSet attrs, String name, boolean def) {
        String v = raw(attrs, name);
        if (v == null) return def;
        if (v.charAt(0) == '@') {
            int id = resolveId(ctx, v);
            if (id == 0) return def;
            try {
                return ctx.getResources().getBoolean(id);
            } catch (Throwable t) {
                return def;
            }
        }
        v = v.trim();
        if ("true".equalsIgnoreCase(v))  return true;
        if ("false".equalsIgnoreCase(v)) return false;
        return def;
    }

    static int integer(Context ctx, AttributeSet attrs, String name, int def) {
        String v = raw(attrs, name);
        if (v == null) return def;
        if (v.charAt(0) == '@') {
            int id = resolveId(ctx, v);
            if (id == 0) return def;
            try {
                return ctx.getResources().getInteger(id);
            } catch (Throwable t) {
                return def;
            }
        }
        try {
            return Integer.decode(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** `android:entries` / `android:entryValues` -- always an `@array/` in
     *  practice, but a comma-free literal is accepted as a one-element array so
     *  a hand-built AttributeSet is not silently dropped. */
    static CharSequence[] textArray(Context ctx, AttributeSet attrs, String name) {
        String v = raw(attrs, name);
        if (v == null) return null;
        if (v.charAt(0) == '@') {
            int id = resolveId(ctx, v);
            if (id == 0) return null;
            try {
                CharSequence[] arr = ctx.getResources().getTextArray(id);
                if (arr != null) return arr;
            } catch (Throwable ignored) {}
            try {
                String[] arr = ctx.getResources().getStringArray(id);
                return arr;
            } catch (Throwable t) {
                return null;
            }
        }
        return new CharSequence[] { v };
    }

    /** `android:defaultValue` on a MultiSelectListPreference: AOSP reads it with
     *  TypedArray.getTextArray and turns it into a Set<String>. */
    static Set<String> stringSet(Context ctx, AttributeSet attrs, String name) {
        CharSequence[] arr = textArray(ctx, attrs, name);
        if (arr == null) return null;
        String[] out = new String[arr.length];
        for (int i = 0; i < arr.length; i++) out[i] = arr[i] == null ? null : arr[i].toString();
        return new LinkedHashSet<String>(Arrays.asList(out));
    }

    /** `android:enabled="@bool/x"`-style attribute presence test, so a caller can
     *  tell "absent" from "explicitly set to the default value". AOSP gets this
     *  from TypedArray.getIndexCount(). */
    static boolean has(AttributeSet attrs, String name) {
        return raw(attrs, name) != null;
    }
}
