/* ============================================================================
 * router.js —— 基于 location.hash 的极简路由器 + 菜单权限控制
 *
 * 职责：
 *   1. 维护“路由表”：hash → 页面模块（pages/*.js 注册到 AJ.pages）
 *   2. 解析 #/path?query 形式的地址，把参数传给页面模块
 *   3. 未登录统一跳 #/login；已登录访问无权限页面时提示并跳回工作台
 *   4. 按当前角色渲染左侧菜单（只显示有权限的菜单项）
 *   5. 页面切换时调用上一页的 destroy()，避免定时器与挂载在 window 上的监听泄漏
 *
 * 页面模块约定（见 pages/*.js）：
 *   AJ.pages.courses = {
 *     title: '课程信息管理',
 *     roles: ['ADMIN','DIRECTOR','COURSE_ADMIN','SUPERVISOR'],
 *     render: function (container, params) { ... },   // 必填
 *     destroy: function () { ... }                    // 选填
 *   }
 * ========================================================================== */
(function (window, document) {
  'use strict';

  var AJ = window.AJ = window.AJ || {};
  var Api = AJ.Api;

  /* ------------------------------------------------------------------
   * 1. 路由表（契约第 9 节“前端页面（骨架冻结）”）
   *    permission 为可选字段：有值时按权限码控制菜单可见性与访问
   * ---------------------------------------------------------------- */
  var ROUTES = [
    { path: '/login', key: 'login', title: '登录', module: null },

    // —— 概览 ——
    { path: '/dashboard', key: 'dashboard', title: '工作台', icon: '◉', group: '概览',
      roles: ['ADMIN', 'DIRECTOR', 'TEACHER', 'SUPERVISOR', 'COURSE_ADMIN'] },

    // —— 课程与教学 ——
    { path: '/courses', key: 'courses', title: '课程信息管理', icon: '▤', group: '课程与教学',
      roles: ['ADMIN', 'DIRECTOR', 'COURSE_ADMIN', 'SUPERVISOR'], permission: 'course:read' },
    { path: '/offerings', key: 'offerings', title: '开课与授课管理', icon: '▦', group: '课程与教学',
      roles: ['ADMIN', 'DIRECTOR', 'COURSE_ADMIN', 'SUPERVISOR', 'TEACHER'], permission: 'offering:read' },
    { path: '/teachers', key: 'teachers', title: '教师信息管理', icon: '☺', group: '课程与教学',
      roles: ['ADMIN', 'DIRECTOR', 'COURSE_ADMIN'], permission: 'teacher:read' },
    { path: '/resources', key: 'resources', title: '教学资源管理', icon: '⌘', group: '课程与教学',
      roles: ['ADMIN', 'DIRECTOR', 'TEACHER', 'SUPERVISOR', 'COURSE_ADMIN'], permission: 'resource:read' },
    { path: '/prep', key: 'prep', title: '备课查看', icon: '✎', group: '课程与教学',
      roles: ['TEACHER', 'DIRECTOR', 'ADMIN'], permission: 'resource:prep' },

    // —— 评价与督导 ——
    { path: '/evaluations', key: 'evaluations', title: '教学评价管理', icon: '★', group: '评价与督导',
      roles: ['ADMIN', 'DIRECTOR', 'SUPERVISOR', 'TEACHER'], permission: 'evaluation:read' },
    { path: '/supervision', key: 'supervision', title: '督导与听评课', icon: '✓', group: '评价与督导',
      roles: ['ADMIN', 'SUPERVISOR', 'DIRECTOR'], permission: 'supervision:read' },
    { path: '/coverage', key: 'coverage', title: '督导覆盖率', icon: '◎', group: '评价与督导',
      roles: ['ADMIN', 'SUPERVISOR', 'DIRECTOR'], permission: 'coverage:read' },

    // —— 系统管理 ——
    { path: '/admin', key: 'admin', title: '用户与权限管理', icon: '⚙', group: '系统管理',
      roles: ['ADMIN'], permission: 'user:read' }
  ];

  var DEFAULT_ROUTE = '/dashboard';
  var currentRoute = null;   // 当前路由定义
  var currentPage = null;    // 当前页面模块（用于 destroy）

  /* ------------------------------------------------------------------
   * 1.1 渲染令牌与异步竞态防护
   *
   * 背景：路由可能在短时间内被连续解析（例如启动时 Router.start() 与
   * 启动脚本里的 fetchMe() 各触发一次），旧页面的异步请求回调若在此时返回，
   * 会把内容写进已经切换过的容器，覆盖新页面的内容（实测会“吃掉”数据表格）。
   *
   * 解决：每次渲染分配一个自增令牌，并挂在内容容器上；页面在异步回调里
   * 先调用 guard() 探测令牌是否已过期，过期则直接丢弃本次更新。
   * ---------------------------------------------------------------- */
  var renderToken = 0;

  function nextToken() { return ++renderToken; }

  /** 当前有效的渲染令牌 */
  function token() { return renderToken; }

  /** 给定令牌是否仍然是当前有效令牌 */
  function isCurrent(t) { return t === renderToken; }

  /**
   * 生成一个竞态守卫函数。
   * 用法：
   *     var g = AJ.Router.guard();
   *     Api.get(url).then(g(function (data) { ... }));
   * @returns {function} g(fn) -> 包装后的回调；令牌过期时 fn 不会执行
   */
  function guard() {
    var t = renderToken;
    return function wrap(fn) {
      return function () {
        if (t !== renderToken) { return undefined; }
        if (typeof fn !== 'function') { return undefined; }
        return fn.apply(null, arguments);
      };
    };
  }

  /* ------------------------------------------------------------------
   * 2. hash 解析：#/courses?keyword=x → { path:'/courses', query:{keyword:'x'} }
   * ---------------------------------------------------------------- */
  function parseHash() {
    var raw = window.location.hash || '';
    if (raw.indexOf('#') === 0) { raw = raw.slice(1); }
    if (!raw || raw === '/') { return { path: '', query: {} }; }

    var qIndex = raw.indexOf('?');
    var path = qIndex === -1 ? raw : raw.slice(0, qIndex);
    var queryStr = qIndex === -1 ? '' : raw.slice(qIndex + 1);

    if (path.charAt(0) !== '/') { path = '/' + path; }

    var query = {};
    if (queryStr) {
      queryStr.split('&').forEach(function (pair) {
        if (!pair) { return; }
        var eq = pair.indexOf('=');
        var k = eq === -1 ? pair : pair.slice(0, eq);
        var v = eq === -1 ? '' : pair.slice(eq + 1);
        try { query[decodeURIComponent(k)] = decodeURIComponent(v.replace(/\+/g, ' ')); }
        catch (e) { query[k] = v; }
      });
    }
    return { path: path, query: query };
  }

  function findByPath(path) {
    for (var i = 0; i < ROUTES.length; i++) {
      if (ROUTES[i].path === path) { return ROUTES[i]; }
    }
    return null;
  }

  /* ------------------------------------------------------------------
   * 3. 跳转
   * ---------------------------------------------------------------- */
  function go(path, options) {
    options = options || {};
    var target = '#' + path;
    if (window.location.hash === target) {
      // 地址未变化时手动触发一次解析（例如点击当前菜单刷新数据）
      if (options.force) { resolve(); }
      return;
    }
    if (options.replace) {
      window.location.replace(window.location.pathname + window.location.search + target);
    } else {
      window.location.hash = target;
    }
  }

  /* ------------------------------------------------------------------
   * 4. 访问控制
   *
   * 注意：登录态分两步建立——token 先落盘，用户信息（含角色与权限码）随后
   * 由 /auth/me 补齐。这段时间内 roleCode() 返回空串，若直接按角色判权限，
   * 会把合法页面误判为“无权限”并重定向到工作台（实测 #/courses 会被弹回
   * #/dashboard）。因此角色未就绪时不做拦截，只完成渲染；角色就绪后由
   * refreshShell() 重建菜单，后续 resolve 按真实角色重新判定。
   * ---------------------------------------------------------------- */

  /** 当前用户是否已加载出角色（角色与权限码就绪） */
  function roleReady() {
    var u = Api.getCurrentUser();
    return !!(u && (u.roleCode || u.role));
  }

  /** 判断当前登录用户是否可以访问某个路由 */
  function canAccess(route) {
    if (!route) { return false; }
    if (route.path === '/login') { return true; }
    if (!Api.isLoggedIn()) { return false; }
    // 角色未就绪（/auth/me 尚未返回）：不拦截，避免把合法页面误判为越权
    if (!roleReady()) { return true; }
    if (route.roles && !Api.hasRole(route.roles)) { return false; }
    // 菜单权限码只做“可见性”控制：无权限码时不硬拦，避免后端权限码不全导致整页打不开
    return true;
  }

  /** 判断菜单项是否对当前用户可见 */
  function menuVisible(route) {
    if (!route.group) { return false; }
    // 角色未就绪时先全部隐藏，待 refreshShell() 按真实角色重建菜单
    if (!roleReady()) { return false; }
    if (route.roles && !Api.hasRole(route.roles)) { return false; }
    if (route.permission && !Api.can(route.permission)) { return false; }
    return true;
  }

  /* ------------------------------------------------------------------
   * 5. 菜单渲染
   * ---------------------------------------------------------------- */
  function renderMenu() {
    var nav = document.getElementById('sidebarNav');
    if (!nav) { return; }

    var groups = [];
    var byGroup = {};
    ROUTES.forEach(function (r) {
      if (!menuVisible(r)) { return; }
      if (!byGroup[r.group]) { byGroup[r.group] = []; groups.push(r.group); }
      byGroup[r.group].push(r);
    });

    var html = '';
    groups.forEach(function (g) {
      html += '<div class="nav-group-title">' + AJ.util.escape(g) + '</div>';
      byGroup[g].forEach(function (r) {
        var active = currentRoute && currentRoute.path === r.path ? ' active' : '';
        html += '<a class="nav-item' + active + '" href="#' + r.path + '" data-path="' + r.path + '">' +
          '<span class="nav-icon">' + r.icon + '</span>' +
          '<span class="nav-text">' + AJ.util.escape(r.title) + '</span>' +
          '</a>';
      });
    });

    nav.innerHTML = html;
  }

  /** 只更新菜单高亮，不重建 DOM */
  function highlightMenu() {
    var items = document.querySelectorAll('#sidebarNav .nav-item');
    for (var i = 0; i < items.length; i++) {
      var p = items[i].getAttribute('data-path');
      if (currentRoute && p === currentRoute.path) {
        items[i].classList.add('active');
      } else {
        items[i].classList.remove('active');
      }
    }
  }

  /* ------------------------------------------------------------------
   * 6. 主解析流程
   * ---------------------------------------------------------------- */
  function resolve() {
    var parsed = parseHash();
    var path = parsed.path;
    var logged = Api.isLoggedIn();

    // 6.1 未登录：除登录页外一律回登录页
    if (!logged) {
      if (path !== '/login') { go('/login', { replace: true }); return; }
      currentRoute = findByPath('/login');
      showLoginShell();
      return;
    }

    // 6.2 已登录却停在登录页：直接进工作台
    if (path === '/login') { go(DEFAULT_ROUTE, { replace: true }); return; }

    // 6.3 空 hash：进默认页
    if (!path) { go(DEFAULT_ROUTE, { replace: true }); return; }

    var route = findByPath(path);

    // 6.4 未知路由
    if (!route) {
      if (AJ.UI && AJ.UI.toast) { AJ.UI.toast('页面不存在：' + path, 'warning'); }
      go(DEFAULT_ROUTE, { replace: true });
      return;
    }

    // 6.5 角色不可访问
    if (!canAccess(route)) {
      if (AJ.UI && AJ.UI.toast) { AJ.UI.toast('无权限访问该功能', 'error'); }
      var fallback = logged ? DEFAULT_ROUTE : '/login';
      if (path === fallback) { return; }
      go(fallback, { replace: true });
      return;
    }

    // 6.6 正常渲染
    currentRoute = route;
    var myToken = nextToken();
    showAppShell();
    renderMenu();
    highlightMenu();

    var titleEl = document.getElementById('topbarTitle');
    if (titleEl) { titleEl.textContent = route.title; }
    document.title = route.title + ' · 爱教学';

    var module = AJ.pages[route.key];
    var container = document.getElementById('content');
    if (!container) { return; }

    // 清理上一页资源
    if (currentPage && typeof currentPage.destroy === 'function') {
      try { currentPage.destroy(); } catch (e) { /* 单页清理失败不阻塞路由 */ }
    }
    currentPage = module || null;

    if (!module || typeof module.render !== 'function') {
      container.innerHTML = AJ.UI
        ? AJ.UI.emptyState('页面尚未实现', '模块 ' + route.key + '.js 未注册到 AJ.pages')
        : '<div class="empty">页面尚未实现</div>';
      return;
    }

    container.innerHTML = '';
    container.scrollTop = 0;
    window.scrollTo(0, 0);

    // 清空容器后重新挂载令牌，供页面异步回调判断是否过期
    container._ajToken = myToken;

    try {
      module.render(container, parsed.query);
    } catch (e) {
      if (myToken !== renderToken) { return; }
      container.innerHTML = '';
      if (AJ.UI && AJ.UI.errorBox) {
        container.appendChild(AJ.UI.errorBox('页面渲染出错：' + (e && e.message ? e.message : e)));
      }
    }
  }

  /* ------------------------------------------------------------------
   * 7. 外壳切换（登录页 / 应用外壳）
   *    实际 DOM 显隐由 index.html 的全局函数控制，这里只做调用
   * ---------------------------------------------------------------- */
  function showLoginShell() {
    if (typeof window.AJShowLogin === 'function') { window.AJShowLogin(); }
  }

  function showAppShell() {
    if (typeof window.AJShowApp === 'function') { window.AJShowApp(); }
  }

  /* ------------------------------------------------------------------
   * 8. 顶部用户信息栏渲染
   * ---------------------------------------------------------------- */
  function renderTopbarUser() {
    var user = Api.getCurrentUser() || {};
    var nameEl = document.getElementById('topbarUserName');
    var roleEl = document.getElementById('topbarUserRole');
    var avatarEl = document.getElementById('topbarAvatar');
    var envEl = document.getElementById('topbarEnv');

    var displayName = user.realName || user.nickname || user.username || '未登录';
    var role = Api.roleCode();
    var roleText = AJ.ROLE_NAMES[role] || user.roleName || role || '未知角色';

    if (nameEl) { nameEl.textContent = displayName; }
    if (roleEl) { roleEl.textContent = roleText; }
    if (avatarEl) { avatarEl.textContent = displayName.charAt(0); }
    if (envEl) {
      var term = user.semester || user.currentSemester || '';
      envEl.textContent = term ? ('当前学期：' + term) : '';
    }
  }

  /* ------------------------------------------------------------------
   * 9. 启动
   * ---------------------------------------------------------------- */
  function start() {
    window.addEventListener('hashchange', resolve);
    resolve();
  }

  AJ.Router = {
    ROUTES: ROUTES,
    go: go,
    resolve: resolve,
    start: start,
    renderTopbarUser: renderTopbarUser,
    renderMenu: renderMenu,
    current: function () { return currentRoute; },
    /** 竞态防护：见 1.1 节说明 */
    guard: guard,
    token: token,
    isCurrent: isCurrent,
    /** 登录/登出后调用：重建菜单与顶栏 */
    refreshShell: function () {
      renderTopbarUser();
      renderMenu();
      highlightMenu();
    }
  };

})(window, document);
