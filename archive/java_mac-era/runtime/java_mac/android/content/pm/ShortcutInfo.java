// android.content.pm.ShortcutInfo (API 25) -- a single app shortcut
// descriptor. Mac.apk surfaces no launcher shortcuts, so these are
// inert data holders: androidx.core.content.pm.ShortcutInfoCompat
// builds a real ShortcutInfo via ShortcutInfo.Builder on API>=25 and
// hands it to ShortcutManagerCompat, which our ShortcutManager
// no-ops. The class (and its nested Builder) must EXIST or the compat
// layer throws NoClassDefFoundError at the first shortcut op
// (Wikipedia hits this during boot once the binary manifest reports
// its real targetSdk, which routes ShortcutInfoCompat down the
// API-25 native path). Mirrors the AOSP public surface; getters
// return what was set so a caller that round-trips through the
// builder sees consistent values.
package android.content.pm;

import android.app.Person;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.PersistableBundle;
import android.os.UserHandle;

import java.util.Objects;
import java.util.Set;

public final class ShortcutInfo implements android.os.Parcelable {
    // AOSP DISABLED_REASON_* (ShortcutInfo.java) -- values a caller compares
    // getDisabledReason() against. Our shortcuts are never actually
    // disabled (ShortcutManager is a no-op sink, see the class header), so
    // getDisabledReason() always returns NOT_DISABLED; the constants still
    // need to exist so a comparison like `info.getDisabledReason() ==
    // ShortcutInfo.DISABLED_REASON_NOT_DISABLED` doesn't NoSuchFieldError.
    public static final int DISABLED_REASON_NOT_DISABLED = 0;
    public static final int DISABLED_REASON_BY_APP = 1;
    public static final int DISABLED_REASON_APP_CHANGED = 2;
    public static final int DISABLED_REASON_UNKNOWN = 3;
    public static final int DISABLED_REASON_VERSION_LOWER = 100;
    public static final int DISABLED_REASON_BACKUP_NOT_SUPPORTED = 101;
    public static final int DISABLED_REASON_SIGNATURE_MISMATCH = 102;
    public static final int DISABLED_REASON_OTHER_RESTORE_ISSUE = 103;

    // AOSP SURFACE_LAUNCHER -- the only surface flag currently defined;
    // pairs with Builder.setExcludedFromSurfaces(int) below.
    public static final int SURFACE_LAUNCHER = 1 << 0;

    /** AOSP ShortcutInfo.SHORTCUT_CATEGORY_CONVERSATION (API 30): the
     *  well-known category string that marks a shortcut as a CONVERSATION, so
     *  the notification shade can promote it into the conversation section.
     *  Value verbatim -- an app puts this exact string into setCategories, so
     *  the VALUE is the contract. */
    public static final String SHORTCUT_CATEGORY_CONVERSATION =
            "android.shortcut.conversation";

    private final String id;
    private final CharSequence shortLabel;
    private final CharSequence longLabel;
    private final CharSequence disabledMessage;
    private final Intent[] intents;
    private final ComponentName activity;
    private final Icon icon;
    private final Set<String> categories;
    private final PersistableBundle extras;
    private final int rank;
    private final boolean longLived;
    private final Person[] persons;
    private final int excludedSurfaces;
    /** AOSP ShortcutInfo.mCapabilityBindings (API 33): capability -> its
     *  parameters. Insertion-ordered, because getCapabilities() is documented
     *  to return them "in the order they were added". */
    private final java.util.LinkedHashMap<Capability, java.util.List<CapabilityParams>>
            capabilityBindings;
    /** AOSP ShortcutInfo.mStartingThemeResId (API 33): the theme used for the
     *  starting window when this shortcut launches. @hide getter upstream, so
     *  only the Builder setter is public API. */
    private final int startingThemeResId;

    private ShortcutInfo(Builder b) {
        this.id = b.id;
        this.mLocusId = b.locusId;
        this.shortLabel = b.shortLabel;
        this.longLabel = b.longLabel;
        this.disabledMessage = b.disabledMessage;
        this.intents = b.intents;
        this.activity = b.activity;
        this.icon = b.icon;
        this.categories = b.categories;
        this.extras = b.extras;
        this.rank = b.rank;
        this.longLived = b.longLived;
        this.persons = b.persons;
        this.excludedSurfaces = b.excludedSurfaces;
        this.capabilityBindings = (b.capabilityBindings == null)
                ? null : new java.util.LinkedHashMap<>(b.capabilityBindings);
        this.startingThemeResId = b.startingThemeResId;
    }

    /** AOSP ShortcutInfo.getCapabilities() (API 33): "Returns the list of
     *  capability names associated with this shortcut." @NonNull; empty when
     *  the shortcut declares none. */
    public java.util.List<Capability> getCapabilities() {
        if (capabilityBindings == null) return java.util.Collections.emptyList();
        return new java.util.ArrayList<>(capabilityBindings.keySet());
    }

    /** AOSP ShortcutInfo.getCapabilityParams(Capability) (API 33): "Returns the
     *  list of values for the given capability, or an empty list if the
     *  capability was not added to this shortcut." @NonNull. */
    public java.util.List<CapabilityParams> getCapabilityParams(Capability capability) {
        if (capability == null || capabilityBindings == null) {
            return java.util.Collections.emptyList();
        }
        java.util.List<CapabilityParams> params = capabilityBindings.get(capability);
        return (params == null) ? java.util.Collections.<CapabilityParams>emptyList()
                                : new java.util.ArrayList<>(params);
    }

    /** AOSP's @hide accessor for the Builder.setStartingTheme value. */
    public int getStartingThemeResId() { return startingThemeResId; }

    public String getId()                  { return id; }
    public CharSequence getShortLabel()    { return shortLabel; }
    public CharSequence getLongLabel()     { return longLabel; }
    public CharSequence getDisabledMessage() { return disabledMessage; }
    public Intent getIntent()              { return (intents != null && intents.length > 0) ? intents[intents.length - 1] : null; }
    public Intent[] getIntents()           { return intents; }
    public ComponentName getActivity()     { return activity; }
    public Icon getIcon()                  { return icon; }
    public Set<String> getCategories()     { return categories; }
    public PersistableBundle getExtras()   { return extras; }
    public int getRank()                   { return rank; }
    public boolean isLongLived()           { return longLived; }
    public String getPackage()             { return activity != null ? activity.getPackageName() : null; }
    public Person[] getPersons()           { return persons; }
    public boolean isExcludedFromSurfaces(int surface) { return (excludedSurfaces & surface) != 0; }

    // ---- Parcelable ----------------------------------------------------
    // AOSP: core/java/android/content/pm/ShortcutInfo.java (android16-release)
    // declares the class Parcelable, and androidx.core's ShortcutInfoCompat
    // parcels one whenever it crosses into ShortcutManagerCompat or is saved
    // in instance state. Without the interface a ShortcutInfo put into a
    // Bundle read back as null in this runtime, since the put is unchecked
    // under VM-wide -Xverify:none.
    //
    // AOSP's own writeToParcel covers a much larger @hide field set (flags,
    // user id, timestamps, bitmap paths, the resource-name indirection). We
    // model the public builder surface only, so that is exactly what is
    // written; see the note in ResolveInfo about why the unmodelled fields are
    // absent rather than padded.
    //
    // categories is a Set<String> in the public API, written as a plain string
    // list and rebuilt as a LinkedHashSet so iteration order survives the
    // round trip (AOSP uses an ArraySet, which is also insertion-ordered).
    private ShortcutInfo(android.os.Parcel p) {
        this.id = p.readString();
        this.mLocusId = p.readInt() != 0
                ? android.content.LocusId.CREATOR.createFromParcel(p) : null;
        this.shortLabel = android.text.TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(p);
        this.longLabel = android.text.TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(p);
        this.disabledMessage =
                android.text.TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(p);
        this.intents = p.createTypedArray(android.content.Intent.CREATOR);
        this.activity = p.readInt() != 0
                ? android.content.ComponentName.CREATOR.createFromParcel(p) : null;
        this.icon = p.readInt() != 0
                ? android.graphics.drawable.Icon.CREATOR.createFromParcel(p) : null;
        java.util.ArrayList<String> cats = p.createStringArrayList();
        this.categories = cats == null ? null : new java.util.LinkedHashSet<>(cats);
        this.extras = p.readInt() != 0
                ? android.os.PersistableBundle.CREATOR.createFromParcel(p) : null;
        this.rank = p.readInt();
        this.longLived = p.readInt() != 0;
        this.persons = p.createTypedArray(android.app.Person.CREATOR);
        this.excludedSurfaces = p.readInt();
        this.startingThemeResId = p.readInt();
        // Capability bindings: a count, then per capability its name and its
        // params list. Written by writeToParcel below in the same order.
        int capCount = p.readInt();
        if (capCount < 0) {
            this.capabilityBindings = null;
        } else {
            java.util.LinkedHashMap<Capability, java.util.List<CapabilityParams>> caps =
                    new java.util.LinkedHashMap<>(Math.max(1, capCount));
            for (int i = 0; i < capCount; i++) {
                Capability cap = Capability.CREATOR.createFromParcel(p);
                java.util.ArrayList<CapabilityParams> params =
                        p.createTypedArrayList(CapabilityParams.CREATOR);
                caps.put(cap, params != null ? params
                        : new java.util.ArrayList<CapabilityParams>());
            }
            this.capabilityBindings = caps;
        }
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel p, int flags) {
        p.writeString(id);
        if (mLocusId == null) { p.writeInt(0); }
        else { p.writeInt(1); mLocusId.writeToParcel(p, flags); }
        android.text.TextUtils.writeToParcel(shortLabel, p, flags);
        android.text.TextUtils.writeToParcel(longLabel, p, flags);
        android.text.TextUtils.writeToParcel(disabledMessage, p, flags);
        p.writeTypedArray(intents, flags);
        if (activity == null) { p.writeInt(0); }
        else { p.writeInt(1); activity.writeToParcel(p, flags); }
        if (icon == null) { p.writeInt(0); }
        else { p.writeInt(1); icon.writeToParcel(p, flags); }
        p.writeStringList(categories == null ? null
                : new java.util.ArrayList<>(categories));
        if (extras == null) { p.writeInt(0); }
        else { p.writeInt(1); extras.writeToParcel(p, flags); }
        p.writeInt(rank);
        p.writeInt(longLived ? 1 : 0);
        p.writeTypedArray(persons, flags);
        p.writeInt(excludedSurfaces);
        p.writeInt(startingThemeResId);
        if (capabilityBindings == null) {
            p.writeInt(-1);
        } else {
            p.writeInt(capabilityBindings.size());
            for (java.util.Map.Entry<Capability, java.util.List<CapabilityParams>> e
                    : capabilityBindings.entrySet()) {
                e.getKey().writeToParcel(p, flags);
                p.writeTypedList(e.getValue());
            }
        }
    }

    public static final android.os.Parcelable.Creator<ShortcutInfo> CREATOR =
            new android.os.Parcelable.Creator<ShortcutInfo>() {
        @Override public ShortcutInfo createFromParcel(android.os.Parcel p) {
            return new ShortcutInfo(p);
        }
        @Override public ShortcutInfo[] newArray(int size) {
            return new ShortcutInfo[size];
        }
    };
    public int getExcludedFromSurfaces()   { return excludedSurfaces; }

    // AOSP state getters (frameworks/base ShortcutInfo.java:1536-2102): on
    // real Android these reflect live ShortcutManager-tracked state (was
    // this shortcut published dynamically, pinned by the launcher, backed
    // up and restored, etc). Mac.apk's ShortcutManager is an intentional
    // no-op sink (see the class header) -- a ShortcutInfo we hand back
    // never went through a real publish/pin lifecycle, so every one of
    // these reports the "freshly built, not yet touched by the system"
    // state. The methods must still exist: androidx's ShortcutInfoCompat
    // API-25+ path calls straight through to the platform type, and a
    // missing method is a NoSuchMethodError at the call site regardless of
    // whether the answer would ever be interesting in our environment.
    public boolean isDynamic()             { return false; }
    public boolean isPinned()              { return false; }
    public boolean isCached()              { return false; }
    public boolean isImmutable()           { return false; }
    public boolean isEnabled()             { return true; }
    public boolean isDeclaredInManifest()  { return false; }
    public boolean hasKeyFieldsOnly()      { return false; }
    public int getDisabledReason()         { return DISABLED_REASON_NOT_DISABLED; }
    public long getLastChangedTimestamp()  { return 0L; }
    // getLocusId()/Builder.setLocusId(LocusId) ARE implemented below. The note
    // that used to live here ("LocusId does not exist anywhere in this tree")
    // is stale: android.content.LocusId now exists as a real Parcelable at
    // runtime/java/android/content/LocusId.java. Corrected 2026-08-13.
    // Single-user environment: every shortcut belongs to the one real user.
    // Mirrors the UserHandle.SYSTEM precedent in LauncherApps.java.
    android.content.LocusId mLocusId;
    public android.content.LocusId getLocusId() { return mLocusId; }
    public UserHandle getUserHandle()      { return UserHandle.SYSTEM; }

    // AOSP-conformance (framework_lint vs android-30 stub jar): AOSP does not
    // mark this final. Final-when-AOSP-is-not is the dangerous direction -- an
    // app compiled against real Android that subclasses this fails to resolve
    // against our jar. See docs/aosp-conformance-audit_2026-08-23.md.
    public static class Builder {
        final Context context;
        String id;
        CharSequence shortLabel;
        CharSequence longLabel;
        CharSequence disabledMessage;
        Intent[] intents;
        ComponentName activity;
        Icon icon;
        Set<String> categories;
        PersistableBundle extras;
        int rank;
        boolean longLived;
        Person[] persons;
        int excludedSurfaces;
        java.util.LinkedHashMap<Capability, java.util.List<CapabilityParams>> capabilityBindings;
        int startingThemeResId;

        public Builder(Context context, String id) {
            this.context = context;
            this.id = id;
        }

        public Builder setShortLabel(CharSequence shortLabel) { this.shortLabel = shortLabel; return this; }
        public Builder setLongLabel(CharSequence longLabel)   { this.longLabel = longLabel; return this; }
        public Builder setDisabledMessage(CharSequence m)     { this.disabledMessage = m; return this; }
        public Builder setIcon(Icon icon)                     { this.icon = icon; return this; }
        public Builder setIntent(Intent intent)               { this.intents = new Intent[]{ intent }; return this; }
        public Builder setIntents(Intent[] intents)           { this.intents = intents; return this; }
        public Builder setActivity(ComponentName activity)    { this.activity = activity; return this; }
        public Builder setCategories(Set<String> categories)  { this.categories = categories; return this; }
        public Builder setRank(int rank)                      { this.rank = rank; return this; }
        public Builder setExtras(PersistableBundle extras)    { this.extras = extras; return this; }
        public Builder setLongLived(boolean longLived)        { this.longLived = longLived; return this; }

        // AOSP ShortcutInfo.java:1356-1365, validation quoted verbatim: null
        // and empty are both rejected, and no element may be null.
        public Builder setPersons(Person[] persons) {
            Objects.requireNonNull(persons, "persons cannot be null");
            if (persons.length == 0) {
                throw new IllegalArgumentException("persons cannot be empty");
            }
            for (Person person : persons) {
                Objects.requireNonNull(person, "persons cannot contain null");
            }
            this.persons = persons.clone();
            return this;
        }

        /** AOSP ShortcutInfo.Builder.setPerson(Person) (API 29): the
         *  single-person convenience, whose upstream body is literally
         *  `return setPersons(new Person[]{person});` -- so it inherits
         *  setPersons' null rejection rather than repeating it. */
        public Builder setPerson(Person person) {
            return setPersons(new Person[] { person });
        }

        /** AOSP ShortcutInfo.Builder.addCapabilityBinding(Capability,
         *  CapabilityParams) (API 33): associate a built-in-intent capability
         *  (and optionally one set of its parameters) with this shortcut, so
         *  the assistant can fulfil a spoken request through it.
         *
         *  Upstream requires the capability, allows null params (registering
         *  the capability alone), and ACCUMULATES repeated params for the same
         *  capability in call order. All three reproduced.
         *
         *  ⚠ Mac.apk has no assistant and no App Actions consumer, so nothing
         *  READS these bindings -- but they round-trip truthfully through
         *  getCapabilities()/getCapabilityParams(), which is what an app that
         *  builds a shortcut and inspects it back observes. Dropping them
         *  would make the getters report a shortcut the app did not build. */
        public Builder addCapabilityBinding(Capability capability,
                CapabilityParams capabilityParams) {
            Objects.requireNonNull(capability, "capability cannot be null");
            if (capabilityBindings == null) {
                capabilityBindings = new java.util.LinkedHashMap<>(1);
            }
            java.util.List<CapabilityParams> params = capabilityBindings.get(capability);
            if (params == null) {
                params = new java.util.ArrayList<>(1);
                capabilityBindings.put(capability, params);
            }
            if (capabilityParams != null) params.add(capabilityParams);
            return this;
        }

        /** AOSP ShortcutInfo.Builder.setStartingTheme(int) (API 33): the theme
         *  resource used for the STARTING WINDOW (the splash drawn before the
         *  activity's first frame) when the user launches this shortcut.
         *
         *  Stored and readable back via the @hide getStartingThemeResId. Mac.apk
         *  surfaces no launcher, so no shortcut is ever launched from one and
         *  the theme is never applied -- but recording it keeps a built
         *  ShortcutInfo an honest description of what the app asked for. */
        public Builder setStartingTheme(int themeResId) {
            this.startingThemeResId = themeResId;
            return this;
        }

        // setLocusId(LocusId) is SKIPPED here alongside getLocusId() above --
        // android.content.LocusId doesn't exist in this tree.

        android.content.LocusId locusId;
        public Builder setLocusId(android.content.LocusId locusId) { this.locusId = locusId; return this; }
        public Builder setExcludedFromSurfaces(int surfaces) { this.excludedSurfaces = surfaces; return this; }

        public ShortcutInfo build() { return new ShortcutInfo(this); }
    }

    // The LANDMINE-DEFUSE stub that used to sit here (a CREATOR that threw
    // UnsupportedOperationException, added because ShortcutInfo was not
    // Parcelable and a silent null would have been a quiet lie) is GONE: the
    // real implementation above replaces it. Its own stated condition for
    // being replaced was "once a lane can build and round-trip test it through
    // ParcelCheck", and that is how this one was verified -- see
    // debug/parcel_verify.sh. Historical write-up:
    // docs/parcelable-gap-workorder_2026-08-13.md section 2.
}

