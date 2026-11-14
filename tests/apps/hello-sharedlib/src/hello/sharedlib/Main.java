package hello.sharedlib;

import android.app.Activity;
import android.os.Bundle;
import java.util.Arrays;
import java.util.List;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

/** Uses classes of org.apache.http.legacy (and android.test.base when the platform adds it), as old apps do: the real ones, not the sandbox's "Stub!". */
public final class Main extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        String tag = "[sharedlib] " + getPackageName();
        try {
            BasicNameValuePair pair = new BasicNameValuePair("q", "a b");
            String query = URLEncodedUtils.format(List.of(pair), "UTF-8");
            DefaultHttpClient client = new DefaultHttpClient();
            client.getConnectionManager().shutdown();
            android.net.http.AndroidHttpClient legacy = android.net.http.AndroidHttpClient.newInstance("hello");  // android.net.http of the same library
            legacy.close();
            boolean testBase;
            try { Class.forName("android.test.AndroidTestCase"); testBase = true; } catch (ClassNotFoundException absent) { testBase = false; }
            int optional = 0;  // the platform's other optional libraries (hello.sharedlib declares them): one class of each, not initialized
            for (String name : new String[] {"com.android.location.provider.LocationProviderBase", "javax.obex.ObexPacket", "com.android.future.usb.UsbManager",
                    "com.android.media.remotedisplay.RemoteDisplayProvider", "com.android.mediadrm.signer.MediaDrmSigner", "com.android.nfc_extras.NfcAdapterExtras"}) {
                try { Class.forName(name, false, getClassLoader()); optional++; } catch (ClassNotFoundException absent) { System.out.println(tag + " no " + name); }
            }
            System.out.println(tag + " optional=" + optional + " query=" + query + " client=" + client.getClass().getName() + " testBase=" + testBase
                    + " libs=" + Arrays.toString(getApplicationInfo().sharedLibraryFiles));
            try {  // another app's ApplicationInfo carries its libraries too (hello.sharedlib.old targets 27: org.apache.http.legacy)
                android.content.pm.ApplicationInfo other = getPackageManager().getApplicationInfo("hello.sharedlib.old", 0);
                boolean files = other.sharedLibraryFiles != null && Arrays.stream(other.sharedLibraryFiles).anyMatch(f -> f.endsWith("org.apache.http.legacy.jar"));
                Object list = android.content.pm.ApplicationInfo.class.getField("sharedLibraryInfos").get(other);  // @hide
                boolean infos = list instanceof List<?> l && l.stream().anyMatch(i -> "org.apache.http.legacy".equals(String.valueOf(((android.content.pm.SharedLibraryInfo) i).getName())));
                System.out.println(tag + " other " + (files && infos ? "PASS" : "FAIL") + " files=" + Arrays.toString(other.sharedLibraryFiles) + " infos=" + list);
            } catch (ReflectiveOperationException hidden) { System.out.println(tag + " other FAIL " + hidden);
            } catch (android.content.pm.PackageManager.NameNotFoundException notInstalled) { System.out.println(tag + " other absent"); }
            System.out.println(tag + " PASS");
        } catch (Throwable failure) {
            System.out.println(tag + " FAIL " + failure);
            failure.printStackTrace(System.out);
        }
        finish();
    }
}
