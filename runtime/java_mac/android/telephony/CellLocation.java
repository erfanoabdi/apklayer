package android.telephony;
/** Base cell location (deprecated). No radio.
 *  Source: frameworks/base .../telephony/CellLocation.java */
public abstract class CellLocation {
    public static CellLocation getEmpty() { return null; }
    public static void requestLocationUpdate() { }
}
