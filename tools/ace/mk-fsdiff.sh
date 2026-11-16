#!/bin/bash
# Writes ace-fsdiff.bt (next to this script) for this host's arch; use it with EXTRA_BT=ace-fsdiff.bt tools/ace/pgr-timeline.sh
H=$(cd "$(dirname "$0")" && pwd)
L="openat:filename openat2:filename faccessat:filename faccessat2:filename newfstatat:filename statx:filename readlinkat:pathname statfs:pathname getxattr:pathname lgetxattr:pathname mknodat:filename mkdirat:pathname unlinkat:pathname renameat:oldname renameat2:oldname symlinkat:newname linkat:newname utimensat:filename fchmodat:filename truncate:path"
[ "$(uname -m)" = aarch64 ] || L="$L open:filename access:filename newstat:filename newlstat:filename readlink:path mknod:filename mkdir:pathname unlink:pathname rename:oldname creat:pathname chmod:filename"
{ if [ "$(uname -m)" = aarch64 ]; then cat "$H/ace-fsdiff.bt.in"; else grep -v "^END {" "$H/ace-fsdiff.bt.in"; fi
  for e in $L; do n=${e%%:*}; a=${e##*:}
    echo "tracepoint:syscalls:sys_enter_$n / @g[pid] / { \$k = str(args->$a); @c[\"$n\", \$k]++; if (@c[\"$n\", \$k] <= 10) { @pr[tid] = 1; printf(\"P %lu %d $n %s\\t%s\\n\", nsecs, tid, \$k, comm); } }"
  done; [ "$(uname -m)" = aarch64 ] || cat "$H/ace-fsdiff-guest.bt.in"; } > "$H/ace-fsdiff.bt"
