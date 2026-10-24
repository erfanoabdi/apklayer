package android.webkit;

// android.webkit.URLUtil: URL classification/normalization helpers. GeoDash
// Meltdown references it in onCreate (its ad/webview glue); a missing class
// threw NoClassDefFoundError and aborted onCreate. Faithful to AOSP
// frameworks/base/.../webkit/URLUtil.java observable behaviour -- pure string
// logic, no platform state, so this is a real implementation, not a stub.
public final class URLUtil {
    // AOSP's URLUtil has the IMPLICIT public default constructor -- its
    // core/api/current.txt entry is "ctor public URLUtil();". A private one
    // here would make `new URLUtil()` (which some old library code does, to
    // hold a reference) fail where it succeeds on a device.
    public URLUtil() {}

    /** AOSP's cookieless-proxy URL prefix. */
    private static final String PROXY_BASE = "file:///cookieless_proxy/";

    /**
     * @return true iff the url is a proxy url that allowed cookieless network
     *         requests from a file url.
     * @deprecated Cookieless proxy is no longer supported.
     */
    @Deprecated
    public static boolean isCookielessProxyUrl(String url) {
        return (null != url) && url.startsWith(PROXY_BASE);
    }

    public static boolean isAssetUrl(String url) {
        return url != null && url.startsWith("file:///android_asset/");
    }
    public static boolean isResourceUrl(String url) {
        return url != null && url.startsWith("file:///android_res/");
    }
    public static boolean isFileUrl(String url) {
        return url != null && url.startsWith("file://") && !isAssetUrl(url) && !isResourceUrl(url);
    }
    public static boolean isAboutUrl(String url) {
        return url != null && url.startsWith("about:");
    }
    public static boolean isDataUrl(String url) {
        return url != null && url.startsWith("data:");
    }
    public static boolean isJavaScriptUrl(String url) {
        return url != null && url.startsWith("javascript:");
    }
    public static boolean isHttpUrl(String url) {
        return url != null && url.length() > 6
            && url.substring(0, 7).equalsIgnoreCase("http://");
    }
    public static boolean isHttpsUrl(String url) {
        return url != null && url.length() > 7
            && url.substring(0, 8).equalsIgnoreCase("https://");
    }
    public static boolean isNetworkUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        return isHttpUrl(url) || isHttpsUrl(url);
    }
    public static boolean isContentUrl(String url) {
        return url != null && url.startsWith("content:");
    }
    public static boolean isValidUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        return isAssetUrl(url) || isResourceUrl(url) || isFileUrl(url)
            || isAboutUrl(url) || isHttpUrl(url) || isHttpsUrl(url)
            || isJavaScriptUrl(url) || isContentUrl(url);
    }

    public static String guessUrl(String inUrl) {
        if (inUrl == null) return "";
        String url = inUrl.trim();
        if (url.isEmpty()) return url;
        if (url.startsWith("about:") || isValidUrl(url)) return url;
        if (url.indexOf(' ') != -1 || (url.indexOf('.') == -1)) {
            // looks like a search term, not a URL: leave as-is (caller decides)
            return url;
        }
        return "http://" + url;
    }

    public static String stripAnchor(String url) {
        if (url == null) return null;
        int anchorIndex = url.indexOf('#');
        return anchorIndex != -1 ? url.substring(0, anchorIndex) : url;
    }

    public static String composeSearchUrl(String inQuery, String template, String queryPlaceHolder) {
        if (template == null || queryPlaceHolder == null) return null;
        int placeHolderIndex = template.indexOf(queryPlaceHolder);
        if (placeHolderIndex < 0) return null;
        String query;
        try {
            query = java.net.URLEncoder.encode(inQuery == null ? "" : inQuery, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return null;
        }
        return template.substring(0, placeHolderIndex) + query
             + template.substring(placeHolderIndex + queryPlaceHolder.length());
    }

    public static byte[] decode(byte[] url) throws IllegalArgumentException {
        if (url == null || url.length == 0) return new byte[0];
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (int i = 0; i < url.length; i++) {
            byte b = url[i];
            if (b == '%' && i + 2 < url.length) {
                int hi = Character.digit(url[i + 1], 16);
                int lo = Character.digit(url[i + 2], 16);
                if (hi < 0 || lo < 0) throw new IllegalArgumentException("Invalid % sequence");
                out.write((hi << 4) | lo);
                i += 2;
            } else {
                out.write(b);
            }
        }
        return out.toByteArray();
    }

    public static String guessFileName(String url, String contentDisposition, String mimeType) {
        String filename = null;
        String extension = null;
        if (url != null) {
            String u = stripAnchor(url);
            int q = u.lastIndexOf('?');
            if (q > 0) u = u.substring(0, q);
            int slash = u.lastIndexOf('/');
            if (slash >= 0 && slash < u.length() - 1) filename = u.substring(slash + 1);
        }
        if (filename == null || filename.isEmpty()) filename = "downloadfile";
        int dot = filename.indexOf('.');
        if (dot < 0) {
            if ("application/pdf".equalsIgnoreCase(mimeType)) extension = ".pdf";
            else extension = ".bin";
            filename = filename + extension;
        }
        return filename;
    }
}
