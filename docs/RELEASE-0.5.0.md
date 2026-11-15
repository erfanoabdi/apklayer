# Release 0.5.0: ordered commands for the lead

apklayer 0.5.0 and Android runtime 0.5.0 (one number for both). General description of the pipeline: `docs/RELEASES.md`. Release notes: `CHANGELOG.md` "0.5.0".
The branch `release-0.5.0` holds the bump (meson.build `0.5.0`, nix/package.nix `0.5.0`, `tools/release/versions` `runtime=0.5.0`, Flatpak metainfo release entry, notes). Nothing is pushed or tagged.
Deb, click, RPM and Flatpak take their version from the tag (`${VERSION#v}`); `debian/changelog` is written by `build-deb.sh`, `click/manifest.json` has `@VERSION@`, `rpm/apklayer.spec` takes `--define apklayer_version`: nothing else to edit.

## What the runtime version 0.5.0 means
- Runtime 0.4.19 was never released (last release v0.4.18 shipped runtime 0.4.17, prebuilts `6e2ee336`, tags `prebuilts-v0.4.17/18`). 0.5.0 is a new runtime for new prebuilts (`prebuilts` tip `34711772`, equal on origin); the guard (`tools/release/check-runtime.py`) passes (checked locally against the real `releases.json` and the real tag list).
- A user on 0.4.18 gets: the new deb/click/RPM; `apklayer setup --update` (the manager runs it) downloads the bundle into `~/.local/share/apklayer/runtime/0.5.0` (size check first), moves `runtime/current` to it and removes older ones; the first CLI use then runs `setup-runtime.sh` once (links the package half, JNI shim, instruments the framework, ~10 s to a few minutes). System apps whose sha256 changed are replaced with their data kept (`system-apps.sha256`).
- Converter ABI is 11 (0.4.18 shipped 3) and data format is 1: apps converted by 0.4.18 are stale; each one is converted again on its next launch (message "converted by an older converter ABI", data stays); `apklayer reconvert --all-stale` does all at once. Nothing else migrates.
- The bundle's `runtime.json` `main_commit` comes from the prebuilts `MANIFEST` (still `62ae0b4d`, 2026-11-04): cosmetic, not read by anything that decides.

## Before tagging
1. The PGR fix is merged. Merge `release-0.5.0` into `main` (it touches only version places, CHANGELOG and this file, so it merges clean), push `main`.
   If the fix also changes the prebuilts branch (libberberis etc.), push that first: the tag below must be made from the final tip. Check `git rev-parse prebuilts origin/prebuilts` are equal. (Currently `34711772`, "x86_64 libberberis_arm64 binary_translation 0043 ... GL/EGL proxies".)
2. Optional dry run on GitHub (builds everything, releases nothing, prints the releases.json entry): Actions -> release -> Run workflow, version `0.5.0`, prebuilts `prebuilts`. Needs the workflow on `main`.
   Do NOT use an rc tag (`v0.5.0-rc`): the tag trigger is `v*`, the deb versions become `0.5.0-rc-1~noble1`, and `releases-json.py` fails after the builds (`int('0-rc')` in its version sort; seen in the local dry run). Tags are `vX.Y.Z` only.
3. Make sure the worktree is current and clean: `git status`, `git log -1 main`.

## Tag and push (in this order)
```sh
cd ~/GitHub/apklayer
git rev-parse prebuilts origin/prebuilts            # the same commit; this is what the release ships
git tag prebuilts-v0.5.0 "$(git rev-parse prebuilts)"   # lightweight tag, as prebuilts-v0.4.17/18
git tag v0.5.0 main                                  # the merged main (after the PGR fix)
git push origin prebuilts-v0.5.0                     # first: the workflow fetches this ref
git push origin v0.5.0                               # starts the release workflow
```
The workflow's `PREBUILTS_REF` is `prebuilts-v0.5.0` (a tag of that name is required), its guard fetches it and `pages:releases.json`, and compares.

## What the workflow does (.github/workflows/release.yml, trigger tag `v*`)
1. `guard`: `check-runtime.py` runtime 0.5.0 against releases.json (no 0.5.0 there: passes). Fails before any build otherwise.
2. In parallel: `deb` (noble, trixie x arm64, amd64 experimental; `build-deb.sh`), `bundle` (`runtime-bundle.sh` for arm64 and x86_64), `sailfish` (aarch64 RPMs), `flatpak` (aarch64, x86_64), then `click` (arm64, needs the noble arm64 deb). Sailfish, Flatpak, click and amd64 debs may fail without stopping the release.
3. `release`: renames `~` to `.` in file names, writes `SHA256SUMS`, `releases-json.py` adds 0.5.0 (converter_abi 11, data_format 1, runtime 0.5.0 with prebuilts `34711772...`) to the `pages` checkout, **creates the GitHub Release `v0.5.0` at once (it is not a draft)** and pushes the `releases.json` commit to `pages`. From that moment the manager's updater offers 0.5.0.

## Verify (the release is live; there is no draft)
Actions -> release run: all jobs green (yellow allowed only for the optional ones). Then:
```sh
gh release view v0.5.0 -R erfanoabdi/apklayer --json assets --jq '.assets[].name'
```
expected: `apklayer_0.5.0-1.noble1_{arm64,amd64}.deb`, `apklayer_0.5.0-1.trixie1_*.deb`, the same for `apklayer-dev_`, `apklayer-runtime-{arm64,x86_64}-0.5.0.tar.zst` (+ `.sha256`), `apklayer.erfanoabdi_0.5.0_arm64.click`, `apklayer-0.5.0-{aarch64,x86_64}.flatpak`, `apklayer-0.5.0-1.aarch64.rpm` and the other RPMs, `SHA256SUMS`.
```sh
curl -s https://erfanoabdi.github.io/apklayer/releases.json | python3 -c 'import json,sys;d=json.load(sys.stdin);e=d["releases"][0];print(d["latest"],e["converter_abi"],e["data_format"],e["runtime"]["version"],e["runtime"]["prebuilts"][:8],list(e["debs"]))'
# 0.5.0 11 1 0.5.0 34711772 ['noble', 'trixie']
```
(the Pages URL may take a minute; or `git show origin/pages:releases.json`). Download the arm64 noble deb and the arm64 bundle on a test host over the LAN rule's allowed path (the workflow's artifacts are on GitHub; check `sha256sum -c SHA256SUMS`), then `sudo apt install ./apklayer_0.5.0-1.noble1_arm64.deb`, `apklayer --version` (expect `0.5.0` and `converterAbi 11`), `apklayer setup`, `apklayer setup --check` ("latest 0.5.0 0.5.0"), `ls ~/.local/share/apklayer/runtime` (`0.5.0` and `current`). From 0.4.18: the manager's update banner and `setup --update`.

## Rollback
Nothing is deleted from users' machines by these steps; a bad release is pulled by removing the entry, the release and the tags:
```sh
gh release delete v0.5.0 -R erfanoabdi/apklayer --cleanup-tag --yes     # release and tag v0.5.0
git push origin :refs/tags/prebuilts-v0.5.0                              # only if the runtime must change: otherwise keep it
git tag -d v0.5.0 prebuilts-v0.5.0
# releases.json: drop the 0.5.0 entry on pages (latest returns to 0.4.18)
git fetch origin pages && git checkout -B pages-fix origin/pages        # in a separate checkout, not the dev tree
git revert --no-edit HEAD                                               # the commit "releases.json: 0.5.0"
git push origin pages-fix:pages
```
Fix and release again as 0.5.1 rather than re-using 0.5.0 when anyone may have downloaded the runtime (the guard refuses runtime 0.5.0 from other prebuilts once it is in releases.json; if 0.5.0 never appeared there, tags can be redone). A changed prebuilts branch always needs a new `runtime=` (0.5.1).
