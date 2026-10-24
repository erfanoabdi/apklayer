package android.system;
/** uname(2) result. Mac.apk reports a Darwin/Android-flavored identity. */
public final class StructUtsname {
    public final String sysname;
    public final String nodename;
    public final String release;
    public final String version;
    public final String machine;
    public StructUtsname(String sysname, String nodename, String release, String version, String machine) {
        this.sysname = sysname; this.nodename = nodename; this.release = release; this.version = version; this.machine = machine;
    }
    @Override public String toString() {
        return "StructUtsname[sysname=" + sysname + ",release=" + release + ",machine=" + machine + "]";
    }
}
