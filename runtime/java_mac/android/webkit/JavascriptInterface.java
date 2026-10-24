package android.webkit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// android.webkit.JavascriptInterface (AOSP API-17). Marks a method on an
// object passed to WebView.addJavascriptInterface as callable from JS. The
// annotation MUST resolve at class-load time or hybrid frameworks
// (Cordova/Capacitor/Ionic) that annotate their bridge methods fail to load
// the bridge class entirely. RUNTIME retention so a future WKWebView bridge
// can reflect the annotated methods at call time.
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface JavascriptInterface {
}
