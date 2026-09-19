/* ============================================================================
 * pages/resources.js —— 教学资源管理（契约第 5 节 / US-03）
 *
 * 功能：
 *   · 列表：分页 + 关键词/资源类型/状态/关联教学班 筛选
 *   · TEACHER 角色由后端强制 resource.teacherId = 自己，前端不额外传参
 *   · 新建 / 编辑 / 删除（resource:write）、详情抽屉、导出 CSV
 *
 * 用到的接口：
 *   GET    /api/resources?keyword&resourceType&status&offeringId&page&size
 *   GET    /api/resources/{id}
 *   POST   /api/resources
 *   PUT    /api/resources/{id}
 *   DELETE /api/resources/{id}
 *   GET    /api/resources/export
 *   GET    /api/offerings（表单选择关联教学班）
 * ========================================================================== */
(function (window, document) {
  'use strict';

  var AJ = window.AJ;
  var Api = AJ.Api;
  var UI = AJ.UI;
  var util = AJ.util;

  /** 资源类型候选值 */
  var TYPES = ['课件', '讲义', '视频', '习题', '案例', '参考文献', '实验指导', '其他'];
  /** 资源状态候选值 */
  var STATUSES = ['已关联', '未关联', '已归档'];

  function asArray(res) {
    if (!res) { return []; }
    if (Object.prototype.toString.call(res) === '[object Array]') { return res; }
    return res.records || res.list || res.rows || [];
  }

  /** 教学班下拉：GET /api/offerings */
  function offeringLoader(keyword) {
    return Api.get('/offerings', { keyword: keyword || '', page: 1, size: 200 })
      .then(function (res) {
        return asArray(res).map(function (o) {
          var course = util.pick(o, ['courseName', 'courseCode'], '');
          var cls = util.pick(o, ['className'], '');
          var sem = util.pick(o, ['semester'], '');
          var teacher = util.pick(o, ['teacherName'], '');
          var label = [course, cls, sem, teacher].filter(function (x) { return !!x; }).join(' · ');
          return { value: o.id, label: label || ('教学班 ID: ' + o.id) };
        });
      });
  }

  /* ==================================================================
   * 1. 表单
   * ================================================================ */
  function formHtml(resource) {
    resource = resource || {};
    var wrap = document.createElement('div');

    var basic = document.createElement('div');
    basic.innerHTML =
      '<div class="form-item"><label class="form-label">资源名称<span class="req">*</span></label>' +
      '<input class="input" name="resourceName" maxlength="128" placeholder="如 数据结构-第3章-树与二叉树.pptx" value="' +
      util.escape(util.pick(resource, ['resourceName', 'name'], '')) + '"></div>' +

      '<div class="form-item"><label class="form-label">资源类型<span class="req">*</span></label>' +
      '<select class="select" name="resourceType">' +
      '<option value="">请选择资源类型</option>' +
      TYPES.map(function (t) {
        var sel = String(util.pick(resource, ['resourceType', 'type'], '')) === t ? ' selected' : '';
        return '<option value="' + util.escape(t) + '"' + sel + '>' + util.escape(t) + '</option>';
      }).join('') +
      '</select></div>' +

      '<div class="form-item"><label class="form-label">资源地址 URL<span class="req">*</span></label>' +
      '<input class="input" name="url" maxlength="512" placeholder="如 https://example.com/course/ppt03.pptx" value="' +
      util.escape(util.pick(resource, ['url', 'fileUrl', 'link'], '')) + '"></div>' +

      '<div class="form-item"><label class="form-label">资源状态</label>' +
      '<select class="select" name="status">' +
      '<option value="">请选择状态</option>' +
      STATUSES.map(function (s) {
        var sel = String(resource.status || '') === s ? ' selected' : '';
        return '<option value="' + util.escape(s) + '"' + sel + '>' + util.escape(s) + '</option>';
      }).join('') +
      '</select></div>';
    wrap.appendChild(basic);

    // 关联教学班（远程下拉，选填）
    wrap.appendChild(UI.remoteSelect({
      name: 'offeringId', label: '关联教学班', loader: offeringLoader,
      value: util.pick(resource, ['offeringId'], ''),
      valueLabel: util.pick(resource, ['offeringLabel'], ''),
      placeholder: '不关联具体教学班'
    }));

    var desc = document.createElement('div');
    desc.innerHTML =
      '<div class="form-item"><label class="form-label">资源说明</label>' +
      '<textarea class="textarea" name="description" rows="4" placeholder="内容概要、适用章节、使用建议等">' +
      util.escape(util.pick(resource, ['description', 'remark'], '')) + '</textarea></div>';
    wrap.appendChild(desc);

    return wrap;
  }

  function validate(values) {
    if (!String(values.resourceName || '').trim()) { return '请填写资源名称'; }
    if (!String(values.resourceType || '').trim()) { return '请选择资源类型'; }
    var url = String(values.url || '').trim();
    if (!url) { return '请填写资源地址 URL'; }
    if (url.length > 512) { return '资源地址过长（不超过 512 字符）'; }
    return null;
  }

  function buildPayload(values) {
    var payload = {
      resourceName: String(values.resourceName || '').trim(),
      resourceType: String(values.resourceType || '').trim(),
      url: String(values.url || '').trim()
    };
    if (values.status) { payload.status = values.status; }
    if (values.description) { payload.description = values.description; }
    if (values.offeringId) { payload.offeringId = Number(values.offeringId); }
    return payload;
  }

  function openForm(resource, onChanged) {
    var isEdit = !!resource;
    UI.formModal({
      title: isEdit ? '编辑教学资源' : '新建教学资源',
      width: 'wide',
      bodyNode: formHtml(resource),
      submitText: isEdit ? '保存修改' : '创建资源',
      onValidate: validate,
      onSubmit: function (values) {
        var payload = buildPayload(values);
        var p = isEdit
          ? Api.put('/resources/' + encodeURIComponent(resource.id), payload)
          : Api.post('/resources', payload);
        return p.then(function () {
          UI.toast(isEdit ? '资源已更新' : '资源已创建', 'success');
          if (typeof onChanged === 'function') { onChanged(); }
        }, function (err) {
          UI.toast((err && err.message) || '保存失败', 'error');
          return false;
        });
      }
    });
  }

  function confirmDelete(resource, onDone) {
    var name = util.pick(resource, ['resourceName', 'name'], '该资源');
    UI.confirm({
      title: '删除教学资源',
      message: '确定要删除资源「' + name + '」吗？',
      warn: '删除后与该资源关联的课程挂接关系也会一并解除。',
      confirmText: '确认删除',
      onConfirm: function () {
        return Api.del('/resources/' + encodeURIComponent(resource.id))
          .then(function () {
            UI.toast('资源已删除', 'success');
            if (typeof onDone === 'function') { onDone(); }
          }, function (err) {
            UI.toast((err && err.message) || '删除失败', 'error');
            return false;
          });
      }
    });
  }

  /* ==================================================================
   * 2. 详情抽屉
   * ================================================================ */
  function openDetail(id, onChanged) {
    var d = UI.drawer({
      title: '资源详情',
      bodyHtml: UI.loadingBlock('正在加载资源详情…')
    });

    Api.get('/resources/' + encodeURIComponent(id))
      .then(guard(function (r) {
        d.body.innerHTML = '';
        if (!r) {
          d.body.appendChild(UI.emptyStateEl('资源不存在', '该资源可能已被删除'));
          return;
        }
        var name = util.pick(r, ['resourceName', 'name'], '未命名资源');
        d.el.querySelector('.drawer-title').textContent = '资源详情 · ' + name;
        var url = util.pick(r, ['url', 'fileUrl', 'link'], '');

        d.body.appendChild(UI.descList([
          { label: '资源名称', value: name },
          { label: '资源类型', value: util.pick(r, ['resourceType', 'type'], '') },
          {
            label: '资源状态', raw: true,
            value: '<span class="badge badge-' + util.statusTone(r.status) + '">' +
              util.escape(r.status || '未知') + '</span>'
          },
          { label: '关联教学班', value: util.pick(r, ['offeringLabel', 'className', 'courseName'], '') },
          { label: '上传教师', value: util.pick(r, ['teacherName', 'uploaderName'], '') },
          { label: '上传时间', value: util.pick(r, ['createdAt', 'createTime', 'uploadTime'], '') },
          {
            label: '资源地址', raw: true,
            value: url
              ? '<a href="' + util.escape(url) + '" target="_blank" rel="noopener noreferrer">' +
                util.escape(util.truncate(url, 48)) + '</a>'
              : '—'
          }
        ]));

        d.body.appendChild(UI.descSection('资源说明',
          UI.descText(util.pick(r, ['description', 'remark'], ''))));

        if (Api.can('resource:write')) {
          var actions = document.createElement('div');
          actions.style.display = 'flex';
          actions.style.gap = '10px';

          var editBtn = document.createElement('button');
          editBtn.type = 'button';
          editBtn.className = 'btn btn-primary btn-sm';
          editBtn.textContent = '编辑资源';
          editBtn.addEventListener('click', function () {
            d.close();
            openForm(r, onChanged);
          });

          var delBtn = document.createElement('button');
          delBtn.type = 'button';
          delBtn.className = 'btn btn-danger btn-sm';
          delBtn.textContent = '删除资源';
          delBtn.addEventListener('click', function () {
            confirmDelete(r, function () {
              d.close();
              if (typeof onChanged === 'function') { onChanged(); }
            });
          });

          actions.appendChild(editBtn);
          actions.appendChild(delBtn);
          d.body.appendChild(UI.descSection('操作', actions));
        }
      }), guard(function (err) {
        d.body.innerHTML = '';
        d.body.appendChild(UI.errorBox((err && err.message) || '资源详情加载失败'));
      }));
  }

  /* ==================================================================
   * 3. 页面渲染
   * ================================================================ */
  function render(container) {
    var guard = AJ.Router.guard();
    var role = Api.roleCode();
    var isTeacher = role === 'TEACHER';
    var canWrite = Api.can('resource:write');

    var head = document.createElement('div');
    head.className = 'page-head';
    head.innerHTML =
      '<h2 class="page-title">教学资源管理</h2>' +
      '<p class="page-desc">' +
      (isTeacher
        ? '当前角色为教师，仅显示本人上传的教学资源。'
        : '维护课件、讲义、视频等教学资源，并支持关联到具体教学班。') +
      '</p>';
    container.appendChild(head);

    // 筛选栏
    var filters = UI.filterBar([
      { name: 'keyword', label: '关键词', placeholder: '资源名称 / 说明' },
      { name: 'resourceType', label: '资源类型', type: 'select', options: TYPES, placeholder: '全部类型' },
      { name: 'status', label: '资源状态', type: 'select', options: STATUSES, placeholder: '全部状态' }
    ], {
      onSearch: function (values) { tbl.setQuery(values); }
    });
    container.appendChild(filters.el);

    // 表格
    var tbl = UI.table({
      columns: [
        {
          title: '资源名称', key: 'resourceName',
          render: function (v, row) {
            return '<span class="text-strong">' +
              util.escape(util.pick(row, ['resourceName', 'name'], '')) + '</span>';
          },
          raw: true
        },
        {
          title: '资源类型', key: 'resourceType', width: '100px',
          render: function (v, row) { return util.pick(row, ['resourceType', 'type'], ''); }
        },
        {
          title: '关联教学班', key: 'offeringLabel', width: '220px',
          render: function (v, row) {
            var parts = [
              util.pick(row, ['courseName', 'courseCode'], ''),
              util.pick(row, ['className'], ''),
              util.pick(row, ['semester'], '')
            ].filter(function (x) { return !!x; });
            return parts.length ? parts.join(' · ') : '未关联';
          }
        },
        {
          title: '上传教师', key: 'teacherName', width: '100px',
          render: function (v, row) { return util.pick(row, ['teacherName', 'uploaderName'], ''); }
        },
        {
          title: '状态', key: 'status', width: '88px', align: 'center', raw: true,
          render: function (v) {
            return '<span class="badge badge-' + util.statusTone(v) + '">' + util.escape(v || '未知') + '</span>';
          }
        },
        {
          title: '上传时间', key: 'createdAt', width: '150px',
          render: function (v, row) { return util.pick(row, ['createdAt', 'createTime', 'uploadTime'], ''); }
        },
        {
          title: '链接', key: 'url', width: '70px', align: 'center', raw: true,
          render: function (v, row) {
            var u = util.pick(row, ['url', 'fileUrl', 'link'], '');
            return u
              ? '<a href="' + util.escape(u) + '" target="_blank" rel="noopener noreferrer">打开</a>'
              : '—';
          }
        },
        {
          title: '操作', width: '150px', className: 'actions', raw: true,
          render: function (v, row) {
            var html = '<button class="btn-link" data-act="detail" data-id="' + util.escape(row.id) + '">详情</button>';
            if (canWrite) {
              html += '<button class="btn-link" data-act="edit" data-id="' + util.escape(row.id) + '">编辑</button>' +
                '<button class="btn-link danger" data-act="del" data-id="' + util.escape(row.id) + '">删除</button>';
            }
            return html;
          }
        }
      ],
      loader: function (query) { return Api.get('/resources', query); },
      emptyTitle: '暂无教学资源',
      emptyDesc: '可调整筛选条件，或新建教学资源'
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
      Api.exportCsv('/resources/export', safeQuery(tbl), '教学资源列表.csv')
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
      addBtn.textContent = '新建资源';
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
        openDetail(id, tbl.reload);
      } else if (act === 'edit') {
        Api.get('/resources/' + encodeURIComponent(id)).then(guard(function (r) {
          openForm(r || row, tbl.reload);
        }), guard(function (err) {
          UI.toast((err && err.message) || '资源详情加载失败', 'error');
        }));
      } else if (act === 'del') {
        confirmDelete(row, tbl.reload);
      }
    });

    tbl.reload();
  }

  function safeQuery(tbl) {
    var q = tbl.getQuery ? tbl.getQuery() : {};
    var out = {};
    ['keyword', 'resourceType', 'status', 'offeringId'].forEach(function (k) {
      if (q[k]) { out[k] = q[k]; }
    });
    return out;
  }

  AJ.pages.resources = {
    title: '教学资源管理',
    roles: ['ADMIN', 'DIRECTOR', 'TEACHER', 'SUPERVISOR', 'COURSE_ADMIN'],
    render: render
  };

})(window, document);
