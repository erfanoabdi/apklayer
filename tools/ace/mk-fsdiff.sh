#!/bin/bash
# Writes ace-fsdiff.bt (next to this script) for this host's arch: tools/ace/mk-fsdiff.sh; use with EXTRA_BT=ace-fsdiff.bt pgr-timeline.sh
H=$(cd "$(dirname "$0")" && pwd)
or() { local s= i; for i in "$@"; do s="${s:+$s || }args->id == $i"; done; echo "$s"; }
eqs() { local s= i; for i in "$@"; do s="${s:+$s || @nr[tid] == }$i"; done; echo "$s"; }
case "$(uname -m)" in
 aarch64) I1="56 437 48 439 79 291 78"; I0="43 8 9"; GD=61; ST="79"; OP="56 437"; MO=16;;
 *)       I1="257 437 269 439 262 332 267"; I0="2 21 4 6 89 137 191 192"; GD=217; ST="4 6 262"; OP="2 257 437"; MO=24;;
esac
sed -e "s/@IDS1@/$(or $I1)/" -e "s/@IDS0@/$(or $I0)/" -e "s/@STATIDS@/$(eqs $ST)/" -e "s/@MODEOFF@/$MO/" "$H/ace-fsdiff.bt.in" > "$H/ace-fsdiff.bt"
