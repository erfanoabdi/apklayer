package android.content;
import android.os.Parcel;
import android.os.Parcelable;
public final class ComponentName implements Parcelable, Cloneable,
        Comparable<ComponentName> {
    private final String pkg;
    private final String cls;
    public ComponentName(String pkg, String cls) { this.pkg = pkg; this.cls = cls; }
    public ComponentName(Context c, String cls) { this.pkg = c != null ? c.getPackageName() : ""; this.cls = cls; }
    public ComponentName(Context c, Class<?> cls) { this.pkg = c != null ? c.getPackageName() : ""; this.cls = cls.getName(); }
    public ComponentName(Parcel in) { this.pkg = in.readString(); this.cls = in.readString(); }
    public String getPackageName() { return pkg; }
    public String getClassName()   { return cls; }
    /** AOSP getShortClassName: when the class name begins with the package
     *  name, return the suffix (with the leading "." preserved for nested
     *  names) -- e.g. pkg="com.x" cls="com.x.MainActivity" -> ".MainActivity".
     *  Apps log/compare against this short form. */
    public String getShortClassName() {
        if (pkg != null && cls != null && cls.startsWith(pkg)) {
            int pn = pkg.length();
            int cn = cls.length();
            if (cn > pn && cls.charAt(pn) == '.') {
                return cls.substring(pn);
            }
        }
        return cls;
    }
    public String flattenToString()   { return pkg + "/" + cls; }
    public String flattenToShortString() { return pkg + "/" + getShortClassName(); }
    public static ComponentName unflattenFromString(String s) {
        if (s == null) return null;
        int slash = s.indexOf('/');
        // AOSP ComponentName.unflattenFromString (aosp-mirror
        // platform_frameworks_base@main, core/java/android/content/
        // ComponentName.java, fetched 2026-07-30):
        //     int sep = str.indexOf('/');
        //     if (sep < 0 || (sep+1) >= str.length()) { return null; }
        // i.e. reject "no slash" AND "nothing after the slash", but a
        // slash at index 0 (empty package, e.g. "/Blah") is VALID -- AOSP
        // never special-cases sep==0. This used to reject `slash <= 0`,
        // which is the wrong boundary in both directions: it returned
        // null for a leading-slash string AOSP accepts ("/Blah" ->
        // pkg="" cls="Blah"), and returned a bogus non-null
        // ComponentName(pkg, "") for a trailing-slash string AOSP
        // rejects ("pkg/" has (sep+1) == length -> null on AOSP), so a
        // caller's `== null` malformed-input check silently passed
        // through a component with an empty class name instead.
        if (slash < 0 || (slash + 1) >= s.length()) return null;
        String pkg = s.substring(0, slash);
        String cls = s.substring(slash + 1);
        // AOSP expands a leading "." in the class part against the package.
        if (cls.length() > 0 && cls.charAt(0) == '.') {
            cls = pkg + cls;
        }
        return new ComponentName(pkg, cls);
    }
    /** AOSP ComponentName.createRelative(pkg, cls): resolves a class name that
     *  may be relative (leading ".") against a package. */
    public static ComponentName createRelative(String pkg, String cls) {
        if (cls != null && !cls.isEmpty() && cls.charAt(0) == '.') {
            cls = pkg + cls;
        }
        return new ComponentName(pkg, cls);
    }
    public static ComponentName createRelative(Context pkg, String cls) {
        return createRelative(pkg.getPackageName(), cls);
    }
    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) { dest.writeString(pkg); dest.writeString(cls); }
    public static final Creator<ComponentName> CREATOR = new Creator<ComponentName>() {
        @Override public ComponentName createFromParcel(Parcel s) { return new ComponentName(s.readString(), s.readString()); }
        @Override public ComponentName[] newArray(int sz) { return new ComponentName[sz]; }
    };
    /** AOSP core/java/android/content/ComponentName.java:293 --
     *      return "{" + mPackage + "/" + mClass + "}";
     *  Note it is NOT flattenToShortString(): the class name is the full one,
     *  only the wrapper differs from toString(). */
    public String toShortString() { return "{" + pkg + "/" + cls + "}"; }

    /** AOSP :381 -- the NULL-TOLERANT static read, paired with
     *  writeToParcel(ComponentName, Parcel) below. A null was written as a
     *  null package string, so a null package reads back as a null object. */
    public static ComponentName readFromParcel(Parcel in) {
        String p = in.readString();
        return p != null ? new ComponentName(p, in.readString()) : null;
    }

    /** AOSP :362 */
    public static void writeToParcel(ComponentName c, Parcel out) {
        if (c != null) {
            c.writeToParcel(out, 0);
        } else {
            out.writeString(null);
        }
    }

    @Override public String toString() { return "ComponentName{" + flattenToString() + "}"; }
    // AOSP defines value equality on (pkg, cls). Without this, apps that key a
    // HashMap on a ComponentName (common in plugin/router code) and intent
    // component comparison fall back to Object identity -> never match.
    @Override public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ComponentName)) return false;
        ComponentName o = (ComponentName) obj;
        return java.util.Objects.equals(pkg, o.pkg)
            && java.util.Objects.equals(cls, o.cls);
    }
    @Override public int hashCode() {
        return (pkg != null ? pkg.hashCode() : 0)
             ^ (cls != null ? cls.hashCode() : 0);
    }
    @Override public int compareTo(ComponentName that) {
        int v = pkg.compareTo(that.pkg);
        return (v != 0) ? v : cls.compareTo(that.cls);
    }
    @Override public ComponentName clone() { return new ComponentName(pkg, cls); }
}
