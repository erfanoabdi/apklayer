# Website follow-up

## Screenshots — awaiting images from the project owner

- [ ] Add a screenshot section after the app compatibility section once real captures are supplied. Do not use generated images as evidence of running apps.
- [ ] Lead with a wide Linux desktop capture showing multiple Android apps in separate Wayland windows.
- [ ] Include apklayer-manager (app library / instance management) and two or three representative app captures, such as maps, messaging, and media.
- [ ] Confirm app names, versions, desktop, architecture, and compatibility caveats for accurate captions. Remove personal information from captures.
- [ ] Save optimized images in `assets/screenshots/`, provide meaningful alt text, explicit dimensions, and lazy loading. Keep the gallery responsive and usable by keyboard if adding image enlargement.
- [ ] Verify desktop and mobile layouts after adding the screenshots.

## Hosting

The site is plain HTML, CSS, and JavaScript. No build step or dependencies are needed. GitHub Pages should deploy the `pages` branch from `/`, as described in the project's release documentation. All local asset and feed paths are relative to support repository subpaths. Keep `.nojekyll` and preserve `releases.json`: the manager's updater consumes it independently of this website.

Local preview: `python3 -m http.server 8000` from this directory, then visit `http://localhost:8000`.

Verification: `node --test tests/site.test.cjs` exercises the release feed and fallback links. Technical website content links to the upstream architecture, app corpus, and device integration notes; keep those references current as hardware coverage changes.
