#!/bin/sh
# Expose the unchanged AOSP effect factory to apklayer's btload entrypoint.
set -eu
python3 - "$1" <<'PY'
from pathlib import Path
import sys
p = Path(sys.argv[1]) / 'hardware/interfaces/audio/aidl/default/Android.bp'
s = p.read_text()
if 'apklayer_audio_effect_factory_sources' not in s:
    s += '''
// apklayer patch: reuse the original effect factory in a shared-library entrypoint.
filegroup {
    name: "apklayer_audio_effect_factory_sources",
    srcs: ["EffectFactory.cpp"],
    visibility: ["//visibility:public"],
}
'''
    p.write_text(s)
p = Path(sys.argv[1]) / 'frameworks/av/media/libmediametrics/Android.bp'
s = p.read_text()
marker = '// apklayer patch: the audioserver shared entrypoint reuses original metrics.'
if marker not in s:
    # Soong permits only the vendor subtree as a visibility grant from outside vendor.
    anchor = '    visibility: [\n'
    assert anchor in s
    s = s.replace(anchor, anchor + '        ' + marker + '\n        "//vendor:__subpackages__",\n', 1)
    p.write_text(s)
# Preserve original unavailable-service fallbacks when system_server is intentionally absent.
for relative in ('frameworks/native/libs/binder/ActivityManager.cpp',
                 'frameworks/native/libs/sensorprivacy/SensorPrivacyManager.cpp'):
    p = Path(sys.argv[1]) / relative
    s = p.read_text()
    marker = '// apklayer patch: standalone clients must not wait for system_server.'
    if marker in s: continue
    service = 'activity' if 'ActivityManager.cpp' in relative else 'sensor_privacy'
    interface = 'IActivityManager' if service == 'activity' else 'hardware::ISensorPrivacyManager'
    anchor = '    sp<' + interface + '> service = mService;'
    assert s.count(anchor) == 1
    s = s.replace('#include <mutex>', '#include <mutex>\n#include <cstdlib>', 1)
    if '#include <cstdlib>' not in s: s = '#include <cstdlib>\n' + s
    added = '\n    ' + marker + '\n    if (std::getenv("APKLAYER_NO_SYSTEM_SERVER")) {\n'
    added += '        if (service == nullptr || !IInterface::asBinder(service)->isBinderAlive()) {\n'
    added += '            auto binder = defaultServiceManager()->checkService(String16("' + service + '"));\n'
    added += '            mService = interface_cast<' + interface + '>(binder);\n'
    added += '        }\n        return mService;\n    }\n'
    p.write_text(s.replace(anchor, anchor + added, 1))
PY
