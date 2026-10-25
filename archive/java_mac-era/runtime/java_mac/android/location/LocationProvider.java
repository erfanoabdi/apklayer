// android.location.LocationProvider -- describes a location source.
// Deprecated in AOSP (apps query provider names directly) but still
// referenced by older code via LocationManager.getProvider. Constants +
// accuracy/power getters mirror AOSP.
package android.location;

public class LocationProvider {
    public static final int OUT_OF_SERVICE = 0;
    public static final int TEMPORARILY_UNAVAILABLE = 1;
    public static final int AVAILABLE = 2;

    private final String name;

    LocationProvider(String name) { this.name = name; }

    public String getName() { return name; }
    public boolean meetsCriteria(Criteria criteria) { return true; }
    public boolean requiresNetwork() { return LocationManager.NETWORK_PROVIDER.equals(name); }
    public boolean requiresSatellite() { return LocationManager.GPS_PROVIDER.equals(name); }
    public boolean requiresCell() { return false; }
    public boolean hasMonetaryCost() { return false; }
    public boolean supportsAltitude() { return LocationManager.GPS_PROVIDER.equals(name); }
    public boolean supportsSpeed() { return LocationManager.GPS_PROVIDER.equals(name); }
    public boolean supportsBearing() { return LocationManager.GPS_PROVIDER.equals(name); }
    public int getPowerRequirement() { return Criteria.POWER_LOW; }
    public int getAccuracy() { return Criteria.ACCURACY_COARSE; }
}
