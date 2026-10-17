/* ============================================================================
 * pages/courses.js —— 课程信息管理（契约第 2 节 / US-02、US-05）
 *
 * 功能：
 *   · 列表：分页 + 关键词/院系/课程类型/状态筛选
 *   · 详情抽屉：课程简介全文、授课教师、教学班数、挂接资源
 *   · 新建 / 编辑 / 删除（需 course:write；COURSE_ADMIN 只读，不显示写按钮）
 *   · 导出 CSV：GET /api/courses/export
 *   · CSV 批量导入：POST /api/courses/import（选择本地文件，读取文本后 POST）
 *
 * 用到的接口：
 *   GET    /api/courses?keyword&departmentCode&courseType&status&page&size
 *   GET    /api/courses/{id}
 *   GET    /api/courses/{id}/offerings
 *   GET    /api/courses/{id}/resources
 *   POST   /api/courses/{id}/resources      挂接资源
 *   DELETE /api/courses/{id}/resources/{rid} 解除挂接（软解绑）
 *   GET    /api/resources                   挂接候选资源（按关键词/类型筛选）
 *   POST   /api/courses
 *   PUT    /api/courses/{id}
 *   DELETE /api/courses/{id}
 *   GET    /api/courses/export
 *   POST   /api/courses/import
 * ========================================================================== */
(function (window, document) {
  'use strict';

  var AJ = window.AJ;
  var Api = AJ.Api;
  var UI = AJ.UI;
  var util = AJ.util;

  /** 课程类型候选值（datalist 提示，后端可自由取值） */
  var TYPES = ['专业必修课', '专业选修课', '通识必修课', '通识选修课', '实践课'];
  /** 课程状态候选值 */
  var STATUSES = ['正常', '停用', '筹建'];
  /** 资源类型候选值（与后端 ResourceService.resourceTypes 保持一致） */
  var RESOURCE_TYPES = ['课件', '教案', '视频', '习题', '参考文献'];

  /** 院系下拉候选（从列表数据自动归纳，避免依赖额外的院系接口） */
  var departmentOptions = [];

  /** 兼容后端返回数组或分页对象 */
  function asArray(res) {
    if (!res) { return []; }
    if (Object.prototype.toString.call(res) === '[object Array]') { return res; }
    return res.records || res.list || res.rows || [];
  }

  /**
   * 路由竞态守卫（模块级入口）。
   *
   * <p>详情抽屉、行内请求在 render 作用域之外发起，必须在此按调用时令牌重新取 guard，
   * 不能复用 render 内的局部变量（原实现直接引用未定义变量，导致抽屉打不开）。
   */
  function guardTask(fn) {
    return AJ.Router.guard()(fn);
  }

  /** 课程详情接口返回 {course, offerings, resources}，统一解包为课程对象 */
  function unwrapCourse(res) {
    return res && res.course ? res.course : res;
  }

  /** 生成带候选项的下拉框 HTML（当前值不在候选里时补一个选项，避免编辑丢值） */
  function selectHtml(name, options, value, placeholder) {
    var html = '<select class="select" name="' + name + '">';
    var matched = false;
    html += '<option value="">' + util.escape(placeholder || '请选择') + '</option>';
    (options || []).forEach(function (o) {
      var v = typeof o === 'object' ? o.value : o;
      var label = typeof o === 'object' ? o.label : o;
      var sel = String(value) === String(v) ? ' selected' : '';
      if (sel) { matched = true; }
      html += '<option value="' + util.escape(v) + '"' + sel + '>' + util.escape(label) + '</option>';
    });
    if (value && !matched) {
      html += '<option value="' + util.escape(value) + '" selected>' + util.escape(value) + '</option>';
    }
    html += '</select>';
    return html;
  }

  /* ==================================================================
   * 1. 表单
   * ================================================================ */
  function courseFormHtml(course) {
    course = course || {};
    return '' +
      '<div class="form-item"><label class="form-label">课程编号<span class="req">*</span></label>' +
      '<input class="input" name="courseCode" maxlength="32" placeholder="如 CS101" value="' +
      util.escape(util.pick(course, ['courseCode', 'code'], '')) + '"></div>' +

      '<div class="form-item"><label class="form-label">课程名称<span class="req">*</span></label>' +
      '<input class="input" name="courseName" maxlength="64" placeholder="如 数据结构" value="' +
      util.escape(util.pick(course, ['courseName', 'name'], '')) + '"></div>' +

      '<div class="form-item"><label class="form-label">课程类型</label>' +
      '<input class="input" name="courseType" list="courseTypeList" placeholder="如 专业必修课" value="' +
      util.escape(util.pick(course, ['courseType', 'type'], '')) + '">' +
      '<datalist id="courseTypeList">' +
      TYPES.map(function (t) { return '<option value="' + util.escape(t) + '"></option>'; }).join('') +
      '</datalist></div>' +

      '<div class="form-item"><label class="form-label">开课院系</label>' +
      '<input class="input" name="departmentCode" placeholder="如 计算机科学与工程学院" value="' +
      util.escape(util.pick(course, ['departmentCode', 'departmentName'], '')) + '"></div>' +

      '<div class="form-item"><label class="form-label">学分</label>' +
      '<input class="input" type="number" name="credit" step="0.5" min="0" placeholder="如 3" value="' +
      util.escape(util.pick(course, ['credit'], '')) + '"></div>' +

      '<div class="form-item"><label class="form-label">总学时</label>' +
      '<input class="input" type="number" name="totalHours" min="0" placeholder="如 48" value="' +
      util.escape(util.pick(course, ['totalHours', 'hours'], '')) + '"></div>' +

      '<div class="form-item"><label class="form-label">课程状态</label>' +
      selectHtml('status', STATUSES, course.status, '请选择状态') + '</div>' +

      '<div class="form-item"><label class="form-label">课程简介</label>' +
      '<textarea class="textarea" name="description" rows="5" placeholder="面向对象、主要内容、先修要求等">' +
      util.escape(util.pick(course, ['description', 'introduction', 'courseIntro'], '')) + '</textarea></div>';
  }

  function validateCourse(values) {
    if (!String(values.courseCode || '').trim()) { return '请填写课程编号'; }
    if (!String(values.courseName || '').trim()) { return '请填写课程名称'; }
    if (values.credit !== null && values.credit !== '' && Number(values.credit) < 0) { return '学分不能为负数'; }
    if (values.totalHours !== null && values.totalHours !== '' && Number(values.totalHours) < 0) { return '总学时不能为负数'; }
    return null;
  }

  /** 组装提交载荷（只提交有值的字段） */
  function buildPayload(values) {
    var payload = {
      courseCode: String(values.courseCode || '').trim(),
      courseName: String(values.courseName || '').trim()
    };
    if (values.courseType) { payload.courseType = values.courseType; }
    if (values.departmentCode) { payload.departmentCode = values.departmentCode; }
    if (values.status) { payload.status = values.status; }
    if (values.description) { payload.description = values.description; }
    if (values.credit !== null && values.credit !== '') { payload.credit = Number(values.credit); }
    if (values.totalHours !== null && values.totalHours !== '') { payload.totalHours = Number(values.totalHours); }
    return payload;
  }

  function openForm(course, onChanged) {
    var isEdit = !!course;
    UI.formModal({
      title: isEdit ? '编辑课程' : '新建课程',
      width: 'wide',
      fieldsHtml: courseFormHtml(course),
      submitText: isEdit ? '保存修改' : '创建课程',
      onValidate: validateCourse,
      onSubmit: function (values) {
        var payload = buildPayload(values);
        var p = isEdit
          ? Api.put('/courses/' + encodeURIComponent(course.id), payload)
          : Api.post('/courses', payload);
        return p.then(function () {
          UI.toast(isEdit ? '课程已更新' : '课程已创建', 'success');
          if (typeof onChanged === 'function') { onChanged(); }
        }, function (err) {
          UI.toast((err && err.message) || '保存失败', 'error');
          return false;
        });
      }
    });
  }

  function confirmDelete(course, onDone) {
    var name = util.pick(course, ['courseName', 'name'], '该课程');
    UI.confirm({
      title: '删除课程',
      message: '确定要删除课程「' + name + '」吗？',
      warn: '课程若已存在开课记录将无法删除（后端会返回冲突提示）。',
      confirmText: '确认删除',
      onConfirm: function () {
        return Api.del('/courses/' + encodeURIComponent(course.id))
          .then(function () {
            UI.toast('课程已删除', 'success');
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
      title: '课程详情',
      bodyHtml: UI.loadingBlock('正在加载课程详情…')
    });

    Api.get('/courses/' + encodeURIComponent(id))
      .then(guardTask(function (res) {
        d.body.innerHTML = '';
        var course = unwrapCourse(res);
        if (!course) {
          d.body.appendChild(UI.emptyStateEl('课程不存在', '该课程可能已被删除'));
          return;
        }
        renderDetail(d, course, onChanged);
      }), guardTask(function (err) {
        d.body.innerHTML = '';
        d.body.appendChild(UI.errorBox((err && err.message) || '课程详情加载失败'));
      }));
  }

  function renderDetail(d, course, onChanged) {
    var name = util.pick(course, ['courseName', 'name'], '未命名课程');
    d.el.querySelector('.drawer-title').textContent = '课程详情 · ' + name;

    // 基本信息
    d.body.appendChild(UI.descList([
      { label: '课程编号', value: util.pick(course, ['courseCode', 'code'], '') },
      { label: '课程名称', value: name },
      { label: '课程类型', value: util.pick(course, ['courseType', 'type'], '') },
      { label: '开课院系', value: util.pick(course, ['departmentCode', 'departmentName'], '') },
      { label: '学分', value: util.pick(course, ['credit'], '') },
      { label: '总学时', value: util.pick(course, ['totalHours', 'hours'], '') },
      {
        label: '课程状态', raw: true,
        value: '<span class="badge badge-' + util.statusTone(course.status) + '">' +
          util.escape(course.status || '未知') + '</span>'
      },
      { label: '授课教师', value: util.pick(course, ['teacherNames', 'teacherName', 'teachers'], '') },
      { label: '教学班数', value: util.pick(course, ['offeringCount', 'offeringNum'], '') }
    ]));

    // 课程简介全文
    d.body.appendChild(UI.descSection('课程简介',
      UI.descText(util.pick(course, ['description', 'introduction', 'courseIntro'], ''))));

    // 开课情况
    var offeringSection = UI.descSection('开课情况', UI.loadingBlock('加载中…'));
    d.body.appendChild(offeringSection);

    // 挂接资源（US-07：内联挂接面板 + 两次点击确认解绑）
    var resourceSection = buildResourceSection(course, onChanged);
    d.body.appendChild(resourceSection.el);

    // 写操作
    if (Api.can('course:write')) {
      d.body.appendChild(UI.descSection('操作', buildDetailActions(course, d, onChanged)));
    }

    Api.get('/courses/' + encodeURIComponent(course.id) + '/offerings')
      .then(guardTask(function (res) {
        UI.fillSection(offeringSection, UI.miniTable([
          { title: '学期', key: 'semester' },
          { title: '教学班', key: 'className' },
          { title: '授课教师', key: 'teacherName' },
          { title: '学生人次', key: 'studentCount', align: 'right',
            render: function (v, row) { return util.pick(row, ['studentCount', 'studentTotal'], '—'); } },
          { title: '状态', key: 'offeringStatus' }
        ], asArray(res), '该课程暂无开课记录'));
      }), guardTask(function (err) {
        UI.fillSection(offeringSection, UI.errorBox((err && err.message) || '开课记录加载失败'));
      }));

    resourceSection.load();
  }

  /**
   * 构建「挂接的教学资源」分区。
   *
   * <p>采用内联面板而非第二层抽屉：`UI.drawer` 与 `UI.formModal` 存在 z-index 叠层冲突，
   * 且 Esc 会同时关闭两层。挂接候选仅保留「本课程资源」或「未绑定教学班的公共资源」，
   * 与 `course_resource` 的课程维度语义一致。
   */
  function buildResourceSection(course, onChanged) {
    var section = UI.descSection('挂接的教学资源', UI.loadingBlock('加载中…'));
    var canWrite = Api.can('resource:write');
    var resources = [];
    var confirmId = null;
    var panelOpen = false;
    var keyword = '';
    var resourceType = '';
    var candidates = [];
    var candidateBox = null;
    var candidateLoading = false;
    var candidateError = '';

    function load() {
      UI.fillSection(section, UI.loadingBlock('加载中…'));
      Api.get('/courses/' + encodeURIComponent(course.id) + '/resources')
        .then(guardTask(function (res) {
          resources = asArray(res);
          confirmId = null;
          paint();
        }), guardTask(function (err) {
          UI.fillSection(section, UI.errorBox((err && err.message) || '资源列表加载失败', load));
        }));
    }

    function paint() {
      var wrap = document.createElement('div');
      // 先挂事件，再填充内容；重新 paint 后监听器随之替换，不会重复绑定
      wrap.addEventListener('click', function (e) {
        var btn = e.target && e.target.closest ? e.target.closest('button[data-act="detach"]') : null;
        if (!btn) { return; }
        var rid = btn.getAttribute('data-id');
        if (String(confirmId) !== String(rid)) {
          confirmId = rid;
          paint();
          return;
        }
        confirmId = null;
        Api.del('/courses/' + encodeURIComponent(course.id) + '/resources/' + encodeURIComponent(rid))
          .then(guardTask(function () {
            UI.toast('已解除挂接', 'success');
            load();
            if (typeof onChanged === 'function') { onChanged(); }
          }), guardTask(function (err) {
            UI.toast((err && err.message) || '解除挂接失败', 'error');
          }));
      });

      var columns = [
        { title: '资源名称', key: 'resourceName' },
        { title: '类型', key: 'resourceType' },
        { title: '状态', key: 'status' },
        {
          title: '链接', raw: true,
          render: function (v, row) {
            var u = util.pick(row, ['fileUrl', 'url', 'link'], '');
            return u ? '<a href="' + util.escape(u) + '" target="_blank" rel="noopener noreferrer">打开</a>' : '—';
          }
        }
      ];
      if (canWrite) {
        columns.push({
          title: '操作', raw: true, align: 'center',
          render: function (v, row) {
            var confirming = String(confirmId) === String(row.id);
            return '<button class="btn-link' + (confirming ? ' danger' : '') + '"' +
              ' data-act="detach" data-id="' + util.escape(row.id) + '">' +
              (confirming ? '确认解除' : '解除挂接') + '</button>';
          }
        });
      }

      if (canWrite) {
        var tools = document.createElement('div');
        tools.style.display = 'flex';
        tools.style.gap = '8px';
        tools.style.marginBottom = '10px';
        var attachBtn = document.createElement('button');
        attachBtn.type = 'button';
        attachBtn.className = 'btn btn-sm';
        attachBtn.textContent = panelOpen ? '收起挂接面板' : '挂接资源';
        attachBtn.addEventListener('click', function () {
          panelOpen = !panelOpen;
          paint();
        });
        tools.appendChild(attachBtn);
        wrap.appendChild(tools);
      }

      wrap.appendChild(UI.miniTable(columns, resources, '该课程暂无挂接资源'));

      if (panelOpen) {
        wrap.appendChild(buildAttachPanel());
      }
      UI.fillSection(section, wrap);
    }

    function buildAttachPanel() {
      var panel = document.createElement('div');
      panel.style.background = '#f8fafc';
      panel.style.border = '1px solid #e2e8f0';
      panel.style.borderRadius = '6px';
      panel.style.padding = '10px';
      panel.style.marginTop = '10px';

      var bar = document.createElement('div');
      bar.style.display = 'flex';
      bar.style.gap = '8px';
      bar.style.marginBottom = '8px';

      var kw = document.createElement('input');
      kw.className = 'input';
      kw.placeholder = '资源名称 / 说明';
      kw.value = keyword;
      kw.style.maxWidth = '220px';
      kw.addEventListener('input', function () { keyword = kw.value; });

      var typeSel = document.createElement('select');
      typeSel.className = 'select';
      typeSel.style.maxWidth = '140px';
      typeSel.innerHTML = '<option value="">全部类型</option>' +
        RESOURCE_TYPES.map(function (t) {
          return '<option value="' + util.escape(t) + '">' + util.escape(t) + '</option>';
        }).join('');
      typeSel.value = resourceType;
      typeSel.addEventListener('change', function () { resourceType = typeSel.value; });

      var searchBtn = document.createElement('button');
      searchBtn.type = 'button';
      searchBtn.className = 'btn btn-sm';
      searchBtn.textContent = '搜索候选资源';
      searchBtn.addEventListener('click', searchCandidates);

      bar.appendChild(kw);
      bar.appendChild(typeSel);
      bar.appendChild(searchBtn);
      panel.appendChild(bar);

      candidateBox = document.createElement('div');
      candidateBox.addEventListener('click', function (e) {
        var btn = e.target && e.target.closest ? e.target.closest('button[data-attach-id]') : null;
        if (!btn) { return; }
        var rid = btn.getAttribute('data-attach-id');
        btn.disabled = true;
        Api.post('/courses/' + encodeURIComponent(course.id) + '/resources', { resourceId: Number(rid) })
          .then(guardTask(function () {
            UI.toast('资源已挂接', 'success');
            load();
            if (typeof onChanged === 'function') { onChanged(); }
          }), guardTask(function (err) {
            UI.toast((err && err.message) || '挂接失败', 'error');
            btn.disabled = false;
          }));
      });
      panel.appendChild(candidateBox);

      searchCandidates();
      return panel;
    }

    function searchCandidates() {
      candidateLoading = true;
      candidateError = '';
      renderCandidates();
      Api.get('/resources', {
        keyword: keyword || '', resourceType: resourceType || '', page: 1, size: 100
      }).then(guardTask(function (res) {
        var attached = {};
        resources.forEach(function (r) { attached[String(r.id)] = true; });
        candidates = asArray(res).filter(function (r) {
          if (attached[String(r.id)]) { return false; }
          // 未绑定教学班的公共资源，或属于当前课程的教学班资源
          return !r.offeringId || String(r.courseId) === String(course.id);
        });
        candidateLoading = false;
        renderCandidates();
      }), guardTask(function (err) {
        candidateLoading = false;
        candidateError = (err && err.message) || '候选资源加载失败';
        renderCandidates();
      }));
    }

    function renderCandidates() {
      if (!candidateBox) { return; }
      candidateBox.innerHTML = '';
      if (candidateLoading) {
        candidateBox.appendChild(UI.loadingBlock('正在加载候选资源…'));
        return;
      }
      if (candidateError) {
        candidateBox.appendChild(UI.errorBox(candidateError, searchCandidates));
        return;
      }
      candidateBox.appendChild(UI.miniTable([
        { title: '资源名称', key: 'resourceName' },
        { title: '类型', key: 'resourceType' },
        {
          title: '教学班', key: 'className',
          render: function (v, row) { return util.pick(row, ['className', 'offeringCode'], '公共资源'); }
        },
        {
          title: '操作', raw: true, align: 'center',
          render: function (v, row) {
            return '<button class="btn-link" data-attach-id="' + util.escape(row.id) + '">挂接</button>';
          }
        }
      ], candidates, '没有可挂接的资源（仅显示本课程或未绑定教学班的资源）'));
    }

    return { el: section, load: load };
  }

  function buildDetailActions(course, d, onChanged) {
    var wrap = document.createElement('div');
    wrap.style.display = 'flex';
    wrap.style.gap = '10px';

    var editBtn = document.createElement('button');
    editBtn.type = 'button';
    editBtn.className = 'btn btn-primary btn-sm';
    editBtn.textContent = '编辑课程';
    editBtn.addEventListener('click', function () {
      d.close();
      openForm(course, onChanged);
    });

    var delBtn = document.createElement('button');
    delBtn.type = 'button';
    delBtn.className = 'btn btn-danger btn-sm';
    delBtn.textContent = '删除课程';
    delBtn.addEventListener('click', function () {
      confirmDelete(course, function () {
        d.close();
        if (typeof onChanged === 'function') { onChanged(); }
      });
    });

    wrap.appendChild(editBtn);
    wrap.appendChild(delBtn);
    return wrap;
  }

  /* ==================================================================
   * 3. CSV 导入 / 模板
   * ================================================================ */
  function startImport(onDone) {
    UI.csvFilePicker(function (text, file) {
      var lines = text.split(/\r?\n/).filter(function (l) { return l.trim() !== ''; });
      var header = (lines[0] || '').trim();

      UI.formModal({
        title: 'CSV 批量导入课程',
        width: 'wide',
        fieldsHtml:
          '<div class="form-item"><label class="form-label">已选择文件</label>' +
          '<input class="input" value="' + util.escape(file.name) + '" disabled></div>' +
          '<div class="form-item"><label class="form-label">文件表头</label>' +
          '<input class="input" value="' + util.escape(header) + '" disabled></div>' +
          '<div class="form-item"><label class="form-label">预计数据行数</label>' +
          '<input class="input" value="' + Math.max(0, lines.length - 1) + '" disabled></div>' +
          '<div class="note-box"><span class="note-icon">!</span><div>' +
          '导入按课程编号去重，编号已存在时以后端返回的跳过/更新策略为准。建议表头：' +
          '课程编号、课程名称、课程类型、开课院系、学分、总学时、课程状态、课程简介。' +
          '</div></div>',
        submitText: '开始导入',
        onSubmit: function () {
          return Api.importCsv('/courses/import', text)
            .then(function (data) {
              var msg = '导入完成';
              if (data && typeof data === 'object') {
                var ok = util.pick(data, ['successCount', 'inserted', 'success', 'imported'], null);
                var skip = util.pick(data, ['skipCount', 'skipped', 'duplicated', 'failed'], null);
                if (ok !== null) { msg += '：成功 ' + ok + ' 条'; }
                if (skip !== null) { msg += '，跳过/失败 ' + skip + ' 条'; }
              } else if (typeof data === 'number') {
                msg += '：成功 ' + data + ' 条';
              }
              UI.toast(msg, 'success', 5000);
              if (typeof onDone === 'function') { onDone(); }
            }, function (err) {
              UI.toast((err && err.message) || '导入失败', 'error', 5000);
              return false;
            });
        }
      });
    });
  }

  /** 下载导入模板（前端本地生成） */
  function downloadTemplate() {
    var csv = UI.buildCsv(
      ['课程编号', '课程名称', '课程类型', '开课院系', '学分', '总学时', '课程状态', '课程简介'],
      [['CS101', '数据结构', '专业必修课', '计算机科学与工程学院', '4', '64', '正常',
        '线性表、树、图等基本数据结构与常用算法。']]
    );
    var blob = new window.Blob(['\ufeff' + csv], { type: 'text/csv;charset=UTF-8' });
    var url = window.URL.createObjectURL(blob);
    var a = document.createElement('a');
    a.href = url;
    a.download = '课程导入模板.csv';
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    window.setTimeout(function () { window.URL.revokeObjectURL(url); }, 1500);
  }

  /* ==================================================================
   * 4. 页面渲染
   * ================================================================ */
  function render(container) {
    var guard = AJ.Router.guard();
    var canWrite = Api.can('course:write');
    var isCourseAdmin = Api.roleCode() === 'COURSE_ADMIN';

    // —— 页头 ——
    var head = document.createElement('div');
    head.className = 'page-head';
    head.innerHTML =
      '<h2 class="page-title">课程信息管理</h2>' +
      '<p class="page-desc">统一维护课程库基础信息，支持按院系、类型、状态筛选，以及 CSV 批量导入导出。' +
      (isCourseAdmin ? '当前角色为课程管理员，仅可查看。' : '') + '</p>';
    container.appendChild(head);

    // —— 筛选栏 ——
    var filters = UI.filterBar([
      { name: 'keyword', label: '关键词', placeholder: '课程编号 / 课程名称' },
      { name: 'departmentCode', label: '开课院系', type: 'select', options: departmentOptions, placeholder: '全部院系' },
      { name: 'courseType', label: '课程类型', type: 'select', options: TYPES, placeholder: '全部类型' },
      { name: 'status', label: '课程状态', type: 'select', options: STATUSES, placeholder: '全部状态' }
    ], {
      onSearch: function (values) { tbl.setQuery(values); }
    });
    container.appendChild(filters.el);

    // —— 表格 ——
    var tbl = UI.table({
      columns: [
        {
          title: '课程编号', key: 'courseCode', width: '110px',
          render: function (v, row) { return util.pick(row, ['courseCode', 'code'], v); }
        },
        {
          title: '课程名称', key: 'courseName', raw: true,
          render: function (v, row) {
            return '<span class="text-strong">' +
              util.escape(util.pick(row, ['courseName', 'name'], '')) + '</span>';
          }
        },
        {
          title: '课程类型', key: 'courseType', width: '120px',
          render: function (v, row) { return util.pick(row, ['courseType', 'type'], ''); }
        },
        {
          title: '开课院系', key: 'departmentCode', width: '170px',
          render: function (v, row) { return util.pick(row, ['departmentCode', 'departmentName'], ''); }
        },
        { title: '学分', key: 'credit', width: '60px', align: 'right' },
        {
          title: '总学时', key: 'totalHours', width: '70px', align: 'right',
          render: function (v, row) { return util.pick(row, ['totalHours', 'hours'], '—'); }
        },
        {
          title: '教学班数', key: 'offeringCount', width: '82px', align: 'right',
          render: function (v, row) { return util.pick(row, ['offeringCount', 'offeringNum'], '—'); }
        },
        {
          title: '状态', key: 'status', width: '80px', align: 'center', raw: true,
          render: function (v) {
            return '<span class="badge badge-' + util.statusTone(v) + '">' + util.escape(v || '未知') + '</span>';
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
      loader: function (query) {
        return Api.get('/courses', query).then(guard(function (res) {
          // 用返回结果归纳院系下拉候选，便于后续按院系筛选
          var rows = asArray(res);
          var seen = {};
          departmentOptions.forEach(function (d) { seen[d] = true; });
          var added = false;
          rows.forEach(function (r) {
            var d = util.pick(r, ['departmentCode', 'departmentName'], '');
            if (d && !seen[d]) { seen[d] = true; added = true; }
          });
          if (added) { departmentOptions = Object.keys(seen); }
          return res;
        }));
      },
      emptyTitle: '暂无课程数据',
      emptyDesc: '可调整筛选条件，或使用“新建课程 / CSV 导入”录入数据'
    });
    container.appendChild(tbl.el);

    // —— 页头工具栏（依赖 tbl.reload，用闭包引用）——
    var toolbar = document.createElement('div');
    toolbar.className = 'page-toolbar';

    var exportBtn = document.createElement('button');
    exportBtn.type = 'button';
    exportBtn.className = 'btn';
    exportBtn.textContent = '导出 CSV';
    exportBtn.addEventListener('click', function () {
      exportBtn.disabled = true;
      exportBtn.textContent = '导出中…';
      Api.exportCsv('/courses/export', safeQuery(tbl), '课程列表.csv')
        .then(function (name) { UI.toast('已导出：' + name, 'success'); },
          function (err) { UI.toast((err && err.message) || '导出失败', 'error'); })
        .then(function () {
          exportBtn.disabled = false;
          exportBtn.textContent = '导出 CSV';
        });
    });
    toolbar.appendChild(exportBtn);

    if (canWrite) {
      toolbar.appendChild(mkBtn('CSV 导入', '', function () { startImport(tbl.reload); }));
      toolbar.appendChild(mkBtn('下载模板', '', downloadTemplate));
      toolbar.appendChild(mkBtn('新建课程', 'btn-primary', function () { openForm(null, tbl.reload); }));
    }
    head.appendChild(toolbar);

    // —— 行内操作事件委托 ——
    tbl.el.addEventListener('click', function (e) {
      var btn = e.target && e.target.closest ? e.target.closest('button[data-act]') : null;
      if (!btn) { return; }
      var act = btn.getAttribute('data-act');
      var id = btn.getAttribute('data-id');
      if (act === 'detail') {
        openDetail(id, tbl.reload);
      } else if (act === 'edit') {
        Api.get('/courses/' + encodeURIComponent(id)).then(guard(function (res) {
          openForm(unwrapCourse(res), tbl.reload);
        }), guard(function (err) {
          UI.toast((err && err.message) || '课程信息加载失败', 'error');
        }));
      } else if (act === 'del') {
        // 先取详情，让确认框显示课程名；取不到时退化为按 ID 删除
        Api.get('/courses/' + encodeURIComponent(id)).then(guard(function (res) {
          confirmDelete(unwrapCourse(res), tbl.reload);
        }), guard(function () {
          confirmDelete({ id: id, courseName: 'ID=' + id }, tbl.reload);
        }));
      }
    });

    tbl.reload();
  }

  /** 生成一个按钮元素 */
  function mkBtn(text, cls, onClick) {
    var b = document.createElement('button');
    b.type = 'button';
    b.className = 'btn' + (cls ? ' ' + cls : '');
    b.textContent = text;
    b.addEventListener('click', onClick);
    return b;
  }

  /** 取当前生效的筛选参数（导出时带上，保证导出与列表口径一致） */
  function safeQuery(tbl) {
    var q = tbl.getQuery ? tbl.getQuery() : {};
    var out = {};
    ['keyword', 'departmentCode', 'courseType', 'status'].forEach(function (k) {
      if (q[k]) { out[k] = q[k]; }
    });
    return out;
  }

  AJ.pages.courses = {
    title: '课程信息管理',
    roles: ['ADMIN', 'DIRECTOR', 'COURSE_ADMIN', 'SUPERVISOR'],
    render: render
  };

})(window, document);
