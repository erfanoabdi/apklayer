// android.widget.DatePicker -- a widget to pick a calendar date.
//
// AOSP (android14-release):
//   frameworks/base/core/java/android/widget/DatePicker.java
//   frameworks/base/core/java/android/widget/DatePickerSpinnerDelegate.java
//   public class DatePicker extends FrameLayout
//
// This is AOSP's SPINNER delegate (DatePickerSpinnerDelegate) folded into the
// widget: three real NumberPickers (month / day / year, ordered by the locale's
// date pattern) that can be dragged, flung, tapped above/below, stepped with
// DPAD up/down, or TYPED into -- plus the spinner delegate's exact date
// arithmetic (day/month wrap carries into the next field, min/max clamping,
// numeric months for locales whose short month names are digits).
// The child tree mirrors @layout/date_picker_legacy_holo (the legacyLayout of
// Widget.Material.DatePicker, styles_material.xml:740-748): a horizontal
// LinearLayout holding LinearLayout#pickers (the three NumberPickers,
// layout_marginTop/Bottom @dimen/picker_top_margin = 16dip, start 8dip, end
// 8dip, 16dip after the last) and a 245x280dip CalendarView.
//
// WHY (2026-09-29, lane text-input): the previous class held the date and
// painted NOTHING ("the spinner/calendar chrome is not painted"), so any date
// dialog was an empty box. Mastodon's sign-up asks for a date of birth via
// <DatePicker android:datePickerMode="spinner" android:calendarViewShown="false">
// in an AlertDialog; the owner could not enter one ("Cant type in date of birth
// to create a new account").
//
// KNOWN GAP, stated rather than hidden: AOSP's CALENDAR delegate
// (DatePickerCalendarDelegate: a header + DayPickerView/YearPickerView pager,
// the default for Widget.Material.DatePicker via @integer/date_picker_mode_material
// = 2) is not ported. A calendar-mode picker is presented with this spinner UI
// and its CalendarView hidden, which is a different LOOK but the same date model
// and a working input, instead of an empty box. getMode() still answers the mode
// the app asked for. Our CalendarView is itself a value model with no UI, which
// is why the legacy layout's CalendarView (calendarViewShown, default true under
// Material) shows as empty space in spinner mode, as its dimensions say it should.
package android.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import java.text.DateFormatSymbols;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Locale;

public class DatePicker extends FrameLayout {
    /** AOSP DatePicker.MODE_SPINNER / MODE_CALENDAR. */
    public static final int MODE_SPINNER = 1;
    public static final int MODE_CALENDAR = 2;

    public interface OnDateChangedListener {
        void onDateChanged(DatePicker view, int year, int monthOfYear, int dayOfMonth);
    }

    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    // DatePickerSpinnerDelegate.java constants.
    private static final String DATE_FORMAT = "MM/dd/yyyy";
    private static final int DEFAULT_START_YEAR = 1900;
    private static final int DEFAULT_END_YEAR = 2100;

    private final int mMode;
    private final LinearLayout mSpinners;
    private final NumberPicker mDaySpinner;
    private final NumberPicker mMonthSpinner;
    private final NumberPicker mYearSpinner;
    private final EditText mDaySpinnerInput;
    private final EditText mMonthSpinnerInput;
    private final EditText mYearSpinnerInput;
    private final CalendarView mCalendarView;
    private String[] mShortMonths;
    private final java.text.DateFormat mDateFormat = new SimpleDateFormat(DATE_FORMAT);
    private int mNumberOfMonths;
    private Calendar mTempDate;
    private Calendar mMinDate;
    private Calendar mMaxDate;
    private Calendar mCurrentDate;
    private boolean mIsEnabled = true;
    private OnDateChangedListener mOnDateChangedListener;

    public DatePicker(Context context) { this(context, null); }
    public DatePicker(Context context, AttributeSet attrs) { this(context, attrs, 0); }
    public DatePicker(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public DatePicker(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr);
        // android:datePickerMode is an enum (spinner=1, calendar=2); the theme
        // default (Widget.Material.DatePicker) is @integer/date_picker_mode_material = 2.
        mMode = attrs == null ? MODE_CALENDAR
                : attrs.getAttributeIntValue(ANDROID_NS, "datePickerMode", MODE_CALENDAR);
        final boolean calendarMode = mMode == MODE_CALENDAR;

        setCurrentLocale(Locale.getDefault());

        // DatePickerSpinnerDelegate(...) attribute reads. Defaults are the
        // Widget.Material.DatePicker style values (calendarViewShown=true;
        // spinnersShown is unset there, so the delegate's DEFAULT_SPINNERS_SHOWN=true).
        boolean spinnersShown = attrBool(attrs, "spinnersShown", true);
        boolean calendarViewShown = !calendarMode && attrBool(attrs, "calendarViewShown", true);
        int startYear = attrs == null ? DEFAULT_START_YEAR
                : attrs.getAttributeIntValue(ANDROID_NS, "startYear", DEFAULT_START_YEAR);
        int endYear = attrs == null ? DEFAULT_END_YEAR
                : attrs.getAttributeIntValue(ANDROID_NS, "endYear", DEFAULT_END_YEAR);
        String minDate = attrs == null ? null : attrs.getAttributeValue(ANDROID_NS, "minDate");
        String maxDate = attrs == null ? null : attrs.getAttributeValue(ANDROID_NS, "maxDate");

        // --- @layout/date_picker_legacy_holo, built with its own params ------
        final float d = context.getResources().getDisplayMetrics().density;
        final LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(android.view.Gravity.CENTER);
        FrameLayout.LayoutParams rootLp = new FrameLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        rootLp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        addView(root, rootLp);

        mSpinners = new LinearLayout(context);
        mSpinners.setId(NumberPicker.internalId(context, "pickers"));
        mSpinners.setOrientation(LinearLayout.HORIZONTAL);
        mSpinners.setGravity(android.view.Gravity.CENTER);
        root.addView(mSpinners, new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 1f));

        mMonthSpinner = newSpinner(context, "month");
        mDaySpinner = newSpinner(context, "day");
        mYearSpinner = newSpinner(context, "year");

        mCalendarView = new CalendarView(context);
        mCalendarView.setId(NumberPicker.internalId(context, "calendar_view"));
        LinearLayout.LayoutParams calLp = new LinearLayout.LayoutParams(
                Math.round(245 * d), Math.round(280 * d), 1f);
        calLp.setMarginStart(Math.round(16 * d));
        calLp.setMarginEnd(Math.round(16 * d));
        mCalendarView.setFocusable(true);
        mCalendarView.setFocusableInTouchMode(true);
        root.addView(mCalendarView, calLp);
        root.setSaveFromParentEnabled(false);

        NumberPicker.OnValueChangeListener onChangeListener = new NumberPicker.OnValueChangeListener() {
            public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
                updateInputState();
                mTempDate.setTimeInMillis(mCurrentDate.getTimeInMillis());
                // take care of wrapping of days and months to update greater fields
                if (picker == mDaySpinner) {
                    int maxDayOfMonth = mTempDate.getActualMaximum(Calendar.DAY_OF_MONTH);
                    if (oldVal == maxDayOfMonth && newVal == 1) {
                        mTempDate.add(Calendar.DAY_OF_MONTH, 1);
                    } else if (oldVal == 1 && newVal == maxDayOfMonth) {
                        mTempDate.add(Calendar.DAY_OF_MONTH, -1);
                    } else {
                        mTempDate.add(Calendar.DAY_OF_MONTH, newVal - oldVal);
                    }
                } else if (picker == mMonthSpinner) {
                    if (oldVal == 11 && newVal == 0) {
                        mTempDate.add(Calendar.MONTH, 1);
                    } else if (oldVal == 0 && newVal == 11) {
                        mTempDate.add(Calendar.MONTH, -1);
                    } else {
                        mTempDate.add(Calendar.MONTH, newVal - oldVal);
                    }
                } else if (picker == mYearSpinner) {
                    mTempDate.set(Calendar.YEAR, newVal);
                } else {
                    throw new IllegalArgumentException();
                }
                // now set the date to the adjusted one
                setDate(mTempDate.get(Calendar.YEAR), mTempDate.get(Calendar.MONTH),
                        mTempDate.get(Calendar.DAY_OF_MONTH));
                updateSpinners();
                updateCalendarView();
                notifyDateChanged();
            }
        };

        mCalendarView.setOnDateChangeListener(new CalendarView.OnDateChangeListener() {
            public void onSelectedDayChange(CalendarView view, int year, int month, int monthDay) {
                setDate(year, month, monthDay);
                updateSpinners();
                notifyDateChanged();
            }
        });

        mDaySpinner.setFormatter(NumberPicker.getTwoDigitFormatter());
        mDaySpinner.setOnLongPressUpdateInterval(100);
        mDaySpinner.setOnValueChangedListener(onChangeListener);
        mDaySpinnerInput = inputOf(mDaySpinner);

        mMonthSpinner.setMinValue(0);
        mMonthSpinner.setMaxValue(mNumberOfMonths - 1);
        mMonthSpinner.setDisplayedValues(mShortMonths);
        mMonthSpinner.setOnLongPressUpdateInterval(200);
        mMonthSpinner.setOnValueChangedListener(onChangeListener);
        mMonthSpinnerInput = inputOf(mMonthSpinner);

        mYearSpinner.setOnLongPressUpdateInterval(100);
        mYearSpinner.setOnValueChangedListener(onChangeListener);
        mYearSpinnerInput = inputOf(mYearSpinner);

        // show only what the user required but make sure we
        // show something and the spinners have higher priority
        if (!spinnersShown && !calendarViewShown) {
            setSpinnersShown(true);
        } else {
            setSpinnersShown(spinnersShown);
            setCalendarViewShown(calendarViewShown);
        }

        // set the min date giving priority of the minDate over startYear
        mTempDate.clear();
        if (!android.text.TextUtils.isEmpty(minDate)) {
            if (!parseDate(minDate, mTempDate)) {
                mTempDate.set(startYear, 0, 1);
            }
        } else {
            mTempDate.set(startYear, 0, 1);
        }
        setMinDate(mTempDate.getTimeInMillis());

        // set the max date giving priority of the maxDate over endYear
        mTempDate.clear();
        if (!android.text.TextUtils.isEmpty(maxDate)) {
            if (!parseDate(maxDate, mTempDate)) {
                mTempDate.set(endYear, 11, 31);
            }
        } else {
            mTempDate.set(endYear, 11, 31);
        }
        setMaxDate(mTempDate.getTimeInMillis());

        // initialize to current date
        mCurrentDate.setTimeInMillis(System.currentTimeMillis());
        init(mCurrentDate.get(Calendar.YEAR), mCurrentDate.get(Calendar.MONTH),
                mCurrentDate.get(Calendar.DAY_OF_MONTH), null);

        // re-order the number spinners to match the current date format
        reorderSpinners();
    }

    private static boolean attrBool(AttributeSet attrs, String name, boolean def) {
        return attrs == null ? def : attrs.getAttributeBooleanValue(ANDROID_NS, name, def);
    }

    private NumberPicker newSpinner(Context context, String idName) {
        final float d = context.getResources().getDisplayMetrics().density;
        NumberPicker p = new NumberPicker(context);
        p.setId(NumberPicker.internalId(context, idName));
        p.setFocusable(true);
        p.setFocusableInTouchMode(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp.topMargin = Math.round(16 * d);      // @dimen/picker_top_margin
        lp.bottomMargin = Math.round(16 * d);   // @dimen/picker_bottom_margin
        lp.setMarginStart(Math.round(8 * d));
        lp.setMarginEnd(Math.round(("year".equals(idName) ? 16 : 8) * d));
        p.setLayoutParams(lp);
        return p;
    }

    private static EditText inputOf(NumberPicker p) {
        for (int i = 0; i < p.getChildCount(); i++) {
            View c = p.getChildAt(i);
            if (c instanceof EditText) return (EditText) c;
        }
        return null;
    }

    /** @hide AOSP getMode(): the mode the widget was CREATED in. */
    public int getMode() { return mMode; }

    public void init(int year, int monthOfYear, int dayOfMonth,
            OnDateChangedListener onDateChangedListener) {
        setDate(year, monthOfYear, dayOfMonth);
        updateSpinners();
        updateCalendarView();
        mOnDateChangedListener = onDateChangedListener;
    }

    public void setOnDateChangedListener(OnDateChangedListener onDateChangedListener) {
        mOnDateChangedListener = onDateChangedListener;
    }

    public void updateDate(int year, int month, int dayOfMonth) {
        if (!isNewDate(year, month, dayOfMonth)) {
            return;
        }
        setDate(year, month, dayOfMonth);
        updateSpinners();
        updateCalendarView();
        notifyDateChanged();
    }

    public int getYear() { return mCurrentDate.get(Calendar.YEAR); }
    public int getMonth() { return mCurrentDate.get(Calendar.MONTH); }
    public int getDayOfMonth() { return mCurrentDate.get(Calendar.DAY_OF_MONTH); }

    public void setFirstDayOfWeek(int firstDayOfWeek) {
        if (firstDayOfWeek < Calendar.SUNDAY || firstDayOfWeek > Calendar.SATURDAY) {
            throw new IllegalArgumentException("firstDayOfWeek must be between 1 and 7");
        }
        mCalendarView.setFirstDayOfWeek(firstDayOfWeek);
    }
    public int getFirstDayOfWeek() { return mCalendarView.getFirstDayOfWeek(); }

    public long getMinDate() { return mCalendarView.getMinDate(); }

    public void setMinDate(long minDate) {
        mTempDate.setTimeInMillis(minDate);
        if (mTempDate.get(Calendar.YEAR) == mMinDate.get(Calendar.YEAR)
                && mTempDate.get(Calendar.DAY_OF_YEAR) == mMinDate.get(Calendar.DAY_OF_YEAR)) {
            // Same day, no-op.
            return;
        }
        mMinDate.setTimeInMillis(minDate);
        mCalendarView.setMinDate(minDate);
        if (mCurrentDate.before(mMinDate)) {
            mCurrentDate.setTimeInMillis(mMinDate.getTimeInMillis());
            updateCalendarView();
        }
        updateSpinners();
    }

    public long getMaxDate() { return mCalendarView.getMaxDate(); }

    public void setMaxDate(long maxDate) {
        mTempDate.setTimeInMillis(maxDate);
        if (mTempDate.get(Calendar.YEAR) == mMaxDate.get(Calendar.YEAR)
                && mTempDate.get(Calendar.DAY_OF_YEAR) == mMaxDate.get(Calendar.DAY_OF_YEAR)) {
            // Same day, no-op.
            return;
        }
        mMaxDate.setTimeInMillis(maxDate);
        mCalendarView.setMaxDate(maxDate);
        if (mCurrentDate.after(mMaxDate)) {
            mCurrentDate.setTimeInMillis(mMaxDate.getTimeInMillis());
            updateCalendarView();
        }
        updateSpinners();
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (mIsEnabled == enabled) {
            return;
        }
        super.setEnabled(enabled);
        mDaySpinner.setEnabled(enabled);
        mMonthSpinner.setEnabled(enabled);
        mYearSpinner.setEnabled(enabled);
        mCalendarView.setEnabled(enabled);
        mIsEnabled = enabled;
    }

    @Override
    public boolean isEnabled() { return mIsEnabled; }

    /** AOSP (spinner delegate) returns the legacy layout's CalendarView. */
    public CalendarView getCalendarView() { return mCalendarView; }

    public void setCalendarViewShown(boolean shown) {
        mCalendarView.setVisibility(shown ? View.VISIBLE : View.GONE);
    }
    public boolean getCalendarViewShown() {
        return (mCalendarView.getVisibility() == View.VISIBLE);
    }

    public void setSpinnersShown(boolean shown) {
        mSpinners.setVisibility(shown ? View.VISIBLE : View.GONE);
    }
    /** AOSP DatePickerSpinnerDelegate.getSpinnersShown: mSpinners.isShown(). */
    public boolean getSpinnersShown() { return mSpinners.isShown(); }

    // ---- DatePickerSpinnerDelegate private helpers, verbatim in behaviour ----

    private void setCurrentLocale(Locale locale) {
        mTempDate = getCalendarForLocale(mTempDate, locale);
        mMinDate = getCalendarForLocale(mMinDate, locale);
        mMaxDate = getCalendarForLocale(mMaxDate, locale);
        mCurrentDate = getCalendarForLocale(mCurrentDate, locale);

        mNumberOfMonths = mTempDate.getActualMaximum(Calendar.MONTH) + 1;
        mShortMonths = new DateFormatSymbols().getShortMonths();

        if (usingNumericMonths()) {
            // We're in a locale where a date should either be all-numeric, or all-text.
            // All-text would require custom NumberPicker formatters for day and year.
            mShortMonths = new String[mNumberOfMonths];
            for (int i = 0; i < mNumberOfMonths; ++i) {
                mShortMonths[i] = String.format("%d", i + 1);
            }
        }
    }

    private boolean usingNumericMonths() {
        return Character.isDigit(mShortMonths[Calendar.JANUARY].charAt(0));
    }

    private Calendar getCalendarForLocale(Calendar oldCalendar, Locale locale) {
        if (oldCalendar == null) {
            return Calendar.getInstance(locale);
        } else {
            final long currentTimeMillis = oldCalendar.getTimeInMillis();
            Calendar newCalendar = Calendar.getInstance(locale);
            newCalendar.setTimeInMillis(currentTimeMillis);
            return newCalendar;
        }
    }

    /** AOSP reorderSpinners: the order of the locale's best "yyyyMMMdd" pattern. */
    private void reorderSpinners() {
        mSpinners.removeAllViews();
        String pattern = android.text.format.DateFormat.getBestDateTimePattern(
                Locale.getDefault(), "yyyyMMMdd");
        char[] order;
        try { order = dateFormatOrder(pattern); }
        catch (IllegalArgumentException e) { order = new char[] { 'M', 'd', 'y' }; }
        final int spinnerCount = order.length;
        for (int i = 0; i < spinnerCount; i++) {
            switch (order[i]) {
                case 'd':
                    mSpinners.addView(mDaySpinner);
                    setImeOptions(mDaySpinner, spinnerCount, i);
                    break;
                case 'M':
                    mSpinners.addView(mMonthSpinner);
                    setImeOptions(mMonthSpinner, spinnerCount, i);
                    break;
                case 'y':
                    mSpinners.addView(mYearSpinner);
                    setImeOptions(mYearSpinner, spinnerCount, i);
                    break;
                default:
                    throw new IllegalArgumentException(Arrays.toString(order));
            }
        }
    }

    /** AOSP DateFormat.getDateFormatOrder(String pattern) (android14
     *  core/java/android/text/format/DateFormat.java): the order in which
     *  'd', 'M' and 'y' first appear, skipping quoted literals. Kept local
     *  because our DateFormat only has the Context overload. */
    static char[] dateFormatOrder(String pattern) {
        char[] result = new char[3];
        int resultIndex = 0;
        boolean sawDay = false, sawMonth = false, sawYear = false;
        for (int i = 0; i < pattern.length(); ++i) {
            char ch = pattern.charAt(i);
            if (ch == 'd' || ch == 'L' || ch == 'M' || ch == 'y') {
                if (ch == 'd' && !sawDay) {
                    result[resultIndex++] = 'd';
                    sawDay = true;
                } else if ((ch == 'L' || ch == 'M') && !sawMonth) {
                    result[resultIndex++] = 'M';
                    sawMonth = true;
                } else if ((ch == 'y') && !sawYear) {
                    result[resultIndex++] = 'y';
                    sawYear = true;
                }
            } else if (ch == 'G') {
                // Ignore the era specifier, if present.
            } else if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')) {
                throw new IllegalArgumentException("Bad pattern character '" + ch + "' in "
                        + pattern);
            } else if (ch == '\'') {
                if (i < pattern.length() - 1 && pattern.charAt(i + 1) == '\'') {
                    ++i;
                } else {
                    i = pattern.indexOf('\'', i + 1);
                    if (i == -1) {
                        throw new IllegalArgumentException("Bad quoting in " + pattern);
                    }
                    ++i;
                }
            } else {
                // Ignore spaces and punctuation.
            }
        }
        if (resultIndex != 3) {
            // Fall back to the canonical US order rather than dropping a field.
            return new char[] { 'M', 'd', 'y' };
        }
        return result;
    }

    private boolean parseDate(String date, Calendar outDate) {
        try {
            outDate.setTime(mDateFormat.parse(date));
            return true;
        } catch (ParseException e) {
            return false;
        }
    }

    private boolean isNewDate(int year, int month, int dayOfMonth) {
        return (mCurrentDate.get(Calendar.YEAR) != year
                || mCurrentDate.get(Calendar.MONTH) != month
                || mCurrentDate.get(Calendar.DAY_OF_MONTH) != dayOfMonth);
    }

    private void setDate(int year, int month, int dayOfMonth) {
        mCurrentDate.set(year, month, dayOfMonth);
        if (mCurrentDate.before(mMinDate)) {
            mCurrentDate.setTimeInMillis(mMinDate.getTimeInMillis());
        } else if (mCurrentDate.after(mMaxDate)) {
            mCurrentDate.setTimeInMillis(mMaxDate.getTimeInMillis());
        }
    }

    private void updateSpinners() {
        if (mDaySpinner == null) return;   // called from setMinDate/setMaxDate before the spinners exist
        // set the spinner ranges respecting the min and max dates
        if (mCurrentDate.equals(mMinDate)) {
            mDaySpinner.setMinValue(mCurrentDate.get(Calendar.DAY_OF_MONTH));
            mDaySpinner.setMaxValue(mCurrentDate.getActualMaximum(Calendar.DAY_OF_MONTH));
            mDaySpinner.setWrapSelectorWheel(false);
            mMonthSpinner.setDisplayedValues(null);
            mMonthSpinner.setMinValue(mCurrentDate.get(Calendar.MONTH));
            mMonthSpinner.setMaxValue(mCurrentDate.getActualMaximum(Calendar.MONTH));
            mMonthSpinner.setWrapSelectorWheel(false);
        } else if (mCurrentDate.equals(mMaxDate)) {
            mDaySpinner.setMinValue(mCurrentDate.getActualMinimum(Calendar.DAY_OF_MONTH));
            mDaySpinner.setMaxValue(mCurrentDate.get(Calendar.DAY_OF_MONTH));
            mDaySpinner.setWrapSelectorWheel(false);
            mMonthSpinner.setDisplayedValues(null);
            mMonthSpinner.setMinValue(mCurrentDate.getActualMinimum(Calendar.MONTH));
            mMonthSpinner.setMaxValue(mCurrentDate.get(Calendar.MONTH));
            mMonthSpinner.setWrapSelectorWheel(false);
        } else {
            mDaySpinner.setMinValue(1);
            mDaySpinner.setMaxValue(mCurrentDate.getActualMaximum(Calendar.DAY_OF_MONTH));
            mDaySpinner.setWrapSelectorWheel(true);
            mMonthSpinner.setDisplayedValues(null);
            mMonthSpinner.setMinValue(0);
            mMonthSpinner.setMaxValue(11);
            mMonthSpinner.setWrapSelectorWheel(true);
        }

        // make sure the month names are a zero based array
        // with the months in the month spinner
        String[] displayedValues = Arrays.copyOfRange(mShortMonths,
                mMonthSpinner.getMinValue(), mMonthSpinner.getMaxValue() + 1);
        mMonthSpinner.setDisplayedValues(displayedValues);

        // year spinner range does not change based on the current date
        mYearSpinner.setMinValue(mMinDate.get(Calendar.YEAR));
        mYearSpinner.setMaxValue(mMaxDate.get(Calendar.YEAR));
        mYearSpinner.setWrapSelectorWheel(false);

        // set the spinner values
        mYearSpinner.setValue(mCurrentDate.get(Calendar.YEAR));
        mMonthSpinner.setValue(mCurrentDate.get(Calendar.MONTH));
        mDaySpinner.setValue(mCurrentDate.get(Calendar.DAY_OF_MONTH));

        if (usingNumericMonths() && mMonthSpinnerInput != null) {
            mMonthSpinnerInput.setRawInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        }
    }

    private void updateCalendarView() {
        if (mCalendarView != null) mCalendarView.setDate(mCurrentDate.getTimeInMillis(), false, false);
    }

    private void notifyDateChanged() {
        if (mOnDateChangedListener != null) {
            mOnDateChangedListener.onDateChanged(this, getYear(), getMonth(), getDayOfMonth());
        }
    }

    private void setImeOptions(NumberPicker spinner, int spinnerCount, int spinnerIndex) {
        final int imeOptions;
        if (spinnerIndex < spinnerCount - 1) {
            imeOptions = EditorInfo.IME_ACTION_NEXT;
        } else {
            imeOptions = EditorInfo.IME_ACTION_DONE;
        }
        EditText input = inputOf(spinner);
        if (input != null) input.setImeOptions(imeOptions);
    }

    /** AOSP updateInputState: when the value changed while one of our inputs
     *  held the IME, close it (commit the typed text via clearFocus). */
    private void updateInputState() {
        InputMethodManager inputMethodManager = (InputMethodManager)
                getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            if (mYearSpinnerInput != null && inputMethodManager.isActive(mYearSpinnerInput)) {
                mYearSpinnerInput.clearFocus();
                inputMethodManager.hideSoftInputFromWindow(getWindowToken(), 0);
            } else if (mMonthSpinnerInput != null && inputMethodManager.isActive(mMonthSpinnerInput)) {
                mMonthSpinnerInput.clearFocus();
                inputMethodManager.hideSoftInputFromWindow(getWindowToken(), 0);
            } else if (mDaySpinnerInput != null && inputMethodManager.isActive(mDaySpinnerInput)) {
                mDaySpinnerInput.clearFocus();
                inputMethodManager.hideSoftInputFromWindow(getWindowToken(), 0);
            }
        }
    }
}
