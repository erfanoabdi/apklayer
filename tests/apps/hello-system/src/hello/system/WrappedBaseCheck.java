package hello.system;

import android.content.Context;

/** An application whose base context is a ContextWrapper of its ContextImpl (App, -Dhello.sys=wrappedbase) gets the state services' managers: the
 * host's redirect to the application's base came back to the same ContextImpl forever (MojoLauncher's StackOverflowError). */
final class WrappedBaseCheck implements Check {
    @Override public String run(Context context) {
        Context app = context.getApplicationContext();
        Object location = app.getSystemService(Context.LOCATION_SERVICE), battery = app.getSystemService(Context.BATTERY_SERVICE);
        Check.require(location != null && battery != null, "managers " + location + " " + battery);
        Check.require(context.getSystemService(Context.LOCATION_SERVICE) == location, "the activity's location manager is not the application's");
        return "wrapped " + App.wrapped + ", location and battery managers";
    }
}
