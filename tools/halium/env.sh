D=~/apklayer-dev-cam
export APKLAYER_INSTANCE=cam XDG_RUNTIME_DIR=$D/run XDG_STATE_HOME=$D/state XDG_DATA_HOME=$D/data WAYLAND_DISPLAY=/run/user/32011/wayland-0
export APKLAYER_NATIVE_JAVA=$D/jdk/bin/java
A=$D/v2/inst/bin/apklayer
export PATH=$D/jdk/bin:$PATH
export PULSE_SERVER=unix:/run/user/32011/pulse/native
