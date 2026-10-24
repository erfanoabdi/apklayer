package android.widget;
import android.content.Context;
public class EditText extends TextView {
    public EditText(Context c) { super(c); macapkInitEditable(); }
    public EditText(Context c, android.util.AttributeSet a) { super(c, a); macapkInitEditable(); }
    /** AOSP API 21 four-argument constructor: chains to the parent's four-argument
     *  form so defStyleRes reaches TextView (AOSP does the same, e.g. EditText.java:100-101).
     *  Without it an app subclass calling the four-argument super() dies with
     *  NoSuchMethodError; see debug/ctorsdk_gate.sh. */
    public EditText(Context context, android.util.AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);   // AOSP EditText.java:100-101
        macapkInitEditable();
    }
    public EditText(Context c, android.util.AttributeSet a, int s) { super(c, a, s); macapkInitEditable(); }

    /** An EditText MUST be able to take focus, including in touch mode.
     *
     *  AOSP reaches this state by two routes, and an EditText ends up with both:
     *
     *    TextView.fixFocusableAndClickableSettings()  (TextView.java, android14)
     *        if (mMovement != null || (mEditor != null && mEditor.mKeyListener != null)) {
     *            setFocusable(FOCUSABLE);
     *            setClickable(true);
     *            setLongClickable(true);
     *        }
     *
     *  and the platform's editTextStyle, which carries
     *  android:focusableInTouchMode="true" -- the conjunct that matters here,
     *  because View.requestFocusNoSearch gates on
     *  `!isInTouchMode() || focusableInTouchMode` and our isInTouchMode() is
     *  hardcoded true (View.java:1996).
     *
     *  We had NEITHER: View's defaults are focusable=false,
     *  focusableInTouchMode=false, and nothing in TextView or EditText changed
     *  them. So requestFocus() on an EditText returned false, the ViewGroup
     *  mFocused chain was never built, and ViewGroup.dispatchKeyEvent had no
     *  focused child to walk to. Measured 2026-08-12 on F-Droid's search box:
     *  Activity.dispatchKeyEvent returned false for all six characters even
     *  after TextView learned to handle them -- the key never reached the
     *  field, so fixing TextView.onKeyDown alone changed nothing.
     *
     *  Set here rather than in TextView because the trigger AOSP uses is "has a
     *  KeyListener", and our listener chain is inert (MetaKeyKeyListener.onKeyDown
     *  returns false), so no TextView in this tree would satisfy it. An EditText
     *  is the editable case by definition. View parses neither attribute from
     *  the AttributeSet, so there is no XML value for this to clobber.
     *
     *  setFocusableInTouchMode(true) also sets focusable=true (View.java:3535),
     *  so it covers the setFocusable(FOCUSABLE) half. */
    private void macapkInitEditable() {
        setFocusableInTouchMode(true);
        setClickable(true);
        setLongClickable(true);
    }

    // AOSP EditText.getFreezesText() (core/java/android/widget/EditText.java)
    // hardcodes true, overriding View's own getFreezesText()/setFreezesText() flag
    // pair (View.java, FREEZES_TEXT_FLAG) that lets ANY view opt in via
    // android:freezesText -- an EditText is unconditionally a text-freezer
    // regardless of that XML attribute, since losing typed-but-unsaved input across
    // a config change/process restore would be a real user-visible data loss.
    // View does not yet declare the base flag pair in this tree (no consumer calls
    // getFreezesText() today), so this cannot be @Override; it still resolves
    // correctly for the common case (any EditText-typed call site), and starts
    // participating in real polymorphic dispatch the moment View grows the base.
    public boolean getFreezesText() { return true; }

    // AOSP TextView.getDefaultEditable() / EditText's override (core/java/android/
    // widget/EditText.java): a constructor-time hook AOSP's TextView constructor
    // consults to decide whether the initial text buffer defaults to Editable.
    // TextView's own init does not yet call this hook in our tree (its buffer is
    // always wrapped lazily on demand via getEditableText()), so this is presently
    // unreached, but it is EditText's real, AOSP-declared override point.
    protected boolean getDefaultEditable() { return true; }

    // AOSP TextView.getDefaultMovementMethod() / EditText's override: the initial
    // MovementMethod AOSP's TextView constructor installs absent an explicit
    // setMovementMethod() call. ArrowKeyMovementMethod (android.text.method,
    // already in this tree) is the real AOSP class for this -- arrow-key/DPAD caret
    // movement through editable text.
    protected android.text.method.MovementMethod getDefaultMovementMethod() {
        return android.text.method.ArrowKeyMovementMethod.getInstance();
    }

    // AOSP narrows TextView.getText():CharSequence to Editable on EditText.
    // Unity's soft-input bridge (com.unity3d.player.S) calls editText.getText()
    // expecting an Editable (then getText().length() / setSelection); without
    // this covariant override the invokevirtual resolves to the wrong
    // descriptor and throws.
    @Override
    public android.text.Editable getText() { return getEditableText(); }

    // AOSP EditText.setText(CharSequence, BufferType) (core/java/android/widget/
    // EditText.java): unlike every other TextView subclass, an EditText's buffer
    // type is NEVER caller-selectable -- it is always forced to EDITABLE, because
    // the whole point of the class is that its text stays an Editable the caret/IME
    // can mutate in place. TextView's own 2-arg setText (TextView.java) ignores the
    // requested type entirely in this tree (every BufferType behaves as NORMAL), so
    // this override changes nothing OBSERVABLE today, but it documents + enforces
    // the real AOSP contract at the class that actually owns it.
    @Override
    public void setText(CharSequence text, BufferType type) {
        super.setText(text, BufferType.EDITABLE);
    }

    // setTextCursorDrawable/getTextCursorDrawable moved to TextView (2026-07-30):
    // AOSP declares this API on TextView, not EditText (frameworks/base core/
    // java/android/widget/TextView.java, android15-release ~line 4097). EditText
    // inherits it; this class used to shadow it with a hardcoded `return null`
    // stub that had no setter and no backing field at all.

    // AOSP declares getAutofillType on View (returning AUTOFILL_TYPE_NONE) and
    // overrides it on TextView:
    //
    //   frameworks/base core/java/android/widget/TextView.java:11740-11743
    //     @Override public @AutofillType int getAutofillType() {
    //         return isTextEditable() ? AUTOFILL_TYPE_TEXT : AUTOFILL_TYPE_NONE;
    //     }
    //   frameworks/base core/java/android/widget/TextView.java:11056-11058
    //     boolean isTextEditable() {
    //         return mText instanceof Editable && onCheckIsTextEditor() && isEnabled();
    //     }
    //
    // TextView is the AOSP-faithful home and this override belongs there. It
    // lives on EditText because that is the class this lane owns; the override
    // stays correct if TextView later grows the base implementation, since the
    // value computed here is the one TextView's formula produces for an
    // EditText.
    //
    // Why the formula collapses to isEnabled() here: of isTextEditable's three
    // conjuncts, two are constant-true for an EditText in this tree. getText()
    // above always returns an Editable, and an EditText is by definition a text
    // editor (AOSP's TextView.onCheckIsTextEditor answers from mEditor's input
    // type, which the EditText style always sets). So only the enabled state
    // varies -- a disabled field is not autofillable, which is the distinction
    // the caller (Aegis, via an autofill-aware wrapper) is asking about.
    //
    // v1.0.940: the constants now live on View, their AOSP home
    // (View.java:1216-1259), so the private duplicates that stood in for them
    // are gone and there is one definition again. AOSP declares
    // getAutofillType on View returning AUTOFILL_TYPE_NONE and overrides it in
    // TextView; this override is the EditText end of that chain.
    @Override public int getAutofillType() {
        return isEnabled() ? android.view.View.AUTOFILL_TYPE_TEXT
                           : android.view.View.AUTOFILL_TYPE_NONE;
    }

    // AOSP View.onCheckIsTextEditor returns false and TextView answers from its
    // Editor's input type; for an EditText that answer is true, which is what
    // makes InputMethodManager offer it an InputConnection and -- the reason
    // this override exists -- what makes TextView.onKeyDown treat a keystroke
    // as an EDIT rather than dropping it (see TextView.macapkIsTextEditor).
    // Without it a focused EditText ignored the physical keyboard entirely,
    // which on macOS is the only keyboard there is.
    @Override public boolean onCheckIsTextEditor() { return true; }

    protected void onSelectionChanged(int selStart, int selEnd) { }

    public void setIsHandwritingDelegate(boolean isHandwritingDelegate) { }
    public boolean isHandwritingDelegate() { return false; }

    // (No onReceiveContent override: AOSP's EditText inherits TextView's, which
    // inserts pasted text. A `return payload` stub here shadowed it.)

    // AOSP EditText.extendSelection(int) (core/java/android/widget/EditText.java):
    // "Convenience for Selection#extendSelection". Selection.extendSelection already
    // has real geometry in this tree (android/text/Selection.java: re-anchors the
    // selection's moving end at the index while leaving getSelectionStart() as the
    // anchor) -- shift+arrow-key-style selection growth. Only the convenience
    // wrapper itself was missing.
    public void extendSelection(int index) {
        android.text.Selection.extendSelection(getText(), index);
    }

    // AOSP EditText.setEllipsize(TextUtils.TruncateAt) (core/java/android/widget/
    // EditText.java): an EditText specifically forbids MARQUEE (a scrolling-ellipsis
    // animation makes no sense on a field being actively caret-navigated/edited) and
    // throws IllegalArgumentException; every other mode delegates to TextView's real
    // setter (TextView.java, already stores mEllipsize).
    @Override
    public void setEllipsize(android.text.TextUtils.TruncateAt ellipsis) {
        if (ellipsis == android.text.TextUtils.TruncateAt.MARQUEE) {
            throw new IllegalArgumentException(
                "EditText cannot use the ellipsize mode TextUtils.TruncateAt.MARQUEE");
        }
        super.setEllipsize(ellipsis);
    }

    // AOSP EditText.getAccessibilityClassName() (core/java/android/widget/
    // EditText.java): hardcodes the EditText class name rather than inheriting
    // View's default getClass().getName() (View.java). Behaviorally identical for a
    // bare EditText instance, but NOT for a subclass (AppCompatEditText and
    // friends) -- AOSP wants every EditText-family view to announce itself to
    // accessibility services / autofill as plain "android.widget.EditText"
    // regardless of which concrete subclass renders it, so a screen reader treats
    // it as a standard text field rather than an unrecognized type.
    @Override
    public CharSequence getAccessibilityClassName() {
        return EditText.class.getName();
    }

    // AOSP EditText.supportsAutoSizeText() (core/java/android/widget/EditText.java,
    // @hide): an EditText opts OUT of TextView's auto-size-text feature -- a field
    // that shrinks its own text while the user is actively typing into it would be
    // disorienting. TextView has no auto-size-text machinery in this tree yet (no
    // setAutoSizeTextTypeWithDefaults, no base supportsAutoSizeText to override), so
    // this is presently unreached, but it is EditText's real, AOSP-declared
    // override point, ready for whenever TextView grows that feature.
    protected boolean supportsAutoSizeText() { return false; }

    /** AOSP EditText.java:306 / :314 (API 33) -- whether ctrl+B/I/U apply
     *  bold/italic/underline spans to the selection. AOSP stores the flag and
     *  its key handling consults it; this port routes no hardware modifier
     *  shortcuts into span editing, so the flag is state. The DEFAULT is
     *  false, which is AOSP's own. */
    private boolean mStyleShortcutsEnabled;
    public void setStyleShortcutsEnabled(boolean enabled) { mStyleShortcutsEnabled = enabled; }
    public boolean isStyleShortcutEnabled() { return mStyleShortcutsEnabled; }
}
