#!/usr/bin/env python3
"""check-runtime.py <releases.json> <source tree> <prebuilts commit> [<git ls-remote output of refs/tags/prebuilts-v*>]: fails when the
runtime version of tools/release/versions was already released from another prebuilts commit (docs/RELEASES.md). Entries from before
releases.json recorded runtime.prebuilts take the commit of their prebuilts-v<release> tag from the ls-remote output."""
import json, os, re, sys

path, tree, commit, *tags = sys.argv[1:]
runtime = next(line.split("=", 1)[1].strip() for line in open(os.path.join(tree, "tools/release/versions")) if line.startswith("runtime="))
tagged = {}
for line in open(tags[0]) if tags else ():
    if m := re.fullmatch(r"([0-9a-f]{40})\trefs/tags/prebuilts-v([0-9.]+)(\^\{\})?", line.strip()):
        if m[3] or m[2] not in tagged:  # an annotated tag's ^{} line is its commit
            tagged[m[2]] = m[1]
for r in json.load(open(path))["releases"]:
    old = r["runtime"].get("prebuilts") or tagged.get(r["version"])
    if r["runtime"]["version"] == runtime and old and old != commit:
        sys.exit(f"error: runtime {runtime} was released in {r['version']} from prebuilts {old}; this build's prebuilts are {commit}.\n"
                 f"Bump runtime= in tools/release/versions (a new runtime version for new prebuilts) and tag again.")
print(f"runtime {runtime} from prebuilts {commit}: ok")
