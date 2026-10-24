// com.android.internal.widget.PreferenceImageView -- the icon slot of the
// platform Preference row (core/res/res/layout/preference_material.xml).
// Mirrors AOSP frameworks/base core/java/com/android/internal/widget/
// PreferenceImageView.java (aosp main) exactly: an ImageView whose onMeasure
// clamps an AT_MOST / UNSPECIFIED spec to maxWidth / maxHeight, because
// ImageView itself only honours them when adjustViewBounds is set. Without the
// class the inflater substitutes a FrameLayout, and Preference.onBindView's
// `(ImageView) view.findViewById(com.android.internal.R.id.icon)` would throw
// ClassCastException -- the one blocker to inflating preference_material for
// Preference rows (docs/platform-layouts-axml_2026-09-26.md).
package com.android.internal.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageView;

public class PreferenceImageView extends ImageView {

    public PreferenceImageView(Context context) {
        this(context, null);
    }

    public PreferenceImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PreferenceImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public PreferenceImageView(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        if (widthMode == MeasureSpec.AT_MOST || widthMode == MeasureSpec.UNSPECIFIED) {
            final int widthSize = MeasureSpec.getSize(widthMeasureSpec);
            final int maxWidth = getMaxWidth();
            if (maxWidth != Integer.MAX_VALUE
                    && (maxWidth < widthSize || widthMode == MeasureSpec.UNSPECIFIED)) {
                widthMeasureSpec = MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST);
            }
        }

        final int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        if (heightMode == MeasureSpec.AT_MOST || heightMode == MeasureSpec.UNSPECIFIED) {
            final int heightSize = MeasureSpec.getSize(heightMeasureSpec);
            final int maxHeight = getMaxHeight();
            if (maxHeight != Integer.MAX_VALUE
                    && (maxHeight < heightSize || heightMode == MeasureSpec.UNSPECIFIED)) {
                heightMeasureSpec = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST);
            }
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
