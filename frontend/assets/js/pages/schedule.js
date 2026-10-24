/* ============================================================================
 * pages/schedule.js —— 排课统筹总览（只读；US-11，原 US-DEAN-05）
 *
 * 功能：
 *   · 复用 GET /api/offerings 取数（后端已按角色强制数据范围：教师本人 / 主任本院系 / 督导全校）
 *   · 按“学期 → 教师 → 教学班”分组展示课程、教师、教学班、上课时间地点、开课状态
 *   · 学期 / 教师 / 关键词筛选；无数据时显示空态
 *   · scheduleInfo 为空时显示“未填写”，不报错
 *
 * 边界（Sprint 3 已冻结）：
 *   · 纯只读：不调用任何 POST/PUT/DELETE 接口，不写库、不改表；
 *   · 不做排课冲突检测（冲突检测顺延至进阶 2，见技术债 TD-01）。
 *
 * 用到的接口：
 *   GET /api/offerings?keyword&semester&teacherId&page&size
 *   GET /api/offerings/semesters
 *   GET /api/teachers?page&size（仅非教师角色用于筛选下拉）
 * ========================================================================== */
(function (window, document) {
  'use strict';

  var AJ = window.AJ;
  var Api = AJ.Api;
  var UI = AJ.UI;
  var util = AJ.util;

  /** 总览一次取数上限：当前种子数据规模有限，前端分组足够；超出时页面底部提示收窄筛选 */
  var FETCH_SIZE = 200;

  /** 页面状态随路由切换重建，避免上一页筛选条件残留 */
  var state = null;

  function asArray(res) {
    if (!res) { return []; }
    if (Object.prototype.toString.call(res) === '[object Array]') { return res; }
    return res.records || res.list || res.rows || [];
  }

  function teacherName(teacher) {
    return util.pick(teacher, ['teacherName', 'name', 'realName'], '未命名教师');
  }

  /* ==================================================================
   * 1. 筛选选项加载（失败不阻塞页面，仅少了候选值）
   * ================================================================ */
  function loadSemesterOptions() {
    return Api.get('/offerings/semesters').then(function (res) {
      return asArray(res);
    }, function () {
      return [];
    });
  }

  function loadTeacherOptions() {
    return Api.get('/teachers', { page: 1, size: FETCH_SIZE }).then(function (res) {
      return asArray(res).map(function (t) {
        var dept = util.pick(t, ['departmentName', 'departmentCode'], '');
        return {
          value: t.id,
          label: teacherName(t) + (dept ? ' · ' + dept : '')
        };
      });
    }, function () {
      return [];
    });
  }

  /* ==================================================================
   * 2. 分组：学期 → 教师 → 教学班
   * ================================================================ */
  function groupBySemesterAndTeacher(records) {
    var groups = [];
    var bySemester = {};

    records.forEach(function (r) {
      var sem = util.pick(r, ['semester'], '未标注学期');
      var g = bySemester[sem];
      if (!g) {
        g = bySemester[sem] = { semester: sem, teachers: {}, order: [], count: 0 };
        groups.push(g);
      }
      var tName = util.pick(r, ['teacherName'], '未指定教师');
      var tg = g.teachers[tName];
      if (!tg) {
        tg = g.teachers[tName] = { name: tName, rows: [] };
        g.order.push(tName);
      }
      tg.rows.push(r);
      g.count++;
    });

    // 学期倒序（2025-2026-1 在 2024-2025-2 之前），教师按姓名排序
    groups.sort(function (a, b) { return a.semester < b.semester ? 1 : a.semester > b.semester ? -1 : 0; });
    groups.forEach(function (g) { g.order.sort(); });
    return groups;
  }

  /* ==================================================================
   * 3. 渲染
   * ================================================================ */
  function scheduleCell(value) {
    var s = value === null || value === undefined ? '' : String(value).trim();
    if (!s) { return '<span class="badge badge-gray">未填写</span>'; }
    return util.escape(s);
  }

  var COLUMNS = [
    {
      title: '课程', key: 'courseName',
      render: function (v, row) {
        var code = util.pick(row, ['courseCode'], '');
        var name = util.pick(row, ['courseName'], '');
        return (code ? code + ' ' : '') + (name || '—');
      }
    },
    {
      title: '教学班', key: 'className', width: '150px',
      render: function (v, row) { return util.pick(row, ['className'], '—'); }
    },
    {
      title: '上课时间地点', key: 'scheduleInfo',
      raw: true,
      render: function (v) { return scheduleCell(v); }
    },
    {
      title: '学生/容量', key: 'studentCount', width: '92px', align: 'right',
      render: function (v, row) {
        return util.num(util.pick(row, ['studentCount'], null)) + ' / ' +
          util.num(util.pick(row, ['capacity'], null));
      }
    },
    {
      title: '开课状态', key: 'offeringStatus', width: '92px', align: 'center', raw: true,
      render: function (v, row) {
        var s = util.pick(row, ['offeringStatus'], '');
        return '<span class="badge badge-' + util.statusTone(s) + '">' + util.escape(s || '未知') + '</span>';
      }
    }
  ];

  function renderStats(host, records, total) {
    host.innerHTML = '';
    var courseIds = {};
    var teacherIds = {};
    var missing = 0;

    records.forEach(function (r) {
      var cid = util.pick(r, ['courseId', 'courseCode'], '');
      if (cid !== '') { courseIds[cid] = true; }
      var tid = util.pick(r, ['teacherId', 'teacherName'], '');
      if (tid !== '') { teacherIds[tid] = true; }
      if (!util.pick(r, ['scheduleInfo'], '')) { missing++; }
    });

    host.appendChild(UI.statGrid([
      { label: '教学班', value: records.length, unit: ' 个', icon: '▦',
        sub: total > records.length ? ('共 ' + total + ' 条，当前展示前 ' + records.length + ' 条') : '当前筛选结果' },
      { label: '课程', value: Object.keys(courseIds).length, unit: ' 门', icon: '▤' },
      { label: '授课教师', value: Object.keys(teacherIds).length, unit: ' 人', icon: '☺' },
      { label: '待补排课信息', value: missing, unit: ' 个', icon: '!', tone: missing > 0 ? 'warning' : 'success',
        sub: missing > 0 ? '上课时间地点为空，页面显示“未填写”' : '全部教学班已填写' }
    ], 4));
  }

  function renderGroups(host, records) {
    host.innerHTML = '';

    if (!records.length) {
      host.innerHTML = UI.emptyState(
        '该筛选条件下暂无排课数据',
        '可调整学期、教师或关键词后重新查询；若当前学期尚未开课，属于正常空态'
      );
      return;
    }

    groupBySemesterAndTeacher(records).forEach(function (g) {
      var card = UI.card({ title: g.semester + ' · ' + g.count + ' 个教学班' });
      g.order.forEach(function (tName) {
        var tg = g.teachers[tName];
        var teacherCard = UI.descSection(
          tName + ' · ' + tg.rows.length + ' 个教学班',
          UI.miniTable(COLUMNS, tg.rows, '暂无教学班')
        );
        card.body.appendChild(teacherCard);
      });
      host.appendChild(card.el);
    });
  }

  /* ==================================================================
   * 4. 取数与状态切换
   * ================================================================ */
  function load(container, values) {
    var g = AJ.Router.guard();
    var bodyHost = state.bodyHost;

    state.keyword = values.keyword || '';
    state.semester = values.semester || '';
    state.teacherId = values.teacherId || '';

    bodyHost.innerHTML = UI.loadingBlock('正在加载排课总览…');

    var query = { page: 1, size: FETCH_SIZE };
    if (state.keyword) { query.keyword = state.keyword; }
    if (state.semester) { query.semester = state.semester; }
    if (state.teacherId) { query.teacherId = state.teacherId; }

    return Api.get('/offerings', query).then(g(function (res) {
      var records = asArray(res);
      var total = res && res.total !== undefined ? util.toNumber(res.total, records.length) : records.length;
      renderStats(state.statsHost, records, total);
      renderGroups(bodyHost, records);
    }), g(function (err) {
      bodyHost.innerHTML = '';
      bodyHost.appendChild(UI.errorBox(
        (err && err.message) || '排课总览加载失败',
        function () { load(container, values); }
      ));
    }));
  }

  /* ==================================================================
   * 5. 页面入口
   * ================================================================ */
  function render(container) {
    var role = Api.roleCode();
    var isTeacher = role === 'TEACHER';
    var g = AJ.Router.guard();

    state = {
      keyword: '', semester: '', teacherId: '',
      bodyHost: null, statsHost: null
    };

    // —— 页头 ——
    var head = document.createElement('div');
    head.className = 'page-head';
    head.innerHTML =
      '<h2 class="page-title">排课统筹总览</h2>' +
      '<p class="page-desc">' +
      (isTeacher
        ? '只读视图，仅展示本人承担的教学班排课情况（含合上教学班）。'
        : '只读视图，按学期、教师、教学班汇总开课与排课信息，用于统筹查阅。') +
      '</p>';
    container.appendChild(head);

    container.appendChild(UI.note(
      '本页为<b>只读总览</b>：数据复用开课接口，按角色自动收窄可见范围；' +
      '不写入、不改表，<b>不做排课冲突检测</b>（冲突检测顺延至后续迭代）。'
    ));

    // —— 选项加载完成后构建筛选栏 ——
    var loading = document.createElement('div');
    loading.innerHTML = UI.loadingBlock('正在加载筛选条件…');
    container.appendChild(loading);

    window.Promise.all([
      loadSemesterOptions(),
      isTeacher || !Api.can('teacher:read') ? [] : loadTeacherOptions()
    ]).then(g(function (opts) {
      if (loading.parentNode) { loading.parentNode.removeChild(loading); }

      var semesters = opts[0] || [];
      var teachers = opts[1] || [];

      var fields = [
        { name: 'keyword', label: '关键词', placeholder: '课程 / 教学班' },
        {
          name: 'semester', label: '学期', type: 'select',
          options: semesters.map(function (s) { return { value: s, label: s }; }),
          placeholder: '全部学期'
        }
      ];
      if (teachers.length) {
        fields.push({
          name: 'teacherId', label: '授课教师', type: 'select',
          options: teachers, placeholder: '全部教师'
        });
      }

      var filters = UI.filterBar(fields, {
        onSearch: function (values) { load(container, values); }
      });
      container.appendChild(filters.el);

      state.statsHost = document.createElement('div');
      container.appendChild(state.statsHost);

      state.bodyHost = document.createElement('div');
      container.appendChild(state.bodyHost);

      load(container, filters.getValues());
    }), g(function (err) {
      if (loading.parentNode) { loading.parentNode.removeChild(loading); }
      container.appendChild(UI.errorBox(
        (err && err.message) || '筛选条件加载失败',
        function () { AJ.Router.resolve(); }
      ));
    }));
  }

  AJ.pages.schedule = {
    title: '排课统筹总览',
    roles: ['ADMIN', 'DIRECTOR', 'COURSE_ADMIN', 'SUPERVISOR', 'TEACHER'],
    permission: 'offering:read',
    render: render,
    destroy: function () { state = null; }
  };

})(window, document);
