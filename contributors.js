// Contributors grid from the public GitHub API, cached for an hour. Fails soft to a link.
(function (root) {
  const API = 'https://api.github.com/repos/erfanoabdi/apklayer/contributors?per_page=100';
  const FALLBACK = 'https://github.com/erfanoabdi/apklayer/graphs/contributors';
  const KEY = 'apklayer-contributors', TTL = 3600e3;

  // Keep humans only, with a safe login and https avatar; GitHub already orders by contributions.
  function clean(list) {
    if (!Array.isArray(list)) return [];
    return list.filter(c => c && c.type !== 'Bot' && /^[A-Za-z0-9-]{1,39}$/.test(c.login || '') &&
      /^https:\/\/avatars\.githubusercontent\.com\//.test(c.avatar_url || ''));
  }

  function cached(storage, now) {
    try {
      const v = JSON.parse(storage.getItem(KEY));
      if (v && now - v.t < TTL && Array.isArray(v.list)) return v.list;
    } catch (e) { /* storage unavailable or corrupt */ }
    return null;
  }

  async function load(fetchFn, storage, now) {
    const hit = cached(storage, now);
    if (hit) return hit;
    const r = await fetchFn(API, { headers: { Accept: 'application/vnd.github+json' } });
    if (!r.ok) throw new Error('GitHub API ' + r.status);
    const list = clean(await r.json());
    if (!list.length) throw new Error('empty');
    try { storage.setItem(KEY, JSON.stringify({ t: now, list })); } catch (e) { /* ignore */ }
    return list;
  }

  function render(doc, ul, list) {
    ul.replaceChildren(...list.map(c => {
      const li = doc.createElement('li'), a = doc.createElement('a'), img = doc.createElement('img'), b = doc.createElement('b');
      a.href = 'https://github.com/' + c.login;
      const n = c.contributions;
      const label = c.login + (typeof n === 'number' ? ', ' + n + (n === 1 ? ' contribution' : ' contributions') : '');
      a.title = label; a.setAttribute('aria-label', label);
      img.src = c.avatar_url + (c.avatar_url.includes('?') ? '&' : '?') + 's=144';
      img.width = img.height = 72; img.loading = 'lazy'; img.alt = '';
      b.textContent = c.login;
      a.append(img, b); li.append(a);
      return li;
    }));
  }

  function fail(doc, ul, msg) {
    ul.replaceChildren();
    msg.hidden = false;
    const a = doc.createElement('a');
    a.href = FALLBACK; a.textContent = 'See the contributors on GitHub';
    msg.replaceChildren('The contributor list could not be loaded right now. ', a, '.');
  }

  async function init(doc, fetchFn, storage) {
    const ul = doc.querySelector('#people'), msg = doc.querySelector('#people-msg');
    if (!ul || !msg) return;
    ul.setAttribute('aria-busy', 'true');
    ul.replaceChildren(...Array.from({ length: 6 }, () => { const li = doc.createElement('li'); const s = doc.createElement('div'); s.className = 'sk'; li.append(s); return li; }));
    try { render(doc, ul, await load(fetchFn, storage, Date.now())); }
    catch (e) { fail(doc, ul, msg); }
    ul.removeAttribute('aria-busy');
  }

  if (typeof document !== 'undefined' && typeof window !== 'undefined') {
    let st = { getItem() { return null; }, setItem() {} };
    try { st = window.localStorage; } catch (e) { /* blocked */ }
    init(document, window.fetch.bind(window), st);
  }
  root.apklayerContributors = { clean, load, init, KEY };
})(typeof module !== 'undefined' ? module.exports : window);
