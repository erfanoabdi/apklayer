package hello.portal;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/** The Storage Access Framework picker through the desktop's file chooser portal: the test cancels the dialog with Esc. */
public final class Main extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), 7);
    }

    @Override protected void onActivityResult(int code, int result, Intent data) {
        System.out.println("[portal] result code=" + code + " resultCode=" + result + " data=" + data + (result == RESULT_CANCELED ? " CANCELED" : " PICKED"));
    }
}
