"""PC (instance env, repo root): run Fennec, freeze it (SIGSTOP from bpftrace) at Gecko's first SIGSEGV, checksum the guest's base.apk mapping against the file,
find the mapping pointer in guest heap (nsZipHandle) and dump the neighbourhood. Usage: python3 zipstop.py <out dir>   (needs sudo, bpftrace)"""
import hashlib, os, subprocess, sys, time
from pathlib import Path
sys.path.insert(0, str(Path.cwd() / 'tests/lib'))
import instance
pkg = 'org.mozilla.fennec_fdroid'
out = Path(sys.argv[1]); out.mkdir(exist_ok=True)
instance.assert_clean()
bt = subprocess.Popen(['sudo', '-n', 'bpftrace', '--unsafe', '-B', 'none', '-e',
    'tracepoint:signal:signal_generate /args->sig == 11 && comm == "Gecko"/ { printf("STOP %d\\n", pid); signal(19); exit(); }'],
    stdout=open(out / 'bt.txt', 'w'), stderr=open(out / 'bt.err', 'w'))
time.sleep(3)
p = subprocess.Popen(['bin/apklayer', 'run', pkg], env=dict(instance.ENV, APKLAYER_JOPTS='-Dapklayer.runms=200000 -Dapklayer.logcat=true'),
                     stdout=open(out / 'app.log', 'w'), stderr=subprocess.STDOUT)
try:
    pid = None
    for _ in range(600):
        t = (out / 'bt.txt').read_text().split('STOP ')
        if len(t) > 1 and t[1].strip(): pid = t[1].split()[0]; break
        assert p.poll() is None
        time.sleep(0.1)
    print('stopped pid', pid, flush=True)
    st = open('/proc/%s/status' % pid).read()
    print([l for l in st.splitlines() if l.startswith('State')])
    maps = [l.split() for l in open('/proc/%s/maps' % pid)]
    open(out / 'maps.txt', 'w').write(open('/proc/%s/maps' % pid).read())
    ap = [m for m in maps if m[-1].endswith('/base.apk')]
    print('apk maps', ap)
    lo = int(ap[0][0].split('-')[0], 16); hi = lo + os.path.getsize(ap[0][-1])
    mem = open('/proc/%s/mem' % pid, 'rb', 0)
    mem.seek(lo); h = hashlib.sha256(); n = hi - lo; got = 0
    f = open(ap[0][-1], 'rb'); fh = hashlib.sha256(); first_diff = None
    while got < n:
        b = mem.read(min(1 << 22, n - got)); d = f.read(len(b))
        if b != d and first_diff is None:
            first_diff = got + next(i for i in range(min(len(b), len(d))) if b[i] != d[i]) if len(b) == len(d) else got
        h.update(b); got += len(b)
        if not b: break
    print('mapped %x-%x len %d read %d sha %s' % (lo, hi, n, got, h.hexdigest()))
    print('file sha', hashlib.sha256(open(ap[0][-1], 'rb').read()).hexdigest(), 'first diff', first_diff)
    import struct, zipfile
    zi = zipfile.ZipFile(ap[0][-1]).getinfo('assets/omni.ja')
    f.seek(zi.header_offset); lh = f.read(30); nl, el = struct.unpack('<HH', lh[26:30])
    data_off = zi.header_offset + 30 + nl + el
    print('omni.ja header_offset %d data_off %d size %d method %d' % (zi.header_offset, data_off, zi.file_size, zi.compress_type))
    for a, b, pr, *_ in [(int(m[0].split('-')[0], 16), int(m[0].split('-')[1], 16), m[1]) for m in maps if m[1][:2] == 'rw' and len(m) < 6]:
        data = b''
        try: mem.seek(a); data = mem.read(b - a)
        except (OSError, ValueError, OverflowError): continue
        for tag, v in (('base', lo), ('omni', lo + data_off)):
            needle = struct.pack('<Q', v); i = data.find(needle)
            while i >= 0:
                print('%s ptr %x at %x:' % (tag, v, a + i), struct.unpack_from('<12Q', data, max(0, i - 32)) if i >= 32 else '')
                i = data.find(needle, i + 8)
finally:
    if os.environ.get('KEEP'): (out / 'pid').write_text(pid); sys.exit(0)
    subprocess.run(['sudo', '-n', 'pkill', '-CONT', '-f', '[b]in/apklayer run'])
    bt.terminate()
    instance.stop()
    p.wait()
    instance.assert_clean()
