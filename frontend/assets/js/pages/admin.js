/* ============================================================================
 * pages/admin.js —— 用户与权限管理（契约第 1 节 / US-01）
 *
 * 三个标签页：
 *   1. 用户管理：列表（关键词/角色/启停筛选）+ 新建/编辑/删除/重置密码
 *   2. 角色权限矩阵：左侧角色下拉，右侧权限码开关组，
 *      GET /api/admin/roles、GET /api/admin/permissions、PUT /api/admin/roles/{roleCode}/permissions
 *   3. 操作日志：GET /api/admin/audit-logs（操作人/动作/对象类型筛选）
 *
 * 本页仅 ADMIN 可见（路由层已限角色 + user:read 权限）。
 * ========================================================================== */
(function (window, document) {
  'use strict';

  var AJ = window.AJ;
  var Api = AJ.Api;
  var UI = AJ.UI;
  var util = AJ.util;

  function asArray(res) {
    if (!res) { return []; }
    if (Object.prototype.toString.call(res) === '[object Array]') { return res; }
    return res.records || res.list || res.rows || [];
  }

  /** 角色码 → 中文名（优先用后端返回的 roleName） */
  function roleName(code, fallback) {
    return AJ.ROLE_NAMES[code] || fallback || code || '未分配';
  }

  /* ==================================================================
   * 1. 用户表单
   * ================================================================ */
  function userFormHtml(user, roleOptions) {
    user = user || {};
    var currentRole = util.pick(user, ['roleCode', 'role'], '');

    var roleSelect = '<select class="select" name="roleCode">';
    roleSelect += '<option value="">请选择角色</option>';
    var matched = false;
    (roleOptions || []).forEach(function (r) {
      var code = typeof r === 'object' ? (r.roleCode || r.code || r.value) : r;
      var label = typeof r === 'object'
        ? (r.roleName || r.name || roleName(code))
        : roleName(r);
      var sel = String(currentRole) === String(code) ? ' selected' : '';
      if (sel) { matched = true; }
      roleSelect += '<option value="' + util.escape(code) + '"' + sel + '>' + util.escape(label) + '</option>';
    });
    if (currentRole && !matched) {
      roleSelect += '<option value="' + util.escape(currentRole) + '" selected>' +
        util.escape(roleName(currentRole)) + '</option>';
    }
    roleSelect += '</select>';

    return '' +
      '<div class="form-item"><label class="form-label">用户名<span class="req">*</span></label>' +
      '<input class="input" name="username" maxlength="32" placeholder="登录账号，如 teacher3" value="' +
      util.escape(util.pick(user, ['username'], '')) + '"></div>' +

      '<div class="form-item"><label class="form-label">姓名</label>' +
      '<input class="input" name="realName" maxlength="32" placeholder="如 张三" value="' +
      util.escape(util.pick(user, ['realName', 'nickname', 'name'], '')) + '"></div>' +

      '<div class="form-item"><label class="form-label">角色<span class="req">*</span></label>' +
      roleSelect + '</div>' +

      '<div class="form-item"><label class="form-label">所属院系</label>' +
      '<input class="input" name="departmentCode" placeholder="选填，如 计算机科学与工程学院" value="' +
      util.escape(util.pick(user, ['departmentCode', 'departmentName'], '')) + '"></div>' +

      '<div class="form-item"><label class="form-label">联系电话</label>' +
      '<input class="input" name="phone" maxlength="20" placeholder="选填" value="' +
      util.escape(util.pick(user, ['phone', 'mobile'], '')) + '"></div>' +

      '<div class="form-item"><label class="form-label">电子邮箱</label>' +
      '<input class="input" name="email" maxlength="64" placeholder="选填" value="' +
      util.escape(util.pick(user, ['email'], '')) + '"></div>' +

      '<div class="form-item"><label class="form-label">账号状态</label>' +
      '<select class="select" name="enabled">' +
      '<option value="true"' + (user.enabled === false ? '' : ' selected') + '>启用</option>' +
      '<option value="false"' + (user.enabled === false ? ' selected' : '') + '>停用</option>' +
      '</select></div>' +

      (user.id ? '' :
        '<div class="form-item"><label class="form-label">初始密码<span class="req">*</span></label>' +
        '<input class="input" type="text" name="password" value="123456">' +
        '<div class="api-hint">演示环境默认初始密码 123456，可自行修改。</div></div>');
  }

  function validateUser(values, isEdit) {
    if (!String(values.username || '').trim()) { return '请填写用户名'; }
    if (!values.roleCode) { return '请选择角色'; }
    if (!isEdit && !String(values.password || '').trim()) { return '请填写初始密码'; }
    if (!isEdit && String(values.password || '').length < 6) { return '初始密码至少 6 位'; }
    if (values.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(values.email)) { return '电子邮箱格式不正确'; }
    return null;
  }

  function buildUserPayload(values, isEdit) {
    var payload = {
      username: String(values.username || '').trim(),
      roleCode: values.roleCode,
      enabled: values.enabled !== 'false'
    };
    if (values.realName) { payload.realName = values.realName; }
    if (values.departmentCode) { payload.departmentCode = values.departmentCode; }
    if (values.phone) { payload.phone = values.phone; }
    if (values.email) { payload.email = values.email; }
    if (!isEdit && values.password) { payload.password = values.password; }
    return payload;
  }

  function openUserForm(user, roleOptions, onChanged) {
    var isEdit = !!user;
    UI.formModal({
      title: isEdit ? '编辑用户' : '新建用户',
      width: 'wide',
      fieldsHtml: userFormHtml(user, roleOptions),
      submitText: isEdit ? '保存修改' : '创建用户',
      onValidate: function (values) { return validateUser(values, isEdit); },
      onSubmit: function (values) {
        var payload = buildUserPayload(values, isEdit);
        var p = isEdit
          ? Api.put('/admin/users/' + encodeURIComponent(user.id), payload)
          : Api.post('/admin/users', payload);
        return p.then(function () {
          UI.toast(isEdit ? '用户已更新' : '用户已创建', 'success');
          if (typeof onChanged === 'function') { onChanged(); }
        }, function (err) {
          UI.toast((err && err.message) || '保存失败', 'error');
          return false;
        });
      }
    });
  }

  function resetPassword(user, onDone) {
    UI.formModal({
      title: '重置密码 · ' + util.pick(user, ['username'], ''),
      width: 'narrow',
      fieldsHtml:
        '<div class="form-item"><label class="form-label">新密码<span class="req">*</span></label>' +
        '<input class="input" type="text" name="password" value="123456"></div>' +
        '<div class="note-box"><span class="note-icon">!</span><div>' +
        '重置后该用户需使用新密码重新登录。演示环境默认 123456。</div></div>',
      submitText: '确认重置',
      onValidate: function (values) {
        if (!String(values.password || '').trim()) { return '请填写新密码'; }
        if (String(values.password).length < 6) { return '密码至少 6 位'; }
        return null;
      },
      onSubmit: function (values) {
        return Api.put('/admin/users/' + encodeURIComponent(user.id) + '/password',
          { password: values.password })
          .then(function () {
            UI.toast('密码已重置', 'success');
            if (typeof onDone === 'function') { onDone(); }
          }, function (err) {
            UI.toast((err && err.message) || '重置失败', 'error');
            return false;
          });
      }
    });
  }

  function deleteUser(user, onDone) {
    var name = util.pick(user, ['realName', 'username'], '该用户');
    UI.confirm({
      title: '删除用户',
      message: '确定要删除用户「' + name + '」吗？',
      warn: '删除后该账号将无法登录，且此操作不可恢复。',
      confirmText: '确认删除',
      onConfirm: function () {
        return Api.del('/admin/users/' + encodeURIComponent(user.id))
          .then(function () {
            UI.toast('用户已删除', 'success');
            if (typeof onDone === 'function') { onDone(); }
          }, function (err) {
            UI.toast((err && err.message) || '删除失败', 'error');
            return false;
          });
      }
    });
  }

  /* ==================================================================
   * 2. 标签页骨架
   * ================================================================ */
  function render(container) {
    var head = document.createElement('div');
    head.className = 'page-head';
    head.innerHTML =
      '<h2 class="page-title">用户与权限管理</h2>' +
      '<p class="page-desc">管理账号与角色权限，查看操作日志。权限码变更即时生效，用户下次请求即按新权限鉴权。</p>';
    container.appendChild(head);

    // 标签页
    var tabs = document.createElement('div');
    tabs.className = 'filter-bar';
    tabs.style.padding = '6px';

    var panelHost = document.createElement('div');
    panelHost.style.marginTop = '16px';

    var defs = [
      { key: 'users', label: '用户管理', builder: buildUsersPanel },
      { key: 'roles', label: '角色权限矩阵', builder: buildRolesPanel },
      { key: 'logs', label: '操作日志', builder: buildLogsPanel }
    ];

    var activeKey = 'users';
    var panelApi = null;

    defs.forEach(function (d) {
      var b = document.createElement('button');
      b.type = 'button';
      b.className = 'btn';
      b.textContent = d.label;
      b.addEventListener('click', function () { activate(d.key); });
      d.node = b;
      tabs.appendChild(b);
    });
    container.appendChild(tabs);
    container.appendChild(panelHost);

    function activate(key) {
      activeKey = key;
      defs.forEach(function (d) {
        if (d.key === key) {
          d.node.className = 'btn btn-primary';
        } else {
          d.node.className = 'btn';
        }
      });
      if (panelApi && typeof panelApi.destroy === 'function') {
        try { panelApi.destroy(); } catch (e) { /* 忽略 */ }
      }
      panelHost.innerHTML = '';
      var def = null;
      defs.forEach(function (d) { if (d.key === key) { def = d; } });
      panelApi = def ? def.builder(panelHost) : null;
    }

    activate(activeKey);
  }

  /* ==================================================================
   * 3. 用户管理面板
   * ================================================================ */
  function buildUsersPanel(host) {
    var roleOptions = [];

    var filters = UI.filterBar([
      { name: 'keyword', label: '关键词', placeholder: '用户名 / 姓名' },
      { name: 'roleCode', label: '角色', type: 'select', options: roleOptions, placeholder: '全部角色' },
      {
        name: 'enabled', label: '账号状态', type: 'select', placeholder: '全部',
        options: [{ value: 'true', label: '启用' }, { value: 'false', label: '停用' }]
      }
    ], {
      onSearch: function (values) { tbl.setQuery(values); }
    });
    host.appendChild(filters.el);

    var tbl = UI.table({
      columns: [
        {
          title: '用户名', key: 'username', width: '130px',
          render: function (v) {
            return '<span class="mono text-strong">' + util.escape(v || '') + '</span>';
          },
          raw: true
        },
        {
          title: '姓名', key: 'realName', width: '110px',
          render: function (v, row) { return util.pick(row, ['realName', 'nickname', 'name'], ''); }
        },
        {
          title: '角色', key: 'roleCode', width: '130px', raw: true,
          render: function (v, row) {
            var code = util.pick(row, ['roleCode', 'role'], '');
            var label = roleName(code, util.pick(row, ['roleName'], ''));
            return '<span class="badge badge-blue">' + util.escape(label) + '</span>';
          }
        },
        {
          title: '所属院系', key: 'departmentCode', width: '160px',
          render: function (v, row) { return util.pick(row, ['departmentCode', 'departmentName'], ''); }
        },
        { title: '联系电话', key: 'phone', width: '120px',
          render: function (v, row) { return util.pick(row, ['phone', 'mobile'], ''); } },
        {
          title: '状态', key: 'enabled', width: '80px', align: 'center', raw: true,
          render: function (v) {
            var on = (v === true || v === 'true' || v === 1 || v === '1' || v === undefined || v === null);
            return '<span class="badge badge-' + (on ? 'green' : 'gray') + '">' + (on ? '启用' : '停用') + '</span>';
          }
        },
        {
          title: '操作', width: '190px', className: 'actions', raw: true,
          render: function (v, row) {
            return '<button class="btn-link" data-act="edit" data-id="' + util.escape(row.id) + '">编辑</button>' +
              '<button class="btn-link" data-act="pwd" data-id="' + util.escape(row.id) + '">重置密码</button>' +
              '<button class="btn-link danger" data-act="del" data-id="' + util.escape(row.id) + '">删除</button>';
          }
        }
      ],
      loader: function (query) { return Api.get('/admin/users', query); },
      emptyTitle: '暂无用户数据',
      emptyDesc: '可调整筛选条件，或新建用户'
    });
    host.appendChild(tbl.el);

    // 工具栏
    var toolbar = document.createElement('div');
    toolbar.className = 'page-toolbar';
    var addBtn = document.createElement('button');
    addBtn.type = 'button';
    addBtn.className = 'btn btn-primary';
    addBtn.textContent = '新建用户';
    addBtn.addEventListener('click', function () { openUserForm(null, roleOptions, tbl.reload); });
    toolbar.appendChild(addBtn);
    // 把工具栏插到页头下方（filters 之前）
    host.insertBefore(toolbar, host.firstChild);

    // 行内操作
    tbl.el.addEventListener('click', function (e) {
      var btn = e.target && e.target.closest ? e.target.closest('button[data-act]') : null;
      if (!btn) { return; }
      var act = btn.getAttribute('data-act');
      var id = btn.getAttribute('data-id');
      var row = null;
      tbl.getRecords().forEach(function (r) { if (String(r.id) === String(id)) { row = r; } });
      row = row || { id: id };

      if (act === 'edit') { openUserForm(row, roleOptions, tbl.reload); }
      else if (act === 'pwd') { resetPassword(row, tbl.reload); }
      else if (act === 'del') { deleteUser(row, tbl.reload); }
    });

    // 先取角色清单，用于筛选下拉与表单选择
    Api.get('/admin/roles')
      .then(function (res) {
        roleOptions = asArray(res).map(function (r) {
          var code = util.pick(r, ['roleCode', 'code', 'value'], '');
          var label = roleName(code, util.pick(r, ['roleName', 'name'], ''));
          var count = util.pick(r, ['userCount', 'accountCount', 'count'], null);
          return {
            value: code,
            label: count === null ? label : (label + '（' + count + ' 人）')
          };
        });
        // 回填筛选下拉
        var sel = filters.el.querySelector('select[name="roleCode"]');
        if (sel) {
          sel.innerHTML = '<option value="">全部角色</option>' + roleOptions.map(function (o) {
            return '<option value="' + util.escape(o.value) + '">' + util.escape(o.label) + '</option>';
          }).join('');
        }
      }, function () {
        // 角色接口不可用时不阻塞用户列表
        UI.toast('角色清单加载失败，角色下拉可能为空', 'warning');
      });

    tbl.reload();

    return {
      destroy: function () { /* 无定时器，无需清理 */ },
      reload: tbl.reload
    };
  }

  /* ==================================================================
   * 4. 角色权限矩阵
   * ================================================================ */
  function buildRolesPanel(host) {
    var allPermissions = [];   // [{code,name,group}]
    var roles = [];            // [{roleCode,roleName}]
    var currentRole = '';
    var currentChecked = {};

    var wrap = document.createElement('div');
    wrap.className = 'grid';
    wrap.style.gridTemplateColumns = '260px 1fr';
    wrap.style.alignItems = 'start';

    // 左：角色列表
    var leftCard = UI.card({ title: '角色' });
    var roleList = document.createElement('div');
    leftCard.body.appendChild(roleList);

    // 右：权限矩阵
    var rightCard = UI.card({ title: '权限矩阵' });
    var matrixHost = document.createElement('div');
    matrixHost.innerHTML = UI.loadingBlock('正在加载权限清单…');
    rightCard.body.appendChild(matrixHost);

    wrap.appendChild(leftCard.el);
    wrap.appendChild(rightCard.el);
    host.appendChild(wrap);

    // 状态提示
    var statusLine = document.createElement('div');
    statusLine.className = 'text-sub';
    statusLine.style.marginTop = '10px';
    host.appendChild(statusLine);

    loadAll();

    function loadAll() {
      window.Promise.all([
        Api.get('/admin/roles'),
        Api.get('/admin/permissions')
      ]).then(function (res) {
        roles = asArray(res[0]).map(function (r) {
          var code = util.pick(r, ['roleCode', 'code', 'value'], '');
          return {
            roleCode: code,
            roleName: roleName(code, util.pick(r, ['roleName', 'name'], '')),
            userCount: util.pick(r, ['userCount', 'accountCount', 'count'], null),
            scope: util.pick(r, ['dataScope', 'scope'], AJ.ROLE_SCOPES[code] || '')
          };
        });

        var perms = asArray(res[1]);
        allPermissions = perms.map(function (p) {
          if (typeof p === 'string') {
            return { code: p, name: AJ.PERMISSION_NAMES[p] || p, group: groupOf(p) };
          }
          var code = util.pick(p, ['code', 'permissionCode', 'value', 'name'], '');
          return {
            code: code,
            name: util.pick(p, ['name', 'label', 'description'], AJ.PERMISSION_NAMES[code] || code),
            group: util.pick(p, ['group', 'module', 'category'], groupOf(code))
          };
        });

        renderRoleList();
        // 默认选中第一个角色
        if (roles.length) { selectRole(roles[0].roleCode); }
        else {
          matrixHost.innerHTML = UI.emptyState('暂无角色数据', '请确认后端已初始化角色表');
        }
      }, function (err) {
        matrixHost.innerHTML = '';
        matrixHost.appendChild(UI.errorBox((err && err.message) || '角色 / 权限清单加载失败', function () {
          matrixHost.innerHTML = UI.loadingBlock('正在加载权限清单…');
          loadAll();
        }));
      });
    }

    /** 权限码 → 所属模块（用于矩阵分组显示） */
    function groupOf(code) {
      var prefix = String(code).split(':')[0];
      var map = {
        user: '用户与账号', role: '角色与权限', audit: '操作日志',
        course: '课程信息', offering: '开课管理', teacher: '教师信息',
        resource: '教学资源', evaluation: '教学评价',
        supervision: '督导听评课', coverage: '督导覆盖率', dashboard: '工作台'
      };
      return map[prefix] || '其他';
    }

    function renderRoleList() {
      roleList.innerHTML = '';
      if (!roles.length) {
        roleList.innerHTML = UI.emptyState('暂无角色');
        return;
      }
      roles.forEach(function (r) {
        var item = document.createElement('div');
        item.className = 'nav-item';
        item.style.color = '#1f2937';
        item.style.borderLeftColor = 'transparent';
        item.style.borderRadius = '4px';
        item.style.marginBottom = '4px';
        item.setAttribute('data-role', r.roleCode);
        item.innerHTML = '<span class="nav-text">' + util.escape(r.roleName) + '</span>' +
          (r.userCount === null ? '' : '<span class="badge badge-gray" style="margin-left:auto">' +
            util.escape(r.userCount) + ' 人</span>');
        item.addEventListener('click', function () { selectRole(r.roleCode); });
        item.addEventListener('mouseenter', function () {
          if (currentRole !== r.roleCode) { item.style.background = '#f9fafb'; }
        });
        item.addEventListener('mouseleave', function () {
          if (currentRole !== r.roleCode) { item.style.background = ''; }
        });
        roleList.appendChild(item);
      });
      highlightRole();
    }

    function highlightRole() {
      var items = roleList.querySelectorAll('[data-role]');
      for (var i = 0; i < items.length; i++) {
        var code = items[i].getAttribute('data-role');
        if (code === currentRole) {
          items[i].style.background = 'var(--c-primary-light)';
          items[i].style.borderLeftColor = 'var(--c-primary)';
          items[i].style.fontWeight = '600';
        } else {
          items[i].style.background = '';
          items[i].style.borderLeftColor = 'transparent';
          items[i].style.fontWeight = '';
        }
      }
    }

    function selectRole(code) {
      currentRole = code;
      highlightRole();
      renderMatrix(UI.loadingBlock('正在加载该角色权限…'));
      statusLine.textContent = '';

      Api.get('/admin/roles/' + encodeURIComponent(code) + '/permissions')
        .then(function (res) {
          var codes = asArray(res).map(function (p) {
            return typeof p === 'string' ? p : util.pick(p, ['code', 'permissionCode', 'value', 'name'], '');
          });
          currentChecked = {};
          codes.forEach(function (c) { if (c) { currentChecked[c] = true; } });
          renderMatrix(null);
        }, function (err) {
          // 取不到已授权限时按“空”处理，仍允许勾选保存
          currentChecked = {};
          renderMatrix(null);
          statusLine.textContent = '提示：该角色已授权限读取失败（' + ((err && err.message) || '未知错误') +
            '），当前显示为全部未勾选，保存前请确认。';
        });
    }

    function renderMatrix(loadingHtml) {
      matrixHost.innerHTML = '';
      if (loadingHtml) {
        matrixHost.innerHTML = loadingHtml;
        return;
      }

      var role = null;
      roles.forEach(function (r) { if (r.roleCode === currentRole) { role = r; } });

      // 角色信息
      var info = document.createElement('div');
      info.style.marginBottom = '14px';
      info.appendChild(UI.descList([
        { label: '角色码', value: currentRole },
        { label: '角色名', value: role ? role.roleName : '' },
        { label: '数据范围', value: role ? role.scope : '' },
        { label: '账号数', value: role && role.userCount !== null ? role.userCount + ' 人' : '' }
      ]));
      matrixHost.appendChild(info);

      if (!allPermissions.length) {
        matrixHost.appendChild(UI.emptyStateEl('暂无权限码', '请确认后端已初始化权限表'));
        return;
      }

      // 按模块分组渲染勾选框
      var groups = {};
      var order = [];
      allPermissions.forEach(function (p) {
        if (!groups[p.group]) { groups[p.group] = []; order.push(p.group); }
        groups[p.group].push(p);
      });

      order.forEach(function (g) {
        var section = document.createElement('div');
        section.style.marginBottom = '16px';

        var head = document.createElement('div');
        head.style.display = 'flex';
        head.style.alignItems = 'center';
        head.style.gap = '10px';
        head.style.marginBottom = '8px';
        head.innerHTML = '<span class="text-strong">' + util.escape(g) + '</span>' +
          '<span class="text-sub">' + groups[g].length + ' 项</span>';

        // 分组全选 / 全不选
        var groupBoxes = [];
        var selectAll = document.createElement('button');
        selectAll.type = 'button';
        selectAll.className = 'btn btn-sm';
        selectAll.textContent = '全选本组';
        selectAll.style.marginLeft = 'auto';
        selectAll.addEventListener('click', function () {
          var allOn = groupBoxes.every(function (b) { return b.checked; });
          groupBoxes.forEach(function (b) {
            b.checked = !allOn;
            syncItem(b);
          });
        });
        head.appendChild(selectAll);
        section.appendChild(head);

        var grid = document.createElement('div');
        grid.className = 'checkbox-group';

        groups[g].forEach(function (p) {
          var item = document.createElement('label');
          item.className = 'checkbox-item' + (currentChecked[p.code] ? ' checked' : '');

          var cb = document.createElement('input');
          cb.type = 'checkbox';
          cb.checked = !!currentChecked[p.code];
          cb.setAttribute('data-code', p.code);

          var textWrap = document.createElement('span');
          textWrap.innerHTML = '<span class="cb-label">' + util.escape(p.name) + '</span>' +
            '<span class="cb-code">' + util.escape(p.code) + '</span>';

          cb.addEventListener('change', function () { syncItem(cb); });

          item.appendChild(cb);
          item.appendChild(textWrap);
          grid.appendChild(item);
          groupBoxes.push(cb);
          cb._item = item;
        });

        section.appendChild(grid);
        matrixHost.appendChild(section);
      });

      // 保存按钮
      var foot = document.createElement('div');
      foot.style.display = 'flex';
      foot.style.gap = '10px';
      foot.style.alignItems = 'center';
      foot.style.paddingTop = '12px';
      foot.style.borderTop = '1px solid var(--c-border)';

      var saveBtn = document.createElement('button');
      saveBtn.type = 'button';
      saveBtn.className = 'btn btn-primary';
      saveBtn.textContent = '保存权限';
      saveBtn.addEventListener('click', save);
      foot.appendChild(saveBtn);

      var resetBtn = document.createElement('button');
      resetBtn.type = 'button';
      resetBtn.className = 'btn';
      resetBtn.textContent = '放弃修改';
      resetBtn.addEventListener('click', function () { selectRole(currentRole); });
      foot.appendChild(resetBtn);

      var hint = document.createElement('span');
      hint.className = 'text-sub';
      hint.textContent = '勾选项将整体覆盖该角色已有权限';
      foot.appendChild(hint);
      matrixHost.appendChild(foot);
    }

    /** 勾选状态与样式同步 */
    function syncItem(cb) {
      if (cb._item) {
        if (cb.checked) { cb._item.classList.add('checked'); }
        else { cb._item.classList.remove('checked'); }
      }
    }

    function save() {
      var boxes = matrixHost.querySelectorAll('input[type="checkbox"][data-code]');
      var selected = [];
      for (var i = 0; i < boxes.length; i++) {
        if (boxes[i].checked) { selected.push(boxes[i].getAttribute('data-code')); }
      }

      var saveBtn = matrixHost.querySelector('.btn-primary');
      if (saveBtn) { saveBtn.disabled = true; saveBtn.textContent = '保存中…'; }

      Api.put('/admin/roles/' + encodeURIComponent(currentRole) + '/permissions',
        { permissions: selected })
        .then(function () {
          UI.toast('已保存「' + roleName(currentRole) + '」的 ' + selected.length + ' 项权限', 'success');
          statusLine.textContent = '最后保存时间：' + util.now() +
            '，共 ' + selected.length + ' 项权限。';
          currentChecked = {};
          selected.forEach(function (c) { currentChecked[c] = true; });
        }, function (err) {
          UI.toast((err && err.message) || '权限保存失败', 'error');
        })
        .then(function () {
          if (saveBtn) { saveBtn.disabled = false; saveBtn.textContent = '保存权限'; }
        });
    }

    return { destroy: function () { /* 无定时器，无需清理 */ } };
  }

  /* ==================================================================
   * 5. 操作日志面板
   * ================================================================ */
  function buildLogsPanel(host) {
    var filters = UI.filterBar([
      { name: 'operator', label: '操作人', placeholder: '用户名 / 姓名' },
      { name: 'action', label: '动作', placeholder: '如 CREATE / UPDATE / DELETE' },
      { name: 'targetType', label: '对象类型', placeholder: '如 COURSE / USER' }
    ], {
      onSearch: function (values) { tbl.setQuery(values); }
    });
    host.appendChild(filters.el);

    var tbl = UI.table({
      columns: [
        {
          title: '时间', key: 'createdAt', width: '160px',
          render: function (v, row) { return util.pick(row, ['createdAt', 'createTime', 'operateTime'], ''); }
        },
        {
          title: '操作人', key: 'operator', width: '110px',
          render: function (v, row) { return util.pick(row, ['operator', 'operatorName', 'username'], ''); }
        },
        {
          title: '动作', key: 'action', width: '110px', raw: true,
          render: function (v) {
            var s = String(v || '');
            var tone = /CREATE|ADD|INSERT/i.test(s) ? 'green'
              : /DELETE|REMOVE/i.test(s) ? 'red'
                : /UPDATE|EDIT|MODIFY/i.test(s) ? 'blue' : 'gray';
            return '<span class="badge badge-' + tone + '">' + util.escape(s || '—') + '</span>';
          }
        },
        {
          title: '对象类型', key: 'targetType', width: '120px',
          render: function (v, row) { return util.pick(row, ['targetType', 'target', 'module'], ''); }
        },
        {
          title: '对象标识', key: 'targetId', width: '110px',
          render: function (v, row) { return util.pick(row, ['targetId', 'targetName', 'bizId'], ''); }
        },
        {
          title: '详情', key: 'detail',
          render: function (v, row) {
            return util.pick(row, ['detail', 'description', 'content', 'remark'], '');
          }
        },
        {
          title: 'IP', key: 'ip', width: '120px',
          render: function (v, row) { return util.pick(row, ['ip', 'clientIp'], ''); }
        }
      ],
      loader: function (query) { return Api.get('/admin/audit-logs', query); },
      pageSize: 20,
      emptyTitle: '暂无操作日志',
      emptyDesc: '系统会自动记录用户与权限、课程、开课等关键写操作'
    });
    host.appendChild(tbl.el);
    tbl.reload();

    return { destroy: function () { /* 无定时器，无需清理 */ }, reload: tbl.reload };
  }

  AJ.pages.admin = {
    title: '用户与权限管理',
    roles: ['ADMIN'],
    render: render
  };

})(window, document);
