#!/bin/sh
# sf-lshot.sh OUT.png: Lipstick's own screenshot of the Sailfish phone (docs/HALIUM.md), run as root: saveScreenshot is for the `privileged` group only,
# and writes below ~/Pictures. The picture is moved to OUT.png.
case "$1" in /*) out=$1;; *) out=$PWD/$1;; esac
rm -f /home/defaultuser/Pictures/sflshot.png
python3 - <<'E'
import os, subprocess
gid = int(subprocess.check_output(['getent', 'group', 'privileged']).decode().split(':')[2])
os.setgroups([100000, gid]); os.setgid(gid); os.setuid(100000)
os.environ['DBUS_SESSION_BUS_ADDRESS'] = 'unix:path=/run/user/100000/dbus/user_bus_socket'
subprocess.call(['dbus-send', '--session', '--print-reply', '--dest=org.nemomobile.lipstick', '/org/nemomobile/lipstick/screenshot',
                 'org.nemomobile.lipstick.saveScreenshot', 'string:/home/defaultuser/Pictures/sflshot.png'], stdout=subprocess.DEVNULL)
E
mv /home/defaultuser/Pictures/sflshot.png "$out"
