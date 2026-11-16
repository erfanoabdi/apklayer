#!/bin/bash
# Writes ace-q4.bt next to this script for this host's arch: ace-q.bt plus every path syscall of the game process (first 4 calls per syscall+path) as `<ms> P <tid> <comm> <syscall> <path>`.
H=$(cd "$(dirname "$0")" && pwd)
L="openat:filename openat2:filename faccessat:filename faccessat2:filename newfstatat:filename statx:filename readlinkat:pathname mknodat:filename mkdirat:pathname unlinkat:pathname renameat:newname renameat2:newname symlinkat:newname linkat:newpath truncate:path"
[ "$(uname -m)" = aarch64 ] || L="$L open:filename access:filename newstat:filename newlstat:filename readlink:path mkdir:pathname unlink:pathname rename:newname creat:pathname"
{ grep -v "^END {" "$H/ace-q.bt" | grep -v "^BEGIN"
  for e in $L; do n=${e%%:*}; a=${e##*:}
    echo "tracepoint:syscalls:sys_enter_$n / @g[pid] / { \$k = str(args->$a); @pc[\"$n\", \$k]++; if (@pc[\"$n\", \$k] <= 4) { printf(\"%lu P %d %s $n %s\\n\", (nsecs - @t0) / 1000000, tid, comm, \$k); } }"
  done; echo 'END { clear(@g); clear(@t0); clear(@rf); clear(@dns); clear(@pc); }'; echo 'BEGIN { printf("READY\n"); }'; } > "$H/ace-q4.bt"
