# The apklayer RPM for Sailfish OS (docs/HALIUM.md "Building for Sailfish OS"): the deb's model (debian/rules) on Sailfish's own toolchain and
# libraries. The Meson build and install with prefix /usr and without the Android half, which the per-user runtime bundle brings (`apklayer setup`).
# tools/release/sailfish-rpm.sh builds it in the Sailfish OS build root (rpm/Dockerfile) with --define 'apklayer_version X' and
# --define 'apklayer_prebuilts <a joined prebuilts checkout>' and --define 'apklayer_slint <Slint built by tools/release/sailfish-slint.sh>'
# (apklayer-manager: Slint's prebuilt compiler needs a newer glibc; this one has the software renderer, drawn on Lipstick's wl_shell).
Name:           apklayer
Version:        %{apklayer_version}
Release:        1
Summary:        Run Android apps on Linux in Wayland windows
License:        AGPL-3.0-or-later
URL:            https://github.com/erfanoabdi/apklayer
Source0:        apklayer-%{version}.tar.gz
ExclusiveArch:  aarch64
BuildRequires:  meson >= 1.3
BuildRequires:  ninja
BuildRequires:  gcc-c++
BuildRequires:  pkgconfig
BuildRequires:  python3-base
BuildRequires:  binutils
BuildRequires:  git
BuildRequires:  temurin-21-jdk
BuildRequires:  pkgconfig(wayland-client)
BuildRequires:  pkgconfig(wayland-cursor)
BuildRequires:  pkgconfig(wayland-protocols)
BuildRequires:  pkgconfig(xkbcommon)
BuildRequires:  pkgconfig(cairo)
BuildRequires:  pkgconfig(libpng)
BuildRequires:  pkgconfig(libdrm)
# the graphics HAL (the composer's display): Sailfish's Mesa libgbm, beside libhybris (it replaces neither libEGL nor libGLES)
BuildRequires:  pkgconfig(gbm)
BuildRequires:  pkgconfig(egl)
BuildRequires:  pkgconfig(gio-2.0)
BuildRequires:  pkgconfig(libpulse-simple)
BuildRequires:  pkgconfig(gstreamer-app-1.0)
BuildRequires:  pkgconfig(gstreamer-video-1.0)
BuildRequires:  pkgconfig(gstreamer-audio-1.0)
BuildRequires:  pkgconfig(gstreamer-allocators-1.0)
BuildRequires:  pkgconfig(libelf)
BuildRequires:  pkgconfig(libbsd)
BuildRequires:  pkgconfig(libunwind)
Requires:       temurin-21-jdk
Requires:       python3-base
# bridge/portal's D-Bus helpers (Bluetooth, location, settings) are PyGObject; Sailfish does not install it by default
Requires:       python3-gobject
Requires:       zip
Requires:       unzip
Requires:       zstd
# the runtime bundle's download (common/fetch.cpp); Sailfish has wget in Chum only: `apklayer setup --bundle <file>` works without
Recommends:     wget
%{?systemd_requires}

# Android's libraries (the runtime bundle brings them) have unversioned sonames, the host's are versioned: no requirement on any unversioned one,
# nor on bionic_translation's own (the package's libraries are private: none is offered to other packages)
%global __provides_exclude_from ^%{_prefix}/lib/apklayer/.*$
%global __requires_exclude ^(.*\\.so\\(\\)|lib[a-z]*_bio\\.so).*$
%global debug_package %{nil}

%description
apklayer runs Android apps (APKs) as Linux processes: the app's dex code is converted to JVM classes once, Android's own native libraries run
through bionic_translation, and each app opens a Wayland window. This package holds the command line tool, the window shell and the native
runtime; the Android runtime bundle (Android's libraries and framework) is downloaded per user by `apklayer setup`.

%package devel
Summary:        apklayer hardware plugin API (headers and pkg-config file)
Requires:       %{name} = %{version}-%{release}

%description devel
Headers and apklayer-plugin.pc for building apklayer hardware plugins out of tree (docs/PLUGINS.md in the apklayer sources).

%prep
%setup -q

%build
# the flags the tree is built and tested with (meson's), as debian/rules does: the path layer and bionic_translation interpose symbols
export PATH=/usr/lib/jvm/temurin-21-jdk/bin:$PATH
env -u CFLAGS -u CXXFLAGS -u CPPFLAGS -u LDFLAGS -u FFLAGS -u FCFLAGS meson setup build --prefix=%{_prefix} --libdir=%{_lib} \
    -Dprebuilts=%{apklayer_prebuilts} -Dandroid_runtime=false -Dpackage_version=%{version}
ninja -C build %{?_smp_mflags}
# apklayer-manager as debian/rules builds it, on its own wl_shell platform (manager/src/wlshell.cpp: Lipstick has no xdg_wm_base for winit)
env -u CFLAGS -u CXXFLAGS -u CPPFLAGS -u LDFLAGS -u FFLAGS -u FCFLAGS meson setup build-manager manager --prefix=%{_prefix} -Dlibdir=lib/apklayer \
    -Dslint=%{apklayer_slint} -Dwlshell=true
ninja -C build-manager %{?_smp_mflags} apklayer-manager

%install
export PATH=/usr/lib/jvm/temurin-21-jdk/bin:$PATH
DESTDIR=%{buildroot} meson install -C build --skip-subprojects --no-rebuild --quiet
DESTDIR=%{buildroot} meson install -C build-manager --no-rebuild --quiet
install -D -m644 debian/instances %{buildroot}%{_sysconfdir}/apklayer/instances
# kernel binder only (apklayer binder kernel): the userspace binder, the default, needs no unit; `systemctl enable --now apklayer-binder` turns it on
install -D -m644 debian/apklayer.apklayer-binder.service %{buildroot}%{_unitdir}/apklayer-binder.service
# Lipstick's launcher: invoker's single instance would lock by the Exec binary (as the app entries say, cli/desktop.cpp); the icon by its path
sed -i -e 's|^Icon=.*|Icon=%{_datadir}/icons/hicolor/256x256/apps/apklayer.png|' -e '$aX-Nemo-Single-Instance=no' \
    %{buildroot}%{_datadir}/applications/apklayer-manager.desktop
# the Lomiri content hub peer is Ubuntu Touch's
rm -f %{buildroot}%{_datadir}/applications/apklayer-share.desktop
rm -rf %{buildroot}%{_datadir}/lomiri-content-hub

%post
systemctl daemon-reload >/dev/null 2>&1 || :
# a phone whose home is on its small system partition keeps apklayer's data on /userdata (cli/datamove.cpp): each user's own directory there,
# made by `apklayer setup`; sticky like /tmp. One that exists (made by hand) stays as it is.
if [ -d /userdata ] && [ ! -e /userdata/apklayer ]; then mkdir /userdata/apklayer && chmod 1777 /userdata/apklayer; fi || :

%postun
systemctl daemon-reload >/dev/null 2>&1 || :

%files
%{_bindir}/apklayer
%{_bindir}/apklayer-manager
%{_datadir}/applications/apklayer-manager.desktop
%{_prefix}/lib/apklayer
%{_datadir}/apklayer
%{_datadir}/icons/hicolor/*/apps/apklayer.*
%{_datadir}/desktop-directories/apklayer.directory
%config(noreplace) %{_sysconfdir}/xdg/menus/applications-merged/apklayer.menu
%{_unitdir}/apklayer-binder.service
%config(noreplace) %{_sysconfdir}/apklayer/instances

%files devel
%{_includedir}/apklayer
%{_libdir}/pkgconfig/apklayer-plugin.pc
