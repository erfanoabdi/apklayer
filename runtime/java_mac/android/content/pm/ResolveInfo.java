// android.content.pm.ResolveInfo -- the result of resolving an Intent
// against the package database. queryIntentActivities / resolveActivity
// / queryIntentServices return these. Apps read activityInfo (or
// serviceInfo / providerInfo) plus the match/priority fields off each
// entry to pick a target or build a chooser.
//
// AOSP ref:
//   frameworks/base/core/java/android/content/pm/ResolveInfo.java
//
// IMPORTANT: AOSP types `activityInfo` as ActivityInfo (NOT
// ApplicationInfo) and `serviceInfo` as ServiceInfo. The guest
// bytecode's getfield/checkcast carries that exact descriptor, so the
// field type MUST match or an app reading `ri.activityInfo` (e.g.
// `ri.activityInfo.launchMode`) would get the wrong field / a
// ClassCastException. The previous stub typed activityInfo as
// ApplicationInfo -- corrected here.
package android.content.pm;

public class ResolveInfo implements android.os.Parcelable {
    public ActivityInfo activityInfo;
    public ServiceInfo serviceInfo;
    public ProviderInfo providerInfo;

    /** The IntentFilter this resolution matched, when known. */
    public android.content.IntentFilter filter;

    /** Order of result in queries. Higher comes first. */
    public int priority;
    /** Preferred order among preferred activities. */
    public int preferredOrder;

    /** Only set when returned by PackageManager#queryIntentActivityOptions:
     *  which of the given specific intents this result came from. 0 is the
     *  first in the list; the AOSP default -1 means it came from the generic
     *  Intent query instead (our own resolution paths do not populate this
     *  yet -- queryIntentActivityOptions is not implemented -- so it is
     *  always the AOSP default here). */
    public int specificIndex = -1;

    /** Whether this resolution is the default action for its data. */
    public boolean isDefault;

    /** Resource id of the icon for this match (0 = none). */
    public int icon;

    /** Resource id of the label (0 = none). */
    public int labelRes;
    /** Non-localized explicit label, when no labelRes. */
    public CharSequence nonLocalizedLabel;

    /** Optional resolved package name (for chooser-style resolution). */
    public String resolvePackageName;

    /** How good the match was; one of the IntentFilter.MATCH_* values. */
    public int match;

    /** Whether the target is exported and queryable by other apps. */
    public boolean system;

    /** API-23: whether the matched component handles the requested action
     *  with an explicit <intent-filter>. */
    public boolean handleAllWebDataURI;

    /** AOSP ResolveInfo.isInstantAppAvailable (API 26, ResolveInfo.java:84):
     *  "whether or not an instant app is available for the resolved intent."
     *  Mac.apk has no Instant App resolver -- PackageManager.isInstantApp()
     *  already answers false unconditionally -- so this stays false, which is
     *  the same answer a device without an installed instant-app resolver
     *  gives. The FIELD must exist because we present API 36 and a getfield on
     *  it is otherwise a NoSuchFieldError. */
    public boolean isInstantAppAvailable;

    /** AOSP ResolveInfo.INTENT_FORWARDER_ACTIVITY (ResolveInfo.java:48-49),
     *  private upstream and private here -- the sentinel targetActivity the
     *  system uses for a cross-profile forwarding stub. */
    private static final String INTENT_FORWARDER_ACTIVITY =
            "com.android.internal.app.IntentForwarderActivity";

    public ResolveInfo() {}

    /** AOSP ResolveInfo(ResolveInfo orig) (ResolveInfo.java:421-442): the copy
     *  constructor, over the fields this tree declares. AOSP also copies
     *  noResourceId / iconResourceId / targetUserId / mAutoResolutionAllowed /
     *  userHandle, none of which exist here. */
    public ResolveInfo(ResolveInfo orig) {
        activityInfo = orig.activityInfo;
        serviceInfo = orig.serviceInfo;
        providerInfo = orig.providerInfo;
        filter = orig.filter;
        priority = orig.priority;
        preferredOrder = orig.preferredOrder;
        match = orig.match;
        specificIndex = orig.specificIndex;
        isDefault = orig.isDefault;
        labelRes = orig.labelRes;
        nonLocalizedLabel = orig.nonLocalizedLabel;
        icon = orig.icon;
        resolvePackageName = orig.resolvePackageName;
        system = orig.system;
        handleAllWebDataURI = orig.handleAllWebDataURI;
        isInstantAppAvailable = orig.isInstantAppAvailable;
    }

    /** AOSP ResolveInfo.isCrossProfileIntentForwarderActivity() (API 30), body
     *  verbatim: true only when the resolved activity IS the system's
     *  cross-profile forwarding stub.
     *
     *  Mac.apk has one user and no work profile, so nothing we resolve ever
     *  targets IntentForwarderActivity and this is always false -- the same
     *  answer a single-user device gives. Implemented as the real comparison
     *  rather than a hardcoded false so it stays correct if a forwarder ever
     *  does appear. Callers use it to hide the "switch to work profile" row
     *  from a chooser. */
    public boolean isCrossProfileIntentForwarderActivity() {
        return activityInfo != null
                && INTENT_FORWARDER_ACTIVITY.equals(activityInfo.targetActivity);
    }

    /** AOSP ResolveInfo.dump(Printer, String):358-360 -> the flags form at
     *  :362-388, verbatim. Note it does NOT call dumpFront/dumpBack: a
     *  ResolveInfo is not a PackageItemInfo, it CONTAINS one, so it prints its
     *  own header and then delegates into the component's dump. */
    public void dump(android.util.Printer pw, String prefix) {
        dump(pw, prefix, PackageItemInfo.DUMP_FLAG_ALL);
    }

    public void dump(android.util.Printer pw, String prefix, int dumpFlags) {
        if (filter != null) {
            pw.println(prefix + "Filter:");
            // AOSP calls filter.dump(pw, prefix + "  ") here. This tree's
            // android.content.IntentFilter -- a different package, owned
            // elsewhere -- has no dump(Printer, String), so the filter body is
            // printed via its toString() instead of AOSP's multi-line breakdown.
            // The HEADER line above is AOSP's, so the surrounding structure a
            // reader (or a log scraper) sees is unchanged; only the filter's own
            // detail lines differ. Replace this with the delegating call the
            // moment IntentFilter.dump exists.
            pw.println(prefix + "  " + filter);
        }
        pw.println(prefix + "priority=" + priority
                + " preferredOrder=" + preferredOrder
                + " match=0x" + Integer.toHexString(match)
                + " specificIndex=" + specificIndex
                + " isDefault=" + isDefault);
        if (resolvePackageName != null) {
            pw.println(prefix + "resolvePackageName=" + resolvePackageName);
        }
        if (labelRes != 0 || nonLocalizedLabel != null || icon != 0) {
            pw.println(prefix + "labelRes=0x" + Integer.toHexString(labelRes)
                    + " nonLocalizedLabel=" + nonLocalizedLabel
                    + " icon=0x" + Integer.toHexString(icon));
        }
        if (activityInfo != null) {
            pw.println(prefix + "ActivityInfo:");
            activityInfo.dump(pw, prefix + "  ", dumpFlags);
        } else if (serviceInfo != null) {
            pw.println(prefix + "ServiceInfo:");
            serviceInfo.dump(pw, prefix + "  ", dumpFlags);
        } else if (providerInfo != null) {
            pw.println(prefix + "ProviderInfo:");
            providerInfo.dump(pw, prefix + "  ", dumpFlags);
        }
    }

    /** AOSP ResolveInfo.loadLabel(PackageManager): the user-visible label
     *  for the resolved component. We mirror AOSP's resolution order:
     *  explicit nonLocalizedLabel, else the component's own label, else
     *  the package name. We have no per-resource label table, so fall
     *  through to the component's loadLabel. */
    public CharSequence loadLabel(PackageManager pm) {
        if (nonLocalizedLabel != null) return nonLocalizedLabel;
        PackageItemInfo ci = getComponentInfo();
        if (ci != null) {
            CharSequence l = ci.loadLabel(pm);
            if (l != null) return l;
        }
        return resolvePackageName != null ? resolvePackageName : "";
    }

    /** AOSP ResolveInfo.loadIcon(PackageManager): the resolution's icon.
     *  Real AOSP first tries a resource-id lookup through resolvePackageName
     *  / the hidden iconResourceId field via PackageManager#getDrawable,
     *  then badges the result via PackageManager#getUserBadgedIcon -- neither
     *  of which exists on this tree's PackageManager (no per-resource
     *  Drawable-by-id loader, no multi-user concept), so those legs of the
     *  real method never fire and it falls through to the component's own
     *  loadIcon either way. We implement exactly that fallthrough, delegating
     *  to the already-correct PackageItemInfo.loadIcon (never returns null,
     *  matching the AOSP contract callers rely on). */
    public android.graphics.drawable.Drawable loadIcon(PackageManager pm) {
        return getComponentInfo().loadIcon(pm);
    }

    /** AOSP ResolveInfo.getComponentInfo(): returns whichever of
     *  activityInfo / serviceInfo / providerInfo is non-null. */
    public ComponentInfo getComponentInfo() {
        if (activityInfo != null) return activityInfo;
        if (serviceInfo != null)  return serviceInfo;
        if (providerInfo != null) return providerInfo;
        throw new IllegalStateException("Missing ComponentInfo!");
    }

    @Override public String toString() {
        ComponentInfo ci;
        try { ci = getComponentInfo(); } catch (IllegalStateException e) { ci = null; }
        return "ResolveInfo{" + (ci != null ? ci.name : "null")
                + " m=0x" + Integer.toHexString(match) + "}";
    }

    /** AOSP nested comparator that orders ResolveInfo by display label. */
    public static class DisplayNameComparator implements java.util.Comparator<ResolveInfo> {
        private final PackageManager mPM;
        public DisplayNameComparator(PackageManager pm) { mPM = pm; }
        @Override public int compare(ResolveInfo a, ResolveInfo b) {
            CharSequence sa = a.loadLabel(mPM);
            if (sa == null) sa = a.activityInfo != null ? a.activityInfo.name : "";
            CharSequence sb = b.loadLabel(mPM);
            if (sb == null) sb = b.activityInfo != null ? b.activityInfo.name : "";
            return sCollator.compare(sa.toString(), sb.toString());
        }
        private final java.text.Collator sCollator = java.text.Collator.getInstance();
    }

    public int getIconResource() { return icon; }

    // ---- Parcelable ----------------------------------------------------
    //
    // AOSP declares ResolveInfo Parcelable (core/java/android/content/pm/
    // ResolveInfo.java, android16-release :writeToParcel). Apps genuinely
    // parcel these: a chooser or share sheet puts the queryIntentActivities
    // result into a Bundle or an Intent extra. Without the interface,
    // Bundle.getParcelable in this runtime returns null for the value the app
    // just put (the put is unchecked under VM-wide -Xverify:none), so the list
    // silently came back empty.
    //
    // Field order is AOSP's. The fields AOSP writes that we do not declare
    // (targetUserId, noResourceId, iconResourceId, mAutoResolutionAllowed,
    // isInstantAppAvailable, userHandle) are omitted rather than padded --
    // nothing outside this process reads these bytes, so there is no wire to
    // stay compatible with, and a padded slot would only invite someone to
    // read it back as a real value.
    private ResolveInfo(android.os.Parcel source) {
        switch (source.readInt()) {
            case 1: activityInfo = ActivityInfo.CREATOR.createFromParcel(source); break;
            case 2: serviceInfo  = ServiceInfo.CREATOR.createFromParcel(source);  break;
            case 3: providerInfo = ProviderInfo.CREATOR.createFromParcel(source); break;
            default: break;
        }
        if (source.readInt() != 0) {
            filter = android.content.IntentFilter.CREATOR.createFromParcel(source);
        }
        priority = source.readInt();
        preferredOrder = source.readInt();
        match = source.readInt();
        specificIndex = source.readInt();
        labelRes = source.readInt();
        nonLocalizedLabel =
            android.text.TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(source);
        icon = source.readInt();
        resolvePackageName = source.readString();
        system = source.readInt() != 0;
        handleAllWebDataURI = source.readInt() != 0;
        isDefault = source.readInt() != 0;
        isInstantAppAvailable = source.readInt() != 0;
    }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel dest, int parcelableFlags) {
        if (activityInfo != null) {
            dest.writeInt(1);
            activityInfo.writeToParcel(dest, parcelableFlags);
        } else if (serviceInfo != null) {
            dest.writeInt(2);
            serviceInfo.writeToParcel(dest, parcelableFlags);
        } else if (providerInfo != null) {
            dest.writeInt(3);
            providerInfo.writeToParcel(dest, parcelableFlags);
        } else {
            dest.writeInt(0);
        }
        if (filter != null) {
            dest.writeInt(1);
            filter.writeToParcel(dest, parcelableFlags);
        } else {
            dest.writeInt(0);
        }
        dest.writeInt(priority);
        dest.writeInt(preferredOrder);
        dest.writeInt(match);
        dest.writeInt(specificIndex);
        dest.writeInt(labelRes);
        android.text.TextUtils.writeToParcel(nonLocalizedLabel, dest, parcelableFlags);
        dest.writeInt(icon);
        dest.writeString(resolvePackageName);
        dest.writeInt(system ? 1 : 0);
        dest.writeInt(handleAllWebDataURI ? 1 : 0);
        // AOSP carries isDefault on the hidden `noResourceId`/filter path; we
        // model it as a plain field, so it is written explicitly and last.
        dest.writeInt(isDefault ? 1 : 0);
        dest.writeInt(isInstantAppAvailable ? 1 : 0);
    }

    public static final android.os.Parcelable.Creator<ResolveInfo> CREATOR =
            new android.os.Parcelable.Creator<ResolveInfo>() {
        @Override public ResolveInfo createFromParcel(android.os.Parcel source) {
            return new ResolveInfo(source);
        }
        @Override public ResolveInfo[] newArray(int size) {
            return new ResolveInfo[size];
        }
    };
}

