/* ============================================================================
 * pages/supervision.js —— 督导与听评课（契约第 7 节 / US-04 / Sprint 2 US-10）
 *
 * 功能：
 *   · 列表：分页 + 学期/督导/状态筛选
 *   · 状态机：待安排 → 已安排 → 已完成；表单按状态显示计划/完成日期
 *   · 安排听评课（新建）、更新听评课结果（编辑）、快捷“安排/完成”
 *   · 详情抽屉、导出 CSV
 *
 * 用到的接口：
 *   GET  /api/supervisions?offeringId&supervisorId&semester&status&page&size
 *   GET  /api/supervisions/supervisors（督导候选教师）
 *   POST /api/supervisions
 *   PUT  /api/supervisions/{id}
 *   GET  /api/offerings（选择教学班）
 * ========================================================================== */
(function (window, document) {
  'use strict';

  var AJ = window.AJ;
  var Api = AJ.Api;
  var UI = AJ.UI;
  var util = AJ.util;

  /** 听评课状态候选值（与后端 SupervisionService.STATUSES 一致） */
  var STATUSES = [
    { value: '待安排', label: '待安排' },
    { value: '已安排', label: '已安排' },
    { value: '已完成', label: '已完成' }
  ];

  function asArray(res) {
    if (!res) { return []; }
    if (Object.prototype.toString.call(res) === '[object Array]') { return res; }
    return res.records || res.list || res.rows || [];
  }

  /** 兼容历史英文状态码，统一显示为中文 */
  function statusText(v) {
    var s = String(v || '');
    if (/FINISHED|COMPLETED/i.test(s)) { return '已完成'; }
    if (/PLANNED|SCHEDULED|ARRANGED/i.test(s)) { return '已安排'; }
    if (/PENDING/i.test(s)) { return '待安排'; }
    if (/CANCEL/i.test(s)) { return '已取消'; }
    return s || '待安排';
  }

  function statusBadge(v) {
    var text = statusText(v);
    var tone = /已完成/.test(text) ? 'green' : /已安排/.test(text) ? 'blue' :
      /已取消/.test(text) ? 'gray' : 'orange';
    return '<span class="badge badge-' + tone + '">' + util.escape(text) + '</span>';
  }

  /** 取日期输入框可用的 yyyy-MM-dd（后端可能返回带时间的字符串） */
  function dateValue(v) {
    var s = String(v || '');
    if (!s) { return ''; }
    return s.length >= 10 ? s.slice(0, 10) : s;
  }

  function today() {
    var d = new Date();
    var m = String(d.getMonth() + 1).padStart(2, '0');
    var day = String(d.getDate()).padStart(2, '0');
    return d.getFullYear() + '-' + m + '-' + day;
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
   * 督导候选教师：走 /api/supervisions/supervisors（只需 supervision:read）。
   * 不再依赖 /api/admin/users，避免教学督导账号无 user:read 权限时无法安排听评课。
   */
  function supervisorLoader(keyword) {
    return Api.get('/supervisions/supervisors', { keyword: keyword || '' })
      .then(function (res) {
        return asArray(res).map(function (t) {
          var name = util.pick(t, ['name', 'realName'], '未命名');
          var dept = util.pick(t, ['departmentName', 'departmentCode'], '');
          return { value: t.id, label: name + (dept ? '（' + dept + '）' : '') };
        });
      });
  }

  /** 按状态显示/隐藏日期行；切到已完成时自动填今天 */
  function syncStatusFields(wrap) {
    var statusEl = wrap.querySelector('[name="status"]');
    var status = statusEl ? statusEl.value : '已安排';
    var fields = wrap.querySelectorAll('[data-show-for]');
    for (var i = 0; i < fields.length; i++) {
      var allowed = String(fields[i].getAttribute('data-show-for') || '').split(',');
      fields[i].style.display = allowed.indexOf(status) !== -1 ? '' : 'none';
    }
    if (status === '已完成') {
      var finish = wrap.querySelector('[name="finishDate"]');
      if (finish && !finish.value) { finish.value = today(); }
    }
  }

  /* ==================================================================
   * 1. 表单（教学班 + 督导 + 状态/日期）
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

    // 督导：直接用听评课权限下的候选教师接口
    wrap.appendChild(UI.remoteSelect({
      name: 'supervisorId', label: '教学督导', required: true, loader: supervisorLoader,
      value: util.pick(sup, ['supervisorId'], ''),
      valueLabel: util.pick(sup, ['supervisorName'], ''),
      placeholder: '请选择教学督导'
    }));

    var currentStatus = statusText(util.pick(sup, ['status'], '')) || '已安排';
    if (!util.pick(sup, ['status'], '')) { currentStatus = '已安排'; }
    var statusOptions = STATUSES.map(function (s) {
      return '<option value="' + s.value + '"' +
        (currentStatus === s.value ? ' selected' : '') + '>' + s.label + '</option>';
    }).join('');

    var rest = document.createElement('div');
    rest.innerHTML =
      '<div class="form-item"><label class="form-label">学期<span class="req">*</span></label>' +
      '<input class="input" name="semester" placeholder="如 2025-2026-1" value="' +
      util.escape(util.pick(sup, ['semester'], '')) + '"></div>' +

      '<div class="form-item"><label class="form-label">状态<span class="req">*</span></label>' +
      '<select class="select" name="status">' + statusOptions + '</select>' +
      '<div class="api-hint">待安排 → 已安排 → 已完成，只能向前流转。</div></div>' +

      '<div class="form-item" data-show-for="已安排,已完成"><label class="form-label">计划听课日期<span class="req">*</span></label>' +
      '<input class="input" type="date" name="planDate" value="' +
      util.escape(dateValue(util.pick(sup, ['planDate', 'supervisionTime'], ''))) + '"></div>' +

      '<div class="form-item" data-show-for="已完成"><label class="form-label">实际完成日期</label>' +
      '<input class="input" type="date" name="finishDate" value="' +
      util.escape(dateValue(util.pick(sup, ['finishDate'], ''))) + '">' +
      '<div class="api-hint">留空时由后端自动填写当天。</div></div>' +

      '<div class="form-item" data-show-for="已完成"><label class="form-label">评价得分</label>' +
      '<input class="input" type="number" name="score" min="0" max="100" step="0.5" placeholder="0-100，选填" value="' +
      util.escape(util.pick(sup, ['score', 'evalScore'], '')) + '"></div>' +

      '<div class="form-item"><label class="form-label">听课记录 / 评价意见</label>' +
      '<textarea class="textarea" name="comment" rows="4" placeholder="课堂组织、师生互动、改进建议等">' +
      util.escape(util.pick(sup, ['comment', 'content', 'opinion', 'remark'], '')) + '</textarea></div>';
    wrap.appendChild(rest);

    var statusEl = wrap.querySelector('[name="status"]');
    if (statusEl) {
      statusEl.addEventListener('change', function () { syncStatusFields(wrap); });
    }
    syncStatusFields(wrap);
    return wrap;
  }

  function validate(values) {
    if (!values.offeringId) { return '请选择教学班'; }
    if (!values.supervisorId) { return '请选择教学督导'; }
    if (!String(values.semester || '').trim()) { return '请填写学期'; }
    var status = values.status || '已安排';
    if (status === '已安排' && !values.planDate) {
      return '已安排状态必须填写计划听课日期';
    }
    if (status === '已完成' && values.finishDate && values.planDate &&
        values.finishDate < values.planDate) {
      return '完成日期不能早于计划听课日期';
    }
    if (values.score !== null && values.score !== '' &&
        (Number(values.score) < 0 || Number(values.score) > 100)) {
      return '评价得分需在 0—100 之间';
    }
    return null;
  }

  function buildPayload(values) {
    var status = values.status || '已安排';
    var payload = {
      offeringId: Number(values.offeringId),
      supervisorId: Number(values.supervisorId),
      semester: String(values.semester || '').trim(),
      status: status
    };
    if (status !== '待安排' && values.planDate) {
      payload.planDate = values.planDate;
    }
    if (status === '已完成') {
      if (values.finishDate) { payload.finishDate = values.finishDate; }
      if (values.score !== null && values.score !== '') {
        payload.score = Number(values.score);
      }
    }
    if (values.comment) {
      payload.comment = String(values.comment).trim();
    }
    return payload;
  }

  function openForm(sup, onChanged) {
    var isEdit = !!(sup && sup.id);
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
      { label: '计划听课日期', value: util.pick(row, ['planDate'], '—') },
      { label: '实际完成日期', value: util.pick(row, ['finishDate'], '—') },
      { label: '状态', raw: true, value: statusBadge(util.pick(row, ['status'], '')) },
      {
        label: '评价得分', raw: true,
        value: (function () {
          var s = util.pick(row, ['score', 'evalScore'], null);
          return s === null || s === '' ? '—' : '<span class="text-strong">' + util.escape(Number(s).toFixed(1)) + '</span> 分';
        })()
      }
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

  /** 复制一行并覆盖状态/日期，供快捷“安排/完成”使用 */
  function withStatus(row, status, finishDate) {
    var copy = {};
    for (var k in row) {
      if (Object.prototype.hasOwnProperty.call(row, k)) { copy[k] = row[k]; }
    }
    copy.status = status;
    if (finishDate) { copy.finishDate = finishDate; }
    return copy;
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
      '<p class="page-desc">按“待安排 → 已安排 → 已完成”推进听评课；完成后覆盖率自动联动。口径见“督导覆盖率”页面。</p>';
    container.appendChild(head);

    // 筛选栏
    var filters = UI.filterBar([
      { name: 'semester', label: '学期', placeholder: '如 2025-2026-1' },
      { name: 'status', label: '状态', type: 'select', options: STATUSES, placeholder: '全部状态' }
    ], {
      onSearch: function (values) { tbl.setQuery(values); }
    });
    container.appendChild(filters.el);

    // 督导筛选（听评课权限即可加载候选教师）
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

    // 表格
    var tbl = UI.table({
      columns: [
        { title: '学期', key: 'semester', width: '115px' },
        {
          title: '课程', key: 'courseName',
          render: function (v, row) { return util.pick(row, ['courseName', 'courseCode'], ''); }
        },
        { title: '教学班', key: 'className', width: '120px' },
        { title: '授课教师', key: 'teacherName', width: '90px' },
        { title: '教学督导', key: 'supervisorName', width: '90px' },
        {
          title: '计划 / 完成', key: 'planDate', width: '170px',
          render: function (v, row) {
            return util.escape(util.pick(row, ['planDate'], '—')) + ' / ' +
              util.escape(util.pick(row, ['finishDate'], '—'));
          }
        },
        {
          title: '状态', key: 'status', width: '85px', align: 'center', raw: true,
          render: function (v) { return statusBadge(v); }
        },
        {
          title: '得分', key: 'score', width: '65px', align: 'right',
          render: function (v, row) {
            var s = util.pick(row, ['score', 'evalScore'], null);
            return s === null || s === '' ? '—' : Number(s).toFixed(1);
          }
        },
        {
          title: '操作', width: '170px', className: 'actions', raw: true,
          render: function (v, row) {
            var html = '<button class="btn-link" data-act="detail" data-id="' + util.escape(row.id) + '">详情</button>';
            if (canWrite) {
              html += '<button class="btn-link" data-act="edit" data-id="' + util.escape(row.id) +
                '">更新</button>';
              var st = statusText(row.status);
              if (st === '待安排') {
                html += '<button class="btn-link" data-act="arrange" data-id="' +
                  util.escape(row.id) + '">安排</button>';
              } else if (st === '已安排') {
                html += '<button class="btn-link" data-act="finish" data-id="' +
                  util.escape(row.id) + '">完成</button>';
              }
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
      } else if (act === 'arrange') {
        openForm(withStatus(row, '已安排'), tbl.reload);
      } else if (act === 'finish') {
        openForm(withStatus(row, '已完成', today()), tbl.reload);
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
