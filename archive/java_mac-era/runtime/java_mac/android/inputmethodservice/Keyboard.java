package android.inputmethodservice;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/** Loads an XML description of a keyboard and holds the resulting key
 *  geometry. Deprecated at API 29 in favour of each IME shipping its own
 *  layout, but still shipped in the platform and still used -- both by
 *  keyboard apps and, importantly for this host, by ordinary apps that embed a
 *  KeyboardView as an in-app keypad (PIN entry, calculators, custom numeric
 *  input). THAT use is fully hostable in Mac.apk, which is why this class is
 *  implemented rather than stubbed.
 *
 *  ONE DELIBERATE DEVIATION, and it is a mechanism change, not a behaviour
 *  change: AOSP reads every attribute through
 *  Resources.obtainAttributes(Xml.asAttributeSet(parser),
 *  com.android.internal.R.styleable.Keyboard*). Those styleable index arrays
 *  are platform-internal and Mac.apk ships no com.android.internal.R, so the
 *  attributes are read from the same parser BY NAME in the android namespace
 *  instead. The names and their flag values are AOSP's own
 *  core/res/res/values/attrs.xml declare-styleable Keyboard / Keyboard_Row /
 *  Keyboard_Key, quoted in readEdgeFlags() and the constructors below; the
 *  dimension|fraction handling reproduces AOSP's getDimensionOrFraction()
 *  exactly, including its "unrecognised type falls back to the default" rule.
 *
 *  API 3. Source: frameworks/base
 *  core/java/android/inputmethodservice/Keyboard.java (android16-release). */
@Deprecated
public class Keyboard {

    static final String TAG = "Keyboard";

    private static final String TAG_KEYBOARD = "Keyboard";
    private static final String TAG_ROW = "Row";
    private static final String TAG_KEY = "Key";

    /** The XML namespace every framework attribute in a keyboard layout is
     *  qualified with. */
    static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    /** Key edge flags: the key is anchored to the left edge of the keyboard. */
    public static final int EDGE_LEFT = 0x01;
    /** Key edge flags: the key is anchored to the right edge of the keyboard. */
    public static final int EDGE_RIGHT = 0x02;
    /** Row edge flags: the row is anchored to the top of the keyboard. */
    public static final int EDGE_TOP = 0x04;
    /** Row edge flags: the row is anchored to the bottom of the keyboard. */
    public static final int EDGE_BOTTOM = 0x08;

    public static final int KEYCODE_SHIFT = -1;
    public static final int KEYCODE_MODE_CHANGE = -2;
    public static final int KEYCODE_CANCEL = -3;
    public static final int KEYCODE_DONE = -4;
    public static final int KEYCODE_DELETE = -5;
    public static final int KEYCODE_ALT = -6;

    private int mDefaultHorizontalGap;
    private int mDefaultWidth;
    private int mDefaultHeight;
    private int mDefaultVerticalGap;

    private boolean mShifted;
    private Key[] mShiftKeys = { null, null };
    private int[] mShiftKeyIndices = { -1, -1 };

    private int mTotalHeight;
    private int mTotalWidth;

    private List<Key> mKeys;
    private List<Key> mModifierKeys;

    private int mDisplayWidth;
    private int mDisplayHeight;
    private int mKeyboardMode;

    // AOSP's proximity grid: the keyboard is bucketed into a 10x5 grid and each
    // cell caches the keys within mProximityThreshold of it, so getNearestKeys()
    // is a table lookup rather than a scan.
    private static final int GRID_WIDTH = 10;
    private static final int GRID_HEIGHT = 5;
    private static final int GRID_SIZE = GRID_WIDTH * GRID_HEIGHT;

    private int mCellWidth;
    private int mCellHeight;
    private int[][] mGridNeighbors;
    private int mProximityThreshold;

    /** AOSP: 1.8 key widths is "near" for proximity correction. */
    private static final float SEARCH_DISTANCE = 1.8f;

    private ArrayList<Row> rows = new ArrayList<Row>();

    /** Container for keys in the keyboard. All keys in a row are at the same
     *  Y-coordinate. Some of the key size defaults can be overridden per row. */
    @Deprecated
    public static class Row {
        /** Default width of a key in this row. */
        public int defaultWidth;
        /** Default height of a key in this row. */
        public int defaultHeight;
        /** Default horizontal gap between keys in this row. */
        public int defaultHorizontalGap;
        /** Vertical gap following this row. */
        public int verticalGap;

        ArrayList<Key> mKeys = new ArrayList<Key>();

        /** Edge flags for this row of keys: EDGE_TOP and/or EDGE_BOTTOM. */
        public int rowEdgeFlags;
        /** The keyboard mode for this row. */
        public int mode;

        private Keyboard parent;

        public Row(Keyboard parent) {
            this.parent = parent;
        }

        /** AOSP reads keyWidth/keyHeight/horizontalGap/verticalGap out of the
         *  Keyboard styleable and rowEdgeFlags/keyboardMode out of Keyboard_Row,
         *  each defaulting to the keyboard-wide value. */
        public Row(Resources res, Keyboard parent, XmlResourceParser parser) {
            this.parent = parent;
            defaultWidth = getDimensionOrFraction(res, parser, "keyWidth",
                    parent.mDisplayWidth, parent.mDefaultWidth);
            defaultHeight = getDimensionOrFraction(res, parser, "keyHeight",
                    parent.mDisplayHeight, parent.mDefaultHeight);
            defaultHorizontalGap = getDimensionOrFraction(res, parser, "horizontalGap",
                    parent.mDisplayWidth, parent.mDefaultHorizontalGap);
            verticalGap = getDimensionOrFraction(res, parser, "verticalGap",
                    parent.mDisplayHeight, parent.mDefaultVerticalGap);
            rowEdgeFlags = readEdgeFlags(parser, "rowEdgeFlags");
            mode = parser.getAttributeResourceValue(ANDROID_NS, "keyboardMode", 0);
        }
    }

    /** Class for describing the position and characteristics of a single key in
     *  the keyboard. */
    @Deprecated
    public static class Key {
        /** All the key codes (unicode or custom) that this key could generate,
         *  zero'th being the most important. */
        public int[] codes;
        /** Label to display. */
        public CharSequence label;
        /** Icon to display instead of a label. Icon takes precedence over a
         *  label. */
        public Drawable icon;
        /** Preview version of the icon, for the preview popup. */
        public Drawable iconPreview;
        /** Width of the key, not including the gap. */
        public int width;
        /** Height of the key, not including the gap. */
        public int height;
        /** The horizontal gap before this key. */
        public int gap;
        /** Whether this key is sticky, i.e., a toggle key. */
        public boolean sticky;
        /** X coordinate of the key in the keyboard layout. */
        public int x;
        /** Y coordinate of the key in the keyboard layout. */
        public int y;
        /** The current pressed state of this key. */
        public boolean pressed;
        /** If this is a sticky key, is it on? */
        public boolean on;
        /** Text to output when pressed. Overrides the codes. */
        public CharSequence text;
        /** Popup characters. */
        public CharSequence popupCharacters;
        /** Flags that specify the anchoring to edges of the keyboard for
         *  detecting touch events that are just out of the boundary of the key.
         *  Any combination of EDGE_LEFT/RIGHT/TOP/BOTTOM. */
        public int edgeFlags;
        /** Whether this is a modifier key, such as Shift or Alt. */
        public boolean modifier;
        /** The keyboard this key belongs to. */
        private Keyboard keyboard;
        /** If this key pops up a mini keyboard, this is the resource id for the
         *  XML layout of that keyboard. */
        public int popupResId;
        /** Whether this key repeats itself when held down. */
        public boolean repeatable;

        // AOSP's six drawable-state vectors, built from the framework's own
        // public state attrs. Values taken from android.R.attr via
        // `javap -constants` on the SDK jar rather than guessed, because this
        // tree ships no android.R:
        //   state_checkable = 16842911 (0x0101009f)
        //   state_checked   = 16842912 (0x010100a0)
        //   state_pressed   = 16842919 (0x010100a7)
        private static final int STATE_CHECKABLE = 16842911;
        private static final int STATE_CHECKED   = 16842912;
        private static final int STATE_PRESSED   = 16842919;

        private static final int[] KEY_STATE_NORMAL_ON = {
            STATE_CHECKABLE, STATE_CHECKED
        };
        private static final int[] KEY_STATE_PRESSED_ON = {
            STATE_PRESSED, STATE_CHECKABLE, STATE_CHECKED
        };
        private static final int[] KEY_STATE_NORMAL_OFF = {
            STATE_CHECKABLE
        };
        private static final int[] KEY_STATE_PRESSED_OFF = {
            STATE_PRESSED, STATE_CHECKABLE
        };
        private static final int[] KEY_STATE_NORMAL = {
        };
        private static final int[] KEY_STATE_PRESSED = {
            STATE_PRESSED
        };

        /** Create an empty key with no attributes, inheriting the row's
         *  defaults. */
        public Key(Row parent) {
            keyboard = parent.parent;
            height = parent.defaultHeight;
            width = parent.defaultWidth;
            gap = parent.defaultHorizontalGap;
            edgeFlags = parent.rowEdgeFlags;
        }

        /** Create a key with the given top-left coordinate and extract its
         *  attributes from the XML parser. */
        public Key(Resources res, Row parent, int x, int y, XmlResourceParser parser) {
            this(parent);
            this.x = x;
            this.y = y;

            width = getDimensionOrFraction(res, parser, "keyWidth",
                    keyboard.mDisplayWidth, parent.defaultWidth);
            height = getDimensionOrFraction(res, parser, "keyHeight",
                    keyboard.mDisplayHeight, parent.defaultHeight);
            gap = getDimensionOrFraction(res, parser, "horizontalGap",
                    keyboard.mDisplayWidth, parent.defaultHorizontalGap);

            // AOSP applies the gap AFTER reading it, so the key's x is the
            // caller's running x plus this key's own leading gap.
            this.x += gap;

            // AOSP: codes is format="integer|string" -- it peeks the TypedValue
            // and makes a one-element array from TYPE_INT_DEC/HEX, or splits a
            // TYPE_STRING on commas. Reading the raw text instead, the same
            // three cases are: an "@integer/..." reference (resolved through
            // Resources, which is what a compiled TYPE_INT would have been), a
            // bare integer, or a comma-separated list -- parseCSV handles the
            // last two identically because a single token is a one-element CSV.
            String codesValue = parser.getAttributeValue(ANDROID_NS, "codes");
            if (codesValue != null) {
                codesValue = codesValue.trim();
                if (!codesValue.isEmpty() && codesValue.charAt(0) == '@' && res != null) {
                    int id = parser.getAttributeResourceValue(ANDROID_NS, "codes", 0);
                    if (id != 0) {
                        try {
                            codes = new int[] { res.getInteger(id) };
                        } catch (RuntimeException ignored) {
                            codes = null;
                        }
                    }
                } else if (!codesValue.isEmpty()) {
                    codes = parseCSV(codesValue);
                }
            }

            iconPreview = readDrawable(res, parser, "iconPreview");
            if (iconPreview != null) {
                iconPreview.setBounds(0, 0, iconPreview.getIntrinsicWidth(),
                        iconPreview.getIntrinsicHeight());
            }
            popupCharacters = readText(res, parser, "popupCharacters");
            popupResId = parser.getAttributeResourceValue(ANDROID_NS, "popupKeyboard", 0);
            repeatable = readBoolean(parser, "isRepeatable", false);
            modifier = readBoolean(parser, "isModifier", false);
            sticky = readBoolean(parser, "isSticky", false);
            edgeFlags = readEdgeFlags(parser, "keyEdgeFlags");
            edgeFlags |= parent.rowEdgeFlags;

            icon = readDrawable(res, parser, "keyIcon");
            if (icon != null) {
                icon.setBounds(0, 0, icon.getIntrinsicWidth(), icon.getIntrinsicHeight());
            }

            label = readText(res, parser, "keyLabel");
            text = readText(res, parser, "keyOutputText");

            // AOSP: with no explicit codes, the first character of the label is
            // the code.
            if (codes == null && label != null && label.length() > 0) {
                codes = new int[] { label.charAt(0) };
            }
        }

        /** Informs the key that it has been pressed, in case it needs to change
         *  its appearance or state. */
        public void onPressed() {
            pressed = !pressed;
        }

        /** Changes the pressed state of the key. Toggled state of the key will
         *  be flipped when the user lifts inside the key. */
        public void onReleased(boolean inside) {
            pressed = !pressed;
            if (sticky && inside) {
                on = !on;
            }
        }

        /** AOSP parses either a single integer or a comma-separated list;
         *  an unparsable token is skipped, leaving a 0 in its slot. */
        int[] parseCSV(String value) {
            int count = 0;
            int lastIndex = 0;
            if (value.length() > 0) {
                count++;
                while ((lastIndex = value.indexOf(",", lastIndex + 1)) > 0) {
                    count++;
                }
            }
            int[] values = new int[count];
            count = 0;
            StringTokenizer st = new StringTokenizer(value, ",");
            while (st.hasMoreTokens()) {
                try {
                    values[count++] = Integer.parseInt(st.nextToken().trim());
                } catch (NumberFormatException nfe) {
                    // AOSP logs and leaves the slot at 0.
                }
            }
            return values;
        }

        /** Detects if a point falls inside this key. A key anchored to an edge
         *  also claims everything beyond that edge, so a touch off the side of
         *  the keyboard still lands on a key. */
        public boolean isInside(int x, int y) {
            boolean leftEdge = (edgeFlags & EDGE_LEFT) > 0;
            boolean rightEdge = (edgeFlags & EDGE_RIGHT) > 0;
            boolean topEdge = (edgeFlags & EDGE_TOP) > 0;
            boolean bottomEdge = (edgeFlags & EDGE_BOTTOM) > 0;
            if ((x >= this.x || (leftEdge && x <= this.x + this.width))
                    && (x < this.x + this.width || (rightEdge && x >= this.x))
                    && (y >= this.y || (topEdge && y <= this.y + this.height))
                    && (y < this.y + this.height || (bottomEdge && y >= this.y))) {
                return true;
            } else {
                return false;
            }
        }

        /** Returns the square of the distance between the center of the key and
         *  the given point. */
        public int squaredDistanceFrom(int x, int y) {
            int xDist = this.x + width / 2 - x;
            int yDist = this.y + height / 2 - y;
            return xDist * xDist + yDist * yDist;
        }

        /** Returns the drawable state for the key, based on the current state
         *  and type of the key. */
        public int[] getCurrentDrawableState() {
            int[] states = KEY_STATE_NORMAL;
            if (on) {
                if (pressed) {
                    states = KEY_STATE_PRESSED_ON;
                } else {
                    states = KEY_STATE_NORMAL_ON;
                }
            } else {
                if (sticky) {
                    if (pressed) {
                        states = KEY_STATE_PRESSED_OFF;
                    } else {
                        states = KEY_STATE_NORMAL_OFF;
                    }
                } else {
                    if (pressed) {
                        states = KEY_STATE_PRESSED;
                    }
                }
            }
            return states;
        }
    }

    /** Creates a keyboard from the given xml key layout file. */
    public Keyboard(Context context, int xmlLayoutResId) {
        this(context, xmlLayoutResId, 0);
    }

    /** Creates a keyboard from the given xml key layout file, for the given
     *  display dimensions rather than the real screen's. */
    public Keyboard(Context context, int xmlLayoutResId, int modeId, int width, int height) {
        mDisplayWidth = width;
        mDisplayHeight = height;
        mDefaultHorizontalGap = 0;
        mDefaultWidth = mDisplayWidth / 10;
        mDefaultVerticalGap = 0;
        mDefaultHeight = mDefaultWidth;
        mKeys = new ArrayList<Key>();
        mModifierKeys = new ArrayList<Key>();
        mKeyboardMode = modeId;
        loadKeyboard(context, context.getResources().getXml(xmlLayoutResId));
    }

    /** Creates a keyboard from the given xml key layout file, restricted to
     *  rows whose keyboardMode matches modeId (or which declare no mode). */
    public Keyboard(Context context, int xmlLayoutResId, int modeId) {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        mDisplayWidth = dm.widthPixels;
        mDisplayHeight = dm.heightPixels;
        mDefaultHorizontalGap = 0;
        mDefaultWidth = mDisplayWidth / 10;
        mDefaultVerticalGap = 0;
        mDefaultHeight = mDefaultWidth;
        mKeys = new ArrayList<Key>();
        mModifierKeys = new ArrayList<Key>();
        mKeyboardMode = modeId;
        loadKeyboard(context, context.getResources().getXml(xmlLayoutResId));
    }

    /** Creates a blank keyboard from the given resource file and populates it
     *  with the specified characters in left-to-right, top-to-bottom fashion,
     *  using the specified number of columns. This is the constructor a popup
     *  ("mini") keyboard is built with. */
    public Keyboard(Context context, int layoutTemplateResId, CharSequence characters,
            int columns, int horizontalPadding) {
        this(context, layoutTemplateResId);
        int x = 0;
        int y = 0;
        int column = 0;
        mTotalWidth = 0;

        Row row = new Row(this);
        row.defaultHeight = mDefaultHeight;
        row.defaultWidth = mDefaultWidth;
        row.defaultHorizontalGap = mDefaultHorizontalGap;
        row.verticalGap = mDefaultVerticalGap;
        row.rowEdgeFlags = EDGE_TOP | EDGE_BOTTOM;
        final int maxColumns = columns == -1 ? Integer.MAX_VALUE : columns;
        for (int i = 0; i < characters.length(); i++) {
            char c = characters.charAt(i);
            if (column >= maxColumns
                    || x + mDefaultWidth + horizontalPadding > mDisplayWidth) {
                x = 0;
                y += mDefaultVerticalGap + mDefaultHeight;
                column = 0;
            }
            final Key key = new Key(row);
            key.x = x;
            key.y = y;
            key.label = String.valueOf(c);
            key.codes = new int[] { c };
            column++;
            x += key.width + key.gap;
            mKeys.add(key);
            row.mKeys.add(key);
            if (x > mTotalWidth) {
                mTotalWidth = x;
            }
        }
        mTotalHeight = y + mDefaultHeight;
        rows.add(row);
    }

    /** Package-private in AOSP; KeyboardView calls it from onSizeChanged so a
     *  keyboard laid out for one width scales down into a narrower view. */
    final void resize(int newWidth, int newHeight) {
        int numRows = rows.size();
        for (int rowIndex = 0; rowIndex < numRows; ++rowIndex) {
            Row row = rows.get(rowIndex);
            int numKeys = row.mKeys.size();
            int totalGap = 0;
            int totalWidth = 0;
            for (int keyIndex = 0; keyIndex < numKeys; ++keyIndex) {
                Key key = row.mKeys.get(keyIndex);
                if (keyIndex > 0) {
                    totalGap += key.gap;
                }
                totalWidth += key.width;
            }
            if (totalGap + totalWidth > newWidth) {
                int x = 0;
                float scaleFactor = (float) (newWidth - totalGap) / totalWidth;
                for (int keyIndex = 0; keyIndex < numKeys; ++keyIndex) {
                    Key key = row.mKeys.get(keyIndex);
                    key.width *= scaleFactor;
                    key.x = x;
                    x += key.width + key.gap;
                }
            }
        }
        mTotalWidth = newWidth;
        // AOSP does NOT touch mTotalHeight here; height is not rescaled.
    }

    public List<Key> getKeys() {
        return mKeys;
    }

    public List<Key> getModifierKeys() {
        return mModifierKeys;
    }

    protected int getHorizontalGap() {
        return mDefaultHorizontalGap;
    }

    protected void setHorizontalGap(int gap) {
        mDefaultHorizontalGap = gap;
    }

    protected int getVerticalGap() {
        return mDefaultVerticalGap;
    }

    protected void setVerticalGap(int gap) {
        mDefaultVerticalGap = gap;
    }

    protected int getKeyHeight() {
        return mDefaultHeight;
    }

    protected void setKeyHeight(int height) {
        mDefaultHeight = height;
    }

    protected int getKeyWidth() {
        return mDefaultWidth;
    }

    protected void setKeyWidth(int width) {
        mDefaultWidth = width;
    }

    /** Returns the total height of the keyboard. */
    public int getHeight() {
        return mTotalHeight;
    }

    public int getMinWidth() {
        return mTotalWidth;
    }

    /** AOSP pushes the shift state onto every shift key and reports whether the
     *  keyboard's own state actually changed (the caller uses the return to
     *  decide whether to redraw). */
    public boolean setShifted(boolean shiftState) {
        for (Key shiftKey : mShiftKeys) {
            if (shiftKey != null) {
                shiftKey.on = shiftState;
            }
        }
        if (mShifted != shiftState) {
            mShifted = shiftState;
            return true;
        }
        return false;
    }

    public boolean isShifted() {
        return mShifted;
    }

    /** @hide in AOSP, but part of how KeyboardView invalidates both shift keys. */
    public int[] getShiftKeyIndices() {
        return mShiftKeyIndices;
    }

    public int getShiftKeyIndex() {
        return mShiftKeyIndices[0];
    }

    private void computeNearestNeighbors() {
        mCellWidth = (getMinWidth() + GRID_WIDTH - 1) / GRID_WIDTH;
        mCellHeight = (getHeight() + GRID_HEIGHT - 1) / GRID_HEIGHT;
        mGridNeighbors = new int[GRID_SIZE][];
        int[] indices = new int[mKeys.size()];
        final int gridWidth = GRID_WIDTH * mCellWidth;
        final int gridHeight = GRID_HEIGHT * mCellHeight;
        for (int x = 0; x < gridWidth; x += mCellWidth) {
            for (int y = 0; y < gridHeight; y += mCellHeight) {
                int count = 0;
                for (int i = 0; i < mKeys.size(); i++) {
                    final Key key = mKeys.get(i);
                    if (key.squaredDistanceFrom(x, y) < mProximityThreshold
                            || key.squaredDistanceFrom(x + mCellWidth - 1, y)
                                    < mProximityThreshold
                            || key.squaredDistanceFrom(x + mCellWidth - 1, y + mCellHeight - 1)
                                    < mProximityThreshold
                            || key.squaredDistanceFrom(x, y + mCellHeight - 1)
                                    < mProximityThreshold) {
                        indices[count++] = i;
                    }
                }
                int[] cell = new int[count];
                System.arraycopy(indices, 0, cell, 0, count);
                mGridNeighbors[(y / mCellHeight) * GRID_WIDTH + (x / mCellWidth)] = cell;
            }
        }
    }

    /** Returns the indices of the keys that are closest to the given point. */
    public int[] getNearestKeys(int x, int y) {
        if (mGridNeighbors == null) computeNearestNeighbors();
        if (mCellWidth > 0 && mCellHeight > 0
                && x >= 0 && x < getMinWidth() && y >= 0 && y < getHeight()) {
            int index = (y / mCellHeight) * GRID_WIDTH + (x / mCellWidth);
            if (index < GRID_SIZE) {
                return mGridNeighbors[index];
            }
        }
        return new int[0];
    }

    protected Row createRowFromXml(Resources res, XmlResourceParser parser) {
        return new Row(res, this, parser);
    }

    protected Key createKeyFromXml(Resources res, Row parent, int x, int y,
            XmlResourceParser parser) {
        return new Key(res, parent, x, y, parser);
    }

    private void loadKeyboard(Context context, XmlResourceParser parser) {
        boolean inKey = false;
        boolean inRow = false;
        int x = 0;
        int y = 0;
        Key key = null;
        Row currentRow = null;
        Resources res = context.getResources();
        boolean skipRow = false;
        try {
            int event;
            while ((event = parser.next()) != XmlResourceParser.END_DOCUMENT) {
                if (event == XmlResourceParser.START_TAG) {
                    String tag = parser.getName();
                    if (TAG_ROW.equals(tag)) {
                        inRow = true;
                        x = 0;
                        currentRow = createRowFromXml(res, parser);
                        rows.add(currentRow);
                        skipRow = currentRow.mode != 0 && currentRow.mode != mKeyboardMode;
                        if (skipRow) {
                            skipToEndOfRow(parser);
                            inRow = false;
                        }
                    } else if (TAG_KEY.equals(tag)) {
                        inKey = true;
                        key = createKeyFromXml(res, currentRow, x, y, parser);
                        mKeys.add(key);
                        if (key.codes != null && key.codes.length > 0
                                && key.codes[0] == KEYCODE_SHIFT) {
                            for (int i = 0; i < mShiftKeys.length; i++) {
                                if (mShiftKeys[i] == null) {
                                    mShiftKeys[i] = key;
                                    mShiftKeyIndices[i] = mKeys.size() - 1;
                                    break;
                                }
                            }
                            mModifierKeys.add(key);
                        } else if (key.codes != null && key.codes.length > 0
                                && key.codes[0] == KEYCODE_ALT) {
                            mModifierKeys.add(key);
                        }
                        currentRow.mKeys.add(key);
                    } else if (TAG_KEYBOARD.equals(tag)) {
                        parseKeyboardAttributes(res, parser);
                    }
                } else if (event == XmlResourceParser.END_TAG) {
                    if (inKey) {
                        inKey = false;
                        x += key.gap + key.width;
                        if (x > mTotalWidth) {
                            mTotalWidth = x;
                        }
                    } else if (inRow) {
                        inRow = false;
                        y += currentRow.verticalGap;
                        y += currentRow.defaultHeight;
                    }
                }
            }
        } catch (Exception e) {
            // AOSP logs the parse error and keeps whatever was built so far,
            // rather than throwing out of a keyboard constructor.
            android.util.Log.e(TAG, "Parse error:" + e);
        }
        mTotalHeight = y - mDefaultVerticalGap;
    }

    private void skipToEndOfRow(XmlResourceParser parser)
            throws org.xmlpull.v1.XmlPullParserException, java.io.IOException {
        int event;
        while ((event = parser.next()) != XmlResourceParser.END_DOCUMENT) {
            if (event == XmlResourceParser.END_TAG && TAG_ROW.equals(parser.getName())) {
                break;
            }
        }
    }

    private void parseKeyboardAttributes(Resources res, XmlResourceParser parser) {
        mDefaultWidth = getDimensionOrFraction(res, parser, "keyWidth",
                mDisplayWidth, mDisplayWidth / 10);
        mDefaultHeight = getDimensionOrFraction(res, parser, "keyHeight",
                mDisplayHeight, 50);
        mDefaultHorizontalGap = getDimensionOrFraction(res, parser, "horizontalGap",
                mDisplayWidth, 0);
        mDefaultVerticalGap = getDimensionOrFraction(res, parser, "verticalGap",
                mDisplayHeight, 0);
        mProximityThreshold = (int) (mDefaultWidth * SEARCH_DISTANCE);
        mProximityThreshold = mProximityThreshold * mProximityThreshold; // squared for comparison
    }

    // -----------------------------------------------------------------------
    // Attribute readers.
    //
    // These stand in for AOSP's TypedArray-over-internal-styleable reads (see
    // the class comment). Each one reproduces the TYPE RULES of the attribute
    // it replaces, taken from AOSP core/res/res/values/attrs.xml.
    // -----------------------------------------------------------------------

    /** AOSP getDimensionOrFraction(TypedArray, index, base, defValue): a
     *  TYPE_DIMENSION is read as a pixel offset, a TYPE_FRACTION is resolved
     *  against `base` and rounded, and ANY other type (including absent) falls
     *  back to defValue.
     *
     *  Here the same three cases are recognised from the raw attribute text:
     *  a "@..." resource reference is a dimension resource, a "N%" / "N%p"
     *  suffix is a fraction, and "48dp" / "48px" / bare number is a dimension.
     *  Anything else returns defValue, matching AOSP's fall-through. */
    static int getDimensionOrFraction(Resources res, XmlResourceParser parser, String name,
            int base, int defValue) {
        String v = parser.getAttributeValue(ANDROID_NS, name);
        if (v == null) return defValue;
        v = v.trim();
        if (v.isEmpty()) return defValue;

        if (v.charAt(0) == '@' || v.charAt(0) == '?') {
            int id = parser.getAttributeResourceValue(ANDROID_NS, name, 0);
            if (id == 0 || res == null) return defValue;
            try {
                return res.getDimensionPixelOffset(id);
            } catch (RuntimeException e) {
                return defValue;
            }
        }

        if (v.endsWith("%p") || v.endsWith("%")) {
            String num = v.endsWith("%p") ? v.substring(0, v.length() - 2)
                                          : v.substring(0, v.length() - 1);
            try {
                // AOSP: Math.round(a.getFraction(index, base, base, defValue)),
                // and a "%" fraction is value/100 of the base.
                return Math.round(Float.parseFloat(num.trim()) / 100f * base);
            } catch (NumberFormatException e) {
                return defValue;
            }
        }

        return parseDimensionValue(res, v, defValue);
    }

    /** "48dp" / "48dip" / "48sp" / "48px" / "48pt" / "48in" / "48mm" / "48"
     *  converted through TypedValue.applyDimension against the real display
     *  metrics, then truncated -- AOSP's getDimensionPixelOffset() truncates
     *  rather than rounding, which is why this uses (int) and not Math.round. */
    static int parseDimensionValue(Resources res, String v, int defValue) {
        int unit;
        String num;
        String lower = v.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith("dip"))      { unit = TypedValue.COMPLEX_UNIT_DIP; num = v.substring(0, v.length() - 3); }
        else if (lower.endsWith("dp"))  { unit = TypedValue.COMPLEX_UNIT_DIP; num = v.substring(0, v.length() - 2); }
        else if (lower.endsWith("sp"))  { unit = TypedValue.COMPLEX_UNIT_SP;  num = v.substring(0, v.length() - 2); }
        else if (lower.endsWith("px"))  { unit = TypedValue.COMPLEX_UNIT_PX;  num = v.substring(0, v.length() - 2); }
        else if (lower.endsWith("pt"))  { unit = TypedValue.COMPLEX_UNIT_PT;  num = v.substring(0, v.length() - 2); }
        else if (lower.endsWith("in"))  { unit = TypedValue.COMPLEX_UNIT_IN;  num = v.substring(0, v.length() - 2); }
        else if (lower.endsWith("mm"))  { unit = TypedValue.COMPLEX_UNIT_MM;  num = v.substring(0, v.length() - 2); }
        else                            { unit = TypedValue.COMPLEX_UNIT_PX;  num = v; }
        try {
            float value = Float.parseFloat(num.trim());
            DisplayMetrics dm = res != null ? res.getDisplayMetrics() : null;
            if (dm == null) return (int) value;
            return (int) TypedValue.applyDimension(unit, value, dm);
        } catch (NumberFormatException e) {
            return defValue;
        }
    }

    /** AOSP reads rowEdgeFlags / keyEdgeFlags as a TypedArray getInt over a
     *  flags attr. The flag names and values are AOSP's own attrs.xml:
     *  rowEdgeFlags { top=4, bottom=8 }, keyEdgeFlags { left=1, right=2 }.
     *  Compiled resources arrive as an integer, so a numeric value is honoured
     *  first; a decoded XML tree gives the names, possibly OR'd with '|'. */
    static int readEdgeFlags(XmlResourceParser parser, String name) {
        String v = parser.getAttributeValue(ANDROID_NS, name);
        if (v == null) return 0;
        v = v.trim();
        if (v.isEmpty()) return 0;
        try {
            return Integer.decode(v);
        } catch (NumberFormatException ignored) {
            // fall through to the symbolic form
        }
        int flags = 0;
        for (String part : v.split("\\|")) {
            switch (part.trim()) {
                case "left":   flags |= EDGE_LEFT;   break;
                case "right":  flags |= EDGE_RIGHT;  break;
                case "top":    flags |= EDGE_TOP;    break;
                case "bottom": flags |= EDGE_BOTTOM; break;
                default: break;
            }
        }
        return flags;
    }

    static boolean readBoolean(XmlResourceParser parser, String name, boolean defValue) {
        String v = parser.getAttributeValue(ANDROID_NS, name);
        if (v == null) return defValue;
        v = v.trim();
        if ("true".equalsIgnoreCase(v)) return true;
        if ("false".equalsIgnoreCase(v)) return false;
        // A compiled boolean resource arrives as 0/-1 (AOSP's TYPE_INT_BOOLEAN).
        try {
            return Integer.decode(v) != 0;
        } catch (NumberFormatException e) {
            return defValue;
        }
    }

    /** A string attribute that may be a literal or an "@string/..." reference,
     *  matching TypedArray.getText()'s handling of format="string". */
    static CharSequence readText(Resources res, XmlResourceParser parser, String name) {
        String v = parser.getAttributeValue(ANDROID_NS, name);
        if (v == null) return null;
        if (!v.isEmpty() && v.charAt(0) == '@') {
            int id = parser.getAttributeResourceValue(ANDROID_NS, name, 0);
            if (id != 0 && res != null) {
                try {
                    CharSequence t = res.getText(id);
                    if (t != null) return t;
                } catch (RuntimeException ignored) {
                    // fall through to the literal
                }
            }
        }
        return v;
    }

    /** format="reference" drawable, matching TypedArray.getDrawable(). */
    static Drawable readDrawable(Resources res, XmlResourceParser parser, String name) {
        if (res == null) return null;
        int id = parser.getAttributeResourceValue(ANDROID_NS, name, 0);
        if (id == 0) return null;
        try {
            return res.getDrawable(id);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
