// android.content.ContentProviderOperation -- one operation in a batch that
// ContentResolver.applyBatch / ContentProvider.applyBatch applies against a
// provider, plus the Builder that constructs it.
//
// Why Mac.apk needs it: three corpus apps reference the class (Fossify Gallery,
// Telegram, Signal). Contacts / calendar / media code is the usual caller, and
// the pattern is always the same -- build an ArrayList of operations where a
// later row refers to the row id an EARLIER operation produced, then hand the
// whole list to applyBatch. That cross-row reference is the "value back
// reference", and it is the entire reason batches exist: without it the caller
// would have to apply the insert, read the returned Uri, and only then build
// the dependent rows. So this is a REAL implementation, not a value-shaped
// stub: a half-implementation that dropped back-references would not fail
// loudly, it would silently write rows pointing at the wrong parent.
//
// AOSP source mirrored (verbatim semantics, field-for-field):
// frameworks/base/core/java/android/content/ContentProviderOperation.java
// (android11-release). Line refs used below are into that file.
//
// Two deliberate departures, both because the AOSP code reaches for @hide
// helpers this tree does not expose:
//   1. ContentValues.getValues() / putObject() / isSupportedValue() and
//      Bundle.putObject() are all @hide in AOSP. They are reproduced here as
//      private static helpers with the same type-dispatch chain (see
//      putContentValue / isSupportedValue / putBundleValue below), so the
//      observable behaviour -- including which types throw
//      IllegalArgumentException -- is unchanged.
//   2. writeToParcel mirrors AOSP's field ORDER using the primitives this
//      tree's Parcel provides, the same accommodation android.content.ContentValues
//      already makes here. Reads are paired with writes at the same positions.
//      AOSP uses Parcel.writeArrayMap/readArrayMap for the two ArrayMaps; both
//      are @hide in AOSP and this tree's Parcel does not expose either, so the
//      maps go through writeValue/readValue and are copied back into an ArrayMap
//      on read (see the constructor). The SparseArray uses AOSP's own
//      writeSparseArray/readSparseArray pair, which this tree's Parcel DOES
//      expose publicly, exactly as AOSP does.
package android.content;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;
import android.util.SparseArray;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;

public class ContentProviderOperation implements Parcelable {
    /** @hide exposed for unit tests (AOSP ContentProviderOperation.java:44) */
    public final static int TYPE_INSERT = 1;
    /** @hide exposed for unit tests */
    public final static int TYPE_UPDATE = 2;
    /** @hide exposed for unit tests */
    public final static int TYPE_DELETE = 3;
    /** @hide exposed for unit tests */
    public final static int TYPE_ASSERT = 4;
    /** @hide exposed for unit tests */
    public final static int TYPE_CALL = 5;

    private final int mType;
    private final Uri mUri;
    private final String mMethod;
    private final String mArg;
    private final ArrayMap<String, Object> mValues;
    private final ArrayMap<String, Object> mExtras;
    private final String mSelection;
    private final SparseArray<Object> mSelectionArgs;
    private final Integer mExpectedCount;
    private final boolean mYieldAllowed;
    private final boolean mExceptionAllowed;

    private final static String TAG = "ContentProviderOperation";

    /** Creates a ContentProviderOperation by copying the contents of a Builder. */
    private ContentProviderOperation(Builder builder) {
        mType = builder.mType;
        mUri = builder.mUri;
        mMethod = builder.mMethod;
        mArg = builder.mArg;
        mValues = builder.mValues;
        mExtras = builder.mExtras;
        mSelection = builder.mSelection;
        mSelectionArgs = builder.mSelectionArgs;
        mExpectedCount = builder.mExpectedCount;
        mYieldAllowed = builder.mYieldAllowed;
        mExceptionAllowed = builder.mExceptionAllowed;
    }

    private ContentProviderOperation(Parcel source) {
        mType = source.readInt();
        mUri = Uri.CREATOR.createFromParcel(source);
        mMethod = source.readInt() != 0 ? source.readString() : null;
        mArg = source.readInt() != 0 ? source.readString() : null;
        // AOSP writes the map SIZE then the map itself (-1 meaning null). The
        // size int is kept so the read/write positions still pair up.
        final int valuesSize = source.readInt();
        mValues = (valuesSize != -1) ? readArrayMapValue(source, valuesSize) : null;
        final int extrasSize = source.readInt();
        mExtras = (extrasSize != -1) ? readArrayMapValue(source, extrasSize) : null;
        mSelection = source.readInt() != 0 ? source.readString() : null;
        // AOSP: mSelectionArgs = source.readSparseArray(null). readSparseArray
        // builds a fresh SparseArray, so unlike the old cast-and-clone there is
        // nothing to defend against here -- and no cast to get wrong.
        mSelectionArgs = source.readSparseArray(null);
        mExpectedCount = source.readInt() != 0 ? source.readInt() : null;
        mYieldAllowed = source.readInt() != 0;
        mExceptionAllowed = source.readInt() != 0;
    }

    /**
     * Read one of the two ArrayMap fields back off a Parcel.
     *
     * AOSP does {@code source.readArrayMap(mValues, null)} (Parcel.java, @hide),
     * which fills a caller-supplied ArrayMap directly. This tree's Parcel exposes
     * neither readArrayMap nor writeArrayMap, so the pair here is
     * writeValue/readValue -- and readValue answers a VAL_MAP with a plain
     * java.util.HashMap (Parcel.java: {@code case VAL_MAP: return readHashMap(loader)}),
     * never an ArrayMap. Casting the result straight to ArrayMap therefore throws
     * ClassCastException; read it as the general Map and copy. The copy is not a
     * workaround, it is the same independence a real cross-process unparcel gives
     * the caller.
     *
     * A stream that says "non-null" in the size int but carries a null map is
     * degenerate (this class never writes it); AOSP lands on an EMPTY ArrayMap in
     * that case, because readArrayMap returns early and leaves the map it was
     * handed untouched. Same answer here.
     */
    private static ArrayMap<String, Object> readArrayMapValue(Parcel source, int size) {
        final Object value = source.readValue(null);
        final ArrayMap<String, Object> out =
                new ArrayMap<String, Object>(Math.max(size, 0));
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            final Map<String, Object> map = (Map<String, Object>) value;
            out.putAll(map);
        }
        return out;
    }

    /** @hide AOSP's "same operation, different Uri" copy constructor, used by
     *  the provider transport when it rewrites a Uri for a cross-user call. */
    public ContentProviderOperation(ContentProviderOperation cpo, Uri withUri) {
        mType = cpo.mType;
        mUri = withUri;
        mMethod = cpo.mMethod;
        mArg = cpo.mArg;
        mValues = cpo.mValues;
        mExtras = cpo.mExtras;
        mSelection = cpo.mSelection;
        mSelectionArgs = cpo.mSelectionArgs;
        mExpectedCount = cpo.mExpectedCount;
        mYieldAllowed = cpo.mYieldAllowed;
        mExceptionAllowed = cpo.mExceptionAllowed;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mType);
        Uri.writeToParcel(dest, mUri);
        if (mMethod != null) {
            dest.writeInt(1);
            dest.writeString(mMethod);
        } else {
            dest.writeInt(0);
        }
        if (mArg != null) {
            dest.writeInt(1);
            dest.writeString(mArg);
        } else {
            dest.writeInt(0);
        }
        if (mValues != null) {
            dest.writeInt(mValues.size());
            dest.writeValue(mValues);
        } else {
            dest.writeInt(-1);
        }
        if (mExtras != null) {
            dest.writeInt(mExtras.size());
            dest.writeValue(mExtras);
        } else {
            dest.writeInt(-1);
        }
        if (mSelection != null) {
            dest.writeInt(1);
            dest.writeString(mSelection);
        } else {
            dest.writeInt(0);
        }
        // AOSP: dest.writeSparseArray(mSelectionArgs). Paired with
        // readSparseArray above -- writeValue would have prefixed a VAL_
        // type tag the typed reader does not consume.
        dest.writeSparseArray(mSelectionArgs);
        if (mExpectedCount != null) {
            dest.writeInt(1);
            dest.writeInt(mExpectedCount);
        } else {
            dest.writeInt(0);
        }
        dest.writeInt(mYieldAllowed ? 1 : 0);
        dest.writeInt(mExceptionAllowed ? 1 : 0);
    }

    /** Builder for an operation that will invoke ContentProvider.insert. */
    public static Builder newInsert(Uri uri) {
        return new Builder(TYPE_INSERT, uri);
    }

    /** Builder for an operation that will invoke ContentProvider.update. */
    public static Builder newUpdate(Uri uri) {
        return new Builder(TYPE_UPDATE, uri);
    }

    /** Builder for an operation that will invoke ContentProvider.delete. */
    public static Builder newDelete(Uri uri) {
        return new Builder(TYPE_DELETE, uri);
    }

    /** Builder for an operation that asserts the set of values supplied
     *  through Builder.withValues(ContentValues). */
    public static Builder newAssertQuery(Uri uri) {
        return new Builder(TYPE_ASSERT, uri);
    }

    /** Builder for an operation that will invoke ContentProvider.call. */
    public static Builder newCall(Uri uri, String method, String arg) {
        return new Builder(TYPE_CALL, uri, method, arg);
    }

    /** Gets the Uri for the target of the operation. */
    public Uri getUri() {
        return mUri;
    }

    /** True if the operation allows yielding the database to other
     *  transactions when the database is contended. */
    public boolean isYieldAllowed() {
        return mYieldAllowed;
    }

    /** True if this operation allows subsequent operations to continue even if
     *  it throws. When true, the exception is returned via
     *  ContentProviderResult.exception rather than aborting the batch. */
    public boolean isExceptionAllowed() {
        return mExceptionAllowed;
    }

    /** @hide exposed for unit tests */
    public int getType() {
        return mType;
    }

    public boolean isInsert() {
        return mType == TYPE_INSERT;
    }

    public boolean isDelete() {
        return mType == TYPE_DELETE;
    }

    public boolean isUpdate() {
        return mType == TYPE_UPDATE;
    }

    public boolean isAssertQuery() {
        return mType == TYPE_ASSERT;
    }

    public boolean isCall() {
        return mType == TYPE_CALL;
    }

    /** True for an insertion, deletion, or update. */
    public boolean isWriteOperation() {
        return mType == TYPE_DELETE || mType == TYPE_INSERT || mType == TYPE_UPDATE;
    }

    /** True for an assert query. */
    public boolean isReadOperation() {
        return mType == TYPE_ASSERT;
    }

    /**
     * Applies this operation using the given provider. backRefs supplies the
     * results of the operations already applied in this batch, which is what
     * withValueBackReference / withSelectionBackReference / withExtraBackReference
     * resolve against.
     *
     * @param provider the ContentProvider on which this batch is applied
     * @param backRefs results of earlier operations in the batch
     * @param numBackRefs how many entries of backRefs are valid
     * @throws OperationApplicationException if the insert fails or the number
     *         of rows affected did not match the expected count
     */
    public ContentProviderResult apply(ContentProvider provider,
            ContentProviderResult[] backRefs, int numBackRefs)
            throws OperationApplicationException {
        if (mExceptionAllowed) {
            try {
                return applyInternal(provider, backRefs, numBackRefs);
            } catch (Exception e) {
                return new ContentProviderResult(e);
            }
        } else {
            return applyInternal(provider, backRefs, numBackRefs);
        }
    }

    private ContentProviderResult applyInternal(ContentProvider provider,
            ContentProviderResult[] backRefs, int numBackRefs)
            throws OperationApplicationException {
        final ContentValues values = resolveValueBackReferences(backRefs, numBackRefs);

        // If the creator requested explicit selection or selectionArgs, it
        // should take precedence over similar values they defined in extras.
        Bundle extras = resolveExtrasBackReferences(backRefs, numBackRefs);
        if (mSelection != null) {
            extras = (extras != null) ? extras : new Bundle();
            extras.putString(ContentResolver.QUERY_ARG_SQL_SELECTION, mSelection);
        }
        if (mSelectionArgs != null) {
            extras = (extras != null) ? extras : new Bundle();
            extras.putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                    resolveSelectionArgsBackReferences(backRefs, numBackRefs));
        }

        if (mType == TYPE_INSERT) {
            final Uri newUri = provider.insert(mUri, values, extras);
            if (newUri != null) {
                return new ContentProviderResult(newUri);
            } else {
                throw new OperationApplicationException(
                        "Insert into " + mUri + " returned no result");
            }
        } else if (mType == TYPE_CALL) {
            final Bundle res = provider.call(mUri.getAuthority(), mMethod, mArg, extras);
            return new ContentProviderResult(res);
        }

        final int numRows;
        if (mType == TYPE_DELETE) {
            numRows = provider.delete(mUri, extras);
        } else if (mType == TYPE_UPDATE) {
            numRows = provider.update(mUri, values, extras);
        } else if (mType == TYPE_ASSERT) {
            // Assert that all rows match expected values.
            String[] projection = null;
            if (values != null) {
                // Build projection map from expected values.
                final ArrayList<String> projectionList = new ArrayList<String>();
                for (Map.Entry<String, Object> entry : values.valueSet()) {
                    projectionList.add(entry.getKey());
                }
                projection = projectionList.toArray(new String[projectionList.size()]);
            }
            final Cursor cursor = provider.query(mUri, projection, extras, null);
            try {
                numRows = cursor.getCount();
                if (projection != null) {
                    while (cursor.moveToNext()) {
                        for (int i = 0; i < projection.length; i++) {
                            final String cursorValue = cursor.getString(i);
                            final String expectedValue = values.getAsString(projection[i]);
                            if (!TextUtils.equals(cursorValue, expectedValue)) {
                                // Throw when expected values do not match.
                                throw new OperationApplicationException("Found value " + cursorValue
                                        + " when expected " + expectedValue + " for column "
                                        + projection[i]);
                            }
                        }
                    }
                }
            } finally {
                cursor.close();
            }
        } else {
            throw new IllegalStateException("bad type, " + mType);
        }

        if (mExpectedCount != null && mExpectedCount != numRows) {
            throw new OperationApplicationException(
                    "Expected " + mExpectedCount + " rows but actual " + numRows);
        }

        return new ContentProviderResult(numRows);
    }

    /**
     * Return the values for this operation after resolving any requested
     * back-references using the given results.
     */
    public ContentValues resolveValueBackReferences(
            ContentProviderResult[] backRefs, int numBackRefs) {
        if (mValues != null) {
            final ContentValues values = new ContentValues();
            for (int i = 0; i < mValues.size(); i++) {
                final Object value = mValues.valueAt(i);
                final Object resolved;
                if (value instanceof BackReference) {
                    resolved = ((BackReference) value).resolve(backRefs, numBackRefs);
                } else {
                    resolved = value;
                }
                putContentValue(values, mValues.keyAt(i), resolved);
            }
            return values;
        } else {
            return null;
        }
    }

    /**
     * Return the extras for this operation after resolving any requested
     * back-references using the given results.
     */
    public Bundle resolveExtrasBackReferences(
            ContentProviderResult[] backRefs, int numBackRefs) {
        if (mExtras != null) {
            final Bundle extras = new Bundle();
            for (int i = 0; i < mExtras.size(); i++) {
                final Object value = mExtras.valueAt(i);
                final Object resolved;
                if (value instanceof BackReference) {
                    resolved = ((BackReference) value).resolve(backRefs, numBackRefs);
                } else {
                    resolved = value;
                }
                putBundleValue(extras, mExtras.keyAt(i), resolved);
            }
            return extras;
        } else {
            return null;
        }
    }

    /**
     * Return the selection arguments for this operation after resolving any
     * requested back-references using the given results.
     */
    public String[] resolveSelectionArgsBackReferences(
            ContentProviderResult[] backRefs, int numBackRefs) {
        if (mSelectionArgs != null) {
            int max = -1;
            for (int i = 0; i < mSelectionArgs.size(); i++) {
                max = Math.max(max, mSelectionArgs.keyAt(i));
            }

            final String[] selectionArgs = new String[max + 1];
            for (int i = 0; i < mSelectionArgs.size(); i++) {
                final Object value = mSelectionArgs.valueAt(i);
                final Object resolved;
                if (value instanceof BackReference) {
                    resolved = ((BackReference) value).resolve(backRefs, numBackRefs);
                } else {
                    resolved = value;
                }
                selectionArgs[mSelectionArgs.keyAt(i)] = String.valueOf(resolved);
            }
            return selectionArgs;
        } else {
            return null;
        }
    }

    /** {@hide} */
    public static String typeToString(int type) {
        switch (type) {
            case TYPE_INSERT: return "insert";
            case TYPE_UPDATE: return "update";
            case TYPE_DELETE: return "delete";
            case TYPE_ASSERT: return "assert";
            case TYPE_CALL: return "call";
            default: return Integer.toString(type);
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ContentProviderOperation(");
        sb.append("type=" + typeToString(mType) + " ");
        if (mUri != null) {
            sb.append("uri=" + mUri + " ");
        }
        if (mValues != null) {
            sb.append("values=" + mValues + " ");
        }
        if (mSelection != null) {
            sb.append("selection=" + mSelection + " ");
        }
        if (mSelectionArgs != null) {
            sb.append("selectionArgs=" + mSelectionArgs + " ");
        }
        if (mExpectedCount != null) {
            sb.append("expectedCount=" + mExpectedCount + " ");
        }
        if (mYieldAllowed) {
            sb.append("yieldAllowed ");
        }
        if (mExceptionAllowed) {
            sb.append("exceptionAllowed ");
        }
        sb.deleteCharAt(sb.length() - 1);
        sb.append(")");
        return sb.toString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<ContentProviderOperation> CREATOR =
            new Creator<ContentProviderOperation>() {
        @Override
        public ContentProviderOperation createFromParcel(Parcel source) {
            return new ContentProviderOperation(source);
        }

        @Override
        public ContentProviderOperation[] newArray(int size) {
            return new ContentProviderOperation[size];
        }
    };

    // ---- @hide helpers reproduced from ContentValues / Bundle ------------
    //
    // AOSP calls ContentValues.putObject / isSupportedValue and
    // Bundle.putObject here; all three are @hide, so they are not on this
    // tree's public surface. The dispatch chains below are copied so the
    // accepted-type set and the IllegalArgumentException message match.

    /** AOSP ContentValues.putObject (ContentValues.java:222-246). */
    private static void putContentValue(ContentValues values, String key, Object value) {
        if (value == null) {
            values.putNull(key);
        } else if (value instanceof String) {
            values.put(key, (String) value);
        } else if (value instanceof Byte) {
            values.put(key, (Byte) value);
        } else if (value instanceof Short) {
            values.put(key, (Short) value);
        } else if (value instanceof Integer) {
            values.put(key, (Integer) value);
        } else if (value instanceof Long) {
            values.put(key, (Long) value);
        } else if (value instanceof Float) {
            values.put(key, (Float) value);
        } else if (value instanceof Double) {
            values.put(key, (Double) value);
        } else if (value instanceof Boolean) {
            values.put(key, (Boolean) value);
        } else if (value instanceof byte[]) {
            values.put(key, (byte[]) value);
        } else {
            throw new IllegalArgumentException("Unsupported type " + value.getClass());
        }
    }

    /** AOSP ContentValues.isSupportedValue (ContentValues.java:587-611). */
    private static boolean isSupportedValue(Object value) {
        return value == null
                || value instanceof String
                || value instanceof Byte
                || value instanceof Short
                || value instanceof Integer
                || value instanceof Long
                || value instanceof Float
                || value instanceof Double
                || value instanceof Boolean
                || value instanceof byte[];
    }

    /** AOSP Bundle.putObject (Bundle.java:440-486), same order -- the order is
     *  load-bearing, e.g. String is BOTH CharSequence and Serializable and AOSP
     *  routes it to putCharSequence.
     *
     *  Two AOSP branches are dropped because this tree's Bundle has no such
     *  setter: `value instanceof List` -> putParcelableList (an ArrayList still
     *  lands on putParcelableArrayList one branch earlier, so only a non-list
     *  List is affected) and the BaseBundle.putObject super-call fallback. A
     *  value that reaches neither now raises IllegalArgumentException naming
     *  the type, which is louder than AOSP's silent drop and is what a caller
     *  needs to see. */
    @SuppressWarnings("unchecked")
    private static void putBundleValue(Bundle bundle, String key, Object value) {
        if (value instanceof Byte) {
            bundle.putByte(key, (Byte) value);
        } else if (value instanceof Character) {
            bundle.putChar(key, (Character) value);
        } else if (value instanceof Short) {
            bundle.putShort(key, (Short) value);
        } else if (value instanceof Float) {
            bundle.putFloat(key, (Float) value);
        } else if (value instanceof CharSequence) {
            bundle.putCharSequence(key, (CharSequence) value);
        } else if (value instanceof Parcelable) {
            bundle.putParcelable(key, (Parcelable) value);
        } else if (value instanceof android.util.Size) {
            bundle.putSize(key, (android.util.Size) value);
        } else if (value instanceof android.util.SizeF) {
            bundle.putSizeF(key, (android.util.SizeF) value);
        } else if (value instanceof Parcelable[]) {
            bundle.putParcelableArray(key, (Parcelable[]) value);
        } else if (value instanceof ArrayList) {
            bundle.putParcelableArrayList(key, (ArrayList<? extends Parcelable>) value);
        } else if (value instanceof SparseArray) {
            bundle.putSparseParcelableArray(key, (SparseArray<? extends Parcelable>) value);
        } else if (value instanceof java.io.Serializable) {
            bundle.putSerializable(key, (java.io.Serializable) value);
        } else if (value instanceof byte[]) {
            bundle.putByteArray(key, (byte[]) value);
        } else if (value instanceof short[]) {
            bundle.putShortArray(key, (short[]) value);
        } else if (value instanceof char[]) {
            bundle.putCharArray(key, (char[]) value);
        } else if (value instanceof float[]) {
            bundle.putFloatArray(key, (float[]) value);
        } else if (value instanceof CharSequence[]) {
            bundle.putCharSequenceArray(key, (CharSequence[]) value);
        } else if (value instanceof Bundle) {
            bundle.putBundle(key, (Bundle) value);
        } else if (value instanceof android.os.IBinder) {
            bundle.putBinder(key, (android.os.IBinder) value);
        } else if (value == null) {
            bundle.putString(key, null);
        } else {
            throw new IllegalArgumentException("Unsupported type " + value.getClass());
        }
    }

    /**
     * A reference to a value produced by an EARLIER operation in the same
     * batch. fromIndex selects which result; fromKey, when non-null, selects a
     * key out of that result's extras Bundle (an operation built with newCall
     * returns extras rather than a Uri or a count).
     *
     * {@hide}
     */
    public static class BackReference implements Parcelable {
        private final int fromIndex;
        private final String fromKey;

        private BackReference(int fromIndex, String fromKey) {
            this.fromIndex = fromIndex;
            this.fromKey = fromKey;
        }

        public BackReference(Parcel src) {
            this.fromIndex = src.readInt();
            if (src.readInt() != 0) {
                this.fromKey = src.readString();
            } else {
                this.fromKey = null;
            }
        }

        public Object resolve(ContentProviderResult[] backRefs, int numBackRefs) {
            if (fromIndex >= numBackRefs) {
                Log.e(TAG, this.toString());
                throw new ArrayIndexOutOfBoundsException("asked for back ref " + fromIndex
                        + " but there are only " + numBackRefs + " back refs");
            }
            ContentProviderResult backRef = backRefs[fromIndex];
            Object backRefValue;
            if (backRef.extras != null) {
                backRefValue = backRef.extras.get(fromKey);
            } else if (backRef.uri != null) {
                // The insert case: the referenced row's id is the last path
                // segment of the Uri the provider returned.
                backRefValue = ContentUris.parseId(backRef.uri);
            } else {
                backRefValue = (long) backRef.count;
            }
            return backRefValue;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(fromIndex);
            if (fromKey != null) {
                dest.writeInt(1);
                dest.writeString(fromKey);
            } else {
                dest.writeInt(0);
            }
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<BackReference> CREATOR = new Creator<BackReference>() {
            @Override
            public BackReference createFromParcel(Parcel source) {
                return new BackReference(source);
            }

            @Override
            public BackReference[] newArray(int size) {
                return new BackReference[size];
            }
        };
    }

    /**
     * Adds parameters to a ContentProviderOperation. Created by
     * ContentProviderOperation.newInsert / newUpdate / newDelete /
     * newAssertQuery / newCall; the withXXX methods add parameters; build()
     * produces the operation.
     */
    public static class Builder {
        private final int mType;
        private final Uri mUri;
        private final String mMethod;
        private final String mArg;
        private ArrayMap<String, Object> mValues;
        private ArrayMap<String, Object> mExtras;
        private String mSelection;
        private SparseArray<Object> mSelectionArgs;
        private Integer mExpectedCount;
        private boolean mYieldAllowed;
        private boolean mExceptionAllowed;

        private Builder(int type, Uri uri) {
            this(type, uri, null, null);
        }

        private Builder(int type, Uri uri, String method, String arg) {
            mType = type;
            mUri = Objects.requireNonNull(uri);
            mMethod = method;
            mArg = arg;
        }

        /** Create a ContentProviderOperation from this Builder. */
        public ContentProviderOperation build() {
            if (mType == TYPE_UPDATE) {
                if ((mValues == null || mValues.isEmpty())) {
                    throw new IllegalArgumentException("Empty values");
                }
            }
            if (mType == TYPE_ASSERT) {
                if ((mValues == null || mValues.isEmpty())
                        && (mExpectedCount == null)) {
                    throw new IllegalArgumentException("Empty values");
                }
            }
            return new ContentProviderOperation(this);
        }

        private void ensureValues() {
            if (mValues == null) {
                mValues = new ArrayMap<String, Object>();
            }
        }

        private void ensureExtras() {
            if (mExtras == null) {
                mExtras = new ArrayMap<String, Object>();
            }
        }

        private void ensureSelectionArgs() {
            if (mSelectionArgs == null) {
                mSelectionArgs = new SparseArray<Object>();
            }
        }

        // A plain value never overwrites an already-registered back reference:
        // withValues(cv) after withValueBackReference(k, i) must leave the back
        // reference in place, which is what "will not replace any
        // back-reference requests" in the AOSP javadoc means.
        private void setValue(String key, Object value) {
            ensureValues();
            final boolean oldReference = mValues.get(key) instanceof BackReference;
            final boolean newReference = value instanceof BackReference;
            if (!oldReference || newReference) {
                mValues.put(key, value);
            }
        }

        private void setExtra(String key, Object value) {
            ensureExtras();
            final boolean oldReference = mExtras.get(key) instanceof BackReference;
            final boolean newReference = value instanceof BackReference;
            if (!oldReference || newReference) {
                mExtras.put(key, value);
            }
        }

        private void setSelectionArg(int index, Object value) {
            ensureSelectionArgs();
            final boolean oldReference = mSelectionArgs.get(index) instanceof BackReference;
            final boolean newReference = value instanceof BackReference;
            if (!oldReference || newReference) {
                mSelectionArgs.put(index, value);
            }
        }

        /**
         * Configure the values to use for this operation. Replaces any
         * previously defined values for the contained keys, but does not
         * replace any back-reference requests.
         */
        public Builder withValues(ContentValues values) {
            assertValuesAllowed();
            ensureValues();
            // AOSP reads values.getValues() (an @hide raw ArrayMap accessor);
            // valueSet() is the public equivalent over the same entries.
            for (Map.Entry<String, Object> entry : values.valueSet()) {
                setValue(entry.getKey(), entry.getValue());
            }
            return this;
        }

        /** Configure one value, replacing any previously defined value for this key. */
        public Builder withValue(String key, Object value) {
            assertValuesAllowed();
            if (!isSupportedValue(value)) {
                throw new IllegalArgumentException("bad value type: " + value.getClass().getName());
            }
            setValue(key, value);
            return this;
        }

        /**
         * Configure the given values to be dynamically overwritten using the
         * result of a previous operation: each map value is the INDEX of the
         * earlier operation whose result supplies the real value.
         */
        public Builder withValueBackReferences(ContentValues backReferences) {
            assertValuesAllowed();
            for (Map.Entry<String, Object> entry : backReferences.valueSet()) {
                setValue(entry.getKey(),
                        new BackReference(((Integer) entry.getValue()).intValue(), null));
            }
            return this;
        }

        /**
         * Configure one value to be dynamically overwritten using the result of
         * a previous operation.
         *
         * @param key the key indicating which value to configure
         * @param fromIndex index of the earlier ContentProviderResult to read
         */
        public Builder withValueBackReference(String key, int fromIndex) {
            assertValuesAllowed();
            setValue(key, new BackReference(fromIndex, null));
            return this;
        }

        /**
         * Configure one value to be dynamically overwritten using a key out of
         * an earlier result's extras Bundle.
         */
        public Builder withValueBackReference(String key, int fromIndex, String fromKey) {
            assertValuesAllowed();
            setValue(key, new BackReference(fromIndex, fromKey));
            return this;
        }

        /**
         * Configure the extras to use for this operation. Replaces any
         * previously defined values for the contained keys, but does not
         * replace any back-reference requests.
         */
        public Builder withExtras(Bundle extras) {
            assertExtrasAllowed();
            ensureExtras();
            for (String key : extras.keySet()) {
                setExtra(key, extras.get(key));
            }
            return this;
        }

        /** Configure one extra, replacing any previously defined extra for this key. */
        public Builder withExtra(String key, Object value) {
            assertExtrasAllowed();
            setExtra(key, value);
            return this;
        }

        /** Configure one extra to be dynamically overwritten using the result
         *  of a previous operation. */
        public Builder withExtraBackReference(String key, int fromIndex) {
            assertExtrasAllowed();
            setExtra(key, new BackReference(fromIndex, null));
            return this;
        }

        /** Configure one extra to be dynamically overwritten using a key out of
         *  an earlier result's extras Bundle. */
        public Builder withExtraBackReference(String key, int fromIndex, String fromKey) {
            assertExtrasAllowed();
            setExtra(key, new BackReference(fromIndex, fromKey));
            return this;
        }

        /**
         * Configure the selection and selection arguments. Replaces any
         * previously defined selection and selection arguments, but does not
         * replace any back-reference requests. An occurrence of `?` in the
         * selection is replaced with the corresponding selection argument when
         * the operation is executed.
         */
        public Builder withSelection(String selection, String[] selectionArgs) {
            assertSelectionAllowed();
            mSelection = selection;
            if (selectionArgs != null) {
                ensureSelectionArgs();
                for (int i = 0; i < selectionArgs.length; i++) {
                    setSelectionArg(i, selectionArgs[i]);
                }
            }
            return this;
        }

        /**
         * Configure one selection argument to be dynamically overwritten using
         * the result of a previous operation.
         *
         * @param index which selection argument to configure
         * @param fromIndex index of the earlier ContentProviderResult to read
         */
        public Builder withSelectionBackReference(int index, int fromIndex) {
            assertSelectionAllowed();
            setSelectionArg(index, new BackReference(fromIndex, null));
            return this;
        }

        /**
         * Configure one selection argument to be dynamically overwritten using
         * a key out of an earlier result's extras Bundle.
         */
        public Builder withSelectionBackReference(int index, int fromIndex, String fromKey) {
            assertSelectionAllowed();
            setSelectionArg(index, new BackReference(fromIndex, fromKey));
            return this;
        }

        /**
         * If set and the number of rows affected by this operation does not
         * match this count, OperationApplicationException is thrown. Only
         * update, delete, and assert may have expected counts.
         */
        public Builder withExpectedCount(int count) {
            if (mType != TYPE_UPDATE && mType != TYPE_DELETE && mType != TYPE_ASSERT) {
                throw new IllegalArgumentException(
                        "only updates, deletes, and asserts can have expected counts");
            }
            mExpectedCount = count;
            return this;
        }

        /** If true the operation allows yielding the database to other
         *  transactions when the database is contended. */
        public Builder withYieldAllowed(boolean yieldAllowed) {
            mYieldAllowed = yieldAllowed;
            return this;
        }

        /** If true, subsequent operations continue even if this one throws;
         *  the exception is returned via ContentProviderResult.exception. */
        public Builder withExceptionAllowed(boolean exceptionAllowed) {
            mExceptionAllowed = exceptionAllowed;
            return this;
        }

        /** {@hide} */
        public Builder withFailureAllowed(boolean failureAllowed) {
            return withExceptionAllowed(failureAllowed);
        }

        private void assertValuesAllowed() {
            switch (mType) {
                case TYPE_INSERT:
                case TYPE_UPDATE:
                case TYPE_ASSERT:
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Values not supported for " + typeToString(mType));
            }
        }

        private void assertSelectionAllowed() {
            switch (mType) {
                case TYPE_UPDATE:
                case TYPE_DELETE:
                case TYPE_ASSERT:
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Selection not supported for " + typeToString(mType));
            }
        }

        private void assertExtrasAllowed() {
            switch (mType) {
                case TYPE_INSERT:
                case TYPE_UPDATE:
                case TYPE_DELETE:
                case TYPE_ASSERT:
                case TYPE_CALL:
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Extras not supported for " + typeToString(mType));
            }
        }
    }
}
