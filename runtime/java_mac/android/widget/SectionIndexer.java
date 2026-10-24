// android.widget.SectionIndexer -- lets a list adapter expose alphabetical
// sections for fast-scroll. Was Absent. AOSP:
//   frameworks/base/core/java/android/widget/SectionIndexer.java
package android.widget;

public interface SectionIndexer {
    Object[] getSections();
    int getPositionForSection(int sectionIndex);
    int getSectionForPosition(int position);
}
