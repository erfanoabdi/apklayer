# A joined checkout of the prebuilts branch: Android's libraries, the android root, the system apps
# and the Robolectric jars that the Android half of the runtime is built from (docs/INSTALL.md).
#
# Only the architecture being built is fetched; the branch carries both and is 1.7 GB whole.
{
  lib,
  stdenv,
  fetchgit,
  runCommand,
}: let
  # the prebuilts branch of erfanoabdi/apklayer, matching tools/release/versions (runtime=)
  rev = "b3b9718a99a04959c8547327e04fc69de7f0d876";

  cpus = {
    aarch64-linux = "arm64";
    x86_64-linux = "x86_64";
  };

  cpu =
    cpus.${stdenv.hostPlatform.system}
    or (throw "apklayer has no prebuilts for ${stdenv.hostPlatform.system}");

  # one per architecture: the sparse checkout differs, so the hash does too
  hashes = {
    arm64 = "sha256-VjrWv/q4zZyDSiT7msGZswHdfooYi58olw3ZTtqPyJQ=";
    x86_64 = "sha256-lpTwNybjo0+xrlZr6rUqFC0R1535KmbwXfFJxHuJkEQ=";
  };

  source = fetchgit {
    name = "apklayer-prebuilts-${cpu}-source";
    url = "https://github.com/erfanoabdi/apklayer";
    inherit rev;
    # cone mode keeps the top-level files (join-parts.sh, MANIFEST, SHA256SUMS-split) as well
    sparseCheckout = ["common" cpu];
    hash = hashes.${cpu};
  };
in
  runCommand "apklayer-prebuilts-${cpu}" {
    # the directory name the system apps and Android libraries sit under
    passthru = {inherit cpu;};

    meta = {
      description = "Android libraries and framework jars apklayer's runtime is built from";
      # AOSP and Robolectric keep their own licenses: docs/THIRD_PARTY.md
      license = [lib.licenses.asl20 lib.licenses.mit];
      platforms = builtins.attrNames cpus;
    };
  } ''
    cp -r "${source}" "$out"
    chmod -R u+w "$out"

    # SHA256SUMS-split names both architectures. Keep the lines for the files this checkout has, so
    # join-parts.sh still verifies everything it joined instead of failing on the absent one.
    grep -E "  (common|${cpu})/" "$out/SHA256SUMS-split" > "$out/SHA256SUMS-split.new"
    mv "$out/SHA256SUMS-split.new" "$out/SHA256SUMS-split"

    # files over 45 MB are split for GitHub: this puts them back and checks their sha256
    sh "$out/join-parts.sh" "$out"
  ''
