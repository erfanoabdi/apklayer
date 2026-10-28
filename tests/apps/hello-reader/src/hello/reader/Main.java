package hello.reader;

import android.app.Activity;
import android.os.Bundle;

/** A viewer with no permission of its own: it reads the uri it is started with, which works only through the grant that came with the intent. */
public final class Main extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        new Thread(() -> {
            try (java.io.InputStream in = getContentResolver().openInputStream(getIntent().getData())) {
                System.out.println("[reader] read: " + new String(in.readNBytes(40)).replace('\n', ' '));
            } catch (Throwable failure) {
                System.out.println("[reader] FAIL " + failure);
            }
            finish();
        }).start();
    }
}
