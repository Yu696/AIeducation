/* ============================================================================
 * pages/dashboard.js —— 工作台首页
 *
 * 按当前登录角色调用不同工作台接口（契约第 8 节）：
 *   ADMIN      → GET /api/dashboard/admin        账号数、角色分布、操作日志摘要
 *   DIRECTOR   → GET /api/dashboard/director     课程/开课/教师/资源/覆盖率/评教概览 + 教师授课分布
 *   TEACHER    → GET /api/dashboard/teacher      我的课程数、教学班数、学生人次、资源数、评教均分
 *   SUPERVISOR → GET /api/dashboard/supervisor   开课清单概览、覆盖率、待安排听评课清单
 *   兜底        → GET /api/dashboard/summary
 *
 * 渲染策略：统计卡片对任意数值字段自适应生成，图表从数组型字段自适应选取，
 * 因此后端字段名与预期不完全一致时页面依然可用（不会出现空白页）。
 * ========================================================================== */
(function (window, document) {
  'use strict';

  var AJ = window.AJ;
  var Api = AJ.Api;
  var UI = AJ.UI;
  var util = AJ.util;
  var Charts = AJ.Charts;

  /** 图表实例登记表，页面切换时统一销毁 */
  var mounted = [];
  /** 页面内所有异步请求的序号，用于丢弃过期响应 */
  var reqSeq = 0;
  /**
   * 当前渲染批次的竞态守卫。
   * renderData / loadFallbackChart 是模块级函数，无法访问 render 内的局部
   * guard，因此把“当前批次”的守卫保存在模块级变量中，由 render() 刷新。
   */
  var currentGuard = function () { return function (fn) { return fn; }; };

  function trackChart(chart) {
    if (chart) { mounted.push(chart); }
    return chart;
  }

  /* ------------------------------------------------------------------
   * 字段中文名映射（后端字段名 → 界面标签）
   * ---------------------------------------------------------------- */
  var LABELS = {
    // 通用规模
    totalCourses: '课程总数', courseCount: '课程总数', courses: '课程总数',
    totalOfferings: '开课总数', offeringCount: '开课总数', offerings: '开课总数',
    totalTeachers: '教师总数', teacherCount: '教师总数', teachers: '教师总数',
    totalResources: '资源总数', resourceCount: '资源总数', resources: '资源总数',
    totalStudents: '学生总人次', studentCount: '学生总人次', studentTotal: '学生总人次',
    totalEvaluations: '评价记录数', evaluationCount: '评价记录数',

    // 覆盖率
    coverageRate: '督导覆盖率', supervisionCoverageRate: '督导覆盖率',
    totalOfferingsInCoverage: '统计范围开课数', supervisedOfferings: '已督导开课数',
    unsupervisedOfferings: '未督导开课数', pendingSupervisions: '待安排听评课',

    // 评教
    avgScore: '评教平均分', averageScore: '评教平均分', evaluationAvgScore: '评教平均分',
    myAvgScore: '我的评教均分', scoreAvg: '评教平均分',

    // 教师视角
    myCourses: '我的课程数', myCourseCount: '我的课程数',
    myOfferings: '我的教学班数', myOfferingCount: '我的教学班数',
    myStudents: '我的学生人次', myStudentCount: '我的学生人次',
    myResources: '我的资源数', myResourceCount: '我的资源数',
    myAvg: '我的评教均分',

    // 管理员视角
    userCount: '账号总数', totalUsers: '账号总数', enabledUsers: '启用账号',
    disabledUsers: '停用账号', roleCount: '角色数', auditLogCount: '操作日志条数',
    todayAuditCount: '今日操作数',

    // 主任视角
    departmentCount: '院系数', crossDepartment: '跨院可见',
    semester: '当前学期'
  };

  /** 值本身是“比率”的字段，需要按百分比展示 */
  var PERCENT_KEYS = /(rate|Rate|percent|Percent|ratio)$/;

  /** 已知的“数组型字段 → 图表类型”映射 */
  var ARRAY_HINTS = [
    { keys: ['teacherWorkload', 'teacherWorkloads', 'teacherDistribution', 'teacherCourseDistribution', 'byTeacher'],
      title: '教师授课分布', type: 'bar' },
    { keys: ['byDepartment', 'departmentCoverage', 'coverageByDepartment', 'departmentStats'],
      title: '按院系统计', type: 'hbar' },
    { keys: ['bySemester', 'semesterTrend', 'coverageBySemester', 'semesterStats'],
      title: '按学期趋势', type: 'line' },
    { keys: ['scoreDistribution', 'scoreDist', 'scoreRanges'],
      title: '评教分数分布', type: 'line' },
    { keys: ['roleDistribution', 'roleStats', 'roles'],
      title: '角色分布', type: 'donut' },
    { keys: ['courseTypeDistribution', 'courseTypes'],
      title: '课程类型分布', type: 'donut' },
    { keys: ['pendingList', 'pendingSupervisionList', 'toArrangeSupervisions'],
      title: '待安排听评课清单', type: 'list' },
    { keys: ['recentAuditLogs', 'auditLogs'],
      title: '最近操作日志', type: 'list' }
  ];

  /** 生成一个统计卡片描述 */
  function statOf(key, value, index) {
    var label = LABELS[key] || humanize(key);
    var item = { label: label, value: value };
    if (typeof value === 'number' || /^-?\d+(\.\d+)?$/.test(String(value))) {
      var n = Number(value);
      if (PERCENT_KEYS.test(key)) {
        item.value = n.toFixed(1);
        item.unit = '%';
        item.tone = n >= 80 ? 'success' : n >= 50 ? '' : 'warning';
      } else if (/score|Score|avg|Avg/.test(key)) {
        item.value = n.toFixed(2);
      }
      item.icon = label.charAt(0);
    }
    // 前几张卡片轮换配色，视觉上更易区分
    if (!item.tone) {
      item.tone = ['', 'success', 'warning'][index % 3];
    }
    return item;
  }

  /** 把 camelCase 字段名转成可读中文兜底标签（未知字段也能看懂） */
  function humanize(key) {
    var s = String(key)
      .replace(/([A-Z])/g, ' $1')
      .replace(/^./, function (c) { return c.toUpperCase(); });
    return s.trim();
  }

  /** 判断值是否为“可展示的标量” */
  function isScalar(v) {
    return v === null || v === undefined ||
      typeof v === 'string' || typeof v === 'number' || typeof v === 'boolean';
  }

  /**
   * 从工作台返回对象里挑出标量字段做统计卡片
   * @param {object} data 接口 data
   * @param {number} max 最多显示几张
   */
  function extractStats(data, max) {
    var cards = [];
    if (!data || typeof data !== 'object') { return cards; }

    Object.keys(data).forEach(function (k) {
      if (cards.length >= (max || 8)) { return; }
      var v = data[k];
      if (!isScalar(v)) { return; }
      if (v === null || v === '') { return; }
      if (typeof v === 'boolean') { return; }
      // 纯文本类字段不适合做卡片
      if (typeof v === 'string' && !/^\d+(\.\d+)?%?$/.test(v)) { return; }
      cards.push(statOf(k, v, cards.length));
    });

    return cards;
  }

  /** 从对象里取第一个命中的数组字段 */
  function pickArray(data, keys) {
    if (!data) { return null; }
    for (var i = 0; i < keys.length; i++) {
      var v = data[keys[i]];
      if (Object.prototype.toString.call(v) === '[object Array]') { return v; }
    }
    return null;
  }

  /** 数组元素是否为“标签—数值”结构（可绘图） */
  function isLabelValueArray(arr) {
    if (!arr || !arr.length) { return false; }
    var first = arr[0];
    if (Object.prototype.toString.call(first) === '[object Array]') { return true; }
    if (first && typeof first === 'object') {
      var hasLabel = ['label', 'name', 'key', 'departmentName', 'deptName', 'teacherName',
        'courseName', 'semester', 'range', 'type', 'roleName'].some(function (k) {
        return first[k] !== undefined;
      });
      var hasValue = ['value', 'count', 'total', 'num', 'amount', 'rate', 'percent'].some(function (k) {
        return first[k] !== undefined;
      });
      return hasLabel && hasValue;
    }
    return typeof first === 'number';
  }

  /** 数组元素是否为可列表展示的对象（非绘图） */
  function isRecordArray(arr) {
    if (!arr || !arr.length) { return false; }
    var first = arr[0];
    return first && typeof first === 'object' && Object.prototype.toString.call(first) !== '[object Array]';
  }

  /**
   * 渲染“对象数组”为简单列表（待安排听评课、操作日志等）
   */
  function recordListCard(titleText, arr, limit) {
    var columns = Object.keys(arr[0] || {}).slice(0, 6);
    var header = columns.map(function (k) { return { title: LABELS[k] || humanize(k), key: k }; });
    var c = UI.card({ title: titleText });
    c.body.appendChild(UI.miniTable(header, arr.slice(0, limit || 10), '暂无记录'));
    return c.el;
  }

  /* ------------------------------------------------------------------
   * 页面骨架
   * ---------------------------------------------------------------- */
  function render(container) {
    var mySeq = ++reqSeq;
    // 竞态守卫：路由若已切换，异步回调直接丢弃，避免覆盖新页面内容
    var guard = AJ.Router.guard();
    currentGuard = guard;
    mounted = [];

    var role = Api.roleCode();
    var user = Api.getCurrentUser() || {};
    var displayName = user.realName || user.nickname || user.username || '用户';

    // 页头
    var head = document.createElement('div');
    head.className = 'page-head';
    head.innerHTML =
      '<h2 class="page-title">' + util.escape(displayName) + '，您好</h2>' +
      '<p class="page-desc">当前角色：' + util.escape(AJ.ROLE_NAMES[role] || role || '未知') +
      '　数据范围：' + util.escape(AJ.ROLE_SCOPES[role] || '—') +
      '　' + util.escape(util.now().slice(0, 10)) + '</p>';
    container.appendChild(head);

    var body = document.createElement('div');
    body.innerHTML = UI.loadingBlock('正在加载工作台数据…');
    container.appendChild(body);

    // 按角色决定接口与标题
    var endpoint = '/dashboard/summary';
    var title = '工作台概览';
    if (role === 'ADMIN') { endpoint = '/dashboard/admin'; title = '管理员工作台'; }
    else if (role === 'DIRECTOR') { endpoint = '/dashboard/director'; title = '教研室主任工作台'; }
    else if (role === 'TEACHER') { endpoint = '/dashboard/teacher'; title = '教师工作台'; }
    else if (role === 'SUPERVISOR') { endpoint = '/dashboard/supervisor'; title = '教学督导工作台'; }

    load(endpoint);

    function load(path) {
      Api.get(path)
        .then(guard(function (data) {
          if (mySeq !== reqSeq) { return; }
          renderData(body, data, title);
        }), guard(function (err) {
          if (mySeq !== reqSeq) { return; }
          // 角色专属接口不可用时退回通用概览，尽量不让工作台空白
          if (path !== '/dashboard/summary' && err && err.code !== 401 && err.code !== 403) {
            Api.get('/dashboard/summary').then(guard(function (data) {
              if (mySeq !== reqSeq) { return; }
              renderData(body, data, '工作台概览');
            }), guard(function (err2) {
              if (mySeq !== reqSeq) { return; }
              body.innerHTML = '';
              body.appendChild(UI.errorBox((err2 && err2.message) || '工作台数据加载失败', function () {
                body.innerHTML = UI.loadingBlock('正在加载工作台数据…');
                load(endpoint);
              }));
            }));
            return;
          }
          body.innerHTML = '';
          body.appendChild(UI.errorBox((err && err.message) || '工作台数据加载失败', function () {
            body.innerHTML = UI.loadingBlock('正在加载工作台数据…');
            load(endpoint);
          }));
        }));
    }
  }

  /* ------------------------------------------------------------------
   * 数据渲染
   * ---------------------------------------------------------------- */
  function renderData(body, data, title) {
    body.innerHTML = '';

    // 1) 统计卡片
    var cards = extractStats(data, 8);
    if (cards.length) {
      body.appendChild(UI.statGrid(cards, 4));
    }

    // 2) 图表区
    var chartCards = [];
    var used = {};

    ARRAY_HINTS.forEach(function (hint) {
      if (chartCards.length >= 3) { return; }
      var arr = pickArray(data, hint.keys);
      if (!arr || !arr.length) { return; }
      // 记录已使用字段名，避免后面重复渲染
      hint.keys.forEach(function (k) { used[k] = true; });

      if (hint.type === 'list') {
        if (isRecordArray(arr)) { chartCards.push(recordListCard(hint.title, arr)); }
        return;
      }
      if (!isLabelValueArray(arr)) {
        if (isRecordArray(arr)) { chartCards.push(recordListCard(hint.title, arr)); }
        return;
      }

      var grid = document.createElement('div');
      grid.className = 'grid grid-2';
      var c = UI.card({ title: hint.title });
      var chart;
      if (hint.type === 'bar') {
        chart = Charts.barChart(arr, { height: 250 });
      } else if (hint.type === 'hbar') {
        chart = Charts.hBarChart(arr, { height: 250, valueUnit: '' });
      } else if (hint.type === 'donut') {
        chart = Charts.donutChart(arr, { height: 250 });
      } else {
        chart = Charts.lineChart(arr, { height: 250 });
      }
      trackChart(chart);
      c.body.appendChild(chart.el);
      chartCards.push(c.el);
    });

    // 3) 未命中预设的数组字段：自动识别后补图，保证至少有 2 个图表
    if (chartCards.length < 3 && data && typeof data === 'object') {
      Object.keys(data).forEach(function (k) {
        if (chartCards.length >= 3) { return; }
        if (used[k]) { return; }
        var arr = data[k];
        if (Object.prototype.toString.call(arr) !== '[object Array]') { return; }
        if (!arr.length || !isLabelValueArray(arr) || !isRecordArray(arr)) { return; }
        // 需要一个可读标题
        var titleText = LABELS[k] || humanize(k);
        var c = UI.card({ title: titleText });
        var chart = Charts.barChart(arr, { height: 250 });
        trackChart(chart);
        c.body.appendChild(chart.el);
        chartCards.push(c.el);
      });
    }

    if (chartCards.length) {
      var wrap = document.createElement('div');
      wrap.className = 'grid grid-2';
      chartCards.forEach(function (el) { wrap.appendChild(el); });
      body.appendChild(wrap);
    }

    // 4) 兜底图表：若接口只返回标量（例如退回 /dashboard/summary），
    //    则用课程库数据在前端聚合出「课程类型分布」，保证工作台至少有图表可看
    if (!chartCards.length) {
      loadFallbackChart(body, currentGuard);
    }

    // 5) 完全无数据时的空状态
    if (!cards.length && !chartCards.length) {
      body.innerHTML = UI.emptyState('暂无工作台数据',
        title + ' 未返回可展示的统计数据，请确认后端接口 ' + '数据已初始化');
    }

    // 5) 快捷入口（按角色给出常用页面）
    body.appendChild(quickLinks());
  }

  /**
   * 兜底图表：拉取课程库并按课程类型聚合，画一张柱状图。
   * 仅在主接口未返回任何数组型统计数据时使用（例如 /dashboard/summary）。
   * 取不到数据时静默跳过，不影响页面其它内容。
   */
  function loadFallbackChart(body, guard) {
    Api.get('/courses', { page: 1, size: 200 }).then(guard(function (res) {
      var rows = res && (res.records || res.list || res.rows);
      if (Object.prototype.toString.call(res) === '[object Array]') { rows = res; }
      if (!rows || !rows.length) { return; }

      var byType = {};
      rows.forEach(function (c) {
        var t = util.pick(c, ['courseType', 'type'], '未分类');
        byType[t] = (byType[t] || 0) + 1;
      });
      var data = Object.keys(byType).map(function (k) {
        return { label: k, value: byType[k] };
      });
      if (!data.length) { return; }

      var c = UI.card({ title: '课程类型分布（按课程库聚合）' });
      var chart = Charts.barChart(data, { height: 250, valueUnit: '门' });
      trackChart(chart);
      c.body.appendChild(chart.el);

      var wrap = document.createElement('div');
      wrap.className = 'grid grid-2';
      wrap.appendChild(c.el);
      body.appendChild(wrap);
    }), guard(function () { /* 兜底失败时静默忽略 */ }));
  }

  /** 底部快捷入口卡片 */
  function quickLinks() {
    var role = Api.roleCode();
    var links = [];
    if (role === 'ADMIN') {
      links = [['/courses', '课程信息管理'], ['/offerings', '开课与授课管理'],
        ['/admin', '用户与权限管理'], ['/coverage', '督导覆盖率']];
    } else if (role === 'DIRECTOR') {
      links = [['/courses', '课程信息管理'], ['/teachers', '教师信息管理'],
        ['/coverage', '督导覆盖率'], ['/prep', '备课查看']];
    } else if (role === 'TEACHER') {
      links = [['/offerings', '我的教学班'], ['/resources', '我的教学资源'],
        ['/prep', '备课查看'], ['/evaluations', '我的教学评价']];
    } else if (role === 'SUPERVISOR') {
      links = [['/offerings', '全校开课情况'], ['/supervision', '督导与听评课'],
        ['/coverage', '督导覆盖率'], ['/evaluations', '教学评价管理']];
    } else {
      links = [['/courses', '课程信息管理'], ['/offerings', '开课与授课管理']];
    }

    // 只保留当前角色真正能访问的入口
    var html = '';
    links.forEach(function (l) {
      var route = null;
      AJ.Router.ROUTES.forEach(function (r) { if (r.path === l[0]) { route = r; } });
      if (!route) { return; }
      if (route.roles && !Api.hasRole(route.roles)) { return; }
      html += '<a class="btn" href="#' + l[0] + '">' + util.escape(l[1]) + '</a>';
    });

    var c = UI.card({ title: '快捷入口' });
    c.body.innerHTML = '<div style="display:flex;flex-wrap:wrap;gap:10px">' + (html || '<span class="text-sub">暂无可用入口</span>') + '</div>';
    return c.el;
  }

  /** 页面卸载：销毁图表，避免 ResizeObserver 泄漏 */
  function destroy() {
    reqSeq++;
    mounted.forEach(function (c) {
      if (c && typeof c.destroy === 'function') {
        try { c.destroy(); } catch (e) { /* 忽略 */ }
      }
    });
    mounted = [];
  }

  AJ.pages.dashboard = {
    title: '工作台',
    roles: ['ADMIN', 'DIRECTOR', 'TEACHER', 'SUPERVISOR', 'COURSE_ADMIN'],
    render: render,
    destroy: destroy
  };

})(window, document);
