// android.content.Entity -- a ContentValues row plus the sub-rows that belong
// to it, the unit an EntityIterator yields from a ContentProvider.
//
// AOSP ref: frameworks/base/core/java/android/content/Entity.java
// (android16-release). Ported verbatim.
package android.content;

import android.net.Uri;

import java.util.ArrayList;

public final class Entity {
    private final ContentValues mValues;
    private final ArrayList<NamedContentValues> mSubValues;

    public Entity(ContentValues values) {
        mValues = values;
        mSubValues = new ArrayList<NamedContentValues>();
    }

    public ContentValues getEntityValues() { return mValues; }

    public ArrayList<NamedContentValues> getSubValues() { return mSubValues; }

    public void addSubValue(Uri uri, ContentValues values) {
        mSubValues.add(new Entity.NamedContentValues(uri, values));
    }

    public static class NamedContentValues {
        public final Uri uri;
        public final ContentValues values;

        public NamedContentValues(Uri uri, ContentValues values) {
            this.uri = uri;
            this.values = values;
        }
    }

    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("Entity: ").append(getEntityValues());
        for (Entity.NamedContentValues namedValue : getSubValues()) {
            sb.append("\n  ").append(namedValue.uri);
            sb.append("\n  -> ").append(namedValue.values);
        }
        return sb.toString();
    }
}
