package com.google.firebase.firestore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// com.google.firebase.firestore.SetOptions -- the optional merge behavior for
// DocumentReference.set / WriteBatch.set / Transaction.set. On our offline
// shim writes succeed locally without a backend, so the merge mode never
// changes an observable result, but apps construct these and pass them to
// set(), so the factory surface must exist exactly as published.
public final class SetOptions {

    private static final SetOptions OVERWRITE = new SetOptions(false, null);
    private static final SetOptions MERGE_ALL = new SetOptions(true, null);

    private final boolean merge;
    private final List<FieldPath> fieldMask;

    private SetOptions(boolean merge, List<FieldPath> fieldMask) {
        this.merge = merge;
        this.fieldMask = fieldMask;
    }

    public boolean isMerge() { return merge; }

    List<FieldPath> getFieldMask() { return fieldMask; }

    // Returns an instance that merges every field of the supplied data,
    // leaving omitted fields untouched.
    public static SetOptions merge() { return MERGE_ALL; }

    // Returns an instance that merges only the named (string) fields.
    public static SetOptions mergeFields(List<String> fields) {
        List<FieldPath> mask = new ArrayList<>();
        for (String f : fields) {
            mask.add(FieldPath.of(f.split("\\.")));
        }
        return new SetOptions(true, mask);
    }

    public static SetOptions mergeFields(String... fields) {
        return mergeFields(new ArrayList<>(Arrays.asList(fields)));
    }

    public static SetOptions mergeFieldPaths(List<FieldPath> fields) {
        return new SetOptions(true, new ArrayList<>(fields));
    }

    // Package-private accessor used internally; not part of the public surface
    // but used by the data layer to distinguish full-overwrite from merge.
    static SetOptions overwrite() { return OVERWRITE; }
}
