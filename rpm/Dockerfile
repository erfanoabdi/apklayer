# The Sailfish OS build root of the apklayer RPMs (tools/release/sailfish-rpm.sh, docs/HALIUM.md "Building for Sailfish OS"): the Platform SDK's
# own aarch64 target (Sailfish_OS-<release>-Sailfish_SDK_Target-aarch64.tar from releases.sailfishos.org/sdk/targets, the rootfs that sb2 and mb2
# build in) run natively as an aarch64 container instead of under scratchbox2: the same packages, repositories and rpmbuild, without the i486 SDK
# chroot and qemu (the Mac and GitHub's ubuntu-24.04-arm runners are aarch64). Compiler and tools come from Sailfish's own repositories.
FROM scratch
ADD target.tar /
RUN zypper -n --gpg-auto-import-keys ref && zypper -n in rpm-build gcc gcc-c++ make autoconf automake libtool pkgconfig meson ninja \
    python3-base git curl ca-certificates tar gzip xz && zypper -n clean -a
CMD ["/bin/bash"]
