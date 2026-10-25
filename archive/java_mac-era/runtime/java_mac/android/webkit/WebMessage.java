package android.webkit;

// android.webkit.WebMessage (AOSP API-23).
// A message sent over a WebMessagePort channel. Self-contained value object.
// Ref: https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/webkit/WebMessage.java
public class WebMessage {

    private final String mData;
    private final WebMessagePort[] mPorts;

    public WebMessage(String data) {
        mData = data;
        mPorts = null;
    }

    public WebMessage(String data, WebMessagePort[] ports) {
        mData = data;
        mPorts = ports;
    }

    public String getData() { return mData; }

    public WebMessagePort[] getPorts() { return mPorts; }
}
