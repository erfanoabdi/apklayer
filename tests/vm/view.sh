#!/bin/bash
# Opening a file as DocumentsUI does: a privileged sender starts ACTION_VIEW with a document uri and FLAG_GRANT_READ_URI_PERMISSION; the viewer, which holds no permission, reads it.
# On the VM, in the group's instance (tests/run view): APKLAYER_INSTANCE, APKLY_STATE (its logs) and the test apps installed by the runner.
: "${APKLY_STATE:=${XDG_STATE_HOME:-$HOME/.local/state}/apklayer/${APKLAYER_INSTANCE:-default}}"
out=$APKLY_STATE/view.out
bin/apklayer stop >/dev/null 2>&1
doc=${PWD#$HOME/}/CLAUDE.md  # a file of the repo copy, as a document of the home root (linux-home: is $HOME)
APKLAYER_JOPTS="-Dapklayer.runms=40000 -Dhello.view=linux-home:$doc" bin/apklayer run hello.files > $out 2>&1 || true
grep -a "\[files\] DONE\|may not grant" $out
grep -a -h "\[reader\]" $out $APKLY_STATE/app-hello.reader.log
bin/apklayer stop >/dev/null 2>&1
grep -a -h -q "\[reader\] read:" $out $APKLY_STATE/app-hello.reader.log
