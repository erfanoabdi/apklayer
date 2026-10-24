package com.google.android.gms.ads;
/** Base of the ad-request builder chain (AdRequest.Builder / PublisherAdRequest.
 *  Builder / AdManagerAdRequest.Builder extend it). Setters return this base type
 *  (the AdMob SDK's self-type contract). */
public abstract class AbstractAdRequestBuilder<T extends AbstractAdRequestBuilder<T>> {
    public AbstractAdRequestBuilder<T> addNetworkExtrasBundle(Class<?> adNetworkClass, android.os.Bundle networkExtras) { return this; }
    public AbstractAdRequestBuilder<T> addCustomEventExtrasBundle(Class<?> adapterClass, android.os.Bundle customEventExtras) { return this; }
    public AbstractAdRequestBuilder<T> setRequestAgent(String requestAgent) { return this; }
    public AbstractAdRequestBuilder<T> setAdString(String adString) { return this; }
    public AbstractAdRequestBuilder<T> addKeyword(String keyword) { return this; }
    public AbstractAdRequestBuilder<T> setContentUrl(String contentUrl) { return this; }
    public AbstractAdRequestBuilder<T> setNeighboringContentUrls(java.util.List<String> urls) { return this; }
}
