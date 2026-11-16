# Review text for PR #4 (phozzy, nix-flake-fixes): for the lead to post

Not posted. Thank you first: the follow-up to #3 is careful and the comments say why, which is rare. Your commits are merged as they are (merge, not
squash). Marks: **must-fix** (already fixed by us in the merge, listed so you know), **nit** (optional).

## cli/gapps.cpp, cli/gapps.h, cli/setup.cpp (allowlists read where they are kept)
- Idea is right: a package build serves android-root read-only, so copying there cannot work. Dropping `copyConfig`, `applyGappsConfig` and the call in
  setup.cpp is a good deletion. The merge with the new `downloads.json` change (it touched the same two files) was clean and nothing semantic clashed.
- **must-fix (we fixed it)**: `removeGapps` no longer deleted the XMLs an *earlier* build copied into the runtime's `android-root/system/etc`. A user who
  upgrades with Google's apps installed and then runs `setup --microg` kept Google's privileged allowlist, so microG's GmsCore (same package name)
  stayed a privileged system app. We kept the old recursive removal as `removeCopiedConfig` ("migration", it only unlinks, a read-only runtime has none).
  It can go in a later release.
- `gappsEtcDir` returns "" unless the Services Framework is installed: good, a removal takes effect at once and the stale `google-apps/etc` of a half
  removal is ignored.
- setup.cpp message "Installed; restart the instance to use them": fine and honest (the hub reads the lists once per JVM).

## cli/env.cpp, cli/spare.cpp, runtime/.../HostSystemConfig.java
- env.cpp: setting and **erasing** `APKLAYER_EXTRA_ETC` is correct (nativeEnv starts from the caller's environment); thank you for the comment.
- spare.cpp: comment only; the claim holds (the variable is in no ignored set, so the spare key differs and a stale spare is not reused).
- HostSystemConfig: reading `permissions`, `sysconfig` and `default-permissions` from the extra root mirrors the build's own three reads. nit: the
  variable is trusted as a path; fine here (the user's own environment), but it is worth one sentence in docs/ENV-VARS.md if you want it public. It is
  internal, so we left it out.

## cli/sandbox.cpp
- The read-only bind of the directory after the tmpfs is the right place (after `appsDir`). nit: in our run the new group `files-gapps-etc` also
  passes with this bind removed (sandbox active): the provider's permission check is the hub's, outside the sandbox. The bind only matters for the app's
  own `HostSystemConfig` (AppEnvironment: features and default grants), which `hello-files` does not observe. The code is still right; the test does
  not prove the bind. We reworded the group comment to say so.

## cli/manage.cpp
- `permissions` now looks in both roots, so the CLI and the JVM agree. Good. nit: the single-quote variant is harmless, but the JVM parses XML, so
  `privapp-permissions package = "x"` (spaces) is still missed by both substring searches; same as before.

## cli/layout.cpp, cli/layout.h, nix/package.nix (APKLAYER_CLI)
- Real bug found in the wrapper case, and a minimal fix (`--set-default`, falls back to `/proc/self/exe`). No remarks.

## docs/INSTALL.md, tools/release/gapps.sh
- Match the code. Accepted unchanged.

## tests/groups.toml (files-gapps-etc)
- **must-fix (we fixed it)**: the setup creates `apps/com.google.android.gsf` and the XML in the real data home and nothing removed them, so the test
  host would then count as having Google's apps installed (`gappsInstalled`, the manager's tile, `setup --microg` would uninstall "Google's" apps).
  The `run` now removes what the test made (empty-directory `rmdir`, so a real install is never touched).
- nit: the setup also removed the shared `apklayer-test-hello-files.xml` of the `files` group from `$APKLAYER_NATIVE`; the two groups must not run at
  the same time on one native tree (each instance of tests/run has its own, so it is fine there).

## nix/nixos-module.nix, flake.nix (subordinate ids)
- Disjoint 65536-id blocks per user: correct and important (the old shared range made two users' sandboxed apps the same host uids). Setting
  explicit ranges also switches off NixOS's `autoSubUidGidRange` for those users (it is only defaulted on when both lists are empty), so there is no
  clash with the automatic allocation of other normal users beyond the one stated below.
- nit: the base 100000 is fixed. A user with a hand-written range or an auto range that NixOS allocates from the same pool can overlap if both are
  used on one machine; a sentence in the option description ("do not mix with manual subUidRanges") would be enough.
- nit: a name listed twice in `sandbox.users` uses an index twice (the second is dropped by `listToAttrs`, the range of the second slot stays
  unused); harmless, `lib.unique` would tidy it.
- The assertion with the message for undeclared users is good (the original error is far from the cause). `or {}` on a missing user is fine.
- flake.nix: the `nixos-module-sandbox` check also asserts disjointness at evaluation. Nice. Not run by us (no Nix on our hosts): evaluated by reading only.

## nix/system-apps.nix
- `apklayer install --system` instead of webview.sh: right, the script reaches the CLI by a path no meson install writes. Accepted.
