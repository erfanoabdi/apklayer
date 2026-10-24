// com.google.ads.AdView -- shadow stub.
//
// Mac.apk's user has stated repeatedly that ads will not be supported.
// Shadowing AdView (and a couple of helper types) here keeps Google's
// ad SDK <clinit> from running at all -- the parent ClassLoader hands
// out THIS class instead of the bytecode in the app jar, because
// Boot.java parents the URLClassLoader at the JVM-startup loader.
//
// This is the same Wine pattern used for any "do not enable" SDK:
// provide an empty drop-in replacement and leave the real
// implementation forever unloaded.
package com.google.ads;

import android.content.Context;
import android.view.ViewGroup;

public class AdView extends ViewGroup {
    public AdView(Context c) { super(c); }
    public AdView(Context c, AdSize size, String adUnitId) { super(c); }
    public AdView(Context c, AdSize size, String adUnitId, java.util.Map<?, ?> requestExtras) { super(c); }
    public AdView(android.app.Activity activity, AdSize size, String adUnitId) { super(activity); }
    // Constructors with the obfuscated AdSize name some APKs reference.
    public AdView(android.app.Activity activity, g size, String adUnitId) { super(activity); }
    public AdView(Context c, g size, String adUnitId) { super(c); }

    public void loadAd(AdRequest request) {}
    // Obfuscated `loadAd`. The dex bytecode invokes
    // `Lcom/google/ads/AdView;->a(Lcom/google/ads/d;)V`; we mirror that
    // exact signature so the JVM's exact-match dispatch is happy.
    public void a(d request) {}
    public void destroy() {}
    public void stopLoading() {}
    public void pause() {}
    public void resume() {}
    public void setAdListener(AdListener listener) {}
    public AdSize getAdSize() { return null; }
    public String getAdUnitId() { return ""; }
    public boolean isReady()        { return false; }
}
