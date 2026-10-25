// android.view.WindowInsets -- the system-bar / display-cutout inset bag
// dispatched through the view tree.
//
// AOSP source, android-11.0.0_r48 (the API level the answer-differential
// golden was recorded at; line numbers below refer to it):
//   https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/
//   android-11.0.0_r48/core/java/android/view/WindowInsets.java
//
// WHY THIS IS THREE ARRAYS AND NOT A HANDFUL OF Rects.
// Until v1.0.923 this class stored ONE `systemWindowInsets` bag and answered
// every system-bar type out of it. That shape passes the deprecated getters
// (getSystemWindowInsetTop and friends) and fails EVERY typed one, because on
// AOSP the per-type values are genuinely independent:
//
//   - assignCompatInsets (:259-263) puts ONLY `top` on statusBars and
//     left/right/bottom on navigationBars. One Rect stored whole answers the
//     status bar's slot with the navigation bar's bottom, which is the
//     double-count that renders as "content shifted down".
//   - getInsets(mask) (:203-220) is a componentwise MAX across the requested
//     slots. Never a sum, never "the first one set".
//   - getInsetsIgnoringVisibility reads a SECOND, independent map (:355-357)
//     and THROWS for the IME, whose height is not knowable in advance.
//   - isVisible(mask) (:369-379) reads a THIRD map and ANDs over it. It is not
//     derivable from "are the insets non-zero": a bar can be hidden while the
//     app still knows how tall it would be.
//
// So we now carry AOSP's own three maps, indexed by indexOf(type):
//   mTypeInsetsMap      current insets per type
//   mTypeMaxInsetsMap   the same ignoring visibility (source of getStableInsets)
//   mTypeVisibilityMap  whether each type is on screen right now
// plus the three "consumed" booleans that decide isConsumed(), and the compat
// pair (mCompatInsetsTypes / mCompatIgnoreVisibility) that the deprecated
// getters read through.
//
// COMPATIBILITY WITH THE MAC.APK DISPATCH PATH. ViewCanvasDriver builds the
// dispatched bag with `new Builder().setSystemWindowInsets(sys).setStableInsets(sys)`.
// Under the split above, getSystemWindowInsets() comes back as
// getInsets(mTypeInsetsMap, systemBars()) = max(statusBars, navigationBars) =
// the original Rect, bit for bit, so every deprecated reader (including
// View.onApplyWindowInsets) sees exactly what it saw before. What changes is
// that getInsets(statusBars()) now answers the STATUS BAR rather than the
// whole rect, which is what AndroidX and Material actually ask for.
//
// The values come from Mac.apk's runtime (`ViewCanvasDriver.dispatchSystemBarInsets`)
// reflecting the macOS-side status bar (top) + gesture-nav inset (bottom).
package android.view;

import android.graphics.Insets;
import android.graphics.Rect;
import java.util.Arrays;
import java.util.Objects;

public class WindowInsets {
    // Bit values follow `android.view.WindowInsets.Type.*` exactly
    // (AOSP :1281-1300) so an androidx-built
    // `WindowInsetsCompat.Type.toPlatformType(int)` mapping round-trips
    // through us correctly.
    private static final int FIRST                     = 1;
    private static final int STATUS_BARS               = FIRST;
    private static final int NAVIGATION_BARS           = 1 << 1;
    private static final int CAPTION_BAR               = 1 << 2;
    private static final int IME                       = 1 << 3;
    private static final int SYSTEM_GESTURES           = 1 << 4;
    private static final int MANDATORY_SYSTEM_GESTURES = 1 << 5;
    private static final int TAPPABLE_ELEMENT          = 1 << 6;
    private static final int DISPLAY_CUTOUT            = 1 << 7;
    private static final int LAST                      = 1 << 8;
    private static final int WINDOW_DECOR              = LAST;
    private static final int SIZE                      = 9;
    // AOSP :1456-1461: "All system bars. Includes statusBars(), captionBar() as
    // well as navigationBars(), but not ime()." A reimplementation written from
    // the javadoc's shorter phrasing drops CAPTION_BAR and answers 3, after
    // which every getInsets(systemBars()) in every AndroidX app reads the wrong
    // slots.
    private static final int SYSTEM_BARS = STATUS_BARS | NAVIGATION_BARS | CAPTION_BAR;
    /** AOSP Type.all() (:1464-1471): every inset type combined. */
    private static final int ALL = 0xFFFFFFFF;

    /** AOSP Type.indexOf (:1304-1328). Deliberately a switch on the EXACT
     *  value, throwing for anything else: a duplicated or non-power-of-two
     *  mask must fail loudly here rather than silently aliasing two types
     *  onto one slot. */
    static int indexOf(int type) {
        switch (type) {
            case STATUS_BARS:               return 0;
            case NAVIGATION_BARS:           return 1;
            case CAPTION_BAR:               return 2;
            case IME:                       return 3;
            case SYSTEM_GESTURES:           return 4;
            case MANDATORY_SYSTEM_GESTURES: return 5;
            case TAPPABLE_ELEMENT:          return 6;
            case DISPLAY_CUTOUT:            return 7;
            case WINDOW_DECOR:              return 8;
            default:
                throw new IllegalArgumentException("type needs to be >= FIRST and <= LAST,"
                        + " type=" + type);
        }
    }

    /** Stand-in for AOSP's `DisplayCutout.NO_CUTOUT`: a non-null but EMPTY
     *  cutout. The distinction matters even though Mac.apk windows never have a
     *  notch, because "there is a cutout slot and it is empty" and "the cutout
     *  has been consumed" are different states and isConsumed() reads the
     *  difference. android/view/DisplayCutout.java does not define the constant
     *  (it is a stub owned elsewhere), so it is materialised here. */
    private static final DisplayCutout NO_CUTOUT = new DisplayCutout();

    /** AOSP DisplayCutout.isEmpty(): no safe insets and no bounding rects.
     *  Every DisplayCutout this tree can produce is empty (macOS windows have
     *  no notch), but the test is written against the public getters rather
     *  than assumed, so a future real cutout is handled correctly. */
    private static boolean isEmptyCutout(DisplayCutout c) {
        if (c == null) return true;
        try {
            return c.getSafeInsetLeft() == 0 && c.getSafeInsetTop() == 0
                    && c.getSafeInsetRight() == 0 && c.getSafeInsetBottom() == 0
                    && (c.getBoundingRects() == null || c.getBoundingRects().isEmpty());
        } catch (Throwable t) {
            return true;
        }
    }

    /** AOSP API-30 (:114-118): a process-wide singleton representing "no
     *  insets, already consumed". androidx.core.view.WindowInsetsCompat reads
     *  this as its default/sentinel value; a missing field throws
     *  NoSuchFieldError from any view whose compat code touches it (28/37
     *  corpus apps -- the widest single field gap found in this lane).
     *  Built exactly as AOSP builds it: `new WindowInsets((Rect) null, null,
     *  false, false, null)`, i.e. both type maps null (= consumed) and a null
     *  cutout (= consumed), so isConsumed() answers true. */
    public static final WindowInsets CONSUMED =
        new WindowInsets((Rect) null, null, false, false, null);

    private final Insets[] mTypeInsetsMap;
    private final Insets[] mTypeMaxInsetsMap;
    private final boolean[] mTypeVisibilityMap;
    private final boolean mIsRound;
    private final DisplayCutout mDisplayCutout;
    private final boolean mAlwaysConsumeSystemBars;
    private final boolean mSystemWindowInsetsConsumed;
    private final boolean mStableInsetsConsumed;
    private final boolean mDisplayCutoutConsumed;
    private final int mCompatInsetsTypes;
    private final boolean mCompatIgnoreVisibility;

    /** Not an AOSP constructor. `View.getRootWindowInsets()` hands out a
     *  non-null stub bag (see the comment at its definition: axmol's
     *  getSafeInsets skips the null check and dereferences the result), and
     *  that call site is not owned by this file, so the no-arg shape stays.
     *  Both maps exist but are empty, which reproduces the pre-v1.0.923
     *  observable answers exactly: isConsumed() false, hasInsets() false,
     *  getDisplayCutout() null. */
    public WindowInsets() {
        this(new Insets[SIZE], new Insets[SIZE], new boolean[SIZE], false, false, null,
             SYSTEM_BARS, false);
    }

    /** AOSP's deprecated Rect constructor (:120-133). Kept because it is the
     *  only route to the compat VISIBILITY map: createCompatVisibilityMap marks
     *  a type visible exactly when its slot is non-empty, which is the right
     *  answer for a caller that only has a Rect to give. */
    public WindowInsets(Rect systemWindowInsetsRect, Rect stableInsetsRect,
            boolean isRound, boolean alwaysConsumeSystemBars, DisplayCutout displayCutout) {
        this(createCompatTypeMap(systemWindowInsetsRect), createCompatTypeMap(stableInsetsRect),
             createCompatVisibilityMap(createCompatTypeMap(systemWindowInsetsRect)),
             isRound, alwaysConsumeSystemBars, displayCutout, SYSTEM_BARS,
             false /* compatIgnoreVisibility */);
    }

    /** AOSP copy constructor (:175-183). androidx WindowInsetsCompat (Jetchat /
     *  any app reading insets) does `new WindowInsets(src)` to snapshot; a
     *  missing ctor threw NoSuchMethodError. v1.0.429. */
    public WindowInsets(WindowInsets src) {
        this(src == null || src.mSystemWindowInsetsConsumed ? null : src.mTypeInsetsMap,
             src == null || src.mStableInsetsConsumed ? null : src.mTypeMaxInsetsMap,
             src == null ? new boolean[SIZE] : src.mTypeVisibilityMap,
             src != null && src.mIsRound,
             src != null && src.mAlwaysConsumeSystemBars,
             src == null ? null : displayCutoutCopyConstructorArgument(src),
             src == null ? SYSTEM_BARS : src.mCompatInsetsTypes,
             src != null && src.mCompatIgnoreVisibility);
    }

    /** AOSP's real constructor (:145-172). A null type map means "that half is
     *  fully consumed", which is what isConsumed() reads. */
    private WindowInsets(Insets[] typeInsetsMap, Insets[] typeMaxInsetsMap,
            boolean[] typeVisibilityMap, boolean isRound, boolean alwaysConsumeSystemBars,
            DisplayCutout displayCutout, int compatInsetsTypes, boolean compatIgnoreVisibility) {
        mSystemWindowInsetsConsumed = typeInsetsMap == null;
        mTypeInsetsMap = mSystemWindowInsetsConsumed ? new Insets[SIZE] : typeInsetsMap.clone();

        mStableInsetsConsumed = typeMaxInsetsMap == null;
        mTypeMaxInsetsMap = mStableInsetsConsumed ? new Insets[SIZE] : typeMaxInsetsMap.clone();

        mTypeVisibilityMap = typeVisibilityMap != null ? typeVisibilityMap : new boolean[SIZE];
        mIsRound = isRound;
        mAlwaysConsumeSystemBars = alwaysConsumeSystemBars;
        mCompatInsetsTypes = compatInsetsTypes;
        mCompatIgnoreVisibility = compatIgnoreVisibility;

        mDisplayCutoutConsumed = displayCutout == null;
        mDisplayCutout = (mDisplayCutoutConsumed || isEmptyCutout(displayCutout))
                ? null : displayCutout;
    }

    private static DisplayCutout displayCutoutCopyConstructorArgument(WindowInsets w) {
        if (w.mDisplayCutoutConsumed) return null;
        if (w.mDisplayCutout == null) return NO_CUTOUT;
        return w.mDisplayCutout;
    }

    /** AOSP :199-220. Componentwise MAX over every slot named by typeMask,
     *  skipping the slots nobody has written. Bits above LAST (e.g. the API-35
     *  systemOverlays() value) are simply not iterated, exactly as on AOSP. */
    static Insets getInsets(Insets[] typeInsetsMap, int typeMask) {
        Insets result = null;
        for (int i = FIRST; i <= LAST; i = i << 1) {
            if ((typeMask & i) == 0) continue;
            Insets insets = typeInsetsMap[indexOf(i)];
            if (insets == null) continue;
            result = (result == null) ? insets : Insets.max(result, insets);
        }
        return result == null ? Insets.NONE : result;
    }

    /** AOSP :225-233: write `insets` into every slot named by typeMask. */
    private static void setInsets(Insets[] typeInsetsMap, int typeMask, Insets insets) {
        for (int i = FIRST; i <= LAST; i = i << 1) {
            if ((typeMask & i) == 0) continue;
            typeInsetsMap[indexOf(i)] = insets;
        }
    }

    private static Insets[] createCompatTypeMap(Rect insets) {
        if (insets == null) return null;
        Insets[] typeInsetsMap = new Insets[SIZE];
        assignCompatInsets(typeInsetsMap, insets);
        return typeInsetsMap;
    }

    /** AOSP :259-263, verbatim in effect: ONLY `top` lands on statusBars, and
     *  left/right/bottom land on navigationBars. This one method is what makes
     *  a single-Rect implementation fail every typed getter. */
    static void assignCompatInsets(Insets[] typeInsetsMap, Rect insets) {
        typeInsetsMap[indexOf(STATUS_BARS)] = Insets.of(0, insets.top, 0, 0);
        typeInsetsMap[indexOf(NAVIGATION_BARS)] =
                Insets.of(insets.left, 0, insets.right, insets.bottom);
    }

    /** AOSP :265-277: a type whose compat slot is non-empty counts as visible.
     *  Only the Rect constructor uses this; Builder.setInsets deliberately does
     *  NOT touch visibility (:1147-1153), because a builder caller that cares
     *  says so through setVisible. */
    private static boolean[] createCompatVisibilityMap(Insets[] typeInsetsMap) {
        boolean[] typeVisibilityMap = new boolean[SIZE];
        if (typeInsetsMap == null) return typeVisibilityMap;
        for (int i = FIRST; i <= LAST; i = i << 1) {
            int index = indexOf(i);
            if (!Insets.NONE.equals(typeInsetsMap[index])) typeVisibilityMap[index] = true;
        }
        return typeVisibilityMap;
    }

    /** Returns the DisplayCutout for the window, or null if there is none.
     *  Mac.apk windows have no notch, so an empty cutout normalises to null in
     *  the constructor exactly as AOSP does. */
    public DisplayCutout getDisplayCutout() { return mDisplayCutout; }

    // ---- the deprecated compat surface -------------------------------------
    // AOSP :305-320: getSystemWindowInsets() reads getInsets(mCompatInsetsTypes)
    // or, when the producer set compatIgnoreVisibility, the max map with the IME
    // folded back in. Builder.build() always produces
    // compatInsetsTypes=systemBars() / compatIgnoreVisibility=false (:1273-1278),
    // so on a Builder-built bag this is exactly getInsets(systemBars()).

    public Insets getSystemWindowInsets() {
        Insets result = mCompatIgnoreVisibility
                ? getInsetsIgnoringVisibility(mCompatInsetsTypes & ~IME)
                : getInsets(mCompatInsetsTypes);
        // The IME has no maximum, so it cannot come out of the max map; AOSP
        // folds it back in by hand.
        if ((mCompatInsetsTypes & IME) != 0 && mCompatIgnoreVisibility) {
            result = Insets.max(result, getInsets(IME));
        }
        return result;
    }

    public int getSystemWindowInsetLeft()   { return getSystemWindowInsets().left;   }
    public int getSystemWindowInsetTop()    { return getSystemWindowInsets().top;    }
    public int getSystemWindowInsetRight()  { return getSystemWindowInsets().right;  }
    public int getSystemWindowInsetBottom() { return getSystemWindowInsets().bottom; }

    /** AOSP :628-630: the stable insets are the MAX map read through the compat
     *  mask, not the current one. */
    public Insets getStableInsets() { return getInsets(mTypeMaxInsetsMap, mCompatInsetsTypes); }
    public int getStableInsetLeft()   { return getStableInsets().left;   }
    public int getStableInsetTop()    { return getStableInsets().top;    }
    public int getStableInsetRight()  { return getStableInsets().right;  }
    public int getStableInsetBottom() { return getStableInsets().bottom; }
    public boolean hasStableInsets()  { return !getStableInsets().equals(Insets.NONE); }

    /** AOSP :758-760 / :790-792 / :818-820: each deprecated side getter reads
     *  its own slot out of the CURRENT map. */
    public Insets getSystemGestureInsets()          { return getInsets(mTypeInsetsMap, SYSTEM_GESTURES); }
    public Insets getMandatorySystemGestureInsets() { return getInsets(mTypeInsetsMap, MANDATORY_SYSTEM_GESTURES); }
    public Insets getTappableElementInsets()        { return getInsets(mTypeInsetsMap, TAPPABLE_ELEMENT); }

    /** Per-type accessor (AOSP :335-338). The compat layer (androidx
     *  WindowInsetsCompat$Impl30) calls this for each of its compat-side type
     *  bits via the platform Type's int values. */
    public Insets getInsets(int typeMask) { return getInsets(mTypeInsetsMap, typeMask); }

    /** AOSP :353-359. The IME's height depends on the focused view's EditorInfo
     *  and the IME's own UI state, so there IS no maximum and AOSP throws
     *  rather than inventing one. Note the guard is a BIT TEST here, while
     *  Builder.setInsetsIgnoringVisibility uses exact equality (:1180-1182);
     *  the asymmetry is deliberate on AOSP's part and is mirrored, not
     *  "cleaned up" -- unifying the two rules makes one of the two calls throw
     *  where no reference device does. */
    public Insets getInsetsIgnoringVisibility(int typeMask) {
        if ((typeMask & IME) != 0) {
            throw new IllegalArgumentException("Unable to query the maximum insets for IME");
        }
        return getInsets(mTypeMaxInsetsMap, typeMask);
    }

    /** AOSP :369-379: an AND over every bit in the mask, so the empty mask is
     *  vacuously true. Reading it off "are the insets non-zero" is wrong in
     *  both directions: a hidden bar still reports its height through the max
     *  map, and a zero-height visible bar exists. */
    public boolean isVisible(int typeMask) {
        for (int i = FIRST; i <= LAST; i = i << 1) {
            if ((typeMask & i) == 0) continue;
            if (!mTypeVisibilityMap[indexOf(i)]) return false;
        }
        return true;
    }

    /** AOSP :519-523: all three parts consumed. ViewGroup.dispatchApplyWindowInsets
     *  stops cascading to children once an upstream listener returns one. */
    public boolean isConsumed() {
        return mSystemWindowInsetsConsumed && mStableInsetsConsumed && mDisplayCutoutConsumed;
    }

    /** AOSP :461-465. */
    public boolean hasSystemWindowInsets() {
        return !getSystemWindowInsets().equals(Insets.NONE);
    }

    /** AOSP :467-471: ANY inset in EITHER map, or a cutout. */
    public boolean hasInsets() {
        return !getInsets(mTypeInsetsMap, ALL).equals(Insets.NONE)
                || !getInsets(mTypeMaxInsetsMap, ALL).equals(Insets.NONE)
                || mDisplayCutout != null;
    }

    /** AOSP :543-550: both maps go, the VISIBILITY map is carried across
     *  untouched, and the cutout keeps whatever state it had. Preserving
     *  visibility is load-bearing: an app that consumes the insets and then
     *  asks isVisible(statusBars()) must still be told the bar is on screen. */
    public WindowInsets consumeSystemWindowInsets() {
        return new WindowInsets(null, null, mTypeVisibilityMap, mIsRound,
                mAlwaysConsumeSystemBars, displayCutoutCopyConstructorArgument(this),
                mCompatInsetsTypes, mCompatIgnoreVisibility);
    }

    /** AOSP :831-833 is literally `return this` on R and later: the stable
     *  insets can no longer be consumed independently. The IDENTITY is the
     *  specification, not an implementation detail, so callers comparing the
     *  result with == keep working. */
    public WindowInsets consumeStableInsets() { return this; }

    /** AOSP :502-510: a copy with the cutout slot dropped. The call must EXIST
     *  even where it is a near-no-op: androidx.core.view.WindowInsetsCompat's
     *  clinit looks it up reflectively on API 28+ and fails class-init with
     *  NoSuchMethodError if absent, which cascades to every downstream user
     *  (Material's BottomNavigationView listener that converts to/from compat). */
    public WindowInsets consumeDisplayCutout() {
        return new WindowInsets(mSystemWindowInsetsConsumed ? null : mTypeInsetsMap,
                mStableInsetsConsumed ? null : mTypeMaxInsetsMap,
                mTypeVisibilityMap, mIsRound, mAlwaysConsumeSystemBars,
                null /* displayCutout */, mCompatInsetsTypes, mCompatIgnoreVisibility);
    }

    /** AOSP :579-591. Already-consumed insets are returned UNCHANGED (the Q+
     *  behaviour); before Q the new values were applied on top of a consumed
     *  bag, which produced the invalid "consumed but non-zero" state. */
    public WindowInsets replaceSystemWindowInsets(int left, int top, int right, int bottom) {
        if (mSystemWindowInsetsConsumed) return this;
        return new Builder(this).setSystemWindowInsets(Insets.of(left, top, right, bottom)).build();
    }
    public WindowInsets replaceSystemWindowInsets(Rect systemWindowInsets) {
        if (systemWindowInsets == null) return this;
        return replaceSystemWindowInsets(systemWindowInsets.left, systemWindowInsets.top,
                systemWindowInsets.right, systemWindowInsets.bottom);
    }

    /** AOSP WindowInsets.isRound (API 20): true only on a circular (watch)
     *  display. Mac.apk targets rectangular windows, so nothing in this tree
     *  ever sets it, but the flag is carried so a copy round-trips. */
    public boolean isRound() { return mIsRound; }

    /** @hide on AOSP (:838-840); read by the multi-window inset path. */
    public boolean shouldAlwaysConsumeSystemBars() { return mAlwaysConsumeSystemBars; }

    // ---- inset() ------------------------------------------------------------

    /** AOSP :884-887 (@hide). */
    public WindowInsets inset(Rect r) {
        return r == null ? inset((Insets) null) : inset(r.left, r.top, r.right, r.bottom);
    }

    /** AOSP :896-900: `Objects.requireNonNull(insets)` first, so a null
     *  argument is an NPE rather than a silent no-op. */
    public WindowInsets inset(Insets insets) {
        Objects.requireNonNull(insets);
        return inset(insets.left, insets.top, insets.right, insets.bottom);
    }

    /** AOSP :920-947. Each of the four amounts must be non-negative
     *  (Preconditions.checkArgumentNonnegative -> IllegalArgumentException);
     *  insetting is otherwise a per-slot reduction floored at zero. */
    public WindowInsets inset(int left, int top, int right, int bottom) {
        checkArgumentNonnegative(left);
        checkArgumentNonnegative(top);
        checkArgumentNonnegative(right);
        checkArgumentNonnegative(bottom);
        return new WindowInsets(
                mSystemWindowInsetsConsumed
                        ? null : insetInsets(mTypeInsetsMap, left, top, right, bottom),
                mStableInsetsConsumed
                        ? null : insetInsets(mTypeMaxInsetsMap, left, top, right, bottom),
                mTypeVisibilityMap, mIsRound, mAlwaysConsumeSystemBars,
                mDisplayCutoutConsumed ? null : (mDisplayCutout == null ? NO_CUTOUT : mDisplayCutout),
                mCompatInsetsTypes, mCompatIgnoreVisibility);
    }

    private static void checkArgumentNonnegative(int value) {
        if (value < 0) throw new IllegalArgumentException("Expected argument to be non-negative.");
    }

    private static Insets[] insetInsets(Insets[] typeInsetsMap, int l, int t, int r, int b) {
        boolean cloned = false;
        for (int i = 0; i < SIZE; i++) {
            Insets insets = typeInsetsMap[i];
            if (insets == null) continue;
            Insets insetInsets = insetInsets(insets, l, t, r, b);
            if (insetInsets != insets) {
                if (!cloned) { typeInsetsMap = typeInsetsMap.clone(); cloned = true; }
                typeInsetsMap[i] = insetInsets;
            }
        }
        return typeInsetsMap;
    }

    /** AOSP :987-996 -- INCLUDING the early-return quirk on :995. The test
     *  compares the NEW components against the INSET AMOUNTS rather than
     *  against the originals, so insetting (2,4,6,8) by (1,2,3,4) computes
     *  new=(1,2,3,4), matches, and hands back the ORIGINAL (2,4,6,8)
     *  untouched. We reimplement observable behaviour, not our idea of
     *  correctness: an app that has been tuned against a real device is tuned
     *  against this. Do not "fix" it. */
    static Insets insetInsets(Insets insets, int left, int top, int right, int bottom) {
        int newLeft   = Math.max(0, insets.left   - left);
        int newTop    = Math.max(0, insets.top    - top);
        int newRight  = Math.max(0, insets.right  - right);
        int newBottom = Math.max(0, insets.bottom - bottom);
        if (newLeft == left && newTop == top && newRight == right && newBottom == bottom) {
            return insets;
        }
        return Insets.of(newLeft, newTop, newRight, newBottom);
    }

    /** @hide on AOSP (:1002-1004). */
    boolean isSystemWindowInsetsConsumed() { return mSystemWindowInsetsConsumed; }

    // ---- equality (AOSP :949-971) ------------------------------------------
    // WindowInsets became value-equal in P, and androidx caches dispatched
    // insets keyed on equality: without these two, every dispatch looks like a
    // change and listeners re-run for nothing.

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WindowInsets)) return false;
        WindowInsets that = (WindowInsets) o;
        return mIsRound == that.mIsRound
                && mAlwaysConsumeSystemBars == that.mAlwaysConsumeSystemBars
                && mSystemWindowInsetsConsumed == that.mSystemWindowInsetsConsumed
                && mStableInsetsConsumed == that.mStableInsetsConsumed
                && mDisplayCutoutConsumed == that.mDisplayCutoutConsumed
                && Arrays.equals(mTypeInsetsMap, that.mTypeInsetsMap)
                && Arrays.equals(mTypeMaxInsetsMap, that.mTypeMaxInsetsMap)
                && Arrays.equals(mTypeVisibilityMap, that.mTypeVisibilityMap)
                && Objects.equals(mDisplayCutout, that.mDisplayCutout);
    }

    @Override public int hashCode() {
        return Objects.hash(Arrays.hashCode(mTypeInsetsMap), Arrays.hashCode(mTypeMaxInsetsMap),
                Arrays.hashCode(mTypeVisibilityMap), mIsRound, mDisplayCutout,
                mAlwaysConsumeSystemBars, mSystemWindowInsetsConsumed, mStableInsetsConsumed,
                mDisplayCutoutConsumed);
    }

    @Override public String toString() {
        StringBuilder result = new StringBuilder("WindowInsets{");
        for (int i = 0; i < SIZE; i++) {
            Insets insets = mTypeInsetsMap[i];
            Insets maxInsets = mTypeMaxInsetsMap[i];
            boolean visible = mTypeVisibilityMap[i];
            if (!Insets.NONE.equals(insets) || !Insets.NONE.equals(maxInsets) || visible) {
                result.append(' ').append(1 << i).append('=').append(insets)
                      .append(" max=").append(maxInsets).append(" vis=").append(visible);
            }
        }
        if (mDisplayCutout != null) result.append(" cutout=").append(mDisplayCutout);
        if (mIsRound) result.append(" round");
        if (isConsumed()) result.append(" CONSUMED");
        return result.append('}').toString();
    }

    /** AOSP-shape Builder (:1006-1279). All setters return the builder for
     *  chaining; ViewCompat's API-29+ BuilderImpl forwards to these one-for-one.
     *  A fresh Builder starts with BOTH maps flagged consumed, so
     *  `new Builder().build().isConsumed()` is true. */
    public static final class Builder {
        private final Insets[] mTypeInsetsMap;
        private final Insets[] mTypeMaxInsetsMap;
        private final boolean[] mTypeVisibilityMap;
        private boolean mSystemInsetsConsumed = true;
        private boolean mStableInsetsConsumed = true;
        private DisplayCutout mDisplayCutout;
        private boolean mIsRound;
        private boolean mAlwaysConsumeSystemBars;
        // API 34: the display shape carried by these insets. Mac.apk windows are
        // rectangular so the shape is informational; kept so a chain that sets
        // it round-trips.
        private DisplayShape mDisplayShape = DisplayShape.NONE;

        public Builder() {
            mTypeInsetsMap = new Insets[SIZE];
            mTypeMaxInsetsMap = new Insets[SIZE];
            mTypeVisibilityMap = new boolean[SIZE];
        }

        public Builder(WindowInsets insets) {
            if (insets == null) {
                mTypeInsetsMap = new Insets[SIZE];
                mTypeMaxInsetsMap = new Insets[SIZE];
                mTypeVisibilityMap = new boolean[SIZE];
                return;
            }
            mTypeInsetsMap = insets.mTypeInsetsMap.clone();
            mTypeMaxInsetsMap = insets.mTypeMaxInsetsMap.clone();
            mTypeVisibilityMap = insets.mTypeVisibilityMap.clone();
            mSystemInsetsConsumed = insets.mSystemWindowInsetsConsumed;
            mStableInsetsConsumed = insets.mStableInsetsConsumed;
            mDisplayCutout = displayCutoutCopyConstructorArgument(insets);
            mIsRound = insets.mIsRound;
            mAlwaysConsumeSystemBars = insets.mAlwaysConsumeSystemBars;
        }

        /** AOSP :1064-1070: routes through assignCompatInsets, so the Rect is
         *  SPLIT across statusBars (top) and navigationBars (sides + bottom). */
        public Builder setSystemWindowInsets(Insets systemWindowInsets) {
            Objects.requireNonNull(systemWindowInsets);
            assignCompatInsets(mTypeInsetsMap, systemWindowInsets.toRect());
            mSystemInsetsConsumed = false;
            return this;
        }

        /** AOSP :1215-1222: the same split, applied to the MAX map. */
        /** AOSP WindowInsets.Builder.setFrame(int, int) (API 36): the window
         *  frame size these insets describe, read back by WindowInsets.getFrame(). */
        public Builder setFrame(int width, int height) {
            mFrameWidth = width;
            mFrameHeight = height;
            return this;
        }
        private int mFrameWidth;
        private int mFrameHeight;

        public Builder setStableInsets(Insets stableInsets) {
            Objects.requireNonNull(stableInsets);
            assignCompatInsets(mTypeMaxInsetsMap, stableInsets.toRect());
            mStableInsetsConsumed = false;
            return this;
        }

        public Builder setSystemGestureInsets(Insets insets) {
            WindowInsets.setInsets(mTypeInsetsMap, SYSTEM_GESTURES, insets);
            return this;
        }
        public Builder setMandatorySystemGestureInsets(Insets insets) {
            WindowInsets.setInsets(mTypeInsetsMap, MANDATORY_SYSTEM_GESTURES, insets);
            return this;
        }
        public Builder setTappableElementInsets(Insets insets) {
            WindowInsets.setInsets(mTypeInsetsMap, TAPPABLE_ELEMENT, insets);
            return this;
        }

        /** AOSP :1147-1153: per-type setter over the CURRENT map. Deliberately
         *  does not touch the visibility map. */
        public Builder setInsets(int typeMask, Insets insets) {
            Objects.requireNonNull(insets);
            WindowInsets.setInsets(mTypeInsetsMap, typeMask, insets);
            mSystemInsetsConsumed = false;
            return this;
        }

        /** AOSP :1178-1187. The IME guard here is EXACT EQUALITY
         *  (`typeMask == IME`), unlike getInsetsIgnoringVisibility's bit test:
         *  ime() alone throws, and ime()|statusBars() does NOT. Mirrored as-is;
         *  a host that unifies the two rules throws where no reference device
         *  does. */
        public Builder setInsetsIgnoringVisibility(int typeMask, Insets insets) {
            if (typeMask == IME) {
                throw new IllegalArgumentException("Maximum inset not available for IME");
            }
            Objects.requireNonNull(insets);
            WindowInsets.setInsets(mTypeMaxInsetsMap, typeMask, insets);
            mStableInsetsConsumed = false;
            return this;
        }

        /** AOSP :1197-1206. */
        public Builder setVisible(int typeMask, boolean visible) {
            for (int i = FIRST; i <= LAST; i = i << 1) {
                if ((typeMask & i) == 0) continue;
                mTypeVisibilityMap[indexOf(i)] = visible;
            }
            return this;
        }

        /** AOSP :1231-1246: a null cutout becomes NO_CUTOUT (present but
         *  empty), which is NOT the same as "consumed"; a non-empty cutout also
         *  writes its safe insets into the DISPLAY_CUTOUT slot of both maps. */
        public Builder setDisplayCutout(DisplayCutout displayCutout) {
            mDisplayCutout = displayCutout != null ? displayCutout : NO_CUTOUT;
            if (!isEmptyCutout(mDisplayCutout)) {
                final Insets safeInsets = Insets.of(mDisplayCutout.getSafeInsetLeft(),
                        mDisplayCutout.getSafeInsetTop(), mDisplayCutout.getSafeInsetRight(),
                        mDisplayCutout.getSafeInsetBottom());
                final int index = indexOf(DISPLAY_CUTOUT);
                mTypeInsetsMap[index] = safeInsets;
                mTypeMaxInsetsMap[index] = safeInsets;
                mTypeVisibilityMap[index] = true;
            }
            return this;
        }

        /** @hide on AOSP (:1249-1259). */
        public Builder setRound(boolean round) { mIsRound = round; return this; }
        public Builder setAlwaysConsumeSystemBars(boolean alwaysConsumeSystemBars) {
            mAlwaysConsumeSystemBars = alwaysConsumeSystemBars; return this;
        }

        public Builder setDisplayShape(DisplayShape displayShape) {
            mDisplayShape = displayShape; return this;
        }
        // API 31/35 setters Mac.apk has no geometry for. Kept as accepted
        // no-ops so an androidx builder chain resolves and completes.
        public Builder setBoundingRects(int typeMask, java.util.List<Rect> rects) { return this; }
        public Builder setBoundingRectsIgnoringVisibility(int typeMask, java.util.List<Rect> rects) { return this; }
        public Builder setRoundedCorner(int position, RoundedCorner roundedCorner) { return this; }
        public Builder setPrivacyIndicatorBounds(Rect bounds) { return this; }

        /** AOSP :1266-1278: a map that nobody wrote is passed as null, i.e.
         *  consumed. compatInsetsTypes is always systemBars() and
         *  compatIgnoreVisibility always false for a Builder-built bag. */
        public WindowInsets build() {
            // The visibility map is handed over UN-cloned, matching AOSP: a
            // Builder reused after build() really does mutate the instance it
            // already produced. Observable behaviour, mirrored rather than
            // tidied.
            WindowInsets w = new WindowInsets(mSystemInsetsConsumed ? null : mTypeInsetsMap,
                    mStableInsetsConsumed ? null : mTypeMaxInsetsMap, mTypeVisibilityMap,
                    mIsRound, mAlwaysConsumeSystemBars, mDisplayCutout,
                    SYSTEM_BARS, false /* compatIgnoreVisibility */);
            w.mBuiltDisplayShape = mDisplayShape;
            w.macapkSetFrame(mFrameWidth, mFrameHeight);
            return w;
        }
    }

    /** Not AOSP state; carries the API-34 DisplayShape a Builder chain set so
     *  getDisplayShape() round-trips it. Deliberately outside equals/hashCode,
     *  which mirror AOSP's field set exactly. */
    private DisplayShape mBuiltDisplayShape = DisplayShape.NONE;

    /** AOSP-shape Type (:1281-1478). Returns the bit values that ViewCompat's
     *  compat-side Type maps onto, so `WindowInsets.Type.systemBars()` resolves
     *  to 7 (statusBars|navigationBars|captionBar) identically on real Android
     *  and here. */
    public static final class Type {
        private Type() {}
        public static int statusBars()                { return STATUS_BARS; }
        public static int navigationBars()            { return NAVIGATION_BARS; }
        public static int captionBar()                { return CAPTION_BAR; }
        public static int ime()                       { return IME; }
        public static int systemGestures()            { return SYSTEM_GESTURES; }
        public static int mandatorySystemGestures()   { return MANDATORY_SYSTEM_GESTURES; }
        public static int tappableElement()           { return TAPPABLE_ELEMENT; }
        public static int displayCutout()             { return DISPLAY_CUTOUT; }
        public static int systemBars()                { return SYSTEM_BARS; }
        /** API 35. Not part of systemBars() and outside the API-30 index range,
         *  so getInsets never iterates it; present for link compatibility. */
        public static int systemOverlays()            { return 1 << 12; }
    }

    /** AOSP Side (:1481-1508). WindowManager.LayoutParams' fit-insets defaults
     *  are expressed against it, and until now it was a bare literal there. */
    public static final class Side {
        public static final int LEFT   = 1 << 0;
        public static final int TOP    = 1 << 1;
        public static final int RIGHT  = 1 << 2;
        public static final int BOTTOM = 1 << 3;
        private Side() {}
        public static int all() { return LEFT | TOP | RIGHT | BOTTOM; }
    }

    /** AOSP WindowInsets.getDisplayShape (API 34). Mac.apk windows are
     *  rectangular, so this is the rectangular (empty path) DisplayShape unless
     *  a Builder chain supplied one. */
    public DisplayShape getDisplayShape() { return mBuiltDisplayShape; }

    /** AOSP WindowInsets.getRoundedCorner (API 31): none on a rectangular window. */
    public RoundedCorner getRoundedCorner(int position) { return null; }
    public Rect getPrivacyIndicatorBounds() { return null; }

    /** AOSP WindowInsets.getBoundingRects (API 35): the rects a type occupies.
     *  Mac.apk system bars are simple full-width strips with no sub-rects. */
    public java.util.List<Rect> getBoundingRects(int typeMask) { return java.util.Collections.emptyList(); }
    public java.util.List<Rect> getBoundingRectsIgnoringVisibility(int typeMask) { return java.util.Collections.emptyList(); }

    /** AOSP WindowInsets.getFrame() (API 36): the size of the window frame these
     *  insets were computed against. AOSP builds it from the frame the window
     *  manager supplied; Builder.setFrame(int, int) is how a caller supplies its
     *  own. A WindowInsets built without one reports 0x0, which is AOSP's own
     *  default for a Builder that never called setFrame. */
    public android.util.Size getFrame() {
        return new android.util.Size(mFrameWidth, mFrameHeight);
    }
    private int mFrameWidth;
    private int mFrameHeight;
    /** @hide -- set by Builder.setFrame(int, int). */
    final void macapkSetFrame(int width, int height) {
        mFrameWidth = width;
        mFrameHeight = height;
    }
}
