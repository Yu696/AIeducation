/* ============================================================================
 * pages/coverage.js —— 督导覆盖率（契约第 7 节 / US-04）
 *
 * 功能：
 *   · GET /api/supervision/coverage → {totalOfferings, supervisedOfferings, coverageRate,
 *     byDepartment[], bySemester[]}
 *   · 总体覆盖率大数字 + 进度环
 *   · 按院系表格 + 横向条形图；按学期折线图
 *   · 页面显著位置写明覆盖率计算口径（契约第 7 节原文，US-04 验收标准③）
 *   · 导出 CSV：GET /api/supervision/coverage/export
 * ========================================================================== */
(function (window, document) {
  'use strict';

  var AJ = window.AJ;
  var Api = AJ.Api;
  var UI = AJ.UI;
  var util = AJ.util;

  /** 覆盖率计算口径（契约第 7 节原文，不得改写） */
  var COVERAGE_RULE =
    '覆盖率 = 统计周期内存在有效听课记录（supervision.status=\'FINISHED\'）的开课记录数 ÷ ' +
    '统计周期内开设状态为\'正常开课\'的开课记录数 × 100%，按开课记录（offering）去重计数，' +
    '一个人次多次听课只计 1。';

  var mounted = [];

  function asArray(res) {
    if (!res) { return []; }
    if (Object.prototype.toString.call(res) === '[object Array]') { return res; }
    return res.records || res.list || res.rows || [];
  }

  /**
   * 解析覆盖率响应，兼容后端返回包装在 data 之外或字段名微调的情况。
   */
  function parseCoverage(data) {
    data = data || {};
    var total = util.toNumber(util.pick(data,
      ['totalOfferings', 'total', 'offeringTotal', 'normalOfferingCount'], 0));
    var supervised = util.toNumber(util.pick(data,
      ['supervisedOfferings', 'supervised', 'finishedCount', 'supervisedCount'], 0));
    var rate = util.pick(data, ['coverageRate', 'rate', 'coverage'], null);
    if (rate === null || rate === '') {
      // 后端未给比率时用两个计数兜底计算
      rate = total > 0 ? (supervised / total * 100) : 0;
    }
    return {
      totalOfferings: total,
      supervisedOfferings: supervised,
      unsupervisedOfferings: Math.max(0, total - supervised),
      coverageRate: util.toNumber(rate, 0),
      byDepartment: asArray(util.pick(data, ['byDepartment', 'departmentStats', 'departmentCoverage'], [])),
      bySemester: asArray(util.pick(data, ['bySemester', 'semesterStats', 'semesterCoverage'], []))
    };
  }

  /** 从院系/学期分组项里取标签与覆盖率 */
  function groupPoints(rows) {
    return rows.map(function (r) {
      var label = util.pick(r, ['departmentName', 'departmentCode', 'deptName', 'name', 'semester', 'label'], '未标注');
      var rate = util.pick(r, ['coverageRate', 'rate', 'coverage', 'percent'], null);
      var total = util.toNumber(util.pick(r, ['totalOfferings', 'total', 'offeringCount'], 0));
      var supervised = util.toNumber(util.pick(r, ['supervisedOfferings', 'supervised', 'finishedCount'], 0));
      if (rate === null || rate === '') {
        rate = total > 0 ? (supervised / total * 100) : 0;
      }
      return {
        raw: r,
        label: label,
        total: total,
        supervised: supervised,
        unsupervised: Math.max(0, total - supervised),
        rate: util.toNumber(rate, 0)
      };
    });
  }

  function toneOf(rate) {
    return rate >= 80 ? 'green' : rate >= 60 ? 'blue' : rate >= 40 ? 'orange' : 'red';
  }

  /* ==================================================================
   * 页面渲染
   * ================================================================ */
  function render(container) {
    mounted = [];

    var head = document.createElement('div');
    head.className = 'page-head';
    head.innerHTML =
      '<h2 class="page-title">督导覆盖率</h2>' +
      '<p class="page-desc">按开课记录统计“正常开课”中被有效听评课覆盖的比例，支持按院系与学期分解。</p>';

    var toolbar = document.createElement('div');
    toolbar.className = 'page-toolbar';

    var refreshBtn = document.createElement('button');
    refreshBtn.type = 'button';
    refreshBtn.className = 'btn';
    refreshBtn.textContent = '刷新';
    refreshBtn.addEventListener('click', load);
    toolbar.appendChild(refreshBtn);

    var exportBtn = document.createElement('button');
    exportBtn.type = 'button';
    exportBtn.className = 'btn btn-primary';
    exportBtn.textContent = '导出 CSV';
    exportBtn.addEventListener('click', function () {
      exportBtn.disabled = true;
      exportBtn.textContent = '导出中…';
      Api.exportCsv('/supervision/coverage/export', {}, '督导覆盖率.csv')
        .then(function (name) { UI.toast('已导出：' + name, 'success'); },
          function (err) { UI.toast((err && err.message) || '导出失败', 'error'); })
        .then(function () {
          exportBtn.disabled = false;
          exportBtn.textContent = '导出 CSV';
        });
    });
    toolbar.appendChild(exportBtn);
    head.appendChild(toolbar);
    container.appendChild(head);

    // 口径说明：页面显著位置固定展示
    container.appendChild(UI.note('<strong>覆盖率计算口径</strong>（Sprint 1 冻结口径，US-04 验收标准③）：<br>' +
      util.escape(COVERAGE_RULE)));

    var body = document.createElement('div');
    body.style.marginTop = '16px';
    body.innerHTML = UI.loadingBlock('正在加载覆盖率数据…');
    container.appendChild(body);

    load();

    function load() {
      body.innerHTML = UI.loadingBlock('正在加载覆盖率数据…');
      Api.get('/supervision/coverage')
        .then(function (data) {
          renderData(body, parseCoverage(data));
        }, function (err) {
          body.innerHTML = '';
          body.appendChild(UI.errorBox((err && err.message) || '覆盖率数据加载失败', load));
        });
    }
  }

  function renderData(body, cov) {
    // 销毁上一轮图表
    mounted.forEach(function (c) { if (c && c.destroy) { try { c.destroy(); } catch (e) { /* 忽略 */ } } });
    mounted = [];
    body.innerHTML = '';

    var deptPoints = groupPoints(cov.byDepartment);
    var semPoints = groupPoints(cov.bySemester);

    /* ---------- 1. 总体覆盖率 ---------- */
    var heroCard = UI.card({ title: '总体督导覆盖率' });
    var hero = document.createElement('div');
    hero.className = 'coverage-hero';

    // 大数字
    var figure = document.createElement('div');
    figure.className = 'coverage-figure';
    figure.innerHTML =
      '<div class="coverage-num">' + util.escape(cov.coverageRate.toFixed(1)) +
      '<span class="unit">%</span></div>' +
      '<div class="coverage-cap">总体覆盖率</div>';
    hero.appendChild(figure);

    // 进度环
    var ring = UI.progressRing(cov.coverageRate, {
      size: 150, label: '已督导 / 应督导'
    });
    hero.appendChild(ring.el);

    // 关键数字
    var facts = document.createElement('div');
    facts.className = 'coverage-facts';
    facts.appendChild(UI.descList([
      { label: '应督导开课记录数', value: cov.totalOfferings + ' 个' },
      { label: '已督导开课记录数', value: cov.supervisedOfferings + ' 个' },
      { label: '未覆盖开课记录数', value: cov.unsupervisedOfferings + ' 个' },
      {
        label: '覆盖率评价', raw: true,
        value: '<span class="badge badge-' + toneOf(cov.coverageRate) + '">' +
          (cov.coverageRate >= 80 ? '达标（≥80%）' :
            cov.coverageRate >= 60 ? '基本达标（60%—80%）' :
              cov.coverageRate >= 40 ? '偏低（40%—60%）' : '偏低（<40%）') + '</span>'
      }
    ]));
    hero.appendChild(facts);
    heroCard.body.appendChild(hero);
    body.appendChild(heroCard.el);

    /* ---------- 2. 按院系 ---------- */
    if (deptPoints.length) {
      var grid = document.createElement('div');
      grid.className = 'grid grid-2';

      // 按院系表格 + 进度条
      var deptCard = UI.card({ title: '按院系覆盖率' });
      deptCard.body.appendChild(UI.miniTable([
        { title: '院系', key: 'label' },
        {
          title: '应督导', align: 'right',
          render: function (v, row) { return util.num(row.total); }
        },
        {
          title: '已督导', align: 'right',
          render: function (v, row) { return util.num(row.supervised); }
        },
        {
          title: '覆盖率', align: 'right', raw: true,
          render: function (v, row) {
            return '<span class="badge badge-' + toneOf(row.rate) + '">' +
              util.escape(row.rate.toFixed(1)) + '%</span>';
          }
        }
      ], deptPoints, '暂无院系数据'));

      // 院系进度条明细
      var bars = document.createElement('div');
      bars.style.marginTop = '14px';
      deptPoints.forEach(function (p) {
        bars.appendChild(UI.progressRow(p.label, p.supervised, p.total, p.rate.toFixed(1) + '%'));
      });
      deptCard.body.appendChild(bars);
      grid.appendChild(deptCard.el);

      // 按院系条形图
      var chartCard = UI.card({ title: '按院系覆盖率（%）' });
      var chart = AJ.Charts.hBarChart(deptPoints.map(function (p) {
        return { label: p.label, value: Number(p.rate.toFixed(1)) };
      }), { height: Math.max(160, deptPoints.length * 34 + 30), max: 100, valueUnit: '%', labelWidth: 170 });
      mounted.push(chart);
      chartCard.body.appendChild(chart.el);
      grid.appendChild(chartCard.el);

      body.appendChild(grid);
    }

    /* ---------- 3. 按学期趋势 ---------- */
    if (semPoints.length) {
      var semGrid = document.createElement('div');
      semGrid.className = 'grid grid-2';

      var semCard = UI.card({ title: '按学期覆盖率趋势' });
      var semChart = AJ.Charts.lineChart(semPoints.map(function (p) {
        return { label: p.label, value: Number(p.rate.toFixed(1)) };
      }), { height: 250, yMax: 100, yUnit: '%' });
      mounted.push(semChart);
      semCard.body.appendChild(semChart.el);
      semGrid.appendChild(semCard.el);

      var semTableCard = UI.card({ title: '按学期明细' });
      semTableCard.body.appendChild(UI.miniTable([
        { title: '学期', key: 'label' },
        { title: '应督导', align: 'right', render: function (v, row) { return util.num(row.total); } },
        { title: '已督导', align: 'right', render: function (v, row) { return util.num(row.supervised); } },
        { title: '未覆盖', align: 'right', render: function (v, row) { return util.num(row.unsupervised); } },
        {
          title: '覆盖率', align: 'right', raw: true,
          render: function (v, row) {
            return '<span class="badge badge-' + toneOf(row.rate) + '">' +
              util.escape(row.rate.toFixed(1)) + '%</span>';
          }
        }
      ], semPoints, '暂无学期数据'));
      semGrid.appendChild(semTableCard.el);

      body.appendChild(semGrid);
    }

    /* ---------- 4. 无分解数据时的提示 ---------- */
    if (!deptPoints.length && !semPoints.length) {
      var tip = document.createElement('div');
      tip.innerHTML = UI.emptyState('暂无按院系 / 按学期的分解数据',
        '总体覆盖率已展示；如需院系与学期维度分解，请确认后端已返回 byDepartment / bySemester 字段');
      body.appendChild(tip);
    }

    /* ---------- 5. 口径再次落款（数据字典引用） ---------- */
    var foot = document.createElement('div');
    foot.className = 'text-sub';
    foot.style.marginTop = '4px';
    foot.textContent = '口径来源：Sprint 1 接口契约第 7 节（已写入数据字典），统计周期以当前筛选/查询条件为准，' +
      '按开课记录去重计数，同一个人次多次听课只计 1。';
    body.appendChild(foot);
  }

  function destroy() {
    mounted.forEach(function (c) { if (c && c.destroy) { try { c.destroy(); } catch (e) { /* 忽略 */ } } });
    mounted = [];
  }

  AJ.pages.coverage = {
    title: '督导覆盖率',
    roles: ['ADMIN', 'SUPERVISOR', 'DIRECTOR'],
    render: render,
    destroy: destroy
  };

})(window, document);
