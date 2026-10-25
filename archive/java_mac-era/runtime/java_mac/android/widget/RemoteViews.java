// android.widget.RemoteViews -- a description of a view hierarchy plus a list
// of deferred mutations (setTextViewText, setImageViewResource, ...) that can
// be inflated and applied in another process (app widgets / notifications).
// Was a bare `extends Object` stub. We record actions and apply them to a real
// inflated View locally (no cross-process Binder); the common setter surface
// is faithful. AOSP:
//   frameworks/base/core/java/android/widget/RemoteViews.java
//   public class RemoteViews implements Parcelable, LayoutInflater.Filter
package android.widget;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

public class RemoteViews implements Parcelable, LayoutInflater.Filter {
    private final String mPackage;
    private final int mLayoutId;
    private final List<Action> mActions = new ArrayList<Action>();

    private interface Action { void apply(View root); }

    public RemoteViews(String packageName, int layoutId) {
        mPackage = packageName;
        mLayoutId = layoutId;
    }

    public RemoteViews(RemoteViews src) {
        mPackage = src.mPackage;
        mLayoutId = src.mLayoutId;
        mActions.addAll(src.mActions);
    }

    public RemoteViews(Parcel parcel) {
        mPackage = parcel.readString();
        mLayoutId = parcel.readInt();
    }

    public String getPackage() { return mPackage; }
    public int getLayoutId() { return mLayoutId; }

    public void setTextViewText(final int viewId, final CharSequence text) {
        mActions.add(new Action() {
            @Override public void apply(View root) {
                View v = root.findViewById(viewId);
                if (v instanceof TextView) ((TextView) v).setText(text);
            }
        });
    }

    public void setTextViewTextSize(final int viewId, final int units, final float size) {
        mActions.add(new Action() {
            @Override public void apply(View root) {
                View v = root.findViewById(viewId);
                if (v instanceof TextView) ((TextView) v).setTextSize(units, size);
            }
        });
    }

    public void setTextColor(final int viewId, final int color) {
        mActions.add(new Action() {
            @Override public void apply(View root) {
                View v = root.findViewById(viewId);
                if (v instanceof TextView) ((TextView) v).setTextColor(color);
            }
        });
    }

    public void setImageViewResource(final int viewId, final int srcId) {
        mActions.add(new Action() {
            @Override public void apply(View root) {
                View v = root.findViewById(viewId);
                if (v instanceof ImageView) ((ImageView) v).setImageResource(srcId);
            }
        });
    }

    public void setImageViewBitmap(final int viewId, final android.graphics.Bitmap bitmap) {
        mActions.add(new Action() {
            @Override public void apply(View root) {
                View v = root.findViewById(viewId);
                if (v instanceof ImageView) ((ImageView) v).setImageBitmap(bitmap);
            }
        });
    }

    public void setViewVisibility(final int viewId, final int visibility) {
        mActions.add(new Action() {
            @Override public void apply(View root) {
                View v = root.findViewById(viewId);
                if (v != null) v.setVisibility(visibility);
            }
        });
    }

    public void setOnClickPendingIntent(final int viewId,
            final android.app.PendingIntent pendingIntent) {
        mActions.add(new Action() {
            @Override public void apply(View root) {
                final View v = root.findViewById(viewId);
                if (v != null) {
                    v.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View view) {
                            if (pendingIntent != null) {
                                try { pendingIntent.send(); } catch (Throwable ignored) {}
                            }
                        }
                    });
                }
            }
        });
    }

    public void setBoolean(int viewId, String methodName, boolean value) {
        invokeReflect(viewId, methodName, boolean.class, value);
    }
    public void setInt(int viewId, String methodName, int value) {
        invokeReflect(viewId, methodName, int.class, value);
    }
    public void setCharSequence(int viewId, String methodName, CharSequence value) {
        invokeReflect(viewId, methodName, CharSequence.class, value);
    }

    private void invokeReflect(final int viewId, final String methodName,
            final Class<?> paramType, final Object value) {
        mActions.add(new Action() {
            @Override public void apply(View root) {
                View v = root.findViewById(viewId);
                if (v == null) return;
                try {
                    v.getClass().getMethod(methodName, paramType).invoke(v, value);
                } catch (Throwable ignored) {}
            }
        });
    }

    public View apply(Context context, ViewGroup parent) {
        View result = inflateView(context, parent);
        performApply(result);
        return result;
    }

    public void reapply(Context context, View v) { performApply(v); }

    private View inflateView(Context context, ViewGroup parent) {
        LayoutInflater inflater = LayoutInflater.from(context);
        return inflater.inflate(mLayoutId, parent, false);
    }

    private void performApply(View root) {
        if (root == null) return;
        for (Action a : mActions) {
            try { a.apply(root); } catch (Throwable ignored) {}
        }
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mPackage);
        dest.writeInt(mLayoutId);
    }

    public static final Parcelable.Creator<RemoteViews> CREATOR =
            new Parcelable.Creator<RemoteViews>() {
        @Override public RemoteViews createFromParcel(Parcel parcel) {
            return new RemoteViews(parcel);
        }
        @Override public RemoteViews[] newArray(int size) {
            return new RemoteViews[size];
        }
    };

    // AOSP marker annotation for View classes usable from RemoteViews.
    // AOSP: frameworks/base/core/java/android/widget/RemoteViews.java
    //   @Target({ ElementType.TYPE })
    //   @Retention(RetentionPolicy.RUNTIME)
    //   public @interface RemoteView {}
    // The RUNTIME retention is load-bearing, not decoration: onLoadClass()
    // (the LayoutInflater.Filter hook, below) answers
    // clazz.isAnnotationPresent(RemoteView.class), and with javac's default
    // CLASS retention that call is false for EVERY class, so the filter would
    // reject every remotable view.
    @java.lang.annotation.Target({ java.lang.annotation.ElementType.TYPE })
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
    public @interface RemoteView {}

    // ==================================================================
    // v1.0.684: the rest of the common RemoteViews setter surface.
    //
    // RemoteViews drives BOTH notifications and app widgets, and 25 of 37
    // corpus apps call setViewPadding / removeAllViews / addView / setLong /
    // setContentDescription / clone. Each records an Action applied to the
    // inflated View, matching the existing design in this file.
    //
    // The generic setX(viewId, methodName, value) forms are AOSP's reflection
    // bridge: they invoke the named method on the target view. Implementing
    // them by real reflection (rather than a no-op) is what makes the long
    // tail of setter names work without enumerating every one.
    //
    // Source: frameworks/base/core/java/android/widget/RemoteViews.java
    // ==================================================================

    /** AOSP's reflection-based setter: invoke methodName(value) on the view. */
    private void reflectAction(final int viewId, final String methodName,
                               final Class<?> paramType, final Object value) {
        mActions.add(new Action() {
            @Override public void apply(View root) {
                View v = root.findViewById(viewId);
                if (v == null) return;
                try {
                    v.getClass().getMethod(methodName, paramType).invoke(v, value);
                } catch (Throwable t) {
                    // AOSP throws ActionException here; a widget that names a
                    // method the view does not have is an app bug, but killing
                    // the whole notification/widget inflate over it is worse.
                    android.util.Log.w("RemoteViews",
                            "failed to call " + methodName + " on view " + viewId, t);
                }
            }
        });
    }

    public void setLong(int viewId, String methodName, long value) {
        reflectAction(viewId, methodName, long.class, Long.valueOf(value));
    }

    public void setFloat(int viewId, String methodName, float value) {
        reflectAction(viewId, methodName, float.class, Float.valueOf(value));
    }

    public void setByte(int viewId, String methodName, byte value) {
        reflectAction(viewId, methodName, byte.class, Byte.valueOf(value));
    }

    public void setShort(int viewId, String methodName, short value) {
        reflectAction(viewId, methodName, short.class, Short.valueOf(value));
    }

    public void setChar(int viewId, String methodName, char value) {
        reflectAction(viewId, methodName, char.class, Character.valueOf(value));
    }

    public void setDouble(int viewId, String methodName, double value) {
        reflectAction(viewId, methodName, double.class, Double.valueOf(value));
    }

    public void setString(int viewId, String methodName, String value) {
        reflectAction(viewId, methodName, String.class, value);
    }

    public void setBundle(int viewId, String methodName, android.os.Bundle value) {
        reflectAction(viewId, methodName, android.os.Bundle.class, value);
    }

    public void setIntent(int viewId, String methodName, android.content.Intent value) {
        reflectAction(viewId, methodName, android.content.Intent.class, value);
    }

    public void setUri(int viewId, String methodName, android.net.Uri value) {
        reflectAction(viewId, methodName, android.net.Uri.class, value);
    }

    public void setBitmap(int viewId, String methodName, android.graphics.Bitmap value) {
        reflectAction(viewId, methodName, android.graphics.Bitmap.class, value);
    }

    public void setViewPadding(final int viewId, final int left, final int top,
                               final int right, final int bottom) {
        mActions.add(new Action() {
            @Override public void apply(View root) {
                View v = root.findViewById(viewId);
                if (v != null) v.setPadding(left, top, right, bottom);
            }
        });
    }

    public void setContentDescription(final int viewId, final CharSequence cd) {
        mActions.add(new Action() {
            @Override public void apply(View root) {
                View v = root.findViewById(viewId);
                if (v != null) v.setContentDescription(cd);
            }
        });
    }

    public void setViewEnabled(final int viewId, final boolean enabled) {
        mActions.add(new Action() {
            @Override public void apply(View root) {
                View v = root.findViewById(viewId);
                if (v != null) v.setEnabled(enabled);
            }
        });
    }

    public void removeAllViews(final int viewId) {
        mActions.add(new Action() {
            @Override public void apply(View root) {
                View v = root.findViewById(viewId);
                if (v instanceof ViewGroup) ((ViewGroup) v).removeAllViews();
            }
        });
    }

    /** Inflate the nested RemoteViews and append it to the target ViewGroup. */
    public void addView(final int viewId, final RemoteViews nestedView) {
        mActions.add(new Action() {
            @Override public void apply(View root) {
                View v = root.findViewById(viewId);
                if (!(v instanceof ViewGroup)) return;
                if (nestedView == null) return;
                View child = nestedView.apply(v.getContext(), (ViewGroup) v);
                if (child != null) ((ViewGroup) v).addView(child);
            }
        });
    }

    public void setChronometerCountDown(int viewId, boolean isCountDown) {
        reflectAction(viewId, "setCountDown", boolean.class, Boolean.valueOf(isCountDown));
    }

    public void setChronometer(int viewId, long base, String format, boolean started) {
        setLong(viewId, "setBase", base);
        setString(viewId, "setFormat", format);
        setBoolean(viewId, started ? "start" : "stop", started);
    }

    public void setProgressBar(int viewId, int max, int progress, boolean indeterminate) {
        setBoolean(viewId, "setIndeterminate", indeterminate);
        if (!indeterminate) {
            setInt(viewId, "setMax", max);
            setInt(viewId, "setProgress", progress);
        }
    }

    public void setImageViewIcon(int viewId, android.graphics.drawable.Icon icon) {
        reflectAction(viewId, "setImageIcon", android.graphics.drawable.Icon.class, icon);
    }

    public void setTextViewCompoundDrawables(int viewId, int start, int top, int end, int bottom) {
        mActions.add(new Action() {
            @Override public void apply(View root) {
                View v = root.findViewById(viewId);
                if (v instanceof TextView) {
                    ((TextView) v).setCompoundDrawablesWithIntrinsicBounds(start, top, end, bottom);
                }
            }
        });
    }

    /**
     * AOSP's clone() is a deep-ish copy: the action list is duplicated so the
     * copy can be mutated independently. Apps clone a base notification layout
     * and specialise it per-item, so sharing the list would cross-contaminate.
     */
    @Override public RemoteViews clone() {
        return new RemoteViews(this);
    }

    /** AOSP's landscape/portrait pair constructor. */
    public RemoteViews(RemoteViews landscape, RemoteViews portrait) {
        // Mac.apk has one orientation at a time and no cross-process apply;
        // the portrait layout is the one that gets inflated.
        this(portrait != null ? portrait : landscape);
    }

    /** AOSP setRemoteAdapter(viewId, items) (API 31): supplies an inline
     *  collection (the modern, RemoteViewsService-free path) to an adapter view.
     *  Mac.apk has no home-screen/widget host applying this RemoteViews, so the
     *  items are retained on the RemoteViews but there is nothing to bind them
     *  into. */
    public void setRemoteAdapter(int viewId, RemoteCollectionItems items) { }

    /** An inline list of RemoteViews items for a collection (API 31).
     *  Source: frameworks/base/core/java/android/widget/RemoteViews.java */
    public static final class RemoteCollectionItems implements Parcelable {
        private final long[] mIds;
        private final RemoteViews[] mViews;
        private final boolean mHasStableIds;
        private final int mViewTypeCount;
        private RemoteCollectionItems(long[] ids, RemoteViews[] views,
                boolean hasStableIds, int viewTypeCount) {
            mIds = ids; mViews = views; mHasStableIds = hasStableIds;
            mViewTypeCount = Math.max(1, viewTypeCount);
        }
        public int getItemCount() { return mViews.length; }
        public long getItemId(int position) { return mIds[position]; }
        public RemoteViews getItemView(int position) { return mViews[position]; }
        public int getViewTypeCount() { return mViewTypeCount; }
        public boolean hasStableIds() { return mHasStableIds; }

        // AOSP declares `public static final class RemoteCollectionItems
        // implements Parcelable` (frameworks/base/core/java/android/widget/
        // RemoteViews.java). The interface and its three members were missing
        // here, so an app that put a RemoteCollectionItems into a Bundle or an
        // Intent extra -- which is the ONLY way it reaches a widget host --
        // failed the `instanceof Parcelable` check before it ever got to write
        // itself. The wire format below matches this file's own RemoteViews
        // parcel format (package + layoutId), not AOSP's, because the two ends
        // of the pipe are both this class.
        public int describeContents() { return 0; }

        public void writeToParcel(Parcel dest, int flags) {
            dest.writeLongArray(mIds);
            dest.writeInt(mViews.length);
            for (RemoteViews rv : mViews) rv.writeToParcel(dest, flags);
            dest.writeBoolean(mHasStableIds);
            dest.writeInt(mViewTypeCount);
        }

        public static final Parcelable.Creator<RemoteCollectionItems> CREATOR =
                new Parcelable.Creator<RemoteCollectionItems>() {
            @Override public RemoteCollectionItems createFromParcel(Parcel in) {
                long[] ids = in.createLongArray();
                int n = in.readInt();
                RemoteViews[] views = new RemoteViews[n];
                for (int i = 0; i < n; i++) views[i] = new RemoteViews(in);
                boolean stable = in.readBoolean();
                int viewTypeCount = in.readInt();
                return new RemoteCollectionItems(ids, views, stable, viewTypeCount);
            }
            @Override public RemoteCollectionItems[] newArray(int size) {
                return new RemoteCollectionItems[size];
            }
        };

        public static final class Builder {
            private final java.util.ArrayList<Long> mIds = new java.util.ArrayList<>();
            private final java.util.ArrayList<RemoteViews> mViews = new java.util.ArrayList<>();
            private boolean mHasStableIds;
            private int mViewTypeCount;
            public Builder() { }
            public Builder addItem(long id, RemoteViews view) {
                mIds.add(id); mViews.add(view); return this;
            }
            public Builder setHasStableIds(boolean hasStableIds) {
                mHasStableIds = hasStableIds; return this;
            }
            public Builder setViewTypeCount(int viewTypeCount) {
                mViewTypeCount = viewTypeCount; return this;
            }
            public RemoteCollectionItems build() {
                long[] ids = new long[mIds.size()];
                for (int i = 0; i < ids.length; i++) ids[i] = mIds.get(i);
                int vtc = mViewTypeCount;
                if (vtc == 0) {
                    java.util.HashSet<String> seen = new java.util.HashSet<>();
                    for (RemoteViews rv : mViews) seen.add(String.valueOf(rv));
                    vtc = Math.max(1, seen.size());
                }
                return new RemoteCollectionItems(ids,
                        mViews.toArray(new RemoteViews[0]), mHasStableIds, vtc);
            }
        }
    }

    public void setViewLayoutWidth(int viewId, float width, int units) { }
    public void setViewLayoutWidthDimen(int viewId, int dimenResource) { }
    public void setViewLayoutHeight(int viewId, float height, int units) { }
    public void setViewLayoutHeightDimen(int viewId, int dimenResource) { }
    public void setViewOutlinePreferredRadius(int viewId, float radius, int units) { }
    public void setViewOutlinePreferredRadiusDimen(int viewId, int resId) { }
    public void setRemoteAdapter(int viewId, android.content.Intent intent) { }

    /** AOSP RemoteViews.setEmptyView(viewId, emptyViewId): equivalent to
     *  calling AdapterView#setEmptyView(View) once both ids resolve inside
     *  the inflated hierarchy. Unlike most of this file's reflection-based
     *  setters, AdapterView.setEmptyView(View) is a real, already-implemented
     *  accessor in this tree (android/widget/AdapterView.java), so this
     *  action has genuine effect: it registers the empty view AOSP shows in
     *  place of the AdapterView once its data set is (re)bound empty. Source:
     *  frameworks/base/core/java/android/widget/RemoteViews.java
     *  setEmptyView() / the private SetEmptyView action's apply(). */
    public void setEmptyView(final int viewId, final int emptyViewId) {
        mActions.add(new Action() {
            @Override public void apply(View root) {
                View v = root.findViewById(viewId);
                if (!(v instanceof AdapterView<?>)) return;
                View emptyView = root.findViewById(emptyViewId);
                if (emptyView == null) return;
                ((AdapterView<?>) v).setEmptyView(emptyView);
            }
        });
    }

    public void setPendingIntentTemplate(int viewId, android.app.PendingIntent pendingIntentTemplate) { }

    // Generic reflection setters (API 31 color/dimen/blend/icon families). Direct
    // values are applied via the existing invokeReflect bridge; the @*Res / @*Attr
    // forms need remote resource resolution Mac.apk does not do, so they are
    // recorded no-ops (the RemoteViews still inflates + applies its other actions).
    public void setColorInt(int viewId, String methodName, int notNight, int night) {
        invokeReflect(viewId, methodName, int.class, notNight);
    }
    public void setColor(int viewId, String methodName, int colorRes) { }
    public void setColorAttr(int viewId, String methodName, int colorAttr) { }
    public void setColorStateList(int viewId, String methodName, android.content.res.ColorStateList value) {
        invokeReflect(viewId, methodName, android.content.res.ColorStateList.class, value);
    }
    public void setColorStateList(int viewId, String methodName,
            android.content.res.ColorStateList notNight, android.content.res.ColorStateList night) {
        invokeReflect(viewId, methodName, android.content.res.ColorStateList.class, notNight);
    }
    public void setColorStateList(int viewId, String methodName, int colorStateListRes) { }
    public void setColorStateListAttr(int viewId, String methodName, int colorStateListAttr) { }
    public void setIntDimen(int viewId, String methodName, int dimenResource) { }
    public void setIntDimen(int viewId, String methodName, float value, int unit) { }
    public void setIntDimenAttr(int viewId, String methodName, int dimenAttr) { }
    public void setFloatDimen(int viewId, String methodName, int dimenResource) { }
    public void setFloatDimen(int viewId, String methodName, float value, int unit) {
        invokeReflect(viewId, methodName, float.class, value);
    }
    public void setFloatDimenAttr(int viewId, String methodName, int dimenAttr) { }
    public void setCharSequence(int viewId, String methodName, int stringResource) { }
    public void setCharSequenceAttr(int viewId, String methodName, int stringAttr) { }
    public void setBlendMode(int viewId, String methodName, android.graphics.BlendMode value) {
        invokeReflect(viewId, methodName, android.graphics.BlendMode.class, value);
    }
    public void setIcon(int viewId, String methodName, android.graphics.drawable.Icon notNight,
            android.graphics.drawable.Icon night) {
        invokeReflect(viewId, methodName, android.graphics.drawable.Icon.class, notNight);
    }
    public void setOnClickFillInIntent(int viewId, android.content.Intent fillInIntent) { }
    public void addStableView(int viewId, RemoteViews remoteViews, int stableId) { addView(viewId, remoteViews); }

    public void setCompoundButtonChecked(int viewId, boolean checked) {
        setBoolean(viewId, "setChecked", checked);
    }
    public void setImageViewUri(int viewId, android.net.Uri uri) { }
    public void setIcon(int viewId, String methodName, android.graphics.drawable.Icon icon) {
        invokeReflect(viewId, methodName, android.graphics.drawable.Icon.class, icon);
    }
    public void setOnClickResponse(int viewId, RemoteResponse response) { }
    public void setOnCheckedChangeResponse(int viewId, RemoteResponse response) { }
    /** AOSP RemoteResponse (API 31): a click/checked-change reaction (fill-in
     *  intent or pending intent). Mac.apk applies RemoteViews locally with no
     *  collection host, so a response is held but not dispatched. */
    public static class RemoteResponse {
        public static RemoteResponse fromPendingIntent(android.app.PendingIntent pendingIntent) { return new RemoteResponse(); }
        public static RemoteResponse fromFillInIntent(android.content.Intent fillInIntent) { return new RemoteResponse(); }
        public RemoteResponse addSharedElement(int viewId, String sharedElementName) { return this; }
    }

    public RemoteViews(java.util.Map<android.util.SizeF, RemoteViews> remoteViews) {
        mPackage = null;
        mLayoutId = 0;
    }
    public RemoteViews(String packageName, int layoutId, int viewId) {
        mPackage = packageName;
        mLayoutId = layoutId;
        // AOSP RemoteViews.java:10030 getViewId() reads this back; it used to
        // be dropped on the floor, so the (previously missing) getter had
        // nothing to return.
        mViewId = viewId;
    }

    // ==================================================================
    // API-36 surface completion (apidiff: android.widget.RemoteViews had 23
    // public members missing, plus 4 on RemoteCollectionItems). Bodies mirror
    // frameworks/base/core/java/android/widget/RemoteViews.java
    // (android16-release); cited line numbers are from that file.
    // ==================================================================

    private int mViewId;
    private int mLightBackgroundLayoutId;

    // AOSP :285-295 -- the @MarginType values setViewLayoutMargin* take. javac
    // inlines these into callers, so a missing one is invisible at runtime and
    // apidiff --live-only does not report them; they are still part of the API
    // and setViewLayoutMargin is unusable without them.
    public static final int MARGIN_LEFT   = 0;
    public static final int MARGIN_TOP    = 1;
    public static final int MARGIN_RIGHT  = 2;
    public static final int MARGIN_BOTTOM = 3;
    public static final int MARGIN_START  = 4;
    public static final int MARGIN_END    = 5;

    /** AOSP :10030 -- the view id passed to RemoteViews(String, int, int). */
    public int getViewId() { return mViewId; }

    /** AOSP :8914 -- the LayoutInflater.Filter hook: a class may be inflated
     *  from a RemoteViews only if it is annotated @RemoteView.
     *  AOSP declares `class RemoteViews implements Parcelable,
     *  LayoutInflater.Filter`, and the interface is declared on the class above.
     *  (android.view.LayoutInflater.Filter, LayoutInflater.java:97, gained its
     *  nested Filter interface in the concurrent android.view pass; before that
     *  this method had to stand alone.) */
    @Override
    public boolean onLoadClass(Class clazz) {
        return clazz != null && clazz.isAnnotationPresent(RemoteView.class);
    }

    // ---- the setInt-backed family ------------------------------------
    // Each of these is literally `setInt(viewId, "<name>", value)` in AOSP, and
    // this file's setInt already does the reflective invoke, so they are exact.
    /** AOSP :6681 -- AdapterViewAnimator.setDisplayedChild(int). */
    public void setDisplayedChild(int viewId, int childIndex) {
        setInt(viewId, "setDisplayedChild", childIndex);
    }
    /** AOSP :7225 -- AbsListView.smoothScrollToPosition(int). */
    public void setScrollPosition(int viewId, int position) {
        setInt(viewId, "smoothScrollToPosition", position);
    }
    /** AOSP :7235 -- ListView.smoothScrollByOffset(int). */
    public void setRelativeScrollPosition(int viewId, int offset) {
        setInt(viewId, "smoothScrollByOffset", offset);
    }
    /** AOSP :7919 / :7929 / :7939. */
    public void setAccessibilityTraversalBefore(int viewId, int nextId) {
        setInt(viewId, "setAccessibilityTraversalBefore", nextId);
    }
    public void setAccessibilityTraversalAfter(int viewId, int nextId) {
        setInt(viewId, "setAccessibilityTraversalAfter", nextId);
    }
    public void setLabelFor(int viewId, int labeledId) {
        setInt(viewId, "setLabelFor", labeledId);
    }

    /** AOSP :6658 / :6671 -- ViewContentNavigation, a no-arg reflective call
     *  of showNext()/showPrevious() on an AdapterViewAnimator/ViewAnimator. */
    public void showNext(int viewId)     { navigateAction(viewId, true); }
    public void showPrevious(int viewId) { navigateAction(viewId, false); }
    private void navigateAction(final int viewId, final boolean next) {
        mActions.add(new Action() {
            @Override public void apply(View root) {
                View v = root.findViewById(viewId);
                if (v == null) return;
                try {
                    v.getClass().getMethod(next ? "showNext" : "showPrevious").invoke(v);
                } catch (Throwable t) {
                    android.util.Log.w("RemoteViews", "showNext/showPrevious failed on " + viewId, t);
                }
            }
        });
    }

    /** AOSP :7959 SetRadioGroupCheckedAction. AOSP temporarily unhooks every
     *  child's OnCheckedChangeListener around the check() so a remote check
     *  does not fire the app's listener, then restores them. It finds those
     *  listeners through a framework-internal tag id
     *  (R.id.remote_checked_change_listener_tag) that only RemoteViews itself
     *  ever writes -- and this port never writes it, because
     *  setOnCheckedChangeResponse has no host to dispatch through. So the
     *  save/restore loop would iterate over nothing, and what remains is
     *  AOSP's own middle step. */
    public void setRadioGroupChecked(final int viewId, final int checkedId) {
        mActions.add(new Action() {
            @Override public void apply(View root) {
                final View target = root.findViewById(viewId);
                if (target == null) return;
                if (!(target instanceof RadioGroup)) {
                    android.util.Log.w("RemoteViews",
                            "Cannot check " + viewId + " because it's not a RadioGroup");
                    return;
                }
                ((RadioGroup) target).check(checkedId);
            }
        });
    }

    /** AOSP :6742 TextViewDrawableAction(viewId, isRelative=true, ...) --
     *  TextView.setCompoundDrawablesRelativeWithIntrinsicBounds(int,int,int,int).
     *  The absolute sibling already lives above in this file; this is the
     *  start/end (RTL-aware) form. */
    public void setTextViewCompoundDrawablesRelative(final int viewId, final int start,
            final int top, final int end, final int bottom) {
        mActions.add(new Action() {
            @Override public void apply(View root) {
                View v = root.findViewById(viewId);
                if (v instanceof TextView) {
                    ((TextView) v).setCompoundDrawablesRelativeWithIntrinsicBounds(
                            start, top, end, bottom);
                }
            }
        });
    }

    /** AOSP :7144 -- the appWidgetId-carrying overload is documented as
     *  "@deprecated ... use setRemoteAdapter(int, Intent)" and its whole body
     *  is that delegation. */
    public void setRemoteAdapter(int appWidgetId, int viewId, android.content.Intent intent) {
        setRemoteAdapter(viewId, intent);
    }

    /** AOSP :7968 -- an alternate layout the host may inflate on a light
     *  background. AOSP only stores it; the host decides whether to use it. */
    public void setLightBackgroundLayoutId(int layoutId) { mLightBackgroundLayoutId = layoutId; }

    /** AOSP :565 SetIntTagAction(viewId, com.android.internal.R.id.
     *  remoteViewsMetricsId, tag) -- a KEYED tag (setTag(int,Object)) under a
     *  framework-internal id, read only by the system's widget-usage metrics
     *  logger. There is no such logger here, and the key is an internal id we
     *  cannot name, so the tag is recorded on the RemoteViews itself.
     *  Deliberately NOT written onto the view's primary tag: that slot belongs
     *  to the app (View.setTag(Object)/getTag()), and AOSP never touches it
     *  here -- clobbering it would be a real, app-visible regression traded
     *  for an unobservable one. */
    public void setUsageEventTag(int viewId, int tag) {
        mUsageEventTags.put(Integer.valueOf(viewId), Integer.valueOf(tag));
    }
    private final java.util.HashMap<Integer, Integer> mUsageEventTags =
            new java.util.HashMap<Integer, Integer>();

    /** AOSP :7027 -- a PendingIntent fired when a stylus starts handwriting on
     *  the view. Mac.apk has no stylus input source and no host to fire a
     *  PendingIntent from a remote view, so, exactly like the sibling
     *  setOnClickResponse/setOnCheckedChangeResponse above, the response is
     *  accepted and never dispatched. */
    public void setOnStylusHandwritingPendingIntent(int viewId,
            android.app.PendingIntent pendingIntent) { }

    // ---- layout margins / sizes --------------------------------------
    /** AOSP :7292 LayoutParamAction(viewId, type, value, units) -- set one
     *  margin of the target's MarginLayoutParams from a complex dimension. */
    public void setViewLayoutMargin(int viewId, int type, float value, int units) {
        marginAction(viewId, type, value, units, null);
    }
    /** AOSP :7261 -- the same, from a dimen RESOURCE, resolved against the
     *  applied context's Resources at apply time (which is where AOSP resolves
     *  it too -- LayoutParamAction defers resolution until apply). */
    public void setViewLayoutMarginDimen(int viewId, int type, int dimen) {
        marginAction(viewId, type, 0f, 0, new int[] { dimen, 0 });
    }
    /** AOSP :7274 -- the same, from a theme ATTRIBUTE. */
    public void setViewLayoutMarginAttr(int viewId, int type, int attr) {
        marginAction(viewId, type, 0f, 0, new int[] { attr, 1 });
    }

    /** Shared body for the three setViewLayoutMargin* forms. `resolve` is null
     *  for a literal value, else {id, 0=dimen-resource | 1=theme-attribute}. */
    private void marginAction(final int viewId, final int type, final float value,
            final int units, final int[] resolve) {
        mActions.add(new Action() {
            @Override public void apply(View root) {
                View v = root.findViewById(viewId);
                if (v == null) return;
                android.view.ViewGroup.LayoutParams lp = v.getLayoutParams();
                if (!(lp instanceof android.view.ViewGroup.MarginLayoutParams)) return;
                android.view.ViewGroup.MarginLayoutParams mlp =
                        (android.view.ViewGroup.MarginLayoutParams) lp;
                final int px = resolvePx(v.getContext(), value, units, resolve);
                switch (type) {
                    case MARGIN_LEFT:   mlp.leftMargin = px; break;
                    case MARGIN_TOP:    mlp.topMargin = px; break;
                    case MARGIN_RIGHT:  mlp.rightMargin = px; break;
                    case MARGIN_BOTTOM: mlp.bottomMargin = px; break;
                    case MARGIN_START:  mlp.setMarginStart(px); break;
                    case MARGIN_END:    mlp.setMarginEnd(px); break;
                    default: return;
                }
                v.setLayoutParams(mlp);
            }
        });
    }

    /** AOSP :7331 / :7370 -- layout width/height from a theme attribute.
     *  The literal and dimen-resource forms already exist above this block. */
    public void setViewLayoutWidthAttr(int viewId, int widthAttr) {
        sizeAttrAction(viewId, widthAttr, true);
    }
    public void setViewLayoutHeightAttr(int viewId, int heightAttr) {
        sizeAttrAction(viewId, heightAttr, false);
    }
    private void sizeAttrAction(final int viewId, final int attr, final boolean isWidth) {
        mActions.add(new Action() {
            @Override public void apply(View root) {
                View v = root.findViewById(viewId);
                if (v == null) return;
                android.view.ViewGroup.LayoutParams lp = v.getLayoutParams();
                if (lp == null) return;
                final int px = resolvePx(v.getContext(), 0f, 0, new int[] { attr, 1 });
                if (isWidth) lp.width = px; else lp.height = px;
                v.setLayoutParams(lp);
            }
        });
    }

    /** Turn a LayoutParamAction's value into pixels the way AOSP's
     *  LayoutParamAction.getValue does: a literal complex dimension via
     *  TypedValue.applyDimension, a dimen resource via
     *  Resources.getDimensionPixelSize, an attribute via the applied theme. */
    private static int resolvePx(Context ctx, float value, int units, int[] resolve) {
        if (ctx == null) return 0;
        try {
            if (resolve == null) {
                return (int) android.util.TypedValue.applyDimension(units, value,
                        ctx.getResources().getDisplayMetrics());
            }
            if (resolve[1] == 0) {
                return ctx.getResources().getDimensionPixelSize(resolve[0]);
            }
            android.util.TypedValue tv = new android.util.TypedValue();
            if (ctx.getTheme() != null && ctx.getTheme().resolveAttribute(resolve[0], tv, true)) {
                return android.util.TypedValue.complexToDimensionPixelSize(
                        tv.data, ctx.getResources().getDisplayMetrics());
            }
        } catch (Throwable ignored) { }
        return 0;
    }

    /** AOSP :7400 SetViewOutlinePreferredRadiusAction with an attribute source.
     *  Its two siblings just above in this file (setViewOutlinePreferredRadius,
     *  setViewOutlinePreferredRadiusDimen) are already inert here because
     *  nothing in this runtime consumes a View's outline provider for
     *  clipping; this third form is inert for exactly the same reason and is
     *  deliberately kept consistent with them rather than half-wired. */
    public void setViewOutlinePreferredRadiusAttr(int viewId, int attrId) { }
}
