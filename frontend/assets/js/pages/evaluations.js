/* ============================================================================
 * pages/evaluations.js —— 教学评价管理（契约第 6 节，史诗三，只读展示）
 *
 * 功能：
 *   · 评价明细列表：分页 + 评价类型/学期/分数区间筛选（TEACHER 由后端强制只看本人）
 *   · 学生评教汇总：GET /api/evaluations/summary → 课程、平均分、参评人次
 *   · Canvas 分数分布折线图 + 各课程平均分条形图（数据取自 summary，缺失时回退到明细数据）
 *   · 评价详情抽屉、导出 CSV
 *
 * 用到的接口：
 *   GET /api/evaluations?offeringId&type&semester&scoreMin&scoreMax&page&size
 *   GET /api/evaluations/summary
 *   GET /api/evaluations/{id}
 *   GET /api/evaluations/export
 * ========================================================================== */
(function (window, document) {
  'use strict';

  var AJ = window.AJ;
  var Api = AJ.Api;
  var UI = AJ.UI;
  var util = AJ.util;

  /** 评价类型候选值（与后端 evaluationTypes 保持一致） */
  var TYPES = ['学生评教', '督导评教', '同行评议'];
  /** 分析筛选类型：全部类型用 value=全部 传给后端 */
  var ANALYSIS_TYPES = [
    { value: '学生评教', label: '学生评教' },
    { value: '督导评教', label: '督导评教' },
    { value: '同行评议', label: '同行评议' },
    { value: '全部', label: '全部类型' }
  ];

  var mounted = [];
  /**
   * 当前渲染批次的竞态守卫。
   * 因为 loadSummary 是模块级函数（不在 render 作用域内），无法直接访问 render
   * 内的局部 guard，故在此保存一份“当前批次”的守卫，由 render() 每次刷新。
   */
  var currentGuard = function () { return function (fn) { return fn; }; };

  function asArray(res) {
    if (!res) { return []; }
    if (Object.prototype.toString.call(res) === '[object Array]') { return res; }
    return res.summary || res.records || res.list || res.rows || [];
  }

  /** 从对象中安全取数组字段 */
  function pickArray(obj, key) {
    if (!obj || Object.prototype.toString.call(obj) === '[object Array]') { return []; }
    var v = obj[key];
    return Object.prototype.toString.call(v) === '[object Array]' ? v : [];
  }

  /** 课程下拉（分析筛选） */
  function courseLoader(keyword) {
    return Api.get('/courses', { keyword: keyword || '', page: 1, size: 200 })
      .then(function (res) {
        return asArray(res).map(function (c) {
          var code = util.pick(c, ['courseCode'], '');
          var name = util.pick(c, ['courseName'], '');
          return {
            value: c.id,
            label: (code ? code + ' ' : '') + (name || ('课程 ID: ' + c.id))
          };
        });
      });
  }

  /** 教师下拉（分析筛选；教师角色隐藏） */
  function teacherLoader(keyword) {
    return Api.get('/teachers', { keyword: keyword || '', page: 1, size: 200 })
      .then(function (res) {
        return asArray(res).map(function (t) {
          var name = util.pick(t, ['name', 'realName'], '未命名');
          var dept = util.pick(t, ['departmentName', 'departmentCode'], '');
          return { value: t.id, label: name + (dept ? '（' + dept + '）' : '') };
        });
      });
  }

  /** 取评价分数（兼容 score / totalScore / avgScore） */
  function scoreOf(row) {
    var v = util.pick(row, ['score', 'totalScore', 'evalScore', 'value'], null);
    return v === null ? null : Number(v);
  }

  /**
   * 把明细行按固定分数区间聚合成分布数据（用于折线图）
   * 区间：<60、60-69、70-79、80-89、90-100
   */
  function scoreDistribution(rows) {
    var bins = [
      { label: '60分以下', min: -Infinity, max: 60, value: 0 },
      { label: '60-69分', min: 60, max: 70, value: 0 },
      { label: '70-79分', min: 70, max: 80, value: 0 },
      { label: '80-89分', min: 80, max: 90, value: 0 },
      { label: '90-100分', min: 90, max: Infinity, value: 0 }
    ];
    rows.forEach(function (r) {
      var s = scoreOf(r);
      if (s === null || isNaN(s)) { return; }
      for (var i = 0; i < bins.length; i++) {
        if (s >= bins[i].min && s < bins[i].max) { bins[i].value++; return; }
      }
    });
    return bins;
  }

  /** 从 summary 返回里解析“课程 / 平均分 / 参评人次”三元组 */
  function parseSummary(data) {
    var rows = asArray(data);
    return rows.map(function (r) {
      return {
        raw: r,
        courseName: util.pick(r, ['courseName', 'name', 'courseCode'], '未知课程'),
        courseCode: util.pick(r, ['courseCode'], ''),
        avgScore: util.toNumber(util.pick(r, ['avgScore', 'averageScore', 'score', 'meanScore'], 0)),
        count: util.toNumber(util.pick(r, ['count', 'studentCount', 'participantCount',
          'evaluationCount', 'total', 'num'], 0)),
        maxScore: util.pick(r, ['maxScore'], null),
        minScore: util.pick(r, ['minScore'], null),
        semester: util.pick(r, ['semester'], ''),
        teacherName: util.pick(r, ['teacherName'], '')
      };
    });
  }

  /* ==================================================================
   * 1. 详情抽屉
   * ================================================================ */
  function openDetail(id) {
    var d = UI.drawer({
      title: '评价详情',
      bodyHtml: UI.loadingBlock('正在加载评价详情…')
    });

    Api.get('/evaluations/' + encodeURIComponent(id))
      .then(guard(function (e) {
        d.body.innerHTML = '';
        if (!e) {
          d.body.appendChild(UI.emptyStateEl('评价记录不存在', '该记录可能已被删除'));
          return;
        }
        var s = scoreOf(e);
        d.body.appendChild(UI.descList([
          { label: '评价类型', value: util.pick(e, ['type', 'evaluationType'], '') },
          {
            label: '评价分数', raw: true,
            value: s === null || isNaN(s)
              ? '—'
              : '<span class="text-strong" style="font-size:16px">' + util.escape(s.toFixed(1)) + '</span> 分'
          },
          { label: '课程', value: util.pick(e, ['courseName', 'courseCode'], '') },
          { label: '教学班', value: util.pick(e, ['className'], '') },
          { label: '授课教师', value: util.pick(e, ['teacherName'], '') },
          { label: '学期', value: util.pick(e, ['semester'], '') },
          { label: '参评人次', value: util.pick(e, ['participantCount', 'count', 'studentCount'], '') },
          { label: '评价人', value: util.pick(e, ['evaluatorName', 'evaluator', 'userName'], '匿名') },
          { label: '评价时间', value: util.pick(e, ['createdAt', 'createTime', 'evaluationTime'], '') }
        ]));

        d.body.appendChild(UI.descSection('评价意见',
          UI.descText(util.pick(e, ['comment', 'content', 'opinion', 'remark'], ''))));

        // 分项得分（如教学态度、内容组织、互动等）
        var items = util.pick(e, ['items', 'details', 'indicators'], null);
        if (Object.prototype.toString.call(items) === '[object Array]' && items.length) {
          d.body.appendChild(UI.descSection('分项得分', UI.miniTable([
            { title: '评价项', key: 'itemName',
              render: function (v, row) { return util.pick(row, ['itemName', 'name', 'indicator'], ''); } },
            { title: '得分', align: 'right',
              render: function (v, row) { return util.pick(row, ['score', 'value'], '—'); } },
            { title: '满分', align: 'right',
              render: function (v, row) { return util.pick(row, ['maxScore', 'fullScore'], '—'); } }
          ], items, '暂无分项得分')));
        }
      }), guard(function (err) {
        d.body.innerHTML = '';
        d.body.appendChild(UI.errorBox((err && err.message) || '评价详情加载失败'));
      }));
  }

  /* ==================================================================
   * 2. 页面渲染
   * ================================================================ */
  function render(container) {
    var guard = AJ.Router.guard();
    currentGuard = guard;
    var role = Api.roleCode();
    var isTeacher = role === 'TEACHER';
    mounted = [];

    var head = document.createElement('div');
    head.className = 'page-head';
    head.innerHTML =
      '<h2 class="page-title">教学评价管理</h2>' +
      '<p class="page-desc">' +
      (isTeacher
        ? '当前角色为教师，仅显示本人所授课程的评价数据（只读）。'
        : '展示学生评教、同行评教与督导评教结果，含课程平均分、参评人次与分数分布（只读）。') +
      '</p>';

    var toolbar = document.createElement('div');
    toolbar.className = 'page-toolbar';
    var exportBtn = document.createElement('button');
    exportBtn.type = 'button';
    exportBtn.className = 'btn';
    exportBtn.textContent = '导出明细 CSV';
    exportBtn.addEventListener('click', function () {
      exportBtn.disabled = true;
      exportBtn.textContent = '导出中…';
      Api.exportCsv('/evaluations/export', safeQuery(tbl), '教学评价列表.csv')
        .then(function (name) { UI.toast('已导出：' + name, 'success'); },
          function (err) { UI.toast((err && err.message) || '导出失败', 'error'); })
        .then(function () {
          exportBtn.disabled = false;
          exportBtn.textContent = '导出明细 CSV';
        });
    });
    toolbar.appendChild(exportBtn);

    var analysisExportBtn = document.createElement('button');
    analysisExportBtn.type = 'button';
    analysisExportBtn.className = 'btn btn-primary';
    analysisExportBtn.textContent = '导出分析 CSV';
    analysisExportBtn.addEventListener('click', function () {
      analysisExportBtn.disabled = true;
      analysisExportBtn.textContent = '导出中…';
      Api.exportCsv('/evaluations/summary/export', buildAnalysisQuery(), '评教分析.csv')
        .then(function (name) { UI.toast('已导出：' + name, 'success'); },
          function (err) { UI.toast((err && err.message) || '导出失败', 'error'); })
        .then(function () {
          analysisExportBtn.disabled = false;
          analysisExportBtn.textContent = '导出分析 CSV';
        });
    });
    toolbar.appendChild(analysisExportBtn);
    head.appendChild(toolbar);
    container.appendChild(head);

    // —— 分析筛选：学期 / 类型 / 课程 / 教师 ——
    var analysisBar = document.createElement('div');
    container.appendChild(analysisBar);
    var analysisFilters = UI.filterBar([
      { name: 'semester', label: '学期', placeholder: '如 2025-2026-1' },
      {
        name: 'evaluationType', label: '评价类型', type: 'select',
        options: ANALYSIS_TYPES, placeholder: '学生评教（默认）'
      }
    ], {
      onSearch: function () { reloadAnalysis(); },
      onReset: function () { resetAnalysis(); }
    });
    analysisBar.appendChild(analysisFilters.el);

    var courseFilter = UI.remoteSelect({
      name: 'courseId', label: '课程', loader: courseLoader, placeholder: '全部课程'
    });
    var teacherFilter = isTeacher ? null : UI.remoteSelect({
      name: 'teacherId', label: '授课教师', loader: teacherLoader, placeholder: '全部教师'
    });
    [courseFilter, teacherFilter].forEach(function (sel) {
      if (!sel) { return; }
      var input = sel.querySelector('input');
      if (input) { input.style.display = 'none'; }
      var label = sel.querySelector('.form-label');
      if (label) {
        label.style.fontSize = '12px';
        label.style.color = '#6b7280';
      }
      sel.querySelector('select').addEventListener('change', reloadAnalysis);
      analysisFilters.el.insertBefore(sel, analysisFilters.el.querySelector('.filter-actions'));
    });

    // —— 汇总区（统计卡片 + 图表）——
    var summaryCard = UI.card({ title: '评教分析（按课程 / 学期 / 教师）' });
    summaryCard.body.innerHTML = UI.loadingBlock('正在加载评教汇总…');
    container.appendChild(summaryCard.el);

    // —— 明细表 ——
    var listCard = UI.card({ title: '评价明细' });
    container.appendChild(listCard.el);

    var filters = UI.filterBar([
      { name: 'evaluationType', label: '评价类型', type: 'select', options: TYPES, placeholder: '全部类型' },
      { name: 'semester', label: '学期', placeholder: '如 2026-春季学期' },
      { name: 'scoreMin', label: '最低分', type: 'number', placeholder: '0' },
      { name: 'scoreMax', label: '最高分', type: 'number', placeholder: '100' }
    ], {
      onSearch: function (values) { tbl.setQuery(values); }
    });
    listCard.body.appendChild(filters.el);

    var tbl = UI.table({
      columns: [
        {
          title: '评价类型', key: 'type', width: '110px',
          render: function (v, row) { return util.pick(row, ['type', 'evaluationType'], ''); }
        },
        {
          title: '课程', key: 'courseName',
          render: function (v, row) { return util.pick(row, ['courseName', 'courseCode'], ''); }
        },
        { title: '教学班', key: 'className', width: '130px' },
        { title: '授课教师', key: 'teacherName', width: '100px' },
        { title: '学期', key: 'semester', width: '120px' },
        {
          title: '分数', key: 'score', width: '80px', align: 'right', raw: true,
          render: function (v, row) {
            var s = scoreOf(row);
            if (s === null || isNaN(s)) { return '—'; }
            var tone = s >= 90 ? 'green' : s >= 80 ? 'blue' : s >= 60 ? 'orange' : 'red';
            return '<span class="badge badge-' + tone + '">' + util.escape(s.toFixed(1)) + '</span>';
          }
        },
        {
          title: '参评人次', key: 'participantCount', width: '92px', align: 'right',
          render: function (v, row) { return util.pick(row, ['participantCount', 'count', 'studentCount'], '—'); }
        },
        {
          title: '评价时间', key: 'createdAt', width: '150px',
          render: function (v, row) { return util.pick(row, ['createdAt', 'createTime', 'evaluationTime'], ''); }
        },
        {
          title: '操作', width: '76px', className: 'actions', raw: true,
          render: function (v, row) {
            return '<button class="btn-link" data-act="detail" data-id="' + util.escape(row.id) + '">详情</button>';
          }
        }
      ],
      loader: function (query) { return Api.get('/evaluations', query); },
      emptyTitle: '暂无评价数据',
      emptyDesc: '可调整筛选条件；若整个系统无评价数据，请确认后端已导入样例评价记录'
    });
    listCard.body.appendChild(tbl.el);

    // 行内操作
    tbl.el.addEventListener('click', function (e) {
      var btn = e.target && e.target.closest ? e.target.closest('button[data-act]') : null;
      if (!btn) { return; }
      if (btn.getAttribute('data-act') === 'detail') {
        openDetail(btn.getAttribute('data-id'));
      }
    });

    /** 读取当前分析筛选条件 */
    function buildAnalysisQuery() {
      var q = analysisFilters.getValues() || {};
      var out = {};
      if (q.semester) { out.semester = q.semester; }
      if (q.evaluationType) { out.evaluationType = q.evaluationType; }
      var cid = courseFilter.querySelector('select').value;
      if (cid) { out.courseId = cid; }
      if (teacherFilter) {
        var tid = teacherFilter.querySelector('select').value;
        if (tid) { out.teacherId = tid; }
      }
      return out;
    }

    function reloadAnalysis() {
      loadSummary(summaryCard, tbl, buildAnalysisQuery());
    }

    function resetAnalysis() {
      analysisFilters.setValues({ semester: '', evaluationType: '' });
      courseFilter.querySelector('select').value = '';
      if (teacherFilter) { teacherFilter.querySelector('select').value = ''; }
      reloadAnalysis();
    }

    // 加载汇总（默认学生评教）
    loadSummary(summaryCard, tbl, {});

    tbl.reload();
  }

  /* ==================================================================
   * 3. 汇总区渲染
   * ================================================================ */
  function loadSummary(card, tbl, query) {
    var guard = currentGuard;
    Api.get('/evaluations/summary', query || {})
      .then(guard(function (data) {
        renderSummary(card, data, tbl);
      }), guard(function (err) {
        card.body.innerHTML = '';
        card.body.appendChild(UI.errorBox((err && err.message) || '评教汇总加载失败', function () {
          card.body.innerHTML = UI.loadingBlock('正在加载评教汇总…');
          loadSummary(card, tbl, query);
        }));
      }));
  }

  function renderSummary(card, data, tbl) {
    // 清理旧图表
    mounted.forEach(function (c) { if (c && c.destroy) { try { c.destroy(); } catch (e) { /* 忽略 */ } } });
    mounted = [];
    card.body.innerHTML = '';

    var summaries = parseSummary(data);

    // 后端返回对象形式：{summary, trend, teacherSummary, distribution, ...}
    var isObj = data && typeof data === 'object' &&
      Object.prototype.toString.call(data) !== '[object Array]';
    var trend = isObj ? pickArray(data, 'trend') : [];
    var teacherSummary = isObj ? pickArray(data, 'teacherSummary') : [];
    var distFromBackend = isObj
      ? util.pick(data, ['scoreDistribution', 'scoreDist', 'distribution'], null)
      : null;

    var computedTotal = 0;
    var weighted = 0;
    summaries.forEach(function (s) {
      computedTotal += s.count || 0;
      weighted += (s.count || 0) * (s.avgScore || 0);
    });
    var computedAvg = computedTotal > 0 ? (weighted / computedTotal) : (
      summaries.length ? summaries.reduce(function (a, s) { return a + s.avgScore; }, 0) / summaries.length : 0
    );
    var totalCount = isObj && util.pick(data, ['totalEvaluations'], null) !== null
      ? util.toNumber(util.pick(data, ['totalEvaluations'], 0)) : computedTotal;
    var overallAvg = isObj && util.pick(data, ['overallAvgScore'], null) !== null
      ? util.toNumber(util.pick(data, ['overallAvgScore'], 0)) : computedAvg;
    var courseCount = isObj
      ? util.toNumber(util.pick(data, ['courseCount'], summaries.length)) : summaries.length;

    var best = null;
    summaries.forEach(function (s) {
      if (!best || s.avgScore > best.avgScore) { best = s; }
    });

    // —— 统计卡片 ——
    card.body.appendChild(UI.statGrid([
      { label: '纳入汇总课程数', value: courseCount, unit: '门', icon: '课', tone: '' },
      { label: '参评总人次', value: totalCount, unit: '人次', icon: '人', tone: 'success' },
      {
        label: '总体平均分', value: summaries.length ? overallAvg.toFixed(2) : '—', unit: '分', icon: '分',
        tone: overallAvg >= 90 ? 'success' : overallAvg >= 80 ? '' : 'warning'
      },
      {
        label: '最高平均分课程', value: best ? best.avgScore.toFixed(2) : '—', unit: '分', icon: '优',
        sub: best ? best.courseName : '', tone: 'success'
      }
    ], 4));

    if (!summaries.length) {
      var empty = document.createElement('div');
      empty.innerHTML = UI.emptyState('暂无评教汇总数据', '可调整上方筛选条件，或先确认是否存在评价记录');
      card.body.appendChild(empty);
      return;
    }

    // —— 学期趋势 ——
    if (trend.length) {
      var trendCard = UI.card({ title: '按学期评教趋势' });
      if (trend.length > 1) {
        var trendChart = AJ.Charts.lineChart(trend.map(function (t) {
          return {
            label: util.pick(t, ['semester'], ''),
            value: util.toNumber(util.pick(t, ['avgScore'], 0))
          };
        }), { height: 250, yMax: 100, yUnit: ' 分' });
        mounted.push(trendChart);
        trendCard.body.appendChild(trendChart.el);
      } else {
        trendCard.body.appendChild(UI.note('当前筛选条件下只有一个学期，暂无法形成趋势对比。'));
      }
      card.body.appendChild(trendCard.el);
    }

    // —— 图表区 ——
    var grid = document.createElement('div');
    grid.className = 'grid grid-2';
    grid.style.marginTop = '16px';

    // 各课程平均分（条形图，按分数降序，最多 12 门）
    var sorted = summaries.slice().sort(function (a, b) { return b.avgScore - a.avgScore; }).slice(0, 12);
    var avgCard = UI.card({ title: '各课程评教平均分（前 12 门）' });
    var avgChart = AJ.Charts.hBarChart(sorted.map(function (s) {
      return { label: s.courseName, value: Number(s.avgScore.toFixed(2)) };
    }), { height: Math.max(150, sorted.length * 32 + 30), max: 100, valueUnit: ' 分', labelWidth: 170 });
    mounted.push(avgChart);
    avgCard.body.appendChild(avgChart.el);
    grid.appendChild(avgCard.el);

    // 分数分布（折线图）
    var distCard = UI.card({ title: '评教分数分布' });
    var distData = null;
    if (Object.prototype.toString.call(distFromBackend) === '[object Array]' && distFromBackend.length) {
      distData = distFromBackend;
    } else {
      // 从明细数据（当前页 + 已加载）统计；若明细为空则按平均分生成概览
      var records = tbl ? tbl.getRecords() : [];
      if (records.length) {
        distData = scoreDistribution(records);
      } else {
        distData = scoreDistribution(summaries.map(function (s) { return { score: s.avgScore }; }));
      }
    }
    var distChart = AJ.Charts.lineChart(distData, { height: 250, yUnit: '条数' });
    mounted.push(distChart);
    distCard.body.appendChild(distChart.el);

    var hint = document.createElement('div');
    hint.className = 'text-sub';
    hint.style.marginTop = '8px';
    hint.textContent = '说明：分数区间按 <60、60-69、70-79、80-89、90-100 划分；' +
      '若后端未返回分布数据，则基于当前筛选条件下的明细记录实时统计。';
    distCard.body.appendChild(hint);
    grid.appendChild(distCard.el);

    card.body.appendChild(grid);

    // —— 按教师汇总 ——
    if (teacherSummary.length) {
      var teacherCard = UI.card({ title: '按教师汇总' });
      teacherCard.body.appendChild(UI.miniTable([
        { title: '教师', key: 'teacherName' },
        {
          title: '课程数', align: 'right',
          render: function (v, row) { return util.num(util.pick(row, ['courseCount'], 0)); }
        },
        {
          title: '参评人次', align: 'right',
          render: function (v, row) { return util.num(util.pick(row, ['evaluationCount'], 0)); }
        },
        {
          title: '平均分', align: 'right', raw: true,
          render: function (v, row) {
            var s = util.toNumber(util.pick(row, ['avgScore'], 0));
            var tone = s >= 90 ? 'green' : s >= 80 ? 'blue' : s >= 60 ? 'orange' : 'red';
            return '<span class="badge badge-' + tone + '">' + util.escape(s.toFixed(1)) + '</span>';
          }
        }
      ], teacherSummary, '暂无教师汇总数据'));

      var teacherChart = AJ.Charts.hBarChart(teacherSummary.slice(0, 12).map(function (t) {
        return {
          label: util.pick(t, ['teacherName'], '未命名'),
          value: util.toNumber(util.pick(t, ['avgScore'], 0))
        };
      }), { height: Math.max(150, Math.min(teacherSummary.length, 12) * 32 + 30),
        max: 100, valueUnit: ' 分', labelWidth: 140 });
      mounted.push(teacherChart);
      teacherCard.body.appendChild(teacherChart.el);
      card.body.appendChild(teacherCard.el);
    }

    // —— 汇总明细表 ——
    var tableWrap = UI.card({ title: '按课程汇总明细' });
    tableWrap.body.appendChild(UI.miniTable([
      { title: '课程', key: 'courseName' },
      { title: '学期', key: 'semester' },
      { title: '授课教师', key: 'teacherName' },
      {
        title: '平均分', key: 'avgScore', align: 'right', raw: true,
        render: function (v, row) {
          var s = Number(v || 0);
          var tone = s >= 90 ? 'green' : s >= 80 ? 'blue' : s >= 60 ? 'orange' : 'red';
          return '<span class="badge badge-' + tone + '">' + util.escape(s.toFixed(2)) + '</span>';
        }
      },
      {
        title: '最高分', key: 'maxScore', align: 'right',
        render: function (v) { return v === null || v === undefined ? '—' : Number(v).toFixed(1); }
      },
      {
        title: '最低分', key: 'minScore', align: 'right',
        render: function (v) { return v === null || v === undefined ? '—' : Number(v).toFixed(1); }
      },
      {
        title: '参评人次', key: 'count', align: 'right',
        render: function (v) { return util.num(v); }
      }
    ], summaries, '暂无汇总数据'));
    card.body.appendChild(tableWrap.el);
  }

  function safeQuery(tbl) {
    var q = tbl.getQuery ? tbl.getQuery() : {};
    var out = {};
    ['evaluationType', 'semester', 'scoreMin', 'scoreMax'].forEach(function (k) {
      if (q[k]) { out[k] = q[k]; }
    });
    return out;
  }

  function destroy() {
    mounted.forEach(function (c) { if (c && c.destroy) { try { c.destroy(); } catch (e) { /* 忽略 */ } } });
    mounted = [];
  }

  AJ.pages.evaluations = {
    title: '教学评价管理',
    roles: ['ADMIN', 'DIRECTOR', 'SUPERVISOR', 'TEACHER'],
    render: render,
    destroy: destroy
  };

})(window, document);
