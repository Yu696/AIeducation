/* ============================================================================
 * pages/supervision.js —— 督导与听评课（契约第 7 节 / US-04）
 *
 * 功能：
 *   · 列表：分页 + 学期/督导/状态筛选
 *   · 安排听评课（新建）、更新听评课结果（编辑）
 *   · 详情抽屉、导出 CSV
 *
 * 用到的接口：
 *   GET  /api/supervisions?offeringId&supervisorId&semester&status&page&size
 *   POST /api/supervisions
 *   PUT  /api/supervisions/{id}
 *   GET  /api/offerings（选择教学班）
 *   GET  /api/admin/users（选择督导，需 user:read；无权限时降级为手工输入）
 * ========================================================================== */
(function (window, document) {
  'use strict';

  var AJ = window.AJ;
  var Api = AJ.Api;
  var UI = AJ.UI;
  var util = AJ.util;

  /** 听评课状态候选值（含后端可能用的英文状态码） */
  var STATUSES = [
    { value: '已安排', label: '已安排' },
    { value: '已完成', label: '已完成' },
    { value: '已取消', label: '已取消' },
    { value: 'FINISHED', label: '已完成（FINISHED）' },
    { value: 'PLANNED', label: '已安排（PLANNED）' },
    { value: 'CANCELLED', label: '已取消（CANCELLED）' }
  ];

  function asArray(res) {
    if (!res) { return []; }
    if (Object.prototype.toString.call(res) === '[object Array]') { return res; }
    return res.records || res.list || res.rows || [];
  }

  /** 状态中文显示 */
  function statusText(v) {
    var s = String(v || '');
    if (/FINISHED/i.test(s)) { return '已完成'; }
    if (/PLANNED|SCHEDULED/i.test(s)) { return '已安排'; }
    if (/CANCEL/i.test(s)) { return '已取消'; }
    return s || '未知';
  }

  function statusBadge(v) {
    var text = statusText(v);
    var tone = /已完成/.test(text) ? 'green' : /已安排/.test(text) ? 'blue' : /已取消/.test(text) ? 'gray' : 'orange';
    return '<span class="badge badge-' + tone + '">' + util.escape(text) + '</span>';
  }

  /** 教学班下拉 */
  function offeringLoader(keyword) {
    return Api.get('/offerings', { keyword: keyword || '', page: 1, size: 200 })
      .then(function (res) {
        return asArray(res).map(function (o) {
          var parts = [
            util.pick(o, ['courseName', 'courseCode'], ''),
            util.pick(o, ['className'], ''),
            util.pick(o, ['semester'], ''),
            util.pick(o, ['teacherName'], '')
          ].filter(function (x) { return !!x; });
          return { value: o.id, label: parts.join(' · ') || ('教学班 ID: ' + o.id) };
        });
      });
  }

  /**
   * 督导下拉：优先从用户管理里取“教学督导”角色账号。
   * 无 user:read 权限时抛错，由调用方降级为手工输入督导姓名。
   */
  function supervisorLoader(keyword) {
    return Api.get('/admin/users', { roleCode: 'SUPERVISOR', keyword: keyword || '', page: 1, size: 200 })
      .then(function (res) {
        return asArray(res).map(function (u) {
          var name = util.pick(u, ['realName', 'nickname', 'username'], '未命名');
          return { value: u.id, label: name + '（' + util.pick(u, ['username'], '') + '）' };
        });
      });
  }

  /* ==================================================================
   * 1. 表单（教学班 + 督导 + 安排信息）
   * ================================================================ */
  function buildForm(sup) {
    sup = sup || {};
    var wrap = document.createElement('div');

    // 教学班
    wrap.appendChild(UI.remoteSelect({
      name: 'offeringId', label: '教学班', required: true, loader: offeringLoader,
      value: util.pick(sup, ['offeringId'], ''),
      valueLabel: util.pick(sup, ['courseName', 'className'], ''),
      placeholder: '请选择要听评课的教学班'
    }));

    // 督导：有 user:read 权限时用下拉，否则手工输入
    if (Api.can('user:read')) {
      wrap.appendChild(UI.remoteSelect({
        name: 'supervisorId', label: '教学督导', required: true, loader: supervisorLoader,
        value: util.pick(sup, ['supervisorId'], ''),
        valueLabel: util.pick(sup, ['supervisorName'], ''),
        placeholder: '请选择教学督导'
      }));
    } else {
      var manual = document.createElement('div');
      manual.className = 'form-item';
      manual.innerHTML =
        '<label class="form-label">教学督导姓名<span class="req">*</span></label>' +
        '<input class="input" name="supervisorName" placeholder="请输入督导姓名" value="' +
        util.escape(util.pick(sup, ['supervisorName'], '')) + '">' +
        '<div class="api-hint">当前账号无用户查询权限，无法加载督导名单，改为手工填写。</div>';
      wrap.appendChild(manual);
    }

    // 其余字段
    var rest = document.createElement('div');
    rest.innerHTML =
      '<div class="form-item"><label class="form-label">学期<span class="req">*</span></label>' +
      '<input class="input" name="semester" placeholder="如 2026-春季学期" value="' +
      util.escape(util.pick(sup, ['semester'], '')) + '"></div>' +

      '<div class="form-item"><label class="form-label">听课时间</label>' +
      '<input class="input" name="supervisionTime" placeholder="如 2026-03-18 10:00" value="' +
      util.escape(util.pick(sup, ['supervisionTime', 'listenTime', 'planTime'], '')) + '">' +
      '<div class="api-hint">格式建议：yyyy-MM-dd HH:mm</div></div>' +

      '<div class="form-item"><label class="form-label">听课地点</label>' +
      '<input class="input" name="location" placeholder="如 信息楼 A407" value="' +
      util.escape(util.pick(sup, ['location', 'place'], '')) + '"></div>' +

      '<div class="form-item"><label class="form-label">状态</label>' +
      '<select class="select" name="status">' +
      '<option value="">请选择状态</option>' +
      STATUSES.map(function (s) {
        var cur = String(util.pick(sup, ['status'], ''));
        var sel = (cur === s.value || statusText(cur) === s.label) ? ' selected' : '';
        return '<option value="' + util.escape(s.value) + '"' + sel + '>' + util.escape(s.label) + '</option>';
      }).join('') +
      '</select></div>' +

      '<div class="form-item"><label class="form-label">评价得分</label>' +
      '<input class="input" type="number" name="score" min="0" max="100" step="0.5" placeholder="0-100，选填" value="' +
      util.escape(util.pick(sup, ['score', 'evalScore'], '')) + '"></div>' +

      '<div class="form-item"><label class="form-label">听课记录 / 评价意见</label>' +
      '<textarea class="textarea" name="comment" rows="4" placeholder="课堂组织、师生互动、改进建议等">' +
      util.escape(util.pick(sup, ['comment', 'content', 'opinion', 'remark'], '')) + '</textarea></div>';
    wrap.appendChild(rest);

    return wrap;
  }

  function validate(values) {
    if (!values.offeringId) { return '请选择教学班'; }
    if (Api.can('user:read')) {
      if (!values.supervisorId) { return '请选择教学督导'; }
    } else if (!String(values.supervisorName || '').trim()) {
      return '请填写教学督导姓名';
    }
    if (!String(values.semester || '').trim()) { return '请填写学期'; }
    if (values.score !== null && values.score !== '' && (Number(values.score) < 0 || Number(values.score) > 100)) {
      return '评价得分需在 0—100 之间';
    }
    return null;
  }

  function buildPayload(values) {
    var payload = {
      offeringId: Number(values.offeringId),
      semester: String(values.semester || '').trim()
    };
    if (values.supervisorId) { payload.supervisorId = Number(values.supervisorId); }
    if (values.supervisorName) { payload.supervisorName = String(values.supervisorName).trim(); }
    if (values.supervisionTime) { payload.supervisionTime = values.supervisionTime; }
    if (values.location) { payload.location = values.location; }
    if (values.status) { payload.status = values.status; }
    if (values.comment) { payload.comment = values.comment; }
    if (values.score !== null && values.score !== '') { payload.score = Number(values.score); }
    return payload;
  }

  function openForm(sup, onChanged) {
    var isEdit = !!sup;
    UI.formModal({
      title: isEdit ? '更新听评课记录' : '安排听评课',
      width: 'wide',
      bodyNode: buildForm(sup),
      submitText: isEdit ? '保存修改' : '提交安排',
      onValidate: validate,
      onSubmit: function (values) {
        var payload = buildPayload(values);
        var p = isEdit
          ? Api.put('/supervisions/' + encodeURIComponent(sup.id), payload)
          : Api.post('/supervisions', payload);
        return p.then(function () {
          UI.toast(isEdit ? '听评课记录已更新' : '听评课已安排', 'success');
          if (typeof onChanged === 'function') { onChanged(); }
        }, function (err) {
          UI.toast((err && err.message) || '保存失败', 'error');
          return false;
        });
      }
    });
  }

  /* ==================================================================
   * 2. 详情抽屉
   * ================================================================ */
  function openDetail(row) {
    var d = UI.drawer({
      title: '听评课详情',
      bodyHtml: UI.loadingBlock('正在加载听评课详情…')
    });

    d.body.innerHTML = '';
    d.body.appendChild(UI.descList([
      { label: '学期', value: util.pick(row, ['semester'], '') },
      { label: '课程', value: util.pick(row, ['courseName', 'courseCode'], '') },
      { label: '教学班', value: util.pick(row, ['className'], '') },
      { label: '授课教师', value: util.pick(row, ['teacherName'], '') },
      { label: '教学督导', value: util.pick(row, ['supervisorName'], '') },
      { label: '听课时间', value: util.pick(row, ['supervisionTime', 'listenTime', 'planTime'], '') },
      { label: '听课地点', value: util.pick(row, ['location', 'place'], '') },
      { label: '状态', raw: true, value: statusBadge(util.pick(row, ['status'], '')) },
      {
        label: '评价得分', raw: true,
        value: (function () {
          var s = util.pick(row, ['score', 'evalScore'], null);
          return s === null || s === '' ? '—' : '<span class="text-strong">' + util.escape(Number(s).toFixed(1)) + '</span> 分';
        })()
      },
      { label: '登记时间', value: util.pick(row, ['createdAt', 'createTime'], '') }
    ]));

    d.body.appendChild(UI.descSection('听课记录 / 评价意见',
      UI.descText(util.pick(row, ['comment', 'content', 'opinion', 'remark'], ''))));

    if (Api.can('supervision:write')) {
      var actions = document.createElement('div');
      actions.style.display = 'flex';
      actions.style.gap = '10px';

      var editBtn = document.createElement('button');
      editBtn.type = 'button';
      editBtn.className = 'btn btn-primary btn-sm';
      editBtn.textContent = '更新记录';
      editBtn.addEventListener('click', function () {
        d.close();
        openForm(row, function () { AJ.Router.resolve(); });
      });
      actions.appendChild(editBtn);
      d.body.appendChild(UI.descSection('操作', actions));
    }
  }

  /* ==================================================================
   * 3. 页面渲染
   * ================================================================ */
  function render(container) {
    var guard = AJ.Router.guard();
    var canWrite = Api.can('supervision:write');

    var head = document.createElement('div');
    head.className = 'page-head';
    head.innerHTML =
      '<h2 class="page-title">督导与听评课</h2>' +
      '<p class="page-desc">安排与登记听评课，跟踪听课状态与结果。覆盖率口径见“督导覆盖率”页面。</p>';
    container.appendChild(head);

    // 筛选栏
    var filters = UI.filterBar([
      { name: 'semester', label: '学期', placeholder: '如 2026-春季学期' },
      { name: 'status', label: '状态', type: 'select', options: STATUSES, placeholder: '全部状态' }
    ], {
      onSearch: function (values) { tbl.setQuery(values); }
    });
    container.appendChild(filters.el);

    // 督导筛选（有权限时用下拉）
    if (Api.can('user:read')) {
      var supFilter = UI.remoteSelect({
        name: 'supervisorId', label: '教学督导', loader: supervisorLoader, placeholder: '全部督导'
      });
      supFilter.querySelector('.form-label').style.fontSize = '12px';
      supFilter.querySelector('.form-label').style.color = '#6b7280';
      supFilter.querySelector('input').style.display = 'none';
      supFilter.querySelector('select').addEventListener('change', function (e) {
        tbl.setQuery({ supervisorId: e.target.value });
      });
      filters.el.insertBefore(supFilter, filters.el.querySelector('.filter-actions'));
    }

    // 表格
    var tbl = UI.table({
      columns: [
        { title: '学期', key: 'semester', width: '120px' },
        {
          title: '课程', key: 'courseName',
          render: function (v, row) { return util.pick(row, ['courseName', 'courseCode'], ''); }
        },
        { title: '教学班', key: 'className', width: '130px' },
        { title: '授课教师', key: 'teacherName', width: '100px' },
        { title: '教学督导', key: 'supervisorName', width: '100px' },
        {
          title: '听课时间', key: 'supervisionTime', width: '150px',
          render: function (v, row) { return util.pick(row, ['supervisionTime', 'listenTime', 'planTime'], ''); }
        },
        {
          title: '状态', key: 'status', width: '90px', align: 'center', raw: true,
          render: function (v) { return statusBadge(v); }
        },
        {
          title: '得分', key: 'score', width: '70px', align: 'right',
          render: function (v, row) {
            var s = util.pick(row, ['score', 'evalScore'], null);
            return s === null || s === '' ? '—' : Number(s).toFixed(1);
          }
        },
        {
          title: '操作', width: '140px', className: 'actions', raw: true,
          render: function (v, row) {
            var html = '<button class="btn-link" data-act="detail" data-id="' + util.escape(row.id) + '">详情</button>';
            if (canWrite) {
              html += '<button class="btn-link" data-act="edit" data-id="' + util.escape(row.id) +
                '">更新</button>';
            }
            return html;
          }
        }
      ],
      loader: function (query) { return Api.get('/supervisions', query); },
      emptyTitle: '暂无听评课记录',
      emptyDesc: '可调整筛选条件，或点击“安排听评课”新增记录'
    });
    container.appendChild(tbl.el);

    // 工具栏
    var toolbar = document.createElement('div');
    toolbar.className = 'page-toolbar';

    var exportBtn = document.createElement('button');
    exportBtn.type = 'button';
    exportBtn.className = 'btn';
    exportBtn.textContent = '导出 CSV';
    exportBtn.addEventListener('click', function () {
      exportBtn.disabled = true;
      exportBtn.textContent = '导出中…';
      // 注意：契约第 7 节只声明了 /api/supervision/coverage/export，
      // 未声明 /api/supervisions/export。此处按“听评课明细导出”实现，
      // 后端需提供对应接口（见 frontend/README.md 的接口差异说明）。
      Api.exportCsv('/supervisions/export', safeQuery(tbl), '听评课记录.csv')
        .then(function (name) { UI.toast('已导出：' + name, 'success'); },
          function (err) { UI.toast((err && err.message) || '导出失败', 'error'); })
        .then(function () {
          exportBtn.disabled = false;
          exportBtn.textContent = '导出 CSV';
        });
    });
    toolbar.appendChild(exportBtn);

    if (canWrite) {
      var addBtn = document.createElement('button');
      addBtn.type = 'button';
      addBtn.className = 'btn btn-primary';
      addBtn.textContent = '安排听评课';
      addBtn.addEventListener('click', function () { openForm(null, tbl.reload); });
      toolbar.appendChild(addBtn);
    }
    head.appendChild(toolbar);

    // 行内操作
    tbl.el.addEventListener('click', function (e) {
      var btn = e.target && e.target.closest ? e.target.closest('button[data-act]') : null;
      if (!btn) { return; }
      var act = btn.getAttribute('data-act');
      var id = btn.getAttribute('data-id');
      var row = null;
      tbl.getRecords().forEach(function (r) { if (String(r.id) === String(id)) { row = r; } });
      row = row || { id: id };

      if (act === 'detail') {
        openDetail(row);
      } else if (act === 'edit') {
        openForm(row, tbl.reload);
      }
    });

    tbl.reload();
  }

  function safeQuery(tbl) {
    var q = tbl.getQuery ? tbl.getQuery() : {};
    var out = {};
    ['offeringId', 'supervisorId', 'semester', 'status'].forEach(function (k) {
      if (q[k]) { out[k] = q[k]; }
    });
    return out;
  }

  AJ.pages.supervision = {
    title: '督导与听评课',
    roles: ['ADMIN', 'SUPERVISOR', 'DIRECTOR'],
    render: render
  };

})(window, document);
