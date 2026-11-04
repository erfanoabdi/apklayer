#!/usr/bin/env python3
"""releases-json.py <releases.json> <version> <dist dir> <owner/repo> <versions file>: adds (or replaces) the release <version> in the pages
branch's releases.json from the files the release workflow uploads (docs/RELEASES.md has the schema), and makes it the latest."""
import datetime, hashlib, json, os, re, sys

path, version, dist, repo, versions_file = sys.argv[1:]
base = f"https://github.com/{repo}/releases/download/v{version}/"
versions = dict(line.strip().split("=", 1) for line in open(versions_file) if "=" in line and not line.startswith("#"))


def asset(name):
    with open(os.path.join(dist, name), "rb") as f:
        return {"url": base + name, "sha256": hashlib.file_digest(f, "sha256").hexdigest()}


debs, runtime = {}, {}
cpu_arch = {"arm64": "arm64", "x86_64": "amd64"}  # bundles are named by the prebuilts' CPU, keyed by Debian architecture like the debs
for name in sorted(os.listdir(dist)):
    if m := re.fullmatch(rf"apklayer_{re.escape(version)}-1[~.]([a-z]+)1_([a-z0-9]+)\.deb", name):  # the release job names them with . for ~
        debs.setdefault(m[1], {})[m[2]] = asset(name)
    elif m := re.fullmatch(rf"apklayer-runtime-([a-z0-9_]+)-{re.escape(versions['runtime'])}\.tar\.zst", name):
        runtime[cpu_arch[m[1]]] = asset(name)
assert debs and runtime, f"no debs or runtime bundle for {version} in {dist}"

entry = {"version": version, "date": datetime.date.today().isoformat(), "notes_url": f"https://github.com/{repo}/releases/tag/v{version}",
         "converter_abi": int(versions["converter_abi"]), "data_format": int(versions["data_format"]),
         "runtime": {"version": versions["runtime"], "assets": runtime}, "debs": debs}
data = json.load(open(path)) if os.path.exists(path) else {"latest": "", "releases": []}
data["releases"] = [entry] + [r for r in data["releases"] if r["version"] != version]
data["releases"].sort(key=lambda r: tuple(int(x) for x in r["version"].split(".")), reverse=True)
data["latest"] = data["releases"][0]["version"]
with open(path, "w") as f:
    json.dump(data, f, indent=2)
    f.write("\n")
print(json.dumps(entry, indent=2))
