#!/bin/sh
# relink-instance-data.sh <instance>...: print the commands that point <data>/<n>/data/user/0/<pkg> and user_de/0/<pkg> at the instance's own
# app-data/<pkg>/app and /device (links copied from another instance point into it). APPLY=1 runs them; no data is touched, only links.
dh=${XDG_DATA_HOME:-$HOME/.local/share}/apklayer
for n; do
    for l in "$dh/$n"/data/user/0/* "$dh/$n"/data/user_de/0/*; do
        [ -L "$l" ] || continue
        case $l in */user_de/*) sub=device ;; *) sub=app ;; esac
        want=$dh/$n/app-data/$(basename "$l")/$sub
        [ "$(readlink "$l")" = "$want" ] && continue
        echo "ln -sfn '$want' '$l.new' && mv -T '$l.new' '$l'"
        [ -z "$APPLY" ] || { ln -sfn "$want" "$l.new" && mv -T "$l.new" "$l"; }
    done
done
