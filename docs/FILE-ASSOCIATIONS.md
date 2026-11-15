# File associations (the desktop opens files with Android apps)

Two directions, both through the desktop's own mechanisms (freedesktop shared-mime-info and desktop entries): no portal, no root.

## 1. Android packages open in the manager

`data/apklayer-manager.desktop` has `MimeType=` for `.apk` (`application/vnd.android.package-archive`, known to shared-mime-info) and for `.apks`,
`.xapk`, `.apkm`, which `data/apklayer-mime.xml` defines (`application/x-android-apks|xapk|apkm`, subclasses of zip), and `Exec=apklayer-manager %F`.
Opening one in GNOME Files, Dolphin or `xdg-open` starts the manager (one process for all the files a file manager selects) with its install
dialog filled in with the first file; Install converts and installs (`apklayer install`, which takes the split formats), the busy overlay is the progress,
and the dialog of the new app offers Open. A second selected file follows the first one's install.

Packaging: meson installs the entry and `mime/packages/apklayer.xml`; the deb and rpm scripts run `update-mime-database` and `update-desktop-database`
(dpkg's triggers do it too); the Flatpak exports the entry and the mime package under its app id; `manager/install-desktop.sh` does the same in
`~/.local/share` for a source tree. The CLI's own `apklayer-open-apk.desktop` (`apklayer run %f`, installs and runs without a dialog) is the fallback only
where no manager entry is installed. Click and Sailfish: the click's entry drops `MimeType=`; the rpm ships the entry with it (untested on a phone).

## 2. Files an app declares open with it

At install the converter reads the manifest (`ManifestFilters.fileTypes`): every `VIEW` or `EDIT` intent filter with `CATEGORY_DEFAULT` and file-like data
(no scheme, or `file`/`content`; an `https`-only filter is a link, not a file) gives `open.N=<mimeTypes>;<extensions>` in `info.properties`; the
extensions come from `pathPattern`/`pathSuffix` ending in a literal `.ext` (`.*\\.ovpn`). `SEND` is a share target, not an open.

`apklayer-<package>.desktop` (cli/desktop.cpp, `cli/filetypes.cpp`) then gets:

- `MimeType=` the declared types. A bare `*/*` is dropped (it would claim every file); `image/*`, `video/*`, `audio/*`, `text/*` become the common
  concrete types of the family (desktops do not match wildcards).
- for an extension, the system's type for `*.ext` (globs2). With none, the manifest's concrete type when the system does not know it, else
  `application/x-apklayer-<ext>`: a shared-mime-info package `~/.local/share/mime/packages/apklayer-<package>.xml` (glob `*.ext`, the app's label as
  comment) and `update-mime-database`. In the Flatpak the package is written only where the host's `mime` folder is writable, and the host's database
  is not updated (no tool in the runtime): `update-mime-database ~/.local/share/mime` on the host; through the launcher portal there is no package.
- `Exec=... open <package> %f` instead of `run <package>` (a launch from the menu is `open <package>` without a file, the same as `run`).

Rewritten with every `writeDesktop` (install, update, `launcher show|hide`, a stale entry's refresh); uninstall removes the entry and the package and
updates the database. Several apps for one type: the desktop's Open With picks (apklayer sets no default).

## 3. `apklayer open <package> [file]`

The file is copied into the app's own cache (`<cache>/opened/<token>/data` with its `type`; `xdg-mime query filetype` gives the type, a day of old
ones is pruned) and the app starts (or is told, if it runs: `onNewIntent`, the relaunch path of `run`) with an `ACTION_VIEW` whose data is
`content://apklayer.opened/<token>/<name>`, that MIME type and `FLAG_GRANT_READ_URI_PERMISSION`, through the same intent file as other cross-app
launches (`APKLAYER_INTENT_FILE`). `HostOpenedFiles` (registered in every app JVM) serves exactly that Uri: read only, `OpenableColumns`
(`_display_name`, `_size`), `getType`. The app never sees the host path or the folder.

Limits: not under the per-app sandbox yet (the app's data is its uid's; `open` says so); one file per launch (a file manager that selects several
runs the handler once per file, `%f`, and each reaches the running app in `onNewIntent`); the copy is whole (a very large file takes its time).

## Tests

`tests/run open-files` (`tests/vm/open-files.py`, test app `tests/apps/hello-openfile` declaring OpenVPN for Android's filters): the entry and mime package,
`xdg-mime`/`gio mime`, `gio open sample.ovpn` (Uri, type, display name, size, content in the app), a second file in `onNewIntent`, removal on uninstall; with
`~/corpus-openvpn.apk` the real OpenVPN entry; the manager's entry, the four types, and the manager's install dialog driven through its scripted input.
Not covered: OpenVPN receiving a profile, a real file manager's double click, KDE Dolphin, the Flatpak and Sailfish/Ubuntu Touch variants.
