package com.android.billingclient.api;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

// com.android.billingclient.api.InAppMessageParams -- the request passed to
// BillingClient.showInAppMessages. It records which in-app message categories
// the app wants Play to surface; on a de-Googled device no Play in-app message
// exists, so showInAppMessages returns NO_ACTION_NEEDED with no UI shown.
public final class InAppMessageParams {

    // com.android.billingclient.api.InAppMessageParams.InAppMessageCategoryId --
    // the categories an app can request.
    public @interface InAppMessageCategoryId {
        int UNKNOWN_IN_APP_MESSAGE_CATEGORY_ID = 0;
        int TRANSACTIONAL = 2;
    }

    private final Set<Integer> categoryIds;

    private InAppMessageParams(Set<Integer> categoryIds) {
        this.categoryIds = categoryIds;
    }

    public Set<Integer> getCategoryIds() { return categoryIds; }

    public static Builder newBuilder() { return new Builder(); }

    public static final class Builder {
        private final Set<Integer> categoryIds = new HashSet<Integer>();

        Builder() {}

        public Builder addInAppMessageCategoryToShow(int inAppMessageCategoryId) {
            this.categoryIds.add(Integer.valueOf(inAppMessageCategoryId));
            return this;
        }

        public Builder addAllInAppMessageCategoriesToShow() {
            this.categoryIds.add(Integer.valueOf(
                    InAppMessageCategoryId.TRANSACTIONAL));
            return this;
        }

        public InAppMessageParams build() {
            return new InAppMessageParams(
                    Collections.unmodifiableSet(new HashSet<Integer>(categoryIds)));
        }
    }
}
