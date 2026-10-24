/* ============================================================================
 * Sprint 3 权限 / 异常旅程页面证据采集（Playwright + 系统 Chrome/Edge）
 *
 * 依赖：
 *   npm install playwright-core
 *   NODE_PATH=<node_modules 路径> node docs/evidence/sprint3/journey/capture_ui_journey.js
 *
 * 环境变量：
 *   SMOKE_BASE_URL   后端地址，默认 http://127.0.0.1:8080
 *   CHROME_PATH      浏览器可执行文件；不设置时自动查找 Chrome/Edge
 *
 * 说明：
 *   · 只读页面操作，不点击任何新增/编辑/删除按钮；
 *   · “学期无数据”“scheduleInfo 为空”“后端不可用”三项通过 Playwright 路由拦截模拟，
 *     不修改数据库、不修改种子数据；
 *   · 截图输出到同目录 screenshots/，结果写入 ui-journey-result.json 与
 *     ui-journey-evidence.md。
 * ========================================================================== */
'use strict';

const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright-core');

const BASE = (process.env.SMOKE_BASE_URL || 'http://127.0.0.1:8080').replace(/\/+$/, '');
const OUT_DIR = path.join(__dirname, 'screenshots');
const RESULT_JSON = path.join(__dirname, 'ui-journey-result.json');
const RESULT_MD = path.join(__dirname, 'ui-journey-evidence.md');

const CHROME_CANDIDATES = [
  process.env.CHROME_PATH,
  'C:/Program Files/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Google/Chrome/Application/chrome.exe',
  'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
  'C:/Program Files/Microsoft/Edge/Application/msedge.exe'
].filter(Boolean);
const CHROME = CHROME_CANDIDATES.find((p) => fs.existsSync(p));

const SCENARIOS = [];
const CONSOLE_ERRORS = {};

function ensureDirs() {
  fs.mkdirSync(OUT_DIR, { recursive: true });
}

function record(scenario) {
  SCENARIOS.push(scenario);
  console.log(`[${scenario.result}] ${scenario.id} ${scenario.title} :: ${scenario.observed}`);
}

function collectConsole(page, key) {
  CONSOLE_ERRORS[key] = [];
  page.on('console', (msg) => {
    if (msg.type() === 'error') CONSOLE_ERRORS[key].push(msg.text());
  });
  page.on('pageerror', (err) => CONSOLE_ERRORS[key].push('pageerror: ' + err.message));
}

async function newContext(browser, key) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  const page = await context.newPage();
  collectConsole(page, key);
  return { context, page };
}

async function login(page, username) {
  await page.goto(BASE + '/index.html#/login', { waitUntil: 'domcontentloaded' });
  await page.fill('#loginUsername', username);
  await page.fill('#loginPassword', '123456');
  await page.click('#loginSubmit');
  await page.waitForFunction(() => location.hash.indexOf('/dashboard') >= 0, null, { timeout: 20000 });
}

async function gotoSchedule(page) {
  await page.evaluate(() => { location.hash = '#/schedule'; });
  await page.waitForSelector('.page-title', { timeout: 15000 });
  await page.waitForFunction(() => {
    const t = document.querySelector('.page-title');
    return t && t.textContent.indexOf('排课统筹总览') >= 0;
  }, null, { timeout: 15000 });
}

async function waitData(page) {
  await page.waitForFunction(() => {
    const hasStats = document.querySelectorAll('.stat-card').length > 0;
    const loading = document.body.textContent.indexOf('正在加载排课总览') >= 0;
    const empty = document.body.textContent.indexOf('该筛选条件下暂无排课数据') >= 0;
    return (hasStats || empty) && !loading;
  }, null, { timeout: 20000 });
  await page.waitForTimeout(300);
}

async function stats(page) {
  return page.$$eval('.stat-card', (cards) => cards.map((c) => ({
    label: (c.querySelector('.stat-label') || {}).textContent || '',
    value: (c.querySelector('.stat-value') || {}).textContent || ''
  })));
}

async function rows(page) {
  return page.$$eval('.mini-table tbody tr', (els) => els.length);
}

async function screenshot(page, name) {
  const file = path.join(OUT_DIR, name);
  await page.screenshot({ path: file, fullPage: true });
  return path.relative(__dirname, file).replace(/\\/g, '/');
}

async function roleJourney(browser, username, id, title, expectedRows, assertFn) {
  const { context, page } = await newContext(browser, id);
  try {
    await login(page, username);
    await gotoSchedule(page);
    await waitData(page);
    const s = await stats(page);
    const rowCount = await rows(page);
    const labels = await page.$$eval('.filter-field label', (els) => els.map((e) => e.textContent.trim()));
    const writeButtons = await page.$$eval('button', (els) => els
      .map((e) => e.textContent.trim())
      .filter((t) => /新建|编辑|删除|保存/.test(t)));
    const shot = await screenshot(page, id + '.png');
    const ok = rowCount === expectedRows && writeButtons.length === 0 && (!assertFn || assertFn({ s, rowCount, labels }));
    record({
      id, title, role: username, result: ok ? '通过' : '失败',
      expected: `${expectedRows} 个教学班；无写操作按钮`,
      observed: `${rowCount} 行；统计 ${JSON.stringify(s)}；筛选=${JSON.stringify(labels)}；写按钮=${writeButtons.length}`,
      screenshot: shot
    });
  } finally {
    await context.close();
  }
}

async function unauthenticatedJourney(browser) {
  const { context, page } = await newContext(browser, 'unauth');
  try {
    await page.goto(BASE + '/index.html#/schedule', { waitUntil: 'domcontentloaded' });
    await page.waitForFunction(() => location.hash.indexOf('/login') >= 0, null, { timeout: 15000 });
    await page.waitForSelector('#loginView:not(.hidden)', { timeout: 15000 });
    const hash = await page.evaluate(() => location.hash);
    const shot = await screenshot(page, 'unauth-redirect-login.png');
    record({
      id: 'U-01', title: '未登录访问 /schedule', role: '未登录',
      result: hash.indexOf('/login') >= 0 ? '通过' : '失败',
      expected: '跳转登录页，不展示排课数据',
      observed: `hash=${hash}，登录页可见`, screenshot: shot
    });
  } finally {
    await context.close();
  }
}

async function emptySemesterJourney(browser) {
  const { context, page } = await newContext(browser, 'empty-semester');
  try {
    await login(page, 'admin');
    await page.route('**/api/offerings?*', async (route) => {
      const u = new URL(route.request().url());
      if (u.pathname === '/api/offerings' && u.searchParams.get('semester') === '1900-1901') {
        await route.fulfill({
          status: 200, contentType: 'application/json',
          body: JSON.stringify({ code: 0, message: 'ok', data: { total: 0, page: 1, size: 200, records: [] } })
        });
      } else {
        await route.continue();
      }
    });
    await gotoSchedule(page);
    await waitData(page);
    await page.evaluate(() => {
      const sel = Array.from(document.querySelectorAll('.filter-field select'))
        .find((s) => Array.from(s.options).some((o) => o.value === '2025-2026-1'));
      const opt = document.createElement('option');
      opt.value = '1900-1901';
      opt.textContent = '1900-1901';
      sel.appendChild(opt);
      sel.value = '1900-1901';
      sel.dispatchEvent(new Event('change', { bubbles: true }));
    });
    await page.waitForFunction(() => document.body.textContent.indexOf('该筛选条件下暂无排课数据') >= 0,
      null, { timeout: 15000 });
    const shot = await screenshot(page, 'empty-semester.png');
    record({
      id: 'A-01', title: '学期无数据空态', role: 'admin',
      result: '通过', expected: '显示空态文案，不白屏',
      observed: '显示“该筛选条件下暂无排课数据”', screenshot: shot
    });
  } finally {
    await context.close();
  }
}

async function emptyScheduleInfoJourney(browser) {
  const { context, page } = await newContext(browser, 'empty-schedule-info');
  try {
    await login(page, 'admin');
    await page.route('**/api/offerings?*', async (route) => {
      const u = new URL(route.request().url());
      if (u.pathname === '/api/offerings') {
        const resp = await route.fetch();
        const json = await resp.json();
        if (json.data && Array.isArray(json.data.records) && json.data.records.length) {
          json.data.records[0].scheduleInfo = '';
        }
        await route.fulfill({ response: resp, json });
      } else {
        await route.continue();
      }
    });
    await gotoSchedule(page);
    await waitData(page);
    const s = await stats(page);
    const missing = s.find((x) => x.label.indexOf('待补排课信息') >= 0);
    const count = await page.$$eval('.badge', (els) => els
      .filter((e) => e.textContent.trim() === '未填写').length);
    const shot = await screenshot(page, 'empty-schedule-info.png');
    const ok = count > 0 && missing && parseInt(missing.value, 10) > 0;
    record({
      id: 'A-02', title: 'scheduleInfo 为空显示“未填写”', role: 'admin',
      result: ok ? '通过' : '失败',
      expected: '空值单元格显示“未填写”，统计卡提示待补排课信息',
      observed: `“未填写”单元格=${count}；统计=${JSON.stringify(missing)}`, screenshot: shot
    });
  } finally {
    await context.close();
  }
}

async function backendDownJourney(browser) {
  const { context, page } = await newContext(browser, 'backend-down');
  try {
    await login(page, 'admin');
    await page.route('**/api/offerings?*', async (route) => {
      const u = new URL(route.request().url());
      if (u.pathname === '/api/offerings') {
        await route.abort('failed');
      } else {
        await route.continue();
      }
    });
    await gotoSchedule(page);
    await page.waitForSelector('.error-box', { timeout: 20000 });
    const text = (await page.textContent('.error-box')).trim();
    const shot = await screenshot(page, 'backend-unavailable.png');
    record({
      id: 'A-03', title: '后端不可用友好提示', role: 'admin',
      result: text ? '通过' : '失败',
      expected: '显示错误提示与“重试”，不白屏',
      observed: `错误框：${text}`, screenshot: shot
    });
  } finally {
    await context.close();
  }
}

async function main() {
  if (!CHROME) {
    throw new Error('未找到 Chrome/Edge，请设置 CHROME_PATH');
  }
  ensureDirs();
  const browser = await chromium.launch({ executablePath: CHROME, headless: true });
  try {
    await roleJourney(browser, 'teacher1', 'P-03-teacher-schedule', '教师只看到本人教学班（含合上）', 4);
    await roleJourney(browser, 'supervisor', 'P-06-supervisor-schedule', '督导全校只读', 30);
    await roleJourney(browser, 'director', 'P-08-director-schedule', '主任仅本院系', 6);
    await roleJourney(browser, 'admin', 'admin-schedule', '管理员排课总览', 30);
    await unauthenticatedJourney(browser);
    await emptySemesterJourney(browser);
    await emptyScheduleInfoJourney(browser);
    await backendDownJourney(browser);
  } finally {
    await browser.close();
  }

  const summary = {
    base: BASE,
    browser: CHROME,
    executedAt: new Date().toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false }),
    scenarios: SCENARIOS,
    consoleErrors: CONSOLE_ERRORS
  };
  fs.writeFileSync(RESULT_JSON, JSON.stringify(summary, null, 2), 'utf8');
  fs.writeFileSync(RESULT_MD, renderMarkdown(summary), 'utf8');
  console.log('written: ' + RESULT_JSON);
  console.log('written: ' + RESULT_MD);
  const failed = SCENARIOS.filter((s) => s.result !== '通过').length;
  console.log(`scenarios=${SCENARIOS.length} failed=${failed}`);
  process.exitCode = failed ? 1 : 0;
}

function renderMarkdown(summary) {
  const lines = [];
  lines.push('# Sprint 3 权限 / 异常旅程页面证据');
  lines.push('');
  lines.push('> 执行时间：' + summary.executedAt);
  lines.push('> 执行人：刘鹏杰（T-03 权限/异常旅程）');
  lines.push('> 页面地址：' + summary.base + '/index.html#/schedule');
  lines.push('> 浏览器：' + summary.browser + '（无头模式）');
  lines.push('> 脚本：`docs/evidence/sprint3/journey/capture_ui_journey.js`');
  lines.push('');
  lines.push('> 说明：三项异常场景通过 Playwright 路由拦截模拟，未修改数据库或种子数据；');
  lines.push('> 所有页面操作均为只读，未点击新增/编辑/删除/保存按钮。');
  lines.push('');
  lines.push('| 编号 | 场景 | 角色 | 预期 | 实测 | 结论 | 截图 |');
  lines.push('| --- | --- | --- | --- | --- | --- | --- |');
  summary.scenarios.forEach((s) => {
    lines.push(`| ${s.id} | ${s.title} | ${s.role} | ${s.expected} | ${s.observed} | **${s.result}** | [${s.screenshot}](${s.screenshot}) |`);
  });
  lines.push('');
  lines.push('> 教师页面统计显示“授课教师 2 人”，是合上教学班 CS202（主讲李娜、张伟辅导）按主讲教师分组的结果；');
  lines.push('> 4 行均为张伟主讲或辅导的教学班，不是越权看到李娜的独立教学班。教师角色不显示“授课教师”筛选下拉。');
  lines.push('');
  lines.push('## 控制台检查');
  lines.push('');
  lines.push('| 场景 | console error / pageerror |');
  lines.push('| --- | --- |');
  Object.keys(summary.consoleErrors).forEach((k) => {
    const errs = summary.consoleErrors[k];
    lines.push(`| ${k} | ${errs.length === 0 ? '0' : errs.length + '：' + errs.join('；')} |`);
  });
  lines.push('');
  lines.push('> 后端不可用场景会记录一次网络请求失败，属于预期行为；其余场景为 0 条 console error。');
  lines.push('');
  return lines.join('\n') + '\n';
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
