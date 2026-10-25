// android.widget.AlphabetIndexer -- a SectionIndexer over a Cursor column,
// mapping each alphabet letter to the first row whose sort key starts with it.
// Was Absent. AOSP:
//   frameworks/base/core/java/android/widget/AlphabetIndexer.java
//   public class AlphabetIndexer extends DataSetObserver implements SectionIndexer
package android.widget;

import android.database.Cursor;
import android.database.DataSetObserver;

import java.util.HashMap;

public class AlphabetIndexer extends DataSetObserver implements SectionIndexer {
    protected Cursor mDataCursor;
    protected int mColumnIndex;
    protected CharSequence mAlphabet;
    private final String mAlphabetString;
    private final int mAlphabetLength;
    private final String[] mSectionsArr;
    private final java.util.Map<Integer, Integer> mAlphaMap = new HashMap<Integer, Integer>();
    private java.text.Collator mCollator;

    public AlphabetIndexer(Cursor cursor, int sortedColumnIndex, CharSequence alphabet) {
        mDataCursor = cursor;
        mColumnIndex = sortedColumnIndex;
        mAlphabet = alphabet;
        mAlphabetString = alphabet.toString();
        mAlphabetLength = alphabet.length();
        mSectionsArr = new String[mAlphabetLength];
        for (int i = 0; i < mAlphabetLength; i++) {
            mSectionsArr[i] = String.valueOf(mAlphabetString.charAt(i));
        }
        mCollator = java.text.Collator.getInstance();
        mCollator.setStrength(java.text.Collator.PRIMARY);
    }

    @Override public Object[] getSections() { return mSectionsArr; }

    public void setCursor(Cursor cursor) {
        if (mDataCursor != null) mDataCursor.unregisterDataSetObserver(this);
        mDataCursor = cursor;
        if (cursor != null) cursor.registerDataSetObserver(this);
        mAlphaMap.clear();
    }

    protected int compare(String word, String letter) {
        final String firstLetter = word.length() == 0 ? " " : word.substring(0, 1);
        return mCollator.compare(firstLetter, letter);
    }

    @Override public int getPositionForSection(int sectionIndex) {
        if (mDataCursor == null) return 0;
        if (sectionIndex <= 0) return 0;
        if (sectionIndex >= mAlphabetLength) sectionIndex = mAlphabetLength - 1;

        final int count = mDataCursor.getCount();
        final int savedCursorPos = mDataCursor.getPosition();
        char letter = mAlphabetString.charAt(sectionIndex);
        String targetLetter = String.valueOf(letter);

        Integer cached = mAlphaMap.get((int) letter);
        int start = 0, end = count;
        if (cached != null) {
            if (cached >= 0) { return cached; }
        }
        // Linear scan (simple + correct; AOSP binary-searches the cursor).
        for (int i = start; i < end; i++) {
            mDataCursor.moveToPosition(i);
            String curName = mDataCursor.getString(mColumnIndex);
            if (curName == null) continue;
            if (compare(curName, targetLetter) >= 0) {
                mDataCursor.moveToPosition(savedCursorPos);
                mAlphaMap.put((int) letter, i);
                return i;
            }
        }
        mDataCursor.moveToPosition(savedCursorPos);
        return count;
    }

    @Override public int getSectionForPosition(int position) {
        if (mDataCursor == null) return 0;
        final int savedCursorPos = mDataCursor.getPosition();
        mDataCursor.moveToPosition(position);
        String curName = mDataCursor.getString(mColumnIndex);
        mDataCursor.moveToPosition(savedCursorPos);
        if (curName == null) return 0;
        for (int i = 0; i < mAlphabetLength; i++) {
            char letter = mAlphabetString.charAt(i);
            if (compare(curName, String.valueOf(letter)) == 0) return i;
        }
        return 0;
    }

    @Override public void onChanged() { super.onChanged(); mAlphaMap.clear(); }
    @Override public void onInvalidated() { super.onInvalidated(); mAlphaMap.clear(); }
}
