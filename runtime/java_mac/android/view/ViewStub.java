// android.view.ViewStub -- an invisible, zero-sized View used to lazily
// inflate a layout resource at runtime. When inflate() is called (or the stub
// is made VISIBLE/INVISIBLE), the stub inflates its android:layout, removes
// ITSELF from its parent, and inserts the inflated view in its place at the
// same index with the stub's LayoutParams.
//
// Why Mac.apk needs this (v1.0.406): Reddit's com.reddit.launch.main.MainActivity
// .onCreate references the android.view.ViewStub CLASS directly (a field type /
// cast). With the class absent, onCreate threw NoClassDefFoundError: android/
// view/ViewStub at link/verify time and aborted (ActivityHost caught it and
// continued, so the activity's onCreate never finished). Providing the class
// lets onCreate link and run.
//
// AOSP source mirrored: frameworks/base/core/java/android/view/ViewStub.java
// (observable behavior: GONE+zero-size until inflate; inflate() self-replaces;
// setVisibility(VISIBLE) triggers inflate; OnInflateListener callback).
package android.view;

import android.content.Context;
import java.lang.ref.WeakReference;

public final class ViewStub extends View {
    private int mInflatedId = NO_ID;
    private int mLayoutResource = 0;
    private WeakReference<View> mInflatedViewRef;
    private LayoutInflater mInflater;
    private OnInflateListener mInflateListener;

    public ViewStub(Context context) {
        this(context, 0);
    }

    /** @param layoutResource the layout resource to inflate on demand. */
    public ViewStub(Context context, int layoutResource) {
        super(context);
        mLayoutResource = layoutResource;
        initStub();
    }

    public ViewStub(Context context, android.util.AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ViewStub(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    /** BISECT GATE (2026-09-26, default ON; lane B-inflater, cluster B4 of
     *  docs/widget-fidelity-cluster_2026-09-25.md). MACAPK_VIEWSTUB_THEMED=0
     *  restores the pre-change read: the hand parse alone, which has no `?`
     *  branch. */
    static final boolean MACAPK_VIEWSTUB_THEMED =
        !"0".equals(System.getenv("MACAPK_VIEWSTUB_THEMED"));

    /** AOSP ViewStub(Context, AttributeSet, int, int) (android14-release
     *  core/java/android/view/ViewStub.java:106-121):
     *      super(context);
     *      final TypedArray a = context.obtainStyledAttributes(attrs,
     *              R.styleable.ViewStub, defStyleAttr, defStyleRes);
     *      mInflatedId = a.getResourceId(R.styleable.ViewStub_inflatedId, NO_ID);
     *      mLayoutResource = a.getResourceId(R.styleable.ViewStub_layout, 0);
     *      mID = a.getResourceId(R.styleable.ViewStub_id, NO_ID);
     *  i.e. the attributes are read through the THEME, so `android:layout="?attr"`
     *  resolves. The hand parse this class used before has no `?` branch.
     *  MEASURED victim: Mastodon's `loader_fragment` (and ten sibling layouts)
     *  declare `<ViewStub android:id="@id/error" android:layout="?errorViewLayout">`;
     *  Theme.Mastodon.AutoLightDark answers `@layout/appkit_error`, and the hand
     *  parse returned layout 0, so inflate() produced an empty FrameLayout and
     *  onCreateView NPE'd at TextView.setText (fleet run.log L3952).
     *  Offline, the app's own layout through our inflater: getLayoutResource()
     *  0x0 before, the appkit_error id after (debug/inflaterattr_gate.sh).
     *
     *  super(context, attrs) rather than AOSP's super(context): it is what sets
     *  the view id from the AttributeSet here (AOSP's mID read above), and it
     *  reads nothing else. Ids verified against public-final.xml:
     *  layout 0x010100f2, inflatedId 0x010100f3. The hand parse stays as the
     *  FALLBACK for a value the TypedArray could not resolve, so this is a strict
     *  superset of the old read. */
    public ViewStub(Context context, android.util.AttributeSet attrs, int defStyleAttr,
                    int defStyleRes) {
        super(context, attrs);
        // AOSP reads android:layout + android:inflatedId in the ViewStub
        // constructor (frameworks/base ViewStub(Context,AttributeSet,...)),
        // NOT via the generic attribute pass -- they are ViewStub-specific.
        // Our LayoutInflater doesn't special-case ViewStub, so without this an
        // app that does findViewById(stub).inflate() (e.g. Reddit's
        // MainActivity.onCreate) would hit mLayoutResource==0 and abort.
        if (attrs != null) {
            if (MACAPK_VIEWSTUB_THEMED && context != null) {
                try {
                    android.content.res.TypedArray a = context.obtainStyledAttributes(attrs,
                        new int[] { 0x010100f3 /*inflatedId*/, 0x010100f2 /*layout*/ },
                        defStyleAttr, defStyleRes);
                    mInflatedId = a.getResourceId(0, NO_ID);
                    mLayoutResource = a.getResourceId(1, 0);
                    a.recycle();
                } catch (Throwable ignored) {
                    // fall through to the hand parse below
                }
            }
            if (mLayoutResource == 0) {
                mLayoutResource = resolveAttrResId(context, attrs, "layout", 0);
            }
            if (mInflatedId == NO_ID) {
                mInflatedId = resolveAttrResId(context, attrs, "inflatedId", NO_ID);
            }
        }
        initStub();
    }

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    /** Resolve an android:<name> attribute whose value is a resource reference
     *  ("@layout/foo", "@id/bar", "@+id/bar", "@android:id/baz", or a numeric
     *  "@2131..." / "2131...") to its integer id. Our AttributeSet's
     *  getAttributeResourceValue only parses already-numeric strings, so for
     *  decoded "@type/name" refs we resolve via Resources.getIdentifier. */
    private static int resolveAttrResId(Context c, android.util.AttributeSet a, String name, int def) {
        String v;
        try { v = a.getAttributeValue(ANDROID_NS, name); } catch (Throwable t) { return def; }
        if (v == null) return def;
        v = v.trim();
        if (v.isEmpty()) return def;
        if (v.charAt(0) == '@') v = v.substring(1);
        // Numeric form ("2131231234" or hex after a '0x').
        try { return Integer.parseInt(v); } catch (NumberFormatException ignore) {}
        // "type/name" or "pkg:type/name".
        int slash = v.indexOf('/');
        if (slash <= 0) return def;
        String type = v.substring(0, slash);
        String resName = v.substring(slash + 1);
        String pkg = null;
        int colon = type.indexOf(':');
        if (colon >= 0) { pkg = type.substring(0, colon); type = type.substring(colon + 1); }
        if (type.startsWith("+")) type = type.substring(1); // "@+id/foo"
        try {
            if (pkg == null) pkg = c.getPackageName();
            int id = c.getResources().getIdentifier(resName, type, pkg);
            return id != 0 ? id : def;
        } catch (Throwable t) {
            return def;
        }
    }

    private void initStub() {
        // AOSP: a stub is invisible and draws nothing until inflated.
        setVisibility(GONE);
        setWillNotDraw(true);
    }

    public int getInflatedId() { return mInflatedId; }
    /** The id to assign to the inflated View (so callers can findViewById it). */
    public void setInflatedId(int inflatedId) { mInflatedId = inflatedId; }

    public int getLayoutResource() { return mLayoutResource; }
    public void setLayoutResource(int layoutResource) { mLayoutResource = layoutResource; }

    public void setLayoutInflater(LayoutInflater inflater) { mInflater = inflater; }
    public LayoutInflater getLayoutInflater() { return mInflater; }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // AOSP: a stub occupies zero space.
        setMeasuredDimension(0, 0);
    }

    @Override
    public void setVisibility(int visibility) {
        if (mInflatedViewRef != null) {
            // Already inflated: forward the visibility to the inflated view
            // (the stub itself is gone from the tree).
            View view = mInflatedViewRef.get();
            if (view != null) {
                view.setVisibility(visibility);
            }
        } else {
            super.setVisibility(visibility);
            if (visibility == VISIBLE || visibility == INVISIBLE) {
                inflate();
            }
        }
    }

    /** Inflate the layout resource identified by {@link #getLayoutResource()}
     *  and replace this StubbedView in its parent by the inflated view.
     *  @return the inflated View. */
    public View inflate() {
        final ViewParent viewParent = getParent();

        if (!(viewParent instanceof ViewGroup)) {
            // AOSP throws IllegalStateException here. In Mac.apk an app may call
            // inflate() on a stub that our inflater left detached (or already
            // replaced); throwing aborts the caller's onCreate (observed
            // breaking Reddit's MainActivity setup -> Compose never composed).
            // Return null gracefully instead.
            return null;
        }

        final ViewGroup parent = (ViewGroup) viewParent;
        final LayoutInflater factory =
                (mInflater != null) ? mInflater : LayoutInflater.from(getContext());

        final View view;
        if (mLayoutResource != 0) {
            view = factory.inflate(mLayoutResource, parent, false);
        } else {
            // No layoutResource resolved (the inflater didn't carry
            // android:layout and the app didn't setLayoutResource). AOSP throws
            // IllegalArgumentException; we substitute an empty placeholder so
            // the swap + the caller's result usage stay valid and onCreate
            // continues rather than aborting.
            view = new android.widget.FrameLayout(getContext());
        }

        if (mInflatedId != NO_ID) {
            view.setId(mInflatedId);
        }

        final int index = parent.indexOfChild(this);
        parent.removeViewInLayout(this);

        final ViewGroup.LayoutParams layoutParams = getLayoutParams();
        if (layoutParams != null) {
            parent.addView(view, index, layoutParams);
        } else {
            parent.addView(view, index);
        }

        mInflatedViewRef = new WeakReference<View>(view);

        if (mInflateListener != null) {
            mInflateListener.onInflate(this, view);
        }

        return view;
    }

    public void setOnInflateListener(OnInflateListener inflateListener) {
        mInflateListener = inflateListener;
    }

    /** Listener used to receive a notification after a ViewStub has
     *  successfully inflated its layout resource. */
    public interface OnInflateListener {
        /** Invoked after a ViewStub successfully inflated its layout resource.
         *  @param stub the ViewStub that initiated the inflation
         *  @param inflated the inflated View */
        void onInflate(ViewStub stub, View inflated);
    }
}
