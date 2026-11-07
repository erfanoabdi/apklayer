# The armhf build root of the 32-bit app process (docs/PLAN.md "apps built for another CPU" 2(a), tools/armhf/build.sh): Ubuntu 24.04 arm64
# with Debian's armhf cross compiler and the armhf (multiarch) libraries of the deb's Build-Depends (debian/control), plus the armhf JRE the
# 32-bit process runs on. The same glibc (2.39) as the Ubuntu 24.04 phones the route runs on natively.
FROM ubuntu:24.04
RUN dpkg --add-architecture armhf && \
    sed -i 's|^URIs: http://ports.ubuntu.com/ubuntu-ports/|URIs: http://ports.ubuntu.com/ubuntu-ports/\nArchitectures: arm64 armhf|' /etc/apt/sources.list.d/ubuntu.sources && \
    apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
      crossbuild-essential-armhf meson ninja-build pkg-config python3 binutils git ca-certificates patch openjdk-21-jdk-headless \
      wayland-protocols libwayland-bin \
      libwayland-dev:armhf libxkbcommon-dev:armhf libcairo2-dev:armhf libpng-dev:armhf libdrm-dev:armhf libgbm-dev:armhf libegl-dev:armhf \
      libgles-dev:armhf libpipewire-0.3-dev:armhf libgstreamer1.0-dev:armhf libgstreamer-plugins-base1.0-dev:armhf libelf-dev:armhf \
      libunwind-dev:armhf libbsd-dev:armhf libpulse-dev:armhf libglib2.0-dev:armhf \
      libegl-mesa0:armhf libgl1-mesa-dri:armhf zstd && \
    apt-get download openjdk-21-jre-headless:armhf && dpkg -x openjdk-21-jre-headless_*_armhf.deb / && rm -f *.deb && \
    apt-get clean && rm -rf /var/lib/apt/lists/*
# the same tree's arm64 (native) build for a phone with that glibc (the 64-bit CLI and runtime beside the 32-bit one): the deb's Build-Depends
RUN apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends build-essential zip unzip \
      libwayland-dev libxkbcommon-dev libcairo2-dev libpng-dev libdrm-dev libgbm-dev libegl-dev libgles-dev libpipewire-0.3-dev \
      libgstreamer1.0-dev libgstreamer-plugins-base1.0-dev libelf-dev libunwind-dev libbsd-dev libpulse-dev libglib2.0-dev libinput-dev && \
    apt-get clean && rm -rf /var/lib/apt/lists/*
CMD ["/bin/bash"]
