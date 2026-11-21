# apklayer Google apps (gapps branch)

The smallest set of Google's apps that `apklayer setup --gapps` installs, for arm64 and x86_64, taken from LiteGapps' official SDK 37 builds
(`sources`: URL and sha256 per CPU). `main`'s workflow `gapps` packs it into one zip per CPU and publishes it as the GitHub release
`gapps-<commit>`; `main`'s `data/downloads.json` points at those zips with their sha256.

| Path | What |
|---|---|
| `<cpu>/system/system_ext/priv-app/GoogleServicesFramework/` | Google Services Framework |
| `<cpu>/system/product/priv-app/GmsCore/` | Play services (its native libraries inside the APK: arm64-v8a + armeabi-v7a, x86_64 + x86) |
| `<cpu>/system/product/priv-app/Phonesky/` | Play Store (arm64-v8a / x86_64 libraries inside the APK) |
| `<cpu>/system/product/app/Google{Calendar,Contacts}SyncAdapter/` | the sync adapters |
| `<cpu>/system/{product,system_ext}/etc/{permissions,sysconfig,default-permissions}/` | Google's XMLs (privileged permissions, sysconfig, default grants), without the Pixel feature lists |
| `SHA256SUMS` | sha256 of every whole file |

The APKs are Google's signed files, unchanged: they are not stripped, which would break their signatures.

- `sh import.sh [zip dir]`: downloads the zips in `sources`, checks them and fills `<cpu>/` (the workflow runs it with `import`: it is about 700 MB).
- `sh join-parts.sh`: after cloning, joins the files split into 45 MB parts for GitHub's 100 MB file limit and checks `SHA256SUMS`.
- `sh pack.sh <out>`: `<out>/apklayer-gapps-{arm64,x86_64}.zip` (the `system/` tree; reproducible, so the sha256 is known before the release) and `<out>/SHA256SUMS`.

Licenses: Google's apps are Google's (proprietary, redistributed as LiteGapps publishes them); the scripts are AGPL-3.0-or-later like `main`.
