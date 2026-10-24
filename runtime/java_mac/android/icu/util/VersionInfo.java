package android.icu.util;
/** An ICU version (major.minor.milli.micro). */
public final class VersionInfo implements Comparable<VersionInfo> {
    private final int mMajor, mMinor, mMilli, mMicro;
    private VersionInfo(int a, int b, int c, int d) { mMajor=a; mMinor=b; mMilli=c; mMicro=d; }
    public static VersionInfo getInstance(int major, int minor, int milli, int micro) { return new VersionInfo(major, minor, milli, micro); }
    public static VersionInfo getInstance(int major, int minor, int milli) { return new VersionInfo(major, minor, milli, 0); }
    public static VersionInfo getInstance(int major, int minor) { return new VersionInfo(major, minor, 0, 0); }
    public static VersionInfo getInstance(int major) { return new VersionInfo(major, 0, 0, 0); }
    public static VersionInfo getInstance(String version) {
        String[] p = version != null ? version.split("\\.") : new String[0];
        int[] v = new int[4];
        for (int i = 0; i < 4 && i < p.length; i++) { try { v[i] = Integer.parseInt(p[i].trim()); } catch (Exception e) {} }
        return new VersionInfo(v[0], v[1], v[2], v[3]);
    }
    public int getMajor() { return mMajor; }
    public int getMinor() { return mMinor; }
    public int getMilli() { return mMilli; }
    public int getMicro() { return mMicro; }
    @Override public int compareTo(VersionInfo other) {
        if (other == null) return 1;
        int a = (mMajor<<24)|(mMinor<<16)|(mMilli<<8)|mMicro;
        int b = (other.mMajor<<24)|(other.mMinor<<16)|(other.mMilli<<8)|other.mMicro;
        return Integer.compare(a, b);
    }
    @Override public String toString() { return mMajor+"."+mMinor+"."+mMilli+"."+mMicro; }
}
