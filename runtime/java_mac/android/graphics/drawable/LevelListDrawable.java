// android.graphics.drawable.LevelListDrawable -- AOSP DrawableContainer
// that maps a [minLevel, maxLevel] range to each child and shows the
// child whose range contains the current level. AOSP:
//   https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/graphics/java/android/graphics/drawable/LevelListDrawable.java
// Apps use it for e.g. battery / signal-strength icons that swap art as
// setLevel() climbs. addLevel registers a child; onLevelChange picks it.
package android.graphics.drawable;

import java.util.ArrayList;

public class LevelListDrawable extends DrawableContainer {
    private final ArrayList<int[]> mLows = new ArrayList<>(); // [low, high] per child slot
    private final ArrayList<Integer> mIndices = new ArrayList<>();

    public LevelListDrawable() {}

    /** Register a drawable for the level range [low, high] (inclusive). */
    public void addLevel(int low, int high, Drawable drawable) {
        int idx = addChild(drawable);
        mLows.add(new int[]{low, high});
        mIndices.add(idx);
        // If this range contains the current level (or no current yet),
        // select it so the container shows something immediately.
        if (getCurrent() == null) onLevelChange(getLevel());
    }

    @Override public boolean onLevelChange(int level) {
        int idx = indexForLevel(level);
        if (idx >= 0) {
            return selectDrawable(idx);
        }
        return super.onLevelChange(level);
    }

    private int indexForLevel(int level) {
        for (int i = 0; i < mLows.size(); i++) {
            int[] range = mLows.get(i);
            if (level >= range[0] && level <= range[1]) {
                return mIndices.get(i);
            }
        }
        return -1;
    }
}
