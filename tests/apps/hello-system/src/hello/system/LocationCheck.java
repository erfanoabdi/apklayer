package hello.system;

import android.content.Context;
import android.location.LocationManager;
import android.provider.Settings;

/** Location as the system reports it: the mode is written (by the system at boot), and LocationManager agrees with it and with the providers it allows. */
final class LocationCheck implements Check {
    @Override public String run(Context context) throws Exception {
        LocationManager location = context.getSystemService(LocationManager.class);
        int mode = Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.LOCATION_MODE, -1);
        String allowed = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
        boolean network = location.getAllProviders().contains(LocationManager.NETWORK_PROVIDER) && location.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        String seen = "mode " + mode + ", providers [" + allowed + "], enabled " + location.isLocationEnabled() + ", network " + network;
        Check.require(mode >= 0 && allowed != null, "no location state from the system: " + seen);
        Check.require(location.isLocationEnabled() == (mode != Settings.Secure.LOCATION_MODE_OFF), seen);
        Check.require(network == (mode != Settings.Secure.LOCATION_MODE_OFF && allowed.contains(LocationManager.NETWORK_PROVIDER)), seen);
        return seen;
    }
}
