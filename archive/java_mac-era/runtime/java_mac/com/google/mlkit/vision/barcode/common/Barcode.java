package com.google.mlkit.vision.barcode.common;

import android.graphics.Rect;
import android.graphics.Point;

// com.google.mlkit.vision.barcode.common.Barcode -- a single decoded barcode.
// (The class moved to the vision.barcode.common artifact; the older
// vision.barcode.Barcode is a thin re-export.) Mac.apk decodes no barcodes (no
// model backend), so a scan Task fails and apps never receive a real Barcode via
// the success path; the type exists so success-listener generics and field reads
// link. Constants mirror the published format/type enums exactly because apps
// switch on getFormat()/getValueType(). Ref: developers.google.com/android/reference
public class Barcode {

    // Format constants (BarcodeFormat).
    public static final int FORMAT_UNKNOWN = -1;
    public static final int FORMAT_ALL_FORMATS = 0;
    public static final int FORMAT_CODE_128 = 1;
    public static final int FORMAT_CODE_39 = 2;
    public static final int FORMAT_CODE_93 = 4;
    public static final int FORMAT_CODABAR = 8;
    public static final int FORMAT_DATA_MATRIX = 16;
    public static final int FORMAT_EAN_13 = 32;
    public static final int FORMAT_EAN_8 = 64;
    public static final int FORMAT_ITF = 128;
    public static final int FORMAT_QR_CODE = 256;
    public static final int FORMAT_UPC_A = 512;
    public static final int FORMAT_UPC_E = 1024;
    public static final int FORMAT_PDF417 = 2048;
    public static final int FORMAT_AZTEC = 4096;

    // Value-type constants.
    public static final int TYPE_UNKNOWN = 0;
    public static final int TYPE_CONTACT_INFO = 1;
    public static final int TYPE_EMAIL = 2;
    public static final int TYPE_ISBN = 3;
    public static final int TYPE_PHONE = 4;
    public static final int TYPE_PRODUCT = 5;
    public static final int TYPE_SMS = 6;
    public static final int TYPE_TEXT = 7;
    public static final int TYPE_URL = 8;
    public static final int TYPE_WIFI = 9;
    public static final int TYPE_GEO = 10;
    public static final int TYPE_CALENDAR_EVENT = 11;
    public static final int TYPE_DRIVER_LICENSE = 12;

    private final String rawValue;
    private final byte[] rawBytes;
    private final String displayValue;
    private final int format;
    private final int valueType;
    private final Rect boundingBox;
    private final Point[] cornerPoints;

    public Barcode() { this(null, null, null, FORMAT_UNKNOWN, TYPE_UNKNOWN, null, null); }
    public Barcode(String rawValue, byte[] rawBytes, String displayValue, int format,
            int valueType, Rect boundingBox, Point[] cornerPoints) {
        this.rawValue = rawValue;
        this.rawBytes = rawBytes;
        this.displayValue = displayValue;
        this.format = format;
        this.valueType = valueType;
        this.boundingBox = boundingBox;
        this.cornerPoints = cornerPoints;
    }

    public String getRawValue() { return rawValue; }
    public byte[] getRawBytes() { return rawBytes; }
    public String getDisplayValue() { return displayValue; }
    public int getFormat() { return format; }
    public int getValueType() { return valueType; }
    public Rect getBoundingBox() { return boundingBox; }
    public Point[] getCornerPoints() { return cornerPoints; }
}
