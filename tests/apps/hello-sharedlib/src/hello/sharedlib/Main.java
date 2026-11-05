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
            System.out.println(tag + " query=" + query + " client=" + client.getClass().getName() + " testBase=" + testBase
                    + " libs=" + Arrays.toString(getApplicationInfo().sharedLibraryFiles));
            System.out.println(tag + " PASS");
        } catch (Throwable failure) {
            System.out.println(tag + " FAIL " + failure);
            failure.printStackTrace(System.out);
        }
        finish();
    }
}
