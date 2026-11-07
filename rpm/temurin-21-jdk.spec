# Eclipse Temurin's OpenJDK 21 for Sailfish OS, which has no Java (docs/HALIUM.md "Building for Sailfish OS"): Adoptium's own Linux aarch64 build,
# made against glibc 2.17, so it runs on Sailfish's glibc as it is; repackaged unchanged at Adoptium's RPM path (/usr/lib/jvm/temurin-21-jdk,
# where apklayer looks for it). Its AWT/X11 and ALSA libraries are unused by apklayer (headless) and need libraries Sailfish lacks: no automatic
# requirements, and nothing it carries is offered to other packages.
Name:           temurin-21-jdk
Version:        21.0.13
Release:        9
Summary:        Eclipse Temurin OpenJDK 21 (Adoptium build)
License:        GPL-2.0-only WITH Classpath-exception-2.0
URL:            https://adoptium.net
Source0:        OpenJDK21U-jdk_aarch64_linux_hotspot_21.0.13_9.tar.gz
ExclusiveArch:  aarch64
AutoReqProv:    no
Provides:       java-21-headless
%global debug_package %{nil}
%global __os_install_post %{nil}

%description
OpenJDK 21 as Eclipse Temurin builds it, for apklayer on Sailfish OS.

%prep
%setup -q -n jdk-%{version}+%{release}

%install
mkdir -p %{buildroot}/usr/lib/jvm/temurin-21-jdk
cp -a . %{buildroot}/usr/lib/jvm/temurin-21-jdk/

%files
/usr/lib/jvm/temurin-21-jdk
