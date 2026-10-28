# Compatibility corpus results

Run 2026-10-28 10:27

| app | kind | install | launch | first frame | note |
|---|---|---|---|---|---|
| uibench | AOSP views, GPU | ok | ok | ok | - |
| otr2 | native game, bgfx, GLES 3.1 | ok | ok | FAIL | Caused by: java.lang.ClassNotFoundException: android.webkit.WebViewFactoryProvider |
| newpipe | Java, media, WebView-less | ok | ok | ok | - |
| nextcloud | Java, network | ok | FAIL | FAIL | java.lang.UnsatisfiedLinkError: 'void apklayer.host.NativeLoader.setLibraryPath(java.lang.String)' |
| opencamera | camera | ok | ok | ok | - |
| telegram | Java + native | ok | FAIL | FAIL | java.lang.ClassNotFoundException: org.telegram.messenger.AnimatedFileDrawableStream |
| whatsapp | Java + native | ok | FAIL | FAIL | java.lang.UnsatisfiedLinkError: couldn't find DSO to load: liblog.so result: 0 |
| cpu-z | system info | ok | ok | ok | - |
| drminfo | DRM info | ok | FAIL | FAIL | [apklayer] cannot set nativeLibraryDir: java.lang.RuntimeException: java.lang.NullPointerException: Cannot inv |
| gpstest | location | ok | ok | ok | - |
| wireguard | VPN, native | ok | FAIL | FAIL | [apklayer] cannot set nativeLibraryDir: java.lang.RuntimeException: java.lang.NullPointerException: Cannot inv |
| openvpn | VPN, native | ok | ok | ok | - |
| shelter | device admin | ok | ok | ok | - |
| vibrations | vibrator | ok | ok | ok | - |
| barcode | camera | ok | ok | ok | - |
| astro | files | ok | ok | ok | - |
| device-info | system info | ok | ok | ok | - |
| sensors | sensors | ok | ok | ok | - |
| microg | Google services | ok | ok | ok | - |
| documentsui | system app | ok | ok | ok | - |

14 of 20 reach a first GPU frame.
