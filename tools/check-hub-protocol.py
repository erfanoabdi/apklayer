#!/usr/bin/env python3
"""Fail when two HubProtocol transaction codes share a number (merges of parallel branches did this twice).
ENDPOINT_* codes are a second namespace (the endpoint socket), the rest share the hub's."""
import re, sys, pathlib
src = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else
                   pathlib.Path(__file__).resolve().parents[1] / "runtime/host/java/apklayer/host/HubProtocol.java").read_text()
src = re.sub(r"//[^\n]*", "", src)
seen = {}
bad = []
for stmt in re.findall(r"static final int ([^;]*);", src):
    for name, num in re.findall(r"([A-Z][A-Z0-9_]*)\s*=\s*(\d+)", stmt):
        ns = "endpoint" if name.startswith("ENDPOINT_") else "hub"
        key = (ns, int(num))
        if key in seen:
            bad.append(f"{name} and {seen[key]} are both {num} ({ns} codes)")
        seen[key] = name
if bad:
    print("HubProtocol: duplicate codes:\n  " + "\n  ".join(bad)); sys.exit(1)
print(f"HubProtocol: {len(seen)} codes, no duplicates")
