#!/usr/bin/env python3
"""vpn-link.py absent|present PREFIX [SECONDS]: waits until no / some interface named PREFIX* exists; exit 1 on the deadline. The host-wide VPN removes its TUN
asynchronously (NetworkManager deletes the device after the connection). Woken by `ip monitor link`; the kernel announces a deletion before the interface's sysfs
entry is gone, so the state is looked at again 0.2 s after an event too."""
import os, select, subprocess, sys, time
mode, prefix, seconds = sys.argv[1], sys.argv[2], float(sys.argv[3]) if len(sys.argv) > 3 else 15
here = lambda: any(n.startswith(prefix) for n in os.listdir("/sys/class/net"))
monitor = subprocess.Popen(["ip", "-o", "monitor", "link"], stdout=subprocess.PIPE, text=True)  # subscribed before the first look
end = time.monotonic() + seconds
woken = False
try:
    while here() != (mode == "present"):
        left = end - time.monotonic()
        if left <= 0: sys.exit(1)
        ready = select.select([monitor.stdout], [], [], min(left, 0.2) if woken else left)[0]
        woken = bool(ready) or woken
        if ready: monitor.stdout.readline()
        elif woken: woken = False  # the recheck after the event came up empty: wait for the next one
finally:
    monitor.kill()
