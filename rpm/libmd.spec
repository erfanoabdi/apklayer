# libmd (message digests), which libbsd needs; Sailfish OS 5.0 has none (rpm/sources).
Name:           libmd
Version:        1.1.0
Release:        1
Summary:        Message digest functions from BSD systems
License:        BSD-3-Clause AND BSD-2-Clause AND ISC AND SUSE-Public-Domain
URL:            https://www.hadrons.org/software/libmd/
Source0:        libmd-%{version}.tar.xz

%description
Message digest functions found on BSD systems (MD2, MD4, MD5, RIPEMD-160, SHA-1, SHA-2).

%package devel
Summary:        libmd headers
Requires:       %{name} = %{version}-%{release}

%description devel
Headers and pkg-config file of libmd.

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
%{_libdir}/libmd.so.*

%files devel
%{_includedir}/*
%{_libdir}/libmd.so
%{_libdir}/pkgconfig/libmd.pc
