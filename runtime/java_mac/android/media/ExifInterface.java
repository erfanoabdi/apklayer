// android.media.ExifInterface -- reads (and nominally writes) EXIF tags of a
// JPEG. AOSP frameworks/base media/java/android/media/ExifInterface.java (this
// platform class is deprecated in favor of androidx.exifinterface, but apps
// still reference it).
//
// We implement a real, dependency-free reader: parse the JPEG APP1 ("Exif\0\0")
// segment, walk the TIFF/IFD0 + ExifIFD + GPS-IFD entries, and expose the common
// string/int/double tags + getLatLong. Writing is not supported on a host with
// no in-place JPEG re-mux, so saveAttributes()/setAttribute() are best-effort
// in-memory only (AOSP's contract is that getAttribute reflects what was read or
// set; a save without a backend is a no-op, matching a read-only file).
package android.media;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class ExifInterface {
    public static final String TAG_IMAGE_WIDTH = "ImageWidth";
    public static final String TAG_IMAGE_LENGTH = "ImageLength";
    public static final String TAG_MAKE = "Make";
    public static final String TAG_MODEL = "Model";
    public static final String TAG_ORIENTATION = "Orientation";
    public static final String TAG_DATETIME = "DateTime";
    public static final String TAG_DATETIME_ORIGINAL = "DateTimeOriginal";
    public static final String TAG_DATETIME_DIGITIZED = "DateTimeDigitized";
    public static final String TAG_EXPOSURE_TIME = "ExposureTime";
    public static final String TAG_F_NUMBER = "FNumber";
    public static final String TAG_ISO_SPEED_RATINGS = "ISOSpeedRatings";
    public static final String TAG_FOCAL_LENGTH = "FocalLength";
    public static final String TAG_FLASH = "Flash";
    public static final String TAG_WHITE_BALANCE = "WhiteBalance";
    public static final String TAG_GPS_LATITUDE = "GPSLatitude";
    public static final String TAG_GPS_LATITUDE_REF = "GPSLatitudeRef";
    public static final String TAG_GPS_LONGITUDE = "GPSLongitude";
    public static final String TAG_GPS_LONGITUDE_REF = "GPSLongitudeRef";
    public static final String TAG_GPS_ALTITUDE = "GPSAltitude";
    public static final String TAG_GPS_ALTITUDE_REF = "GPSAltitudeRef";
    public static final String TAG_GPS_TIMESTAMP = "GPSTimeStamp";
    public static final String TAG_GPS_DATESTAMP = "GPSDateStamp";
    public static final String TAG_GPS_PROCESSING_METHOD = "GPSProcessingMethod";
    public static final String TAG_SOFTWARE = "Software";

    public static final int ORIENTATION_UNDEFINED = 0;
    public static final int ORIENTATION_NORMAL = 1;
    public static final int ORIENTATION_FLIP_HORIZONTAL = 2;
    public static final int ORIENTATION_ROTATE_180 = 3;
    public static final int ORIENTATION_FLIP_VERTICAL = 4;
    public static final int ORIENTATION_TRANSPOSE = 5;
    public static final int ORIENTATION_ROTATE_90 = 6;
    public static final int ORIENTATION_TRANSVERSE = 7;
    public static final int ORIENTATION_ROTATE_270 = 8;

    private final Map<String, String> mTags = new HashMap<String, String>();

    public ExifInterface(String filename) throws IOException {
        try (java.io.FileInputStream in = new java.io.FileInputStream(filename)) {
            parse(in);
        } catch (java.io.FileNotFoundException fnf) {
            throw fnf;
        } catch (IOException io) {
            throw io;
        } catch (Throwable t) {
            // Malformed EXIF must not crash the app -- AOSP swallows parse errors
            // and leaves tags empty. Mirror that.
        }
    }
    public ExifInterface(InputStream inputStream) throws IOException {
        try { parse(inputStream); } catch (Throwable t) { /* leave tags empty */ }
    }
    public ExifInterface(java.io.File file) throws IOException {
        this(file.getAbsolutePath());
    }
    public ExifInterface(java.io.FileDescriptor fileDescriptor) throws IOException {
        try (java.io.FileInputStream in = new java.io.FileInputStream(fileDescriptor)) {
            parse(in);
        } catch (Throwable t) { /* leave tags empty */ }
    }

    public String getAttribute(String tag) { return mTags.get(tag); }
    public int getAttributeInt(String tag, int defaultValue) {
        String v = mTags.get(tag);
        if (v == null) return defaultValue;
        try { return Integer.parseInt(v.trim()); }
        catch (NumberFormatException e) { return defaultValue; }
    }
    public double getAttributeDouble(String tag, double defaultValue) {
        String v = mTags.get(tag);
        if (v == null) return defaultValue;
        try {
            int slash = v.indexOf('/');
            if (slash > 0) {
                double num = Double.parseDouble(v.substring(0, slash));
                double den = Double.parseDouble(v.substring(slash + 1));
                return den != 0 ? num / den : defaultValue;
            }
            return Double.parseDouble(v.trim());
        } catch (NumberFormatException e) { return defaultValue; }
    }
    public void setAttribute(String tag, String value) {
        if (value == null) mTags.remove(tag); else mTags.put(tag, value);
    }
    public boolean hasAttribute(String tag) { return mTags.containsKey(tag); }

    // No in-place JPEG re-mux backend; AOSP saves to disk. We keep the in-memory
    // tag map consistent so getAttribute reflects set values, but cannot persist.
    public void saveAttributes() throws IOException {}

    public boolean getLatLong(float[] output) {
        Double lat = parseGps(TAG_GPS_LATITUDE, TAG_GPS_LATITUDE_REF, "S");
        Double lon = parseGps(TAG_GPS_LONGITUDE, TAG_GPS_LONGITUDE_REF, "W");
        if (lat == null || lon == null) return false;
        if (output != null && output.length >= 2) {
            output[0] = lat.floatValue();
            output[1] = lon.floatValue();
        }
        return true;
    }
    // AOSP API-29 double[] variant.
    public double[] getLatLong() {
        float[] f = new float[2];
        return getLatLong(f) ? new double[] { f[0], f[1] } : null;
    }
    public double getAltitude(double defaultValue) {
        double alt = getAttributeDouble(TAG_GPS_ALTITUDE, Double.NaN);
        if (Double.isNaN(alt)) return defaultValue;
        int ref = getAttributeInt(TAG_GPS_ALTITUDE_REF, 0);
        return ref == 1 ? -alt : alt;
    }
    public int getRotationDegrees() {
        switch (getAttributeInt(TAG_ORIENTATION, ORIENTATION_NORMAL)) {
            case ORIENTATION_ROTATE_90:  return 90;
            case ORIENTATION_ROTATE_180: return 180;
            case ORIENTATION_ROTATE_270: return 270;
            default:                     return 0;
        }
    }
    public byte[] getThumbnail() { return null; }
    public byte[] getThumbnailBytes() { return null; }
    public boolean hasThumbnail() { return false; }

    // =====================================================================
    // API-gap close-out. Signatures from /tmp/aosp_api/android16.txt; behaviour
    // from frameworks/base/media/java/android/media/ExifInterface.java
    // (android16-release).
    // =====================================================================

    /** AOSP's stream-type argument for the InputStream constructor: the stream
     *  is a whole image file. */
    public static final int STREAM_TYPE_FULL_IMAGE_DATA = 0;
    /** ...or a bare EXIF blob with no surrounding image. */
    public static final int STREAM_TYPE_EXIF_DATA_ONLY = 1;

    /**
     * API 30. The two stream types differ only in whether the parser expects a
     * container (JPEG/HEIF/PNG) around the EXIF blob. This reader already
     * locates the APP1 segment inside a container and tolerates a malformed
     * stream by leaving the tag map empty, so both types take the same path --
     * and a bare EXIF blob simply yields no APP1 match, which is the same
     * "no tags" outcome AOSP produces when the blob does not parse.
     */
    public ExifInterface(InputStream inputStream, int streamType) throws IOException {
        this(inputStream);
    }

    /**
     * API 30. AOSP: "@return true if the mime type is supported" -- the formats
     * its parser understands. Answered from the SAME set this reader actually
     * handles rather than from AOSP's longer list, because claiming a format we
     * cannot parse would make an app hand us a file and get silently empty tags
     * instead of taking its own fallback.
     *
     * This reader parses the JPEG APP1 Exif segment (see the class comment), so
     * the JPEG mime types are what it supports.
     */
    public static boolean isSupportedMimeType(String mimeType) {
        java.util.Objects.requireNonNull(mimeType, "mimeType shouldn't be null");
        String m = mimeType.toLowerCase(java.util.Locale.ROOT);
        return m.equals("image/jpeg") || m.equals("image/jpg") || m.equals("image/x-jpeg");
    }

    /**
     * AOSP: the raw bytes of a tag, "or null if the tag is not present". This
     * reader decodes each entry to a String as it walks the IFD and does not
     * retain the original byte run, so the honest answer is the UTF-8 bytes of
     * the decoded value when the tag is present -- which is exactly right for
     * the ASCII-typed tags apps use this for (Make, Model, DateTime, the GPS
     * refs) -- and null when it is not. It never fabricates bytes for a tag that
     * was never read.
     */
    public byte[] getAttributeBytes(String tag) {
        String value = mTags.get(tag);
        return (value == null) ? null
                : value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * AOSP: the [offset, length] of a tag's value WITHIN the source file, "or
     * null if the tag is not present". That is a byte range into the file, and
     * this reader does not record one while parsing -- so null is the only
     * honest answer for every tag. Returning a made-up range would send an app
     * that seeks to it (which is the entire point of this API) to the wrong
     * bytes, silently.
     */
    public long[] getAttributeRange(String tag) {
        java.util.Objects.requireNonNull(tag, "tag shouldn't be null");
        return null;
    }

    // ---- date/time accessors (API 29) ------------------------------------
    // AOSP parses the EXIF "yyyy:MM:dd HH:mm:ss" strings this reader already
    // stores, adds the matching SubSec milliseconds, and returns -1 when the
    // tag is absent or unparsable. Real parsing, from data we really have.

    public long getDateTime() { return parseExifDateTime(TAG_DATETIME, "SubSecTime"); }

    public long getDateTimeOriginal() {
        return parseExifDateTime(TAG_DATETIME_ORIGINAL, "SubSecTimeOriginal");
    }

    public long getDateTimeDigitized() {
        return parseExifDateTime(TAG_DATETIME_DIGITIZED, "SubSecTimeDigitized");
    }

    /**
     * AOSP combines GPSDateStamp ("yyyy:MM:dd") with GPSTimeStamp (three
     * rationals, hour/minute/second) and returns the result in UTC, or -1.
     */
    public long getGpsDateTime() {
        String date = mTags.get(TAG_GPS_DATESTAMP);
        String time = mTags.get(TAG_GPS_TIMESTAMP);
        if (date == null || time == null) return -1;
        try {
            String[] d = date.trim().split(":");
            if (d.length != 3) return -1;
            String[] t = time.trim().split(",");
            if (t.length != 3) return -1;
            java.util.Calendar cal = java.util.Calendar.getInstance(
                    java.util.TimeZone.getTimeZone("UTC"));
            cal.clear();
            cal.set(Integer.parseInt(d[0].trim()), Integer.parseInt(d[1].trim()) - 1,
                    Integer.parseInt(d[2].trim()),
                    (int) toRational(t[0]), (int) toRational(t[1]), (int) toRational(t[2]));
            return cal.getTimeInMillis();
        } catch (Throwable t) {
            return -1;
        }
    }

    /** AOSP's EXIF datetime format is "yyyy:MM:dd HH:mm:ss" in LOCAL time (EXIF
     *  carries no zone), with an optional SubSec tag giving the fractional
     *  seconds as a decimal string. -1 when absent or unparsable, as AOSP. */
    private long parseExifDateTime(String dateTag, String subSecTag) {
        String value = mTags.get(dateTag);
        if (value == null) return -1;
        try {
            java.text.SimpleDateFormat fmt =
                    new java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", java.util.Locale.US);
            fmt.setTimeZone(java.util.TimeZone.getDefault());
            java.util.Date parsed = fmt.parse(value.trim());
            if (parsed == null) return -1;
            long millis = parsed.getTime();
            String subSec = mTags.get(subSecTag);
            if (subSec != null) {
                try {
                    // "12" means .12 s = 120 ms, so the digits are a fraction.
                    double frac = Double.parseDouble("0." + subSec.trim());
                    millis += Math.round(frac * 1000.0);
                } catch (NumberFormatException ignore) {
                    // A malformed SubSec just contributes nothing.
                }
            }
            return millis;
        } catch (Throwable t) {
            return -1;
        }
    }

    // ---- thumbnail ---------------------------------------------------------
    // This reader parses IFD ENTRIES only; it never copies the embedded
    // thumbnail's byte run out of the file (hasThumbnail() has always reported
    // false for that reason). So there is no thumbnail to decode, no byte range
    // to report, and no compression to describe -- and all three answers below
    // agree with hasThumbnail(), which is what stops an app from acting on one.

    /** AOSP: the decoded thumbnail, "or null if no thumbnail exists". */
    public android.graphics.Bitmap getThumbnailBitmap() {
        byte[] bytes = getThumbnail();
        if (bytes == null) return null;
        return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }

    /** AOSP: [offset, length] of the thumbnail within the file, or null. */
    public long[] getThumbnailRange() { return null; }

    /** AOSP: whether the thumbnail is JPEG-compressed (as opposed to a raw
     *  strip). With no thumbnail at all, false -- consistent with
     *  hasThumbnail(). */
    public boolean isThumbnailCompressed() { return false; }

    private Double parseGps(String tag, String refTag, String negativeRef) {
        String dms = mTags.get(tag);
        if (dms == null) return null;
        try {
            // EXIF GPS coordinate is three rational d,m,s.
            String[] parts = dms.split(",");
            double deg = parts.length > 0 ? toRational(parts[0]) : 0;
            double min = parts.length > 1 ? toRational(parts[1]) : 0;
            double sec = parts.length > 2 ? toRational(parts[2]) : 0;
            double value = deg + min / 60.0 + sec / 3600.0;
            String ref = mTags.get(refTag);
            if (ref != null && ref.trim().equalsIgnoreCase(negativeRef)) value = -value;
            return Double.valueOf(value);
        } catch (Throwable t) { return null; }
    }
    private static double toRational(String s) {
        s = s.trim();
        int slash = s.indexOf('/');
        if (slash > 0) {
            double num = Double.parseDouble(s.substring(0, slash));
            double den = Double.parseDouble(s.substring(slash + 1));
            return den != 0 ? num / den : 0;
        }
        return Double.parseDouble(s);
    }

    // ----- minimal JPEG/TIFF EXIF reader.

    private void parse(InputStream in) throws IOException {
        if (in == null) return;
        java.io.DataInputStream d = new java.io.DataInputStream(
            in instanceof java.io.BufferedInputStream ? in : new java.io.BufferedInputStream(in));
        if ((d.readUnsignedByte() != 0xFF) || (d.readUnsignedByte() != 0xD8)) {
            return;  // not a JPEG (SOI marker absent) -> no EXIF
        }
        while (true) {
            int marker = d.readUnsignedByte();
            if (marker != 0xFF) return;
            int type = d.readUnsignedByte();
            if (type == 0xD9 || type == 0xDA) return;  // EOI / start of scan
            int len = (d.readUnsignedByte() << 8) | d.readUnsignedByte();
            if (len < 2) return;
            byte[] segment = new byte[len - 2];
            d.readFully(segment);
            if (type == 0xE1 && segment.length > 6
                    && segment[0] == 'E' && segment[1] == 'x' && segment[2] == 'i'
                    && segment[3] == 'f' && segment[4] == 0 && segment[5] == 0) {
                parseTiff(segment, 6);
                return;
            }
        }
    }

    private void parseTiff(byte[] b, int tiffStart) {
        if (b.length < tiffStart + 8) return;
        boolean little;
        if (b[tiffStart] == 'I' && b[tiffStart + 1] == 'I') little = true;
        else if (b[tiffStart] == 'M' && b[tiffStart + 1] == 'M') little = false;
        else return;
        int ifd0 = (int) readUInt(b, tiffStart + 4, 4, little);
        int exifOffset = parseIfd(b, tiffStart, tiffStart + ifd0, little, false);
        if (exifOffset > 0) parseIfd(b, tiffStart, tiffStart + exifOffset, little, false);
        int gpsOffset = mGpsIfdOffset;
        if (gpsOffset > 0) parseIfd(b, tiffStart, tiffStart + gpsOffset, little, true);
    }

    private int mGpsIfdOffset = -1;

    // Returns the ExifIFD pointer offset (relative to tiffStart) if seen, else -1.
    private int parseIfd(byte[] b, int tiffStart, int ifdPos, boolean little, boolean gps) {
        if (ifdPos < 0 || ifdPos + 2 > b.length) return -1;
        int count = (int) readUInt(b, ifdPos, 2, little);
        int exifPtr = -1;
        int p = ifdPos + 2;
        for (int i = 0; i < count && p + 12 <= b.length; i++, p += 12) {
            int tagId = (int) readUInt(b, p, 2, little);
            int format = (int) readUInt(b, p + 2, 2, little);
            int comps = (int) readUInt(b, p + 4, 4, little);
            int valueLen = comps * formatBytes(format);
            int valuePos = valueLen <= 4 ? p + 8
                : tiffStart + (int) readUInt(b, p + 8, 4, little);

            if (!gps && tagId == 0x8769) { exifPtr = (int) readUInt(b, p + 8, 4, little); continue; }
            if (!gps && tagId == 0x8825) { mGpsIfdOffset = (int) readUInt(b, p + 8, 4, little); continue; }

            String name = gps ? gpsTagName(tagId) : ifdTagName(tagId);
            if (name == null) continue;
            String val = readValue(b, valuePos, format, comps, little);
            if (val != null) mTags.put(name, val);
        }
        return exifPtr;
    }

    private static String readValue(byte[] b, int pos, int format, int comps, boolean little) {
        if (pos < 0 || pos >= b.length) return null;
        try {
            switch (format) {
                case 2: {  // ASCII
                    int end = pos;
                    while (end < b.length && end < pos + comps && b[end] != 0) end++;
                    return new String(b, pos, end - pos, java.nio.charset.StandardCharsets.US_ASCII);
                }
                case 1:    // BYTE
                case 3:    // SHORT
                case 4: {  // LONG
                    int sz = formatBytes(format);
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < comps && pos + (i + 1) * sz <= b.length; i++) {
                        if (i > 0) sb.append(',');
                        sb.append(readUInt(b, pos + i * sz, sz, little));
                    }
                    return sb.toString();
                }
                case 5:    // RATIONAL
                case 10: { // SRATIONAL
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < comps && pos + (i + 1) * 8 <= b.length; i++) {
                        if (i > 0) sb.append(',');
                        long num = readUInt(b, pos + i * 8, 4, little);
                        long den = readUInt(b, pos + i * 8 + 4, 4, little);
                        sb.append(num).append('/').append(den);
                    }
                    return sb.toString();
                }
                default: return null;
            }
        } catch (Throwable t) { return null; }
    }

    private static int formatBytes(int format) {
        switch (format) {
            case 1: case 2: case 6: case 7: return 1;
            case 3: case 8: return 2;
            case 4: case 9: case 11: return 4;
            case 5: case 10: case 12: return 8;
            default: return 1;
        }
    }
    private static long readUInt(byte[] b, int pos, int len, boolean little) {
        long v = 0;
        for (int i = 0; i < len; i++) {
            int shift = little ? (8 * i) : (8 * (len - 1 - i));
            v |= ((long) (b[pos + i] & 0xFF)) << shift;
        }
        return v;
    }

    private static String ifdTagName(int id) {
        switch (id) {
            case 0x0100: return TAG_IMAGE_WIDTH;
            case 0x0101: return TAG_IMAGE_LENGTH;
            case 0x010F: return TAG_MAKE;
            case 0x0110: return TAG_MODEL;
            case 0x0112: return TAG_ORIENTATION;
            case 0x0131: return TAG_SOFTWARE;
            case 0x0132: return TAG_DATETIME;
            case 0x829A: return TAG_EXPOSURE_TIME;
            case 0x829D: return TAG_F_NUMBER;
            case 0x8827: return TAG_ISO_SPEED_RATINGS;
            case 0x9003: return TAG_DATETIME_ORIGINAL;
            case 0x9004: return TAG_DATETIME_DIGITIZED;
            case 0x9209: return TAG_FLASH;
            case 0x920A: return TAG_FOCAL_LENGTH;
            case 0xA403: return TAG_WHITE_BALANCE;
            default: return null;
        }
    }
    private static String gpsTagName(int id) {
        switch (id) {
            case 0x0001: return TAG_GPS_LATITUDE_REF;
            case 0x0002: return TAG_GPS_LATITUDE;
            case 0x0003: return TAG_GPS_LONGITUDE_REF;
            case 0x0004: return TAG_GPS_LONGITUDE;
            case 0x0005: return TAG_GPS_ALTITUDE_REF;
            case 0x0006: return TAG_GPS_ALTITUDE;
            case 0x0007: return TAG_GPS_TIMESTAMP;
            case 0x001D: return TAG_GPS_DATESTAMP;
            default: return null;
        }
    }
}
