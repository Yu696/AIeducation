/* ============================================================================
 * pages/teachers.js —— 教师信息管理（契约第 4 节 / US-02）
 *
 * 满足故事原文：“作为教研室主任，我想查看 5 位教师的课程简介与开课情况，以便统筹排课”
 *   · 列表：分页 + 关键词/院系/职称筛选
 *   · 详情抽屉：教师基本信息 + 课程简介与开课情况（GET /api/teachers/{id} 与 /offerings）
 *   · 新建 / 编辑 / 删除、导出 CSV
 *
 * 用到的接口：
 *   GET    /api/teachers?keyword&departmentCode&title&page&size
 *   GET    /api/teachers/{id}
 *   GET    /api/teachers/{id}/offerings
 *   POST   /api/teachers
 *   PUT    /api/teachers/{id}
 *   DELETE /api/teachers/{id}
 *   GET    /api/teachers/export
 * ========================================================================== */
(function (window, document) {
  'use strict';

  var AJ = window.AJ;
  var Api = AJ.Api;
  var UI = AJ.UI;
  var util = AJ.util;

  /** 职称候选值 */
  var TITLES = ['助教', '讲师', '副教授', '教授', '研究员', '高级工程师'];

  var departmentOptions = [];

  function asArray(res) {
    if (!res) { return []; }
    if (Object.prototype.toString.call(res) === '[object Array]') { return res; }
    return res.records || res.list || res.rows || [];
  }

  /* ==================================================================
   * 1. 表单
   * ================================================================ */
  function formHtml(teacher) {
    teacher = teacher || {};
    return '' +
      '<div class="form-item"><label class="form-label">工号<span class="req">*</span></label>' +
      '<input class="input" name="teacherCode" maxlength="32" placeholder="如 T2026001" value="' +
      util.escape(util.pick(teacher, ['teacherCode', 'code', 'employeeNo'], '')) + '"></div>' +

      '<div class="form-item"><label class="form-label">姓名<span class="req">*</span></label>' +
      '<input class="input" name="teacherName" maxlength="32" placeholder="如 张三" value="' +
      util.escape(util.pick(teacher, ['teacherName', 'name', 'realName'], '')) + '"></div>' +

      '<div class="form-item"><label class="form-label">所属院系</label>' +
      '<input class="input" name="departmentCode" placeholder="如 计算机科学与工程学院" value="' +
      util.escape(util.pick(teacher, ['departmentCode', 'departmentName'], '')) + '"></div>' +

      '<div class="form-item"><label class="form-label">职称</label>' +
      '<input class="input" name="title" list="titleList" placeholder="如 副教授" value="' +
      util.escape(util.pick(teacher, ['title', 'professionalTitle'], '')) + '">' +
      '<datalist id="titleList">' +
      TITLES.map(function (t) { return '<option value="' + util.escape(t) + '"></option>'; }).join('') +
      '</datalist></div>' +

      '<div class="form-item"><label class="form-label">联系电话</label>' +
      '<input class="input" name="phone" maxlength="20" placeholder="选填" value="' +
      util.escape(util.pick(teacher, ['phone', 'mobile'], '')) + '"></div>' +

      '<div class="form-item"><label class="form-label">电子邮箱</label>' +
      '<input class="input" name="email" maxlength="64" placeholder="选填" value="' +
      util.escape(util.pick(teacher, ['email'], '')) + '"></div>' +

      '<div class="form-item"><label class="form-label">研究方向 / 简介</label>' +
      '<textarea class="textarea" name="description" rows="4" placeholder="研究方向、主讲课程等">' +
      util.escape(util.pick(teacher, ['description', 'introduction', 'researchArea'], '')) + '</textarea></div>';
  }

  function validate(values) {
    if (!String(values.teacherCode || '').trim()) { return '请填写工号'; }
    if (!String(values.teacherName || '').trim()) { return '请填写姓名'; }
    if (values.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(values.email)) { return '电子邮箱格式不正确'; }
    return null;
  }

  function buildPayload(values) {
    var payload = {
      teacherCode: String(values.teacherCode || '').trim(),
      teacherName: String(values.teacherName || '').trim()
    };
    if (values.departmentCode) { payload.departmentCode = values.departmentCode; }
    if (values.title) { payload.title = values.title; }
    if (values.phone) { payload.phone = values.phone; }
    if (values.email) { payload.email = values.email; }
    if (values.description) { payload.description = values.description; }
    return payload;
  }

  function openForm(teacher, onChanged) {
    var isEdit = !!teacher;
    UI.formModal({
      title: isEdit ? '编辑教师信息' : '新建教师',
      width: 'wide',
      fieldsHtml: formHtml(teacher),
      submitText: isEdit ? '保存修改' : '创建教师',
      onValidate: validate,
      onSubmit: function (values) {
        var payload = buildPayload(values);
        var p = isEdit
          ? Api.put('/teachers/' + encodeURIComponent(teacher.id), payload)
          : Api.post('/teachers', payload);
        return p.then(function () {
          UI.toast(isEdit ? '教师信息已更新' : '教师已创建', 'success');
          if (typeof onChanged === 'function') { onChanged(); }
        }, function (err) {
          UI.toast((err && err.message) || '保存失败', 'error');
          return false;
        });
      }
    });
  }

  function confirmDelete(teacher, onDone) {
    var name = util.pick(teacher, ['teacherName', 'name'], '该教师');
    UI.confirm({
      title: '删除教师',
      message: '确定要删除教师「' + name + '」吗？',
      warn: '若该教师仍有在授课程，后端将拒绝删除并返回冲突提示。',
      confirmText: '确认删除',
      onConfirm: function () {
        return Api.del('/teachers/' + encodeURIComponent(teacher.id))
          .then(function () {
            UI.toast('教师已删除', 'success');
            if (typeof onDone === 'function') { onDone(); }
          }, function (err) {
            UI.toast((err && err.message) || '删除失败', 'error');
            return false;
          });
      }
    });
  }

  /* ==================================================================
   * 2. 详情抽屉（课程简介与开课情况）
   * ================================================================ */
  function openDetail(id, onChanged) {
    var d = UI.drawer({
      title: '教师详情',
      bodyHtml: UI.loadingBlock('正在加载教师详情…')
    });

    Api.get('/teachers/' + encodeURIComponent(id))
      .then(guard(function (teacher) {
        d.body.innerHTML = '';
        if (!teacher) {
          d.body.appendChild(UI.emptyStateEl('教师不存在', '该教师可能已被删除'));
          return;
        }
        renderDetail(d, teacher, onChanged);
      }), guard(function (err) {
        d.body.innerHTML = '';
        d.body.appendChild(UI.errorBox((err && err.message) || '教师详情加载失败'));
      }));
  }

  function renderDetail(d, teacher, onChanged) {
    var name = util.pick(teacher, ['teacherName', 'name', 'realName'], '未命名教师');
    d.el.querySelector('.drawer-title').textContent = '教师详情 · ' + name;

    // 基本信息
    d.body.appendChild(UI.descList([
      { label: '工号', value: util.pick(teacher, ['teacherCode', 'code', 'employeeNo'], '') },
      { label: '姓名', value: name },
      { label: '所属院系', value: util.pick(teacher, ['departmentCode', 'departmentName'], '') },
      { label: '职称', value: util.pick(teacher, ['title', 'professionalTitle'], '') },
      { label: '联系电话', value: util.pick(teacher, ['phone', 'mobile'], '') },
      { label: '电子邮箱', value: util.pick(teacher, ['email'], '') },
      { label: '在授课程数', value: util.pick(teacher, ['offeringCount', 'courseCount'], '') }
    ]));

    // 研究方向 / 简介
    var desc = util.pick(teacher, ['description', 'introduction', 'researchArea'], '');
    if (desc) {
      d.body.appendChild(UI.descSection('研究方向 / 简介', UI.descText(desc)));
    }

    // 课程简介与开课情况
    var offeringSection = UI.descSection('课程简介与开课情况', UI.loadingBlock('加载中…'));
    d.body.appendChild(offeringSection);

    Api.get('/teachers/' + encodeURIComponent(teacher.id) + '/offerings')
      .then(guard(function (res) {
        var rows = asArray(res);
        if (!rows.length) {
          UI.fillSection(offeringSection, UI.emptyStateEl('该教师暂无开课记录', '可在开课与授课管理中补充'));
          return;
        }

        var holder = document.createElement('div');

        // 汇总卡片：授课门数、教学班数、学生总人次
        var courseSet = {};
        var totalStudents = 0;
        rows.forEach(function (r) {
          var c = util.pick(r, ['courseId', 'courseName'], '');
          if (c) { courseSet[c] = true; }
          totalStudents += util.toNumber(util.pick(r, ['studentCount', 'studentTotal'], 0));
        });

        var statGrid = document.createElement('div');
        statGrid.className = 'grid grid-3';
        statGrid.style.marginBottom = '12px';
        statGrid.appendChild(UI.statCard({
          label: '授课门数', value: Object.keys(courseSet).length, unit: '门', icon: '课', tone: ''
        }));
        statGrid.appendChild(UI.statCard({
          label: '教学班数', value: rows.length, unit: '个', icon: '班', tone: 'success'
        }));
        statGrid.appendChild(UI.statCard({
          label: '学生总人次', value: totalStudents, unit: '人次', icon: '人', tone: 'warning'
        }));
        holder.appendChild(statGrid);

        // 开课明细（含课程简介）
        holder.appendChild(UI.miniTable([
          { title: '学期', key: 'semester' },
          {
            title: '课程', key: 'courseName',
            render: function (v, row) { return util.pick(row, ['courseName', 'courseCode'], ''); }
          },
          { title: '教学班', key: 'className' },
          {
            title: '学生人次', key: 'studentCount', align: 'right',
            render: function (v, row) { return util.pick(row, ['studentCount', 'studentTotal'], '—'); }
          },
          {
            title: '开课状态', key: 'offeringStatus',
            render: function (v, row) { return util.pick(row, ['offeringStatus', 'status'], ''); }
          },
          {
            title: '课程简介', key: 'courseDescription',
            render: function (v, row) {
              var text = util.pick(row, ['courseDescription', 'description', 'introduction'], '');
              return text ? util.truncate(text, 40) : '—';
            }
          }
        ], rows, '暂无开课记录'));

        // 按学期分布图
        var bySemester = {};
        rows.forEach(function (r) {
          var s = util.pick(r, ['semester'], '未标注学期');
          bySemester[s] = (bySemester[s] || 0) + 1;
        });
        var chartData = Object.keys(bySemester).map(function (k) {
          return { label: k, value: bySemester[k] };
        });
        if (chartData.length) {
          var chartSection = UI.descSection('按学期的教学班分布', null);
          var chart = AJ.Charts.barChart(chartData, { height: 190, valueUnit: '个教学班' });
          chartSection.appendChild(chart.el);
          holder.appendChild(chartSection);
          d._chart = chart;
        }

        UI.fillSection(offeringSection, holder);
      }), guard(function (err) {
        UI.fillSection(offeringSection, UI.errorBox((err && err.message) || '开课情况加载失败'));
      }));

    // 写操作
    if (Api.can('teacher:write')) {
      var actions = document.createElement('div');
      actions.style.display = 'flex';
      actions.style.gap = '10px';

      var editBtn = document.createElement('button');
      editBtn.type = 'button';
      editBtn.className = 'btn btn-primary btn-sm';
      editBtn.textContent = '编辑教师';
      editBtn.addEventListener('click', function () {
        d.close();
        openForm(teacher, onChanged);
      });

      var delBtn = document.createElement('button');
      delBtn.type = 'button';
      delBtn.className = 'btn btn-danger btn-sm';
      delBtn.textContent = '删除教师';
      delBtn.addEventListener('click', function () {
        confirmDelete(teacher, function () {
          d.close();
          if (typeof onChanged === 'function') { onChanged(); }
        });
      });

      actions.appendChild(editBtn);
      actions.appendChild(delBtn);
      d.body.appendChild(UI.descSection('操作', actions));
    }
  }

  /* ==================================================================
   * 3. 页面渲染
   * ================================================================ */
  function render(container) {
    var guard = AJ.Router.guard();
    var canWrite = Api.can('teacher:write');

    var head = document.createElement('div');
    head.className = 'page-head';
    head.innerHTML =
      '<h2 class="page-title">教师信息管理</h2>' +
      '<p class="page-desc">查看教师基本信息、课程简介与开课情况，支持按院系与职称筛选。' +
      '点击“详情”可查看该教师的完整授课情况。</p>';
    container.appendChild(head);

    var filters = UI.filterBar([
      { name: 'keyword', label: '关键词', placeholder: '工号 / 姓名' },
      { name: 'departmentCode', label: '所属院系', type: 'select', options: departmentOptions, placeholder: '全部院系' },
      { name: 'title', label: '职称', type: 'select', options: TITLES, placeholder: '全部职称' }
    ], {
      onSearch: function (values) { tbl.setQuery(values); }
    });
    container.appendChild(filters.el);

    var tbl = UI.table({
      columns: [
        {
          title: '工号', key: 'teacherCode', width: '110px',
          render: function (v, row) { return util.pick(row, ['teacherCode', 'code', 'employeeNo'], v); }
        },
        {
          title: '姓名', key: 'teacherName',
          render: function (v, row) {
            return '<span class="text-strong">' +
              util.escape(util.pick(row, ['teacherName', 'name', 'realName'], '')) + '</span>';
          },
          raw: true
        },
        { title: '职称', key: 'title', width: '100px',
          render: function (v, row) { return util.pick(row, ['title', 'professionalTitle'], ''); } },
        {
          title: '所属院系', key: 'departmentCode', width: '180px',
          render: function (v, row) { return util.pick(row, ['departmentCode', 'departmentName'], ''); }
        },
        { title: '联系电话', key: 'phone', width: '130px',
          render: function (v, row) { return util.pick(row, ['phone', 'mobile'], ''); } },
        { title: '电子邮箱', key: 'email', width: '180px' },
        {
          title: '在授课程数', key: 'offeringCount', width: '96px', align: 'right',
          render: function (v, row) { return util.pick(row, ['offeringCount', 'courseCount'], '—'); }
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
      loader: function (query) {
        return Api.get('/teachers', query).then(guard(function (res) {
          // 归纳院系候选
          var seen = {};
          departmentOptions.forEach(function (d) { seen[d] = true; });
          var added = false;
          asArray(res).forEach(function (r) {
            var d = util.pick(r, ['departmentCode', 'departmentName'], '');
            if (d && !seen[d]) { seen[d] = true; added = true; }
          });
          if (added) { departmentOptions = Object.keys(seen); }
          return res;
        }));
      },
      emptyTitle: '暂无教师数据',
      emptyDesc: '可调整筛选条件，或新建教师信息'
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
      Api.exportCsv('/teachers/export', safeQuery(tbl), '教师列表.csv')
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
      addBtn.textContent = '新建教师';
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

      if (act === 'detail') {
        openDetail(id, tbl.reload);
      } else if (act === 'edit') {
        Api.get('/teachers/' + encodeURIComponent(id)).then(guard(function (t) {
          openForm(t, tbl.reload);
        }), guard(function (err) {
          UI.toast((err && err.message) || '教师信息加载失败', 'error');
        }));
      } else if (act === 'del') {
        var row = null;
        tbl.getRecords().forEach(function (r) { if (String(r.id) === String(id)) { row = r; } });
        confirmDelete(row || { id: id }, tbl.reload);
      }
    });

    tbl.reload();
  }

  function safeQuery(tbl) {
    var q = tbl.getQuery ? tbl.getQuery() : {};
    var out = {};
    ['keyword', 'departmentCode', 'title'].forEach(function (k) { if (q[k]) { out[k] = q[k]; } });
    return out;
  }

  AJ.pages.teachers = {
    title: '教师信息管理',
    roles: ['ADMIN', 'DIRECTOR', 'COURSE_ADMIN'],
    render: render
  };

})(window, document);
