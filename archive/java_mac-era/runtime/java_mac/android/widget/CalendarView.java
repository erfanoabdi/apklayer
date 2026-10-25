// android.widget.CalendarView -- a widget showing a month calendar for date
// selection. Was Absent. The selected-date model, min/max date, week config,
// and OnDateChangeListener are faithful; the month grid is not painted. AOSP:
//   frameworks/base/core/java/android/widget/CalendarView.java
//   public class CalendarView extends FrameLayout
package android.widget;

import android.content.Context;

import java.util.Calendar;

public class CalendarView extends FrameLayout {
    public interface OnDateChangeListener {
        void onSelectedDayChange(CalendarView view, int year, int month, int dayOfMonth);
    }

    private long mSelectedDate = System.currentTimeMillis();
    private long mMinDate;
    private long mMaxDate;
    private int mFirstDayOfWeek = Calendar.getInstance().getFirstDayOfWeek();
    private OnDateChangeListener mListener;

    public CalendarView(Context context) { super(context); init(); }
    public CalendarView(Context context, android.util.AttributeSet attrs) {
        super(context, attrs); init();
    }
    /** AOSP API 21 four-argument constructor. AOSP's three-argument form is
     *  exactly this(context, attrs, defStyleAttr, 0), so delegating the other
     *  way is identical for defStyleRes == 0; a non-zero defStyleRes is
     *  DROPPED here, as in the existing FrameLayout/Button four-argument forms
     *  (TextView, ProgressBar and their subclasses honour it). Without it an
     *  app subclass calling the four-argument
     *  super() dies with NoSuchMethodError; see debug/ctorsdk_gate.sh. */
    public CalendarView(Context context, android.util.AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        this(context, attrs, defStyleAttr);
    }
    public CalendarView(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr); init();
    }

    private void init() {
        Calendar c = Calendar.getInstance();
        c.set(1900, Calendar.JANUARY, 1);
        mMinDate = c.getTimeInMillis();
        c.set(2100, Calendar.DECEMBER, 31);
        mMaxDate = c.getTimeInMillis();
    }

    public void setDate(long date) { setDate(date, false, false); }
    public void setDate(long date, boolean animate, boolean center) {
        mSelectedDate = date;
        if (mListener != null) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(date);
            mListener.onSelectedDayChange(this, c.get(Calendar.YEAR),
                    c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH));
        }
    }
    public long getDate() { return mSelectedDate; }

    public long getMinDate() { return mMinDate; }
    public void setMinDate(long minDate) { mMinDate = minDate; }
    public long getMaxDate() { return mMaxDate; }
    public void setMaxDate(long maxDate) { mMaxDate = maxDate; }

    public int getFirstDayOfWeek() { return mFirstDayOfWeek; }
    public void setFirstDayOfWeek(int firstDayOfWeek) { mFirstDayOfWeek = firstDayOfWeek; }

    public void setOnDateChangeListener(OnDateChangeListener listener) {
        mListener = listener;
    }

    // AOSP CalendarView.setShowWeekNumber/getShowWeekNumber,
    // getDateTextAppearance/setDateTextAppearance,
    // getWeekDayTextAppearance/setWeekDayTextAppearance were all `{}` / a
    // hardcoded constant with no backing field -- since the month grid isn't
    // painted here (see header comment), these are pure data-model state like
    // getMinDate/getFirstDayOfWeek above, so the fix is the same
    // accessor-round-trip pattern already used by the rest of this class.
    // Default true per AOSP CalendarViewLegacyDelegate.DEFAULT_SHOW_WEEK_NUMBER
    // (frameworks/base core/java/android/widget/CalendarViewLegacyDelegate.java ~line 50).
    private boolean mShowWeekNumber = true;
    public void setShowWeekNumber(boolean showWeekNumber) { mShowWeekNumber = showWeekNumber; }
    public boolean getShowWeekNumber() { return mShowWeekNumber; }
    private int mDateTextAppearance;
    public int getDateTextAppearance() { return mDateTextAppearance; }
    public void setDateTextAppearance(int resourceId) { mDateTextAppearance = resourceId; }
    private int mWeekDayTextAppearance;
    public int getWeekDayTextAppearance() { return mWeekDayTextAppearance; }
    public void setWeekDayTextAppearance(int resourceId) { mWeekDayTextAppearance = resourceId; }

    // ==================================================================
    // API-36 surface completion (apidiff: android.widget.CalendarView had 15
    // public members missing -- the whole deprecated legacy-appearance family).
    //
    // AOSP routes all of these through a delegate, and the two delegates
    // disagree: CalendarViewMaterialDelegate makes every one a documented
    // no-op returning 0/null, while CalendarViewLegacyDelegate stores and
    // honours them. The delegate is chosen from R.styleable.
    // CalendarView_calendarViewMode, whose CODE default is MODE_HOLO
    // (CalendarView.java:117) even though the Material platform style sets
    // modeMaterial. This CalendarView resolves no styled attributes and so
    // takes no theme's word for the mode, which puts it on AOSP's code
    // default -- the legacy delegate -- so these store and round-trip rather
    // than silently discarding. Nothing here is painted yet (this widget has
    // no week grid), so the values are state only, exactly as the Material
    // delegate's would be; the difference is only what the getter answers.
    // AOSP: core/java/android/widget/CalendarViewLegacyDelegate.java.
    // ==================================================================

    // CalendarViewLegacyDelegate.DEFAULT_SHOWN_WEEK_COUNT (:88).
    private int mShownWeekCount = 6;
    // The colour/drawable defaults come from styled attributes in AOSP
    // (R.styleable.CalendarView_*); with no styled attributes read they are
    // the field defaults, 0 and null.
    private int mSelectedWeekBackgroundColor;
    private int mFocusedMonthDateColor;
    private int mUnfocusedMonthDateColor;
    private int mWeekNumberColor;
    private int mWeekSeparatorLineColor;
    private android.graphics.drawable.Drawable mSelectedDateVerticalBar;

    /** AOSP CalendarViewLegacyDelegate :339 -- invalidate only on a change. */
    public void setShownWeekCount(int count) {
        if (mShownWeekCount != count) { mShownWeekCount = count; invalidate(); }
    }
    public int getShownWeekCount() { return mShownWeekCount; }

    /** AOSP :352 / :368 / :384 / :404 / :424 and their getters. */
    public void setSelectedWeekBackgroundColor(int color) {
        if (mSelectedWeekBackgroundColor != color) {
            mSelectedWeekBackgroundColor = color; invalidate();
        }
    }
    public int getSelectedWeekBackgroundColor() { return mSelectedWeekBackgroundColor; }

    public void setFocusedMonthDateColor(int color) {
        if (mFocusedMonthDateColor != color) { mFocusedMonthDateColor = color; invalidate(); }
    }
    public int getFocusedMonthDateColor() { return mFocusedMonthDateColor; }

    public void setUnfocusedMonthDateColor(int color) {
        if (mUnfocusedMonthDateColor != color) { mUnfocusedMonthDateColor = color; invalidate(); }
    }
    public int getUnfocusedMonthDateColor() { return mUnfocusedMonthDateColor; }

    public void setWeekNumberColor(int color) {
        if (mWeekNumberColor != color) { mWeekNumberColor = color; invalidate(); }
    }
    public int getWeekNumberColor() { return mWeekNumberColor; }

    public void setWeekSeparatorLineColor(int color) {
        if (mWeekSeparatorLineColor != color) { mWeekSeparatorLineColor = color; invalidate(); }
    }
    public int getWeekSeparatorLineColor() { return mWeekSeparatorLineColor; }

    /** AOSP :445 / :452 -- the resource form resolves and delegates. */
    public void setSelectedDateVerticalBar(int resourceId) {
        setSelectedDateVerticalBar(getContext().getDrawable(resourceId));
    }
    public void setSelectedDateVerticalBar(android.graphics.drawable.Drawable drawable) {
        if (mSelectedDateVerticalBar != drawable) {
            mSelectedDateVerticalBar = drawable; invalidate();
        }
    }
    public android.graphics.drawable.Drawable getSelectedDateVerticalBar() {
        return mSelectedDateVerticalBar;
    }
}
