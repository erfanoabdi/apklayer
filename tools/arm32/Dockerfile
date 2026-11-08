# The build root of the 32-bit app process (docs/PLAN.md "apps built for another CPU" 2(a), tools/arm32/build.sh): Debian 13 arm64 with Debian's
# armel cross compiler and the armel (multiarch) libraries of the deb's Build-Depends (debian/control), plus the armel JRE the process runs on.
# armel, not armhf: Android's armeabi-v7a code uses the base procedure call standard (floats in core registers, no Tag_ABI_VFP_args), as armel's
# glibc, Mesa and JVM do; armhf's pass them in VFP registers, so every call with a float between them goes wrong. Debian's armel JVM is Zero.
FROM debian:trixie
RUN dpkg --add-architecture armel && apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
      crossbuild-essential-armel meson ninja-build pkg-config python3 binutils git ca-certificates patch openjdk-21-jdk-headless \
      wayland-protocols libwayland-bin \
      libwayland-dev:armel libxkbcommon-dev:armel libcairo2-dev:armel libpng-dev:armel libdrm-dev:armel libgbm-dev:armel libegl-dev:armel \
      libgles-dev:armel libpipewire-0.3-dev:armel libgstreamer1.0-dev:armel libgstreamer-plugins-base1.0-dev:armel libelf-dev:armel \
      libunwind-dev:armel libbsd-dev:armel libpulse-dev:armel libglib2.0-dev:armel libffi8:armel \
      libegl-mesa0:armel libgl1-mesa-dri:armel zstd && \
    apt-get download openjdk-21-jre-headless:armel && dpkg -x openjdk-21-jre-headless_*_armel.deb / && rm -f *.deb && \
    apt-get clean && rm -rf /var/lib/apt/lists/*
CMD ["/bin/bash"]
