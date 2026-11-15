# Test hosts

A fresh tree needs bionic_translation (the wrap in `subprojects/` points at gitlab). Without network, point the tree at a local checkout of the
pinned revision (`subprojects/bionic_translation.wrap`):

- on the Mac: `cp -a <checkout> subprojects/bionic_translation` (ignored by git) or `export APKLAYER_BIONIC_TRANSLATION=<checkout>`;
  `tests/run` copies it to the VM tree once (when the VM tree has none), so the VM's meson downloads nothing;
- on a host by hand: `APKLAYER_BIONIC_TRANSLATION=<checkout> meson setup b ...` copies it into `subprojects/` when that is missing.
