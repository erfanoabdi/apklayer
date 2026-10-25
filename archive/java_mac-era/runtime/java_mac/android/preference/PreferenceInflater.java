// android.preference.PreferenceInflater -- builds a real Preference hierarchy
// from a preference XML resource. Package-private and non-API, exactly as AOSP's
// PreferenceInflater (which extends the package-private GenericInflater) is.
//
// ★ THIS CLOSES THE ONE GAP THE PACKAGE STILL HAD.
//
// Until now `PreferenceActivity.addPreferencesFromResource` and
// `PreferenceFragment.addPreferencesFromResource` were no-ops carrying a KNOWN
// GAP note: "Mac.apk has neither the inflater nor the preference list host ...
// a PreferenceActivity subclass gets an empty settings screen." The persistence
// half was covered generically by com.macapk.loader.PreferenceDefaultsScanner,
// so an app's settings VALUES were right, but the settings SCREEN was empty and
// findPreference() returned null for every key -- which breaks the extremely
// common `findPreference("x").setOnPreferenceChangeListener(...)` idiom with an
// NPE inside onCreate, and ActivityHost swallows onCreate exceptions, so the
// activity aborts silently (auto-memory: activity-oncreate-abort-null-singleton).
//
// HOW THIS DIFFERS FROM AOSP, and why:
//
//   AOSP walks the compiled XML with an XmlPullParser and constructs each
//   Preference through its (Context, AttributeSet) constructor; each constructor
//   then reads its own attributes via obtainStyledAttributes keyed by
//   com.android.internal.R.styleable ids. Mac.apk has no com.android.internal.R,
//   so the ATTRIBUTE READING moved to PreferenceAttrs (by name, same names, same
//   defaults, same place -- inside the constructor). Everything else here is
//   AOSP's own structure: same default package prefix, same
//   createItem-by-reflection, same recursive descent, same
//   addItemFromInflater(), same <intent>/<extra> child-tag handling, same
//   onMergeRoots semantics.
//
//   The parser comes from Resources.getXml(resId), which Mac.apk implements over
//   the apktool-decoded res/xml (see Resources.getXml -> ResourceIndex.openXml
//   -> DomXmlResourceParser).
//
// WHAT IT DOES NOT DO: it does not hide a failure. A tag whose class cannot be
// loaded or constructed throws InflateException, which is what AOSP does and
// what the no-shortcuts rule requires -- the CLAUDE.md note about a past
// temptation to flip a host View to GONE so an inflate error would not surface
// is about exactly this code path. A caller that wants to survive a bad tag has
// to catch it, as it would on a device.
package android.preference;

import android.content.Context;
import android.content.Intent;
import android.content.res.XmlResourceParser;
import android.util.AttributeSet;
import android.view.InflateException;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("deprecation")
class PreferenceInflater {

    /** AOSP PreferenceInflater.init: setDefaultPackage("android.preference."). */
    private static final String DEFAULT_PACKAGE = "android.preference.";

    private static final String INTENT_TAG_NAME = "intent";
    private static final String EXTRA_TAG_NAME = "extra";

    private static final Class<?>[] CONSTRUCTOR_SIGNATURE =
            new Class<?>[] { Context.class, AttributeSet.class };

    /** AOSP caches constructors in a static map keyed by tag name; same here.
     *  Concurrent because Mac.apk can inflate from more than one thread. */
    private static final Map<String, Constructor<?>> sConstructorMap =
            new java.util.concurrent.ConcurrentHashMap<String, Constructor<?>>();

    private final Context mContext;
    private final PreferenceManager mPreferenceManager;

    PreferenceInflater(Context context, PreferenceManager preferenceManager) {
        mContext = context;
        mPreferenceManager = preferenceManager;
    }

    Context getContext() { return mContext; }

    /**
     * AOSP GenericInflater.inflate(int resource, P root, boolean attachToRoot).
     *
     * @param resId  the preference XML
     * @param root   an existing root to merge into, or null to use the XML's own
     * @return the resulting root PreferenceScreen
     */
    Preference inflate(int resId, PreferenceGroup root, boolean attachToRoot) {
        if (mContext == null) return root;
        XmlResourceParser parser = null;
        try {
            parser = mContext.getResources().getXml(resId);
            return inflate(parser, root, attachToRoot);
        } catch (android.content.res.Resources.NotFoundException e) {
            // AOSP would throw here too (getXml is the only source). Rethrow as
            // InflateException so a caller sees ONE exception type from this
            // entry point, with the original cause attached.
            InflateException ie = new InflateException(
                    "Preference XML resource 0x" + Integer.toHexString(resId) + " not found");
            ie.initCause(e);
            throw ie;
        } finally {
            if (parser != null) {
                try { parser.close(); } catch (Throwable ignored) {}
            }
        }
    }

    /** AOSP GenericInflater.inflate(XmlPullParser, P, boolean). */
    Preference inflate(XmlPullParser parser, PreferenceGroup root, boolean attachToRoot) {
        try {
            final AttributeSet attrs = asAttributeSet(parser);

            int type;
            while ((type = parser.next()) != XmlPullParser.START_TAG
                    && type != XmlPullParser.END_DOCUMENT) {
                // AOSP: skip until the root start tag.
            }

            if (type != XmlPullParser.START_TAG) {
                throw new InflateException(
                        "No start tag found in the preference XML");
            }

            // AOSP creates the XML root item, then onMergeRoots decides which
            // root survives.
            Preference xmlRoot = createItemFromTag(parser.getName(), attrs);
            PreferenceGroup result = onMergeRoots(root, (PreferenceGroup) xmlRoot);

            rInflate(parser, result, attrs);

            return result;
        } catch (XmlPullParserException e) {
            InflateException ie = new InflateException(e.getMessage());
            ie.initCause(e);
            throw ie;
        } catch (IOException e) {
            InflateException ie = new InflateException(e.getMessage());
            ie.initCause(e);
            throw ie;
        }
    }

    /** AOSP PreferenceInflater.onMergeRoots. */
    private PreferenceGroup onMergeRoots(PreferenceGroup givenRoot, PreferenceGroup xmlRoot) {
        if (givenRoot == null) {
            if (mPreferenceManager != null) xmlRoot.onAttachedToHierarchy(mPreferenceManager);
            return xmlRoot;
        }
        return givenRoot;
    }

    /** AOSP GenericInflater.rInflate: recursive descent, children added through
     *  addItemFromInflater so a PreferenceGroup can veto (PreferenceCategory
     *  rejects a nested category, exactly as on a device). */
    private void rInflate(XmlPullParser parser, Preference parent, AttributeSet attrs)
            throws XmlPullParserException, IOException {
        final int depth = parser.getDepth();

        int type;
        while (((type = parser.next()) != XmlPullParser.END_TAG
                || parser.getDepth() > depth) && type != XmlPullParser.END_DOCUMENT) {

            if (type != XmlPullParser.START_TAG) continue;

            if (onCreateCustomFromTag(parser, parent, attrs)) continue;

            final String name = parser.getName();
            Preference item = createItemFromTag(name, attrs);

            if (parent instanceof PreferenceGroup) {
                ((PreferenceGroup) parent).addItemFromInflater(item);
            } else {
                throw new InflateException("<" + name + "> cannot be a child of <"
                        + (parent == null ? "null" : parent.getClass().getSimpleName())
                        + ">, which is not a PreferenceGroup");
            }

            rInflate(parser, item, attrs);
        }
    }

    /**
     * AOSP PreferenceInflater.onCreateCustomFromTag: `<intent>` and `<extra>`
     * are not preferences, they configure the PARENT preference.
     */
    private boolean onCreateCustomFromTag(XmlPullParser parser, Preference parentPreference,
            AttributeSet attrs) throws XmlPullParserException, IOException {
        final String tag = parser.getName();

        if (INTENT_TAG_NAME.equals(tag)) {
            Intent intent = parseIntentTag(attrs);
            if (intent != null && parentPreference != null) parentPreference.setIntent(intent);
            skipCurrentTag(parser);
            return true;
        }

        if (EXTRA_TAG_NAME.equals(tag)) {
            if (parentPreference != null) parseExtraTag(attrs, parentPreference.getExtras());
            skipCurrentTag(parser);
            return true;
        }

        return false;
    }

    /**
     * AOSP routes this through Intent.parseIntent(Resources, parser, attrs),
     * which reads the same six attributes off the same AttributeSet. Mac.apk's
     * Intent has no parseIntent, so the six are read here by name -- the
     * attribute names are the platform's and are frozen.
     * (frameworks/base/core/java/android/content/Intent.java, parseIntent.)
     */
    private Intent parseIntentTag(AttributeSet attrs) {
        String action = PreferenceAttrs.string(mContext, attrs, "action");
        String data = PreferenceAttrs.string(mContext, attrs, "data");
        String mimeType = PreferenceAttrs.string(mContext, attrs, "mimeType");
        String targetPackage = PreferenceAttrs.string(mContext, attrs, "targetPackage");
        String targetClass = PreferenceAttrs.string(mContext, attrs, "targetClass");

        Intent intent = new Intent();
        if (action != null) intent.setAction(action);
        if (data != null || mimeType != null) {
            android.net.Uri uri = null;
            if (data != null) {
                try { uri = android.net.Uri.parse(data); } catch (Throwable ignored) {}
            }
            intent.setDataAndType(uri, mimeType);
        }
        if (targetPackage != null && targetClass != null) {
            intent.setClassName(targetPackage, targetClass);
        } else if (targetPackage != null) {
            intent.setPackage(targetPackage);
        }
        return intent;
    }

    /**
     * AOSP routes this through Resources.parseBundleExtra("extra", attrs,
     * bundle), which reads android:name plus exactly one typed android:value and
     * puts it in the bundle with the matching type. Mirrored by name; the type
     * is inferred from the literal the same way aapt's typed value would have
     * been produced (true/false -> boolean, integral -> int, else string).
     */
    private void parseExtraTag(AttributeSet attrs, android.os.Bundle out) {
        if (out == null) return;
        String name = PreferenceAttrs.string(mContext, attrs, "name");
        if (name == null) return;
        String raw = PreferenceAttrs.raw(attrs, "value");
        if (raw == null) return;
        if (raw.charAt(0) == '@') {
            String resolved = PreferenceAttrs.string(mContext, attrs, "value");
            out.putString(name, resolved);
            return;
        }
        String v = raw.trim();
        if ("true".equalsIgnoreCase(v) || "false".equalsIgnoreCase(v)) {
            out.putBoolean(name, Boolean.parseBoolean(v));
            return;
        }
        try {
            out.putInt(name, Integer.decode(v));
            return;
        } catch (NumberFormatException ignored) {}
        out.putString(name, raw);
    }

    /** AOSP XmlUtils.skipCurrentTag. */
    private static void skipCurrentTag(XmlPullParser parser)
            throws XmlPullParserException, IOException {
        int outerDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
            // walk to the matching end tag
        }
    }

    /**
     * AOSP GenericInflater.createItem: load the class (prefixing a dot-free tag
     * with the default package, exactly as View inflation prefixes
     * "android.widget."), find its (Context, AttributeSet) constructor, and
     * instantiate. A custom app subclass carries a dot and is used as-is.
     */
    private Preference createItemFromTag(String name, AttributeSet attrs) {
        final boolean qualified = name.indexOf('.') >= 0;
        final String className = qualified ? name : DEFAULT_PACKAGE + name;

        try {
            Constructor<?> constructor = sConstructorMap.get(className);
            if (constructor == null) {
                ClassLoader cl = mContext.getClassLoader();
                Class<?> clazz = (cl != null ? Class.forName(className, false, cl)
                                             : Class.forName(className));
                constructor = clazz.getConstructor(CONSTRUCTOR_SIGNATURE);
                constructor.setAccessible(true);
                sConstructorMap.put(className, constructor);
            }
            Object item = constructor.newInstance(mContext, attrs);
            if (!(item instanceof Preference)) {
                throw new InflateException("<" + name + "> is not a Preference");
            }
            return (Preference) item;
        } catch (InflateException e) {
            throw e;
        } catch (ClassNotFoundException e) {
            InflateException ie = new InflateException("Error inflating preference class "
                    + className + " (from tag <" + name + ">)");
            ie.initCause(e);
            throw ie;
        } catch (NoSuchMethodException e) {
            InflateException ie = new InflateException(className
                    + " has no (Context, AttributeSet) constructor, so tag <" + name
                    + "> cannot be inflated");
            ie.initCause(e);
            throw ie;
        } catch (Exception e) {
            InflateException ie = new InflateException("Error inflating preference <"
                    + name + ">");
            ie.initCause(e);
            throw ie;
        }
    }

    /** Mac.apk's Resources.getXml returns a DomXmlResourceParser, which IS an
     *  XmlResourceParser and therefore already an AttributeSet -- the same
     *  identity AOSP relies on (Xml.asAttributeSet). A non-XmlResourceParser
     *  parser can only reach here if an app calls the package-private overload
     *  reflectively, so it is rejected rather than adapted. */
    private static AttributeSet asAttributeSet(XmlPullParser parser) {
        if (parser instanceof AttributeSet) return (AttributeSet) parser;
        throw new InflateException(
                "Preference XML parser does not implement AttributeSet: " + parser);
    }
}
