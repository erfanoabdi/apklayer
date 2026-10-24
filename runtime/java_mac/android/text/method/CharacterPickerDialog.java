// android.text.method.CharacterPickerDialog -- the long-press accent picker: a
// small grid of alternative characters ("e" -> e-acute, e-grave, ...) that
// replaces or inserts the chosen one into the Editable. Was absent entirely
// (NoClassDefFoundError). Mirrors
// frameworks/base/core/java/android/text/method/CharacterPickerDialog.java.
//
// AOSP inflates com.android.internal.R.layout.character_picker into a GridView
// of buttons. Mac.apk has no com.android.internal resources, so the grid is
// built PROGRAMMATICALLY from the same `options` string, with the same one
// Button per option and the same click wiring -- the observable behaviour
// (tapping an option replaces/inserts it and dismisses; tapping Cancel just
// dismisses) is identical, only the layout source differs.
package android.text.method;

import android.app.Dialog;
import android.content.Context;
import android.text.Editable;
import android.text.Selection;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.LinearLayout;

public class CharacterPickerDialog extends Dialog
        implements AdapterView.OnItemClickListener, View.OnClickListener {

    private final View mView;
    private final Editable mText;
    private final String mOptions;
    private final boolean mInsert;
    private Button mCancelButton;

    /**
     * @param insert true to INSERT the chosen option at the caret, false to
     *               REPLACE the character before it -- the difference between
     *               a picker opened on empty space and one opened by
     *               long-pressing a character.
     */
    public CharacterPickerDialog(Context context, View view, Editable text, String options,
            boolean insert) {
        super(context);
        mView = view;
        mText = text;
        mOptions = options;
        mInsert = insert;
    }

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context context = getContext();
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);

        GridView grid = new GridView(context);
        grid.setNumColumns(Math.min(mOptions == null ? 1 : Math.max(1, mOptions.length()), 6));
        grid.setAdapter(new OptionsAdapter(context));
        grid.setOnItemClickListener(this);
        root.addView(grid);

        mCancelButton = new Button(context);
        mCancelButton.setText("Cancel");
        mCancelButton.setOnClickListener(this);
        root.addView(mCancelButton);

        setContentView(root);
    }

    /** Handles clicks on the character buttons. */
    @Override
    public void onItemClick(AdapterView parent, View view, int position, long id) {
        String result = String.valueOf(mOptions.charAt(position));
        replaceCharacterAndClose(result);
    }

    private void replaceCharacterAndClose(CharSequence replace) {
        int selEnd = Selection.getSelectionEnd(mText);
        if (mInsert || selEnd == 0) {
            mText.insert(selEnd, replace);
        } else {
            mText.replace(selEnd - 1, selEnd, replace);
        }
        dismiss();
    }

    /** Handles clicks on the Cancel button, and on an option Button directly. */
    @Override
    public void onClick(View v) {
        if (v == mCancelButton) {
            dismiss();
        } else if (v instanceof Button) {
            CharSequence result = ((Button) v).getText();
            replaceCharacterAndClose(result);
        }
    }

    private class OptionsAdapter extends BaseAdapter {
        private final Context mContext;

        OptionsAdapter(Context context) {
            mContext = context;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Button b = new Button(mContext);
            b.setText(String.valueOf(mOptions.charAt(position)));
            b.setOnClickListener(CharacterPickerDialog.this);
            return b;
        }

        @Override
        public final int getCount() {
            return mOptions == null ? 0 : mOptions.length();
        }

        @Override
        public final Object getItem(int position) {
            return String.valueOf(mOptions.charAt(position));
        }

        @Override
        public final long getItemId(int position) {
            return position;
        }
    }
}
