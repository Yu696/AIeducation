/* ============================================================================
 * pages/offerings.js —— 开课与授课管理（契约第 3 节 / US-02、US-04）
 *
 * 功能：
 *   · 列表：分页 + 关键词/学期/教师/院系/开课状态/是否开课/是否已督导 筛选
 *   · TEACHER 角色由后端强制只看本人开课（前端不额外传参，信任后端的可见范围控制）
 *   · 增删改开课记录
 *   · 学生人次明细：GET /api/offerings/{id}/student-counts，并支持新增/更新人次记录
 *   · 导出 CSV：GET /api/offerings/export
 *
 * 用到的接口：
 *   GET    /api/offerings?keyword&semester&teacherId&departmentCode&offeringStatus&offered&supervised&page&size
 *   GET    /api/offerings/{id}
 *   POST   /api/offerings
 *   PUT    /api/offerings/{id}
 *   DELETE /api/offerings/{id}
 *   GET    /api/offerings/{id}/student-counts
 *   POST   /api/offerings/{id}/student-counts
 *   GET    /api/offerings/export
 *   GET    /api/courses（表单选择课程）
 *   GET    /api/teachers（表单选择教师）
 * ========================================================================== */
(function (window, document) {
  'use strict';

  var AJ = window.AJ;
  var Api = AJ.Api;
  var UI = AJ.UI;
  var util = AJ.util;

  /** 开课状态候选值 */
  var OFFERING_STATUSES = ['正常开课', '停开', '已结课', '筹建'];
  /** 是/否 下拉 */
  var BOOL_OPTIONS = [{ value: 'true', label: '是' }, { value: 'false', label: '否' }];

  /** 学期候选（当前年份前后各一年，覆盖常见演示数据） */
  function semesterOptions() {
    var y = new Date().getFullYear();
    var out = [];
    for (var i = 1; i >= -1; i--) {
      var yy = y - i;
      out.push(yy + '-春季学期');
      out.push(yy + '-秋季学期');
    }
    return out;
  }

  function asArray(res) {
    if (!res) { return []; }
    if (Object.prototype.toString.call(res) === '[object Array]') { return res; }
    return res.records || res.list || res.rows || [];
  }

  /* ==================================================================
   * 1. 远程选项加载器
   * ================================================================ */
  /** 课程下拉：GET /api/courses（取前 200 条，按关键词过滤） */
  function courseLoader(keyword) {
    return Api.get('/courses', { keyword: keyword || '', page: 1, size: 200 })
      .then(function (res) {
        return asArray(res).map(function (c) {
          var code = util.pick(c, ['courseCode', 'code'], '');
          var name = util.pick(c, ['courseName', 'name'], '未命名课程');
          return { value: c.id, label: (code ? code + ' ' : '') + name };
        });
      });
  }

  /** 教师下拉：GET /api/teachers */
  function teacherLoader(keyword) {
    return Api.get('/teachers', { keyword: keyword || '', page: 1, size: 200 })
      .then(function (res) {
        return asArray(res).map(function (t) {
          var name = util.pick(t, ['teacherName', 'name', 'realName'], '未命名教师');
          var dept = util.pick(t, ['departmentCode', 'departmentName'], '');
          var title = util.pick(t, ['title', 'professionalTitle'], '');
          return { value: t.id, label: name + (title ? ' · ' + title : '') + (dept ? ' · ' + dept : '') };
        });
      });
  }

  /* ==================================================================
   * 2. 表单
   * ================================================================ */
  function formHtml(offering) {
    offering = offering || {};
    var wrap = document.createElement('div');

    // 课程（远程下拉）
    wrap.appendChild(UI.remoteSelect({
      name: 'courseId', label: '课程', required: true, loader: courseLoader,
      value: util.pick(offering, ['courseId'], ''),
      valueLabel: util.pick(offering, ['courseName', 'courseCode'], ''),
      placeholder: '请选择课程'
    }));

    // 教师（远程下拉）
    wrap.appendChild(UI.remoteSelect({
      name: 'teacherId', label: '授课教师', required: true, loader: teacherLoader,
      value: util.pick(offering, ['teacherId'], ''),
      valueLabel: util.pick(offering, ['teacherName'], ''),
      placeholder: '请选择授课教师'
    }));

    // 其余字段
    var rest = document.createElement('div');
    rest.innerHTML =
      '<div class="form-item"><label class="form-label">学期<span class="req">*</span></label>' +
      '<input class="input" name="semester" list="semesterList" placeholder="如 2026-春季学期" value="' +
      util.escape(util.pick(offering, ['semester'], '')) + '">' +
      '<datalist id="semesterList">' +
      semesterOptions().map(function (s) { return '<option value="' + util.escape(s) + '"></option>'; }).join('') +
      '</datalist></div>' +

      '<div class="form-item"><label class="form-label">教学班名称</label>' +
      '<input class="input" name="className" placeholder="如 数据结构-01班" value="' +
      util.escape(util.pick(offering, ['className', 'name'], '')) + '"></div>' +

      '<div class="form-item"><label class="form-label">开课院系</label>' +
      '<input class="input" name="departmentCode" placeholder="如 计算机科学与工程学院" value="' +
      util.escape(util.pick(offering, ['departmentCode', 'departmentName'], '')) + '"></div>' +

      '<div class="form-item"><label class="form-label">学生人次</label>' +
      '<input class="input" type="number" name="studentCount" min="0" placeholder="如 120" value="' +
      util.escape(util.pick(offering, ['studentCount', 'studentTotal'], '')) + '"></div>' +

      '<div class="form-item"><label class="form-label">开课状态</label>' +
      '<select class="select" name="offeringStatus">' +
      '<option value="">请选择开课状态</option>' +
      OFFERING_STATUSES.map(function (s) {
        var sel = String(util.pick(offering, ['offeringStatus', 'status'], '')) === s ? ' selected' : '';
        return '<option value="' + util.escape(s) + '"' + sel + '>' + util.escape(s) + '</option>';
      }).join('') +
      '</select></div>';
    wrap.appendChild(rest);

    return wrap;
  }

  function validate(values) {
    if (!values.courseId) { return '请选择课程'; }
    if (!values.teacherId) { return '请选择授课教师'; }
    if (!String(values.semester || '').trim()) { return '请填写学期'; }
    if (values.studentCount !== null && values.studentCount !== '' && Number(values.studentCount) < 0) {
      return '学生人次不能为负数';
    }
    return null;
  }

  function buildPayload(values) {
    var payload = {
      courseId: Number(values.courseId),
      teacherId: Number(values.teacherId),
      semester: String(values.semester || '').trim()
    };
    if (values.className) { payload.className = values.className; }
    if (values.departmentCode) { payload.departmentCode = values.departmentCode; }
    if (values.offeringStatus) { payload.offeringStatus = values.offeringStatus; }
    if (values.studentCount !== null && values.studentCount !== '') { payload.studentCount = Number(values.studentCount); }
    return payload;
  }

  function openForm(offering, onChanged) {
    var isEdit = !!offering;
    UI.formModal({
      title: isEdit ? '编辑开课记录' : '新建开课记录',
      width: 'wide',
      bodyNode: formHtml(offering),
      submitText: isEdit ? '保存修改' : '创建开课',
      onValidate: validate,
      onSubmit: function (values) {
        var payload = buildPayload(values);
        var p = isEdit
          ? Api.put('/offerings/' + encodeURIComponent(offering.id), payload)
          : Api.post('/offerings', payload);
        return p.then(function () {
          UI.toast(isEdit ? '开课记录已更新' : '开课记录已创建', 'success');
          if (typeof onChanged === 'function') { onChanged(); }
        }, function (err) {
          UI.toast((err && err.message) || '保存失败', 'error');
          return false;
        });
      }
    });
  }

  function confirmDelete(offering, onDone) {
    var label = util.pick(offering, ['courseName', 'className'], '该开课记录');
    UI.confirm({
      title: '删除开课记录',
      message: '确定要删除「' + label + '」的开课记录吗？',
      warn: '若该教学班已有听评课或评价数据，后端可能拒绝删除。',
      confirmText: '确认删除',
      onConfirm: function () {
        return Api.del('/offerings/' + encodeURIComponent(offering.id))
          .then(function () {
            UI.toast('开课记录已删除', 'success');
            if (typeof onDone === 'function') { onDone(); }
          }, function (err) {
            UI.toast((err && err.message) || '删除失败', 'error');
            return false;
          });
      }
    });
  }

  /* ==================================================================
   * 3. 学生人次明细抽屉
   * ================================================================ */
  function openStudentCounts(offering, onChanged) {
    var canWrite = Api.can('offering:write');
    var d = UI.drawer({
      title: '学生人次明细',
      bodyHtml: UI.loadingBlock('正在加载学生人次明细…')
    });

    load();

    function load() {
      d.body.innerHTML = UI.loadingBlock('正在加载学生人次明细…');
      Api.get('/offerings/' + encodeURIComponent(offering.id) + '/student-counts')
        .then(guard(function (res) {
          var rows = asArray(res);
          d.body.innerHTML = '';

          d.body.appendChild(UI.descList([
            { label: '课程', value: util.pick(offering, ['courseName', 'courseCode'], '') },
            { label: '教学班', value: util.pick(offering, ['className'], '') },
            { label: '授课教师', value: util.pick(offering, ['teacherName'], '') },
            { label: '学期', value: util.pick(offering, ['semester'], '') },
            {
              label: '合计人次', raw: true,
              value: '<span class="text-strong">' + util.escape(sumCount(rows)) + '</span>'
            }
          ]));

          d.body.appendChild(UI.descSection('人次记录明细', rows.length
            ? UI.miniTable([
              { title: '学年', key: 'academicYear' },
              { title: '学期', key: 'semester' },
              { title: '班级', key: 'className' },
              { title: '学生人次', key: 'studentCount', align: 'right',
                render: function (v, row) { return util.pick(row, ['studentCount', 'count', 'num'], '—'); } },
              { title: '数据来源', key: 'dataSource' },
              { title: '备注', key: 'remark' }
            ], rows, '暂无学生人次明细')
            : null));

          if (rows.length) {
            var chartWrap = UI.descSection('人次分布', null);
            var chart = AJ.Charts.barChart(rows.map(function (r) {
              return {
                label: util.pick(r, ['semester', 'academicYear', 'className'], '—'),
                value: util.toNumber(util.pick(r, ['studentCount', 'count', 'num'], 0))
              };
            }), { height: 200, valueUnit: '人次' });
            chartWrap.appendChild(chart.el);
            d.body.appendChild(chartWrap);
            d._chart = chart;
          }

          if (canWrite) {
            d.body.appendChild(UI.descSection('新增 / 更新人次记录',
              buildCountForm(offering, onChanged, load)));
          }
        }), guard(function (err) {
          d.body.innerHTML = '';
          d.body.appendChild(UI.errorBox((err && err.message) || '学生人次明细加载失败', load));
        }));
    }
  }

  function sumCount(rows) {
    var s = 0;
    rows.forEach(function (r) { s += util.toNumber(util.pick(r, ['studentCount', 'count', 'num'], 0)); });
    return s;
  }

  /** 抽屉里的“新增人次”表单 */
  function buildCountForm(offering, onChanged, onSaved) {
    var wrap = document.createElement('div');

    var form = document.createElement('div');
    form.style.display = 'grid';
    form.style.gap = '10px';
    form.innerHTML =
      '<div class="form-item" style="margin:0"><label class="form-label">学年</label>' +
      '<input class="input" name="academicYear" placeholder="如 2025-2026"></div>' +

      '<div class="form-item" style="margin:0"><label class="form-label">学期</label>' +
      '<input class="input" name="semester" value="' + util.escape(util.pick(offering, ['semester'], '')) + '"></div>' +

      '<div class="form-item" style="margin:0"><label class="form-label">班级</label>' +
      '<input class="input" name="className" value="' + util.escape(util.pick(offering, ['className'], '')) + '"></div>' +

      '<div class="form-item" style="margin:0"><label class="form-label">学生人次<span class="req">*</span></label>' +
      '<input class="input" type="number" name="studentCount" min="0" placeholder="如 60"></div>' +

      '<div class="form-item" style="margin:0"><label class="form-label">数据来源</label>' +
      '<input class="input" name="dataSource" placeholder="如 教务系统导出 / 人工录入"></div>' +

      '<div class="form-item" style="margin:0"><label class="form-label">备注</label>' +
      '<input class="input" name="remark" placeholder="选填"></div>';
    wrap.appendChild(form);

    var btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'btn btn-primary btn-sm';
    btn.textContent = '提交人次记录';
    btn.style.marginTop = '10px';
    btn.addEventListener('click', function () {
      var values = {};
      var controls = form.querySelectorAll('[name]');
      for (var i = 0; i < controls.length; i++) {
        var el = controls[i];
        values[el.getAttribute('name')] = el.type === 'number'
          ? (el.value === '' ? null : Number(el.value))
          : el.value;
      }
      if (values.studentCount === null || values.studentCount === undefined) {
        UI.toast('请填写学生人次', 'warning');
        return;
      }
      if (values.studentCount < 0) {
        UI.toast('学生人次不能为负数', 'warning');
        return;
      }

      btn.disabled = true;
      btn.textContent = '提交中…';
      Api.post('/offerings/' + encodeURIComponent(offering.id) + '/student-counts', values)
        .then(function () {
          UI.toast('人次记录已保存', 'success');
          if (typeof onSaved === 'function') { onSaved(); }
          if (typeof onChanged === 'function') { onChanged(); }
        }, function (err) {
          UI.toast((err && err.message) || '保存失败', 'error');
        })
        .then(function () {
          btn.disabled = false;
          btn.textContent = '提交人次记录';
        });
    });
    wrap.appendChild(btn);
    return wrap;
  }

  /* ==================================================================
   * 4. 页面渲染
   * ================================================================ */
  function render(container) {
    var guard = AJ.Router.guard();
    var role = Api.roleCode();
    var isTeacher = role === 'TEACHER';
    var canWrite = Api.can('offering:write');

    // —— 页头 ——
    var head = document.createElement('div');
    head.className = 'page-head';
    head.innerHTML =
      '<h2 class="page-title">开课与授课管理</h2>' +
      '<p class="page-desc">' +
      (isTeacher
        ? '当前角色为教师，仅显示本人承担的教学班开课情况。'
        : '维护教学班开课信息，可按学期、教师、院系、开课状态与督导状态筛选。') +
      '</p>';
    container.appendChild(head);

    // —— 筛选栏 ——
    var filters = UI.filterBar([
      { name: 'keyword', label: '关键词', placeholder: '课程名称 / 教学班' },
      { name: 'semester', label: '学期', type: 'select', options: semesterOptions(), placeholder: '全部学期' },
      { name: 'offeringStatus', label: '开课状态', type: 'select', options: OFFERING_STATUSES, placeholder: '全部状态' },
      { name: 'offered', label: '是否开课', type: 'select', options: BOOL_OPTIONS, placeholder: '全部' },
      { name: 'supervised', label: '是否已督导', type: 'select', options: BOOL_OPTIONS, placeholder: '全部' }
    ], {
      onSearch: function (values) { tbl.setQuery(values); }
    });
    container.appendChild(filters.el);

    // 教师筛选（非教师角色才显示，选项懒加载）
    var teacherFilter = UI.remoteSelect({
      name: 'teacherId', label: '授课教师', loader: teacherLoader, placeholder: '全部教师'
    });
    teacherFilter.style.minWidth = '200px';
    teacherFilter.querySelector('.form-label').style.fontSize = '12px';
    teacherFilter.querySelector('.form-label').style.color = '#6b7280';
    teacherFilter.querySelector('input').style.display = 'none';
    teacherFilter.querySelector('select').addEventListener('change', function (e) {
      tbl.setQuery({ teacherId: e.target.value });
    });
    if (!isTeacher) {
      filters.el.insertBefore(teacherFilter, filters.el.querySelector('.filter-actions'));
    }

    // —— 表格 ——
    var tbl = UI.table({
      columns: [
        { title: '学期', key: 'semester', width: '120px' },
        {
          title: '课程', key: 'courseName',
          render: function (v, row) {
            return util.pick(row, ['courseName', 'courseCode'], '');
          }
        },
        {
          title: '教学班', key: 'className', width: '140px',
          render: function (v, row) { return util.pick(row, ['className', 'name'], ''); }
        },
        {
          title: '授课教师', key: 'teacherName', width: '100px',
          render: function (v, row) { return util.pick(row, ['teacherName'], ''); }
        },
        {
          title: '开课院系', key: 'departmentCode', width: '150px',
          render: function (v, row) { return util.pick(row, ['departmentCode', 'departmentName'], ''); }
        },
        {
          title: '学生人次', key: 'studentCount', width: '86px', align: 'right',
          render: function (v, row) { return util.pick(row, ['studentCount', 'studentTotal'], '—'); }
        },
        {
          title: '开课状态', key: 'offeringStatus', width: '92px', align: 'center', raw: true,
          render: function (v, row) {
            var s = util.pick(row, ['offeringStatus', 'status'], '');
            return '<span class="badge badge-' + util.statusTone(s) + '">' + util.escape(s || '未知') + '</span>';
          }
        },
        {
          title: '督导状态', key: 'supervised', width: '92px', align: 'center', raw: true,
          render: function (v, row) {
            var s = util.pick(row, ['supervised', 'supervisionStatus'], null);
            if (s === null || s === undefined || s === '') { return '<span class="badge badge-gray">未知</span>'; }
            var done = (s === true || /已督导|已完成|FINISHED|true/i.test(String(s)));
            return '<span class="badge badge-' + (done ? 'green' : 'orange') + '">' +
              (done ? '已督导' : '未督导') + '</span>';
          }
        },
        {
          title: '操作', width: '160px', className: 'actions', raw: true,
          render: function (v, row) {
            var html = '<button class="btn-link" data-act="counts" data-id="' + util.escape(row.id) + '">学生人次</button>';
            if (canWrite) {
              html += '<button class="btn-link" data-act="edit" data-id="' + util.escape(row.id) + '">编辑</button>' +
                '<button class="btn-link danger" data-act="del" data-id="' + util.escape(row.id) + '">删除</button>';
            }
            return html;
          }
        }
      ],
      loader: function (query) {
        return Api.get('/offerings', query);
      },
      emptyTitle: '暂无开课记录',
      emptyDesc: '可调整筛选条件，或新建开课记录'
    });
    container.appendChild(tbl.el);

    // —— 工具栏 ——
    var toolbar = document.createElement('div');
    toolbar.className = 'page-toolbar';

    var exportBtn = document.createElement('button');
    exportBtn.type = 'button';
    exportBtn.className = 'btn';
    exportBtn.textContent = '导出 CSV';
    exportBtn.addEventListener('click', function () {
      exportBtn.disabled = true;
      exportBtn.textContent = '导出中…';
      Api.exportCsv('/offerings/export', safeQuery(tbl), '开课列表.csv')
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
      addBtn.textContent = '新建开课';
      addBtn.addEventListener('click', function () { openForm(null, tbl.reload); });
      toolbar.appendChild(addBtn);
    }
    head.appendChild(toolbar);

    // —— 行内操作 ——
    tbl.el.addEventListener('click', function (e) {
      var btn = e.target && e.target.closest ? e.target.closest('button[data-act]') : null;
      if (!btn) { return; }
      var act = btn.getAttribute('data-act');
      var id = btn.getAttribute('data-id');

      // 从当前列表数据里找到整行对象，避免额外请求
      var row = null;
      tbl.getRecords().forEach(function (r) { if (String(r.id) === String(id)) { row = r; } });
      row = row || { id: id };

      if (act === 'counts') {
        openStudentCounts(row, tbl.reload);
      } else if (act === 'edit') {
        Api.get('/offerings/' + encodeURIComponent(id)).then(guard(function (o) {
          openForm(o || row, tbl.reload);
        }), guard(function (err) {
          UI.toast((err && err.message) || '开课详情加载失败', 'error');
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
    ['keyword', 'semester', 'teacherId', 'departmentCode', 'offeringStatus', 'offered', 'supervised']
      .forEach(function (k) { if (q[k]) { out[k] = q[k]; } });
    return out;
  }

  AJ.pages.offerings = {
    title: '开课与授课管理',
    roles: ['ADMIN', 'DIRECTOR', 'COURSE_ADMIN', 'SUPERVISOR', 'TEACHER'],
    render: render
  };

})(window, document);
