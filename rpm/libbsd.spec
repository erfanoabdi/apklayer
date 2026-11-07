# libbsd, which bionic_translation's libc_bio links for the BSD functions bionic has and glibc 2.30 lacks (strlcpy, arc4random...); Sailfish OS
# 5.0 has none (rpm/sources).
Name:           libbsd
Version:        0.12.2
Release:        1
Summary:        Utility functions from BSD systems
License:        BSD-3-Clause AND BSD-2-Clause AND ISC AND MIT AND Beerware
URL:            https://libbsd.freedesktop.org
Source0:        libbsd-%{version}.tar.xz
BuildRequires:  libmd-devel

%description
Functions commonly found on BSD systems and missing from glibc.

%package devel
Summary:        libbsd headers
Requires:       %{name} = %{version}-%{release}
Requires:       libmd-devel

%description devel
Headers and pkg-config files of libbsd.

%prep
%setup -q

%build
%configure --disable-static
make %{?_smp_mflags}

%install
make install DESTDIR=%{buildroot}
rm -f %{buildroot}%{_libdir}/*.la
rm -rf %{buildroot}%{_mandir}

%post -p /sbin/ldconfig
%postun -p /sbin/ldconfig

%files
%{_libdir}/libbsd.so.*

%files devel
%{_includedir}/*
%{_libdir}/libbsd.so
%{_libdir}/libbsd-ctor.a
%{_libdir}/pkgconfig/libbsd*.pc
