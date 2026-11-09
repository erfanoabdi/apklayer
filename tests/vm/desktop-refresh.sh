#!/bin/bash
# An app's desktop entry whose Exec names a CLI that is gone (an /opt tarball replaced by the RPM on Sailfish OS) is written again by `apklayer list`
# with this CLI; an entry whose CLI exists is left alone. A data home of its own (XDG_DATA_HOME, XDG_CONFIG_HOME): no stack, no real app.
set -uo pipefail
w=$(mktemp -d "$HOME/.desktop-refresh.XXXXXX"); trap 'rm -rf "$w"' EXIT
export XDG_DATA_HOME=$w/data XDG_CONFIG_HOME=$w/config
# an existing submenu (merge file and its directory entry): writeMenu would otherwise rewrite every entry as for a first menu
mkdir -p $XDG_CONFIG_HOME/menus/applications-merged $XDG_DATA_HOME/applications $XDG_DATA_HOME/desktop-directories && touch $XDG_CONFIG_HOME/menus/applications-merged/apklayer-menu $XDG_DATA_HOME/desktop-directories/apklayer-directory
for p in gone.app kept.app; do
    mkdir -p $XDG_DATA_HOME/apklayer/apps/$p && printf 'package=%s\nlabel=%s\nuid=10001\n' $p $p > $XDG_DATA_HOME/apklayer/apps/$p/info.properties
done
printf '[Desktop Entry]\nExec=/opt/apklayer/lib/apklayer/cli/apklayer run gone.app\n' > $XDG_DATA_HOME/applications/apklayer-gone.app.desktop
printf '[Desktop Entry]\nExec=/bin/true run kept.app\n' > $XDG_DATA_HOME/applications/apklayer-kept.app.desktop
bin/apklayer list > /dev/null 2>&1
gone=$(grep '^Exec=' $XDG_DATA_HOME/applications/apklayer-gone.app.desktop) kept=$(grep '^Exec=' $XDG_DATA_HOME/applications/apklayer-kept.app.desktop)
cli=${gone#Exec=}; cli=${cli%% *}
if [ -x "$cli" ] && [ "$gone" = "Exec=$cli run gone.app" ] && [ "$kept" = "Exec=/bin/true run kept.app" ]; then echo "PASS stale entry rewritten: $gone"
else echo "FAIL gone: $gone kept: $kept"; exit 1; fi
