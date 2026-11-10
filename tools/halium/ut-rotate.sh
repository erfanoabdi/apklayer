#!/bin/bash
# ut-rotate.sh DIR PKG|- STEP...: turns the Ubuntu Touch phone without touching it (docs/HALIUM.md "Rotation on Ubuntu Touch"). The sudo password (also
# the greeter's PIN) is stdin's first line; DIR holds env.sh (the instance, $A) and fake-accel.py. A uinput accelerometer (fake-accel.py) feeds a
# sensorfwd whose accelerometer adaptor is the evdev one (the conf.d copied and bind-mounted in a private mount namespace), Lomiri's rotation lock
# is off. PKG runs with WAYLAND_DEBUG on its shell (DIR's shell binary must be a wrapper that sets it). A STEP is NAME:x,y,z (gravity in mG;
# 0,1000,0 portrait, 1000,0,0 and -1000,0,0 landscape): set, then the app's picture (shots/NAME-app.png). Afterwards: the app stopped,
# Lomiri portrait again, the lock as it was, the real sensorfwd, the screen off again if it was. Never a Lomiri screenshot. Needs the phone's
# ~/apklayer-dev/uikeys.py and uitap.py (uinput keys and taps).
D=$1; shift; . $D/env.sh; cd $D; mkdir -p shots
read -r PW
WOKE=0
if [ "$(cat /sys/class/leds/lcd-backlight/brightness)" -eq 0 ]; then  # screen off and locked: wake, PIN on the greeter
  WOKE=1; python3 ~/apklayer-dev/uikeys.py POWER; sleep 1.5
  python3 ~/apklayer-dev/uitap.py $(echo "$PW" | python3 -c 'import sys; k={"1":(96,1088),"2":(271,1088),"3":(447,1088),"4":(96,1219),"5":(271,1219),"6":(447,1219),"7":(96,1346),"8":(271,1346),"9":(447,1346),"0":(271,1472)}; print(*[v for d in sys.stdin.read().strip() for v in k[d]])'); sleep 1.5
fi
S() { echo "$PW" | sudo -S -p '' "$@"; }
PKG=$1; shift
LOCK=$(gsettings get com.lomiri.touch.system rotation-lock)
rm -rf $D/sfwconf; mkdir -p $D/sfwconf
for f in /etc/sensorfw/sensord.conf.d/*.conf; do
  sed 's/^accelerometeradaptor *=.*/accelerometeradaptor = accelerometeradaptor/' "$f" > $D/sfwconf/$(basename "$f")
done
printf '[accelerometer]\ninput_match = apklayer-accel\n' > $D/sfwconf/99-apklayer-accel.conf
coproc ACC { exec python3 -u $D/fake-accel.py; }
read -r line <&${ACC[0]}; echo "$line"
echo "0 1000 0" >&${ACC[1]}; read -r line <&${ACC[0]}
S systemctl stop sensorfwd
S unshare -m sh -c "mount --bind $D/sfwconf /etc/sensorfw/sensord.conf.d && exec /usr/sbin/sensorfwd --log-level=info" > $D/sfw.log 2>&1 &
SFW=$!
sleep 2
gsettings set com.lomiri.touch.system rotation-lock false
if [ "$PKG" != - ]; then
  WAYLAND_DEBUG=1 $A run $PKG > $D/run.log 2>&1 &
  sleep 12
fi
for step in "$@"; do
  name=${step%%:*}; g=${step#*:}
  echo "${g//,/ }" >&${ACC[1]}; read -r line <&${ACC[0]}; echo "$name: $line"
  sleep 4
  echo "  sensorfw orientation: $(busctl --system call com.nokia.SensorService /SensorManager/orientationsensor local.OrientationSensor orientation 2>&1)"
  [ "$PKG" != - ] && $A screenshot $PKG shots/$name-app.png > /dev/null 2>&1
done
[ "$PKG" != - ] && $A stop
echo "0 1000 0" >&${ACC[1]}; read -r line <&${ACC[0]}; sleep 3  # portrait again before the real sensor (flat: "unknown") takes over
gsettings set com.lomiri.touch.system rotation-lock "$LOCK"
S pkill -f "[/]usr/sbin/sensorfwd --log-level=info"; wait $SFW
exec {ACC[1]}>&-
wait $ACC_PID
S systemctl start sensorfwd
[ $WOKE = 1 ] && python3 ~/apklayer-dev/uikeys.py POWER
echo "sensorfwd $(systemctl is-active sensorfwd), rotation lock $(gsettings get com.lomiri.touch.system rotation-lock), backlight $(cat /sys/class/leds/lcd-backlight/brightness)"
