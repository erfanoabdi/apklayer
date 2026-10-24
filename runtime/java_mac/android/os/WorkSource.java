// android.os.WorkSource -- attributes power/work to one or more uids (used by
// PowerManager.WakeLock.setWorkSource and AlarmManager). Mac.apk does no power
// attribution, but the value object is fully functional (real uid list) so apps
// constructing and passing one behave correctly.
//
// AOSP ref: frameworks/base/core/java/android/os/WorkSource.java
package android.os;

public class WorkSource implements Parcelable {
    private final java.util.ArrayList<Integer> mUids = new java.util.ArrayList<>();
    private final java.util.ArrayList<String> mNames = new java.util.ArrayList<>();

    public WorkSource() {}

    public WorkSource(int uid) { mUids.add(uid); mNames.add(null); }

    public WorkSource(int uid, String name) { mUids.add(uid); mNames.add(name); }

    public WorkSource(WorkSource orig) {
        if (orig != null) { mUids.addAll(orig.mUids); mNames.addAll(orig.mNames); }
    }

    public int size() { return mUids.size(); }

    public int get(int index) { return mUids.get(index); }

    public String getName(int index) { return mNames.get(index); }

    public boolean add(int uid) {
        mUids.add(uid); mNames.add(null);
        return true;
    }

    public boolean add(int uid, String name) {
        mUids.add(uid); mNames.add(name);
        return true;
    }

    public boolean add(WorkSource other) {
        if (other == null) return false;
        mUids.addAll(other.mUids); mNames.addAll(other.mNames);
        return true;
    }

    public boolean remove(WorkSource other) {
        if (other == null) return false;
        boolean changed = false;
        for (int i = 0; i < other.mUids.size(); i++) {
            int idx = mUids.indexOf(other.mUids.get(i));
            if (idx >= 0) { mUids.remove(idx); mNames.remove(idx); changed = true; }
        }
        return changed;
    }

    public void set(WorkSource other) {
        clear();
        if (other != null) { mUids.addAll(other.mUids); mNames.addAll(other.mNames); }
    }

    public void clear() { mUids.clear(); mNames.clear(); }

    public boolean isEmpty() { return mUids.isEmpty(); }

    /** AOSP core/java/android/os/WorkSource.java:300 -- "Compare this
     *  WorkSource with another. @return If there is a DIFFERENCE, true is
     *  returned." Note the inverted sense (true means unequal), and that AOSP
     *  compares names only when BOTH name arrays are non-null. Ported with the
     *  same shape over our list-backed storage. */
    public boolean diff(WorkSource other) {
        int n = mUids.size();
        if (other == null || n != other.mUids.size()) {
            return true;
        }
        for (int i = 0; i < n; i++) {
            if (!mUids.get(i).equals(other.mUids.get(i))) {
                return true;
            }
            String a = mNames.get(i);
            String b = other.mNames.get(i);
            if (a != null && b != null && !a.equals(b)) {
                return true;
            }
        }
        return false;
    }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mUids.size());
        for (int i = 0; i < mUids.size(); i++) dest.writeInt(mUids.get(i));
        dest.writeInt(mNames.size());
        for (int i = 0; i < mNames.size(); i++) dest.writeString(mNames.get(i));
    }

    public static final Parcelable.Creator<WorkSource> CREATOR =
        new Parcelable.Creator<WorkSource>() {
            @Override public WorkSource createFromParcel(Parcel in) {
                WorkSource ws = new WorkSource();
                int n = in.readInt();
                for (int i = 0; i < n; i++) ws.mUids.add(in.readInt());
                int m = in.readInt();
                for (int i = 0; i < m; i++) ws.mNames.add(in.readString());
                return ws;
            }
            @Override public WorkSource[] newArray(int size) { return new WorkSource[size]; }
        };
}
