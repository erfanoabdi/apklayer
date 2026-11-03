package hello.payload;

import android.app.Activity;
import android.os.Bundle;
import java.io.File;
import java.nio.file.Files;

/** What an installed package sees of itself, told to the installer (tests/vm/installer.py): its version, a string only its config split defines, its data from before an update. */
public class Main extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            int id = getResources().getIdentifier("split_marker", "string", getPackageName());
            String split;
            try { split = id == 0 ? "no-id" : getString(id); } catch (android.content.res.Resources.NotFoundException missing) { split = "missing"; }
            File marker = new File(getFilesDir(), "marker");
            String data = marker.exists() ? new String(Files.readAllBytes(marker.toPath())) : "none";
            long version = getPackageManager().getPackageInfo(getPackageName(), 0).getLongVersionCode();
            String report = "version=" + version + " split=" + split + " splits=" + java.util.Arrays.toString(getApplicationInfo().splitNames) + " data=" + data;
            System.out.println("[payload] " + report);
            sendBroadcast(new android.content.Intent("hello.payload.REPORT").setPackage("hello.installer").putExtra("report", report));
            Files.write(marker.toPath(), ("v" + version).getBytes());
        } catch (Exception failure) { System.out.println("[payload] FAIL " + failure); }
        finish();
    }
}
