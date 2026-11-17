const assert = require('node:assert/strict');
const { test } = require('node:test');
const { clean, load, KEY } = require('../contributors.js').apklayerContributors;
const render = require('../plan.js').renderPlan;

const av = 'https://avatars.githubusercontent.com/u/1?v=4';
const sample = [
  { login: 'a', type: 'User', avatar_url: av, contributions: 5 },
  { login: 'dependabot[bot]', type: 'Bot', avatar_url: av, contributions: 9 },
  { login: 'b', type: 'Bot', avatar_url: av, contributions: 9 },
  { login: 'evil"><x', type: 'User', avatar_url: av, contributions: 1 },
  { login: 'c', type: 'User', avatar_url: 'http://evil.example/x.png', contributions: 1 },
];
const mem = () => { const m = new Map(); return { getItem: k => m.has(k) ? m.get(k) : null, setItem: (k, v) => m.set(k, v) }; };

test('clean drops bots, unsafe logins and foreign avatars', () => {
  assert.deepEqual(clean(sample).map(c => c.login), ['a']);
  assert.deepEqual(clean(null), []);
  assert.deepEqual(clean({ message: 'rate limit' }), []);
});

test('load caches for an hour and survives broken storage', async () => {
  let calls = 0;
  const f = async () => (calls++, { ok: true, json: async () => sample });
  const st = mem();
  assert.equal((await load(f, st, 1000)).length, 1);
  assert.equal((await load(f, st, 2000)).length, 1);
  assert.equal(calls, 1);
  await load(f, st, 1000 + 3600e3 + 1);
  assert.equal(calls, 2);
  const broken = { getItem() { throw new Error('x'); }, setItem() { throw new Error('x'); } };
  assert.equal((await load(f, broken, 1)).length, 1);
  st.setItem(KEY, '{bad');
  assert.equal((await load(f, st, 5)).length, 1);
});

test('rate limit, errors and empty lists reject', async () => {
  await assert.rejects(load(async () => ({ ok: false, status: 403 }), mem(), 1));
  await assert.rejects(load(async () => ({ ok: true, json: async () => [] }), mem(), 1));
  await assert.rejects(load(async () => { throw new Error('offline'); }, mem(), 1));
});

test('plan renderer escapes html and keeps lists', () => {
  const h = render('# T\n\n- a `<b>`\n- **x**\n\ntext <script>');
  assert.match(h, /<h1>T<\/h1><ul><li>a <code>&lt;b&gt;<\/code><\/li><li><strong>x<\/strong><\/li><\/ul><p>text &lt;script&gt;<\/p>/);
});
