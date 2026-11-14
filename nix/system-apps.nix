# Installs Android's own content providers and the WebView, which apps expect to be there
# (docs/INSTALL.md step 6). They are ordinary APKs in the prebuilts, so nothing is downloaded —
# but no meson.build installs them, so somebody has to, once per user.
#
# Installed apps are shared by every instance; the stamp keeps this to one run per apklayer build.
{
  lib,
  writeShellApplication,
  apklayer,
}: let
  inherit (apklayer.passthru) prebuilts;

  systemApps = prebuilts + "/${prebuilts.passthru.cpu}/system-apps";

  providers = [
    "SettingsProvider"
    "MediaProvider"
    "ContactsProvider"
    "CalendarProvider"
    "DownloadProvider"
    "UserDictionaryProvider"
    "ExternalStorageProvider"
    "TelephonyProvider"
    "DocumentsUI"
  ];
in
  writeShellApplication {
    name = "apklayer-install-system-apps";

    runtimeInputs = [apklayer];

    text = ''
      stamp="''${XDG_DATA_HOME:-$HOME/.local/share}/apklayer/system-apps.stamp"
      want=${lib.escapeShellArg "${apklayer}"}

      if [ "$(cat "$stamp" 2>/dev/null || true)" = "$want" ]; then
          exit 0
      fi

      for app in ${lib.escapeShellArgs providers}; do
          apk="${systemApps}/$app.apk"
          if [ -e "$apk" ]; then
              apklayer install "$apk"
          else
              echo "apklayer: no $app in the prebuilts, skipped" >&2
          fi
      done

      # the WebView provider is installed as a system app, not with `apklayer install`
      if [ -e "${systemApps}/webview.apk" ]; then
          sh ${apklayer}/share/apklayer/tools/host-setup/webview.sh "${systemApps}/webview.apk"
      fi

      mkdir -p "$(dirname "$stamp")"
      printf %s "$want" > "$stamp"
    '';
  }
