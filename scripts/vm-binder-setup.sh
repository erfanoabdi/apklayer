#!/bin/sh
# vm-binder-setup: kernel binder for the arm64 stack on the Ubuntu VM (PLAN P7.1; the userspace binder replaces this later).
# Run as root: echo PASSWORD | sudo -S sh scripts/vm-binder-setup.sh. Loads binder_linux, mounts binderfs and makes /dev/binder (and hwbinder, vndbinder) usable by every user. Idempotent.
set -e
modprobe binder_linux devices=binder,hwbinder,vndbinder 2>/dev/null || modprobe binder_linux
mkdir -p /dev/binderfs
mountpoint -q /dev/binderfs || mount -t binder binder /dev/binderfs
chmod 666 /dev/binderfs/binder /dev/binderfs/hwbinder /dev/binderfs/vndbinder
for n in binder hwbinder vndbinder; do ln -sf /dev/binderfs/$n /dev/$n; done
ls -la /dev/binder
