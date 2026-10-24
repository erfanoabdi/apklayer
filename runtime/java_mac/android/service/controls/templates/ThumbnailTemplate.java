package android.service.controls.templates;

import android.graphics.drawable.Icon;

/**
 * A control whose state is shown as an image -- a camera snapshot, say.
 *
 * Source: frameworks/base/core/java/android/service/controls/templates/ThumbnailTemplate.java
 */
public final class ThumbnailTemplate extends ControlTemplate {

    private final boolean mActive;
    private final Icon mThumbnail;
    private final CharSequence mContentDescription;

    public ThumbnailTemplate(String templateId, boolean active, Icon thumbnail,
            CharSequence contentDescription) {
        super(templateId);
        if (thumbnail == null) throw new NullPointerException("thumbnail");
        if (contentDescription == null) throw new NullPointerException("contentDescription");
        mActive = active;
        mThumbnail = thumbnail;
        mContentDescription = contentDescription;
    }

    public boolean isActive() { return mActive; }

    public Icon getThumbnail() { return mThumbnail; }

    public CharSequence getContentDescription() { return mContentDescription; }

    @Override public int getTemplateType() { return TYPE_THUMBNAIL; }
}
