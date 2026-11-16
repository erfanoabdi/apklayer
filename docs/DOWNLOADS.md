# Download sources (downloads.json)

Where `apklayer setup --gapps`, `--microg` and `--fdroid` download from is data, not code: `<share>/apklayer/downloads.json` (`/usr/share/apklayer/downloads.json` installed, `data/downloads.json` in the source tree).

```json
{
  "gapps":  {"arm64": {"version": "2026-10-12", "url": "https://...zip/download", "sha256": "<64 hex>"}, "amd64": {...}},
  "microg": {"index": "https://microg.org/fdroid/repo/index-v1.json", "packages": ["com.google.android.gms", "com.android.vending"]},
  "fdroid": {"index": "https://f-droid.org/repo/index-v1.json", "packages": ["org.fdroid.fdroid"]}
}
```

## Changing a URL or hash after install

Edit nothing under `/usr`: write `~/.config/apklayer/downloads.json` (`$XDG_CONFIG_HOME/apklayer/downloads.json`) with only what changes. It is merged over the installed file per entry: `gapps.arm64` (url, sha256 and version together), `microg`, `fdroid` (index and packages together). Entries it lacks stay as installed. So a new LiteGapps build for arm64 is

```json
{"gapps": {"arm64": {"url": "https://.../LiteGapps-arm64-new.zip/download", "sha256": "..."}}}
```

The environment wins over both files: `APKLAYER_GAPPS_URL`, `APKLAYER_GAPPS_SHA256`, `APKLAYER_MICROG_INDEX`, `APKLAYER_FDROID_INDEX`.

A missing or invalid file stops the setup step with an error naming the file. Not in this file: the microG signature hashes in `ShadowHostPackageManager` (they identify microG's certificate, not a download) and the Slint pin of `manager/fetch-slint.sh` (build time).
