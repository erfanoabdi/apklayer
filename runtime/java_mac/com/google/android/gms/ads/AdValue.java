// com.google.android.gms.ads.AdValue
//
// GMS Ads reference: the paid-event value reported via
// OnPaidEventListener. On a no-backend device no paid event ever fires,
// but apps reference the type, so we ship the full immutable shape.
// We never synthesize a paid event (that would be fabricated revenue).
package com.google.android.gms.ads;

public final class AdValue {
    // Precision-type constants from the GMS reference.
    public static final int PRECISION_TYPE_UNKNOWN = 0;
    public static final int PRECISION_TYPE_ESTIMATED = 1;
    public static final int PRECISION_TYPE_PUBLISHER_PROVIDED = 2;
    public static final int PRECISION_TYPE_PRECISE = 3;

    private final int precisionType;
    private final String currencyCode;
    private final long valueMicros;

    private AdValue(int precisionType, String currencyCode, long valueMicros) {
        this.precisionType = precisionType;
        this.currencyCode = currencyCode;
        this.valueMicros = valueMicros;
    }

    public static AdValue zza(int precisionType, String currencyCode, long valueMicros) {
        return new AdValue(precisionType, currencyCode, valueMicros);
    }

    public int getPrecisionType() { return precisionType; }

    public String getCurrencyCode() { return currencyCode; }

    public long getValueMicros() { return valueMicros; }
}
