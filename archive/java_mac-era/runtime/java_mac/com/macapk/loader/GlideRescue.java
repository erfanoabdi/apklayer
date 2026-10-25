// Mac.apk v1.0.111 -- GlideRescue: walks the view tree finding
// ImageViews that have a pending Glide load, extracts the model URL
// via reflection, fetches synchronously through HttpURLConnection,
// and sets the bitmap on the ImageView directly.
//
// Why this exists: F-Droid's Glide load path runs through Ktor +
// kotlinx.coroutines. In our environment, GlobalScope.launch(Dispatchers.IO)
// inside org.fdroid.download.glide.HttpFetcher.loadData never dispatches
// -- all DefaultDispatcher workers stay parked, all OkHttp Dispatcher
// threads stay idle, zero HTTP requests fire. Thread-dump diagnosis at
// elapsed=80s confirms this. Diagnosing the underlying coroutine issue
// is several hours of work for a future session.
//
// This rescue is a TARGETED, RETIRABLE bypass: it runs against any
// ImageView whose Glide-tag indicates a pending REMOTE load, and is a
// no-op once Glide can do its own loads. The reflection-only design
// means we never reference F-Droid types (org.fdroid.download.*) at
// compile time, just at runtime via getMethod / invoke -- so this code
// is library-agnostic.
//
// To retire (in Current_State.md "Per-APK patches"): once the Kotlin
// coroutine dispatch path works for Glide loads in our env, this whole
// file goes away.

package com.macapk.loader;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class GlideRescue {
    private static final String TAG = "Mac.apk.GlideRescue";

    /** Glide's "request" tag id on a view, declared in
     *  com.bumptech.glide.R.id.glide_custom_view_target_tag. We discover
     *  it reflectively the first time it's needed and cache here. */
    private static volatile int TAG_ID = -1;

    /** Avoid re-fetching the same URL repeatedly. Keyed by URL, value
     *  is the decoded bitmap. */
    private static final ConcurrentHashMap<String, Bitmap> CACHE = new ConcurrentHashMap<>();
    // WEAK (v1.0.1643): the IdentityHashMap-backed set held every ImageView
    // ever scanned -- and each one's activity -- for the life of the process.
    private static final java.util.Set<ImageView> SEEN =
        java.util.Collections.synchronizedSet(java.util.Collections.newSetFromMap(
            new java.util.WeakHashMap<ImageView, Boolean>()));
    // URLs with a fetch in flight (or done). Dedups concurrent fetches by URL
    // (not by ImageView) so a recycled RV ImageView with a new URL still fetches,
    // and a cached URL re-applies to any view whose drawable was cleared.
    private static final java.util.Set<String> FETCHING =
        java.util.Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    // v1.0.406: per-URL rounding radius (px) harvested from the Glide request's
    // RoundedCorners/CircleCrop transform, so the decoded bitmap is rounded the
    // way the app asked (F-Droid's app icon uses RoundedCorners). -1 = circle.
    private static final java.util.Map<String, Integer> ROUND_RADIUS =
        new ConcurrentHashMap<String, Integer>();

    private static final ExecutorService POOL = Executors.newFixedThreadPool(8, new ThreadFactory() {
        private final AtomicInteger n = new AtomicInteger();
        @Override public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "macapk-glide-rescue-" + n.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    });

    private GlideRescue() {}

    /** Walk the tree once per call, kicking off async fetches for any
     *  ImageView with a Glide-tag whose model we can resolve. Idempotent
     *  per ImageView (each is scanned once). */
    private static volatile int scanCount = 0;
    private static final java.util.concurrent.atomic.AtomicInteger loggedUrls = new java.util.concurrent.atomic.AtomicInteger();
    private static final java.util.concurrent.atomic.AtomicInteger bitsLoaded = new java.util.concurrent.atomic.AtomicInteger();
    public static void scanAndFetch(View rootView) {
        if (rootView == null) return;
        // Sync-tag resolution on the main thread. Fetches run on POOL.
        List<ImageView> targets = new ArrayList<>();
        collectImageViews(rootView, targets);
        scanCount++;
        // Log first call and every 60 frames (~1s at 60fps).
        if (scanCount == 1 || scanCount % 60 == 0) {
            int withTags = 0;
            for (ImageView iv : targets) {
                try {
                    Field f = View.class.getDeclaredField("keyedTags");
                    f.setAccessible(true);
                    Object t = f.get(iv);
                    if (t instanceof java.util.Map<?, ?> m && !m.isEmpty()) withTags++;
                } catch (Throwable ignored) {}
            }
            NativeLog.i(TAG, "scan#" + scanCount + ": " + targets.size()
                + " ImageViews, " + withTags + " with non-empty keyedTags");
        }
        if (targets.isEmpty()) return;
        for (ImageView iv : targets) {
            // Find Glide's request tag by walking the view's keyed-tag
            // SparseArray. Glide's R.id.glide_custom_view_target_tag is
            // baked into Glide's bytecode at the APK's compile time;
            // our framework can't know the integer key. Probing all
            // keyed tags and detecting a Glide Request by class name
            // avoids any per-APK class reference. We re-scan every pass (no
            // permanent per-view skip): an ImageView's Glide request can attach
            // AFTER the first scan (F-Droid's FeatureImage gets its icon request
            // only once the app data binds), and the app can CLEAR a view's
            // drawable later -- F-Droid re-runs FeatureImage.loadImageAndDisplay
            // on each data refresh, whose Glide into() sets a null placeholder
            // over our applied icon. Re-applying the cached bitmap whenever the
            // drawable goes null (below) survives both.
            Object tag = findGlideRequestTag(iv);
            if (tag == null) continue;
            String url = extractModelUrl(tag);
            if (loggedUrls.incrementAndGet() <= 3) {
                NativeLog.i(TAG, "extracted url=" + url + " for tag=" + tag.getClass().getSimpleName());
            }
            if (url == null) continue;
            // Harvest the request's rounding transform (RoundedCorners radius /
            // CircleCrop) so the decoded bitmap is rounded the way the app asked.
            int rr = extractRoundingRadius(tag);
            if (rr != 0) ROUND_RADIUS.putIfAbsent(url, rr);
            // Cached? (Re-)apply on the main thread when the view has no drawable
            // -- first load, or after the app cleared it. getDrawable()!=null means
            // our image is already showing, so we don't churn.
            Bitmap cached = CACHE.get(url);
            if (cached != null) {
                if (iv.getDrawable() == null) {
                    final ImageView fiv = iv; final Bitmap fbm = cached;
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        if (fiv.getDrawable() == null) applyBitmap(fiv, fbm);
                    });
                }
                continue;
            }
            // Not cached: fetch once per URL (dedup across views + recycling).
            if (!FETCHING.add(url)) continue;
            // Target size for downsampling: the ImageView's laid-out bounds
            // (read now, on the loop/scan thread). A screenshot shown in a small
            // thumbnail does not need its full 2160x3840 resolution.
            final int reqW = Math.max(1, iv.getRight() - iv.getLeft());
            final int reqH = Math.max(1, iv.getBottom() - iv.getTop());
            POOL.submit(() -> {
                int n = bitsLoaded.incrementAndGet();
                if (n <= 3) NativeLog.i(TAG, "fetch start #" + n + ": " + url);
                try {
                    byte[] bytes = httpGet(url);
                    if (n <= 3) NativeLog.i(TAG, "fetch got " + bytes.length + " bytes #" + n);
                    // Decode bounds first, then downsample to the target size --
                    // the AOSP/Glide "load large bitmaps efficiently" pattern. A
                    // full-res decode of several 8 MP screenshots OOM'd the heap.
                    BitmapFactory.Options bo = new BitmapFactory.Options();
                    bo.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bo);
                    int tw = reqW, th = reqH;
                    // Cap the target so a degenerate (huge) ImageView bound still
                    // bounds memory; screenshots/feature graphics never need >1080px.
                    if (tw > 1080) tw = 1080;
                    if (th > 1080) th = 1080;
                    bo.inSampleSize = computeInSampleSize(bo.outWidth, bo.outHeight, tw, th);
                    bo.inJustDecodeBounds = false;
                    Bitmap bm = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bo);
                    if (bm == null) {
                        NativeLog.w(TAG, "decode failed for " + url);
                        FETCHING.remove(url);   // allow a later retry
                        return;
                    }
                    if (n <= 3) NativeLog.i(TAG, "decoded " + bm.getWidth() + "x" + bm.getHeight() + " #" + n);
                    // Round the decoded bitmap before caching. Priority:
                    //   1) the app's own RoundedCorners/CircleCrop transform, if any;
                    //   2) the Android rounded-app-icon treatment -- a SQUARE,
                    //      icon-sized image with no explicit transform gets the
                    //      adaptive-icon rounded-square look (real Android masks
                    //      app icons; F-Droid serves a square PNG and relies on
                    //      that). Screenshots / feature graphics are not square,
                    //      so they're untouched. Gated MACAPK_ICON_ROUND=0.
                    Integer rr2 = ROUND_RADIUS.get(url);
                    int radius = (rr2 != null) ? rr2 : 0;
                    if (radius == 0) {
                        int bw = bm.getWidth(), bh = bm.getHeight();
                        boolean square = bw > 0 && bh > 0
                            && Math.abs(bw - bh) <= Math.max(bw, bh) * 0.08;
                        boolean iconSized = reqW <= 192 && reqH <= 192;  // <= ~96dp
                        if (square && iconSized
                                && !"0".equals(System.getenv("MACAPK_ICON_ROUND")))
                            radius = Math.round(Math.min(bw, bh) * 0.18f);
                    }
                    final Bitmap finalBm = (radius != 0) ? roundBitmap(bm, radius) : bm;
                    CACHE.put(url, finalBm);   // stays cached -> re-applied to any view that loses its drawable
                    // Hop to main via the global Handler queue so the
                    // bitmap-set happens during the next frame's drain.
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> applyBitmap(iv, finalBm));
                } catch (Throwable t) {
                    NativeLog.w(TAG, "fetch failed for " + url + ": " + t);
                    FETCHING.remove(url);   // allow a later retry
                }
            });
        }
    }

    private static final java.util.concurrent.atomic.AtomicInteger applied = new java.util.concurrent.atomic.AtomicInteger();
    private static void applyBitmap(ImageView iv, Bitmap bm) {
        try {
            iv.setImageDrawable(new BitmapDrawable(null, bm));
            if ("1".equals(System.getenv("MACAPK_TRACE_FEATUREIMG"))
                    && iv.getClass().getSimpleName().contains("FeatureImage")) {
                NativeLog.i(TAG, "applied bitmap to FeatureImage "
                    + bm.getWidth() + "x" + bm.getHeight()
                    + " getDrawable=" + (iv.getDrawable() != null)
                    + " bounds=[" + iv.getLeft() + "," + iv.getTop() + "-" + iv.getRight() + "," + iv.getBottom() + "]");
            }
            int n = applied.incrementAndGet();
            if (n <= 5 || n % 20 == 0) {
                NativeLog.i(TAG, "applied bitmap #" + n + " to ImageView@"
                    + Integer.toHexString(System.identityHashCode(iv))
                    + " bounds=[" + iv.getLeft() + "," + iv.getTop() + "-"
                    + iv.getRight() + "," + iv.getBottom() + "]");
            }
        } catch (Throwable t) {
            NativeLog.w(TAG, "setImageDrawable failed: " + t);
        }
    }

    /** AOSP "Loading Large Bitmaps Efficiently" calculateInSampleSize: the
     *  largest power-of-two subsample that keeps the decoded image at least as
     *  large as the target box. */
    private static int computeInSampleSize(int srcW, int srcH, int reqW, int reqH) {
        int sample = 1;
        if (srcW <= 0 || srcH <= 0 || reqW <= 0 || reqH <= 0) return 1;
        if (srcH > reqH || srcW > reqW) {
            final int halfH = srcH / 2, halfW = srcW / 2;
            while ((halfH / sample) >= reqH && (halfW / sample) >= reqW) sample *= 2;
        }
        return Math.max(1, sample);
    }

    private static int countKeyedTags(View v) {
        try {
            Field f = View.class.getDeclaredField("keyedTags");
            f.setAccessible(true);
            Object t = f.get(v);
            if (t instanceof java.util.Map<?, ?> m) return m.size();
        } catch (Throwable ignored) {}
        return -1;
    }

    private static void collectImageViews(View v, List<ImageView> out) {
        if (v == null) return;
        if (v instanceof ImageView iv) out.add(iv);
        if (v instanceof ViewGroup vg) {
            int n = vg.getChildCount();
            for (int i = 0; i < n; i++) collectImageViews(vg.getChildAt(i), out);
        }
    }

    private static int ensureTagId() {
        int v = TAG_ID;
        if (v != -1) return v;
        try {
            Class<?> rid = Class.forName("com.bumptech.glide.R$id",
                true, Thread.currentThread().getContextClassLoader());
            Field f = rid.getField("glide_custom_view_target_tag");
            TAG_ID = f.getInt(null);
        } catch (Throwable t) {
            TAG_ID = 0;
        }
        return TAG_ID;
    }

    /** Probe the View's keyed-tag map for any tag whose value looks
     *  like a Glide Request (com.bumptech.glide.request.*). In our
     *  framework's View, keyedTags is a HashMap<Integer, Object>
     *  (AOSP uses a SparseArray; the observable contents are
     *  identical). */
    private static volatile boolean dumpedOnce = false;
    private static Object findGlideRequestTag(View v) {
        try {
            Field f = View.class.getDeclaredField("keyedTags");
            f.setAccessible(true);
            Object tags = f.get(v);
            if (!(tags instanceof java.util.Map<?, ?> map)) return null;
            // Diagnostic dump on FIRST ImageView seen with non-empty tags
            // (regardless of match), to discover Glide's actual storage shape.
            if (!dumpedOnce && !map.isEmpty()) {
                dumpedOnce = true;
                StringBuilder sb = new StringBuilder("tags on ImageView@"
                    + Integer.toHexString(System.identityHashCode(v)) + ":");
                for (java.util.Map.Entry<?, ?> e : map.entrySet()) {
                    sb.append(" [");
                    sb.append(e.getKey());
                    sb.append("]=");
                    sb.append(e.getValue() == null ? "null" : e.getValue().getClass().getName());
                }
                NativeLog.i(TAG, sb.toString());
            }
            // Probe for Glide's request. Accept any class whose name
            // contains "glide" (case-insensitive) AND has a "model"
            // accessor; this catches both com.bumptech.glide.request.*
            // and (less commonly) custom request classes.
            for (Object val : map.values()) {
                if (val == null) continue;
                String cls = val.getClass().getName();
                if (cls.toLowerCase().contains("glide") || cls.contains("Request")) {
                    return val;
                }
            }
        } catch (Throwable t) {
            if (!dumpedOnce) {
                dumpedOnce = true;
                NativeLog.w(TAG, "tag probe failed: " + t);
            }
        }
        return null;
    }

    /** Given a Glide Request object (typically SingleRequest), reach
     *  through it to find the model, and from the model construct a
     *  URL string. Currently supports F-Droid's DownloadRequest model
     *  (indexFile.name + first https mirror.baseUrl). Returns null if
     *  no URL can be resolved. */
    private static volatile boolean dumpedReqOnce = false;
    private static String extractModelUrl(Object request) {
        try {
            if (!dumpedReqOnce) {
                dumpedReqOnce = true;
                StringBuilder sb = new StringBuilder("SingleRequest fields:");
                for (Class<?> c = request.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                    for (Field f : c.getDeclaredFields()) {
                        if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                        sb.append(" ").append(f.getName()).append(":").append(f.getType().getSimpleName());
                    }
                }
                NativeLog.i(TAG, sb.toString());
            }
            // SingleRequest stores the model in a private field 'model'.
            Object model = readField(request, "model");
            if (model == null) return null;
            // F-Droid DownloadRequest path: detect by method-presence
            // (NOT by invokeNoArg with String return -- getIndexFile
            // returns an IndexFile object, not a String, so the early
            // String-typed probe always failed and the fallback was
            // taking model.toString() = the entire DownloadRequest dump
            // as a "URL", producing endless HTTP errors.
            if (hasMethod(model.getClass(), "getIndexFile")
                && hasMethod(model.getClass(), "getMirrors")) {
                Object idx = invokeNoArgObject(model, "getIndexFile");
                String fileName = idx == null ? null
                    : (String) invokeNoArgObject(idx, "getName");
                String baseUrl = pickFirstHttpsMirror(model);
                if (fileName != null && baseUrl != null) {
                    // Normalise to exactly one slash between base and file:
                    //   base="...repo"   file="/app/..."  -> "...repo/app/..."
                    //   base="...repo/"  file="/app/..."  -> "...repo/app/..."
                    //   base="...repo"   file="app/..."   -> "...repo/app/..."
                    //   base="...repo/"  file="app/..."   -> "...repo/app/..."
                    String b = baseUrl.endsWith("/")
                        ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
                    String f = fileName.startsWith("/")
                        ? fileName : "/" + fileName;
                    return b + f;
                }
                return null;  // DownloadRequest but couldn't resolve; don't fall through
            }
            // Generic fallbacks for String / Uri / URL models.
            if (model instanceof String s) return s;
            String s = model.toString();
            if (s.startsWith("http://") || s.startsWith("https://")) return s;
            return null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean hasMethod(Class<?> c, String name) {
        for (java.lang.reflect.Method m : c.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == 0) return true;
        }
        return false;
    }

    private static String pickFirstHttpsMirror(Object downloadRequest) {
        try {
            Object mirrors = invokeNoArgObject(downloadRequest, "getMirrors");
            if (!(mirrors instanceof List<?> ml)) return null;
            // First pass: https + non-onion + non-IPFS.
            for (Object m : ml) {
                String base = (String) invokeNoArgObject(m, "getBaseUrl");
                Boolean ipfs = (Boolean) invokeNoArgObject(m, "isIpfsGateway");
                if (base == null) continue;
                if (!base.startsWith("https://")) continue;
                if (base.contains(".onion")) continue;
                if (ipfs != null && ipfs) continue;
                return base;
            }
            // Fallback: any https.
            for (Object m : ml) {
                String base = (String) invokeNoArgObject(m, "getBaseUrl");
                if (base != null && base.startsWith("https://") && !base.contains(".onion")) {
                    return base;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static byte[] httpGet(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("GET");
        c.setConnectTimeout(15_000);
        c.setReadTimeout(30_000);
        c.setRequestProperty("User-Agent", "Mac.apk/1.0 GlideRescue");
        c.setRequestProperty("Accept", "image/*");
        try {
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new java.io.IOException("HTTP " + code + " for " + urlStr);
            }
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream bao = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) >= 0) bao.write(buf, 0, n);
                return bao.toByteArray();
            }
        } finally {
            c.disconnect();
        }
    }

    private static Object readField(Object o, String name) {
        if (o == null) return null;
        for (Class<?> c = o.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(o);
            } catch (NoSuchFieldException ignored) {}
            catch (Throwable t) { return null; }
        }
        return null;
    }

    /** v1.0.406: read the rounding transform off a Glide request (a
     *  SingleRequest). Returns the RoundedCorners radius in px, -1 for a
     *  CircleCrop, or 0 when the request has no rounding transform. The
     *  transforms live in BaseRequestOptions.transformations (Class -> Transformation). */
    private static int extractRoundingRadius(Object request) {
        try {
            Object opts = readField(request, "requestOptions");
            Object trans = (opts != null) ? readField(opts, "transformations") : null;
            if (trans == null) trans = readField(request, "transformations");
            if (!(trans instanceof java.util.Map<?, ?>)) return 0;
            for (Object v : ((java.util.Map<?, ?>) trans).values()) {
                if (v == null) continue;
                String cn = v.getClass().getName();
                if (cn.endsWith("CircleCrop")) return -1;
                if (cn.endsWith("RoundedCorners")) {
                    Object r = readField(v, "roundingRadius");
                    if (r instanceof Integer && (Integer) r > 0) return (Integer) r;
                }
                if (cn.endsWith("GranularRoundedCorners")) {
                    Object r = readField(v, "topLeft");   // approximate with one corner
                    if (r instanceof Float && (Float) r > 0f) return Math.round((Float) r);
                }
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    /** Round a bitmap's corners offscreen (radius px; -1 = circle), mirroring
     *  Glide's RoundedCorners/CircleCrop transform. Anti-aliased via the
     *  canvas clip. Returns the original on any failure. */
    private static Bitmap roundBitmap(Bitmap src, int radius) {
        try {
            if (src == null || radius == 0) return src;
            int w = src.getWidth(), h = src.getHeight();
            if (w <= 0 || h <= 0) return src;
            int r = (radius < 0) ? Math.min(w, h) / 2 : Math.min(radius, Math.min(w, h) / 2);
            if (r <= 0) return src;
            Bitmap out = Bitmap.createBitmapWithCanvasBacking(w, h);
            android.graphics.Canvas c = new android.graphics.Canvas(out);
            android.graphics.Path clip = new android.graphics.Path();
            clip.addRoundRect(new android.graphics.RectF(0, 0, w, h), r, r,
                android.graphics.Path.Direction.CW);
            c.clipPath(clip);
            c.drawBitmap(src, 0, 0, null);
            return out;
        } catch (Throwable t) {
            return src;
        }
    }

    /** Helper that returns null for non-String returns. */
    private static String invokeNoArg(Object o, String method) {
        Object r = invokeNoArgObject(o, method);
        return r instanceof String ? (String) r : null;
    }

    private static Object invokeNoArgObject(Object o, String method) {
        if (o == null) return null;
        try {
            Method m = o.getClass().getMethod(method);
            return m.invoke(o);
        } catch (Throwable t) {
            return null;
        }
    }
}
