/* ============================================================================
 * pages/prep.js —— 备课查看（契约第 5 节 / US-03）
 *
 * 功能：调用 GET /api/resources/prep，按“我的课程 → 该课程可用资源”分组卡片展示，
 *       顶部给出课程数、资源数、平均资源数统计与资源分布图。
 *
 * 后端返回结构可能是以下任意一种，本页做了自适应解析（均可正常渲染）：
 *   A. { records: [ {courseId, courseName, resources: [...] }, ... ] }
 *   B. [ {courseId, courseName, resources: [...] }, ... ]
 *   C. { courses: [ {..., resourceList: [...] } ] }
 *   D. [ {courseId, courseName, resourceId, resourceName, resourceType, url}, ... ]（扁平行）
 *
 * 用到的接口：
 *   GET /api/resources/prep
 * ========================================================================== */
(function (window, document) {
  'use strict';

  var AJ = window.AJ;
  var Api = AJ.Api;
  var UI = AJ.UI;
  var util = AJ.util;

  var mounted = [];

  function asArray(res) {
    if (!res) { return []; }
    if (Object.prototype.toString.call(res) === '[object Array]') { return res; }
    return res.records || res.list || res.rows || res.courses || [];
  }

  /** 从课程对象里取出资源数组（兼容多种字段名） */
  function resourcesOf(course) {
    var keys = ['resources', 'resourceList', 'items', 'resourceItems', 'availableResources'];
    for (var i = 0; i < keys.length; i++) {
      var v = course[keys[i]];
      if (Object.prototype.toString.call(v) === '[object Array]') { return v; }
    }
    return null;
  }

  /**
   * 把后端返回统一成 [{courseKey, courseName, courseCode, semester, className, teacherName, resources:[]}]
   */
  function normalizeGroups(data) {
    var rows = asArray(data);
    if (!rows.length) { return []; }

    var first = rows[0] || {};
    var nested = resourcesOf(first) !== null;

    // 情况 A/B/C：每个元素就是一门课，内含资源数组
    if (nested) {
      return rows.map(function (c) {
        return buildGroup(c, resourcesOf(c) || []);
      });
    }

    // 情况 D：扁平行，按 courseKey 聚合
    var map = {};
    var order = [];
    rows.forEach(function (r) {
      var key = String(util.pick(r, ['courseId', 'courseCode', 'courseName'], 'unknown'));
      if (!map[key]) {
        map[key] = buildGroup(r, []);
        order.push(key);
      }
      map[key].resources.push(r);
    });
    return order.map(function (k) { return map[k]; });
  }

  function buildGroup(course, resources) {
    var courseName = util.pick(course, ['courseName', 'name'], '未命名课程');
    var courseCode = util.pick(course, ['courseCode', 'code'], '');
    return {
      raw: course,
      key: String(util.pick(course, ['courseId', 'id', 'courseCode'], courseName)),
      courseName: courseName,
      courseCode: courseCode,
      semester: util.pick(course, ['semester'], ''),
      className: util.pick(course, ['className'], ''),
      teacherName: util.pick(course, ['teacherName'], ''),
      offeringCount: util.pick(course, ['offeringCount', 'offeringNum'], resources.length ? null : ''),
      resources: resources || []
    };
  }

  /** 单个资源卡片 */
  function resourceCard(r) {
    var name = util.pick(r, ['resourceName', 'name'], '未命名资源');
    var type = util.pick(r, ['resourceType', 'type'], '');
    var url = util.pick(r, ['url', 'fileUrl', 'link'], '');
    var desc = util.pick(r, ['description', 'remark'], '');
    var status = util.pick(r, ['status'], '');
    var time = util.pick(r, ['createdAt', 'createTime', 'uploadTime'], '');

    var el = document.createElement('div');
    el.className = 'res-item';
    el.innerHTML =
      '<div class="res-item-head">' +
        '<div class="res-item-name">' + util.escape(name) + '</div>' +
        (type ? '<span class="badge badge-blue">' + util.escape(type) + '</span>' : '') +
      '</div>' +
      (desc ? '<div class="res-item-desc">' + util.escape(desc) + '</div>' : '') +
      '<div class="res-item-foot">' +
        (status ? '<span class="badge badge-' + util.statusTone(status) + '">' + util.escape(status) + '</span>' : '') +
        (time ? '<span>' + util.escape(time) + '</span>' : '') +
        (url
          ? '<a class="res-open" href="' + util.escape(url) + '" target="_blank" rel="noopener noreferrer">打开资源</a>'
          : '<span class="res-open text-sub">无链接</span>') +
      '</div>';
    return el;
  }

  /** 一门课程的分组卡片 */
  function courseCard(group) {
    var c = document.createElement('div');
    c.className = 'card prep-course';

    var head = document.createElement('div');
    head.className = 'prep-course-head';
    var metaParts = [];
    if (group.semester) { metaParts.push(group.semester); }
    if (group.className) { metaParts.push(group.className); }
    if (group.teacherName) { metaParts.push(group.teacherName); }
    head.innerHTML =
      '<span class="prep-course-name">' +
        (group.courseCode ? util.escape(group.courseCode) + ' ' : '') +
        util.escape(group.courseName) +
      '</span>' +
      '<span class="badge badge-green">' + group.resources.length + ' 个资源</span>' +
      (metaParts.length ? '<span class="prep-meta">' + util.escape(metaParts.join(' · ')) + '</span>' : '');
    c.appendChild(head);

    if (group.resources.length) {
      var list = document.createElement('div');
      list.className = 'res-list';
      group.resources.forEach(function (r) { list.appendChild(resourceCard(r)); });
      c.appendChild(list);
    } else {
      c.appendChild(UI.emptyStateEl('该课程暂无可用资源', '可到“教学资源管理”挂接资源到本课程'));
    }
    return c;
  }

  /* ==================================================================
   * 页面渲染
   * ================================================================ */
  function render(container) {
    var guard = AJ.Router.guard();
    mounted = [];

    var head = document.createElement('div');
    head.className = 'page-head';
    head.innerHTML =
      '<h2 class="page-title">备课查看</h2>' +
      '<p class="page-desc">按“我的课程 → 该课程可用资源”汇总展示，便于快速定位备课所需课件、讲义与习题。</p>';

    var refreshBtn = document.createElement('button');
    refreshBtn.type = 'button';
    refreshBtn.className = 'btn';
    refreshBtn.textContent = '刷新';
    refreshBtn.addEventListener('click', reload);
    var toolbar = document.createElement('div');
    toolbar.className = 'page-toolbar';
    toolbar.appendChild(refreshBtn);
    head.appendChild(toolbar);
    container.appendChild(head);

    var body = document.createElement('div');
    body.innerHTML = UI.loadingBlock('正在加载备课资源…');
    container.appendChild(body);

    reload();

    function reload() {
      body.innerHTML = UI.loadingBlock('正在加载备课资源…');
      Api.get('/resources/prep')
        .then(guard(function (data) {
          renderData(body, data);
        }), guard(function (err) {
          body.innerHTML = '';
          body.appendChild(UI.errorBox((err && err.message) || '备课资源加载失败', reload));
        }));
    }
  }

  function renderData(body, data) {
    // 销毁上一轮图表
    mounted.forEach(function (c) { if (c && c.destroy) { try { c.destroy(); } catch (e) { /* 忽略 */ } } });
    mounted = [];
    body.innerHTML = '';

    var groups = normalizeGroups(data);
    var totalResources = groups.reduce(function (s, g) { return s + g.resources.length; }, 0);

    // —— 统计卡片 ——
    body.appendChild(UI.statGrid([
      { label: '我的课程数', value: groups.length, unit: '门', icon: '课', tone: '' },
      { label: '可用资源数', value: totalResources, unit: '个', icon: '资', tone: 'success' },
      {
        label: '平均每门课程资源数',
        value: groups.length ? (totalResources / groups.length).toFixed(1) : '0.0',
        unit: '个', icon: '均', tone: 'warning'
      },
      {
        label: '无资源课程数',
        value: groups.filter(function (g) { return !g.resources.length; }).length,
        unit: '门', icon: '缺',
        tone: groups.some(function (g) { return !g.resources.length; }) ? 'danger' : 'success'
      }
    ], 4));

    if (!groups.length) {
      var empty = document.createElement('div');
      empty.className = 'card';
      empty.innerHTML = '<div class="card-body">' +
        UI.emptyState('暂无备课资源', '当前账号下没有可查看的课程或资源，请确认后端已初始化数据且当前教师有在授课程') +
        '</div>';
      body.appendChild(empty);
      return;
    }

    // —— 资源分布图 ——
    var chartCard = UI.card({ title: '各课程可用资源数分布' });
    var chart = AJ.Charts.hBarChart(groups.map(function (g) {
      return {
        label: (g.courseCode ? g.courseCode + ' ' : '') + g.courseName,
        value: g.resources.length
      };
    }), { height: Math.max(150, groups.length * 34 + 30), valueUnit: ' 个', labelWidth: 190 });
    mounted.push(chart);
    chartCard.body.appendChild(chart.el);
    body.appendChild(chartCard.el);

    // —— 课程分组卡片 ——
    var title = document.createElement('div');
    title.className = 'section-title';
    title.textContent = '我的课程与可用资源（共 ' + groups.length + ' 门课程 / ' + totalResources + ' 个资源）';
    body.appendChild(title);

    groups.forEach(function (g) { body.appendChild(courseCard(g)); });
  }

  function destroy() {
    mounted.forEach(function (c) { if (c && c.destroy) { try { c.destroy(); } catch (e) { /* 忽略 */ } } });
    mounted = [];
  }

  AJ.pages.prep = {
    title: '备课查看',
    roles: ['TEACHER', 'DIRECTOR', 'ADMIN'],
    render: render,
    destroy: destroy
  };

})(window, document);
