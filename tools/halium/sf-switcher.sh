#!/bin/sh
# sf-switcher.sh DIR: Lipstick's switcher with two apps (Termux, hello.input), as root on the Sailfish phone, unlocked, DIR holds touch.py, sf-lshot.sh
# and the apps installed for defaultuser's instance. Pictures DIR/sw-*.png: A (second app up), B (after the home gesture: covers), C (Termux's cover tapped).
D=${1:-.}
U="su -l defaultuser -c"
cd "$D"
$U "cd $D; apklayer stop >/dev/null 2>&1; apklayer run com.termux > sw-termux.log 2>&1 &"
sleep 20
$U "cd $D; APKLAYER_JOPTS=-Dapklayer.runms=120000 apklayer run hello.input > sw-input.log 2>&1 &"
sleep 15
sh sf-lshot.sh sw-A.png
python3 touch.py swipe:1075,1200,200,1200,250
sleep 2
sh sf-lshot.sh sw-B.png
