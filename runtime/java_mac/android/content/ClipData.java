// android.content.ClipData -- clipboard content. Per AOSP
// frameworks/base/core/java/android/content/ClipData.java (android14-release).
// Holds a ClipDescription + one or more Items.
//
// 2026-09-26 (clipboard host bridge): brought to AOSP's contract where the
// clipboard path depends on it -- the item constructors reject a null item
// (NullPointerException "item is null"), getItemAt is a plain List.get
// (IndexOutOfBoundsException past the end, where this used to answer null),
// addItem(ContentResolver, Item) extends the mime types, Item.coerceToText /
// coerceToHtmlText / coerceToStyledText follow AOSP's order, and the @hide
// members ClipboardService itself uses (Item(Item), ClipData(description,
// ArrayList), setItemAt) exist.
package android.content;

import android.net.Uri;
import java.util.ArrayList;

public class ClipData implements android.os.Parcelable {
    static final String[] MIMETYPES_TEXT_PLAIN   = new String[] { ClipDescription.MIMETYPE_TEXT_PLAIN };
    static final String[] MIMETYPES_TEXT_HTML    = new String[] { ClipDescription.MIMETYPE_TEXT_HTML };
    static final String[] MIMETYPES_TEXT_URILIST = new String[] { ClipDescription.MIMETYPE_TEXT_URILIST };
    static final String[] MIMETYPES_TEXT_INTENT  = new String[] { ClipDescription.MIMETYPE_TEXT_INTENT };

    private final ClipDescription description;
    private final ArrayList<Item> items;

    public ClipData(CharSequence label, String[] mimeTypes, Item item) {
        this.description = new ClipDescription(label, mimeTypes);
        if (item == null) {
            throw new NullPointerException("item is null");
        }
        this.items = new ArrayList<Item>();
        items.add(item);
        updateStyledText();
    }
    public ClipData(ClipDescription description, Item item) {
        this.description = description;
        if (item == null) {
            throw new NullPointerException("item is null");
        }
        this.items = new ArrayList<Item>();
        items.add(item);
        updateStyledText();
    }
    /** AOSP @hide: "a defensive copy of this list is not made". */
    public ClipData(ClipDescription description, ArrayList<Item> items) {
        this.description = description;
        if (items == null) {
            throw new NullPointerException("item is null");
        }
        this.items = items;
    }
    public ClipData(ClipData other) {
        this.description = other.description;
        this.items = new ArrayList<Item>(other.items);
    }

    // AOSP's ClipData keeps ClipDescription.isStyledText() in step with the
    // FIRST item's text (it calls the package-private setIsStyledText whenever
    // that item is set), so a paste target can ask the description whether it
    // needs to preserve spans without materialising the item. Without this the
    // accessor added on ClipDescription would answer false for a clip that
    // genuinely carries a Spanned.
    private void updateStyledText() {
        if (description == null || items.isEmpty()) return;
        CharSequence t = items.get(0).getText();
        description.setIsStyledText(t instanceof android.text.Spanned);
    }

    public ClipDescription getDescription() { return description; }
    public int getItemCount() { return items.size(); }
    /** AOSP `return mItems.get(index);` -- out of range throws. */
    public Item getItemAt(int index) {
        return items.get(index);
    }
    /** AOSP @hide. */
    public void setItemAt(int index, Item item) {
        items.set(index, item);
    }
    public void addItem(Item item) {
        if (item == null) {
            throw new NullPointerException("item is null");
        }
        items.add(item);
        if (items.size() == 1) updateStyledText();
    }
    /** AOSP (API 26): addItem, then widen the description's mime types to
     *  cover what the new item carries. */
    public void addItem(android.content.ContentResolver resolver, Item item) {
        addItem(item);
        if (description == null) return;
        if (item.getHtmlText() != null) {
            description.addMimeTypes(MIMETYPES_TEXT_HTML);
        } else if (item.getText() != null) {
            description.addMimeTypes(MIMETYPES_TEXT_PLAIN);
        }
        if (item.getIntent() != null) {
            description.addMimeTypes(MIMETYPES_TEXT_INTENT);
        }
        if (item.getUri() != null) {
            description.addMimeTypes(getMimeTypes(resolver, item.getUri()));
        }
    }

    public static ClipData newPlainText(CharSequence label, CharSequence text) {
        Item item = new Item(text);
        return new ClipData(label, MIMETYPES_TEXT_PLAIN, item);
    }
    public static ClipData newHtmlText(CharSequence label, CharSequence text, String htmlText) {
        Item item = new Item(text, htmlText);
        return new ClipData(label, MIMETYPES_TEXT_HTML, item);
    }
    /** AOSP newUri: a content: URI takes its provider's type + stream types;
     *  anything else is text/uri-list. */
    public static ClipData newUri(android.content.ContentResolver resolver, CharSequence label, Uri uri) {
        Item item = new Item(uri);
        return new ClipData(label, getMimeTypes(resolver, uri), item);
    }
    public static ClipData newRawUri(CharSequence label, Uri uri) {
        Item item = new Item(uri);
        return new ClipData(label, MIMETYPES_TEXT_URILIST, item);
    }
    public static ClipData newIntent(CharSequence label, Intent intent) {
        Item item = new Item(intent);
        return new ClipData(label, MIMETYPES_TEXT_INTENT, item);
    }

    // AOSP ClipData.getMimeTypes (private).
    private static String[] getMimeTypes(android.content.ContentResolver resolver, Uri uri) {
        String[] mimeTypes = null;
        if (resolver != null && uri != null
                && android.content.ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            String realType = null;
            try {
                realType = resolver.getType(uri);
                mimeTypes = resolver.getStreamTypes(uri, "*/*");
            } catch (RuntimeException e) {
                mimeTypes = null;
            }
            if (realType != null) {
                if (mimeTypes == null) {
                    mimeTypes = new String[] { realType };
                } else if (!java.util.Arrays.asList(mimeTypes).contains(realType)) {
                    String[] tmp = new String[mimeTypes.length + 1];
                    tmp[0] = realType;
                    System.arraycopy(mimeTypes, 0, tmp, 1, mimeTypes.length);
                    mimeTypes = tmp;
                }
            }
        }
        if (mimeTypes == null) {
            mimeTypes = MIMETYPES_TEXT_URILIST;
        }
        return mimeTypes;
    }

    /** What a Binder round trip gives the far side: a new ClipData with its
     *  own description (label, mime types, extras, timestamp, styled flag)
     *  and its own Items. ClipboardManager's in-process service uses it
     *  where AOSP's clip crosses a process boundary, so the service's copy
     *  and each caller's copy are independent objects, as on a device. */
    ClipData macapkCopyForTransfer() {
        ClipDescription d = description != null ? description.macapkCopyForTransfer() : null;
        ArrayList<Item> copies = new ArrayList<Item>(items.size());
        for (Item it : items) copies.add(new Item(it));
        return new ClipData(d, copies);
    }

    @Override public String toString() {
        return "ClipData(items=" + items.size() + ")";
    }

    // ---- Parcelable ----
    //
    // AOSP declares `public class ClipData implements Parcelable`. The
    // interface is load-bearing at the JVM TYPE level, not just at runtime:
    // any API that takes/returns a Parcelable (Intent.setClipData ->
    // Intent.writeToParcel, DragEvent's clip data, Bundle.putParcelable,
    // InputConnection.commitContent) fails verification/ClassCast when the
    // argument type is not assignable to Parcelable, so an app crashes
    // before any of our logic runs.
    //
    // AOSP's Item is deliberately NOT a Parcelable of its own -- ClipData
    // .writeToParcel flattens each Item's fields inline, and we mirror that
    // structure (description, then item count, then per-item fields).
    //
    // Mac.apk's android.os.Parcel is an in-process object-graph shim
    // (writeParcelable IS writeObjectRef), so the flattened representation
    // is a sequence of live object references rather than AOSP's byte
    // layout. That is the same contract the rest of our framework marshals
    // under, and it is what lets the description travel with its extras and
    // timestamp intact -- a field-by-field copy would silently drop both.
    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(android.os.Parcel dest, int flags) {
        dest.writeValue(description);
        dest.writeInt(items.size());
        for (Item item : items) {
            dest.writeValue(item.text);
            dest.writeString(item.htmlText);
            dest.writeValue(item.intent);
            dest.writeValue(item.uri);
            dest.writeValue(item.intentSender);
        }
    }

    public static final android.os.Parcelable.Creator<ClipData> CREATOR =
        new android.os.Parcelable.Creator<ClipData>() {
            @Override public ClipData createFromParcel(android.os.Parcel source) {
                ClipDescription desc = (ClipDescription) source.readValue(null);
                ClipData out = new ClipData(desc, new ArrayList<Item>());
                int n = source.readInt();
                for (int i = 0; i < n; ++i) {
                    CharSequence text = (CharSequence) source.readValue(null);
                    String html     = source.readString();
                    Intent intent   = (Intent) source.readValue(null);
                    Uri uri         = (Uri) source.readValue(null);
                    IntentSender is = (IntentSender) source.readValue(null);
                    Item it = new Item(text, html, intent, uri);
                    it.intentSender = is;
                    out.items.add(it);
                    out.updateStyledText();
                }
                return out;
            }
            @Override public ClipData[] newArray(int size) {
                return new ClipData[size];
            }
        };

    public static class Item {
        private CharSequence text;
        private String htmlText;
        private Intent intent;
        private Uri uri;
        private IntentSender intentSender;

        /** AOSP @hide copy constructor (ClipboardService uses it to give a
         *  related user its own items). */
        public Item(Item other) {
            this.text = other.text;
            this.htmlText = other.htmlText;
            this.intent = other.intent;
            this.uri = other.uri;
            this.intentSender = other.intentSender;
        }
        public Item(CharSequence text) { this.text = text; }
        public Item(CharSequence text, String htmlText) { this.text = text; this.htmlText = htmlText; }
        public Item(Intent intent) { this.intent = intent; }
        public Item(Uri uri) { this.uri = uri; }
        public Item(CharSequence text, Intent intent, Uri uri) {
            this.text = text; this.intent = intent; this.uri = uri;
        }
        public Item(CharSequence text, String htmlText, Intent intent, Uri uri) {
            this.text = text; this.htmlText = htmlText; this.intent = intent; this.uri = uri;
        }

        public CharSequence getText() { return text; }
        public android.view.textclassifier.TextLinks getTextLinks() { return null; }
        public String getHtmlText() { return htmlText; }
        public Intent getIntent() { return intent; }
        public Uri getUri() { return uri; }

        /** AOSP core/java/android/content/ClipData.java (API 35) -- the
         *  IntentSender an unhandled-drag handler is invoked through. Only the
         *  Builder can set one (there is no Item(IntentSender) constructor
         *  upstream either), so it is null on every Item built the classic
         *  way. */
        public IntentSender getIntentSender() { return intentSender; }

        /** AOSP coerceToText, in its order: explicit text; else the URI's
         *  content opened as text/*; else the URI itself -- but "" for
         *  content:, android.resource: and file: (a raw provider path is not
         *  text a user meant to copy); else the Intent as an intent: URI;
         *  else "". */
        public CharSequence coerceToText(Context context) {
            if (text != null) return text;
            if (uri != null) {
                CharSequence streamed = macapkReadUriAsText(context, uri, "text/*");
                if (streamed != null) return streamed;
                if (isNonTextualScheme(uri)) return "";
                return uri.toString();
            }
            if (intent != null) {
                return intent.toUri(Intent.URI_INTENT_SCHEME);
            }
            return "";
        }

        /** AOSP coerceToStyledText: spans kept; else the HTML parsed; else
         *  the plain text; else a URI/Intent as a linked string. */
        public CharSequence coerceToStyledText(Context context) {
            if (text instanceof android.text.Spanned) return text;
            if (htmlText != null) {
                try {
                    CharSequence parsed = android.text.Html.fromHtml(htmlText);
                    if (parsed != null) return parsed;
                } catch (RuntimeException e) {
                    // fall back to the plain text, as AOSP does
                }
            }
            if (text != null) return text;
            return coerceToText(context);
        }

        /** AOSP coerceToHtmlText: explicit HTML; else the text as HTML
         *  (Html.toHtml for spans, Html.escapeHtml otherwise); else a
         *  URI/Intent as an anchor (never for content:/file:/android.resource:).
         *  One simplification: for a URI whose provider serves text, AOSP
         *  prefers a text/html stream when getStreamTypes offers one; this
         *  reads text/* and escapes it. */
        public String coerceToHtmlText(Context context) {
            if (htmlText != null) return htmlText;
            if (text != null) {
                if (text instanceof android.text.Spanned) {
                    return android.text.Html.toHtml((android.text.Spanned) text);
                }
                return android.text.Html.escapeHtml(text);
            }
            if (uri != null) {
                CharSequence streamed = macapkReadUriAsText(context, uri, "text/*");
                if (streamed != null) return android.text.Html.escapeHtml(streamed);
                if (isNonTextualScheme(uri)) return "";
                return uriToHtml(uri.toString());
            }
            if (intent != null) return uriToHtml(intent.toUri(Intent.URI_INTENT_SCHEME));
            return "";
        }

        private static boolean isNonTextualScheme(Uri u) {
            String scheme = u.getScheme();
            return android.content.ContentResolver.SCHEME_CONTENT.equals(scheme)
                || android.content.ContentResolver.SCHEME_ANDROID_RESOURCE.equals(scheme)
                || android.content.ContentResolver.SCHEME_FILE.equals(scheme);
        }

        private static String uriToHtml(String u) {
            String esc = android.text.Html.escapeHtml(u);
            return "<a href=\"" + esc + "\">" + esc + "</a>";
        }

        /** AOSP's "First see if the URI can be opened as a plain text stream":
         *  resolver.openTypedAssetFileDescriptor(uri, "text/*", null), read as
         *  UTF-8. Without a Context there is no resolver to ask. */
        private static CharSequence macapkReadUriAsText(Context context, Uri u, String mime) {
            if (context == null) return null;
            android.content.res.AssetFileDescriptor descr;
            try {
                android.content.ContentResolver resolver = context.getContentResolver();
                if (resolver == null) return null;
                descr = resolver.openTypedAssetFileDescriptor(u, mime, null);
            } catch (java.io.FileNotFoundException | RuntimeException e) {
                // AOSP: "Unable to open content URI as text... not really an
                // error, just something to ignore." (SecurityException is a
                // RuntimeException and is ignored the same way, after a log.)
                return null;
            }
            if (descr == null) return null;
            try (java.io.InputStream in = descr.createInputStream();
                 java.io.InputStreamReader reader = new java.io.InputStreamReader(
                         in, java.nio.charset.StandardCharsets.UTF_8)) {
                StringBuilder b = new StringBuilder(128);
                char[] buf = new char[8192];
                int len;
                while ((len = reader.read(buf)) > 0) b.append(buf, 0, len);
                return b.toString();
            } catch (java.io.IOException e) {
                return e.toString();                   // AOSP: "return e.toString();"
            } finally {
                try { descr.close(); } catch (Throwable ignored) { }
            }
        }

        /** AOSP ClipData.Item.Builder (API 35) -- the only way to build an
         *  Item carrying an IntentSender, and the only Item constructor path
         *  that lets every field be set independently. Each setter is
         *  @Nullable-accepting, so clearing a field by passing null is
         *  legal. */
        public static final class Builder {
            private CharSequence mText;
            private String mHtmlText;
            private Intent mIntent;
            private IntentSender mIntentSender;
            private Uri mUri;

            public Builder() { }

            public Builder setText(CharSequence text) { mText = text; return this; }
            public Builder setHtmlText(String htmlText) { mHtmlText = htmlText; return this; }
            public Builder setIntent(Intent intent) { mIntent = intent; return this; }
            public Builder setIntentSender(IntentSender intentSender) {
                mIntentSender = intentSender;
                return this;
            }
            public Builder setUri(Uri uri) { mUri = uri; return this; }

            public Item build() {
                Item item = new Item(mText, mHtmlText, mIntent, mUri);
                item.intentSender = mIntentSender;
                return item;
            }
        }
    }
}
