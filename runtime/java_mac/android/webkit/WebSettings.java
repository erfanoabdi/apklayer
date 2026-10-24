package android.webkit;

// android.webkit.WebSettings (AOSP API-1).
// Configuration object returned by WebView.getSettings(). AOSP declares it
// abstract; we keep that shape but every member now STORES its value so the
// getters round-trip and a future WKWebView bridge can read the desired config
// when it attaches a real engine. Ref:
// https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/webkit/WebSettings.java
public abstract class WebSettings {

    // ----- nested enums (mirror AOSP) -----
    public enum LayoutAlgorithm {
        NORMAL,
        @Deprecated SINGLE_COLUMN,
        NARROW_COLUMNS,
        TEXT_AUTOSIZING
    }

    public enum PluginState { ON, ON_DEMAND, OFF }

    public enum ZoomDensity {
        FAR,    // 240 dpi
        MEDIUM, // 160 dpi
        CLOSE   // 120 dpi
    }

    @Deprecated
    public enum TextSize {
        SMALLEST(50),
        SMALLER(75),
        NORMAL(100),
        LARGER(150),
        LARGEST(200);
        final int value;
        TextSize(int size) { this.value = size; }
    }

    // RenderPriority is the nested enum in real AOSP. We keep the legacy
    // top-level android.webkit.RenderPriority for back-compat (see below) and
    // also expose the faithful nested form so apps referencing
    // WebSettings.RenderPriority resolve.
    @Deprecated
    public enum RenderPriority { NORMAL, HIGH, LOW }

    // ----- public int constants -----
    public static final int LOAD_DEFAULT = -1;
    @Deprecated public static final int LOAD_NORMAL = 0;
    public static final int LOAD_CACHE_ELSE_NETWORK = 1;
    public static final int LOAD_NO_CACHE = 2;
    public static final int LOAD_CACHE_ONLY = 3;

    public static final int MIXED_CONTENT_ALWAYS_ALLOW = 0;
    public static final int MIXED_CONTENT_NEVER_ALLOW = 1;
    public static final int MIXED_CONTENT_COMPATIBILITY_MODE = 2;

    public static final int FORCE_DARK_OFF = 0;
    public static final int FORCE_DARK_AUTO = 1;
    public static final int FORCE_DARK_ON = 2;

    // Flags for (get/set)DisabledActionModeMenuItems -- which items the
    // text-selection action-mode toolbar inside a WebView offers.
    public static final int MENU_ITEM_NONE = 0;
    public static final int MENU_ITEM_SHARE = 1 << 0;
    public static final int MENU_ITEM_WEB_SEARCH = 1 << 1;
    public static final int MENU_ITEM_PROCESS_TEXT = 1 << 2;

    // ----- stored state with realistic defaults -----
    private boolean javaScriptEnabled = false;
    private boolean javaScriptCanOpenWindowsAutomatically = false;
    private boolean useWideViewPort = false;
    private boolean loadWithOverviewMode = false;
    private boolean builtInZoomControls = false;
    private boolean displayZoomControls = true;
    private boolean supportZoom = true;
    private String userAgentString = getDefaultUserAgent(null);
    private boolean allowFileAccess = false;
    private boolean allowContentAccess = true;
    private boolean allowFileAccessFromFileURLs = false;
    private boolean allowUniversalAccessFromFileURLs = false;
    private boolean domStorageEnabled = false;
    private boolean databaseEnabled = false;
    private boolean appCacheEnabled = false;
    private String databasePath = "";
    private String appCachePath = "";
    private int cacheMode = LOAD_DEFAULT;
    private LayoutAlgorithm layoutAlgorithm = LayoutAlgorithm.NARROW_COLUMNS;
    private PluginState pluginState = PluginState.OFF;
    private int mixedContentMode = MIXED_CONTENT_NEVER_ALLOW;
    private boolean supportMultipleWindows = false;
    private boolean mediaPlaybackRequiresUserGesture = true;
    private boolean loadsImagesAutomatically = true;
    private boolean blockNetworkImage = false;
    private boolean blockNetworkLoads = false;
    private boolean geolocationEnabled = true;
    private boolean safeBrowsingEnabled = true;
    private boolean offscreenPreRaster = false;
    private boolean needInitialFocus = true;
    private int textZoom = 100;
    private int minimumFontSize = 8;
    private int minimumLogicalFontSize = 8;
    private int defaultFontSize = 16;
    private int defaultFixedFontSize = 13;
    private String defaultTextEncodingName = "UTF-8";
    private String standardFontFamily = "sans-serif";
    private String fixedFontFamily = "monospace";
    private String sansSerifFontFamily = "sans-serif";
    private String serifFontFamily = "serif";
    private String cursiveFontFamily = "cursive";
    private String fantasyFontFamily = "fantasy";
    private boolean saveFormData = true;
    @Deprecated private boolean savePassword = true;
    private boolean loadWithOverview = false;
    private ZoomDensity defaultZoom = ZoomDensity.MEDIUM;
    private TextSize textSize = TextSize.NORMAL;
    private int forceDark = FORCE_DARK_AUTO;
    private boolean acceptThirdPartyCookies = false;
    // AOSP default per javadoc: "The default value is MENU_ITEM_NONE".
    private int disabledActionModeMenuItems = MENU_ITEM_NONE;
    // AOSP: "Algorithmic darkening is disallowed by default."
    private boolean algorithmicDarkeningAllowed = false;
    // Obsolete/no-effect toggles kept only so legacy boilerplate links
    // instead of NoSuchMethodError; see the getters/setters below for the
    // AOSP deprecation notice on each.
    private boolean lightTouchEnabled = false;
    private boolean enableSmoothTransition = false;
    private boolean pluginsEnabled = false;

    // ----- the default User-Agent (static in AOSP) -----
    // A realistic Android UA so analytics/feature-detection see Android, not "".
    // The embedded Android-version token is derived from Build.VERSION.RELEASE
    // (not hardcoded) because on a real device both come from the same build --
    // anti-fraud/bot-detection and ad-SDK integrity checks are known to cross-
    // check the WebView UA's OS token against Build.VERSION.RELEASE, and a
    // mismatch there is a classic tamper signal.
    public static String getDefaultUserAgent(android.content.Context context) {
        return "Mozilla/5.0 (Linux; Android " + android.os.Build.VERSION.RELEASE
                + "; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0"
                + " Chrome/120.0.0.0 Mobile Safari/537.36";
    }

    // ----- JavaScript -----
    public void setJavaScriptEnabled(boolean flag) { javaScriptEnabled = flag; }
    public boolean getJavaScriptEnabled() { return javaScriptEnabled; }
    public void setJavaScriptCanOpenWindowsAutomatically(boolean flag) {
        javaScriptCanOpenWindowsAutomatically = flag;
    }
    public boolean getJavaScriptCanOpenWindowsAutomatically() {
        return javaScriptCanOpenWindowsAutomatically;
    }

    // ----- viewport / zoom -----
    public void setUseWideViewPort(boolean use) { useWideViewPort = use; }
    public boolean getUseWideViewPort() { return useWideViewPort; }
    public void setLoadWithOverviewMode(boolean overview) { loadWithOverviewMode = overview; }
    public boolean getLoadWithOverviewMode() { return loadWithOverviewMode; }
    public void setBuiltInZoomControls(boolean enabled) { builtInZoomControls = enabled; }
    public boolean getBuiltInZoomControls() { return builtInZoomControls; }
    public void setDisplayZoomControls(boolean enabled) { displayZoomControls = enabled; }
    public boolean getDisplayZoomControls() { return displayZoomControls; }
    public void setSupportZoom(boolean support) { supportZoom = support; }
    public boolean supportZoom() { return supportZoom; }
    public void setTextZoom(int textZoom) { this.textZoom = textZoom; }
    public int getTextZoom() { return textZoom; }
    @Deprecated public void setDefaultZoom(ZoomDensity zoom) { defaultZoom = zoom; }
    @Deprecated public ZoomDensity getDefaultZoom() { return defaultZoom; }
    @Deprecated public void setTextSize(TextSize t) { textSize = t; }
    @Deprecated public TextSize getTextSize() { return textSize; }

    // ----- user agent -----
    public void setUserAgentString(String ua) {
        userAgentString = (ua == null || ua.isEmpty()) ? getDefaultUserAgent(null) : ua;
    }
    public String getUserAgentString() { return userAgentString; }

    // ----- file / content access -----
    public void setAllowFileAccess(boolean allow) { allowFileAccess = allow; }
    public boolean getAllowFileAccess() { return allowFileAccess; }
    public void setAllowContentAccess(boolean allow) { allowContentAccess = allow; }
    public boolean getAllowContentAccess() { return allowContentAccess; }
    @Deprecated public void setAllowFileAccessFromFileURLs(boolean flag) {
        allowFileAccessFromFileURLs = flag;
    }
    public boolean getAllowFileAccessFromFileURLs() { return allowFileAccessFromFileURLs; }
    @Deprecated public void setAllowUniversalAccessFromFileURLs(boolean flag) {
        allowUniversalAccessFromFileURLs = flag;
    }
    public boolean getAllowUniversalAccessFromFileURLs() {
        return allowUniversalAccessFromFileURLs;
    }

    // ----- storage / cache -----
    public void setDomStorageEnabled(boolean flag) { domStorageEnabled = flag; }
    public boolean getDomStorageEnabled() { return domStorageEnabled; }
    @Deprecated public void setDatabaseEnabled(boolean flag) { databaseEnabled = flag; }
    @Deprecated public boolean getDatabaseEnabled() { return databaseEnabled; }
    @Deprecated public void setDatabasePath(String databasePath) { this.databasePath = databasePath; }
    @Deprecated public String getDatabasePath() { return databasePath; }
    @Deprecated public void setAppCacheEnabled(boolean flag) { appCacheEnabled = flag; }
    @Deprecated public void setAppCachePath(String appCachePath) { this.appCachePath = appCachePath; }
    @Deprecated public void setAppCacheMaxSize(long appCacheMaxSize) { }
    public void setCacheMode(int mode) { cacheMode = mode; }
    public int getCacheMode() { return cacheMode; }

    // ----- layout / plugins -----
    public void setLayoutAlgorithm(LayoutAlgorithm la) { layoutAlgorithm = la; }
    public LayoutAlgorithm getLayoutAlgorithm() { return layoutAlgorithm; }
    @Deprecated public void setPluginState(PluginState state) { pluginState = state; }
    @Deprecated public PluginState getPluginState() { return pluginState; }

    // Legacy top-level RenderPriority overload (kept for back-compat with the
    // pre-existing top-level android.webkit.RenderPriority type in this tree).
    @Deprecated public void setRenderPriority(android.webkit.RenderPriority priority) { }
    // Faithful nested-enum overload matching AOSP's actual signature.
    @Deprecated public void setRenderPriority(RenderPriority priority) { }

    // ----- deprecated / obsolete no-op toggles (kept for link-safety) -----
    // AOSP: "From JELLY_BEAN this setting is obsolete and has no effect."
    @Deprecated public void setLightTouchEnabled(boolean enabled) { lightTouchEnabled = enabled; }
    @Deprecated public boolean getLightTouchEnabled() { return lightTouchEnabled; }
    // AOSP: "This method is now obsolete, and will become a no-op in future."
    // Note the AOSP getter name has no "get" prefix.
    @Deprecated public void setEnableSmoothTransition(boolean enable) {
        enableSmoothTransition = enable;
    }
    @Deprecated public boolean enableSmoothTransition() { return enableSmoothTransition; }
    // AOSP: @SystemApi/@hide since JELLY_BEAN_MR2, "deprecated in favor of
    // setPluginState/getPluginState". Not part of the public SDK, but a few
    // apps reach it reflectively for legacy WebView-tuning boilerplate.
    @Deprecated public void setPluginsEnabled(boolean flag) { pluginsEnabled = flag; }
    @Deprecated public boolean getPluginsEnabled() { return pluginsEnabled; }

    // ----- mixed content / windows / media -----
    public void setMixedContentMode(int mode) { mixedContentMode = mode; }
    public int getMixedContentMode() { return mixedContentMode; }
    public void setSupportMultipleWindows(boolean support) { supportMultipleWindows = support; }
    public boolean supportMultipleWindows() { return supportMultipleWindows; }
    public void setMediaPlaybackRequiresUserGesture(boolean require) {
        mediaPlaybackRequiresUserGesture = require;
    }
    public boolean getMediaPlaybackRequiresUserGesture() {
        return mediaPlaybackRequiresUserGesture;
    }

    // ----- action mode / algorithmic darkening -----
    // Controls which items (Share / Web-search / Process-text) the text-
    // selection action-mode toolbar offers. Default MENU_ITEM_NONE per AOSP.
    public void setDisabledActionModeMenuItems(int menuItems) {
        disabledActionModeMenuItems = menuItems;
    }
    public int getDisabledActionModeMenuItems() { return disabledActionModeMenuItems; }
    // Whether WebView may auto-darken page content algorithmically for apps
    // targeting TIRAMISU+. Disallowed by default per AOSP.
    public void setAlgorithmicDarkeningAllowed(boolean allow) {
        algorithmicDarkeningAllowed = allow;
    }
    public boolean isAlgorithmicDarkeningAllowed() { return algorithmicDarkeningAllowed; }

    // ----- image / network loading -----
    public void setLoadsImagesAutomatically(boolean flag) { loadsImagesAutomatically = flag; }
    public boolean getLoadsImagesAutomatically() { return loadsImagesAutomatically; }
    public void setBlockNetworkImage(boolean flag) { blockNetworkImage = flag; }
    public boolean getBlockNetworkImage() { return blockNetworkImage; }
    public void setBlockNetworkLoads(boolean flag) { blockNetworkLoads = flag; }
    public boolean getBlockNetworkLoads() { return blockNetworkLoads; }

    // ----- geolocation / safe browsing / misc -----
    @Deprecated public void setGeolocationEnabled(boolean flag) { geolocationEnabled = flag; }
    @Deprecated public void setGeolocationDatabasePath(String databasePath) { }
    public void setSafeBrowsingEnabled(boolean enabled) { safeBrowsingEnabled = enabled; }
    public boolean getSafeBrowsingEnabled() { return safeBrowsingEnabled; }
    public void setOffscreenPreRaster(boolean enabled) { offscreenPreRaster = enabled; }
    public boolean getOffscreenPreRaster() { return offscreenPreRaster; }
    public void setNeedInitialFocus(boolean flag) { needInitialFocus = flag; }
    @Deprecated public void setForceDark(int forceDark) { this.forceDark = forceDark; }
    @Deprecated public int getForceDark() { return forceDark; }
    @Deprecated public void setSaveFormData(boolean save) { saveFormData = save; }
    @Deprecated public boolean getSaveFormData() { return saveFormData; }
    @Deprecated public void setSavePassword(boolean save) { savePassword = save; }
    @Deprecated public boolean getSavePassword() { return savePassword; }
    public void setAcceptThirdPartyCookies(boolean accept) { acceptThirdPartyCookies = accept; }

    // ----- fonts / encoding -----
    public void setMinimumFontSize(int size) { minimumFontSize = size; }
    public int getMinimumFontSize() { return minimumFontSize; }
    public void setMinimumLogicalFontSize(int size) { minimumLogicalFontSize = size; }
    public int getMinimumLogicalFontSize() { return minimumLogicalFontSize; }
    public void setDefaultFontSize(int size) { defaultFontSize = size; }
    public int getDefaultFontSize() { return defaultFontSize; }
    public void setDefaultFixedFontSize(int size) { defaultFixedFontSize = size; }
    public int getDefaultFixedFontSize() { return defaultFixedFontSize; }
    public void setDefaultTextEncodingName(String encoding) { defaultTextEncodingName = encoding; }
    public String getDefaultTextEncodingName() { return defaultTextEncodingName; }
    public void setStandardFontFamily(String font) { standardFontFamily = font; }
    public String getStandardFontFamily() { return standardFontFamily; }
    public void setFixedFontFamily(String font) { fixedFontFamily = font; }
    public String getFixedFontFamily() { return fixedFontFamily; }
    public void setSansSerifFontFamily(String font) { sansSerifFontFamily = font; }
    public String getSansSerifFontFamily() { return sansSerifFontFamily; }
    public void setSerifFontFamily(String font) { serifFontFamily = font; }
    public String getSerifFontFamily() { return serifFontFamily; }
    public void setCursiveFontFamily(String font) { cursiveFontFamily = font; }
    public String getCursiveFontFamily() { return cursiveFontFamily; }
    public void setFantasyFontFamily(String font) { fantasyFontFamily = font; }
    public String getFantasyFontFamily() { return fantasyFontFamily; }
}
