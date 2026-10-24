package android.webkit;

import android.os.Handler;

// android.webkit.WebMessagePort (AOSP API-23).
// One end of an HTML5 message channel. Abstract in AOSP; the engine subclasses
// it. No engine yet so postMessage/close are never driven, but the type and the
// nested WebMessageCallback resolve. Ref:
// https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/webkit/WebMessagePort.java
public abstract class WebMessagePort {

    public static abstract class WebMessageCallback {
        public void onMessage(WebMessagePort port, WebMessage message) {}
    }

    public abstract void postMessage(WebMessage message);

    public abstract void close();

    public abstract void setWebMessageCallback(WebMessageCallback callback);

    public abstract void setWebMessageCallback(WebMessageCallback callback, Handler handler);
}
