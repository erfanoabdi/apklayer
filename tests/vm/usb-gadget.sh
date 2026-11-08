#!/bin/sh
# usb-gadget.sh up|attach|detach|down: a USB device without hardware for the usb group: Linux's Gadget Zero (g_zero, 0525:a4a0) on usbip's virtual device
# controller (usbip-vudc), plugged into this machine's own virtual host controller (vhci-hcd) through usbipd on 127.0.0.1. Needs sudo -n and the
# distribution's usbip tools (Ubuntu: linux-tools-generic). `up` also installs, once, what a user would set up for a USB app (docs/INSTALL.md "USB devices"):
# a udev rule giving the desktop user the node (uaccess), and keeps the kernel's own usbtest driver off the gadget (an app gets only unclaimed devices).
# USB_GADGET=serial: Linux's serial gadget instead (g_serial, CDC ACM 0525:a4a7), which the host's cdc_acm driver takes as a serial adapter's (/dev/ttyACM*;
# the app detaches it); the gadget's own end is /dev/ttyGS0. SUDO: the sudo command (default `sudo -n`).
set -eu
SUDO=${SUDO:-sudo -n}
case "${USB_GADGET:-zero}" in
zero) module=g_zero product=a4a0;;
serial) module=g_serial product=a4a7;;
*) echo "usb-gadget.sh: USB_GADGET is zero or serial" >&2; exit 2;;
esac
rule=/etc/udev/rules.d/70-apklayer-test-gadget-${USB_GADGET:-zero}.rules
pidfile=/run/apklayer-test-usbipd.pid
port() { $SUDO usbip port 2>/dev/null | awk -v id="0525:$product" '$0 ~ id {print p} /^Port/ {p=$2+0}' | head -1; }
case "$1" in
up)
    [ -e "$rule" ] || printf 'SUBSYSTEM=="usb", ENV{DEVTYPE}=="usb_device", ATTR{idVendor}=="0525", ATTR{idProduct}=="%s", TAG+="uaccess"\n' $product | $SUDO tee "$rule" >/dev/null
    [ $module != g_zero ] || [ -e /etc/modprobe.d/apklayer-test-usbtest.conf ] || echo 'blacklist usbtest' | $SUDO tee /etc/modprobe.d/apklayer-test-usbtest.conf >/dev/null
    $SUDO udevadm control --reload
    [ $module != g_zero ] || $SUDO modprobe -r usbtest 2>/dev/null || true
    $SUDO modprobe usbip-vudc; $SUDO modprobe vhci-hcd; $SUDO modprobe $module
    pgrep -f -- "[-]-device --pid $pidfile" >/dev/null || $SUDO usbipd -D --device --pid "$pidfile"
    p=$(port); [ -z "$p" ] || $SUDO usbip detach -p "$p" >/dev/null;;
attach) $SUDO usbip attach -r 127.0.0.1 -d usbip-vudc.0;;
detach) p=$(port); [ -n "$p" ] && $SUDO usbip detach -p "$p" >/dev/null;;
down)
    p=$(port); [ -z "$p" ] || $SUDO usbip detach -p "$p" >/dev/null
    $SUDO pkill -f -- "[-]-device --pid $pidfile" || true  # usbipd and the child it forked for the gadget's connection
    $SUDO rm -f "$pidfile"
    $SUDO modprobe -r $module usbip-vudc 2>/dev/null || true;;
*) echo "usage: $0 up|attach|detach|down" >&2; exit 2;;
esac
