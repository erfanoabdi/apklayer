#!/bin/sh
# usb-gadget.sh up|attach|detach|down: a USB device without hardware for the usb group: Linux's Gadget Zero (g_zero, 0525:a4a0) on usbip's virtual device
# controller (usbip-vudc), plugged into this machine's own virtual host controller (vhci-hcd) through usbipd on 127.0.0.1. Needs sudo -n and the
# distribution's usbip tools (Ubuntu: linux-tools-generic). `up` also installs, once, what a user would set up for a USB app (docs/INSTALL.md "USB devices"):
# a udev rule giving the desktop user the node (uaccess), and keeps the kernel's own usbtest driver off the gadget (an app gets only unclaimed devices).
set -eu
rule=/etc/udev/rules.d/70-apklayer-test-gadget-zero.rules
pidfile=/run/apklayer-test-usbipd.pid
port() { sudo -n usbip port 2>/dev/null | awk '/0525:a4a0/ {print p} /^Port/ {p=$2+0}' | head -1; }
case "$1" in
up)
    [ -e "$rule" ] || printf '%s\n' 'SUBSYSTEM=="usb", ENV{DEVTYPE}=="usb_device", ATTR{idVendor}=="0525", ATTR{idProduct}=="a4a0", TAG+="uaccess"' | sudo -n tee "$rule" >/dev/null
    [ -e /etc/modprobe.d/apklayer-test-usbtest.conf ] || echo 'blacklist usbtest' | sudo -n tee /etc/modprobe.d/apklayer-test-usbtest.conf >/dev/null
    sudo -n udevadm control --reload
    sudo -n modprobe -r usbtest 2>/dev/null || true
    sudo -n modprobe usbip-vudc; sudo -n modprobe vhci-hcd; sudo -n modprobe g_zero
    [ -s "$pidfile" ] && sudo -n kill -0 "$(cat $pidfile)" 2>/dev/null || sudo -n usbipd -D --device --pid "$pidfile"
    p=$(port); [ -z "$p" ] || sudo -n usbip detach -p "$p" >/dev/null;;
attach) sudo -n usbip attach -r 127.0.0.1 -d usbip-vudc.0;;
detach) p=$(port); [ -n "$p" ] && sudo -n usbip detach -p "$p" >/dev/null;;
down)
    p=$(port); [ -z "$p" ] || sudo -n usbip detach -p "$p" >/dev/null
    [ ! -s "$pidfile" ] || { sudo -n kill "$(cat $pidfile)" 2>/dev/null || true; sudo -n rm -f "$pidfile"; }
    sudo -n modprobe -r g_zero usbip-vudc 2>/dev/null || true;;
*) echo "usage: $0 up|attach|detach|down" >&2; exit 2;;
esac
