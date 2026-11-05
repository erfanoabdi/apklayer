# Website follow-up

## Screenshots

- [x] Capture real apps and manager views from the owner's Parallels VM using fresh app data.
- [x] Add the screenshot section near the top, with microG Settings and NewPipe actually playing a public demo video. Removed the earlier LocalSend, map-download, and vault-chooser captures.
- [x] Restore the original manager overview above microG and NewPipe.
- [x] Add an instance-management capture beside the instance documentation.
- [x] Record app versions, capture methods, host details, and verification scope in `assets/screenshots/README.md`; inspect captures for personal data.
- [x] Save lossless WebP images with explicit dimensions, useful alt text, lazy loading, and keyboard-accessible links to full-size images.
- [x] Add the owner-requested full Parallels desktop capture showing Fennec, microG, OTR2, and Open Camera in separate overlapping windows. Use the whole capture in the hero without a CSS crop.
- [x] Verify NewPipe playback: changing frames and an active PipeWire audio stream, using the existing upstream media check.
- [ ] Add a later camera capture using non-personal sample content. OTR2 was dropped at the owner’s request; do not add a stalled game or its consent dialog as a demo.
- [x] Review the redesigned desktop layout in Firefox. Use an isolated browser for further website QA; do not control the owner’s Mac UI or Parallels.
- [ ] Add a short real screen recording of app launch and playback when suitable footage is available; keep a static poster and respect reduced-motion preferences.

## Technical content

Hardware coverage includes EGL/GLES, Vulkan caveats, PipeWire camera and audio, media codecs, sensors, vibration, location/GNSS, USB, Bluetooth, keystore2/software KeyMint, battery, and connectivity. Platform coverage includes providers, WebView, accounts, cross-app IPC, installs, microG Services + Companion, host input/settings/portals, tooling, and the opt-in sandbox. Source links distinguish implemented backends, stand-in tests, physical device checks, and remaining gaps. Keep these details aligned with upstream changes.

## Hosting

The site is plain HTML, CSS, and JavaScript. No build step or dependencies are needed. GitHub Pages should deploy the `pages` branch from `/`, as described in the project's release documentation. All local asset and feed paths are relative to support repository subpaths. Keep `.nojekyll` and preserve `releases.json`: the manager's updater consumes it independently of this website.

Local preview: `python3 -m http.server 8000` from this directory, then visit `http://localhost:8000`.

Verification: `node --test tests/site.test.cjs` exercises the release feed and fallback links. Technical website content links to the upstream architecture, app corpus, hardware source and test groups, and device integration notes.

## Presentation

The main page leads with the real Ubuntu desktop multiwindow capture and the retained manager screenshot. NewPipe playback remains in the expandable app gallery. Installation comes before the reference sections. Native `details`/`summary` disclosures keep all requested hardware, platform, microG, compatibility, instance, and comparison material available without a long default reading path. No new JavaScript or dependencies are required for the disclosures.

Responsive QA: checked an isolated in-app browser at 320, 375, 390, 400, 401, 700, 701, 768, 1000, and 1280 pixels with all disclosures closed and open. Fixed the installation grid’s minimum track sizing at narrow widths. Verified keyboard disclosure toggling, contained keyboard-accessible table scrolling, local assets, section anchors, and release links. No Mac UI or Parallels control was used.
