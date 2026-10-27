package apklayer.host;

import android.view.View;
import android.view.ViewGroup;
import android.webkit.ValueCallback;
import android.webkit.WebView;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowWebView;

/**
 * android.webkit.WebView backend (PLAN P5.1). Robolectric's WebView only records loadUrl/loadData/
 * evaluateJavascript; here each WebView gets an engine process (bridge/webview/apklayer-webview.py,
 * WebKitGTK offscreen) that is driven from those recordings. Its frames use the GL frame format and are
 * blitted over the WebView's area (HostSurfaces.blit); page-finished and script results come back as
 * WebViewClient / ValueCallback calls.
 */
final class HostWebViews {
    private HostWebViews() {}

    private static final class Engine {
        final WebView view;
        final Process process;
        final OutputStream in;
        final Queue<String[]> events = new ConcurrentLinkedQueue<>();
        final java.util.Map<String, ValueCallback<String>> pending = new java.util.HashMap<>();
        final Path frame;
        final java.util.Set<String> exposed = new java.util.HashSet<>(); // addJavascriptInterface names already injected
        String url;
        Object lastBase, lastLoaded;
        int w, h, seen, evals;
        volatile int rx = -1, ry = -1; // the engine's rectangle in the window (kept by follow(), read by pointer())

        Engine(WebView view, Path frame, Path dataDir) throws IOException {
            this.view = view;
            this.frame = frame;
            String script = System.getProperty("apklayer.webview", "bridge/webview/apklayer-webview.py");
            process = new ProcessBuilder("python3", script, frame.toString(), dataDir.toString())
                    .redirectError(ProcessBuilder.Redirect.INHERIT).start();
            in = process.getOutputStream();
            Thread reader = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    for (String line; (line = r.readLine()) != null; ) {
                        if (Boolean.getBoolean("apklayer.debugwebview")) System.out.println("[apklayer] webview< " + line);
                        events.add(line.split(" ", 3));
                    }
                } catch (IOException ignored) { }
            }, "webview-events");
            reader.setDaemon(true);
            reader.start();
        }

        synchronized void send(String line) {
            if (Boolean.getBoolean("apklayer.debugwebview")) System.out.println("[apklayer] webview> " + line.substring(0, Math.min(60, line.length())));
            try {
                in.write((line + "\n").getBytes(StandardCharsets.UTF_8));
                in.flush();
            } catch (IOException e) {
                System.out.println("[apklayer] webview engine gone: " + e.getMessage());
            }
        }
    }

    private static final Map<WebView, Engine> engines = new WeakHashMap<>();
    private static final String GLDIR = System.getenv("APKLAYER_GLDIR");

    static void follow() {
        if (GLDIR == null) return;
        List<View> windows = org.robolectric.util.ReflectionHelpers.getField(android.view.WindowManagerGlobal.getInstance(), "mViews");
        for (View w : windows) scan(w);
    }

    private static void scan(View v) {
        if (v instanceof WebView wv) {
            if (patched.put(wv, true) == null) giveMeasure(wv);
            if (wv.getMeasuredWidth() > 0 && wv.getMeasuredHeight() > 0) drive(wv);
        } else if (v instanceof ViewGroup g) {
            for (int i = 0; i < g.getChildCount(); i++) scan(g.getChildAt(i));
        }
    }

    private static final Map<WebView, Boolean> patched = new WeakHashMap<>();

    /**
     * Robolectric's WebView provider is a do-nothing proxy, so the view is never measured (0x0). Wrap its
     * view delegate so onMeasure fills the space the layout offers, as WebView's real delegate does.
     */
    private static void giveMeasure(WebView wv) {
        Object provider = org.robolectric.util.ReflectionHelpers.getField(wv, "mProvider");
        ClassLoader cl = provider.getClass().getClassLoader();
        android.webkit.WebViewProvider.ViewDelegate real = ((android.webkit.WebViewProvider) provider).getViewDelegate();
        var delegate = (android.webkit.WebViewProvider.ViewDelegate) java.lang.reflect.Proxy.newProxyInstance(cl,
                new Class<?>[] {android.webkit.WebViewProvider.ViewDelegate.class}, (p, m, a) -> {
                    if (!m.getName().equals("onMeasure")) return m.invoke(real, a);
                    int w = View.MeasureSpec.getMode((int) a[0]) == View.MeasureSpec.UNSPECIFIED ? 0 : View.MeasureSpec.getSize((int) a[0]);
                    int h = View.MeasureSpec.getMode((int) a[1]) == View.MeasureSpec.UNSPECIFIED ? 0 : View.MeasureSpec.getSize((int) a[1]);
                    org.robolectric.util.ReflectionHelpers.callInstanceMethod(wv, "setMeasuredDimension",
                            org.robolectric.util.ReflectionHelpers.ClassParameter.from(int.class, w),
                            org.robolectric.util.ReflectionHelpers.ClassParameter.from(int.class, h));
                    return null;
                });
        Object wrapped = java.lang.reflect.Proxy.newProxyInstance(cl, new Class<?>[] {android.webkit.WebViewProvider.class},
                (p, m, a) -> m.getName().equals("getViewDelegate") ? delegate : m.invoke(provider, a));
        org.robolectric.util.ReflectionHelpers.setField(wv, "mProvider", wrapped);
        wv.requestLayout();
    }

    private static void drive(WebView wv) {
        if (System.getProperty("apklayer.webviewprovider") != null) return; // the provider drives its own WebViews
        Engine e = engines.get(wv);
        ShadowWebView shadow = Shadow.extract(wv);
        if (!(shadow instanceof ShadowHostWebView)) {
            if (engines.putIfAbsent(wv, null) == null) System.out.println("[apklayer] webview shadow is " + shadow.getClass().getName() + ", not ShadowHostWebView: no engine");
            return;
        }
        try {
            if (e == null) {
                Path dir = Path.of(System.getProperty("apklayer.data", System.getProperty("java.io.tmpdir")), "webview");
                Files.createDirectories(dir);
                engines.put(wv, e = new Engine(wv, Path.of(GLDIR, wv.hashCode() + ".rgba"), dir));
                System.out.println("[apklayer] webview engine started for " + wv.hashCode());
            }
        } catch (IOException ex) {
            System.out.println("[apklayer] cannot start the webview engine: " + ex.getMessage());
            engines.put(wv, null);
            return;
        }
        if (e == null) return;
        if (e.w != wv.getMeasuredWidth() || e.h != wv.getMeasuredHeight()) {
            e.send("size " + (e.w = wv.getMeasuredWidth()) + " " + (e.h = wv.getMeasuredHeight()));
        }
        for (String cmd; (cmd = ((ShadowHostWebView) shadow).commands.poll()) != null; ) e.send(cmd);
        exposeInterfaces(wv, shadow, e); // before any load: the page's own scripts may call them at once
        // Robolectric keeps one record per kind of load; whichever changed since the last look is the new load.
        String url = shadow.getLastLoadedUrl();
        ShadowWebView.LoadDataWithBaseURL withBase = shadow.getLastLoadDataWithBaseURL();
        ShadowWebView.LoadData loaded = shadow.getLastLoadData();
        if (withBase != null && withBase != e.lastBase) {
            e.lastBase = withBase;
            e.url = url;
            e.send("html " + b64(withBase.data) + (withBase.baseUrl != null ? " " + withBase.baseUrl : ""));
        } else if (loaded != null && loaded != e.lastLoaded) {
            e.lastLoaded = loaded;
            e.url = url;
            e.send("html " + ("base64".equalsIgnoreCase(loaded.encoding) ? loaded.data : b64(android.net.Uri.decode(loaded.data))));
        } else if (url != null && !url.equals(e.url)) {
            e.url = url;
            e.send("load " + url);
        }
        for (Object[] call; (call = ((ShadowHostWebView) shadow).evals.poll()) != null; ) {
            String id = String.valueOf(++e.evals);
            if (call[1] != null) e.pending.put(id, (ValueCallback<String>) call[1]);
            e.send("eval " + id + " " + b64((String) call[0]));
        }
        for (String[] ev; (ev = e.events.poll()) != null; ) {
            switch (ev[0]) {
                case "history" -> {
                    ((ShadowHostWebView) shadow).canBack = "1".equals(ev[1]);
                    ((ShadowHostWebView) shadow).canForward = ev.length > 2 && "1".equals(ev[2]);
                }
                case "started" -> {
                    ((ShadowHostWebView) shadow).currentUrl = ev.length > 1 ? ev[1] : null;
                    if (wv.getWebViewClient() != null) wv.getWebViewClient().onPageStarted(wv, ev.length > 1 ? ev[1] : "", null);
                }
                case "title" -> {
                    android.webkit.WebChromeClient chrome = shadow.getWebChromeClient();
                    String title = ev.length > 1 ? new String(Base64.getDecoder().decode(ev[1]), StandardCharsets.UTF_8) : "";
                    ((ShadowHostWebView) shadow).currentTitle = title;
                    if (chrome != null) chrome.onReceivedTitle(wv, title);
                }
                case "call" -> e.send("answer " + ev[1] + " " + b64(bridgeCall(wv, shadow, new String(Base64.getDecoder().decode(ev[2]), StandardCharsets.UTF_8))));
                case "nav" -> { // a link click: the app's shouldOverrideUrlLoading decides (true = the app handles it)
                    boolean override = wv.getWebViewClient() != null && wv.getWebViewClient().shouldOverrideUrlLoading(wv, request(ev[2]));
                    e.send("policy " + ev[1] + (override ? " block" : " allow"));
                }
                case "loaded" -> {
                    if (wv.getWebViewClient() != null) wv.getWebViewClient().onPageFinished(wv, ev.length > 1 ? ev[1] : "");
                }
                case "eval" -> {
                    ValueCallback<String> cb = e.pending.remove(ev[1]);
                    if (cb != null) cb.onReceiveValue(new String(Base64.getDecoder().decode(ev[2]), StandardCharsets.UTF_8));
                }
                default -> { }
            }
        }
        if (wv.getParent() instanceof View parent && parent.isShown()) {
            int[] loc = new int[2];
            parent.getLocationOnScreen(loc);
            e.rx = loc[0];
            e.ry = loc[1];
        } else {
            e.rx = -1;
        }
        int seq = seq(e.frame);
        if (seq != e.seen) {
            e.seen = seq;
            wv.getRootView().invalidate(); // the view itself has no bounds (see giveMeasure), so dirty the whole window
        }
    }

    private static android.webkit.WebResourceRequest request(String url) {
        return new android.webkit.WebResourceRequest() {
            public android.net.Uri getUrl() { return android.net.Uri.parse(url); }
            public boolean isForMainFrame() { return true; }
            public boolean isRedirect() { return false; }
            public boolean hasGesture() { return true; }
            public String getMethod() { return "GET"; }
            public Map<String, String> getRequestHeaders() { return Map.of(); }
        };
    }

    // ---- addJavascriptInterface: JS calls come back as synchronous XHRs to a loopback server ----

    /** The page's call apklayer://bridge/<interface>/<method>?a=<JSON args>; runs the Java method, returns JSON. */
    private static String bridgeCall(WebView wv, ShadowWebView shadow, String pathAndQuery) {
        String[] pq = pathAndQuery.split("\\?a=", 2), p = pq[0].split("/");
        try {
            Object target = shadow.getJavascriptInterface(p[0]);
            org.json.JSONArray args = new org.json.JSONArray(pq.length > 1 ? java.net.URLDecoder.decode(pq[1], StandardCharsets.UTF_8) : "[]");
            for (java.lang.reflect.Method m : target == null ? new java.lang.reflect.Method[0] : target.getClass().getMethods()) {
                if (!m.getName().equals(p[1]) || m.getParameterCount() != args.length()) continue;
                Object[] a = new Object[args.length()];
                for (int i = 0; i < a.length; i++) {
                    Class<?> t = m.getParameterTypes()[i];
                    a[i] = t == int.class ? args.getInt(i) : t == long.class ? args.getLong(i) : t == double.class ? args.getDouble(i)
                            : t == boolean.class ? args.getBoolean(i) : t == float.class ? (float) args.getDouble(i) : args.isNull(i) ? null : args.getString(i);
                }
                Object r = m.invoke(target, a);
                return r == null ? "null" : r instanceof String ? org.json.JSONObject.quote((String) r) : String.valueOf(r);
            }
        } catch (Exception ex) {
            System.out.println("[apklayer] javascript interface " + pathAndQuery + " failed: " + ex);
        }
        return "null";
    }

    /** Injects a JS object per addJavascriptInterface name (new ones only) into the engine. */
    private static void exposeInterfaces(WebView wv, ShadowWebView shadow, Engine e) {
        Map<String, Object> interfaces = org.robolectric.util.ReflectionHelpers.getField(shadow, "javascriptInterfaces");
        for (Map.Entry<String, Object> i : interfaces.entrySet()) {
            if (!e.exposed.add(i.getKey())) continue;
            StringBuilder js = new StringBuilder("window." + i.getKey() + "={");
            java.util.Set<String> names = new java.util.TreeSet<>();
            boolean annotated = false;
            for (java.lang.reflect.Method m : i.getValue().getClass().getMethods())
                if (m.isAnnotationPresent(android.webkit.JavascriptInterface.class)) { names.add(m.getName()); annotated = true; }
            if (!annotated) for (java.lang.reflect.Method m : i.getValue().getClass().getMethods())
                if (m.getDeclaringClass() != Object.class) names.add(m.getName());
            try {
                for (String n : names)
                    js.append(n).append(":function(){var x=new XMLHttpRequest();x.open('GET','apklayer://bridge/").append(i.getKey()).append("/").append(n)
                      .append("?a='+encodeURIComponent(JSON.stringify([].slice.call(arguments))),false);x.send();return JSON.parse(x.responseText);},");
                e.send("inject " + b64(js.append("};")));
            } catch (RuntimeException ex) {
                System.out.println("[apklayer] cannot expose " + i.getKey() + ": " + ex);
            }
        }
    }

    private static String b64(CharSequence s) { return b64(s.toString()); }

    private static String b64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static int seq(Path f) {
        try (var in = Files.newInputStream(f)) {
            byte[] h = in.readNBytes(12);
            return h.length < 12 ? 0 : java.nio.ByteBuffer.wrap(h).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt(8);
        } catch (IOException e) {
            return 0;
        }
    }

    private static volatile Engine pressedIn, focused;

    /**
     * Pointer events over a WebView go to its engine instead of the (frameless) view: a click on release,
     * the wheel as page scroll. ev is a HostInput event; returns true if it was consumed.
     */
    static boolean pointer(int[] ev) {
        if (ev[0] > 2 && ev[0] != 13) return false;
        Engine hit = null;
        for (Engine e : engines.values())
            if (e != null && e.rx >= 0 && ev[1] >= e.rx && ev[2] >= e.ry && ev[1] < e.rx + e.w && ev[2] < e.ry + e.h) hit = e;
        if (ev[0] == 13) {
            if (hit == null) return false;
            hit.send("scroll " + (-ev[4] / 10) + " " + (-ev[3] / 10)); // notch*1000 -> 100 px per notch; wheel up scrolls up
            return true;
        }
        if (ev[0] == 0) focused = pressedIn = hit;
        Engine down = pressedIn;
        if (down == null) return false;
        if (ev[0] == 1) {
            pressedIn = null;
            if (hit == down) down.send("click " + (ev[1] - down.rx) + " " + (ev[2] - down.ry));
        }
        return true;
    }

    /** Typing while a WebView has the pointer focus goes to its page: characters, Backspace, Delete, Enter. */
    static boolean key(int action, int code, int unicode, int meta) {
        Engine e = focused;
        if (e == null || e.rx < 0 || !e.view.isShown()) return false;
        boolean ctrl = (meta & (android.view.KeyEvent.META_CTRL_ON | android.view.KeyEvent.META_ALT_ON)) != 0;
        String name = code == android.view.KeyEvent.KEYCODE_DEL ? "BackSpace" : code == android.view.KeyEvent.KEYCODE_FORWARD_DEL ? "Delete"
                : code == android.view.KeyEvent.KEYCODE_ENTER ? "Return" : null;
        if (name != null) {
            if (action == 0) e.send("key " + name);
            return true;
        }
        if (unicode > 0 && !ctrl) {
            if (action == 0) e.send("text " + b64(new String(Character.toChars(unicode))));
            return true;
        }
        return false;
    }

    /** Blits each WebView's latest engine frame into the composed window frame. */
    static void compose(int[] argb, int w, int h) {
        for (Engine e : engines.values()) {
            if (e == null || !e.view.isShown() || !(e.view.getParent() instanceof View parent)) continue;
            int[] loc = new int[2]; // the view's own frame is not laid out (see giveMeasure): its slot is the parent's origin
            parent.getLocationOnScreen(loc);
            HostSurfaces.blit(loc[0], loc[1], e.w, e.h, e.frame, argb, w, h);
        }
    }
}
