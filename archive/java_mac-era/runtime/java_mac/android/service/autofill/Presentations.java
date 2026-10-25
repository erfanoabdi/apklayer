package android.service.autofill;

import android.widget.RemoteViews;

/**
 * API 33 bundle of the different ways one Dataset (or a FillResponse's
 * authentication entry) can be presented: the dropdown menu row, the fill
 * dialog, the inline keyboard strip, and the inline tooltip.
 *
 * Pure value object; the platform reads the four getters. See the package note
 * on AutofillService for why nothing here is stubbed even though no service is
 * ever bound.
 *
 * Source: frameworks/base/core/java/android/service/autofill/Presentations.java
 */
public final class Presentations {

    private final RemoteViews mMenuPresentation;
    private final InlinePresentation mInlinePresentation;
    private final InlinePresentation mInlineTooltipPresentation;
    private final RemoteViews mDialogPresentation;

    private Presentations(RemoteViews menu, InlinePresentation inline,
            InlinePresentation tooltip, RemoteViews dialog) {
        mMenuPresentation = menu;
        mInlinePresentation = inline;
        mInlineTooltipPresentation = tooltip;
        mDialogPresentation = dialog;
    }

    public RemoteViews getMenuPresentation() { return mMenuPresentation; }

    public InlinePresentation getInlinePresentation() { return mInlinePresentation; }

    public InlinePresentation getInlineTooltipPresentation() {
        return mInlineTooltipPresentation;
    }

    public RemoteViews getDialogPresentation() { return mDialogPresentation; }

    public static final class Builder {
        private RemoteViews mMenuPresentation;
        private InlinePresentation mInlinePresentation;
        private InlinePresentation mInlineTooltipPresentation;
        private RemoteViews mDialogPresentation;

        public Builder() { }

        public Builder setMenuPresentation(RemoteViews menuPresentation) {
            if (menuPresentation == null) throw new NullPointerException("menuPresentation");
            mMenuPresentation = menuPresentation;
            return this;
        }

        public Builder setInlinePresentation(InlinePresentation inlinePresentation) {
            if (inlinePresentation == null) {
                throw new NullPointerException("inlinePresentation");
            }
            mInlinePresentation = inlinePresentation;
            return this;
        }

        public Builder setInlineTooltipPresentation(InlinePresentation tooltip) {
            if (tooltip == null) throw new NullPointerException("inlineTooltipPresentation");
            mInlineTooltipPresentation = tooltip;
            return this;
        }

        public Builder setDialogPresentation(RemoteViews dialogPresentation) {
            if (dialogPresentation == null) {
                throw new NullPointerException("dialogPresentation");
            }
            mDialogPresentation = dialogPresentation;
            return this;
        }

        public Presentations build() {
            return new Presentations(mMenuPresentation, mInlinePresentation,
                    mInlineTooltipPresentation, mDialogPresentation);
        }
    }
}
