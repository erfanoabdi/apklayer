// android.widget.CompoundButton -- base class for two-state widgets
// (CheckBox, Switch, RadioButton, ToggleButton). Provides
// setOnCheckedChangeListener + the nested listener interface, plus
// (this pass) the button-drawable paint pipeline AOSP defines:
//   frameworks/base/core/java/android/widget/CompoundButton.java
//
// Was previously a total no-op: setButtonDrawable stored nothing and
// there was no onDraw, so every CheckBox/RadioButton/Switch/ToggleButton
// rendered text-only no matter what drawable/tint the app configured.
// Mirrors AOSP's onDraw/drawableStateChanged/verifyDrawable/tint
// accessors (fetched verbatim from the URL above); the BlendMode-based
// internal tint storage AOSP uses is collapsed to this tree's existing
// PorterDuff.Mode-based Drawable.setTintList/setTintMode (see
// android.graphics.drawable.Drawable), which is the same observable
// public API surface apps actually call.
//
// androidx.preference.SwitchPreferenceCompat / TwoStatePreference
// reference this type when their layout XML contains a Switch
// widget. Even though our setContentView doesn't actually inflate
// the layout into a live View tree, the Preference subclass's
// constructor / static initializer references the type, and class-
// load needs the symbol present.
package android.widget;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;

// AOSP (android11-release CompoundButton.java:61):
//     public abstract class CompoundButton extends Button implements Checkable
// The interface was missing here even though all three of its methods were
// implemented, so `view instanceof Checkable` -- the exact test ListView
// choice modes, RadioGroup, and the fwprobe DRAWSTATE battery perform --
// answered false for every CheckBox/RadioButton/Switch/ToggleButton.
public abstract class CompoundButton extends Button implements Checkable {

    /** AOSP-public listener interface for checked-state changes. */
    public interface OnCheckedChangeListener {
        void onCheckedChanged(CompoundButton buttonView, boolean isChecked);
    }

    private boolean checked;
    private boolean broadcasting;
    private OnCheckedChangeListener listener;
    /** AOSP's mOnCheckedChangeWidgetListener -- the containing widget's slot
     *  (RadioGroup), kept distinct from the app's. */
    private OnCheckedChangeListener widgetListener;

    private Drawable mButtonDrawable;
    private android.content.res.ColorStateList mButtonTintList;
    private android.graphics.PorterDuff.Mode mButtonTintMode;
    private boolean mHasButtonTint;
    private boolean mHasButtonTintMode;

    // ⚠ v1.0.1036+ FIX. All three of these used to be `super(context)`, and Java
    // resolves that to Button(Context) -- the ONE Button constructor that calls
    // macapkApplyDefaultButtonStyle and installs the theme's `buttonStyle`
    // background, textColor, minHeight and minWidth (Button.java:8, and see the
    // invariant asserted at Button.java:37-38: "Only the 1-arg constructor takes
    // this: the inflater always finds (Context, AttributeSet) first, so no
    // inflated view can double-apply." CompoundButton falsified that sentence for
    // CheckBox, RadioButton, Switch and ToggleButton, all four of which forward
    // their AttributeSet correctly and had it dropped one level up.)
    //
    // Consequence being fixed: every inflated checkbox/radio/switch/toggle was
    // painted with a filled Material BUTTON face and given the button family's
    // 176x96px minimum, and its AttributeSet never reached TextView's
    // constructor-time attr parser at all.
    //
    // AOSP CompoundButton (android16-release core/java/android/widget/
    // CompoundButton.java:93-107) funnels every constructor DOWNWARD:
    //     public CompoundButton(Context context) { this(context, null); }
    //     public CompoundButton(Context context, AttributeSet attrs) {
    //         this(context, attrs, 0); }
    //     public CompoundButton(Context context, AttributeSet attrs, int defStyleAttr) {
    //         this(context, attrs, defStyleAttr, 0); }
    //     public CompoundButton(Context context, AttributeSet attrs, int defStyleAttr,
    //             int defStyleRes) { super(context, attrs, defStyleAttr, defStyleRes); }
    // -- note the 1-arg form chains to this(context, null) rather than
    // super(context), so AOSP never routes ANY CompoundButton through
    // Button(Context) either. defStyleAttr is 0; each concrete subclass is what
    // names checkboxStyle / radioButtonStyle / switchStyle / buttonStyleToggle.
    //
    // Where the compound family gets its chrome in this tree instead:
    // LayoutInflater's tier 3 (LayoutInflater.java:2368-2377) maps CheckBox ->
    // checkboxStyle and RadioButton -> radioButtonStyle, and for that family
    // deliberately serves ONLY the `button` indicator drawable, because AOSP's
    // Widget.CompoundButton.* set no minHeight/minWidth/padding at all
    // (LayoutInflater.java:2303-2308, the v1.0.863 refutation). So the chrome this
    // change REMOVES is chrome AOSP does not have. Switch and ToggleButton have no
    // tier-3 row and therefore get no indicator either way -- that gap is tracked
    // separately (worklist V18) and is not made worse here.
    public CompoundButton(Context context) { this(context, null); }
    public CompoundButton(Context context, AttributeSet attrs) { this(context, attrs, 0); }
    /** AOSP API 21 four-argument constructor. AOSP's three-argument form is
     *  exactly this(context, attrs, defStyleAttr, 0), so delegating the other
     *  way is identical for defStyleRes == 0; a non-zero defStyleRes is
     *  DROPPED here, as in the existing FrameLayout/Button four-argument forms
     *  (TextView, ProgressBar and their subclasses honour it). Without it an
     *  app subclass calling the four-argument
     *  super() dies with NoSuchMethodError; see debug/ctorsdk_gate.sh. */
    public CompoundButton(Context context, android.util.AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        this(context, attrs, defStyleAttr);
    }
    public CompoundButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        macapkApplyCompoundButtonStyleDefaults();
        macapkApplyCheckedAttr(attrs);
        macapkApplyDefStyleIndicator(context, attrs, defStyleAttr);
    }

    /** android.R.attr.checkboxStyle / radioButtonStyle, frozen in
     *  core/res/res/values/public-final.xml (CheckBox.java / RadioButton.java
     *  pass exactly these). */
    private static final int MACAPK_CHECKBOX_STYLE    = 0x0101006c;
    private static final int MACAPK_RADIOBUTTON_STYLE = 0x0101007e;

    /** AOSP's CompoundButton constructor (android16-release
     *  CompoundButton.java:106-114):
     *
     *      final TypedArray a = context.obtainStyledAttributes(
     *              attrs, R.styleable.CompoundButton, defStyleAttr, defStyleRes);
     *      final Drawable d = a.getDrawable(R.styleable.CompoundButton_button);
     *      if (d != null) setButtonDrawable(d);
     *
     *  i.e. the indicator comes from the defStyleAttr's style
     *  (Widget.Material.CompoundButton.CheckBox: button =
     *  ?attr/listChoiceIndicatorMultiple) for EVERY construction path. Ours only
     *  ever installed it from LayoutInflater's tier 3 (applyWidgetDefaultStyle ->
     *  macapkInstallButtonDrawable), which runs for inflated views -- so a
     *  checkbox built in CODE (`new CheckBox(ctx)`) drew its label and no box.
     *  MEASURED on Among Us (2026-09-22): Unity's native warning dialog (libunity
     *  0x6fbcf0) builds exactly that and hands it to AlertDialog.Builder.setView;
     *  the capture shows "Disable this warning" with no checkbox at all.
     *
     *  Gated to the programmatic path (attrs == null) and to the two FRAMEWORK
     *  defStyleAttrs: an inflated view still gets its indicator from tier 3
     *  (which prefers an app's bundled AppCompat art), and an AppCompatCheckBox
     *  passes its own app-namespace attr and keeps its own helper. The drawable
     *  is the same synthesized framework indicator tier 3 falls back to
     *  (Resources.MacapkCompoundButtonDrawable). */
    private void macapkApplyDefStyleIndicator(Context context, AttributeSet attrs,
                                              int defStyleAttr) {
        if (attrs != null || mButtonDrawable != null) return;
        final String name = (defStyleAttr == MACAPK_CHECKBOX_STYLE) ? "btn_check_material"
                          : (defStyleAttr == MACAPK_RADIOBUTTON_STYLE) ? "btn_radio_material"
                          : null;
        if (name == null) return;
        try {
            final android.content.res.Resources r =
                    context == null ? null : context.getResources();
            final Drawable d = r == null ? null : r.macapkFrameworkDrawableByName(name);
            if (d != null) setButtonDrawable(d);
        } catch (Throwable ignored) {
            // A missing indicator must never break construction.
        }
    }

    /** AOSP serves these three through `Widget.CompoundButton`, the style every
     *  checkbox / radio / switch inherits (android16-release core/res/res/values/
     *  styles.xml:382-388, quoted verbatim):
     *
     *      <style name="Widget.CompoundButton">
     *          <item name="focusable">true</item>
     *          <item name="clickable">true</item>
     *          <item name="textAppearance">?attr/textAppearance</item>
     *          <item name="textColorPrimaryDisableOnly">...</item>
     *          <item name="gravity">center_vertical|start</item>
     *      </style>
     *
     *  This tree never delivers them. LayoutInflater's tier 3 maps CheckBox ->
     *  checkboxStyle and RadioButton -> radioButtonStyle but deliberately serves
     *  ONLY the `button` indicator drawable out of that style (LayoutInflater
     *  .java:2303-2308, the v1.0.863 refutation), and Switch / ToggleButton have
     *  no tier-3 row at all. So:
     *
     *   * `clickable` was FALSE on every compound button in every app. View
     *     .onTouchEvent gates its whole press/click arm on `if (clickable)`
     *     (View.java:6250), so a tap on a CheckBox or RadioButton was dropped
     *     before ACTION_DOWN was even recorded -- the control could never change
     *     state by touch. MEASURED on Aegis 3.4.2's second onboarding slide
     *     (`fragment_security_picker_slide.xml`, a RadioGroup of three
     *     AppCompatRadioButtons): the ViewCanvasDriver tree dump prints
     *     ` clk=/oc=` only for a view that is clickable or has a listener, and
     *     all three radios printed NEITHER while the sibling AppCompatButton and
     *     both MaterialButtons on the same screen printed `clk=true oc=true`.
     *   * `gravity` was `Gravity.CENTER`, inherited from Button.init(), which is
     *     `Widget.Button`'s gravity (styles.xml:365) and not this family's. On
     *     the same Aegis screen the two `layout_width="match_parent"` radios drew
     *     their labels CENTRED in a 680px row -- "Password" and "Biometrics"
     *     floating in the middle of the screen with their bubble at the far left
     *     -- instead of sitting next to the indicator.
     *
     *  Set in the constructor rather than through a style so it holds for every
     *  ctor an app might use, exactly as ImageButton.initImageButtonDefaults()
     *  already does for `Widget.ImageButton`'s scaleType/focusable/clickable. An
     *  explicit android:gravity / android:clickable in XML still wins, because
     *  LayoutInflater.applyAttributes runs after construction.
     *
     *  ToggleButton is the one sibling that must NOT take the gravity: its
     *  default style is `Widget.Button.Toggle` (styles.xml:402), which descends
     *  from `Widget.Button`, not from `Widget.CompoundButton`. It restores
     *  Gravity.CENTER in its own constructors. */
    private void macapkApplyCompoundButtonStyleDefaults() {
        setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        setFocusable(true);
        setClickable(true);
    }

    /** AOSP reads `android:checked` in the CompoundButton CONSTRUCTOR
     *  (android16-release CompoundButton.java:128-131):
     *
     *      final boolean checked = a.getBoolean(
     *              com.android.internal.R.styleable.CompoundButton_checked, false);
     *      setChecked(checked);
     *
     *  Nothing in this tree did: LayoutInflater has no `checked` case (grep
     *  `"checked"` LayoutInflater.java -> only an unrelated comment), and the
     *  androidx AppCompat subclasses do not read it either -- they leave it to
     *  the framework class, which is here. So `android:checked="true"` was
     *  silently dropped fleet-wide and a pre-selected radio / checkbox rendered
     *  UNCHECKED. Measured on Aegis: `rb_password` carries
     *  android:checked="true" and the emulator shows a filled bubble on entry;
     *  we drew three empty rings.
     *
     *  Doing it here rather than in the inflater is also what makes
     *  RadioGroup.hookRadioChild work: the inflater constructs the child (this
     *  runs) and only then calls addView, so the group sees isChecked()==true
     *  and adopts it as checkedId, which is AOSP's ordering. */
    private void macapkApplyCheckedAttr(AttributeSet a) {
        if (a == null) return;
        try {
            if (a.getAttributeBooleanValue(MACAPK_ANDROID_NS, "checked", false)) {
                setChecked(true);
            }
        } catch (Throwable ignored) {
            // An attribute we cannot parse must never break inflation.
        }
    }
    private static final String MACAPK_ANDROID_NS =
        "http://schemas.android.com/apk/res/android";

    public void setChecked(boolean checked) {
        if (this.checked == checked) return;
        this.checked = checked;
        refreshDrawableState();
        if (broadcasting) return;
        broadcasting = true;
        if (listener != null) listener.onCheckedChanged(this, checked);
        // AOSP notifies the WIDGET listener too, right after the app's
        // (CompoundButton.java:227-232). See setOnCheckedChangeWidgetListener.
        if (widgetListener != null) widgetListener.onCheckedChanged(this, checked);
        broadcasting = false;
    }
    public boolean isChecked()                       { return checked; }
    public void    toggle()                          { setChecked(!checked); }
    public void setOnCheckedChangeListener(OnCheckedChangeListener l) { this.listener = l; }

    /** AOSP CompoundButton.setOnCheckedChangeWidgetListener (@hide, package-
     *  private): a SECOND listener slot reserved for the containing widget, so
     *  RadioGroup's mutual-exclusion hook cannot be clobbered by an app that
     *  calls setOnCheckedChangeListener on a radio inside the group. RadioGroup
     *  used the public slot here, which meant exactly that collision. */
    void setOnCheckedChangeWidgetListener(OnCheckedChangeListener l) { this.widgetListener = l; }

    /** AOSP CompoundButton.performClick (android16-release CompoundButton.java
     *  :144-156), quoted:
     *
     *      public boolean performClick() {
     *          toggle();
     *          final boolean handled = super.performClick();
     *          if (!handled) {
     *              // View only makes a sound effect if the onClickListener was
     *              // called, so we'll need to make one here instead.
     *              playSoundEffect(SoundEffectConstants.CLICK);
     *          }
     *          return handled;
     *      }
     *
     *  THIS OVERRIDE DID NOT EXIST. `toggle()` therefore had exactly two callers
     *  in the whole tree -- an app calling it directly, and RadioGroup.check() --
     *  so a TOUCH could never change a compound button's state even once the
     *  view was clickable: View.onTouchEvent's ACTION_UP arm calls
     *  performClick(), which resolved to View's, which only forwards to an
     *  OnClickListener and returns. A CheckBox with no listener was inert; one
     *  WITH a listener fired the callback while its own tick never appeared.
     *  The sound effect is dropped (we model no sound effects anywhere). */
    @Override
    public boolean performClick() {
        toggle();
        return super.performClick();
    }

    /** `android.R.attr.state_checked`, the id the app's compiled selectors
     *  reference. */
    private static final int[] CHECKED_STATE_SET = { 0x010100a0 };

    /** AOSP CompoundButton.onCreateDrawableState:
     *      final int[] drawableState = super.onCreateDrawableState(extraSpace + 1);
     *      if (isChecked()) mergeDrawableStates(drawableState, CHECKED_STATE_SET);
     *
     *  Without this, `state_checked` was NEVER in any state array anywhere in the
     *  tree: View.onCreateDrawableState emits focused / window-focused / enabled /
     *  selected / pressed / activated / hovered and nothing else. So a `<selector>`
     *  whose first entry is `android:state_checked="true"` could never match, and
     *  every compound button rendered its UNCHECKED art -- a radio with no dot, a
     *  checkbox with no tick -- however correct the rest of the pipeline was.
     *  MACAPK_REAL_LOOPER is on by default, so StateSet.stateSetMatches is the real
     *  matcher and really does reject it.
     *
     *  Flag-off safe: super returns int[0] ignoring extraSpace, and
     *  mergeDrawableStates then finds no free slot and returns the array
     *  unchanged. */
    @Override
    protected int[] onCreateDrawableState(int extraSpace) {
        final int[] drawableState = super.onCreateDrawableState(extraSpace + 1);
        if (isChecked()) {
            android.view.View.mergeDrawableStates(drawableState, CHECKED_STATE_SET);
        }
        return drawableState;
    }

    /** AOSP CompoundButton.setButtonDrawable(int): resolves the resource
     *  through the Context (Resources.getDrawable) then delegates. */
    public void setButtonDrawable(int resId) {
        setButtonDrawable(resId != 0 ? getContext().getDrawable(resId) : null);
    }

    /** AOSP CompoundButton.setButtonDrawable(Drawable): stores the drawable
     *  and hooks it into this View's Drawable.Callback / layout-direction /
     *  drawable-state machinery, and grows the view's min height to the
     *  drawable's intrinsic height so the glyph is never clipped. Was `{}`
     *  -- the drawable was silently dropped and getButtonDrawable() always
     *  returned null, so no checkbox/radio/switch/toggle glyph could ever
     *  paint regardless of what an app configured. */
    public void setButtonDrawable(Drawable drawable) {
        if (mButtonDrawable == drawable) return;
        if (mButtonDrawable != null) {
            mButtonDrawable.setCallback(null);
            unscheduleDrawable(mButtonDrawable);
        }
        mButtonDrawable = drawable;
        if (drawable != null) {
            drawable.setCallback(this);
            drawable.setLayoutDirection(getLayoutDirection());
            if (drawable.isStateful()) drawable.setState(getDrawableState());
            drawable.setVisible(getVisibility() == VISIBLE, false);
            setMinHeight(drawable.getIntrinsicHeight());
            applyButtonTint();
        }
        invalidate();
    }

    public Drawable getButtonDrawable() { return mButtonDrawable; }

    /** AOSP CompoundButton.onDraw: positions mButtonDrawable at the
     *  gravity-resolved vertical offset (RTL-aware horizontal side -- the
     *  glyph sits at the trailing edge in an RTL layout), draws the label
     *  via super.onDraw, then paints the button drawable on top (AOSP's
     *  exact order: label first, glyph over it). This is the missing
     *  rendering primitive; setButtonDrawable alone only stored state. */
    @Override
    protected void onDraw(android.graphics.Canvas canvas) {
        final Drawable buttonDrawable = mButtonDrawable;
        if (buttonDrawable != null) {
            final int verticalGravity = getGravity() & Gravity.VERTICAL_GRAVITY_MASK;
            final int drawableHeight = buttonDrawable.getIntrinsicHeight();
            final int drawableWidth = buttonDrawable.getIntrinsicWidth();
            final int top;
            switch (verticalGravity) {
                case Gravity.BOTTOM:          top = getHeight() - drawableHeight; break;
                case Gravity.CENTER_VERTICAL: top = (getHeight() - drawableHeight) / 2; break;
                default:                      top = 0;
            }
            final int bottom = top + drawableHeight;
            final int left  = isLayoutRtl() ? getWidth() - drawableWidth : 0;
            final int right = isLayoutRtl() ? getWidth() : drawableWidth;
            buttonDrawable.setBounds(left, top, right, bottom);
        }
        super.onDraw(canvas);
        if (buttonDrawable != null) {
            final int scrollX = getScrollX();
            final int scrollY = getScrollY();
            if (scrollX == 0 && scrollY == 0) {
                buttonDrawable.draw(canvas);
            } else {
                canvas.translate(scrollX, scrollY);
                buttonDrawable.draw(canvas);
                canvas.translate(-scrollX, -scrollY);
            }
        }
    }

    /** AOSP CompoundButton.drawableStateChanged: forwards the current
     *  drawable state (checked/pressed/enabled/...) to mButtonDrawable so a
     *  stateful button drawable (a <selector> keyed on state_checked)
     *  picks the right entry. */
    @Override
    public void drawableStateChanged() {
        super.drawableStateChanged();
        final Drawable buttonDrawable = mButtonDrawable;
        if (buttonDrawable != null && buttonDrawable.isStateful()
                && buttonDrawable.setState(getDrawableState())) {
            invalidateDrawable(buttonDrawable);
        }
        // The tint is state-dependent (?colorControlNormal vs
        // ?colorControlActivated), so it has to be recomputed whenever the state
        // moves, not only when it is first set.
        applyButtonTint();
    }

    /** AOSP CompoundButton.verifyDrawable: identifies mButtonDrawable to a
     *  Drawable.Callback dispatch. This tree's View.invalidateDrawable()
     *  invalidates unconditionally rather than gating on verifyDrawable
     *  (see View.java's own comment there), so there is no super
     *  implementation to chain into here (View declares no verifyDrawable
     *  of its own). This still needs to exist with the real Drawable
     *  parameter type: AndroidX CompoundButton subclasses commonly
     *  `@Override ... { return super.verifyDrawable(who) || who == X; }`,
     *  and that super-call resolves against THIS class's descriptor. */
    protected boolean verifyDrawable(Drawable who) {
        return who == mButtonDrawable;
    }

    /** AOSP CompoundButton.jumpDrawablesToCurrentState: skips mid-transition
     *  animation and jumps straight to the current state (called when a
     *  ListView row is recycled, so a stale press/ripple doesn't linger). */
    @Override
    public void jumpDrawablesToCurrentState() {
        super.jumpDrawablesToCurrentState();
        if (mButtonDrawable != null) mButtonDrawable.jumpToCurrentState();
    }

    /** AOSP CompoundButton.drawableHotspotChanged: forwards a touch-down
     *  hotspot (ripple origin) to mButtonDrawable. */
    @Override
    public void drawableHotspotChanged(float x, float y) {
        super.drawableHotspotChanged(x, y);
        if (mButtonDrawable != null) mButtonDrawable.setHotspot(x, y);
    }

    /** AOSP CompoundButton.setButtonTintList / getButtonTintList: the
     *  ColorStateList Material/AppCompat widgets apply over the button
     *  drawable (e.g. ?attr/colorControlActivated for a checked
     *  MaterialCheckBox). Previously entirely missing (NoSuchMethodError
     *  on the four highest-napps method gaps in this lane). */
    public void setButtonTintList(android.content.res.ColorStateList tint) {
        mButtonTintList = tint;
        mHasButtonTint = true;
        applyButtonTint();
    }
    public android.content.res.ColorStateList getButtonTintList() { return mButtonTintList; }

    /** AOSP CompoundButton.setButtonTintMode / getButtonTintMode. */
    public void setButtonTintMode(android.graphics.PorterDuff.Mode tintMode) {
        mButtonTintMode = tintMode;
        mHasButtonTintMode = true;
        applyButtonTint();
    }
    public android.graphics.PorterDuff.Mode getButtonTintMode() { return mButtonTintMode; }

    /** AOSP CompoundButton.applyButtonTint(): pushes the configured tint
     *  onto mButtonDrawable once both a drawable and a tint exist, mutating
     *  first so a shared/cached Drawable's tint doesn't bleed into other
     *  views using the same instance (AOSP does the same mutate()). */
    private void applyButtonTint() {
        if (mButtonDrawable == null || !(mHasButtonTint || mHasButtonTintMode)) return;
        mButtonDrawable = mButtonDrawable.mutate();
        if (mHasButtonTint) mButtonDrawable.setTintList(mButtonTintList);
        if (mHasButtonTintMode) mButtonDrawable.setTintMode(mButtonTintMode);
        if (mButtonDrawable.isStateful()) mButtonDrawable.setState(getDrawableState());
        // ⚠ setTintList ALONE PAINTS NOTHING IN THIS TREE. Drawable.setTintList
        // only stores the list and invalidates; BitmapDrawable consumes no tint at
        // all -- its draw() passes the Paint straight to Canvas.drawBitmap and the
        // only recolouring hook it has is setColorFilter. Canvas DOES honour a
        // PorterDuffColorFilter on a bitmap blit, which is also how AOSP
        // implements a tint internally (Drawable.updateTintFilter builds exactly
        // this filter).
        //
        // This matters because the assets that arrive here are MASKS: AppCompat's
        // abc_btn_radio_to_on_mtrl_*.png are pure black on transparent and are
        // meaningless until ?colorControlNormal / ?colorControlActivated is
        // applied. Resolving the list against the CURRENT state is what makes a
        // checked radio the accent colour and an unchecked one grey.
        if (mHasButtonTint && mButtonTintList != null) {
            int c = mButtonTintList.getColorForState(getDrawableState(),
                                                     mButtonTintList.getDefaultColor());
            android.graphics.PorterDuff.Mode mode =
                (mHasButtonTintMode && mButtonTintMode != null)
                    ? mButtonTintMode : android.graphics.PorterDuff.Mode.SRC_IN;
            mButtonDrawable.setColorFilter(
                new android.graphics.PorterDuffColorFilter(c, mode));
        }
    }

    /** AOSP CompoundButton.getCompoundPaddingLeft / Right: the button drawable
     *  occupies the leading edge, so its intrinsic width is added to the leading
     *  compound padding and the label starts after it.
     *
     *  Load-bearing here, not cosmetic: TextView.getCompoundPaddingLeft is real in
     *  this tree and feeds onMeasure, onDraw and the wrap width. Without the
     *  override the label lays out at x=0 and the indicator paints on top of it. */
    @Override
    public int getCompoundPaddingLeft() {
        int padding = super.getCompoundPaddingLeft();
        if (!isLayoutRtl() && mButtonDrawable != null) {
            padding += mButtonDrawable.getIntrinsicWidth();
        }
        return padding;
    }

    @Override
    public int getCompoundPaddingRight() {
        int padding = super.getCompoundPaddingRight();
        if (isLayoutRtl() && mButtonDrawable != null) {
            padding += mButtonDrawable.getIntrinsicWidth();
        }
        return padding;
    }

    // ---- API-36 surface completion ------------------------------------
    /** AOSP CompoundButton.setButtonIcon(Icon) (:360) -- verbatim. */
    public void setButtonIcon(android.graphics.drawable.Icon icon) {
        setButtonDrawable(icon == null ? null : icon.loadDrawable(getContext()));
    }

    /** AOSP CompoundButton.setButtonTintBlendMode / getButtonTintBlendMode.
     *  The PorterDuff.Mode field above stays the value applyButtonTint()
     *  pushes into the drawable; the BlendMode is the exact round-trip value.
     *  MacApkBlendModes is AOSP's own BlendMode.fromValue /
     *  blendModeToPorterDuffMode mapping. */
    private android.graphics.BlendMode mButtonBlendMode;
    public void setButtonTintBlendMode(android.graphics.BlendMode blendMode) {
        mButtonBlendMode = blendMode;
        setButtonTintMode(MacApkBlendModes.toPorterDuff(blendMode));
    }
    public android.graphics.BlendMode getButtonTintBlendMode() { return mButtonBlendMode; }
}
