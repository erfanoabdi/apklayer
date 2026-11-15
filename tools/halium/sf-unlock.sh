#!/bin/sh
# sf-unlock.sh DIR: wakes the Sailfish phone (Volla Quintus, 1080x2400) and types the lock code (stdin's first line) on Lipstick's keypad with touch.py (DIR holds it);
# run as root. The code is never an argument.
D=${1:-.}
mcetool --unblank-screen
dbus-send --system --type=method_call --dest=com.nokia.mce /com/nokia/mce/request com.nokia.mce.request.req_tklock_mode_change string:unlocked
read -r code
acts=""
for i in $(seq 1 ${#code}); do
  d=$(echo "$code" | cut -c$i)
  case $d in 1) p=324,1684;; 2) p=540,1684;; 3) p=756,1684;; 4) p=324,1850;; 5) p=540,1850;; 6) p=756,1850;; 7) p=324,2016;; 8) p=540,2016;; 9) p=756,2016;; 0) p=540,2180;; esac
  acts="$acts tap:$p"
done
python3 "$D/touch.py" $acts
