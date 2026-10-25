// android.text.style.TtsSpan -- accessibility (TalkBack) metadata span.
// It carries a "type" + a PersistableBundle of arguments describing how a
// run of text should be spoken (e.g. wrap an entered digit so "7 x 3" is
// read as "7 times 3"). Mac.apk does no TTS, so this is a pure data
// carrier: it just needs to construct via its many Builders and be a
// valid setSpan() object. It is NOT a CharacterStyle, so TextView's
// render path ignores it -- matching AOSP, where TtsSpan affects only
// accessibility, never layout/draw.
//
// Added v1.0.365: Google Calculator wraps every entered digit/operator
// with a TtsSpan via `new TtsSpan.TextBuilder(s).build()` inside its
// formula-building accessibility code; the missing class threw
// NoClassDefFoundError in the digit-key handler, aborting it, so typed
// digits never reached the formula. AOSP reference:
// frameworks/base/core/java/android/text/style/TtsSpan.java.
package android.text.style;

import android.os.PersistableBundle;

// 2026-08-23 conformance sweep: implements android.text.ParcelableSpan.
// Payload (type, args) and type id 24 verified against
// frameworks/base/core/java/android/text/style/TtsSpan.java
// (android16-release) L499-533, matching this class's own field order.
public class TtsSpan implements android.text.ParcelableSpan {
    // Types
    public static final String TYPE_TEXT = "android.type.text";
    public static final String TYPE_CARDINAL = "android.type.cardinal";
    public static final String TYPE_ORDINAL = "android.type.ordinal";
    public static final String TYPE_DECIMAL = "android.type.decimal";
    public static final String TYPE_FRACTION = "android.type.fraction";
    public static final String TYPE_MEASURE = "android.type.measure";
    public static final String TYPE_TIME = "android.type.time";
    public static final String TYPE_DATE = "android.type.date";
    public static final String TYPE_MONEY = "android.type.money";
    public static final String TYPE_TELEPHONE = "android.type.telephone";
    public static final String TYPE_ELECTRONIC = "android.type.electronic";
    public static final String TYPE_DIGITS = "android.type.digits";
    public static final String TYPE_VERBATIM = "android.type.verbatim";
    /** AOSP TtsSpan.TYPE_DURATION -- a span of time (hours/minutes/seconds),
     *  as opposed to TYPE_TIME's clock reading. Read by DurationBuilder. */
    public static final String TYPE_DURATION = "android.type.duration";

    // AOSP TtsSpan's ARG_WEEKDAY values -- Calendar.SUNDAY..Calendar.SATURDAY
    // (1..7), NOT zero-based.
    public static final int WEEKDAY_SUNDAY = 1;
    public static final int WEEKDAY_MONDAY = 2;
    public static final int WEEKDAY_TUESDAY = 3;
    public static final int WEEKDAY_WEDNESDAY = 4;
    public static final int WEEKDAY_THURSDAY = 5;
    public static final int WEEKDAY_FRIDAY = 6;
    public static final int WEEKDAY_SATURDAY = 7;

    // AOSP TtsSpan's ARG_MONTH values -- Calendar.JANUARY..Calendar.DECEMBER
    // (0..11), zero-based, unlike the weekdays above.
    public static final int MONTH_JANUARY = 0;
    public static final int MONTH_FEBRUARY = 1;
    public static final int MONTH_MARCH = 2;
    public static final int MONTH_APRIL = 3;
    public static final int MONTH_MAY = 4;
    public static final int MONTH_JUNE = 5;
    public static final int MONTH_JULY = 6;
    public static final int MONTH_AUGUST = 7;
    public static final int MONTH_SEPTEMBER = 8;
    public static final int MONTH_OCTOBER = 9;
    public static final int MONTH_NOVEMBER = 10;
    public static final int MONTH_DECEMBER = 11;

    // Arguments
    public static final String ARG_TEXT = "android.arg.text";
    public static final String ARG_VERBATIM = "android.arg.verbatim";
    public static final String ARG_GENDER = "android.arg.gender";
    public static final String ARG_ANIMACY = "android.arg.animacy";
    public static final String ARG_MULTIPLICITY = "android.arg.multiplicity";
    public static final String ARG_CASE = "android.arg.case";
    public static final String ARG_NUMBER = "android.arg.number";
    public static final String ARG_INTEGER_PART = "android.arg.integer_part";
    public static final String ARG_FRACTIONAL_PART = "android.arg.fractional_part";
    public static final String ARG_NUMERATOR = "android.arg.numerator";
    public static final String ARG_DENOMINATOR = "android.arg.denominator";
    public static final String ARG_UNIT = "android.arg.unit";
    public static final String ARG_HOURS = "android.arg.hours";
    public static final String ARG_MINUTES = "android.arg.minutes";
    /** AOSP TtsSpan.ARG_SECONDS -- used by TYPE_TIME and TYPE_MEASURE. It was
     *  missing, which is why DurationBuilder.setSeconds below had to spell the
     *  string literal out. */
    public static final String ARG_SECONDS = "android.arg.seconds";
    public static final String ARG_WEEKDAY = "android.arg.weekday";
    public static final String ARG_DAY = "android.arg.day";
    public static final String ARG_MONTH = "android.arg.month";
    public static final String ARG_YEAR = "android.arg.year";
    // The KEY is spelled "money", not "currency" -- an upstream naming mismatch that
    // apps inline at compile time. frameworks/base/core/java/android/text/style/TtsSpan.java
    // (android-11.0.0_r48)
    public static final String ARG_CURRENCY = "android.arg.money";
    public static final String ARG_QUANTITY = "android.arg.quantity";
    public static final String ARG_COUNTRY_CODE = "android.arg.country_code";
    public static final String ARG_NUMBER_PARTS = "android.arg.number_parts";
    public static final String ARG_EXTENSION = "android.arg.extension";
    public static final String ARG_PROTOCOL = "android.arg.protocol";
    public static final String ARG_USERNAME = "android.arg.username";
    public static final String ARG_PASSWORD = "android.arg.password";
    public static final String ARG_DOMAIN = "android.arg.domain";
    public static final String ARG_PORT = "android.arg.port";
    public static final String ARG_PATH = "android.arg.path";
    public static final String ARG_QUERY_STRING = "android.arg.query_string";
    public static final String ARG_FRAGMENT_ID = "android.arg.fragment_id";
    public static final String ARG_DIGITS = "android.arg.digits";

    // Value constants used by SemioticClassBuilder setters
    public static final String GENDER_NEUTRAL = "android.neutral";
    public static final String GENDER_MALE = "android.male";
    public static final String GENDER_FEMALE = "android.female";
    public static final String ANIMACY_ANIMATE = "android.animate";
    public static final String ANIMACY_INANIMATE = "android.inanimate";
    public static final String MULTIPLICITY_SINGLE = "android.single";
    public static final String MULTIPLICITY_DUAL = "android.dual";
    public static final String MULTIPLICITY_PLURAL = "android.plural";
    public static final String CASE_NOMINATIVE = "android.nominative";
    public static final String CASE_ACCUSATIVE = "android.accusative";
    public static final String CASE_DATIVE = "android.dative";
    public static final String CASE_ABLATIVE = "android.ablative";
    public static final String CASE_GENITIVE = "android.genitive";
    public static final String CASE_VOCATIVE = "android.vocative";
    public static final String CASE_LOCATIVE = "android.locative";
    public static final String CASE_INSTRUMENTAL = "android.instrumental";

    private final String mType;
    private final PersistableBundle mArgs;

    public TtsSpan(String type, PersistableBundle args) {
        mType = type;
        mArgs = args;
    }

    /** AOSP TtsSpan(Parcel src): `mType = src.readString(); mArgs =
     *  src.readPersistableBundle();` -- the public un-parcelling constructor,
     *  the exact inverse of writeToParcelInternal below. */
    public TtsSpan(android.os.Parcel src) {
        mType = src.readString();
        mArgs = src.readPersistableBundle();
    }

    public String getType() { return mType; }

    public PersistableBundle getArgs() { return mArgs; }

    @Override public int getSpanTypeId() { return getSpanTypeIdInternal(); }
    @Override public int getSpanTypeIdInternal() { return android.text.TextUtils.TTS_SPAN; }
    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        writeToParcelInternal(dest, flags);
    }
    @Override public void writeToParcelInternal(android.os.Parcel dest, int flags) {
        dest.writeString(mType);
        dest.writePersistableBundle(mArgs);
    }
    public static final android.os.Parcelable.Creator<TtsSpan> CREATOR =
            new android.os.Parcelable.Creator<TtsSpan>() {
        @Override public TtsSpan createFromParcel(android.os.Parcel in) {
            return new TtsSpan(in);
        }
        @Override public TtsSpan[] newArray(int size) { return new TtsSpan[size]; }
    };

    /** Generic base builder. Subclasses parameterize C with themselves so
     *  setters can chain. */
    public static class Builder<C extends Builder<?>> {
        protected final PersistableBundle mArgs = new PersistableBundle();
        private final String mType;

        public Builder(String type) { mType = type; }

        public TtsSpan build() { return new TtsSpan(mType, mArgs); }

        @SuppressWarnings("unchecked")
        public C setStringArgument(String arg, String value) {
            mArgs.putString(arg, value);
            return (C) this;
        }

        @SuppressWarnings("unchecked")
        public C setIntArgument(String arg, int value) {
            mArgs.putInt(arg, value);
            return (C) this;
        }

        @SuppressWarnings("unchecked")
        public C setLongArgument(String arg, long value) {
            mArgs.putLong(arg, value);
            return (C) this;
        }
    }

    public static class SemioticClassBuilder<C extends SemioticClassBuilder<?>>
            extends Builder<C> {
        public SemioticClassBuilder(String type) { super(type); }
        public C setGender(String gender) { return setStringArgument(ARG_GENDER, gender); }
        public C setAnimacy(String animacy) { return setStringArgument(ARG_ANIMACY, animacy); }
        public C setMultiplicity(String multiplicity) { return setStringArgument(ARG_MULTIPLICITY, multiplicity); }
        public C setCase(String grammaticalCase) { return setStringArgument(ARG_CASE, grammaticalCase); }
    }

    public static class TextBuilder extends SemioticClassBuilder<TextBuilder> {
        public TextBuilder() { super(TYPE_TEXT); }
        public TextBuilder(String text) { this(); setText(text); }
        public TextBuilder setText(String text) { return setStringArgument(ARG_TEXT, text); }
    }

    public static class VerbatimBuilder extends SemioticClassBuilder<VerbatimBuilder> {
        public VerbatimBuilder() { super(TYPE_VERBATIM); }
        public VerbatimBuilder(String text) { this(); setVerbatim(text); }
        public VerbatimBuilder setVerbatim(String text) { return setStringArgument(ARG_VERBATIM, text); }
    }

    public static class CardinalBuilder extends SemioticClassBuilder<CardinalBuilder> {
        public CardinalBuilder() { super(TYPE_CARDINAL); }
        public CardinalBuilder(long number) { this(); setNumber(number); }
        public CardinalBuilder(String number) { this(); setNumber(number); }
        public CardinalBuilder setNumber(long number) { return setNumber(String.valueOf(number)); }
        public CardinalBuilder setNumber(String number) { return setStringArgument(ARG_NUMBER, number); }
    }

    public static class OrdinalBuilder extends SemioticClassBuilder<OrdinalBuilder> {
        public OrdinalBuilder() { super(TYPE_ORDINAL); }
        public OrdinalBuilder(long number) { this(); setNumber(number); }
        public OrdinalBuilder(String number) { this(); setNumber(number); }
        public OrdinalBuilder setNumber(long number) { return setNumber(String.valueOf(number)); }
        public OrdinalBuilder setNumber(String number) { return setStringArgument(ARG_NUMBER, number); }
    }

    public static class DecimalBuilder extends SemioticClassBuilder<DecimalBuilder> {
        public DecimalBuilder() { super(TYPE_DECIMAL); }
        public DecimalBuilder(double number, int minimumFractionDigits, int maximumFractionDigits) {
            this();
            setArgumentsFromDouble(number, minimumFractionDigits, maximumFractionDigits);
        }
        public DecimalBuilder(String integerPart, String fractionalPart) {
            this();
            setIntegerPart(integerPart);
            setFractionalPart(fractionalPart);
        }
        public DecimalBuilder setArgumentsFromDouble(double number, int minimumFractionDigits, int maximumFractionDigits) {
            // We don't speak; store a plain string rendering so build() works.
            return setStringArgument(ARG_INTEGER_PART, String.valueOf((long) number));
        }
        public DecimalBuilder setIntegerPart(long integerPart) { return setIntegerPart(String.valueOf(integerPart)); }
        public DecimalBuilder setIntegerPart(String integerPart) { return setStringArgument(ARG_INTEGER_PART, integerPart); }
        public DecimalBuilder setFractionalPart(String fractionalPart) { return setStringArgument(ARG_FRACTIONAL_PART, fractionalPart); }
    }

    public static class FractionBuilder extends SemioticClassBuilder<FractionBuilder> {
        public FractionBuilder() { super(TYPE_FRACTION); }
        public FractionBuilder(long integerPart, long numerator, long denominator) {
            this();
            setIntegerPart(integerPart);
            setNumerator(numerator);
            setDenominator(denominator);
        }
        public FractionBuilder setIntegerPart(long integerPart) { return setIntegerPart(String.valueOf(integerPart)); }
        public FractionBuilder setIntegerPart(String integerPart) { return setStringArgument(ARG_INTEGER_PART, integerPart); }
        public FractionBuilder setNumerator(long numerator) { return setNumerator(String.valueOf(numerator)); }
        public FractionBuilder setNumerator(String numerator) { return setStringArgument(ARG_NUMERATOR, numerator); }
        public FractionBuilder setDenominator(long denominator) { return setDenominator(String.valueOf(denominator)); }
        public FractionBuilder setDenominator(String denominator) { return setStringArgument(ARG_DENOMINATOR, denominator); }
    }

    public static class MeasureBuilder extends SemioticClassBuilder<MeasureBuilder> {
        public MeasureBuilder() { super(TYPE_MEASURE); }
        public MeasureBuilder setNumber(long number) { return setNumber(String.valueOf(number)); }
        public MeasureBuilder setNumber(String number) { return setStringArgument(ARG_NUMBER, number); }
        public MeasureBuilder setIntegerPart(long integerPart) { return setIntegerPart(String.valueOf(integerPart)); }
        public MeasureBuilder setIntegerPart(String integerPart) { return setStringArgument(ARG_INTEGER_PART, integerPart); }
        public MeasureBuilder setFractionalPart(String fractionalPart) { return setStringArgument(ARG_FRACTIONAL_PART, fractionalPart); }
        public MeasureBuilder setNumerator(long numerator) { return setNumerator(String.valueOf(numerator)); }
        public MeasureBuilder setNumerator(String numerator) { return setStringArgument(ARG_NUMERATOR, numerator); }
        public MeasureBuilder setDenominator(long denominator) { return setDenominator(String.valueOf(denominator)); }
        public MeasureBuilder setDenominator(String denominator) { return setStringArgument(ARG_DENOMINATOR, denominator); }
        public MeasureBuilder setUnit(String unit) { return setStringArgument(ARG_UNIT, unit); }
    }

    public static class TimeBuilder extends SemioticClassBuilder<TimeBuilder> {
        public TimeBuilder() { super(TYPE_TIME); }
        public TimeBuilder(int hours, int minutes) { this(); setHours(hours); setMinutes(minutes); }
        public TimeBuilder setHours(int hours) { return setIntArgument(ARG_HOURS, hours); }
        public TimeBuilder setMinutes(int minutes) { return setIntArgument(ARG_MINUTES, minutes); }
        /** AOSP TtsSpan.TimeBuilder.setSeconds(int) -- ARG_SECONDS on a
         *  TYPE_TIME span. Missing here, though the sibling DurationBuilder
         *  already had its own. */
        public TimeBuilder setSeconds(int seconds) { return setIntArgument(ARG_SECONDS, seconds); }
    }

    public static class DurationBuilder extends SemioticClassBuilder<DurationBuilder> {
        public DurationBuilder() { super(TYPE_MEASURE); }
        public DurationBuilder setHours(int hours) { return setIntArgument(ARG_HOURS, hours); }
        public DurationBuilder setMinutes(int minutes) { return setIntArgument(ARG_MINUTES, minutes); }
        public DurationBuilder setSeconds(int seconds) { return setIntArgument(ARG_SECONDS, seconds); }
    }

    public static class DateBuilder extends SemioticClassBuilder<DateBuilder> {
        public DateBuilder() { super(TYPE_DATE); }
        public DateBuilder(Integer weekday, Integer day, Integer month, Integer year) {
            this();
            if (weekday != null) setWeekday(weekday);
            if (day != null) setDay(day);
            if (month != null) setMonth(month);
            if (year != null) setYear(year);
        }
        public DateBuilder setWeekday(int weekday) { return setIntArgument(ARG_WEEKDAY, weekday); }
        public DateBuilder setDay(int day) { return setIntArgument(ARG_DAY, day); }
        public DateBuilder setMonth(int month) { return setIntArgument(ARG_MONTH, month); }
        public DateBuilder setYear(int year) { return setIntArgument(ARG_YEAR, year); }
    }

    public static class MoneyBuilder extends SemioticClassBuilder<MoneyBuilder> {
        public MoneyBuilder() { super(TYPE_MONEY); }
        public MoneyBuilder setIntegerPart(long integerPart) { return setIntegerPart(String.valueOf(integerPart)); }
        public MoneyBuilder setIntegerPart(String integerPart) { return setStringArgument(ARG_INTEGER_PART, integerPart); }
        public MoneyBuilder setFractionalPart(String fractionalPart) { return setStringArgument(ARG_FRACTIONAL_PART, fractionalPart); }
        public MoneyBuilder setCurrency(String currency) { return setStringArgument(ARG_CURRENCY, currency); }
        public MoneyBuilder setQuantity(String quantity) { return setStringArgument(ARG_QUANTITY, quantity); }
    }

    public static class TelephoneBuilder extends SemioticClassBuilder<TelephoneBuilder> {
        public TelephoneBuilder() { super(TYPE_TELEPHONE); }
        public TelephoneBuilder(String numberParts) { this(); setNumberParts(numberParts); }
        public TelephoneBuilder setCountryCode(String countryCode) { return setStringArgument(ARG_COUNTRY_CODE, countryCode); }
        public TelephoneBuilder setNumberParts(String numberParts) { return setStringArgument(ARG_NUMBER_PARTS, numberParts); }
        public TelephoneBuilder setExtension(String extension) { return setStringArgument(ARG_EXTENSION, extension); }
    }

    public static class ElectronicBuilder extends SemioticClassBuilder<ElectronicBuilder> {
        public ElectronicBuilder() { super(TYPE_ELECTRONIC); }
        public ElectronicBuilder setEmailArguments(String username, String domain) {
            return setDomain(domain).setUsername(username);
        }
        public ElectronicBuilder setProtocol(String protocol) { return setStringArgument(ARG_PROTOCOL, protocol); }
        public ElectronicBuilder setUsername(String username) { return setStringArgument(ARG_USERNAME, username); }
        public ElectronicBuilder setPassword(String password) { return setStringArgument(ARG_PASSWORD, password); }
        public ElectronicBuilder setDomain(String domain) { return setStringArgument(ARG_DOMAIN, domain); }
        public ElectronicBuilder setPort(int port) { return setIntArgument(ARG_PORT, port); }
        public ElectronicBuilder setPath(String path) { return setStringArgument(ARG_PATH, path); }
        public ElectronicBuilder setQueryString(String queryString) { return setStringArgument(ARG_QUERY_STRING, queryString); }
        public ElectronicBuilder setFragmentId(String fragmentId) { return setStringArgument(ARG_FRAGMENT_ID, fragmentId); }
    }

    public static class DigitsBuilder extends SemioticClassBuilder<DigitsBuilder> {
        public DigitsBuilder() { super(TYPE_DIGITS); }
        public DigitsBuilder(String digits) { this(); setDigits(digits); }
        public DigitsBuilder setDigits(String digits) { return setStringArgument(ARG_DIGITS, digits); }
    }
}
