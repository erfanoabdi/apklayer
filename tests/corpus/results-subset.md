# Compatibility corpus results

Run 2026-11-04 02:40

"first frame" = a GPU frame while running and the screenshot shows content (uniform/nowindow otherwise); "second" = the same after a second launch.

| app | kind | install | launch | first frame | second | fakes | note |
|---|---|---|---|---|---|---|---|
| astro | files | ok | ok | ok | ok | 25 | - |
| device-info | system info | ok | ok | ok | ok | 24 | - |
| sensors | sensors | ok | ok | ok | ok | 27 | - |
| microg | Google services | ok | ok | ok | ok | 23 | - |
| documentsui | system app | ok | ok | uniform | - | 26 | android.os.ServiceManager$ServiceNotFoundException: No service published for: overlay |
| unciv | libgdx, Kotlin | ok | ok | uniform | - | 23 | Loaded 2 rulesets in 68ms |
| openttd | SDL 1.2, GLES1 | ok | ok | ok | ok | 21 | - |

5 of 7 reach a steady GPU picture with content.
