#!/usr/bin/env python3
"""vpn-link.py absent|present PREFIX [SECONDS]: waits (on `ip monitor link`, no polling) until no / some interface named PREFIX* exists; exit 1 on the deadline. The host-wide VPN removes its TUN asynchronously (NetworkManager deletes the device after the connection)."""
import os, select, subprocess, sys, time
mode, prefix, seconds = sys.argv[1], sys.argv[2], float(sys.argv[3]) if len(sys.argv) > 3 else 15
here = lambda: any(n.startswith(prefix) for n in os.listdir("/sys/class/net"))
monitor = subprocess.Popen(["ip", "-o", "monitor", "link"], stdout=subprocess.PIPE, text=True)  # subscribed before the first look
end = time.monotonic() + seconds
try:
    while here() != (mode == "present"):
        left = end - time.monotonic()
        if left <= 0 or not select.select([monitor.stdout], [], [], left)[0]: sys.exit(1)
        monitor.stdout.readline()
finally:
    monitor.kill()
