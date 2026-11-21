# Download sources (downloads.json)

Where `apklayer setup --gapps`, `--microg` and `--fdroid` download from is data, not code: `<share>/apklayer/downloads.json` (`/usr/share/apklayer/downloads.json` installed, `data/downloads.json` in the source tree).

```json
{
  "gapps":  {"arm64": {"version": "2026-10-12", "url": "https://...zip", "sha256": "<64 hex>"}, "amd64": {...}},
  "microg": {"index": "https://microg.org/fdroid/repo/index-v1.json", "packages": ["com.google.android.gms", "com.android.vending"]},
  "fdroid": {"index": "https://f-droid.org/repo/index-v1.json", "packages": ["org.fdroid.fdroid"]}
}
```

The `gapps` zips are apklayer's own release `gapps-<commit>` (assets `apklayer-gapps-arm64.zip`, `apklayer-gapps-x86_64.zip`): the `gapps` branch holds
LiteGapps' superlite set unchanged (Services Framework, Play services, Play Store, the two sync adapters, Google's XMLs; its `sources` names the
LiteGapps zips) and the workflow `gapps` packs it. A new LiteGapps build: edit `sources` on the `gapps` branch, `gh workflow run gapps -f import=true`
(the runner downloads LiteGapps and pushes the branch); the same run points `data/downloads.json` on `main` at the new release (URLs, sha256,
LiteGapps' build date as the version) and pushes it. The zips are reproducible: `sh pack.sh <out>` on a checkout gives the same sha256.

## Changing a URL or hash after install

Edit nothing under `/usr`: write `~/.config/apklayer/downloads.json` (`$XDG_CONFIG_HOME/apklayer/downloads.json`) with only what changes. It is merged over the installed file per entry: `gapps.arm64` (url, sha256 and version together), `microg`, `fdroid` (index and packages together). Entries it lacks stay as installed. So a new LiteGapps build for arm64 is

```json
{"gapps": {"arm64": {"url": "https://.../apklayer-gapps-arm64.zip", "sha256": "..."}}}
```

The environment wins over both files: `APKLAYER_GAPPS_URL`, `APKLAYER_GAPPS_SHA256`, `APKLAYER_MICROG_INDEX`, `APKLAYER_FDROID_INDEX`.

A missing or invalid file stops the setup step with an error naming the file. Not in this file: the microG signature hashes in `ShadowHostPackageManager` (they identify microG's certificate, not a download) and the Slint pin of `manager/fetch-slint.sh` (build time).
