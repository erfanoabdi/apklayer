// android.content.res.TypedArray -- the result of
// Context.obtainStyledAttributes(...). Apps (especially androidx
// widget constructors) call this to read typed values (int, float,
// string, drawable, etc.) for a set of styleable attribute IDs.
//
// We don't have a resources.arsc parser, so we can't actually
// resolve attribute IDs to indices in the styled-attributes vector.
// Instead this stub returns "defaultValue" for every getter and
// reports zero indexes-with-values. AOSP semantics for the
// no-matching-attribute case behave identically: callers see the
// caller-supplied default and continue. This is enough to let
// View / Preference constructors complete without throwing, which
// is the immediate goal -- even if the resulting widget isn't
// styled, the activity reaches setContentView.
package android.content.res;

public class TypedArray implements AutoCloseable {
    private final Resources resources;
    private final int[] data;       // length parallel to attrs[]
    // v1.0.92: store the int[] of attribute IDs the caller asked us to
    // resolve. The IDs are local-package R.attr.* values populated at
    // runtime via AppClassLoader's R-class hot-init (per v1.0.75); we
    // can reverse-lookup the attribute NAME ("windowNoTitle",
    // "fastScrollerEnabled", ...) via Resources.macapkGetResourceIndex().
    // Enables attribute-aware getBoolean defaults so AppCompat's theme
    // probe and RecyclerView's fastScrollerEnabled don't have to share
    // a single global behavior.
    private final int[] attrs;
    // v1.0.93: when TypedArray is the result of
    // `obtainStyledAttributes(AttributeSet set, int[] attrs)`, hold the
    // AttributeSet so getResourceId / getString / getInt / etc. can
    // look up actual values from the inflated XML by attribute NAME.
    // Without this, every getter returns the caller's default and
    // BottomNavigationView.<init> reads its `app:menu` attribute as 0
    // -> Resources.getXml(0) -> NotFoundException.
    private android.util.AttributeSet attributeSet;
    // docs/attr-theme-threading_2026-07-31.md: the Theme this TypedArray was
    // obtained through, or null. AOSP's TypedArray always carries the Theme
    // (or at least the Resources) it was resolved against, because a styled
    // attribute's value can itself be a `?attr/` reference that needs
    // resolving against THAT theme -- see getColor()/getValue() below, the
    // fleet-wide consumers. Non-final (like attributeSet above) so the two
    // pre-existing root constructors don't need touching; every OLD call
    // site keeps constructing with theme==null, which resolveColorString
    // treats identically to the pre-threading static path (Resources.Theme
    // .resolveAttrColor degrades to the exact old behavior when the theme
    // has no applied-style cascade, so this is additive, not a behavior
    // change, for any caller that doesn't pass one).
    private Resources.Theme theme;
    /** Internal: constructed by Context.obtainStyledAttributes (and
     *  the Theme.obtainStyledAttributes variant). */
    public TypedArray(Resources r, int attrCount) {
        this.resources = r;
        this.data  = new int[Math.max(0, attrCount)];
        this.attrs = new int[Math.max(0, attrCount)];
    }
    public TypedArray(Resources r, int[] attrs) {
        this.resources = r;
        int n = attrs == null ? 0 : attrs.length;
        this.data  = new int[n];
        this.attrs = new int[n];
        if (n > 0) System.arraycopy(attrs, 0, this.attrs, 0, n);
    }
    public TypedArray(Resources r, int[] attrs, android.util.AttributeSet set) {
        this(r, attrs);
        this.attributeSet = set;
    }
    /** Theme-aware variant of the (Resources, int[]) ctor -- used by
     *  Theme.obtainStyledAttributes(int resId, int[]) when NO AttributeSet is
     *  bound (the resId==0 / unresolved-style fallback shape). */
    public TypedArray(Resources r, int[] attrs, Resources.Theme theme) {
        this(r, attrs);
        this.theme = theme;
    }
    /** Theme-aware variant of the (Resources, int[], AttributeSet) ctor --
     *  the common real-world shape: a View/widget constructor's
     *  `context.obtainStyledAttributes(attrs, styleable, defStyleAttr,
     *  defStyleRes)` routes through Context.obtainStyledAttributes ->
     *  Theme.obtainStyledAttributes, which now threads `this` (the Theme)
     *  through here so getColor()/getValue() resolve any `?attr/`-valued
     *  styled attribute against the SAME Theme the rest of the attrs came
     *  from -- not always the static application theme. */
    public TypedArray(Resources r, int[] attrs, android.util.AttributeSet set,
                       Resources.Theme theme) {
        this(r, attrs, set);
        this.theme = theme;
    }

    /** Look up the attribute at `index` in the bound AttributeSet by
     *  name (resolved through ResourceIndex). Returns null if either
     *  the AttributeSet wasn't provided or the attr isn't present in
     *  the XML. */
    private String setValueAt(int index) {
        if (attributeSet == null) return null;
        String name = attrNameAt(index);
        if (name == null) return null;
        // v1.0.918: namespace-AWARE lookup. The claim that used to live
        // here -- "the styleable attr id is unique per attribute name
        // regardless of namespace" -- is FALSE whenever a library declares
        // its own attr sharing a framework attr's local name. On a device
        // the slot is filled by attr RESOURCE ID, so the namespaces never
        // cross: an app-package slot (0x7f......) reads ONLY
        // non-android-namespaced XML attributes. (Framework slots kept the
        // legacy any-namespace order here until 2026-09-26; they now take the
        // mirror-image rule below, MACAPK_TYPEDARRAY_FW_SLOT_ID.)
        // MACAPK_ATTR_NS_EXACT=0 reverts to the fuzzy match: a labelled
        // bisect gate, not a mode we support.
        if ((attrs[index] >>> 24) == 0x7f
                && !"0".equals(System.getenv("MACAPK_ATTR_NS_EXACT"))) {
            return attributeSet.getAttributeValue(
                "http://schemas.android.com/apk/res-auto", name);
        }
        // The mirror image for a FRAMEWORK slot (0x01......): only the android
        // identity, in each layer this file can tell apart (a namespace-exact
        // XML set, and the StyleBackedAttributeSet style tiers). NOT the
        // style= tier of layout inflation: LayoutInflater.DomBackedAttributeSet
        // answers that from its own prefix-stripped item map before any layer
        // here is asked. See MACAPK_TYPEDARRAY_FW_SLOT_ID below.
        if ((attrs[index] >>> 24) == 0x01 && MACAPK_TYPEDARRAY_FW_SLOT_ID_LIVE) {
            String fv = macapkFrameworkSlotValue(attributeSet,
                name.startsWith("android:") ? name.substring(8) : name);
            if (fv != MACAPK_FW_SLOT_LEGACY) return fv;
        }
        // Legacy (MACAPK_TYPEDARRAY_FW_SLOT_ID=0, or the MACAPK_FW_SLOT_LEGACY
        // error path): any namespace first.
        String v = attributeSet.getAttributeValue(null, name);
        if (v != null) return v;
        // Fallback: try common Android namespace explicitly.
        return attributeSet.getAttributeValue(MACAPK_NS_ANDROID, name);
    }

    // ------------------------------------------------------------------
    // Framework-slot identity (tafollow lane, 2026-09-26): cluster A's open
    // item, docs/widget-fidelity-cluster_2026-09-25.md "Cluster A
    // implementation plan", fallback #1, done the AOSP way.
    //
    // AOSP fills a styleable slot by attribute RESOURCE ID in every tier.
    // libs/androidfw/AttributeResolution.cpp ApplyStyle (android14-release):
    //     :253  const uint32_t cur_ident = attrs[ii];
    //     :264  xml_attr_finder.Find(cur_ident)        XML attributes, keyed by
    //           parser_->getAttributeNameResID(index) (XmlAttributeFinder :46)
    //     :277  xml_style_attr_finder.Find(cur_ident)  the style= bag
    //     :289  def_style_attr_finder.Find(cur_ident)  defStyleAttr / defStyleRes
    //     :308  theme->GetAttribute(cur_ident)         the theme
    // An app's `app:X` / style item `X` is its own 0x7f attribute and the
    // platform's `android:X` is 0x0101xxxx, so the two are DIFFERENT attributes
    // even when they share a local name. A framework slot can only be filled
    // by an `android:X` XML attribute, an app style's `android:X` item, or a
    // platform style's item (which framework-res keys bare, because inside it
    // `X` IS `android:X`).
    //
    // Ours asked the bound set with a NULL namespace first, which matches ANY
    // namespace, and Resources' StyleBackedAttributeSet ignores the namespace
    // and resolves the name as given, where a bare name is the APP attribute
    // (ResourceIndex.resolveAttrInTheme pass 1; cluster A1 made that exact by
    // stopping platform styles answering it). So a framework slot read an
    // app-namespace item whenever any tier declared one of the same name.
    //
    // MEASURED VICTIM (debug/tafollow_gate.sh; one of the reads the attrident
    // A2 census left unmoved): F-Droid's own ThemeOverlay.App.MaterialAlertDialog
    // sets AppCompat's `dialogCornerRadius` (0x7f040190) to 28dp and inherits
    // `android:dialogCornerRadius=@null` from
    // Base.V14.ThemeOverlay.MaterialComponents.MaterialAlertDialog. The
    // framework slot 0x01010571 read the 28dp (hasValue true, 56px); a device
    // reads the @null: TYPE_NULL, hasValue false, the caller's default.
    //
    // REACH, stated exactly (it is narrower than "every tier"):
    //   COVERED  the defStyleAttr / defStyleRes style tier, the style tier of
    //            obtainStyledAttributes(resId, attrs), the style= tier when the
    //            direct set is namespace-exact (a compiled-XML parser such as
    //            BinaryXmlParser / DomXmlResourceParser), the XML attributes of
    //            such a set, and (MACAPK_TYPEDARRAY_FW_THEME_ID) the theme tier.
    //   OPEN     the style= tier of LAYOUT INFLATION. There the direct layer is
    //            LayoutInflater.DomBackedAttributeSet, whose lookup(android-ns,
    //            X) falls back to its own macapkStyleItems() map; that map
    //            strips `android:` and merges both identities of the element's
    //            style= chain under one key, and it answers BEFORE the
    //            identity-correct StyleBacked layer below it is asked. So a view
    //            with style= in a layout still reads the app item for a
    //            framework slot whenever its style= chain declares the app
    //            attribute. The fix belongs in LayoutInflater (key that map by
    //            identity); the tafollow gate carries it as an XFAIL row, so
    //            the fix reports XPASS.
    //
    // HOW, inside this file: Resources.StyleMergedAttributeSet nests the tiers
    // (Theme.obtainStyledAttributes builds StyleMerged(StyleMerged(set,
    // style=), defStyle)); the layers are walked in that order and each is
    // asked for the android identity in the form it understands:
    //   - a style tier (StyleBackedAttributeSet) by the NAME `android:X`, which
    //     ResourceIndex answers from the leaf-most app `android:X` item, else
    //     the platform ancestor's bare `X` (the ApplyStyle bag order);
    //   - an XML tier by the android NAMESPACE only. No any-namespace fallback:
    //     a namespace-less attribute has no resource id in compiled XML, so it
    //     cannot fill a framework slot on a device either. The one shape where
    //     that would lose a value is XML compiled with stripped namespaces
    //     (aapt2 --no-xml-namespaces), whose attributes keep their ids; the
    //     corpus has NONE (63 APKs, every compiled res/**.xml scanned
    //     2026-09-26: the only namespace-less attributes are genuinely
    //     namespace-less ones, network-security-config src/path/domain and
    //     locale-config key/split). If one ever appears, the fix belongs in
    //     BinaryXmlParser, which has the attribute's id in hand.
    // The layer fields are private to Resources, so they are read
    // reflectively; if that layout ever changes the lookup is disabled as a
    // whole (one stderr line, the exact legacy path) rather than half applied,
    // and a failed read of one set takes the visible legacy path for that read
    // (MACAPK_FW_SLOT_LEGACY), never a half-identity answer. (Two
    // package-private accessors on StyleMergedAttributeSet would retire the
    // reflection; Resources.java was not this change's file.)
    //
    // BLAST RADIUS, measured (debug/tafollow_gate.sh census, 10 apps, every
    // app attribute name that is also a platform one, ~290 per app). These
    // are the census S shape, obtainStyledAttributes(styleRes, {fw}) over an
    // empty theme, i.e. the defStyle / explicit-style tier, NOT layout
    // inflation's style= tier (see REACH): in the style tier alone 157,273 of
    // 221,882 framework-slot reads change their raw
    // and 115,291 their resolved record; 98,918 of those are in THEME styles
    // (a theme's `android:colorControlNormal` against AppCompat's bare one),
    // 16,372 in widget styles (elevation, backgroundTint, iconTint,
    // contentInsetStart ...), 1 in a text appearance, and every one is exactly
    // the resolver's `android:` answer. Checked WITHOUT our resolver too
    // (debug/tafollow_verify/bagoracle.py over the apktool-decoded bags of each
    // app and of framework-res): every scored style-tier and theme-tier read is
    // the bag's android:X item, every moved read's old value was the bag's APP
    // item, 0 disagreements in 10 apps (Google Calculator's 166 Material You
    // rows aside: ResourceIndex's pre-existing dynamic-colour emulation, the
    // same in both arms). Platform names the app does NOT declare never move
    // (the complement census: byte-identical in every arm), so an app with no
    // colliding attribute names sees no change at all. Over the @null / @empty population
    // (the attrident A2 census, 23,131 inflation-shape reads) exactly the four
    // alert-dialog overlay reads move (F-Droid's, and AnkiDroid's three). XML
    // tier upper bound: 1,911 app-namespace attributes with a platform twin and
    // no android: sibling (showAsAction, tint, iconSpaceReserved,
    // singleLineTitle, backgroundTint ...), which a device's library code reads
    // through its APP slot.
    //
    // NOT covered here, on purpose (files this change does not own; the
    // tafollow gate carries them as XFAIL rows so their fixes show up as
    // XPASS): the layout-inflation style= tier (REACH above:
    // LayoutInflater.DomBackedAttributeSet's own style= item map strips
    // `android:` and merges both identities under one key), and
    // Resources.Theme.resolveAttribute, which names a framework id bare.
    //
    // MACAPK_TYPEDARRAY_FW_SLOT_ID=0 restores the any-namespace-first lookup
    // exactly: a labelled bisect gate, not a mode we support.
    // MACAPK_ATTR_IDENTITY=0 (cluster A1's gate, in ResourceIndex) turns this
    // off too, and the theme-tier half below: A1 and this are ONE rule,
    // attribute identity, applied at two layers, and A1's documented bisect
    // contract is that =0 restores the pre-identity answers. For a TypedArray
    // read those include this lookup, since asking `android:X` sidesteps the
    // bare-name walk A1 fixed; debug/attrident_gate.sh's control (a) asserts
    // exactly that contract, row by row.
    // ------------------------------------------------------------------
    private static final boolean MACAPK_ATTR_IDENTITY_ON =
        !"0".equals(System.getenv("MACAPK_ATTR_IDENTITY"));
    static final boolean MACAPK_TYPEDARRAY_FW_SLOT_ID = MACAPK_ATTR_IDENTITY_ON
        && !"0".equals(System.getenv("MACAPK_TYPEDARRAY_FW_SLOT_ID"));
    private static final String MACAPK_NS_ANDROID =
        "http://schemas.android.com/apk/res/android";
    /** Resources.StyleMergedAttributeSet's private `direct` / `style` layers,
     *  or null when the fields are not there. */
    private static final java.lang.reflect.Field MACAPK_MERGED_DIRECT;
    private static final java.lang.reflect.Field MACAPK_MERGED_STYLE;
    /** The gate AND the layer fields: the identity lookup runs whole or not
     *  at all. */
    private static final boolean MACAPK_TYPEDARRAY_FW_SLOT_ID_LIVE;
    static {
        java.lang.reflect.Field d = null, s = null;
        if (MACAPK_TYPEDARRAY_FW_SLOT_ID) {
            try {
                d = Resources.StyleMergedAttributeSet.class.getDeclaredField("direct");
                s = Resources.StyleMergedAttributeSet.class.getDeclaredField("style");
                d.setAccessible(true);
                s.setAccessible(true);
            } catch (Throwable t) {
                d = null; s = null;
                System.err.println("[Mac.apk.TypedArray] framework-slot identity OFF:"
                    + " Resources.StyleMergedAttributeSet has no direct/style fields ("
                    + t + ")");
            }
        }
        MACAPK_MERGED_DIRECT = d;
        MACAPK_MERGED_STYLE = s;
        MACAPK_TYPEDARRAY_FW_SLOT_ID_LIVE = d != null && s != null;
    }

    /** macapkFrameworkSlotValue's "could not separate the layers" answer,
     *  compared by IDENTITY (never equals()): setValueAt then runs the exact
     *  legacy lookup instead. Not a value any AttributeSet can return. */
    private static final String MACAPK_FW_SLOT_LEGACY = new String("<legacy fw-slot lookup>");
    private static volatile boolean macapkFwSlotLegacyWarned;

    /** The value of the framework attribute `android:name` (`name` given
     *  bare) from `set`'s tiers, highest precedence first, or null; or
     *  MACAPK_FW_SLOT_LEGACY when a merged set's layers could not be read. */
    private static String macapkFrameworkSlotValue(android.util.AttributeSet set,
                                                   String name) {
        if (set instanceof Resources.StyleMergedAttributeSet) {
            android.util.AttributeSet direct, style;
            try {
                direct = (android.util.AttributeSet) MACAPK_MERGED_DIRECT.get(set);
                style = (android.util.AttributeSet) MACAPK_MERGED_STYLE.get(set);
            } catch (Throwable t) {
                // Unreachable while the static block found both fields (the
                // instanceof above rules out a wrong receiver), but if it ever
                // fires it must NOT ask the merged set for the android
                // namespace: StyleMergedAttributeSet forwards to its
                // StyleBackedAttributeSet, which ignores the namespace and
                // resolves the bare name as the APP attribute -- the exact
                // crossing this lookup exists to prevent. So hand back the
                // sentinel, and setValueAt runs the visible legacy path.
                if (!macapkFwSlotLegacyWarned) {
                    macapkFwSlotLegacyWarned = true;
                    System.err.println("[Mac.apk.TypedArray] framework-slot identity:"
                        + " could not read StyleMergedAttributeSet layers (" + t
                        + "); legacy any-namespace lookup for this read");
                }
                return MACAPK_FW_SLOT_LEGACY;
            }
            // StyleMergedAttributeSet.getAttributeValue's own order: direct
            // wins, then its style. (A nested MACAPK_FW_SLOT_LEGACY is
            // non-null, so both returns carry it up unchanged.)
            String v = direct != null ? macapkFrameworkSlotValue(direct, name) : null;
            if (v != null) return v;
            return style != null ? macapkFrameworkSlotValue(style, name) : null;
        }
        if (set instanceof Resources.StyleBackedAttributeSet) {
            return set.getAttributeValue(MACAPK_NS_ANDROID, "android:" + name);
        }
        return set.getAttributeValue(MACAPK_NS_ANDROID, name);
    }

    // The THEME tier has the same identity rule, with its own gate so the two
    // can be bisected apart. ApplyStyle's last tier is
    // `theme->GetAttribute(cur_ident)` (AttributeResolution.cpp :308,
    // android14-release), again by id. Ours asked the theme cascade for the
    // BARE name, which ResourceIndex answers from the app item first, so an
    // app theme item that shares a platform attribute's name answered the
    // framework slot. docs/textview-ctor-appearance_2026-09-26.md "Found in
    // siblings" #1 measured two: AnkiDroid's theme `textAllCaps=false` (the
    // AppCompat attribute) answered android:textAllCaps, and anuto's app
    // `shadowColor` answered android:shadowColor. Census (the tafollow gate's
    // T rows, the app theme x ~290 shared names x 10 apps): 925 of 2,794 reads
    // move, 533 in their resolved record, each to the theme cascade's
    // `android:` answer; e.g. AnkiDroid's android:actionBarSize goes from
    // AppCompat's m3_appbar_size_compact (64dp) to the platform theme's 56dp,
    // which is what android.R.attr.actionBarSize reads on a device under a
    // Material3 theme.
    //
    // MACAPK_TYPEDARRAY_FW_THEME_ID=0 restores the bare-name theme query: a
    // labelled bisect gate, independent of MACAPK_TYPEDARRAY_FW_SLOT_ID (and,
    // like it, off under MACAPK_ATTR_IDENTITY=0).
    static final boolean MACAPK_TYPEDARRAY_FW_THEME_ID = MACAPK_ATTR_IDENTITY_ON
        && !"0".equals(System.getenv("MACAPK_TYPEDARRAY_FW_THEME_ID"));

    /** The name the THEME tier is asked for `index`: `android:` + name for a
     *  framework slot, the name unchanged otherwise (and with the gate off). */
    private String macapkThemeTierName(int index, String name) {
        if (!MACAPK_TYPEDARRAY_FW_THEME_ID || name == null || name.startsWith("android:")
                || index < 0 || index >= attrs.length || (attrs[index] >>> 24) != 0x01) {
            return name;
        }
        return "android:" + name;
    }

    // ------------------------------------------------------------------
    // v1.0.913: the THEME as a real value source.
    //
    // AOSP's ApplyStyle fills each requested attribute from "first XML
    // attributes, then XML style, then default style, and finally the
    // theme" (AttributeResolution.cpp:292, android11-release). Our bound
    // AttributeSet covers the first three tiers; the theme tier did not
    // exist, so a TypedArray obtained through Theme.obtainStyledAttributes
    // answered nothing for every attribute the style did not restate (the
    // whole OSA.style/OSA.theme fwprobe families). `macapkThemeTier` is set
    // by the Theme.obtainStyledAttributes construction sites -- exactly the
    // shapes AOSP hands to ApplyStyle with a null parser -- and NOT by the
    // XML-inflation path, whose tier-5 enablement is a separate, fleet-wide
    // measurement (see the session notes).
    //
    // ⚠ getString/getText read the theme through their OWN validated path
    // (macapkThemeString below), not this tier: it resolves via
    // theme.resolveAttrRaw -- whose application-theme fallback is deliberate,
    // so a manifest-theme-only app's viewInflaterClass still resolves --
    // where this tier is cascade-only. macapkThemeString is ON by default
    // since v1.0.920 (MACAPK_THEME_STRING_FALLBACK=0 opts out); it sat gated
    // v1.0.897-919 while its premature enablement measured opencalculator
    // 93.54 -> 84.39 (root cause since fixed -- see the gate site below).
    // Every getter validates what it parses and falls back to the caller's
    // default, which is the per-getter shape docs/refuted-approaches.md
    // prescribes in place of the reverted v1.0.894 blanket fallback.
    // ------------------------------------------------------------------
    boolean macapkThemeTier;

    /** Read ONCE: macapkRawValueAt is on the hot path of every attribute read
     *  of every inflated view, and a getenv per call is not free. */
    private static final String MACAPK_TRACE_ATTR =
        System.getenv("MACAPK_TRACE_ATTR");
    private static final String MACAPK_THEME_TIER_XML =
        System.getenv("MACAPK_THEME_TIER_XML");

    /** The theme tier is LIVE only when the Theme actually carries an
     *  applied-style cascade. The distinction is fleet-load-bearing:
     *  most apps never call setTheme() -- their manifest theme is tracked
     *  as a NAME in ResourceIndex, never applied onto a Theme -- so their
     *  activity Theme's cascade is EMPTY, and honest presence answers
     *  there would flip AppCompat's own guard
     *      if (!a.hasValue(AppCompatTheme_windowActionBar)) throw
     *          new IllegalStateException("You need to use a Theme.AppCompat
     *          theme (or descendant) with this activity.")
     *  from passing (legacy universal-true) to throwing in every AppCompat
     *  activity. A populated cascade (explicit applyStyle/setTheme, and
     *  every probe theme) answers honestly; an empty one keeps the legacy
     *  stub behavior until the manifest theme is actually applied to the
     *  cascade (the "two theme anchors" work, tracked in Current_State). */
    private boolean macapkThemeTierLive() {
        if (!macapkThemeTier || theme == null) return false;
        try { return theme.macapkHasAppliedStyles(); }
        catch (Throwable ignored) { return false; }
    }

    /** The raw source string for `index` across the tiers this TypedArray
     *  models: bound AttributeSet (XML/style/defStyle) first, then -- for
     *  theme-tier arrays -- the Theme's OWN applied-style cascade. The
     *  cascade-only lookup matters: `resolveAttrRaw`'s application-theme
     *  fallback would make a standalone `newTheme()+applyStyle(X)` answer
     *  attributes X never declared (the manifest theme's values), where the
     *  emulator answers `has=0`. Null when nothing answers. */
    private String macapkRawValueAt(int index) {
        if (MACAPK_TYPEDARRAY_SLOT_MEMO && index >= 0 && index < attrs.length) {
            return macapkMemoRaw(index);
        }
        return macapkRawValueAtImpl(index, setValueAt(index));
    }

    // ------------------------------------------------------------------
    // Per-slot memo (tafollow lane, 2026-09-26): MACAPK_TYPEDARRAY_SLOT_MEMO.
    //
    // AOSP resolves every slot ONCE: ThemeImpl.obtainStyledAttributes calls
    // mAssets.applyStyle(..., array.mDataAddress, array.mIndicesAddress)
    // (ResourcesImpl.java :1394-1395, android14-release), ApplyStyle writes each
    // attribute's final record into that array (AttributeResolution.cpp
    // :332-338, out_values[STYLE_TYPE] ... [STYLE_SOURCE_RESOURCE_ID]), and
    // every accessor then reads the same record (TypedArray.java :1174-1182
    // hasValue, :1195-1204 hasValueOrEmpty, :1219 peekValue all index mData).
    // A TypedArray is a snapshot, not a live query. Ours re-walked the
    // tier cascade (bound set, style tiers, then the theme cascade) on EVERY
    // accessor, so the common `hasValue(i)` / `peekValue(i)` / `getX(i)`
    // sequence walked it three times, and a `getIndexCount()` +
    // `getIndex(0..n-1)` loop walked all n slots n+1 times.
    // docs/textview-ctor-appearance_2026-09-26.md measured the cost: the
    // presence scan alone was 32% of a TextView constructor's read, 25% of it
    // the theme cascade walk.
    //
    // The memo keeps, per slot, the bound-set answer (tiers 1-4, what
    // setValueAt returns and getString reads), the full raw (tiers 1-5), AND
    // the last-reference origin read right after the acquisition, because
    // macapkRecordAt turns that origin into the record's resourceId and a
    // memo hit performs no acquisition to read it from. The acquisition runs
    // exactly as macapkRecordAt always ran it (clear the origin slot,
    // acquire, read the slot), so the cached origin is the one the record
    // would have computed. Clearing the slot here as well is invisible to
    // every other reader: each one clears before its own producing call.
    // Two levels, so a getString-only read does not pay for the theme tier
    // it never consults: level 1 = the bound-set answer (and, when that is
    // non-null, the raw and origin with it, since tier 5 is then never
    // asked); level 2 = the theme tier's raw and origin too.
    //
    // Equivalence is a gated property, not an assumption: the tafollow
    // gate's memo census reads every slot through every accessor with the
    // memo ON and OFF and requires byte-identical answers, and its mutant
    // control (a memo that drops the origin) must fail.
    //
    // MACAPK_TYPEDARRAY_SLOT_MEMO=0 restores the per-accessor walk exactly: a
    // labelled bisect gate.
    // ------------------------------------------------------------------
    static final boolean MACAPK_TYPEDARRAY_SLOT_MEMO =
        !"0".equals(System.getenv("MACAPK_TYPEDARRAY_SLOT_MEMO"));
    /** 0 = nothing cached, 1 = bound-set answer only (it was null),
     *  2 = bound-set answer, raw and origin. */
    private byte[] macapkMemoState;
    private String[] macapkMemoSet;
    private String[] macapkMemoRawValue;
    private int[] macapkMemoOrigin;

    /** Level 1: the bound set's answer for `index` (a valid index). */
    private String macapkMemoSetValue(int index) {
        if (macapkMemoState == null) {
            int n = attrs.length;
            macapkMemoState = new byte[n];
            macapkMemoSet = new String[n];
            macapkMemoRawValue = new String[n];
            macapkMemoOrigin = new int[n];
        }
        if (macapkMemoState[index] == 0) {
            Resources.macapkClearValueOrigin();
            String v = setValueAt(index);
            macapkMemoSet[index] = v;
            if (v != null) {
                // Tier 5 is never asked for a bound value; the acquisition is
                // complete, so the origin is read now, as macapkRecordAt would.
                macapkMemoRawValue[index] = macapkRawValueAtImpl(index, v);
                macapkMemoOrigin[index] = Resources.macapkReadValueOrigin();
                macapkMemoState[index] = 2;
            } else {
                macapkMemoState[index] = 1;
            }
        }
        return macapkMemoSet[index];
    }

    /** Level 2: the raw value across all tiers for `index` (a valid index). */
    private String macapkMemoRaw(int index) {
        macapkMemoSetValue(index);
        if (macapkMemoState[index] == 1) {
            // The bound set was silent, so the raw is the theme tier's. Every
            // theme-tier answer comes out of ResourceIndex.resolveAttrInTheme,
            // which clears the origin slot on entry, so clearing again here
            // yields the slot the one-pass acquisition would have left.
            Resources.macapkClearValueOrigin();
            macapkMemoRawValue[index] = macapkRawValueAtImpl(index, null);
            macapkMemoOrigin[index] = Resources.macapkReadValueOrigin();
            macapkMemoState[index] = 2;
        }
        return macapkMemoRawValue[index];
    }

    /** The bound-set answer (tiers 1-4) for `index`: memoised, or setValueAt. */
    private String macapkBoundValueAt(int index) {
        if (MACAPK_TYPEDARRAY_SLOT_MEMO && index >= 0 && index < attrs.length) {
            return macapkMemoSetValue(index);
        }
        return setValueAt(index);
    }

    /** macapkRawValueAt's body: `v` is setValueAt(index), already computed. */
    private String macapkRawValueAtImpl(int index, String v) {
        // TEMPORARY DIAGNOSTIC (MACAPK_TRACE_ATTR=<name>): print every tier's
        // answer for one attribute, so "which tier failed" is measured rather
        // than reasoned about. Added while chasing the Material
        // ThemeEnforcement throw, where four successive fixes to the theme
        // TABLE could not have worked because the theme TIER is not consulted
        // at all on the XML-inflation path. Remove once that is settled.
        String trace = MACAPK_TRACE_ATTR;
        if (trace != null) {
            String nm = attrNameAt(index);
            if (trace.equals(nm)) {
                boolean applied = false;
                try { applied = theme != null && theme.macapkHasAppliedStyles(); }
                catch (Throwable ignored) {}
                System.err.println("[TRACE_ATTR] name=" + nm
                    + " id=0x" + Integer.toHexString(attrs[index])
                    + " setValue=" + v
                    + " themeTier=" + macapkThemeTier
                    + " theme=" + (theme != null)
                    + " hasAppliedStyles=" + applied
                    + " cascadeOnly=" + safeCascade(nm)
                    + " full=" + safeFull(nm));
            }
        }
        if (v != null) return v;
        // TIER 5, THE THEME -- now also on the XML-INFLATION path (v1.0.928).
        //
        // AOSP's ApplyStyle fills each requested attribute from "first XML
        // attributes, then XML style, then default style, and finally the
        // theme" (AttributeResolution.cpp:292, android11-release). FIVE tiers,
        // unconditionally -- there is no device path on which an inflated view
        // skips the theme. We implemented tier 5 only when the TypedArray came
        // from Theme.obtainStyledAttributes (macapkThemeTier), i.e. the
        // set == null shapes, and the XML path stopped after tier 4.
        //
        // MEASURED CONSEQUENCE of that gap: Material's ThemeEnforcement asks an
        // XML-inflated BottomNavigationView for `android:textAppearance`, which
        // no app in the fleet declares on the widget because on a device it
        // comes from the framework theme (themes_material.xml:50-51/335-336,
        // `Theme.Material[.Light]` -> `@style/TextAppearance.Material`). We
        // answered null, it read -1 and threw out of the CONSTRUCTOR, our
        // LayoutInflater substituted a FrameLayout placeholder, and the app's
        // own cast aborted onCreate -- deskclock and antennapod rendered
        // entirely BLANK and F-Droid's bottom nav bar was empty.
        //
        // A trace at this exact site is what settled it, after three earlier
        // fixes to the theme TABLE could not have worked:
        //   name=textAppearance id=0x1010034 setValue=null themeTier=false
        //   theme=true hasAppliedStyles=true
        //   cascadeOnly=@android:style/TextAppearance.Material
        // The theme had the right answer the whole time and was never asked.
        //
        // The `macapkHasAppliedStyles` guard is KEPT and is load-bearing: most
        // apps never call setTheme(), so their activity Theme's cascade is
        // EMPTY, and honest presence answers there would flip AppCompat's own
        // `if (!a.hasValue(AppCompatTheme_windowActionBar)) throw` guard from
        // passing to throwing in every AppCompat activity. An empty cascade
        // keeps the legacy behaviour exactly as before.
        //
        // MACAPK_THEME_TIER_XML=0 reverts to the tier-4 stop: a labelled bisect
        // gate for the fleet measurement, not a mode we support.
        // ⚠ v1.0.931: the `macapkHasAppliedStyles()` probe that used to gate this
        // was INERT, and proving that is what found the REAL gate one line down.
        // `macapkResolveAttrRawCascadeOnly` is a loop over `appliedStyles` and
        // nothing else, so an empty cascade answered null whether the gate opened
        // or not. MEASURED on bobball: `cascadeOnly=null` with the gate shut, and
        // an empty for-loop with it open -- identical.
        boolean tier5 = macapkThemeTier || !"0".equals(MACAPK_THEME_TIER_XML);
        if (tier5 && theme != null) {
            String name = attrNameAt(index);
            if (name != null) {
                // v1.0.918: `android:windowBackground` is deliberately NOT
                // served from the theme tier. Serving it is the honest AOSP
                // answer, but its one theme-only consumer -- AppCompat's
                // AppCompatDelegateImpl ctor, which does a TintTypedArray
                // getDrawableIfKnown over {android.R.attr.windowBackground}
                // and installs the result as the DIALOG window background --
                // gets `abc_dialog_material_background`, an UNTINTED WHITE
                // inset shape (verified in the app's compiled XML: no
                // android:tint anywhere). Whatever darkens that drawable on
                // a real dark-themed device is not implemented here, so the
                // honest serve replaced our emulator-matching
                // colorBackgroundFloating panel with flat white and turned
                // unitconverter's dark Help dialog into white-on-white
                // (recap 85.64 -> 64.04 at v1.0.916).
                // This costs ZERO fwprobe rows: the battery reaches
                // windowBackground only via Theme.resolveAttribute, which
                // never passes through here.
                // MACAPK_THEME_TIER_WINDOWBG=1 re-enables the honest serve
                // for parity work. RETIRE this exclusion once dialog
                // window-background theming works end to end and the
                // unitconverter pair holds.
                if (("windowBackground".equals(name)
                        || "android:windowBackground".equals(name))
                        && !"1".equals(System.getenv("MACAPK_THEME_TIER_WINDOWBG"))) {
                    return null;
                }
                // THE REAL TIER-5 GATE. An activity whose manifest theme we
                // could not NAME has an EMPTY applied-style cascade -- `applyStyle`
                // drops a framework style id because `macapkTypeNameOf` only knows
                // the APP's ids -- and a cascade-only tier 5 then answers nothing
                // at all. MEASURED on bobball: seven inflated widgets asked for
                // `textAppearance` and got null, while the honest resolver held
                // the answer the whole time:
                //   name=textAppearance setValue=null themeTier=false
                //   hasAppliedStyles=false cascadeOnly=null
                //   full=@android:style/TextAppearance.Material
                // That is v1.0.928's bug still live, one tier deeper. Affects
                // bobball, sfsolitaire and frozenbubble in the fleet (20 of 63
                // corpus apps have a framework-or-absent manifest theme; the rest
                // are app-local, nameable, and already populated).
                //
                // `macapkResolveAttrRawHonest` RETURNS the cascade-only answer
                // whenever the cascade is populated, so every app that works today
                // is byte-identical -- the manifest anchor fires only for a theme
                // our wiring never modelled.
                //
                // THEME-PROBE arrays (`macapkThemeTier`) deliberately stay
                // cascade-only: that is the `ProbeBase.Empty` contract the tier was
                // written to protect, and it is what keeps the OSA.* fwprobe
                // families answering `has=0`.
                //
                // The theme is asked for the slot's own identity: `android:X` for a
                // framework slot (MACAPK_TYPEDARRAY_FW_THEME_ID, see below).
                final String themeName = macapkThemeTierName(index, name);
                try {
                    return macapkThemeTier
                        ? theme.macapkResolveAttrRawCascadeOnly(themeName)
                        : theme.macapkResolveAttrRawHonest(themeName);
                } catch (Throwable ignored) {}
            }
        }
        return null;
    }

    /** Diagnostic helpers for MACAPK_TRACE_ATTR (see macapkRawValueAt). */
    private String safeCascade(String name) {
        if (theme == null || name == null) return "n/a";
        try { return String.valueOf(theme.macapkResolveAttrRawCascadeOnly(name)); }
        catch (Throwable t) { return "EX:" + t.getClass().getSimpleName(); }
    }
    private String safeFull(String name) {
        if (theme == null || name == null) return "n/a";
        try { return String.valueOf(theme.macapkResolveAttrRawHonest(name)); }
        catch (Throwable t) { return "EX:" + t.getClass().getSimpleName(); }
    }

    // Resolved-record cache: getValue/getType/peekValue/hasValue agree on
    // one resolution per index instead of re-deriving (and possibly
    // disagreeing - the old per-getter type heuristics did). State:
    // 0 = not yet resolved, 1 = present, 2 = absent.
    private android.util.TypedValue[] macapkRecords;
    private byte[] macapkRecordState;

    /** The aapt-shaped compiled record for `index` (type/data/resourceId/
     *  string exactly as the device's resource table would answer), or null
     *  when the attribute is absent or its raw value cannot be validated.
     *  Absent-or-unvalidatable both read as "no record", which keeps every
     *  caller-default contract intact. */
    private android.util.TypedValue macapkRecordAt(int index) {
        if (index < 0 || index >= attrs.length) return null;
        if (macapkRecordState == null) {
            macapkRecordState = new byte[attrs.length];
            macapkRecords = new android.util.TypedValue[attrs.length];
        }
        byte st = macapkRecordState[index];
        if (st == 1) return macapkRecords[index];
        if (st == 2 || st == 3) return null;
        android.util.TypedValue out = new android.util.TypedValue();
        boolean ok = false;
        // v1.0.917: pre-clear the last-reference slot, because an XML-bound
        // AttributeSet serves raws WITHOUT passing through ResourceIndex
        // (whose theme query would have cleared it), and a stale id from an
        // earlier unrelated query must never become this record's
        // resourceId. Then capture what the acquisition recorded: a
        // style-backed raw runs resolveAttrInTheme, whose settled framework
        // scalars publish the reference id AOSP would report
        // (ResolveReference's out_last_reference -> the packed
        // STYLE_RESOURCE_ID in AttributeResolution.cpp's ApplyStyle).
        final String raw;
        final int rawOrigin;
        if (MACAPK_TYPEDARRAY_SLOT_MEMO) {
            // The memo acquired this slot the same way (clear, acquire, read)
            // and kept the origin with the raw; see MACAPK_TYPEDARRAY_SLOT_MEMO.
            raw = macapkMemoRaw(index);
            rawOrigin = macapkMemoOrigin[index];
        } else {
            Resources.macapkClearValueOrigin();
            raw = macapkRawValueAt(index);
            // Read what the ACQUISITION recorded now: the A2 check below may
            // walk a ?attr through the theme, and that query re-clears the slot.
            rawOrigin = Resources.macapkReadValueOrigin();
        }
        // Cluster A2: an @null / @empty value (literal, or a ?attr whose theme
        // leaf is one) is AOSP's TYPE_NULL record; no record, and state 3
        // remembers DATA_NULL_EMPTY for peekValue. Checked before the resolver
        // so a record can never disagree with hasValue.
        int nullData = macapkNullDataOf(index, raw);
        if (nullData >= 0) {
            macapkRecordState[index] =
                (byte) (nullData == android.util.TypedValue.DATA_NULL_EMPTY ? 3 : 2);
            macapkRecords[index] = null;
            return null;
        }
        if (raw != null && resources != null) {
            try {
                ok = resources.macapkResolveRawToTypedValue(
                        raw, attrNameAt(index), theme, true, out,
                        true, rawOrigin);
            } catch (Throwable ignored) { ok = false; }
        }
        macapkRecordState[index] = (byte) (ok ? 1 : 2);
        macapkRecords[index] = ok ? out : null;
        return ok ? out : null;
    }

    /** Return the attribute NAME for index, or null if not resolvable.
     *  Looks up `attrs[index]` (the resource ID) in the ResourceIndex
     *  (populated from apktool's `values/public.xml`). Used by
     *  attribute-aware getBoolean / getInt / etc. to recognize
     *  well-known framework attrs by name without needing to know
     *  their per-APK resource ID at compile time. */
    private String attrNameAt(int index) {
        if (index < 0 || index >= attrs.length) return null;
        int id = attrs[index];
        if (id == 0) return null;
        try {
            Object ri = Resources.macapkGetResourceIndex();
            if (ri != null) {
                java.lang.reflect.Method m = ri.getClass().getMethod("entryNameOf", int.class);
                Object name = m.invoke(ri, id);
                if (name != null) return name.toString();
            }
        } catch (Throwable t) {
            // fall through to the framework table
        }
        // FRAMEWORK attr fallback. The ResourceIndex only knows the APK's
        // own ids (public.xml, 0x7f range); FRAMEWORK attr ids (0x0101xxxx)
        // aren't in it. androidx.compose's vector parser asks for the
        // android-framework VectorDrawable styleable (android.R.attr.
        // viewportWidth, pathData, fillColor, ...), so without this map the
        // parser reads every attribute as "absent" and throws on
        // viewportWidth == 0. These ids are STABLE platform constants
        // (extracted from android.jar android.R$attr), app-agnostic, and
        // only consulted when the per-APK lookup above returned null -- so
        // an APK's own 0x7f styleables (ConstraintLayout_Layout etc.) never
        // reach here and are completely unaffected.
        return frameworkAttrName(id);
    }

    /** android.R.attr id -> name, for exactly the framework attrs the
     *  Jetpack Compose VectorDrawable parser reads (vector / group /
     *  path / clip-path styleables). Ids verified against android-35
     *  android.jar (android.R$attr). */
    private static String frameworkAttrName(int id) {
        switch (id) {
            // Common framework view attrs (ids verified against android-34
            // android.jar android.R$attr) -- needed so a TypedArray bound to a
            // resolved STYLE (obtainStyledAttributes with a defStyleAttr, e.g.
            // AppCompat's OverflowMenuButton reading android:src from
            // Widget.AppCompat.ActionButton.Overflow) can name the framework
            // attr index and look its value up in the style.
            case 0x01010119: return "src";
            case 0x010100d4: return "background";
            case 0x01010273: return "contentDescription";
            case 0x0101011d: return "scaleType";
            // <vector>
            case 0x01010003: return "name";
            case 0x01010121: return "tint";
            case 0x01010155: return "height";
            case 0x01010159: return "width";
            case 0x0101031f: return "alpha";
            case 0x010103ea: return "autoMirrored";
            case 0x010103fb: return "tintMode";
            case 0x01010402: return "viewportWidth";
            case 0x01010403: return "viewportHeight";
            // <group>
            case 0x010101b5: return "pivotX";
            case 0x010101b6: return "pivotY";
            case 0x01010324: return "scaleX";
            case 0x01010325: return "scaleY";
            case 0x01010326: return "rotation";
            case 0x0101045a: return "translateX";
            case 0x0101045b: return "translateY";
            // <path> / <clip-path>
            case 0x01010404: return "fillColor";
            case 0x01010405: return "pathData";
            // ANIMATOR + INTERPOLATOR attrs (v1.0.932). Without a NAME here,
            // attrNameAt returns null, every attribute of every inflated
            // animator reads ABSENT, and AnimatorInflater produces
            // correctly-typed ObjectAnimators carrying nothing but defaults --
            // which looks like a fix in a log and behaves exactly like the stub
            // it replaced. Ids READ OUT of /tmp/framework-res-api30.apk's
            // resources.arsc, not transcribed; styleable membership
            // cross-checked against aosp-mirror core/res/res/values/attrs.xml.
            case 0x01010141: return "interpolator";
            case 0x01010198: return "duration";
            case 0x010101be: return "startOffset";
            case 0x010101bf: return "repeatCount";
            case 0x010101c0: return "repeatMode";
            case 0x010102de: return "valueFrom";
            case 0x010102df: return "valueTo";
            case 0x010102e0: return "valueType";
            case 0x010102e1: return "propertyName";
            case 0x010102e2: return "ordering";
            case 0x01010474: return "propertyXName";
            case 0x01010475: return "propertyYName";
            case 0x01010024: return "value";
            case 0x010104d8: return "fraction";
            // <cycleInterpolator>/<anticipateOvershoot>/<pathInterpolator> args
            case 0x010101d3: return "factor";
            case 0x010101d4: return "cycles";
            case 0x0101026a: return "tension";
            case 0x0101026b: return "extraTension";
            case 0x010103fc: return "controlX1";
            case 0x010103fd: return "controlY1";
            case 0x010103fe: return "controlX2";
            case 0x010103ff: return "controlY2";
            case 0x01010406: return "strokeColor";
            case 0x01010407: return "strokeWidth";
            case 0x01010408: return "trimPathStart";
            case 0x01010409: return "trimPathEnd";
            case 0x0101040a: return "trimPathOffset";
            case 0x0101040b: return "strokeLineCap";
            case 0x0101040c: return "strokeLineJoin";
            case 0x0101040d: return "strokeMiterLimit";
            case 0x010104cb: return "strokeAlpha";
            case 0x010104cc: return "fillAlpha";
            case 0x0101051e: return "fillType";
            // androidx.preference.Preference (+ subclasses) styleable.
            // AndroidX declares a LIBRARY-local attr AND an
            // `android:`-namespaced alias for each field (e.g. `key` +
            // `android:key`); TypedArrayUtils.getString/getBoolean/...
            // try the library-local index first, then fall back to the
            // android-namespace index. Preference XML authored the
            // traditional way (every real-world example, incl.
            // F-Droid's res/xml/preferences.xml, uses ONLY
            // `android:key`/`android:defaultValue`/etc, never the bare
            // form) means resolution depends ENTIRELY on this fallback
            // -- without these cases, attrNameAt() returns null for
            // every one of them, setValueAt() reports the attribute
            // absent, and getIndexCount()/hasValue() never see it, so
            // Preference's own constructor never calls
            // onGetDefaultValue and `mDefaultValue` stays null forever.
            // Concretely: MultiSelectListPreference's
            // `android:defaultValue="@array/…"` never resolves; if the
            // key later gets populated with the WRONG type by any
            // other path, dispatchSetInitialValue()'s "already
            // persisted" branch unconditionally calls
            // onSetInitialValue(true, null), and
            // MultiSelectListPreference.setValues(null) NPEs on
            // mValues.addAll(null) -- the F-Droid blank-screen
            // regression (FDroidApp.onCreate aborts before
            // Preferences.instance is set). Ids verified two ways: (1)
            // apktool's vendored platform framework/1.apk decoded
            // locally (`apktool d framework/1.apk` ->
            // res/values/public.xml -- the exact resource table real
            // aapt2/Android ship), (2) cross-checked against
            // aosp-mirror/platform_frameworks_base
            // core/res/res/values/public-final.xml. Both agree.
            case 0x01010002: return "icon";
            case 0x0101000d: return "persistent";
            case 0x0101000e: return "enabled";
            case 0x010100b2: return "entries";
            case 0x010100f2: return "layout";
            case 0x01010136: return "max";
            case 0x010101e1: return "title";
            case 0x010101e6: return "selectable";
            case 0x010101e8: return "key";
            case 0x010101e9: return "summary";
            case 0x010101ea: return "order";
            case 0x010101eb: return "widgetLayout";
            case 0x010101ec: return "dependency";
            case 0x010101ed: return "defaultValue";
            case 0x010101ee: return "shouldDisableView";
            case 0x010101f8: return "entryValues";
            case 0x010102e3: return "fragment";
            case 0x01010539: return "min";
            case 0x0101055c: return "singleLineTitle";
            case 0x01010561: return "iconSpaceReserved";
            // android:font / fontWeight / fontStyle / ttcIndex /
            // fontVariationSettings / fontFamily (v1.0.965). Needed so a
            // BUNDLED (non-downloadable) res/font/*.xml <font-family>
            // resolves its <font> children. androidx.core.content.res.
            // FontResourcesParserCompat#readFont reads each attribute via
            // `array.hasValue(FontFamilyFont_font)` (the library's OWN
            // app-namespaced "app:font" attr, package 0x7f, resolved fine
            // through the app's own ResourceIndex) and falls back to
            // `FontFamilyFont_android_font` etc -- the REAL platform attr,
            // 0x0101xxxx -- only when the app-namespaced one is absent from
            // the XML. Without a name row here, attrNameAt (and
            // ResourceIndex's OWN frameworkAttrName, which is consulted
            // FIRST via entryNameOf and is equally missing these five)
            // returns null for the android:-namespaced variant, so a
            // font-family authored with ONLY android: attrs -- the shape
            // aapt2 emits for a library merged from a compileSdk 26+
            // dependency with no app: fallback, e.g. an ad SDK's own
            // font-family XML -- parses its <font> children with
            // resourceId=0 and filename=null. Every entry in the family
            // then fails Font.Builder(resources, 0), FontFamily.Builder
            // never gets a font added, createFromFontFamilyFilesResourceEntry
            // returns null, and ResourcesCompat.loadFont's caller-facing
            // overload throws `Resources$NotFoundException: Font resource
            // ID #0x... could not be retrieved.` from inside the requesting
            // widget's constructor -- the SAME crash shape the v1.0.964
            // getValue path-format fix eliminated for plain (non-XML)
            // fonts, reintroduced here via a different gap the moment XML
            // font-families became reachable for the first time.
            // Ids verified against AOSP core/res/res/values/public-final.xml
            // (aosp-mirror/platform_frameworks_base, fetched 2026-08-13):
            //   <public type="attr" name="fontFamily" id="0x010103ac" />
            //   <public type="attr" name="font" id="0x01010532" />
            //   <public type="attr" name="fontWeight" id="0x01010533" />
            //   <public type="attr" name="fontStyle" id="0x0101053f" />
            //   <public type="attr" name="ttcIndex" id="0x0101056f" />
            //   <public type="attr" name="fontVariationSettings" id="0x01010570" />
            // `fontFamily` is ALSO carried in ResourceIndex's own
            // frameworkAttrName table (added v1.0.935/936 for a related but
            // separate TextAppearance-typescale-weight bug) and is
            // consulted first via entryNameOf, so this row is a no-op there
            // in the common case; kept here too for consistency and so the
            // same gap closes when no ResourceIndex is attached at all.
            case 0x010103ac: return "fontFamily";
            case 0x01010532: return "font";
            case 0x01010533: return "fontWeight";
            case 0x0101053f: return "fontStyle";
            case 0x0101056f: return "ttcIndex";
            case 0x01010570: return "fontVariationSettings";
            default:         return null;
        }
    }

    public Resources getResources() { return resources; }
    /** AOSP TypedArray.getIndexCount: returns the number of styled
     *  attributes that were actually SET (vs. left at default). AOSP
     *  callers (ConstraintLayout.LayoutParams, every widget that
     *  parses theme attrs) iterate via getIndex(i) to find which
     *  attrs to read. v1.0.107: was hardcoded 0, which made
     *  ConstraintLayout.LayoutParams's constraint-attr loop a no-op
     *  -- the LayoutParams ended up with NO constraints, and the
     *  ConstraintLayout's measure pass fell back to a horizontal-
     *  divide-equally layout (children at width = parent/n). Fix:
     *  count + index attrs that the bound AttributeSet actually
     *  provides. */
    public int getIndexCount() {
        // v1.0.913: theme-tier arrays count theme-supplied values too (the
        // emulator's idx= for a theme probe is the full set the theme
        // declares, not 0). Arrays with NO source keep the legacy 0.
        if (attributeSet == null && !macapkThemeTierLive()) return 0;
        int count = 0;
        for (int i = 0; i < attrs.length; i++) {
            if (macapkIndexed(i)) count++;
        }
        // Diagnostic (env-gated): show what a big styleable parse
        // (ConstraintLayout.LayoutParams-sized) actually resolved.
        if (attrs.length > 40
                && System.getenv("MACAPK_TRACE_TA") != null) {
            StringBuilder sb = new StringBuilder("TA parse attrs=")
                .append(attrs.length).append(" found=").append(count)
                .append(" set=[");
            int shown = 0;
            for (int i = 0; i < attrs.length && shown < 12; i++) {
                String v = setValueAt(i);
                if (v == null) continue;
                sb.append(attrNameAt(i)).append('=').append(v).append(' ');
                shown++;
            }
            sb.append(']');
            try {
                Class<?> log = Class.forName("com.macapk.loader.NativeLog");
                log.getMethod("i", String.class, String.class)
                   .invoke(null, "Mac.apk.TypedArray", sb.toString());
            } catch (Throwable ignored) {}
        }
        return count;
    }
    public int getIndex(int at) {
        if (attributeSet == null && !macapkThemeTierLive()) return -1;
        int idx = 0;
        for (int i = 0; i < attrs.length; i++) {
            if (macapkIndexed(i)) {
                if (idx == at) return i;
                idx++;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------
    // Cluster A2 (widget-fidelity lane, 2026-09-26): @null and @empty are
    // TYPE_NULL, so they are NOT values.
    //
    // aapt2 compiles the two literals to distinct records
    // (tools/aapt2/ResourceUtils.cpp TryParseNullOrEmpty :345-364,
    // android14-release): "@null" -> MakeNull(), a TYPE_REFERENCE with data 0;
    // "@empty" -> MakeEmpty(), TYPE_NULL with data DATA_NULL_EMPTY (1).
    // ApplyStyle (libs/androidfw/AttributeResolution.cpp :321-326) then turns
    // the @null reference back into TYPE_NULL / DATA_NULL_UNDEFINED, "Deal with
    // the special @null value -- it turns back to TYPE_NULL", and puts an
    // index into the indices list only when
    //     value.type != TYPE_NULL || value.data == DATA_NULL_EMPTY     (:340)
    // TypedArray.java (android14-release) answers from that record:
    //     hasValue        :1174-1182  type != TYPE_NULL
    //     hasValueOrEmpty :1195-1204  type != TYPE_NULL || data == DATA_NULL_EMPTY
    //     getResourceId   :935-947    TYPE_NULL -> defValue
    //     getString       :260-282    TYPE_NULL -> null
    //     getColor        :503-514    TYPE_NULL -> defValue
    //     getDrawableForDensity :999 / getColorStateList :592
    //                     getValueAt :1372-1377 is false for TYPE_NULL -> null
    // A `?attr` that the theme resolves to @null / @empty lands on the same
    // record (the ApplyStyle resolution runs before the :321 conversion), so
    // the check follows the theme hop the way getResourceId already does.
    //
    // Ours answered `raw != null`, and the raw STRING "@null" / "@empty" is
    // non-null, so hasValue was true while our own peekValue reported
    // TYPE_NULL for the same slot. MEASURED VICTIMS, all in the Material
    // library's presence branches (material-components-android 1.12.0, and the
    // same branches in the SHIPPED bytecode of Google Calculator):
    //   MaterialButtonHelper.loadFromAttributes :131-134
    //     if (hasValue(MaterialButton_android_background)) setBackgroundOverwritten();
    //   -> `android:background=@empty` in Widget.*.Button read as an app
    //     override, so MaterialButton never built its RippleDrawable (with A1,
    //     Google Calculator's history button: the white 176x96 box);
    //   MaterialButtonHelper :97-99 `cornerRadius=@null` -> corner size -1:
    //     every Material pill painted SQUARE (Anki "Get Started");
    //   MaterialCardViewHelper :152-155 `cardCornerRadius=@null` -> radius 0:
    //     every M3 card SQUARE (F-Droid, 12dp in AOSP).
    // `getDrawable` on a present @empty/@null also handed back the
    // VectorDrawable STUB below; that is the "bg=VectorDrawable" View.java's
    // v898 comment left open.
    //
    // The legacy source-less stub (`attributeSet == null && !themeTierLive`
    // -> true) is untouched: that is AppCompat's windowActionBar guard, a
    // different contract.
    //
    // MACAPK_TYPEDARRAY_NULL_HASVALUE=0 restores the pre-fix answers of every
    // site that consults macapkNullDataOf (hasValue, hasValueOrEmpty,
    // getIndexCount/getIndex, getResourceId, getString, getColor,
    // getColorStateList, the record behind getType/getValue/peekValue, and
    // macapkDeclaredTypeNull, which then answers false): a labelled bisect
    // gate, not a mode we support. Gate: debug/attrident_gate.sh (with the
    // gate at 0 every battery row reproduces its pre-fix answer; `--control`
    // also checks those rows against the pinned pre-fix source).
    // ------------------------------------------------------------------
    static final boolean MACAPK_TYPEDARRAY_NULL_HASVALUE =
        !"0".equals(System.getenv("MACAPK_TYPEDARRAY_NULL_HASVALUE"));
    // The TYPE_NULL data values are android.util.TypedValue.DATA_NULL_UNDEFINED
    // (0) and DATA_NULL_EMPTY (1), TypedValue.java :169 / :173 (android14-release).
    /** Per-index cache of a `?attr` value's theme leaf classification:
     *  0 = not computed, 1 = not a null record, 2 = @null, 3 = @empty. Only a
     *  `?`-valued raw needs the theme walk; a literal is a string compare. */
    private byte[] macapkNullHop;

    /** DATA_NULL_UNDEFINED when the value at `index` compiles to AOSP's @null
     *  record, DATA_NULL_EMPTY for @empty, -1 for any other value (including
     *  an absent one, which the callers test separately) and always -1 with
     *  the gate off. `raw` is macapkRawValueAt(index), passed in so a caller
     *  that already has it does not walk the tiers twice. */
    private int macapkNullDataOf(int index, String raw) {
        if (!MACAPK_TYPEDARRAY_NULL_HASVALUE || raw == null) return -1;
        String v = raw.trim();
        if (v.length() > 1 && v.charAt(0) == '?' && index >= 0 && index < attrs.length) {
            if (macapkNullHop == null) macapkNullHop = new byte[attrs.length];
            byte k = macapkNullHop[index];
            if (k == 0) {
                k = 1;
                try {
                    String leaf = Resources.macapkResolveThemeAttrRaw(v, theme);
                    if (leaf != null) {
                        leaf = leaf.trim();
                        if ("@null".equals(leaf)) k = 2;
                        else if ("@empty".equals(leaf)) k = 3;
                    }
                } catch (Throwable ignored) {}
                macapkNullHop[index] = k;
            }
            return k == 2 ? android.util.TypedValue.DATA_NULL_UNDEFINED
                 : k == 3 ? android.util.TypedValue.DATA_NULL_EMPTY : -1;
        }
        if ("@null".equals(v)) return android.util.TypedValue.DATA_NULL_UNDEFINED;
        if ("@empty".equals(v)) return android.util.TypedValue.DATA_NULL_EMPTY;
        return -1;
    }
    /** Is `index` in AOSP's indices list (ApplyStyle :340): a value, or an
     *  explicit @empty; never absent, never @null. */
    private boolean macapkIndexed(int index) {
        String raw = macapkRawValueAt(index);
        return raw != null
            && macapkNullDataOf(index, raw) != android.util.TypedValue.DATA_NULL_UNDEFINED;
    }
    /** Mac.apk-internal, NOT an AOSP query: true when `index` holds an
     *  explicit @null / @empty (literal, or a `?attr` whose theme leaf is
     *  one), i.e. the attribute WAS declared and its value is AOSP's TYPE_NULL
     *  record. hasValue is false for it, as on a device, so a caller that
     *  needs "was a value declared at all" asks this.
     *
     *  The one caller is View's constructor, for macapkBackgroundExplicitlySet:
     *  the flag that keeps the stand-in Material chrome (View
     *  macapkPaintMaterialChrome's Toolbar / AppBarLayout fill) off a view
     *  whose background was declared. Before A2 an explicit
     *  `android:background="@null"` set that flag by ACCIDENT: hasValue was
     *  true, getDrawable handed back the VectorDrawable stub, and installing
     *  it set the flag. A2 removes the stub (AOSP installs nothing), so
     *  without this the flag stayed false and a declared-transparent Toolbar
     *  got an opaque colorSurface slab. Measured offline on F-Droid's own
     *  androidx Toolbar with android:background=@null: gates off 0 ink, A2
     *  alone 20000 ink (#f9f9ff over 200x100). AOSP paints nothing there.
     *
     *  False with MACAPK_TYPEDARRAY_NULL_HASVALUE=0: hasValue is true then and
     *  the stub path sets the flag exactly as before. */
    public boolean macapkDeclaredTypeNull(int index) {
        if (attributeSet == null && !macapkThemeTierLive()) return false;
        if (index < 0 || index >= attrs.length) return false;
        String raw = macapkRawValueAt(index);
        return raw != null && macapkNullDataOf(index, raw) >= 0;
    }

    /** AOSP TypedArray.hasValue: returns true iff the styled attribute at
     *  `index` was actually set. We split by READ KIND:
     *
     *  - THEME read (no bound AttributeSet): keep the universal-true that
     *    unblocks AppCompat's "you need a Theme.AppCompat theme" check -- it
     *    probes `windowActionBar` et al via a theme-only TypedArray, and we
     *    don't model the full theme hierarchy, so we can't answer accurately;
     *    true lets the check pass and the getters still hand back the right
     *    runtime defaults. (F-Droid's CalculatorActivity + every AppCompat
     *    activity depends on this.)
     *  - LAYOUT read (bound AttributeSet): report whether the attr is ACTUALLY
     *    present in the inflated XML. v1.0.396: a universal true broke
     *    AppDetailsActivity -- appcompat's `Toolbar` ctor does
     *    `if (a.hasValue(Toolbar_menu)) inflateMenu(a.getResourceId(Toolbar_menu, 0))`,
     *    and our menu-less `MaterialToolbar` has no `app:menu`, so the bogus
     *    `true` made it call `inflateMenu(0)` -> `Resources.getXml(0x0)` ->
     *    the MaterialToolbar failed to instantiate -> the activity's
     *    `(MaterialToolbar) findViewById(...)` cast threw ClassCastException
     *    and onCreate aborted -> black screen. Reporting actual presence makes
     *    the ctor skip inflateMenu (AOSP behavior) and the toolbar inflates. */
    public boolean hasValue(int index) {
        // v1.0.913: a theme-tier array answers REAL presence -- AOSP's
        // hasValue is `type != TYPE_NULL` (TypedArray.java:1162,
        // android11-release), and the old universal-true was the fwprobe's
        // 72-case false-positive class (`ref has=0, our has=1`): AndroidX's
        // standard `if (a.hasValue(i)) apply(a.getX(i))` guard took the
        // "specified" branch on attributes the theme never set. Arrays with
        // NO source at all (legacy stubs, MacapkResourceArray) keep the
        // universal true that unblocks AppCompat's theme check.
        if (attributeSet == null && !macapkThemeTierLive()) return true;
        // Cluster A2: real presence, any tier, and @null / @empty are TYPE_NULL
        // (see macapkNullDataOf).
        String raw = macapkRawValueAt(index);
        return raw != null && macapkNullDataOf(index, raw) < 0;
    }
    public boolean hasValueOrEmpty(int index) {
        if (attributeSet == null && !macapkThemeTierLive()) return true;
        // Cluster A2: an explicit @empty IS reported here (AOSP :1195-1204);
        // @null is not.
        return macapkIndexed(index);
    }
    public int length()                       { return data.length; }

    // All getters return defaults. AOSP: returns the supplied default
    // when the attribute isn't found OR has no resolvable value, so
    // this is the documented behavior for our case.
    //
    // EXCEPTION (v1.0.72): AppCompatDelegateImpl checks 5 specific
    // theme boolean attrs (windowActionBar, windowActionBarOverlay,
    // windowIsFloating, windowActionModeOverlay, windowNoTitle) and
    // throws IllegalArgumentException "AppCompat does not support
    // the current theme features" if ALL are false. Each is queried
    // with `def=false`. To satisfy AppCompat without modeling AOSP's
    // full theme XML, we return TRUE when the caller's default is
    // false (i.e. "the theme value was overridden, not at default").
    // Apps that branch on getBoolean(..., false) get true, which
    // most apps treat as "set explicitly" -- they'll still call the
    // value-fetcher (getInt/getString/etc.) and see our null/zero
    // defaults. AppCompat's check passes; Preference's boolean
    // attrs (enabled/selectable/persistent) default to true so they
    // still get true. Generic + framework-level; no per-APK code.
    // v1.0.92: attribute-aware getBoolean. AppCompatDelegateImpl's
    // createSubDecor branches on five window-feature flags
    // (windowActionBar/Overlay, windowNoTitle, windowIsFloating,
    // windowActionModeOverlay). To take the lightweight `abc_screen_
    // simple` inflation path (rather than `abc_screen_toolbar`, which
    // requires DecorContentParent / Material widgets we can't
    // instantiate), `windowNoTitle` must be true. Returning true
    // unconditionally for default-false (the old v1.0.71 hack) also
    // tripped RecyclerView's `fastScrollerEnabled` (default false)
    // into true -- which forced initFastScroller with null drawables
    // and threw. Solution: identify each attribute by NAME and return
    // a curated default per attribute. Unknown attributes get the
    // caller's default.
    // ⚠ 2026-08-02 (lane c4): the justification below -- "we don't model the
    // full theme hierarchy, so we can't answer accurately" -- is NO LONGER
    // TRUE, and this constant is the only reason opensudoku/frozenbubble draw
    // no app bar. A theme-reading version was BUILT AND MEASURED this session
    // and is NOT the whole fix; see docs/appcompat-decor-window-flags_2026-08-02.md
    // before reinstating it. Short version: making getBoolean answer from the
    // theme DOES flip AppCompat onto the `abc_screen_toolbar` decor and the
    // ActionBarContainer/Toolbar inflate correctly at 808x112 (= AOSP's 56dp
    // actionBarSize) -- but the bar then paints NOTHING (no title child, and
    // ActionMenuView measures 0 wide), so the rendered output was 99.97%
    // identical and the change was visually INERT. Reinstate it together with
    // the Toolbar title/menu work, not before, and sweep the pair as one.
    public boolean getBoolean(int index, boolean def) {
        // v1.0.913: read the VALUE first -- this getter never consulted the
        // value source at all (it was pure name-cased compensation), so a
        // style/theme `probeBool=true` answered the caller's default even on
        // paths where every other getter answered. AOSP getBoolean coerces
        // any TYPE_*_INT to data != 0 (XmlUtils.convertValueToBoolean shape).
        // Validated: only a record that resolved to an int family answers;
        // anything else falls through to the existing per-name compensation
        // chain and the caller's default, so an unparseable raw can never
        // turn into a silent `false` (the v1.0.894 lesson).
        android.util.TypedValue rec = macapkRecordAt(index);
        if (rec != null
                && rec.type >= android.util.TypedValue.TYPE_FIRST_INT
                && rec.type <= android.util.TypedValue.TYPE_LAST_INT) {
            return rec.data != 0;
        }
        String name = attrNameAt(index);
        if (name != null) {
            // AppCompat theme flags forced so the simple-decor path is taken.
            // ⚠ See the block above: the theme-reading version WORKS (the decor
            // inflates, the title now renders correctly) but the content is not
            // inset below the bar, because AppCompat only adds the action-bar
            // height when `!mOverlayMode && !stable && mDecorFitsSystemWindows`
            // and the modern path applies insets via onApplyWindowInsets --
            // which needs our WindowInsets dispatch to work. Reinstate this
            // TOGETHER with that, never alone.
            // v1.0.894: ON BY DEFAULT (`MACAPK_WINDOW_FLAGS_FROM_THEME=0` opts
            // out). Reading the theme here is what makes AppCompat pick
            // abc_screen_toolbar over abc_screen_simple, i.e. it is what gives
            // an app the decor action bar its own theme asks for.
            //
            // HISTORY, because the numbers below are the whole reason this sat
            // gated for eight versions. At v1.0.886 it measured +13.22 on
            // opensudoku AND -15.07 / -7.16 / -1.53 on fdroid / wikipedia /
            // deskclock, so it was gated. The v1.0.886 note then blamed
            // "resolveAttrRaw answering off the wrong appliedStyles head, NOT
            // the parent chain" -- and that was WRONG. The parent chain WAS the
            // cause: `Theme.AppCompat.Empty` is declared `parent=""`, we
            // inferred a dotted parent for it anyway, and it inherited the whole
            // of `Theme.AppCompat` and shadowed each app's real theme.
            // v1.0.888 fixed that inference.
            //
            // RE-MEASURED at v1.0.894 with the same five apps, gate on:
            //   opensudoku 70.25 -> 83.47  (+13.22, the full original gain)
            //   fdroid     78.49 -> 78.49  (was -15.07)
            //   wikipedia  88.29 -> 88.29  (was  -7.16)
            //   deskclock  89.22 -> 89.22  (was  -1.53)
            //   antennapod 96.39 -> 96.39
            // All three regressions are gone. Trace with
            // MACAPK_TRACE_WINDOW_FLAGS=1, which names the answering style.
            if (!"0".equals(System.getenv("MACAPK_WINDOW_FLAGS_FROM_THEME"))) {
                switch (name) {
                    case "windowNoTitle":
                    case "windowActionBar":
                    case "windowActionBarOverlay":
                    case "windowIsFloating":
                    case "windowActionModeOverlay": {
                        if (theme != null) {
                            try {
                                // The slot's own identity, like every other
                                // theme-tier read in this file
                                // (MACAPK_TYPEDARRAY_FW_THEME_ID): for a
                                // FRAMEWORK slot (android.R.attr.windowNoTitle,
                                // AppCompatTheme_android_windowIsFloating ...)
                                // the theme is asked for android:X, so an absent
                                // android: record no longer falls through to
                                // the APP attribute of the same name here.
                                // AppCompat's own 0x7f AppCompatTheme_window*
                                // slots are unchanged (the helper returns the
                                // name as-is), and so is the gate-off path.
                                String raw = theme.resolveAttrRaw(macapkThemeTierName(index, name));
                                if (raw != null) {
                                    raw = raw.trim();
                                    if ("true".equalsIgnoreCase(raw))  return true;
                                    if ("false".equalsIgnoreCase(raw)) return false;
                                }
                            } catch (Throwable ignored) {}
                        }
                        if ("windowNoTitle".equals(name)) return true;
                        return def;
                    }
                }
            }
            if ("windowNoTitle".equals(name)) return true;
        }
        return def;
    }

    public int getInt(int index, int def) {
        String v = macapkRawValueAt(index);
        if (v == null) return def;
        // v1.0.932: resolve an `@integer/` reference, mirroring how getFloat
        // already resolves `@dimen/`. There are 941 `@integer/` occurrences in
        // the corpus' animator XML alone -- e.g. deskclock's
        // android:duration="@integer/app_bar_elevation_anim_duration" is really
        // 150 and read as the AOSP default 300 without this.
        if (v.startsWith("@integer/")) {
            String r = macapkLookupRaw("integerForName", v.substring(9));
            if (r == null) return def;
            v = r;
        }
        try { return Integer.parseInt(v); } catch (NumberFormatException ignored) {}
        // v1.0.96: Material widgets pass enum-string values through
        // styled attrs (e.g. app:labelVisibilityMode="labeled" maps to
        // NavigationBarView.LABEL_VISIBILITY_LABELED = 1). AOSP's
        // aapt resolves the enum at compile-time to an int, but our
        // apktool-decoded XML still carries the string form. Without
        // this lookup, F-Droid's BottomNav defaults to AUTO mode and
        // hides 4 of the 5 tab labels (only the selected tab shows
        // its large label; the rest show icon-only, but our icons are
        // blank stubs).
        String name = attrNameAt(index);
        int enumVal = resolveEnumString(name, v);
        if (enumVal != Integer.MIN_VALUE) return enumVal;
        return def;
    }

    /** Map a known (attribute, enum-string) pair to its AOSP int
     *  value. Returns Integer.MIN_VALUE when no mapping is known so
     *  callers can fall back to the supplied default. */
    private static int resolveEnumString(String attrName, String v) {
        if (attrName == null || v == null) return Integer.MIN_VALUE;
        // v1.0.860: ask the APP'S OWN attrs.xml first. aapt2 resolves enum/flag
        // attributes to ints at build time; our apktool-decoded XML still carries
        // the source token, and the app ships the declaration that maps it:
        //     <attr name="displayOptions"><flag name="showTitle" value="0x8"/>...
        // Consulting it turns the hand-written cases below from "the mapping" into
        // a fallback for attrs the app does not declare (framework attrs an app
        // never redeclares). Mechanism, not another table -- see
        // ResourceIndex.loadAttrEnums for the measured victim (a blank action-bar
        // title, because `displayOptions` fell through to the caller's 0).
        // Reached reflectively, matching how the rest of this package touches the
        // index (it is held as an Object because ResourceIndex lives in the loader).
        try {
            Object ri = Resources.macapkGetResourceIndex();
            if (ri != null) {
                Object r = ri.getClass()
                    .getMethod("resolveAttrEnumValue", String.class, String.class)
                    .invoke(ri, attrName, v);
                if (r instanceof Integer i) return i;
            }
        } catch (Throwable ignored) {}
        switch (attrName) {
            // ANIMATOR enums (v1.0.932). aapt2 resolved these at build time; the
            // apktool-decoded XML we read still carries the source token, and an
            // app never redeclares a FRAMEWORK attr, so the app-attrs lookup
            // above cannot answer them. Measured corpus impact if they fall
            // through to the caller's default: 135 `ordering="sequentially"`
            // sets would play SIMULTANEOUSLY, 50 `repeatCount="infinite"` would
            // become single-shot, plus 6 `repeatMode="reverse"` and 6 non-float
            // valueTypes. Values from AOSP attrs.xml / AnimatorInflater
            // (TOGETHER=0, SEQUENTIALLY=1; RESTART=1, REVERSE=2; INFINITE=-1).
            case "valueType":
                if ("floatType".equals(v))  return 0;
                if ("intType".equals(v))    return 1;
                if ("pathType".equals(v))   return 2;
                if ("colorType".equals(v))  return 3;
                break;
            case "ordering":
                if ("together".equals(v))     return 0;
                if ("sequentially".equals(v)) return 1;
                break;
            case "repeatMode":
                if ("restart".equals(v))    return 1;
                if ("reverse".equals(v))    return 2;
                break;
            case "repeatCount":
                if ("infinite".equals(v))   return -1;
                break;
            case "labelVisibilityMode":
                if ("auto".equals(v))       return -1;
                if ("selected".equals(v))   return 0;
                if ("labeled".equals(v))    return 1;
                if ("unlabeled".equals(v))  return 2;
                break;
            case "orientation":
                if ("horizontal".equals(v)) return 0;
                if ("vertical".equals(v))   return 1;
                break;
            // android:textStyle is a FLAG attr (normal=0 bold=1 italic=2);
            // apktool hands us the token string. Read by setTextAppearance.
            case "textStyle": {
                int st = 0; boolean known = false;
                for (String tok : v.split("\\|")) {
                    switch (tok.trim()) {
                        case "normal": known = true; break;
                        case "bold":   st |= 1; known = true; break;
                        case "italic": st |= 2; known = true; break;
                        default: break;
                    }
                }
                if (known) return st;
                break;
            }
            // GRAVITY is a FLAG attr, so aapt2 compiles `android:gravity="top"`
            // to the int 0x30 and a TypedArray int read gets that int. Our
            // TypedArray reads the raw apktool XML string ("top", "bottom|end",
            // "center_vertical"), and without this mapping every such read fell
            // through to the caller's default.
            //
            // Measured on OpenCalculator 53 (2026-07-29): the com.sothree
            // SlidingUpPanelLayout ctor does
            //     TypedArray defAttrs = context.obtainStyledAttributes(
            //             attrs, DEFAULT_ATTRS);           // {android.R.attr.gravity}
            //     int gravity = defAttrs.getInt(0, Gravity.NO_GRAVITY);
            //     setGravity(gravity);
            // and setGravity throws IllegalArgumentException("gravity must be
            // set to either top or bottom") for anything that is not TOP/BOTTOM.
            // The layout says android:gravity="top"; we returned NO_GRAVITY, so
            // the whole panel -- which contains the ENTIRE keypad -- failed to
            // instantiate, got a FrameLayout placeholder, and onCreate then died
            // casting it back. Gravity.parse() is the same AOSP token table the
            // inflater already uses for android:gravity / layout_gravity, so
            // this just makes the TypedArray path agree with it.
            case "gravity":
            case "layout_gravity":
            // CoordinatorLayout reads `app:layout_anchorGravity` with getInteger,
            // and it uses the SAME flag table as android:gravity. Without a case
            // here `Integer.parseInt("end|bottom")` throws and the caller's
            // NO_GRAVITY default wins, so an anchored child (a FAB) loses its
            // anchor placement. (Design lane 4, 2026-08-02.)
            case "layout_anchorGravity":
            case "foregroundGravity": {
                // Gravity.parse yields NO_GRAVITY for a string it does not
                // recognise; report "no mapping" in that case so the caller's
                // own default wins rather than a silent 0.
                int g = android.view.Gravity.parse(v);
                return g != android.view.Gravity.NO_GRAVITY
                    ? g : Integer.MIN_VALUE;
            }
            case "autoSizeTextType":
                // TextView.AUTO_SIZE_TEXT_TYPE_NONE / _UNIFORM. The
                // appcompat autosize helper reads this enum to decide
                // whether to configure auto-sizing at all; without the
                // mapping Calculator's formula never got its min/max
                // text sizes and painted at size 0.
                if ("none".equals(v))    return 0;
                if ("uniform".equals(v)) return 1;
                break;
            case "visibility":
                if ("visible".equals(v))   return 0;
                if ("invisible".equals(v)) return 4;
                if ("gone".equals(v))      return 8;
                break;
            case "showAsAction":
                int total = 0;
                for (String part : v.split("\\|")) {
                    switch (part.trim()) {
                        case "never":            total |= 0; break;
                        case "ifRoom":           total |= 1; break;
                        case "always":           total |= 2; break;
                        case "withText":         total |= 4; break;
                        case "collapseActionView": total |= 8; break;
                    }
                }
                return total;
        }
        return Integer.MIN_VALUE;
    }
    public int getInteger(int index, int def) { return getInt(index, def); }
    private static final boolean TRACE_GUIDE =
        System.getenv("MACAPK_TRACE_GUIDE") != null;
    public float getFloat(int index, float def) {
        String v = macapkRawValueAt(index);
        if (TRACE_GUIDE) {
            String nm = attrNameAt(index);
            if (nm != null && nm.toLowerCase().contains("guide"))
                com.macapk.loader.NativeLog.i("TA.guide",
                    "getFloat idx=" + index + " name=" + nm + " raw=" + v + " def=" + def);
        }
        if (v == null) return def;
        // `@fraction/X` / `@dimen/X` references reach float-typed attrs
        // (ConstraintLayout parses layout_constraintGuide_percent via
        // getFloat). Resolve through the raw tables, then parse with
        // percent support ("15%" -> 0.15) -- a percent FRACTION read
        // as a float is the fraction itself per AOSP TypedValue
        // semantics (Guideline percents are 0..1).
        if (v.startsWith("@fraction/")) {
            String r = macapkLookupRaw("getFractionRaw", v.substring(10));
            if (r == null) return def;
            v = r;
        } else if (v.startsWith("@dimen/")) {
            String r = macapkLookupRaw("getDimenRaw", v.substring(7));
            if (r == null) return def;
            v = r;
        } else if (v.startsWith("@") || v.startsWith("?")) {
            return def;
        }
        try {
            if (v.endsWith("%p")) {
                return Float.parseFloat(v.substring(0, v.length() - 2)) / 100f;
            }
            if (v.endsWith("%")) {
                return Float.parseFloat(v.substring(0, v.length() - 1)) / 100f;
            }
            return Float.parseFloat(v);
        } catch (NumberFormatException e) { return def; }
    }
    public int getColor(int index, int def) {
        String v = macapkRawValueAt(index);
        if (v == null) return def;
        if (macapkNullDataOf(index, v) >= 0) return def;   // A2: TYPE_NULL (AOSP :513)
        // v1.0.499: resolve color RESOURCE references, not just `#RRGGBB`
        // literals. The old code returned `def` for any value not starting with
        // '#', so a styled color attr like `app:faceColor="@color/juicyMacaw"`
        // (or `?attr/colorPrimary`) resolved to 0 -- Duolingo's GET STARTED
        // button read faceColor=0 and rendered with NO green fill. Delegate to
        // the shared resolver (handles #RRGGBB / @color/X / ?attr/X). Generic
        // for every app's resource-referenced color attribute.
        //
        // docs/attr-theme-threading_2026-07-31.md: pass `theme` (may be null)
        // so a `?attr/X` value resolves against the Theme THIS TypedArray was
        // obtained through -- e.g. a View-level android:theme overlay -- rather
        // than unconditionally the static application theme. This is the
        // fleet-wide caller: every app that reads a themed color attribute
        // through obtainStyledAttributes goes through here.
        if (resources != null) {
            try { return resources.macapkResolveColorString(v, def, theme); } catch (Throwable ignored) {}
        }
        v = v.trim();
        if (v.startsWith("#")) {
            // macapkParseResourceColor handles the #RGB/#ARGB shorthand apktool can
            // re-emit, which the public Color.parseColor rejects as AOSP does.
            try { return android.graphics.Color.macapkParseResourceColor(v); }
            catch (Throwable e) { return def; }
        }
        return def;
    }
    public int getDimensionPixelSize(int index, int def)  {
        String v = macapkRawValueAt(index);
        if (TRACE_GUIDE) {
            String nm = attrNameAt(index);
            if (nm != null && nm.toLowerCase().contains("guide")) {
                Float px2 = (v == null) ? null : macapkParseDimenToPx(v);
                com.macapk.loader.NativeLog.i("TA.guide",
                    "getDimPxSize idx=" + index + " name=" + nm + " raw=" + v
                    + " -> px=" + px2 + " def=" + def);
            }
        }
        if (v == null) return def;
        Float px = macapkParseDimenToPx(v);
        if (px == null) {
            android.util.TypedValue rec = macapkAttrDimenRecord(index, v);
            if (rec != null) {
                return android.util.TypedValue.complexToDimensionPixelSize(rec.data, macapkMetrics());
            }
        }
        return px != null ? (int) (float) px : def;
    }

    /** MACAPK_TA_ATTR_DIMEN (default ON). A style item that is a THEME
     *  REFERENCE to a dimension ("?android:actionBarSize", the minHeight of
     *  Widget.Toolbar, styles.xml:1284) reached the dimension getters as the raw
     *  "?..." string, which macapkParseDimenToPx cannot parse, so the caller's
     *  default won -- while getValue() on the SAME slot already answered the
     *  resolved TYPE_DIMENSION record. AOSP resolves the reference before any
     *  getter sees it (AttributeResolution.cpp ApplyStyle -> ResolveAttributeReference),
     *  so getDimensionPixelSize answers complexToDimensionPixelSize of it
     *  (TypedArray.java getDimensionPixelSize, android14). Only the "?" case is
     *  routed through the record, so every raw that parsed before parses the same.
     *  =0 restores the raw-only answer.
     *  BLAST RADIUS IS FLEET-WIDE, not just the platform Toolbar that found it:
     *  every getDimension / getDimensionPixelSize / getDimensionPixelOffset /
     *  getLayoutDimension read of a style item written as a ?attr dimension
     *  moves, including View's constructor read of minHeight / minWidth /
     *  padding. So androidx Widget.AppCompat.Toolbar (minHeight=?attr/
     *  actionBarSize) and the Material widget styles in most of the corpus
     *  change size (F-Droid, Anki, NewPipe, Aegis, Gallery, ...), while only
     *  Mastodon uses the platform Toolbar. Measure the Canvas pair set
     *  MACAPK_TA_ATTR_DIMEN=0 vs 1 with debug/recap_ab.sh BEFORE landing and
     *  record the per-app delta. Unit evidence: debug/w5fragments_gate.sh
     *  (android.widget.Toolbar minHeight 0 -> 112 px at 2x). */
    static final boolean MACAPK_TA_ATTR_DIMEN =
        !"0".equals(System.getenv("MACAPK_TA_ATTR_DIMEN"));

    private android.util.TypedValue macapkAttrDimenRecord(int index, String raw) {
        if (!MACAPK_TA_ATTR_DIMEN || raw == null || !raw.startsWith("?")) return null;
        android.util.TypedValue rec = macapkRecordAt(index);
        return rec != null && rec.type == android.util.TypedValue.TYPE_DIMENSION ? rec : null;
    }

    private android.util.DisplayMetrics macapkMetrics() {
        android.util.DisplayMetrics dm = resources != null ? resources.getDisplayMetrics() : null;
        return dm != null ? dm : Resources.getSystem().getDisplayMetrics();
    }
    /** Parse a dimension VALUE string to pixels. Handles `@dimen/X`
     *  references (resolved through the ResourceIndex's raw dimen
     *  table -- previously these parsed as the literal "@..." and
     *  every @dimen margin silently became the caller default) and
     *  unit literals, converted with this array's Resources metrics
     *  (AOSP TypedValue.applyDimension). Returns null when unparseable. */
    private Float macapkParseDimenToPx(String v) {
        if (v == null || v.isEmpty()) return null;
        // lane layout, 2026-08-01: chase dimen->dimen REFERENCE CHAINS, not
        // just one hop. Material3 tokens are routinely aliases-of-aliases
        // (m3_bottom_nav_min_height -> @dimen/m3_comp_navigation_bar_
        // container_height -> 80dp); the old single resolution left v as
        // the second "@dimen/..." string, Float.parseFloat threw, and the
        // caller default (usually 0) won silently. Depth-capped against
        // accidental cycles.
        int hops = 0;
        while (v.startsWith("@dimen/") && hops++ < 8) {
            String resolved = macapkLookupRaw("getDimenRaw", v.substring(7));
            if (resolved == null) return null;
            v = resolved.trim();
        }
        if (v.startsWith("@") || v.startsWith("?")) {
            return null;  // other refs unsupported here
        }
        // The metrics of the Resources this array reads through, as AOSP's
        // mMetrics (see macapkParseDimenLiteral below for the SmartTube
        // case that a fixed device density got wrong).
        return macapkParseDimenLiteral(v, macapkMetrics());
    }
    /** A literal dimension ("16dp", "14sp", "9pt", "12px", "3") to pixels the
     *  way AOSP TypedValue.applyDimension does, against `dm` -- the metrics of
     *  the Resources the value is read through. AOSP converts every dimension
     *  (Resources.getDimension, TypedArray.getDimension*, drawable XML) with
     *  that object's mMetrics, so an app that rewrites its own Resources'
     *  DisplayMetrics (SmartTube's MotherActivity.initDpi sets density to scale
     *  its UI to a 1080p layout) sees ONE density everywhere. These static
     *  parsers used the fixed device density instead, while the inflater and
     *  Resources.getDimension followed the app's metrics: leanback's headers
     *  sidebar came out 540 px wide (270dp at the app's 2.0) but was pushed off
     *  screen by only 265 px (265dp at the device's 1.0), so a blank 275 px
     *  panel sat over the rows. Null for a reference or an unparseable value;
     *  `dm` null falls back to the device density. */
    static Float macapkParseDimenLiteral(String v, android.util.DisplayMetrics dm) {
        if (v == null) return null;
        v = v.trim();
        if (v.isEmpty() || v.startsWith("@") || v.startsWith("?")) return null;
        int unitEnd = v.length();
        for (int i = 0; i < v.length(); i++) {
            if (Character.isLetter(v.charAt(i))) { unitEnd = i; break; }
        }
        try {
            float n = Float.parseFloat(v.substring(0, unitEnd));
            String unit = v.substring(unitEnd);
            float density = (dm != null && dm.density > 0f) ? dm.density
                    : android.util.DisplayMetrics.macapkDeviceDensity();
            float scaled = (dm != null && dm.scaledDensity > 0f) ? dm.scaledDensity : density;
            float xdpi = (dm != null && dm.xdpi > 0f) ? dm.xdpi : density * 160f;
            switch (unit) {
                case "dp": case "dip": return n * density;
                case "sp":             return n * scaled;
                case "pt":             return n * xdpi * (1.0f / 72f);
                case "in":             return n * xdpi;
                case "mm":             return n * xdpi * (1.0f / 25.4f);
                default:               return n;
            }
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String macapkLookupRaw(String method, String name) {
        try {
            Object ri = Resources.macapkGetResourceIndex();
            if (ri == null) return null;
            java.lang.reflect.Method m =
                ri.getClass().getMethod(method, String.class);
            Object v = m.invoke(ri, name);
            return v == null ? null : v.toString();
        } catch (Throwable t) { return null; }
    }
    public int getDimensionPixelOffset(int index, int def){ return getDimensionPixelSize(index, def); }
    public float getDimension(int index, float def)       {
        String v = macapkRawValueAt(index);
        if (v == null) return def;
        Float px = macapkParseDimenToPx(v);
        if (px == null) {
            android.util.TypedValue rec = macapkAttrDimenRecord(index, v);
            if (rec != null) return android.util.TypedValue.complexToDimension(rec.data, macapkMetrics());
        }
        return px != null ? px : def;
    }
    /** AOSP TypedArray.getLayoutDimension(int, int) (android14-release): an
     *  INT-typed slot answers its data, a TYPE_DIMENSION slot answers
     *  complexToDimensionPixelSize, anything else the default. The int case is
     *  what makes MATCH_PARENT / WRAP_CONTENT work: aapt compiles the enum names
     *  of a `dimension|enum` attr (layout_width, leanback's rowHeight,
     *  columnWidth, ...) to TYPE_INT_DEC -1 / -2.
     *
     *  ⚠ This forwarded to getDimensionPixelSize, and our AttributeSet hands
     *  those enum values back as their NAMES ("wrap_content"), which no
     *  dimension parse accepts, so every such read answered the default.
     *  Measured on SmartTube: lb_list_row.xml sets `lb:rowHeight="wrap_content"`
     *  on androidx.leanback's HorizontalGridView, setRowHeight got the default
     *  0 instead of -2, GridLayoutManager took the fixed-row path with a 0 px
     *  row, and every browse row measured 0 tall with its cards bound inside.
     *  (MACAPK_TRACE_ATTR=rowHeight shows the raw: setValue=wrap_content.) */
    public int getLayoutDimension(int index, int def) {
        Integer v = macapkLayoutDimension(index);
        return v != null ? v : def;
    }
    /** AOSP getLayoutDimension(int, String): the same answer, and an
     *  UnsupportedOperationException when the slot is neither an int nor a
     *  dimension ("You must supply a layout_width attribute."). It returned a
     *  constant 0, which made every app LayoutParams.setBaseAttributes override
     *  read width = height = 0. */
    public int getLayoutDimension(int index, String name) {
        Integer v = macapkLayoutDimension(index);
        if (v != null) return v;
        throw new UnsupportedOperationException(getPositionDescription()
                + ": You must supply a " + name + " attribute.");
    }
    private Integer macapkLayoutDimension(int index) {
        String v = macapkRawValueAt(index);
        if (v == null) return null;
        Integer e = macapkLayoutEnumOrInt(v);
        if (e != null) return e;
        if (macapkParseDimenToPx(v.trim()) != null) return getDimensionPixelSize(index, 0);
        // A reference (?attr / @ref) whose resolved record is an int or a
        // dimension: AOSP resolves it before the getter looks at the type.
        android.util.TypedValue rec = macapkRecordAt(index);
        if (rec != null) {
            if (rec.type >= android.util.TypedValue.TYPE_FIRST_INT
                    && rec.type <= android.util.TypedValue.TYPE_LAST_INT) return rec.data;
            if (rec.type == android.util.TypedValue.TYPE_DIMENSION) {
                return android.util.TypedValue.complexToDimensionPixelSize(rec.data, macapkMetrics());
            }
        }
        return null;
    }
    /** The INT-typed spellings a layout dimension can arrive in from our
     *  AttributeSet: the attrs.xml enum names aapt compiles to -1 / -2, and an
     *  integer literal (decimal, or 0x hex as TYPE_INT_HEX). Null otherwise. */
    static Integer macapkLayoutEnumOrInt(String raw) {
        if (raw == null) return null;
        String t = raw.trim();
        switch (t) {
            case "match_parent": case "fill_parent": return -1;
            case "wrap_content": return -2;
            default: break;
        }
        try {
            if (t.startsWith("0x") || t.startsWith("0X")) return (int) Long.parseLong(t.substring(2), 16);
            return Integer.parseInt(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }
    /** AOSP getFraction: value "15%" multiplies `base`; "15%p"
     *  multiplies `pbase`; resolves `@fraction/X` refs first.
     *  ConstraintLayout Guidelines read their percent through this
     *  (base=1) -- the stub default collapsed every percent guideline
     *  to the caller default and zeroed Calculator's display/keypad
     *  split. */
    public float getFraction(int index, int base, int pbase, float def) {
        String v = macapkRawValueAt(index);
        if (v == null) return def;
        if (v.startsWith("@fraction/")) {
            String resolved = macapkLookupRaw("getFractionRaw", v.substring(10));
            if (resolved == null) return def;
            v = resolved;
        } else if (v.startsWith("@") || v.startsWith("?")) {
            return def;
        }
        try {
            if (v.endsWith("%p")) {
                return Float.parseFloat(v.substring(0, v.length() - 2))
                       / 100f * pbase;
            }
            if (v.endsWith("%")) {
                return Float.parseFloat(v.substring(0, v.length() - 1))
                       / 100f * base;
            }
            return Float.parseFloat(v) * base;
        } catch (NumberFormatException e) { return def; }
    }
    /** v1.0.93: getResourceId returns the resolved resource ID of the
     *  XML attribute, or `def` if the attribute isn't set. AOSP source:
     *  if `attrs.getAttributeResourceValue(index, def)` would return a
     *  real ID, this returns that ID. For `@menu/main_activity_screens`,
     *  parseResourceId-via-Resources.getIdentifier resolves to the
     *  apktool-generated public.xml ID. BottomNavigationView reads
     *  `app:menu` via this method during ctor. */
    public int getResourceId(int index, int def) {
        String v = macapkRawValueAt(index);
        if (v == null) return def;
        // A2: TYPE_NULL -> defValue (AOSP :935-947). Load-bearing for @empty,
        // which otherwise reaches getIdentifier("empty", "id") below and
        // answers any app's own @id/empty.
        if (macapkNullDataOf(index, v) >= 0) return def;
        // v1.0.834: honour `?attr/X` theme indirection. AOSP resolves it inside
        // AssetManager before obtainStyledAttributes returns, so a widget
        // reading `android:background="?attr/selectableItemBackground"` sees a
        // real resource id. Our TypedArray reads the raw AttributeSet string,
        // so it has to chase the theme itself; without this every `?attr/`-
        // valued resource attribute silently fell through to `def` (it starts
        // with '?', not '@', and is not "parent" or a number).
        if (v.length() > 1 && v.charAt(0) == '?') {
            String raw = android.content.res.Resources.macapkResolveThemeAttrRaw(v, theme);
            if (raw == null) return def;
            v = raw;
        }
        if (!v.startsWith("@")) {
            // v1.0.107: ConstraintLayout uses the magic value
            // `"parent"` (no @ prefix) on constraint-target attrs
            // (`app:layout_constraintLeft_toLeftOf="parent"`) to mean
            // "constrain to the parent view." AOSP encodes this as
            // PARENT_ID = 0 in ConstraintLayout.LayoutParams (and
            // ConstraintLayout's measure path checks for 0 to flip
            // into parent-side constraint mode). Return 0 here so the
            // constraint engine activates.
            if ("parent".equals(v)) return 0;
            try { return Integer.parseInt(v); } catch (NumberFormatException ignored) { return def; }
        }
        // Strip leading '@' and optional '+id/' prefix; identifier of
        // form `<type>/<name>` looks up in ResourceIndex.
        String body = v.substring(1);
        String type = "id";
        // The PACKAGE has to survive to getIdentifier (v1.0.927). This branch
        // used to strip `android:` and then look the name up with pkg=null, so
        // every `@android:<type>/<name>` value read through getResourceId
        // resolved against the APP's table, missed, and silently returned the
        // caller's default. That is how Material's ThemeEnforcement read -1 for
        // `@android:style/TextAppearance.Material` and threw out of
        // BottomNavigationView's constructor. Latent and general: it was never
        // specific to that one attribute.
        String pkg = null;
        if (body.startsWith("+id/")) {
            body = body.substring(4);
        } else if (body.startsWith("android:")) {
            body = body.substring(8);
            pkg = "android";
            int slash = body.indexOf('/');
            if (slash > 0) { type = body.substring(0, slash); body = body.substring(slash + 1); }
        } else {
            int slash = body.indexOf('/');
            if (slash > 0) { type = body.substring(0, slash); body = body.substring(slash + 1); }
        }
        try {
            if (resources != null) {
                int id = resources.getIdentifier(body, type, pkg);
                if (id != 0) return id;
            }
        } catch (Throwable ignored) {}
        return def;
    }
    public int getSourceResourceId(int index, int def)    { return getResourceId(index, def); }
    public String getString(int index)                    {
        String v = macapkBoundValueAt(index);
        if (v == null) return macapkThemeString(index);
        // A2: TYPE_NULL -> null (AOSP :268-269), not the literal "@null".
        if (macapkNullDataOf(index, v) >= 0) return null;
        // Resolve @string/foo references.
        if (v.startsWith("@string/") || v.startsWith("@android:string/")) {
            int id = getResourceId(index, 0);
            if (id != 0 && resources != null) {
                try { return resources.getString(id); } catch (Throwable ignored) {}
            }
            return null;
        }
        return v;
    }

    /** Answer a THEME-ONLY string read from the theme, the way AOSP does.
     *
     *  `setValueAt` returns null whenever there is no bound AttributeSet, which
     *  is the shape of `Theme/Context.obtainStyledAttributes(int[] attrs)` --
     *  AOSP's `mThemeImpl.obtainStyledAttributes(this, null, attrs, 0, 0)`. That
     *  is NOT a degenerate call on a device: the theme supplies the values, and
     *  a caller that gets its own default back is being told the theme is silent
     *  when it is not. AppCompat's `AppCompatDelegateImpl.createView` probes
     *  `viewInflaterClass` through exactly this shape, and the string it gets is
     *  what selects the Material view inflater that turns `<Button>` into a
     *  `MaterialButton`, `<RadioButton>` into one that draws a circle, and so on.
     *
     *  WHY THIS IS PER-GETTER AND NOT IN `setValueAt`. Answering from the theme
     *  inside the shared helper was tried at v1.0.894 and reverted: `setValueAt`
     *  feeds all 33 getters, so every value the individual parsers could not
     *  coerce turned into 0/transparent instead of a sane default, and
     *  opencalculator's keypad went from 184,033 px of `#b4d2e4` to ZERO (93.54%
     *  -> 84.39%). See docs/refuted-approaches.md. A getter may consult the theme
     *  only where it can VALIDATE what comes back, which is what the reference
     *  test below is for.
     *
     *  RESOLVE THROUGH THE THREADED THEME, NEVER THE STATIC APPLICATION THEME.
     *  The manifest theme is a launch theme for a large minority of real apps
     *  (opensudoku declares `@android:style/Theme.Translucent.NoTitleBar` and
     *  applies its real theme from code; antennapod/newpipe/wikipedia/fdroid all
     *  declare splash or no-display themes) so the static anchor cannot see the
     *  Material base style at all. `theme` follows `setTheme()`, which can.
     *  Measured: docs/theme-anchor-and-viewinflater-census_2026-08-08.md.
     *
     *  Returns null (the caller's default) when there is no theme, no resolvable
     *  name, or a value we cannot validate. */
    private String macapkThemeString(int index) {
        if (theme == null) return null;
        String name = attrNameAt(index);
        if (name == null) return null;
        // ⚠ ON BY DEFAULT since v1.0.920 (`MACAPK_THEME_STRING_FALLBACK=0` opts
        // out -- a labelled bisect gate, the MACAPK_WINDOW_FLAGS_FROM_THEME
        // lifecycle). Gated default-OFF from v1.0.897 to v1.0.919.
        //
        // MECHANISM, VERIFIED END TO END (do not re-derive it):
        //   1. opensudoku's theme really declares
        //        <item name="viewInflaterClass">com.google.android.material.theme
        //         .MaterialComponentsViewInflater</item>
        //      on Base.V14.Theme.MaterialComponents(.Light), and the app ships both
        //      that class and MaterialButton.
        //   2. R8 KEPT MaterialComponentsViewInflater's fully-qualified name while
        //      renaming AppCompatViewInflater. A class only keeps its name when it
        //      is reached BY NAME, so the resource string really is the selection
        //      mechanism.
        //   3. A runtime probe over a full opensudoku launch logged exactly ONE
        //      theme-only getString, on exactly this attribute, with theme != null
        //      and the correct class name resolving. The cascade CAN answer it; the
        //      manifest-pinned static anchor cannot (opensudoku's manifest theme is
        //      @android:style/Theme.Translucent.NoTitleBar and 4 app classes call
        //      setTheme). See docs/theme-anchor-and-viewinflater-census_2026-08-08.md.
        //   4. With it on, AppCl loads MaterialComponentsViewInflater then
        //      MaterialButton, and opensudoku's five <Button> tags become real
        //      MaterialButtons at the correct 672x96 geometry.
        //
        // WHY IT SAT GATED FOR 23 VERSIONS, AND WHAT UNBLOCKED IT. Delivering the
        // string is correct and was not sufficient: measured at v1.0.909/910,
        // flag-on took opensudoku 83.47 -> 92.18 (pills #4ebdb3 0 -> 151,476 vs
        // the emulator's 149,695) but opencalculator 93.60 -> 84.39, keypad
        // #b4d2e4 183,460 px -> ZERO. Root cause of that last blocker, verified
        // in opencalculator's own R8 bytecode (v1.0.920): its 32 <Button> tags
        // become MaterialButton under the flip, and MaterialButton OVERRIDES
        // setBackgroundTintList -> setSupportBackgroundTintList -> its helper,
        // never reaching View's tint fields -- so the inflater's virtual call
        // delivered the 10 per-key android:backgroundTint="?button_*_color"
        // tints into a black hole and the keys painted their untinted
        // ?button_color solid. AppCompatButton does not override the setter,
        // which is why the identical layout painted correctly without the flip.
        // AOSP never virtual-dispatches an inflation-time tint at all (View's
        // ctor writes mBackgroundTint.mTintList directly, View.java:5858-5866,
        // android11-release), so the fix is the faithful mechanism: the
        // inflater now writes the fields via macapkSetInflatedBackgroundTint
        // (LayoutInflater.applyAttribute "backgroundTint"), and the chrome
        // pill honours the per-view tint (View.macapkPaintMaterialChrome).
        //
        // FALSIFIERS for the flip, in pixels (re-measure BEFORE trusting it;
        // v1.0.920 landed this compile-clean but UNMEASURED, alpha policy):
        //   opencalculator  keypad #b4d2e4 ~183,000 px (0 = the swallow is back)
        //   opensudoku      pills  #4ebdb3 ~149,000-151,500 px
        //   anki            intro  #2196f3 ~57,700 px (its MaterialButtons are
        //                   explicit tags, exercised on BOTH flag states)
        //   antennapod / bobball / deskclock / fdroid / unitconverter /
        //   wikipedia: the other 6 viewInflaterClass fleet apps -- must hold
        //   their v1.0.919 recap scores.
        // Parity: OSAGET.base.str.probeString + .probeStringRef flip to EXACT
        // (golden `base-string` / `plain`), 1718 -> 1720 of 1732.
        if ("0".equals(System.getenv("MACAPK_THEME_STRING_FALLBACK"))) return null;
        String raw;
        // The slot's own identity (MACAPK_TYPEDARRAY_FW_THEME_ID): android:X for a
        // framework slot.
        try { raw = theme.resolveAttrRaw(macapkThemeTierName(index, name)); }
        catch (Throwable t) { return null; }
        if (raw == null) return null;
        raw = raw.trim();
        if (raw.isEmpty()) return null;
        // A `@string/` value is a real string once dereferenced, so follow it by
        // NAME. `getResourceId` cannot be used here: it goes through setValueAt,
        // which is null for the whole theme-only path by definition.
        if (raw.startsWith("@string/") || raw.startsWith("@android:string/")) {
            if (resources == null) return null;
            String sname = raw.substring(raw.indexOf('/') + 1);
            try {
                int id = resources.getIdentifier(sname, "string", null);
                if (id != 0) return resources.getString(id);
            } catch (Throwable ignored) {}
            return null;
        }
        // Any other reference is a value we would be handing back UNRESOLVED, and
        // a caller that receives the literal "?attr/foo" where it expected a class
        // name or a locale is worse off than one that receives its own default.
        // This is the validation the v1.0.894 blanket attempt lacked.
        if (raw.startsWith("@") || raw.startsWith("?")) return null;
        if (System.getenv("MACAPK_TRACE_THEMEPROBE") != null) {
            android.util.Log.i("Mac.apk.ThemeProbe",
                "themeOnly getString attr=" + name + " -> " + raw);
        }
        return raw;
    }
    public CharSequence getText(int index)                { return getString(index); }
    /** AOSP TypedArray.getTextArray (frameworks/base core/java/android/content/
     *  res/TypedArray.java, main :1079-1089):
     *      if (getValueAt(index * STYLE_NUM_ENTRIES, value)) {
     *          return mResources.getTextArray(value.resourceId);
     *      }
     *      return null;
     *  i.e. NULL when the attribute is not defined, the array when it references
     *  one, and a NotFoundException ("will throw an exception if the attribute is
     *  defined but is not a text array resource") otherwise.
     *
     *  We used to answer an EMPTY array for both of the non-array cases, and an
     *  empty array is not "absent": AnkiDroid's PermissionsItem reads
     *  `getTextArray(permissions)` and only falls back to the single `permission`
     *  string when that is null, so it built an EMPTY permission list, whose
     *  all-granted check is vacuously TRUE -- its "All files access" switch
     *  showed ON, Continue was enabled, the request was never made, and the
     *  collection had no storage (owner, 2026-09-29: "Needs permissions fixed").
     *  MACAPK_TYPEDARRAY_TEXTARRAY_AOSP=0 restores the empty-array answer.
     *
     *  "Defined" is exactly hasValue(index) (AOSP: getValueAt false <=> TYPE_NULL
     *  <=> !hasValue), which covers unset, @null, @empty and ?attr->@null. The one
     *  arm where our hasValue is a universal true (an array with no AttributeSet
     *  and no live theme tier, see hasValue) has no raw value to read; answering
     *  null there would break the AOSP invariant "hasValue implies non-null" that
     *  androidx Preference.onGetDefaultValue relies on (it iterates the result
     *  without a null check), so that arm keeps the empty array. `@array/x` is
     *  resolved through Resources.getTextArray (the ResourceIndex). */
    public CharSequence[] getTextArray(int index) {
        String v = macapkRawValueAt(index);
        if (!TEXTARRAY_AOSP) {
            if (v != null && (v.startsWith("@array/") || v.startsWith("@android:array/"))) {
                int id = getResourceId(index, 0);
                if (id != 0 && resources != null) {
                    try {
                        CharSequence[] arr = resources.getTextArray(id);
                        if (arr != null) return arr;
                    } catch (Throwable ignored) {}
                }
            }
            return new CharSequence[0];
        }
        // AOSP getValueAt()==false: unset, @null, @empty, ?attr->@null (TYPE_NULL).
        if (!hasValue(index)) return null;
        // hasValue's legacy universal-true arm (no source at all): nothing to read,
        // and hasValue() promised a value, so never null here.
        if (v == null) return new CharSequence[0];
        if (v.startsWith("@") || v.startsWith("?")) {
            int id = getResourceId(index, 0);
            if (id != 0 && resources != null) {
                CharSequence[] arr = null;
                try { arr = resources.getTextArray(id); } catch (Throwable ignored) {}
                if (arr != null) return arr;
            }
            // A reference we could not read is OUR resource gap, not a value
            // that is "not a text array": keep the pre-change empty answer
            // rather than turn a lookup miss into a crash AOSP would not have.
            return new CharSequence[0];
        }
        // A literal (the AOSP value's resourceId is 0): Resources.getTextArray(0)
        // throws NotFoundException on a device.
        throw new Resources.NotFoundException("Text array resource ID #0x0 (attribute value \""
                + v + "\")");
    }

    /** MACAPK_TYPEDARRAY_TEXTARRAY_AOSP (labelled bisect gate, default ON). */
    private static final boolean TEXTARRAY_AOSP =
            !"0".equals(System.getenv("MACAPK_TYPEDARRAY_TEXTARRAY_AOSP"));
    public String getNonResourceString(int index)         { return null; }
    public android.graphics.drawable.Drawable getDrawable(int index) {
        // v1.0.97: when the attribute carries a `@drawable/foo`
        // reference, route through Resources.getDrawable so we
        // return a VectorDrawable stub (satisfies androidx's
        // checkVectorDrawableSetup-style type tests). Without
        // this, MaterialResources path-callers see null and the
        // BottomNav.getOrCreateBadge chain NPEs.
        int id = getResourceId(index, 0);
        if (id != 0 && resources != null) {
            // docs/attr-theme-threading_2026-07-31.md: thread `theme` so any
            // ?attr/-valued colour inside the referenced drawable XML resolves
            // against the Theme this TypedArray was obtained through.
            try { return resources.getDrawable(id, theme); } catch (Throwable ignored) {}
        }
        // v1.0.499: AOSP getDrawable returns NULL when the attribute is unset.
        // Returning a non-null empty VectorDrawable stub for UNSET attrs broke
        // callers that branch on getDrawable()==null: Duolingo's JuicyButton
        // reads getFaceDrawable()/getLipDrawable()==null to mean "use the
        // faceColor/lipColor" -- our stub made cj.r.e() treat it as a real
        // drawable and build a BitmapShader of an EMPTY (transparent) vector,
        // so the GET STARTED button rendered with NO green fill (white-on-white).
        // Keep the empty stub ONLY when the attr IS present but didn't resolve
        // to a drawable -- that is the case androidx's checkVectorDrawableSetup
        // and Material's badge readers hit (they read a set-but-stub attr and
        // NPE on null). hasValue() stays universal for theme reads
        // (attributeSet==null), so those keep the stub too.
        if (id == 0 && !hasValue(index)) return null;
        return new android.graphics.drawable.VectorDrawable();
    }
    /** v1.0.97: never return null. Material widgets that read a
     *  themed ColorStateList chain immediately into getDefaultColor()
     *  -- a null return NPEs the entire <init> path. The most-impactful
     *  instance was BadgeState.readColorFromAttributes during
     *  BottomNav.getOrCreateBadge, which aborted MainActivity.onCreate
     *  before RecyclerView.setAdapter ever ran. We return a default
     *  black ColorStateList; Material picks its own theme colors
     *  layered on top, so a black fallback is invisible in practice. */
    public android.content.res.ColorStateList getColorStateList(int index) {
        // Prefer a resolved color resource (@color/X) so a <selector>/<color>
        // referenced via the attribute returns its real default; fall back to
        // a literal/themed color, then black so Material's getDefaultColor()
        // chain never NPEs on null.
        // Cluster A2: an explicit @null or @empty (or a ?attr the theme sets to
        // one) is TYPE_NULL, and getValueAt is false for it, so AOSP returns
        // null (:592-606). This widens the v1.0.867 explicit-@null rule below
        // to the other TYPE_NULL spellings; the ABSENT case keeps the black
        // fallback for the reason that comment measured.
        {
            String raw0 = macapkRawValueAt(index);
            if (raw0 != null && macapkNullDataOf(index, raw0) >= 0) return null;
        }
        int id = getResourceId(index, 0);
        if (id != 0 && resources != null) {
            try {
                // docs/attr-theme-threading_2026-07-31.md: thread `theme`
                // through for consistency (Resources.getColorStateList(id,
                // Theme) does not yet consult it -- no <selector> color XML
                // parses ?attr/ through this path today -- so this is a no-op
                // now and ready for when that gap closes).
                android.content.res.ColorStateList csl = resources.getColorStateList(id, theme);
                if (csl != null) return csl;
            } catch (Throwable ignored) {}
        }
        int color = getColor(index, Integer.MIN_VALUE);
        if (color != Integer.MIN_VALUE) {
            return android.content.res.ColorStateList.valueOf(color);
        }
        // AOSP contract: getColorStateList returns NULL when the attribute is
        // not defined, and callers are required to handle that -- AppCompat's
        // AppCompatBackgroundHelper does exactly `if (tint != null) setTint...`.
        // An explicit `@null` value means the same thing.
        //
        // WHY THIS MATTERS (v1.0.867). The blanket black fallback below turned
        // "absent, or explicitly @null" into "tint this widget BLACK", and a
        // background tint is applied SRC_IN, so it REPLACES the drawable's own
        // fill. Measured victim: opencalculator's digit keys. Their style says
        //     <item name="android:background">@drawable/circle</item>
        //     <item name="backgroundTint">@null</item>
        // and the shape's `<solid android:color="?button_color">` resolves
        // CORRECTLY to #efefef -- proven at runtime, `fill=0xffefefef`. But
        // AppCompatButton's ctor read `backgroundTint`, got this black CSL
        // instead of null, and called setBackgroundTintList(0xff000000):
        //     fill=0xffefefef tint=0xff000000 -> 0xff000000
        // So all 20 keys painted as solid BLACK blobs against the emulator's
        // #efefef. The ONLY keys that looked right were the ones carrying an
        // explicit `android:backgroundTint="?button_X_color"` in the layout,
        // which overwrote the bogus black with a real colour -- which is exactly
        // why this read as a per-key colour bug rather than a null-handling one.
        //
        // ⚠ NARROWED to an EXPLICIT `@null` ONLY, after measuring. The first cut
        // also returned null when the attribute was ABSENT (`!hasValue`), which
        // is what AOSP does -- and it CRATERED unitconverter from 84.40% to
        // 33.32%: its entire content vanished, leaving a bare #303030 window,
        // with no exception in the log. Something downstream treats a null CSL
        // from an absent attribute as fatal-but-silent, which is precisely the
        // hazard the original blanket-black fallback was defending against.
        //
        // So only the case the app states OUTRIGHT is changed. `@null` written
        // in a style is an explicit "no tint" that cannot be read any other way,
        // it is the case opencalculator actually hits, and it carries none of
        // the absent-attribute blast radius. The wider AOSP-correct behaviour is
        // a separate change needing its own investigation of who NPEs on null.
        // A/B'd behind MACAPK_CSL_NULL=0 before landing (flag then removed):
        // opencalculator 93.42% with, 60.99% without; unitconverter unaffected
        // either way. That A/B also caught a FALSE ATTRIBUTION -- unitconverter
        // had dropped to 33.32% around the same time and looked like collateral
        // damage from this change, but it scored 33.32% with the flag OFF too.
        // The real cause was APP STATE: its Help dialog is first-run-only and
        // repeated recapture runs had flipped `show_help` to false in its
        // shared_prefs, so we stopped drawing a dialog the emulator reference
        // still has. Restoring the pref returned it to 84.40%.
        String raw = macapkRawValueAt(index);
        if (raw != null && "@null".equals(raw.trim())) return null;
        return android.content.res.ColorStateList.valueOf(0xFF000000);
    }
    /** AOSP TypedArray.getType: the data type of the attribute at index, one
     *  of the TypedValue.TYPE_* constants. v1.0.913: answered from the ONE
     *  resolved record (macapkRecordAt), so the type callers branch on is
     *  the LEAF type after `?attr`/`@ref` resolution -- what a compiled
     *  resource table reports -- instead of a raw-string guess that called
     *  every reference TYPE_REFERENCE even when it resolves to a colour.
     *  TYPE_NULL when no value (also AOSP: hasValue and getType agree). */
    public int getType(int index) {
        android.util.TypedValue rec = macapkRecordAt(index);
        return rec != null ? rec.type : android.util.TypedValue.TYPE_NULL;
    }
    /** AOSP TypedArray.getValue: fills outValue with the typed value at index
     *  and returns true if the attribute had a value (getValueAt,
     *  TypedArray.java:1349 android11-release: copy type/data/resourceId,
     *  string ONLY for TYPE_STRING). v1.0.913: the record comes from the
     *  shared raw->TypedValue resolver, which fixed the two encoding bugs
     *  the fwprobe named at this exact surface: TYPE_DIMENSION used to
     *  carry raw PIXELS in data (complexToDimensionPixelSize then decoded
     *  garbage; AOSP packs mantissa/radix/unit), and resourceId was never
     *  populated for reference-resolved values. */
    public boolean getValue(int index, android.util.TypedValue outValue) {
        if (outValue == null) return false;
        android.util.TypedValue rec = macapkRecordAt(index);
        if (rec == null) {
            outValue.type = android.util.TypedValue.TYPE_NULL;
            outValue.string = null;
            outValue.data = 0;
            return false;
        }
        outValue.type = rec.type;
        outValue.data = rec.data;
        outValue.resourceId = rec.resourceId;
        outValue.string = rec.string;
        return true;
    }
    /** AOSP TypedArray.peekValue (android14-release :1214-1228): the value
     *  at index, or NULL when the slot holds no value. getValueAt (:1372-1377)
     *  returns false for ANY TYPE_NULL slot -- absent, @null and @empty alike --
     *  so null is the device's answer for all three, and only hasValueOrEmpty
     *  tells @empty apart.
     *
     *  ⚠ This returned a TYPE_NULL record instead of null (the v1.0.98
     *  never-null stub contract), and the commonest AndroidX presence test is
     *  exactly `peekValue(i) != null` (androidx.leanback's
     *  HorizontalGridView.setRowHeight / VerticalGridView.setColumnWidth,
     *  AppCompat and Material readers), so every UNSET attribute read as
     *  present and the app then applied the getter's default as if it had been
     *  declared. Found while bringing up SmartTube. Our own framework callers
     *  that want the never-null record (TextView's appearance read) use
     *  {@link #macapkPeekRecord} instead. AOSP returns its internal mValue;
     *  we return a COPY so a caller mutating it cannot corrupt the cache. */
    public android.util.TypedValue peekValue(int index) {
        android.util.TypedValue v = macapkPeekRecord(index);
        return v.type == android.util.TypedValue.TYPE_NULL ? null : v;
    }
    /** Mac.apk-internal, never null: peekValue's record with "no value"
     *  expressed as TYPE_NULL. Its data carries what the device's internal
     *  record holds, so it cannot contradict hasValueOrEmpty:
     *  DATA_NULL_EMPTY for @empty (aapt2 MakeEmpty), DATA_NULL_UNDEFINED (0)
     *  for @null and absent. Not AOSP API; app code never sees it. */
    public android.util.TypedValue macapkPeekRecord(int index) {
        android.util.TypedValue rec = macapkRecordAt(index);
        android.util.TypedValue v = new android.util.TypedValue();
        if (rec != null) {
            v.type = rec.type;
            v.data = rec.data;
            v.resourceId = rec.resourceId;
            v.string = rec.string;
        } else {
            v.type = android.util.TypedValue.TYPE_NULL;
            if (macapkRecordState != null && index >= 0 && index < macapkRecordState.length
                    && macapkRecordState[index] == 3) {
                v.data = android.util.TypedValue.DATA_NULL_EMPTY;
            }
        }
        return v;
    }
    public String getPositionDescription()                { return "stub-TypedArray"; }

    public void recycle() { /* no pool */ }

    /** AOSP core/java/android/content/res/TypedArray.java -- TypedArray is
     *  AutoCloseable (API 31) so it can be used in try-with-resources, and
     *  close() is `recycle()`. The INTERFACE is the load-bearing half: without
     *  it a `try (TypedArray a = ...)` compiled against android.jar fails its
     *  checkcast at runtime, not just its call. */
    @Override public void close() { recycle(); }
    public int getChangingConfigurations() { return 0; }

    /**
     * AOSP TypedArray.getFont (API 26): "Retrieve the Typeface for the attribute
     * at index." Resolves the attribute's resource id and loads it through
     * Resources.getFont, which handles both a raw font file and a
     * `<font-family>` descriptor.
     *
     * ⚠ It returned a hardcoded `null`, and that null is LOAD-BEARING in AOSP's
     * own control flow: TextView.readTextAppearance tries getFont FIRST and only
     * falls back to reading the attribute as a family NAME when it yields
     * nothing (TextView.java:4017-4026). With a permanent null, every
     * `android:fontFamily="@font/brand"` in the fleet fell through to the
     * name path, where "@font/brand" is not a fonts.xml family, so it landed on
     * Typeface.DEFAULT -- the app's own bundled font, dropped silently.
     *
     * ⚠ NO RESOURCE ID => NULL, not an exception. AOSP returns null for an
     * ABSENT attribute and only throws for a present-but-not-a-font one, and
     * its caller catches that throw to fall back to the family-NAME path. But
     * the string-valued case is the COMMON one in the fleet -- `fontFamily`
     * usually names a system family -- and throwing there would build a
     * NotFoundException, stack trace and all, for every TextView in every
     * inflation, purely to be caught one frame up. Both shapes reach the
     * caller's fallback identically, so the cheap one is used. A resource id
     * that IS present but cannot be loaded still throws, out of
     * Resources.getFont, which is the case the distinction actually protects.
     */
    public android.graphics.Typeface getFont(int index) {
        int id = getResourceId(index, 0);
        if (id == 0) return null;
        Resources r = resources;
        if (r == null) return null;
        return r.getFont(id);   // throws NotFoundException when unresolvable
    }
}

