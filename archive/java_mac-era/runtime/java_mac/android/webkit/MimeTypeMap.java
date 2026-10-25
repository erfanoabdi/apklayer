// android.webkit.MimeTypeMap -- two-way map between file extensions and
// MIME types. Previously an empty stub, so any call (downloaders,
// FileProvider, image loaders, F-Droid's APK install path all use
// getMimeTypeFromExtension / getFileExtensionFromUrl) threw
// NoSuchMethodError.
//
// AOSP backs this with a large platform mime.types table. We seed a
// compact map of the common types apps actually query and fall back to
// the JDK's java.net.URLConnection content-type guesser for anything
// else, so getMimeTypeFromExtension returns a sensible value for the
// long tail. API surface + getFileExtensionFromUrl semantics mirror
// AOSP exactly.
package android.webkit;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public class MimeTypeMap {
    private static final MimeTypeMap SINGLETON = new MimeTypeMap();

    // extension -> mime type (lowercased extension, no dot).
    private static final Map<String, String> EXT_TO_MIME = new HashMap<>();
    // mime type -> extension (first/canonical extension).
    private static final Map<String, String> MIME_TO_EXT = new HashMap<>();

    private static void add(String ext, String mime) {
        EXT_TO_MIME.put(ext, mime);
        // Don't overwrite a canonical extension already chosen for a mime.
        MIME_TO_EXT.putIfAbsent(mime, ext);
    }

    static {
        // Text / web
        add("txt", "text/plain");        add("html", "text/html");
        add("htm", "text/html");         add("css", "text/css");
        add("csv", "text/csv");          add("xml", "text/xml");
        add("js", "application/javascript"); add("json", "application/json");
        add("md", "text/markdown");
        // Images
        add("png", "image/png");         add("jpg", "image/jpeg");
        add("jpeg", "image/jpeg");       add("gif", "image/gif");
        add("webp", "image/webp");       add("bmp", "image/bmp");
        add("svg", "image/svg+xml");     add("ico", "image/x-icon");
        add("heic", "image/heic");       add("tiff", "image/tiff");
        // Audio
        add("mp3", "audio/mpeg");        add("ogg", "audio/ogg");
        add("wav", "audio/x-wav");       add("m4a", "audio/mp4");
        add("aac", "audio/aac");         add("flac", "audio/flac");
        add("mid", "audio/midi");        add("opus", "audio/ogg");
        // Video
        add("mp4", "video/mp4");         add("webm", "video/webm");
        add("mkv", "video/x-matroska");  add("3gp", "video/3gpp");
        add("avi", "video/avi");         add("mov", "video/quicktime");
        // Documents
        add("pdf", "application/pdf");
        add("doc", "application/msword");
        add("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        add("xls", "application/vnd.ms-excel");
        add("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        add("ppt", "application/vnd.ms-powerpoint");
        add("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation");
        // Archives / packages
        add("zip", "application/zip");   add("gz", "application/gzip");
        add("tar", "application/x-tar"); add("7z", "application/x-7z-compressed");
        add("rar", "application/x-rar-compressed");
        add("jar", "application/java-archive");
        add("apk", "application/vnd.android.package-archive");
        // Fonts / misc
        add("ttf", "font/ttf");          add("otf", "font/otf");
        add("woff", "font/woff");        add("woff2", "font/woff2");
        add("bin", "application/octet-stream");
        add("apng", "image/apng");
    }

    public MimeTypeMap() {}

    public static MimeTypeMap getSingleton() { return SINGLETON; }

    /** Return the registered extension for a MIME type, or null. */
    public String getExtensionFromMimeType(String mimeType) {
        if (mimeType == null) return null;
        return MIME_TO_EXT.get(mimeType.toLowerCase(Locale.US));
    }

    /** Return the MIME type for a file extension, or null if unknown. */
    public String getMimeTypeFromExtension(String extension) {
        if (extension == null) return null;
        String ext = extension.toLowerCase(Locale.US);
        String mime = EXT_TO_MIME.get(ext);
        if (mime != null) return mime;
        // Long-tail fallback via the JDK's filename content-type guesser.
        String guess = java.net.URLConnection.guessContentTypeFromName("x." + ext);
        return guess;   // may be null for genuinely unknown extensions
    }

    public boolean hasExtension(String extension) {
        return extension != null && EXT_TO_MIME.containsKey(extension.toLowerCase(Locale.US));
    }

    public boolean hasMimeType(String mimeType) {
        return mimeType != null && MIME_TO_EXT.containsKey(mimeType.toLowerCase(Locale.US));
    }

    /** AOSP-identical: pull the extension off a URL, ignoring the query
     *  string and only accepting a "safe" filename charset. */
    public static String getFileExtensionFromUrl(String url) {
        if (url != null && url.length() > 0) {
            int query = url.lastIndexOf('?');
            if (query > 0) {
                url = url.substring(0, query);
            }
            int filenamePos = url.lastIndexOf('/');
            String filename = (0 <= filenamePos) ? url.substring(filenamePos + 1) : url;
            if (filename.length() > 0
                    && Pattern.matches("[a-zA-Z_0-9.\\-]+", filename)) {
                int dotPos = filename.lastIndexOf('.');
                if (0 <= dotPos) {
                    return filename.substring(dotPos + 1);
                }
            }
        }
        return "";
    }
}
