// android.preference.PreferenceActivity -- the legacy Activity that hosts a
// preference hierarchy, either as one list or as a two-level headers-plus-
// fragment settings app. Deprecated upstream since API 11, and still the
// settings screen of a large slice of the older-SDK corpus (Frozen Bubble,
// Anuto TD, Simple Solitaire, Unit Converter Ultimate all reach it).
//
// AOSP reference (aosp-mirror android16-release, fetched 2026-08-24):
//   frameworks/base/core/java/android/preference/PreferenceActivity.java
//   public abstract class PreferenceActivity extends ListActivity
//           implements PreferenceFragment.OnPreferenceStartFragmentCallback
//
// ★ WHAT CHANGED HERE (this file used to be 110 lines of honest stubs).
//
// The old file carried a "KNOWN GAP" note saying addPreferencesFromResource was
// a deliberate no-op because "Mac.apk has neither the inflater nor the
// preference list host ... a PreferenceActivity subclass gets an empty settings
// screen", and that faking findPreference() would be worse than an empty screen.
// That reasoning was right at the time and is now obsolete on both counts:
// PreferenceInflater builds the hierarchy, and PreferenceGroupAdapter +
// PreferenceScreen.bind() are the list host. So the screen inflates, the rows
// draw, taps route to the right Preference, and findPreference("key") returns
// the real object -- which is what the ubiquitous
// `findPreference("k").setOnPreferenceChangeListener(...)` idiom in onCreate
// needs to not NPE (and an NPE there is silently swallowed by ActivityHost,
// aborting onCreate; auto-memory activity-oncreate-abort-null-singleton).
//
// THREE DIVERGENCES, all structural, all stated at their site:
//
//  1. NOT abstract, unlike AOSP. Nothing can be harmed by the wider shape (an
//     Activity is always the app's own subclass), and it keeps any reflective
//     instantiation path working.
//  2. AOSP's two-pane layout comes from com.android.internal.R.layout
//     .preference_list_content, with ids prefs / headers / list_footer /
//     prefs_frame. Mac.apk has no internal layouts, so the same STRUCTURE is
//     built programmatically, carrying the frozen AOSP public ids where app
//     code looks them up (android.R.id.list, android.R.id.title).
//  3. showBreadCrumbs uses setTitle rather than a FragmentBreadCrumbs widget,
//     which is exactly AOSP's own fallback when the title view is not a
//     FragmentBreadCrumbs -- and here it never is, because that widget is
//     internal.
package android.preference;

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("deprecation")
public class PreferenceActivity extends android.app.ListActivity
        implements PreferenceFragment.OnPreferenceStartFragmentCallback,
                   PreferenceManager.OnPreferenceTreeClickListener {

    // ---- AOSP intent extras, verbatim spellings (apps put these on an Intent
    //      to deep-link straight into one settings fragment) ----------------

    public static final String EXTRA_SHOW_FRAGMENT = ":android:show_fragment";
    public static final String EXTRA_SHOW_FRAGMENT_ARGUMENTS = ":android:show_fragment_args";
    public static final String EXTRA_SHOW_FRAGMENT_TITLE = ":android:show_fragment_title";
    public static final String EXTRA_SHOW_FRAGMENT_SHORT_TITLE =
            ":android:show_fragment_short_title";
    public static final String EXTRA_NO_HEADERS = ":android:no_headers";

    public static final long HEADER_ID_UNDEFINED = -1;

    /** AOSP's back-stack tag for a pushed preference panel. */
    private static final String BACK_STACK_PREFS = ":android:prefs";

    // Frozen AOSP public ids (core/res/res/values/public-final.xml):
    //   <public type="id" name="list"  id="0x0102000a" />
    //   <public type="id" name="title" id="0x01020016" />
    // App code does findViewById(android.R.id.list) on a PreferenceActivity, so
    // the ListView must carry that id whether the layout came from a resource
    // or was built here.
    private static final int ID_LIST  = 0x0102000a;
    private static final int ID_TITLE = 0x01020016;

    /** Container id for the fragment pane. AOSP uses
     *  com.android.internal.R.id.prefs; there is no public id for it, so a
     *  private generated one is used -- nothing outside this class looks it up
     *  (AOSP's own callers all go through this class's methods). */
    private static final int ID_PREFS_CONTAINER = 0x7e0f0001;

    private PreferenceManager mPreferenceManager;

    private final ArrayList<Header> mHeaders = new ArrayList<Header>();
    private Header mCurHeader;

    private boolean mSinglePane = true;
    private FrameLayout mPrefsContainer;
    private ViewGroup mHeadersContainer;
    private FrameLayout mListFooter;

    /** AOSP's flag for "the hierarchy changed, re-bind on the next resume". */
    private boolean mRequiresBind;

    public PreferenceActivity() { super(); }

    // ---- lifecycle ---------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // AOSP inflates R.layout.preference_list_content here and decides
        // single- vs multi-pane. See divergence (2): the same structure is built
        // in code because there is no internal layout to inflate.
        setContentView(buildContentView());
    }

    private View buildContentView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        ListView list = new ListView(this);
        list.setId(ID_LIST);
        list.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        mHeadersContainer = list;
        body.addView(list);

        mPrefsContainer = new FrameLayout(this);
        mPrefsContainer.setId(ID_PREFS_CONTAINER);
        mPrefsContainer.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        // AOSP starts single-pane with the prefs pane GONE and swaps it in when
        // a header is chosen. Same here.
        mPrefsContainer.setVisibility(View.GONE);
        body.addView(mPrefsContainer);

        root.addView(body);

        mListFooter = new FrameLayout(this);
        mListFooter.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(mListFooter);

        return root;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mRequiresBind) bindPreferences();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mPreferenceManager != null) mPreferenceManager.dispatchActivityStop();
    }

    @Override
    protected void onDestroy() {
        if (mPreferenceManager != null) mPreferenceManager.dispatchActivityDestroy();
        super.onDestroy();
    }

    /** AOSP declares this `protected`; Mac.apk's android.app.Activity declares
     *  it `public`, and Java forbids narrowing an override. Divergence is in
     *  Activity, not here. */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // AOSP: this is how RingtonePreference (and any app Preference that
        // starts an activity for a result) receives its answer.
        if (mPreferenceManager != null) {
            mPreferenceManager.dispatchActivityResult(requestCode, resultCode, data);
        }
    }

    /** AOSP: in single-pane mode, back from the prefs pane returns to the
     *  header list rather than finishing the activity. */
    @Override
    public void onBackPressed() {
        if (mSinglePane && mPrefsContainer != null
                && mPrefsContainer.getVisibility() == View.VISIBLE
                && mHeadersContainer != null && !mHeaders.isEmpty()) {
            mPrefsContainer.setVisibility(View.GONE);
            mHeadersContainer.setVisibility(View.VISIBLE);
            mCurHeader = null;
            return;
        }
        super.onBackPressed();
    }

    // ---- the preference hierarchy -----------------------------------------

    /**
     * AOSP body: inflate `preferencesResId` into the existing root (creating one
     * if there is none) and re-bind.
     *
     * This was the package's one KNOWN GAP and it is closed -- see the file
     * header. A failure to inflate now THROWS (InflateException from
     * PreferenceInflater), as it does on a device; it is not swallowed and the
     * host view is not hidden to make it disappear.
     */
    public void addPreferencesFromResource(int preferencesResId) {
        requirePreferenceManager();
        setPreferenceScreen(mPreferenceManager.inflateFromResource(this, preferencesResId,
                getPreferenceScreen()));
    }

    /** AOSP body: inflate the preference XML named by the `android.preference`
     *  metadata of every activity the intent resolves to. */
    public void addPreferencesFromIntent(Intent intent) {
        requirePreferenceManager();
        setPreferenceScreen(mPreferenceManager.inflateFromIntent(intent, getPreferenceScreen()));
    }

    /** AOSP body: recursive key lookup over the root PreferenceScreen. */
    public Preference findPreference(CharSequence key) {
        if (mPreferenceManager == null) return null;
        return mPreferenceManager.findPreference(key);
    }

    /** AOSP hands back the manager built in onCreate. Created lazily here so it
     *  works regardless of how far the activity lifecycle got -- returning null
     *  used to break the whole Preference.getSharedPreferences() chain that
     *  Preference.persist*() runs through. */
    public PreferenceManager getPreferenceManager() {
        requirePreferenceManager();
        return mPreferenceManager;
    }

    private void requirePreferenceManager() {
        if (mPreferenceManager == null) {
            // AOSP: `new PreferenceManager(this, FIRST_REQUEST_CODE)` with
            // FIRST_REQUEST_CODE == 100, so preference-issued request codes do
            // not collide with an app's own low-numbered ones.
            mPreferenceManager = new PreferenceManager(this, 100);
            // AOSP does this in onCreate; doing it at construction keeps it
            // true no matter which entry point creates the manager first.
            mPreferenceManager.setOnPreferenceTreeClickListener(this);
        }
    }

    public PreferenceScreen getPreferenceScreen() {
        return mPreferenceManager == null ? null : mPreferenceManager.getPreferenceScreen();
    }

    public void setPreferenceScreen(PreferenceScreen screen) {
        requirePreferenceManager();
        if (mPreferenceManager.setPreferences(screen) && screen != null) {
            mRequiresBind = true;
            // AOSP posts the bind; binding straight away is equivalent here and
            // means findPreference/getListAdapter are usable the instant
            // addPreferencesFromResource returns, which is when app code in
            // onCreate uses them.
            bindPreferences();
        }
    }

    private void bindPreferences() {
        final PreferenceScreen preferenceScreen = getPreferenceScreen();
        if (preferenceScreen == null) return;
        ListView list = getListView();
        if (list == null) return;
        preferenceScreen.bind(list);
        mRequiresBind = false;
    }

    /** AOSP: the SharedPreferences the hosted hierarchy persists into. */
    public android.content.SharedPreferences getSharedPreferences() {
        return getPreferenceManager().getSharedPreferences();
    }

    /** AOSP body: false, i.e. "not handled". A subclass overrides it to react to
     *  a row tap; PreferenceScreen.onItemClick has already run the Preference's
     *  own performClick by then. */
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
                                         Preference preference) {
        return false;
    }

    /** AOSP body: a Preference declaring android:fragment opens that fragment in
     *  the prefs pane. This is the OnPreferenceStartFragmentCallback the class
     *  implements, and it is how a PreferenceFragment child hands control back
     *  to its hosting activity. */
    public boolean onPreferenceStartFragment(PreferenceFragment caller, Preference pref) {
        startPreferencePanel(pref.getFragment(), pref.getExtras(), pref.getTitleRes(),
                pref.getTitle(), null, 0);
        return true;
    }

    // ---- headers ----------------------------------------------------------

    /** AOSP: true once onBuildHeaders produced anything. */
    public boolean hasHeaders() {
        return !mHeaders.isEmpty() && mHeadersContainer != null
                && mHeadersContainer.getVisibility() == View.VISIBLE;
    }

    /** AOSP: re-run onBuildHeaders and refresh the list. */
    public void invalidateHeaders() {
        mHeaders.clear();
        onBuildHeaders(mHeaders);
    }

    /** AOSP: "Called when the activity needs its list of headers built."
     *  Overridden by subclasses; empty here, as upstream. */
    public void onBuildHeaders(List<Header> target) {
        // Should be overloaded by subclasses
    }

    /** AOSP: whether to show headers and a fragment side by side.
     *  AOSP reads com.android.internal.R.bool.preferences_prefer_dual_pane,
     *  which is true only on large screens. Mac.apk windows are phone-shaped by
     *  default, and the value is a resource we do not have, so this answers
     *  false -- the single-pane behaviour every phone reports. A subclass that
     *  wants two panes overrides this, which is the documented way. */
    public boolean onIsMultiPane() {
        return false;
    }

    public boolean isMultiPane() { return !mSinglePane; }

    /** AOSP body: the launching intent can suppress the header list entirely. */
    public boolean onIsHidingHeaders() {
        Intent intent = getIntent();
        return intent != null && intent.getBooleanExtra(EXTRA_NO_HEADERS, false);
    }

    /** AOSP body, including the throw: a two-pane settings activity with no
     *  fragment-bearing header has nothing to show in its second pane, and
     *  upstream treats that as an app bug rather than an empty screen. */
    public Header onGetInitialHeader() {
        for (int i = 0; i < mHeaders.size(); i++) {
            Header h = mHeaders.get(i);
            if (h.fragment != null) return h;
        }
        throw new IllegalStateException("Must have at least one header with a fragment");
    }

    /** AOSP body: null. */
    public Header onGetNewHeader() {
        return null;
    }

    /** AOSP body, verbatim. */
    public void onHeaderClick(Header header, int position) {
        if (header.fragment != null) {
            switchToHeader(header);
        } else if (header.intent != null) {
            startActivity(header.intent);
        }
    }

    /**
     * AOSP body: parse a `<preference-headers>` XML into `target`.
     *
     * AOSP reads each `<header>`'s attributes through
     * obtainStyledAttributes(attrs, R.styleable.PreferenceHeader) and uses
     * peekValue() to tell "a string resource" from "a literal string" -- that
     * distinction is what fills titleRes vs title. Here the same distinction is
     * available directly and more cheaply: the raw attribute text either starts
     * with '@' (a reference, so record the id) or does not (a literal, so record
     * the text). Same two fields, same rule.
     */
    public void loadHeadersFromResource(int resid, List<Header> target) {
        XmlResourceParser parser = null;
        try {
            parser = getResources().getXml(resid);
            AttributeSet attrs = (AttributeSet) parser;

            int type;
            while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                    && type != XmlPullParser.START_TAG) {
                // Parse next until start tag is found
            }

            String nodeName = parser.getName();
            if (!"preference-headers".equals(nodeName)) {
                throw new RuntimeException(
                        "XML document must start with <preference-headers> tag; found"
                                + nodeName + " at " + parser.getPositionDescription());
            }

            Bundle curBundle = null;

            final int outerDepth = parser.getDepth();
            while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                    && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
                if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) continue;

                nodeName = parser.getName();
                if ("header".equals(nodeName)) {
                    Header header = new Header();

                    header.id = PreferenceAttrs.resId(this, attrs, "id",
                            (int) HEADER_ID_UNDEFINED);
                    assignTitle(header, attrs);
                    header.iconRes = PreferenceAttrs.resId(this, attrs, "icon", 0);
                    header.fragment = PreferenceAttrs.raw(attrs, "fragment");

                    if (curBundle == null) curBundle = new Bundle();

                    final int innerDepth = parser.getDepth();
                    while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                            && (type != XmlPullParser.END_TAG
                                || parser.getDepth() > innerDepth)) {
                        if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) continue;

                        String innerNodeName = parser.getName();
                        if ("extra".equals(innerNodeName)) {
                            parseHeaderExtra(attrs, curBundle);
                            skipCurrentTag(parser);
                        } else if ("intent".equals(innerNodeName)) {
                            header.intent = parseHeaderIntent(attrs);
                            skipCurrentTag(parser);
                        } else {
                            skipCurrentTag(parser);
                        }
                    }

                    if (curBundle.size() > 0) {
                        header.fragmentArguments = curBundle;
                        curBundle = null;
                    }

                    target.add(header);
                } else {
                    skipCurrentTag(parser);
                }
            }
        } catch (XmlPullParserException e) {
            throw new RuntimeException("Error parsing headers", e);
        } catch (IOException e) {
            throw new RuntimeException("Error parsing headers", e);
        } finally {
            if (parser != null) {
                try { parser.close(); } catch (Throwable ignored) {}
            }
        }
    }

    /** The reference-vs-literal split AOSP gets from TypedArray.peekValue. */
    private void assignTitle(Header header, AttributeSet attrs) {
        String raw = PreferenceAttrs.raw(attrs, "title");
        if (raw != null) {
            if (raw.charAt(0) == '@') header.titleRes = PreferenceAttrs.resolveId(this, raw);
            else header.title = raw;
        }
        raw = PreferenceAttrs.raw(attrs, "summary");
        if (raw != null) {
            if (raw.charAt(0) == '@') header.summaryRes = PreferenceAttrs.resolveId(this, raw);
            else header.summary = raw;
        }
        raw = PreferenceAttrs.raw(attrs, "breadCrumbTitle");
        if (raw != null) {
            if (raw.charAt(0) == '@') {
                header.breadCrumbTitleRes = PreferenceAttrs.resolveId(this, raw);
            } else {
                header.breadCrumbTitle = raw;
            }
        }
        raw = PreferenceAttrs.raw(attrs, "breadCrumbShortTitle");
        if (raw != null) {
            if (raw.charAt(0) == '@') {
                header.breadCrumbShortTitleRes = PreferenceAttrs.resolveId(this, raw);
            } else {
                header.breadCrumbShortTitle = raw;
            }
        }
    }

    private void parseHeaderExtra(AttributeSet attrs, Bundle out) {
        String name = PreferenceAttrs.string(this, attrs, "name");
        if (name == null) return;
        String raw = PreferenceAttrs.raw(attrs, "value");
        if (raw == null) return;
        if (raw.charAt(0) == '@') {
            out.putString(name, PreferenceAttrs.string(this, attrs, "value"));
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

    private Intent parseHeaderIntent(AttributeSet attrs) {
        String action = PreferenceAttrs.string(this, attrs, "action");
        String data = PreferenceAttrs.string(this, attrs, "data");
        String mimeType = PreferenceAttrs.string(this, attrs, "mimeType");
        String targetPackage = PreferenceAttrs.string(this, attrs, "targetPackage");
        String targetClass = PreferenceAttrs.string(this, attrs, "targetClass");

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

    private static void skipCurrentTag(XmlPullParser parser)
            throws XmlPullParserException, IOException {
        int outerDepth = parser.getDepth();
        int type;
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > outerDepth)) {
            // walk to the matching end tag
        }
    }

    // ---- fragment navigation ----------------------------------------------

    /**
     * AOSP body, including the throw. Upstream's default REFUSES unless the
     * subclass overrides it, because accepting an arbitrary fragment class name
     * out of an Intent extra is the Fragment-injection vulnerability this method
     * was added (API 19) to close. Mirrored exactly: an app that deep-links into
     * its own settings must say which fragments it accepts, as it must on a
     * device.
     */
    protected boolean isValidFragment(String fragmentName) {
        int target = 0;
        try {
            android.content.pm.ApplicationInfo ai = getApplicationInfo();
            if (ai != null) target = ai.targetSdkVersion;
        } catch (Throwable ignored) {}
        if (target >= android.os.Build.VERSION_CODES.KITKAT) {
            throw new RuntimeException(
                    "Subclasses of PreferenceActivity must override isValidFragment(String)"
                            + " to verify that the Fragment class is valid! "
                            + this.getClass().getName()
                            + " has not checked if fragment " + fragmentName + " is valid.");
        }
        return true;
    }

    /** AOSP body, verbatim. */
    public Intent onBuildStartFragmentIntent(String fragmentName, Bundle args,
            int titleRes, int shortTitleRes) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClass(this, getClass());
        intent.putExtra(EXTRA_SHOW_FRAGMENT, fragmentName);
        intent.putExtra(EXTRA_SHOW_FRAGMENT_ARGUMENTS, args);
        intent.putExtra(EXTRA_SHOW_FRAGMENT_TITLE, titleRes);
        intent.putExtra(EXTRA_SHOW_FRAGMENT_SHORT_TITLE, shortTitleRes);
        intent.putExtra(EXTRA_NO_HEADERS, true);
        return intent;
    }

    /** AOSP body, verbatim. */
    public void startWithFragment(String fragmentName, Bundle args, Fragment resultTo,
            int resultRequestCode) {
        startWithFragment(fragmentName, args, resultTo, resultRequestCode, 0, 0);
    }

    /** AOSP body, verbatim. */
    public void startWithFragment(String fragmentName, Bundle args, Fragment resultTo,
            int resultRequestCode, int titleRes, int shortTitleRes) {
        Intent intent = onBuildStartFragmentIntent(fragmentName, args, titleRes, shortTitleRes);
        if (resultTo == null) {
            startActivity(intent);
        } else {
            resultTo.startActivityForResult(intent, resultRequestCode);
        }
    }

    /** AOSP body, verbatim. */
    public void startPreferenceFragment(Fragment fragment, boolean push) {
        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.replace(ID_PREFS_CONTAINER, fragment);
        if (push) {
            transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
            transaction.addToBackStack(BACK_STACK_PREFS);
        } else {
            transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
        }
        transaction.commitAllowingStateLoss();
        revealPrefsPane();
    }

    /** AOSP body, verbatim. */
    public void startPreferencePanel(String fragmentClass, Bundle args, int titleRes,
            CharSequence titleText, Fragment resultTo, int resultRequestCode) {
        Fragment f = Fragment.instantiate(this, fragmentClass, args);
        if (resultTo != null) {
            f.setTargetFragment(resultTo, resultRequestCode);
        }
        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.replace(ID_PREFS_CONTAINER, f);
        if (titleRes != 0) {
            transaction.setBreadCrumbTitle(titleRes);
        } else if (titleText != null) {
            transaction.setBreadCrumbTitle(titleText);
        }
        transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
        transaction.addToBackStack(BACK_STACK_PREFS);
        transaction.commitAllowingStateLoss();
        revealPrefsPane();
    }

    /** AOSP body, verbatim (the "TODO: be smarter about popping the stack"
     *  comment upstream is on the onBackPressed call). */
    public void finishPreferencePanel(Fragment caller, int resultCode, Intent resultData) {
        onBackPressed();
        if (caller != null && caller.getTargetFragment() != null) {
            caller.getTargetFragment().onActivityResult(caller.getTargetRequestCode(),
                    resultCode, resultData);
        }
    }

    /** AOSP body, verbatim. */
    public void switchToHeader(String fragmentName, Bundle args) {
        Header selectedHeader = null;
        for (int i = 0; i < mHeaders.size(); i++) {
            if (fragmentName.equals(mHeaders.get(i).fragment)) {
                selectedHeader = mHeaders.get(i);
                break;
            }
        }
        mCurHeader = selectedHeader;
        switchToHeaderInner(fragmentName, args);
    }

    /** AOSP body, verbatim. */
    public void switchToHeader(Header header) {
        if (mCurHeader == header) {
            getFragmentManager().popBackStack(BACK_STACK_PREFS,
                    FragmentManager.POP_BACK_STACK_INCLUSIVE);
        } else {
            if (header.fragment == null) {
                throw new IllegalStateException("can't switch to header that has no fragment");
            }
            switchToHeaderInner(header.fragment, header.fragmentArguments);
            mCurHeader = header;
        }
    }

    private void switchToHeaderInner(String fragmentName, Bundle args) {
        getFragmentManager().popBackStack(BACK_STACK_PREFS,
                FragmentManager.POP_BACK_STACK_INCLUSIVE);
        if (!isValidFragment(fragmentName)) {
            throw new IllegalArgumentException("Invalid fragment for this activity: "
                    + fragmentName);
        }

        Fragment f = Fragment.instantiate(this, fragmentName, args);
        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.setTransition(mSinglePane
                ? FragmentTransaction.TRANSIT_NONE
                : FragmentTransaction.TRANSIT_FRAGMENT_FADE);
        transaction.replace(ID_PREFS_CONTAINER, f);
        transaction.commitAllowingStateLoss();

        revealPrefsPane();
    }

    /** AOSP's single-pane swap, inline in switchToHeaderInner upstream. */
    private void revealPrefsPane() {
        if (mSinglePane && mPrefsContainer != null
                && mPrefsContainer.getVisibility() == View.GONE) {
            mPrefsContainer.setVisibility(View.VISIBLE);
            if (mHeadersContainer != null) mHeadersContainer.setVisibility(View.GONE);
        }
    }

    /** AOSP body reduced to its own fallback branch: upstream looks up
     *  android.R.id.title, casts it to the internal FragmentBreadCrumbs widget,
     *  and on a ClassCastException falls back to setTitle(title). There is no
     *  FragmentBreadCrumbs here, so the fallback is the only branch. */
    public void showBreadCrumbs(CharSequence title, CharSequence shortTitle) {
        if (title != null) setTitle(title);
    }

    /** AOSP body: forwards to the FragmentBreadCrumbs widget when one exists,
     *  and does nothing when one does not -- which is the case here, for the
     *  reason in showBreadCrumbs. The listener is deliberately not invoked: an
     *  up-affordance that is not on screen must not fire. */
    public void setParentTitle(CharSequence title, CharSequence shortTitle,
            View.OnClickListener listener) {
        // No breadcrumb widget to attach to; see showBreadCrumbs.
    }

    /** AOSP body, verbatim. */
    public void setListFooter(View view) {
        if (mListFooter == null) return;
        mListFooter.removeAllViews();
        mListFooter.addView(view, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));
    }

    // ---- Header ------------------------------------------------------------

    /**
     * AOSP: `public static final class Header implements Parcelable`. Every field
     * is public and part of the ABI; apps build these by hand in onBuildHeaders.
     */
    public static final class Header implements Parcelable {
        /** Default HEADER_ID_UNDEFINED, meaning no id. */
        public long id = HEADER_ID_UNDEFINED;

        public int titleRes;
        public CharSequence title;

        public int summaryRes;
        public CharSequence summary;

        public int breadCrumbTitleRes;
        public CharSequence breadCrumbTitle;

        public int breadCrumbShortTitleRes;
        public CharSequence breadCrumbShortTitle;

        public int iconRes;

        /** Full class name of the fragment to display when selected. */
        public String fragment;

        public Bundle fragmentArguments;

        public Intent intent;

        /** Optional additional data for a PreferenceActivity subclass. */
        public Bundle extras;

        public Header() {
        }

        public CharSequence getTitle(Resources res) {
            if (titleRes != 0) return res.getText(titleRes);
            return title;
        }

        public CharSequence getSummary(Resources res) {
            if (summaryRes != 0) return res.getText(summaryRes);
            return summary;
        }

        public CharSequence getBreadCrumbTitle(Resources res) {
            if (breadCrumbTitleRes != 0) return res.getText(breadCrumbTitleRes);
            return breadCrumbTitle;
        }

        public CharSequence getBreadCrumbShortTitle(Resources res) {
            if (breadCrumbShortTitleRes != 0) return res.getText(breadCrumbShortTitleRes);
            return breadCrumbShortTitle;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        /** AOSP writes the CharSequences through TextUtils.writeToParcel, which
         *  preserves spans. Mac.apk's TextUtils has that pair, so it is used;
         *  the field ORDER is AOSP's, so a Parcel written here is readable by
         *  the reader below and vice versa. */
        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeLong(id);
            dest.writeInt(titleRes);
            android.text.TextUtils.writeToParcel(title, dest, flags);
            dest.writeInt(summaryRes);
            android.text.TextUtils.writeToParcel(summary, dest, flags);
            dest.writeInt(breadCrumbTitleRes);
            android.text.TextUtils.writeToParcel(breadCrumbTitle, dest, flags);
            dest.writeInt(breadCrumbShortTitleRes);
            android.text.TextUtils.writeToParcel(breadCrumbShortTitle, dest, flags);
            dest.writeInt(iconRes);
            dest.writeString(fragment);
            dest.writeBundle(fragmentArguments);
            if (intent != null) {
                dest.writeInt(1);
                intent.writeToParcel(dest, flags);
            } else {
                dest.writeInt(0);
            }
            dest.writeBundle(extras);
        }

        public void readFromParcel(Parcel in) {
            id = in.readLong();
            titleRes = in.readInt();
            title = android.text.TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
            summaryRes = in.readInt();
            summary = android.text.TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
            breadCrumbTitleRes = in.readInt();
            breadCrumbTitle = android.text.TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
            breadCrumbShortTitleRes = in.readInt();
            breadCrumbShortTitle =
                    android.text.TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(in);
            iconRes = in.readInt();
            fragment = in.readString();
            fragmentArguments = in.readBundle();
            if (in.readInt() != 0) {
                intent = Intent.CREATOR.createFromParcel(in);
            }
            extras = in.readBundle();
        }

        Header(Parcel in) {
            readFromParcel(in);
        }

        public static final Parcelable.Creator<Header> CREATOR =
                new Parcelable.Creator<Header>() {
                    public Header createFromParcel(Parcel source) { return new Header(source); }
                    public Header[] newArray(int size) { return new Header[size]; }
                };
    }
}
