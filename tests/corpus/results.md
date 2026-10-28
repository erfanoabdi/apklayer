# Compatibility corpus results

Run 2026-10-28 09:21

| app | kind | install | launch | first frame | note |
|---|---|---|---|---|---|
| uibench | AOSP views, GPU | ok | ok | ok | - |
| otr2 | native game, bgfx, GLES 3.1 | ok | ok | FAIL | Caused by: java.lang.ClassNotFoundException: android.webkit.WebViewFactoryProvider |
| newpipe | Java, media, WebView-less | ok | FAIL | FAIL | java.lang.NullPointerException: Cannot load from object array because "<local1>" is null |
| nextcloud | Java, network | ok | FAIL | FAIL | java.lang.UnsatisfiedLinkError: 'void apklayer.host.NativeLoader.setLibraryPath(java.lang.String)' |
| opencamera | camera | ok | ok | ok | - |
| telegram | Java + native | ok | FAIL | FAIL | java.lang.ClassNotFoundException: org.telegram.messenger.AnimatedFileDrawableStream |
| whatsapp | Java + native | ok | FAIL | FAIL | java.lang.NullPointerException |
| cpu-z | system info | ok | ok | ok | - |
| drminfo | DRM info | ok | FAIL | FAIL | java.lang.NullPointerException: it.hdrCapabilities must not be null |
| gpstest | location | ok | ok | ok | - |
| wireguard | VPN, native | ok | FAIL | FAIL | exited after its first frames (status 134) |
| openvpn | VPN, native | ok | ok | ok | - |
| shelter | device admin | ok | ok | ok | - |
| vibrations | vibrator | ok | ok | ok | - |
| barcode | camera | ok | FAIL | FAIL | java.lang.IllegalStateException: Native MediaPlayer callback Parcel adapter is not installed |
| astro | files | ok | FAIL | FAIL | java.lang.RuntimeException: Could not instantiate class yf1. |
| device-info | system info | ok | ok | ok | - |
| sensors | sensors | ok | ok | ok | - |
| microg | Google services | ok | ok | ok | - |
| documentsui | system app | ok | ok | ok | - |

11 of 20 reach a first GPU frame.
