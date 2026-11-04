#!/bin/bash
# accounts.sh: one account store per instance (the hub's account service). hello-system adds its account into a fresh store and makes it visible to
# hello-accounts, which (another app, its own JVM) lists it and gets a token from hello-system's authenticator; hello-accountshidden (a third app, not
# shown the account) lists none and gets no token; hello-system's next run finds the account and its visibility persisted;
# then removeAccount asks the authenticator (it refuses one account, allows another).
# On the VM, in the group's instance (tests/run accounts): APKLAYER_INSTANCE, APKLY_STATE (its logs); the apps installed by the runner. Each run ends itself (runms).
set -uo pipefail
: "${APKLY_STATE:=${XDG_STATE_HOME:-$HOME/.local/state}/apklayer/${APKLAYER_INSTANCE:-default}}"
id=${APKLAYER_INSTANCE:-default}; [ "$id" = default ] && id=0
bin/apklayer stop >/dev/null 2>&1  # first: a running hub would write its store again
rm -f "${XDG_DATA_HOME:-$HOME/.local/share}/apklayer/$id/accounts.json"
run() { APKLAYER_JOPTS="-Dapklayer.runms=15000 $2" bin/apklayer run $1 > $APKLY_STATE/accounts-$3.out 2>&1; bin/apklayer stop >/dev/null 2>&1; grep -a "^\[\(sys\|accounts\)\] \(PASS\|FAIL\)" $APKLY_STATE/accounts-$3.out; }
out=$(run hello.system -Dhello.sys=accounts add; run hello.accounts "" other; run hello.accountshidden "" hidden; run hello.system -Dhello.sys=accounts again; run hello.system -Dhello.sys=accountremoval removal)
echo "$out"
echo "$out" | grep -q "PASS accounts persisted=false" && echo "$out" | grep -q "^\[accounts\] PASS [0-9]" && echo "$out" | grep -q "^\[accounts\] PASS hidden" &&
    echo "$out" | grep -q "PASS accounts persisted=true" && echo "$out" | grep -q "PASS accountremoval"
