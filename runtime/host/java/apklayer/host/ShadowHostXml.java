package apklayer.host;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

/**
 * Binary XML (ABX: SettingsProvider's settings files, package restrictions...) with the framework's pure-Java codec, as Android's host-side
 * builds (ravenwood) do. The ART variants encode strings through libcore's CharsetUtils natives, which the JVM lacks: names came out empty,
 * and the files written that way could not be read back.
 */
@Implements(className = "android.util.Xml", isInAndroidSdk = false)
public class ShadowHostXml {
    @Implementation protected static com.android.modules.utils.TypedXmlPullParser newBinaryPullParser() { return new com.android.modules.utils.BinaryXmlPullParser(); }
    @Implementation protected static com.android.modules.utils.TypedXmlSerializer newBinarySerializer() { return new com.android.modules.utils.BinaryXmlSerializer(); }
}
