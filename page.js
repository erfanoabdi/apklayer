// Release date, per-distro downloads and copy buttons. Everything degrades to the static links in index.html.
const $ = (s, r = document) => r.querySelector(s);
fetch('releases.json').then(r => r.ok ? r.json() : Promise.reject()).then(feed => {
  const rel = (feed.releases || []).find(x => x.version === feed.latest);
  if (!rel) return;
  if (/^\d{4}-\d{2}-\d{2}$/.test(rel.date || '')) {
    const el = $('[data-release-date]');
    if (el) el.textContent = ' · ' + rel.date;
  }
  const box = $('#downloads');
  if (!box) return;
  const links = [];
  const names = { noble: 'Ubuntu 24.04', trixie: 'Debian 13' };
  for (const [code, archs] of Object.entries(rel.debs || {})) {
    for (const [arch, f] of Object.entries(archs)) links.push([`${names[code] || code} (${code}) · ${arch}`, '.deb package', f.url]);
  }
  for (const [arch, f] of Object.entries(rel.clicks || {})) links.push([`Ubuntu Touch · ${arch}`, '.click package', f.url]);
  const ok = links.filter(l => /^https:\/\/github\.com\/erfanoabdi\/apklayer\/releases\/download\//.test(l[2]));
  if (!ok.length) return;
  box.replaceChildren(...ok.map(([t, s, u]) => {
    const a = document.createElement('a');
    a.href = u;
    a.append(t);
    const sm = document.createElement('small');
    sm.textContent = s;
    a.append(sm);
    return a;
  }));
}).catch(() => {});
document.querySelectorAll('[data-copy]').forEach(btn => btn.addEventListener('click', async () => {
  const text = $(btn.dataset.copy).textContent;
  try { await navigator.clipboard.writeText(text); btn.textContent = 'Copied'; } catch { btn.textContent = 'Press Ctrl+C'; }
  setTimeout(() => { btn.textContent = 'Copy'; }, 2000);
}));
