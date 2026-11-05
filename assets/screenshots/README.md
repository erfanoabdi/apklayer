# Capture provenance

Captured on 2026-11-05 from the owner's Parallels VM: Ubuntu 24.04 LTS arm64, GNOME Wayland, virgl (Apple M5 Max compatibility renderer), host OpenGL ES 3.0 / Mesa 25.2.8. These are development-tree builds, not a claim that this is the current release UI. The installed manager build predates the Hardware page.

All published files are lossless WebP conversions of PNG captures, at their captured dimensions. Decoded RGB pixels were compared with the PNGs before inclusion. No UI was generated, composited, cropped, or retouched. Full-size images are linked from the website. Each capture was visually inspected for private content.

| File | Captured state | Capture method |
| --- | --- | --- |
| `manager.webp` | LocalSend 1.18.2 app details and permissions; Organic Maps also installed in the temporary `website-gallery` environment | Native manager `--screenshot`, `--app org.localsend.localsend_app`, `--dark`; 958 × 701 |
| `localsend.webp` | LocalSend 1.18.2 Receive screen; fresh app-generated alias, no file transfer | `apklayer --instance website-gallery screenshot org.localsend.localsend_app`; 325 × 579 |
| `organic-maps.webp` | Organic Maps 2026.09.29-32-FDroid initial world-map download prompt; no maps downloaded and no location requested | `apklayer --instance website-gallery screenshot app.organicmaps`; 325 × 579 |
| `keepassdx.webp` | KeePassDX 4.5.6 fresh vault chooser; no database or credentials opened | `apklayer --instance website screenshot com.kunzisoft.keepass.libre`; 325 × 579 |
| `instances.webp` | Manager Instances page: `website` running and `default` stopped | Native manager `--screenshot`, `--page 1`, `--dark`; 958 × 701 |

The first `website` instance used fresh per-instance app data with the VM's existing app library. LocalSend and Organic Maps came from the project's existing test APK corpus and were installed under a temporary `XDG_DATA_HOME` and `XDG_STATE_HOME` in the VM; the owner's app installs were not updated or reconverted. The second environment used a distinct `website-gallery` instance and Binder devices. Both capture instances were stopped after use. No account was signed into and no physical camera was opened.

These screenshots demonstrate rendered UI and management state. They do not establish complete app compatibility, successful network transfers, map navigation, simultaneous running instances, or hardware capture.
