#!/usr/bin/env python3
"""A one-app F-Droid repository (index-v2, entry.jar signed with a throw-away key) for the F-Droid client flows (fdroid-flow.py); needs keytool and
jarsigner. Usage: fdroid-repo.py OUTDIR APK PACKAGE VERSION_CODE VERSION_NAME LABEL ADDRESS: OUTDIR/repo/ holds the APK, index-v2.json and entry.jar; prints
the repository's signing-certificate fingerprint (the client's trust anchor in the repository address)."""
import hashlib
import json
import re
import subprocess
import sys
import time
import zipfile
from pathlib import Path

out, apk, package, code, name, label = Path(sys.argv[1]), Path(sys.argv[2]), sys.argv[3], int(sys.argv[4]), sys.argv[5], sys.argv[6]
repo = out / 'repo'
repo.mkdir(parents=True, exist_ok=True)


def run(*argv):
    return subprocess.run(argv, check=True, capture_output=True).stdout


# the index lists the APK's signer: sha256 of its (v1) signing certificate
signer = re.search(r'SHA256: ([0-9A-F:]+)', run('keytool', '-printcert', '-jarfile', str(apk)).decode()).group(1).replace(':', '').lower()
data = apk.read_bytes()
file = '/%s_%d.apk' % (package, code)
(repo / file[1:]).write_bytes(data)
now = int(time.time() * 1000)
digest = hashlib.sha256(data).hexdigest()
version = {'added': now, 'file': {'name': file, 'sha256': digest, 'size': len(data)},
           'manifest': {'versionName': name, 'versionCode': code, 'usesSdk': {'minSdkVersion': 21, 'targetSdkVersion': 34}, 'signer': {'sha256': [signer]}}}
index = {'repo': {'name': {'en-US': 'apklayer test repo'}, 'description': {'en-US': 'one test app'}, 'address': sys.argv[7], 'timestamp': now},
         'packages': {package: {'metadata': {'added': now, 'lastUpdated': now, 'name': {'en-US': label}, 'summary': {'en-US': 'apklayer test app'}, 'categories': []},
                                'versions': {digest: version}}}}
blob = json.dumps(index).encode()
(repo / 'index-v2.json').write_bytes(blob)
entry = {'timestamp': now, 'version': 20002, 'index': {'name': '/index-v2.json', 'sha256': hashlib.sha256(blob).hexdigest(), 'size': len(blob), 'numPackages': 1}, 'diffs': {}}
with zipfile.ZipFile(repo / 'entry.jar', 'w', zipfile.ZIP_DEFLATED) as z:
    z.writestr('entry.json', json.dumps(entry))
store = out / 'repo.jks'
store.unlink(missing_ok=True)
keys = ['-keystore', str(store), '-storepass', 'testtest']
run('keytool', '-genkeypair', *keys, '-keypass', 'testtest', '-alias', 'r', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '30', '-dname', 'CN=apklayer test repo')
run('jarsigner', *keys, '-sigalg', 'SHA256withRSA', '-digestalg', 'SHA-256', str(repo / 'entry.jar'), 'r')
print(hashlib.sha256(run('keytool', '-exportcert', *keys, '-alias', 'r')).hexdigest())
