# Website follow-up

## Screenshots

- [x] Capture real apps and manager views from the owner's Parallels VM using fresh app data.
- [x] Add the screenshot section after application compatibility, with microG Settings and NewPipe actually playing a public demo video. Removed the earlier LocalSend, map-download, and vault-chooser captures.
- [x] Add an instance-management capture beside the instance documentation.
- [x] Record app versions, capture methods, host details, and verification scope in `assets/screenshots/README.md`; inspect captures for personal data.
- [x] Save lossless WebP images with explicit dimensions, useful alt text, lazy loading, and keyboard-accessible links to full-size images.
- [ ] Add a composed-on-the-desktop wide capture showing several fully visible app windows when interactive VM window positioning is available. Do not substitute a synthetic montage.
- [x] Verify NewPipe playback: changing frames and an active PipeWire audio stream, using the existing upstream media check.
- [ ] Add a later camera capture using non-personal sample content. OTR2 was dropped at the owner’s request; do not add a stalled game or its consent dialog as a demo.
- [ ] Visually verify desktop and mobile website layouts when browser preview access is available. Source, assets, release behaviour, and HTTP serving are checked independently.

## Technical content

Hardware coverage includes EGL/GLES, Vulkan caveats, PipeWire camera and audio, media codecs, sensors, vibration, location/GNSS, USB, Bluetooth, keystore2/software KeyMint, battery, and connectivity. Platform coverage includes providers, WebView, accounts, cross-app IPC, installs, microG Services + Companion, host input/settings/portals, tooling, and the opt-in sandbox. Source links distinguish implemented backends, stand-in tests, physical device checks, and remaining gaps. Keep these details aligned with upstream changes.

## Hosting

The site is plain HTML, CSS, and JavaScript. No build step or dependencies are needed. GitHub Pages should deploy the `pages` branch from `/`, as described in the project's release documentation. All local asset and feed paths are relative to support repository subpaths. Keep `.nojekyll` and preserve `releases.json`: the manager's updater consumes it independently of this website.

Local preview: `python3 -m http.server 8000` from this directory, then visit `http://localhost:8000`.

Verification: `node --test tests/site.test.cjs` exercises the release feed and fallback links. Technical website content links to the upstream architecture, app corpus, hardware source and test groups, and device integration notes.
