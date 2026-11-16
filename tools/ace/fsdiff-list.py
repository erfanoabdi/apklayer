#!/usr/bin/env python3
"""List of guest-visible paths for tests/apps/hello-filedump (files/list.txt) from ace-fsdiff.bt outputs: fsdiff-list.py extra-1.txt ... > list.txt
Takes every path whose call succeeded (native or translated), /proc/<pid> -> /proc/self, drops fd/task numbers and host-only trees."""
import re, sys
S = set()
for fn in sys.argv[1:]:
    pend = {}
    for l in open(fn, errors='replace'):
        f = l.rstrip('\n').split(' ', 4)
        if f[0] == 'P' and len(f) == 5: pend[f[2]] = f[4]
        elif f[0] == 'X' and len(f) == 4 and f[2] in pend:
            p = pend.pop(f[2])
            if int(f[3]) < 0 and 'ENOENT-too' not in sys.argv: continue
            p = re.sub(r'^/proc/\d+(/|$)', r'/proc/self\1', p.partition('\t')[0])
            if re.match(r'/(proc|sys|dev|system|system_ext|vendor|product|odm|apex|etc|data|storage|sdcard|mnt|cache|config|acct|linkerconfig|d|debug_ramdisk|metadata|oem)(/|$)', p) and '/fd/' not in p and '/task/' not in p and '/map_files/' not in p: S.add(p)
EXTRA = '''/proc /proc/self /proc/self/task /proc/self/fd /proc/self/net /proc/self/mounts /proc/self/mountinfo /proc/self/mountstats /proc/self/cgroup /proc/self/stat /proc/self/statm /proc/self/status
/proc/self/smaps_rollup /proc/self/limits /proc/self/environ /proc/self/cmdline /proc/self/comm /proc/self/oom_score_adj /proc/self/attr/current /proc/self/sched /proc/self/wchan /proc/self/io
/proc/version /proc/stat /proc/uptime /proc/loadavg /proc/filesystems /proc/devices /proc/modules /proc/partitions /proc/swaps /proc/vmstat /proc/zoneinfo /proc/diskstats /proc/cmdline
/proc/sys/kernel/random/boot_id /proc/sys/kernel/osrelease /proc/sys/kernel/ostype /proc/sys/kernel/hostname /proc/sys/kernel/arch /proc/sys/kernel/pid_max /proc/sys/vm/overcommit_memory /proc/sys/fs/inotify/max_user_watches
/sys /sys/devices /sys/devices/system/cpu /sys/devices/system/cpu/online /sys/devices/system/cpu/possible /sys/devices/system/cpu/present /sys/devices/system/cpu/cpu0 /sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq
/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq /sys/devices/system/cpu/cpu0/cpu_capacity /sys/devices/system/cpu/cpu0/topology/core_id /sys/devices/virtual /sys/class /sys/class/power_supply /sys/class/thermal /sys/class/net
/sys/class/block /sys/block /sys/kernel /sys/kernel/debug /sys/fs/selinux /sys/fs/cgroup /sys/module /sys/bus /sys/firmware /sys/hypervisor /sys/devices/virtual/dmi/id /sys/class/dmi/id/product_name
/dev /dev/null /dev/zero /dev/random /dev/urandom /dev/tty /dev/ashmem /dev/binder /dev/hwbinder /dev/vndbinder /dev/ion /dev/dri /dev/kgsl-3d0 /dev/mali0 /dev/socket /dev/input /dev/fuse /dev/kvm /dev/vboxguest /dev/qemu_pipe /dev/goldfish_pipe
/system /system/bin /system/lib64 /system/lib /system/etc /system/framework /system/build.prop /system/bin/app_process64 /system/bin/linker64 /system/bin/sh /system/bin/su /system/xbin /system/etc/hosts /system/fonts
/vendor /vendor/build.prop /vendor/lib64 /vendor/etc /product /product/build.prop /odm /odm/build.prop /system_ext /system_ext/build.prop /apex /apex/com.android.runtime /apex/com.android.art /apex/com.android.runtime/lib64/bionic/libc.so
/default.prop /init /init.rc /sepolicy /sbin /cache /data /data/local/tmp /data/local /mnt /mnt/sdcard /storage /storage/emulated /storage/emulated/0 /sdcard /config /acct /d /metadata /oem /etc /etc/hosts /etc/resolv.conf /etc/passwd /linkerconfig /debug_ramdisk'''.split()
S.update(EXTRA)
print('\n'.join(sorted(S)))
