// Renders the project's open-work list, docs/PLAN.md of the main branch fetched from GitHub (no copy lives on this branch);
// headings, bullets, `code`, **bold**, without a library; text is escaped first.
(function (root) {
  const esc = s => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  const inline = s => esc(s).replace(/`([^`]+)`/g, '<code>$1</code>').replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
  function render(md) {
    let out = '', list = false;
    for (const line of md.split('\n')) {
      const h = /^(#{1,3}) (.*)/.exec(line), li = /^- (.*)/.exec(line);
      if (!li && list) { out += '</ul>'; list = false; }
      if (h) out += `<h${h[1].length}>${inline(h[2])}</h${h[1].length}>`;
      else if (li) { if (!list) { out += '<ul>'; list = true; } out += `<li>${inline(li[1])}</li>`; }
      else if (line.trim()) out += `<p>${inline(line)}</p>`;
    }
    return out + (list ? '</ul>' : '');
  }
  root.renderPlan = render;
  const RAW = 'https://raw.githubusercontent.com/erfanoabdi/apklayer/main/docs/PLAN.md';
  const BLOB = 'https://github.com/erfanoabdi/apklayer/blob/main/docs/PLAN.md';
  root.PLAN_URL = RAW;
  if (typeof document !== 'undefined') {
    fetch(RAW).then(r => r.ok ? r.text() : Promise.reject()).then(t => {
      document.querySelector('#plan').innerHTML = render(t);
    }).catch(() => {
      document.querySelector('#plan').innerHTML = '<h1>Roadmap</h1><p>The open-work list could not be loaded. <a href="' + BLOB + '">Read it on GitHub</a>.</p>';
    });
  }
})(typeof module !== 'undefined' ? module.exports : window);
