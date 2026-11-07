# libunwind 1.8, which bionic_translation links (libc_bio, libdl_bio); Sailfish OS 5.0 has none (rpm/sources).
Name:           libunwind
Version:        1.8.1
Release:        1
Summary:        Determine the call chain of a program
License:        MIT
URL:            https://github.com/libunwind/libunwind
Source0:        libunwind-%{version}.tar.gz

%description
A portable C programming interface to determine the call chain of a program.

%package devel
Summary:        libunwind headers
Requires:       %{name} = %{version}-%{release}

%description devel
Headers and pkg-config files of libunwind.

%prep
%setup -q

%build
%configure --disable-static --disable-minidebuginfo --disable-zlibdebuginfo --disable-tests --disable-documentation
make %{?_smp_mflags}

%install
make install DESTDIR=%{buildroot}
rm -f %{buildroot}%{_libdir}/*.la
rm -rf %{buildroot}%{_mandir} %{buildroot}%{_libexecdir}

%post -p /sbin/ldconfig
%postun -p /sbin/ldconfig

%files
%{_libdir}/libunwind*.so.*

%files devel
%{_includedir}/*
%{_libdir}/libunwind*.so
%{_libdir}/pkgconfig/libunwind*.pc
