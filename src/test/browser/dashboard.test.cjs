const assert = require('node:assert/strict');
const { test, before, after } = require('node:test');
const fs = require('node:fs/promises');
const path = require('node:path');
const { chromium } = require('playwright');

const site = path.resolve(__dirname, '../../main/resources/site');
const names = [
  [0, '豌豆射手', 'Peashooter'], [1, '向日葵', 'Sunflower'],
  [2, '樱桃炸弹', 'Cherry Bomb'], [3, '坚果', 'Wall-nut'],
  [4, '土豆地雷', 'Potato Mine'], [5, '寒冰射手', 'Snow Pea'],
  [62, '普通僵尸', 'Zombie'], [63, '旗帜僵尸', 'Flag Zombie'],
  [64, '路障僵尸', 'Conehead Zombie'], [65, '撑杆僵尸', 'Pole Vaulting Zombie'],
  [66, '铁桶僵尸', 'Buckethead Zombie'], [91, '召唤墓碑', 'Summoning Gravestone'],
  [9, '阳光菇', 'Sun-shroom'], [61, '墓碑', 'Gravestone']
].map(([seed_type, zh, en]) => ({ seed_type, zh, en }));
let browser;
before(async () => {
  browser = await chromium.launch({ headless: true, executablePath: process.env.PLAYWRIGHT_EXECUTABLE_PATH || undefined });
});
after(async () => { await browser?.close(); });

async function dashboard(t, { delayDecks = false, mixedProducers = false, emptyRoof = true } = {}) {
  const page = await browser.newPage({ viewport: { width: 1366, height: 900 } });
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  t.after(async () => { await page.close(); assert.deepEqual(errors, []); });
  let release;
  const gate = new Promise(resolve => { release = resolve; });
  await page.addInitScript(() => {
    const original = CanvasRenderingContext2D.prototype.fillText;
    CanvasRenderingContext2D.prototype.fillText = function (text, ...args) {
      if (this.canvas.id === 'pie') {
        if (/^[\d,]+$/.test(String(text))) this.canvas.dataset.drawnTotal = String(text);
        else this.canvas.dataset.drawnLabel = text;
      }
      return original.call(this, text, ...args);
    };
    window.EventSource = class extends EventTarget {
      constructor() { super(); window.testEvents = this; }
    };
  });
  await page.route('http://dashboard.test/**', async route => {
    const url = new URL(route.request().url());
    let payload;
    if (url.pathname === '/api/seed-names') payload = { items: names };
    else if (url.pathname === '/api/versions') payload = { items: [3215, 3214] };
    else if (url.pathname === '/api/top-decks') {
      if (delayDecks) await gate;
      const zombie = url.searchParams.get('side') === 'ZOMBIE';
      const night = ['NIGHT', 'FOG'].includes(url.searchParams.get('mode'));
      const ids = zombie ? [61, 62, 63, 64, 65, 91] : mixedProducers ? [0, 1, 9, 2, 3, 5] : [0, night ? 9 : 1, 2, 3, 4, 5];
      if (url.searchParams.get('extra_packet') === 'false') ids.pop();
      const plays = url.searchParams.get('netplay_version') === '3215' ? [9, 5] : [42, 72];
      payload = { total_plays: 120, items: emptyRoof && url.searchParams.get('mode') === 'ROOF' ? [] : plays.map((count, index) => ({
        deck_ids: (index ? ids.slice().reverse() : ids).join('|'),
        core_zh: zombie ? '路障僵尸铁桶僵尸' : '寒冰射手坚果',
        core_en: zombie ? 'Conehead Zombie + Buckethead Zombie' : 'Snow Pea + Wall-nut',
        plays: count, wins: Math.round(count * .45), win_rate: Math.round(count * .45) / count
      })) };
    } else if (url.pathname === '/api/card-stats') payload = { items: [{ seed_type: 5, picked: 6, banned: 11, won: 5, win_rate: 5 / 6, pick_rate: .05, ban_rate: 11 / 120 }] };
    else if (url.pathname === '/api/recent-matches') payload = { items: [] };
    if (payload) return route.fulfill({ json: payload });
    const file = { '/data/': 'data.html', '/site/data.js': 'data.js', '/site/data.css': 'data.css', '/site/common.css': 'common.css' }[url.pathname];
    if (file) return route.fulfill({ body: await fs.readFile(path.join(site, file)), contentType: file.endsWith('.js') ? 'text/javascript' : file.endsWith('.css') ? 'text/css' : 'text/html' });
    if (/^\/assets\/cards\/SEED_PACKET_\d+(?:_MOUND_LEVEL_0)?\.PNG$/.test(url.pathname)) {
      return route.fulfill({ body: await fs.readFile(path.join(site, '..', url.pathname.slice(1))), contentType: 'image/png' });
    }
    return route.fulfill({ status: 204 });
  });
  await page.goto('http://dashboard.test/data/');
  return { page, release };
}

async function waitForDecks(page, total) {
  await page.waitForFunction(expected => {
    const canvas = document.querySelector('#pie');
    return !canvas.hidden && canvas.dataset.drawnTotal === String(expected);
  }, total);
  assert(await page.locator('#pie').isVisible());
  // Check actual painted sectors as well as the numeric center, without a resize or theme click.
  assert(await page.locator('#pie').evaluate(canvas => {
    const { width, height } = canvas;
    return canvas.getContext('2d').getImageData(Math.floor(width / 2), Math.floor(height * .12), 1, 1).data[3] > 0;
  }));
}

test('delayed first response paints the chart without resizing', { timeout: 15000 }, async t => {
  const { page, release } = await dashboard(t, { delayDecks: true });
  await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
  release();
  await waitForDecks(page, 114);
  assert.equal(await page.locator('#pie').getAttribute('data-drawn-label'), 'Top 10 对局');
  assert.equal(await page.locator('.metric-row').count(), 0);
});

test('ranked decks show card art without the default producer', { timeout: 15000 }, async t => {
  const { page } = await dashboard(t);
  await page.waitForSelector('#deckList .deck-row');
  assert.deepEqual(await page.locator('.deck-row').first().locator('.deck-card').evaluateAll(images => images.map(image => image.alt)), ['豌豆射手', '樱桃炸弹', '坚果', '土豆地雷', '寒冰射手']);
  assert.equal(await page.locator('.deck-row').first().locator('.deck-cards').textContent(), '');
  await page.waitForFunction(() => [...document.querySelectorAll('.deck-row:first-child .deck-card')].every(image => image.complete && image.naturalWidth > 0));
  const before = await page.locator('#pie').evaluate(canvas => canvas.toDataURL());
  await page.locator('.deck-row').first().focus();
  assert.notEqual(await page.locator('#pie').evaluate(canvas => canvas.toDataURL()), before);
  await page.locator('#versionSel').focus();
  assert.equal(await page.locator('#pie').evaluate(canvas => canvas.toDataURL()), before);
  await page.click('#sideZombie');
  await page.waitForFunction(() => document.querySelector('.deck-card')?.alt === '普通僵尸');
  assert.equal(await page.locator('.deck-card[alt="墓碑"]').count(), 0);
  assert.equal(await page.locator('.deck-row').first().locator('.deck-card').last().getAttribute('src'), '/assets/cards/SEED_PACKET_91_MOUND_LEVEL_0.PNG');
  await page.waitForFunction(() => [...document.querySelectorAll('.deck-row:first-child .deck-card')].every(image => image.complete && image.naturalWidth > 0));
});

test('filter changes, empty recovery, language and live updates redraw complete decks', { timeout: 30000 }, async t => {
  const { page } = await dashboard(t);
  await waitForDecks(page, 114);
  await page.selectOption('#versionSel', '3215');
  await waitForDecks(page, 14);
  await page.selectOption('#modeSel', 'ROOF');
  await page.waitForSelector('.deck-empty');
  assert.equal(await page.locator('#pie').isVisible(), false);
  await page.selectOption('#modeSel', 'DAY');
  await waitForDecks(page, 14);
  await page.selectOption('#extraSel', 'false');
  await page.waitForFunction(() => document.querySelectorAll('.deck-row:first-child .deck-card').length === 4);
  await page.click('#sideZombie');
  await page.waitForFunction(() => document.querySelector('.deck-card')?.alt === '普通僵尸');
  await page.click('#langBtn');
  await page.waitForFunction(() => document.querySelector('.deck-card')?.alt === 'Zombie');
  assert.equal(await page.locator('#pie').getAttribute('data-drawn-label'), 'Top 10 matches');
  const lightColor = await page.locator('.deck-core-name').first().evaluate(el => getComputedStyle(el).color);
  await page.click('#themeBtn');
  assert.notEqual(await page.locator('.deck-core-name').first().evaluate(el => getComputedStyle(el).color), lightColor);
  await waitForDecks(page, 14);
  await page.selectOption('#banSel', 'false');
  await page.waitForFunction(() => document.querySelectorAll('#cards thead th').length === 5);
  assert.equal(await page.locator('#cards tbody td').count(), 5);
  await page.evaluate(() => {
    document.querySelector('#pie').dataset.drawnTotal = '';
    window.testEvents.dispatchEvent(new Event('settle'));
  });
  await waitForDecks(page, 14);
});

test('day and night maps hide only their default producer and preserve statistics', { timeout: 30000 }, async t => {
  const { page } = await dashboard(t, { mixedProducers: true, emptyRoof: false });
  await waitForDecks(page, 114);
  const originalStats = await page.locator('.deck-meta').allTextContents();
  for (const [map, retainedProducer] of [['NIGHT', '向日葵'], ['POOL', '阳光菇'], ['FOG', '向日葵'], ['ROOF', '阳光菇'], ['DAY', '阳光菇']]) {
    await page.selectOption('#modeSel', map);
    const expected = ['豌豆射手', retainedProducer, '樱桃炸弹', '坚果', '寒冰射手'];
    await page.waitForFunction(expected => JSON.stringify([...document.querySelectorAll('.deck-row:first-child .deck-card')].map(image => image.alt)) === JSON.stringify(expected), expected);
    assert.deepEqual(await page.locator('.deck-meta').allTextContents(), originalStats);
    await waitForDecks(page, 114);
  }
});

test('deck images and inline statistics fit desktop and mobile in both languages', { timeout: 30000 }, async t => {
  const { page } = await dashboard(t);
  await waitForDecks(page, 114);
  assert(await page.locator('#cards').evaluate(table => {
    const picked = table.rows[0].cells[1];
    return picked.getBoundingClientRect().left - table.getBoundingClientRect().left < table.clientWidth / 3
      && getComputedStyle(picked).textAlign === 'left';
  }));
  for (const width of [1366, 990, 768, 390, 320]) {
    await page.setViewportSize({ width, height: 900 });
    for (let language = 0; language < 2; language++) {
      await page.click('#langBtn');
      await page.waitForFunction(lang => document.documentElement.lang === lang && document.querySelector('.deck-card')?.alt === (lang === 'en' ? 'Peashooter' : '豌豆射手'), language === 0 ? 'en' : 'zh-CN');
      assert(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), `page overflow at ${width}`);
      assert(await page.locator('.deck-cards').evaluateAll(elements => elements.every(el => el.scrollWidth <= el.clientWidth)), `deck images clipped at ${width}`);
      assert(await page.locator('.deck-heading').evaluateAll(elements => elements.every(el => {
        const name = el.querySelector('.deck-core-name').getBoundingClientRect();
        const meta = el.querySelector('.deck-meta').getBoundingClientRect();
        return Math.abs(name.top - meta.top) < 4 && meta.left >= name.right && meta.right <= el.getBoundingClientRect().right + 1;
      })), `deck statistics not inline at ${width}`);
      assert(await page.locator('.deck-core-name').evaluateAll(elements => elements.every(el => el.title.length > 0)));
    }
  }
});
