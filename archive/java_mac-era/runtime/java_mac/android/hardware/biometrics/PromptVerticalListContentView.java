// android.hardware.biometrics.PromptVerticalListContentView -- a
// BiometricPrompt content area laid out as a vertical list of items, with an
// optional description above it.
//
// Mirrors frameworks/base/core/java/android/hardware/biometrics/PromptVerticalListContentView.java
// (android16-release), including the exact limits (20 items, 640 characters per
// item, 225 characters of description) and, importantly, WHICH limit throws:
// AOSP only throws for too many ITEMS. Exceeding the per-item or description
// character limit is merely logged, so an over-long string must NOT throw here
// -- an app that does it on a device gets a warning and a truncated dialog, not
// a crash, and it must not get a crash here either.
package android.hardware.biometrics;

import java.util.ArrayList;
import java.util.List;

public final class PromptVerticalListContentView
        implements android.os.Parcelable, PromptContentView {

    static final int MAX_ITEM_NUMBER = 20;
    static final int MAX_EACH_ITEM_CHARACTER_NUMBER = 640;
    static final int MAX_DESCRIPTION_CHARACTER_NUMBER = 225;

    private static final String TAG = "PromptVerticalListContentView";

    private final List<PromptContentItem> mContentList;
    private final String mDescription;

    private PromptVerticalListContentView(List<PromptContentItem> contentList,
            String description) {
        mContentList = contentList;
        mDescription = description;
    }

    public static int getMaxItemCount() { return MAX_ITEM_NUMBER; }

    public static int getMaxEachItemCharacterNumber() { return MAX_EACH_ITEM_CHARACTER_NUMBER; }

    public String getDescription() { return mDescription; }

    /** AOSP returns a COPY, so a caller cannot mutate the built view. */
    public List<PromptContentItem> getListItems() { return new ArrayList<>(mContentList); }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeString(mDescription);
        dest.writeInt(mContentList.size());
        for (PromptContentItem item : mContentList) {
            // Each concrete item is itself Parcelable; write it through the
            // generic Parcelable path so the reader can reconstruct the right
            // subtype from its class name.
            dest.writeParcelable((android.os.Parcelable) item, flags);
        }
    }

    public static final Creator<PromptVerticalListContentView> CREATOR =
            new Creator<PromptVerticalListContentView>() {
        @Override public PromptVerticalListContentView createFromParcel(android.os.Parcel in) {
            String description = in.readString();
            int n = in.readInt();
            List<PromptContentItem> items = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                Object o = in.readParcelable(PromptContentItem.class.getClassLoader());
                if (o instanceof PromptContentItem) items.add((PromptContentItem) o);
            }
            return new PromptVerticalListContentView(items, description);
        }
        @Override public PromptVerticalListContentView[] newArray(int size) {
            return new PromptVerticalListContentView[size];
        }
    };

    public static final class Builder {
        private final List<PromptContentItem> mContentList = new ArrayList<>();
        private String mDescription;

        public Builder() {}

        public Builder setDescription(String description) {
            if (description.length() > MAX_DESCRIPTION_CHARACTER_NUMBER) {
                // AOSP logs and continues -- it does NOT throw. Kept.
                System.err.println("[" + TAG + "] The character number of description exceeds "
                        + MAX_DESCRIPTION_CHARACTER_NUMBER);
            }
            mDescription = description;
            return this;
        }

        public Builder addListItem(PromptContentItem listItem) {
            mContentList.add(listItem);
            checkItemLimits(listItem);
            return this;
        }

        public Builder addListItem(PromptContentItem listItem, int index) {
            mContentList.add(index, listItem);
            checkItemLimits(listItem);
            return this;
        }

        /** AOSP's order matters: the item is added FIRST, then the count is
         *  checked, so the throwing call has already mutated the builder. */
        private void checkItemLimits(PromptContentItem listItem) {
            if (doesListItemExceedsCharLimit(listItem)) {
                System.err.println("[" + TAG + "] The character number of list item exceeds "
                        + MAX_EACH_ITEM_CHARACTER_NUMBER);
            }
            if (mContentList.size() > MAX_ITEM_NUMBER) {
                throw new IllegalArgumentException(
                        "The number of list items exceeds " + MAX_ITEM_NUMBER);
            }
        }

        private boolean doesListItemExceedsCharLimit(PromptContentItem listItem) {
            if (listItem instanceof PromptContentItemPlainText) {
                return ((PromptContentItemPlainText) listItem).getText().length()
                        > MAX_EACH_ITEM_CHARACTER_NUMBER;
            } else if (listItem instanceof PromptContentItemBulletedText) {
                return ((PromptContentItemBulletedText) listItem).getText().length()
                        > MAX_EACH_ITEM_CHARACTER_NUMBER;
            }
            return false;
        }

        public PromptVerticalListContentView build() {
            return new PromptVerticalListContentView(mContentList, mDescription);
        }
    }
}
