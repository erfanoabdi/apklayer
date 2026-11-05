const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { runInNewContext } = require('node:vm');
const { test } = require('node:test');

const source = readFileSync(new URL('../site.js', `file://${__filename}`), 'utf8');

async function render(response) {
  const links = [{ href: 'fallback' }, { href: 'fallback' }];
  const label = { textContent: 'fallback' };
  const version = { textContent: 'fallback' };
  await runInNewContext(source, {
    fetch: async path => {
      assert.equal(path, 'releases.json');
      if (response instanceof Error) throw response;
      return response;
    },
    document: {
      querySelectorAll: () => links,
      querySelector: selector => selector === '[data-release-label]' ? label : version,
    },
  });
  return { links, label, version };
}

test('the release feed updates both links and version labels', async () => {
  const feed = JSON.parse(readFileSync(new URL('../releases.json', `file://${__filename}`), 'utf8'));
  const page = await render({ ok: true, json: async () => feed });
  for (const link of page.links) {
    assert.equal(link.href, `https://github.com/erfanoabdi/apklayer/releases/tag/v${feed.latest}`);
  }
  assert.equal(page.label.textContent, `v${feed.latest} is here — see what’s new`);
  assert.equal(page.version.textContent, `v${feed.latest}`);
});

test('missing, invalid, and unavailable releases preserve fallback links', async () => {
  const responses = [
    new Error('Offline'),
    { ok: false },
    { ok: true, json: async () => { throw new Error('Invalid JSON'); } },
    ...[null, {}, { latest: null }, { latest: 3 }, { latest: '<script>' }].map(feed => ({ ok: true, json: async () => feed })),
  ];
  for (const response of responses) {
    const page = await render(response);
    assert.ok(page.links.every(link => link.href === 'fallback'));
    assert.equal(page.label.textContent, 'fallback');
    assert.equal(page.version.textContent, 'fallback');
  }
});
