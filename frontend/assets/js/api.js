/* ============================================================================
 * api.js —— 统一接口访问层（全站唯一出口）
 *
 * 职责：
 *   1. 统一 base（默认 '/api'）+ 自动携带 Authorization: Bearer <token>
 *   2. 统一解包后端响应包 { code, message, data }，code!==0 一律抛 ApiError
 *   3. 401 → 清除登录态并跳转 #/login；403 → 统一提示“无权限访问该功能”
 *   4. 提供 CSV 导出下载、CSV 文件批量导入的专用方法
 *   5. 维护当前登录用户、权限码判断、角色中文名等全局状态
 *
 * 说明：本文件同时定义了全站共享的命名空间 window.AJ，其余脚本按顺序挂载到它上面。
 * ========================================================================== */
(function (window, document) {
  'use strict';

  /* ------------------------------------------------------------------
   * 0. 全局命名空间
   * ---------------------------------------------------------------- */
  var AJ = window.AJ = window.AJ || {};
  AJ.config = AJ.config || {};
  AJ.pages = AJ.pages || {};      // 各页面模块注册处（pages/*.js）
  AJ.util = AJ.util || {};        // 通用小工具

  /* ------------------------------------------------------------------
   * 1. 常量：角色、权限码、存储键
   * ---------------------------------------------------------------- */

  /** 角色码 → 中文名（契约第 1 节） */
  AJ.ROLE_NAMES = {
    ADMIN: '系统管理员',
    DIRECTOR: '教研室主任',
    TEACHER: '教师',
    SUPERVISOR: '教学督导',
    COURSE_ADMIN: '课程管理员'
  };

  /** 角色码 → 数据可见范围说明（契约第 1 节） */
  AJ.ROLE_SCOPES = {
    ADMIN: '全量数据',
    DIRECTOR: '本教研室（跨院由系统配置决定）',
    TEACHER: '仅本人课程与本人资源',
    SUPERVISOR: '全校开课与督导数据（只读）',
    COURSE_ADMIN: '课程库与开课（只读）'
  };

  /** 全部权限码 → 中文名（契约各节汇总，用于权限矩阵展示） */
  AJ.PERMISSION_NAMES = {
    'user:read': '用户查询',
    'user:write': '用户维护',
    'role:read': '角色权限查询',
    'role:write': '角色权限维护',
    'audit:read': '操作日志查询',
    'course:read': '课程查询',
    'course:write': '课程维护',
    'offering:read': '开课查询',
    'offering:write': '开课维护',
    'teacher:read': '教师查询',
    'teacher:write': '教师维护',
    'resource:read': '资源查询',
    'resource:write': '资源维护',
    'resource:prep': '备课查看',
    'evaluation:read': '教学评价查询',
    'supervision:read': '听评课查询',
    'supervision:write': '听评课维护',
    'coverage:read': '督导覆盖率查询',
    'dashboard:read': '工作台概览',
    'dashboard:admin': '管理员工作台',
    'dashboard:director': '主任工作台',
    'dashboard:teacher': '教师工作台',
    'dashboard:supervisor': '督导工作台'
  };

  var STORE = {
    token: 'aijiaoxue.token',
    user: 'aijiaoxue.user',
    apiBase: 'aijiaoxue.apiBase'
  };

  /* ------------------------------------------------------------------
   * 2. localStorage 安全读写（隐私模式下不可用时降级为内存存储）
   * ---------------------------------------------------------------- */
  var memoryStore = {};

  function storeGet(key) {
    try {
      var v = window.localStorage.getItem(key);
      return v === null ? (key in memoryStore ? memoryStore[key] : null) : v;
    } catch (e) {
      return key in memoryStore ? memoryStore[key] : null;
    }
  }

  function storeSet(key, value) {
    memoryStore[key] = value;
    try { window.localStorage.setItem(key, value); } catch (e) { /* 忽略：隐私模式 */ }
  }

  function storeRemove(key) {
    delete memoryStore[key];
    try { window.localStorage.removeItem(key); } catch (e) { /* 忽略 */ }
  }

  /* ------------------------------------------------------------------
   * 3. API 基地址解析
   *    默认同源 '/api'；通过 file:// 直接双击打开时无同源后端，改用本机 8080。
   *    可在顶部“API 地址”里手动覆盖，保存在 localStorage。
   * ---------------------------------------------------------------- */
  var DEFAULT_HTTP_BASE = '/api';
  var DEFAULT_FILE_BASE = 'http://localhost:8080/api';

  function defaultBase() {
    return window.location.protocol === 'file:' ? DEFAULT_FILE_BASE : DEFAULT_HTTP_BASE;
  }

  function getBase() {
    var saved = storeGet(STORE.apiBase);
    if (saved) { return saved.replace(/\/+$/, ''); }
    return defaultBase();
  }

  function setBase(base) {
    var v = String(base || '').trim();
    if (!v) {
      storeRemove(STORE.apiBase);
    } else {
      storeSet(STORE.apiBase, v.replace(/\/+$/, ''));
    }
  }

  /* ------------------------------------------------------------------
   * 4. 登录态
   * ---------------------------------------------------------------- */
  function getToken() { return storeGet(STORE.token) || ''; }

  function getCurrentUser() {
    var raw = storeGet(STORE.user);
    if (!raw) { return null; }
    try {
      var u = JSON.parse(raw);
      // 兼容历史缓存：早期版本可能把 /auth/me 的 {user, permissions} 整体存了进来，
      // 这里做一次解包，避免老数据导致角色判定为空。
      if (u && u.user && !u.roleCode && !u.role) {
        var inner = u.user;
        if (Object.prototype.toString.call(u.permissions) === '[object Array]') {
          inner.permissions = u.permissions;
        }
        return inner;
      }
      return u;
    } catch (e) { return null; }
  }

  /** 写入登录态（登录成功 / 刷新 /api/auth/me 后调用） */
  function setAuth(token, user) {
    if (token) { storeSet(STORE.token, token); }
    if (user) { storeSet(STORE.user, JSON.stringify(user)); }
  }

  function clearAuth() {
    storeRemove(STORE.token);
    storeRemove(STORE.user);
  }

  function isLoggedIn() { return !!getToken(); }

  /** 当前用户角色码（兼容 roleCode / role 两种字段名） */
  function roleCode() {
    var u = getCurrentUser();
    if (!u) { return ''; }
    return u.roleCode || u.role || '';
  }

  /** 当前用户权限码数组（兼容 permissions / permissionCodes 两种字段名） */
  function permissions() {
    var u = getCurrentUser();
    if (!u) { return []; }
    var p = u.permissions || u.permissionCodes || [];
    return Object.prototype.toString.call(p) === '[object Array]' ? p : [];
  }

  /**
   * 是否拥有某个权限码。
   * ADMIN 视为拥有全部权限，避免后端权限码配置不全时管理员被前端拦住。
   */
  function can(code) {
    if (!code) { return true; }
    if (roleCode() === 'ADMIN') { return true; }
    return permissions().indexOf(code) !== -1;
  }

  /** 当前角色是否在给定角色清单内（用于路由与菜单可见性） */
  function hasRole(roles) {
    if (!roles || !roles.length) { return true; }
    return roles.indexOf(roleCode()) !== -1;
  }

  /* ------------------------------------------------------------------
   * 5. 统一错误对象
   * ---------------------------------------------------------------- */
  function ApiError(code, message, httpStatus, url) {
    this.name = 'ApiError';
    this.code = code;
    this.message = message || '请求失败';
    this.httpStatus = httpStatus || 0;
    this.url = url || '';
  }
  ApiError.prototype = Object.create(Error.prototype);
  ApiError.prototype.constructor = ApiError;
  AJ.ApiError = ApiError;

  /* ------------------------------------------------------------------
   * 6. 401 处理：清登录态 + 跳登录页（只提示一次，避免并发请求刷屏）
   * ---------------------------------------------------------------- */
  var redirecting = false;

  function handleUnauthorized(silent) {
    clearAuth();
    if (redirecting) { return; }
    redirecting = true;
    if (!silent && AJ.UI && AJ.UI.toast) {
      AJ.UI.toast('登录已失效，请重新登录', 'warning');
    }
    window.setTimeout(function () {
      redirecting = false;
      if (AJ.Router && AJ.Router.go) {
        AJ.Router.go('/login', { replace: true });
      } else {
        window.location.hash = '#/login';
      }
    }, 200);
  }

  /* ------------------------------------------------------------------
   * 7. 查询串拼接（自动丢弃空值）
   * ---------------------------------------------------------------- */
  function withQuery(path, params) {
    if (!params) { return path; }
    var parts = [];
    for (var k in params) {
      if (!Object.prototype.hasOwnProperty.call(params, k)) { continue; }
      var v = params[k];
      if (v === null || v === undefined || v === '') { continue; }
      parts.push(encodeURIComponent(k) + '=' + encodeURIComponent(v));
    }
    return parts.length ? (path + (path.indexOf('?') === -1 ? '?' : '&') + parts.join('&')) : path;
  }

  /* ------------------------------------------------------------------
   * 8. 请求核心
   * ---------------------------------------------------------------- */
  /**
   * 发起请求并解包响应。
   * @param {string} method  HTTP 方法
   * @param {string} path    以 '/' 开头的接口路径（不含 /api 前缀）
   * @param {object} options { query, body, silent401 }
   * @returns {Promise<any>} 解包后的 data
   */
  function request(method, path, options) {
    options = options || {};
    var url = getBase() + withQuery(path, options.query);

    var headers = { 'Accept': 'application/json' };
    var init = { method: method, headers: headers };

    var token = getToken();
    if (token) { headers['Authorization'] = 'Bearer ' + token; }

    if (options.body !== undefined && options.body !== null) {
      headers['Content-Type'] = 'application/json;charset=UTF-8';
      init.body = JSON.stringify(options.body);
    }

    return window.fetch(url, init).then(function (resp) {
      var status = resp.status;

      // 204 / 空响应：直接按成功处理
      if (status === 204) { return null; }

      return resp.text().then(function (text) {
        var payload = null;
        if (text) {
          try { payload = JSON.parse(text); } catch (e) { payload = null; }
        }

        // 后端未按统一包返回（例如网关转发错误）：按 HTTP 状态判断
        if (payload === null || typeof payload !== 'object') {
          if (status === 401) { handleUnauthorized(options.silent401); }
          throw new ApiError(status, text || ('HTTP ' + status), status, url);
        }

        var code = typeof payload.code === 'number' ? payload.code : (status >= 400 ? status : 0);
        var message = payload.message || '';

        if (code === 0) { return payload.data; }

        if (code === 401 || status === 401) {
          handleUnauthorized(options.silent401);
          throw new ApiError(401, message || '未登录或登录已失效', status, url);
        }
        if (code === 403 || status === 403) {
          if (AJ.UI && AJ.UI.toast) { AJ.UI.toast('无权限访问该功能', 'error'); }
          throw new ApiError(403, message || '无权限访问该功能', status, url);
        }

        throw new ApiError(code, message || ('请求失败（code=' + code + '）'), status, url);
      });
    }, function (networkError) {
      // fetch 本身失败：断网、后端未启动、CORS 被拒
      var hint = '无法连接后端服务，请确认服务已启动';
      if (window.location.protocol === 'file:') {
        hint = '当前以 file:// 方式打开，浏览器会拦截跨源请求。请通过后端地址访问，或先启动本地静态服务';
      }
      throw new ApiError(0, hint + '（' + getBase() + '）', 0, url);
    });
  }

  /* ------------------------------------------------------------------
   * 9. 对外接口
   * ---------------------------------------------------------------- */
  var api = {
    // ---- 配置与登录态 ----
    getBase: getBase,
    setBase: setBase,
    defaultBase: defaultBase,
    getToken: getToken,
    getCurrentUser: getCurrentUser,
    setAuth: setAuth,
    clearAuth: clearAuth,
    isLoggedIn: isLoggedIn,
    roleCode: roleCode,
    permissions: permissions,
    can: can,
    hasRole: hasRole,
    handleUnauthorized: handleUnauthorized,
    withQuery: withQuery,

    // ---- HTTP 动词 ----
    get: function (path, query, options) {
      options = options || {};
      options.query = query;
      return request('GET', path, options);
    },
    post: function (path, body, query, options) {
      options = options || {};
      options.query = query;
      options.body = body === undefined ? {} : body;
      return request('POST', path, options);
    },
    put: function (path, body, query, options) {
      options = options || {};
      options.query = query;
      options.body = body === undefined ? {} : body;
      return request('PUT', path, options);
    },
    del: function (path, query, options) {
      options = options || {};
      options.query = query;
      return request('DELETE', path, options);
    },

    /* ---- 认证 ---- */
    /** 登录：成功返回 {token, user}，并写入本地登录态 */
    login: function (username, password) {
      return request('POST', '/auth/login', { body: { username: username, password: password }, silent401: true })
        .then(function (data) {
          if (!data || !data.token) {
            throw new ApiError(500, '登录响应缺少 token 字段', 200, '/auth/login');
          }
          setAuth(data.token, data.user || null);
          return data;
        });
    },
    logout: function () {
      // 无论后端是否成功都清除本地登录态
      return request('POST', '/auth/logout', { silent401: true })
        .catch(function () { return null; })
        .then(function () { clearAuth(); });
    },
    /**
     * 拉取当前用户信息并刷新本地缓存（刷新页面后同步权限变化）。
     *
     * 后端 /auth/me 返回 {user:{...}, permissions:[...]}，而 /auth/login 返回的 data
     * 里 user 与 permissions 是并列两个字段。这里必须把 user 解包出来，
     * 并把 permissions 合并进用户对象后再落盘 —— 否则本地缓存里存的是外层包装对象，
     * roleCode()/permissions() 都取不到值，角色会被判定为"未就绪"，导致左侧菜单整体不渲染。
     */
    fetchMe: function () {
      return request('GET', '/auth/me').then(function (data) {
        var user = data && data.user ? data.user : data;
        if (user && data && Object.prototype.toString.call(data.permissions) === '[object Array]') {
          user.permissions = data.permissions;
        }
        setAuth(null, user || null);
        return user;
      });
    },

    /* ---- 公共配置 ---- */
    publicConfig: function () {
      return request('GET', '/config/public', { silent401: true });
    },

    /* ----------------------------------------------------------------
     * CSV 导出下载
     * 后端返回 text/csv，这里统一走 blob 下载，避免被 JSON 解包逻辑破坏。
     * -------------------------------------------------------------- */
    exportCsv: function (path, query, fallbackName) {
      var url = getBase() + withQuery(path, query);
      var headers = {};
      var token = getToken();
      if (token) { headers['Authorization'] = 'Bearer ' + token; }

      return window.fetch(url, { method: 'GET', headers: headers }).then(function (resp) {
        if (resp.status === 401) {
          handleUnauthorized(false);
          throw new ApiError(401, '未登录或登录已失效', 401, url);
        }
        if (resp.status === 403) {
          if (AJ.UI && AJ.UI.toast) { AJ.UI.toast('无权限访问该功能', 'error'); }
          throw new ApiError(403, '无权限访问该功能', 403, url);
        }
        // 出错时后端可能仍返回 JSON 包，这里解析出来给出真实原因
        var ctype = resp.headers.get('Content-Type') || '';
        if (!resp.ok || ctype.indexOf('json') !== -1) {
          return resp.text().then(function (text) {
            var msg = text;
            try {
              var p = JSON.parse(text);
              msg = p.message || text;
            } catch (e) { /* 保持原文 */ }
            throw new ApiError(resp.status, msg || '导出失败', resp.status, url);
          });
        }
        var name = extractFilename(resp.headers.get('Content-Disposition')) || fallbackName || 'export.csv';
        return resp.blob().then(function (blob) {
          saveBlob(blob, name);
          return name;
        });
      }, function () {
        throw new ApiError(0, '无法连接后端服务，导出失败（' + getBase() + '）', 0, url);
      });
    },

    /* ----------------------------------------------------------------
     * CSV 批量导入
     * 以 text/csv 原文 POST，同时可用 query 传递可选策略参数。
     * -------------------------------------------------------------- */
    importCsv: function (path, csvText, query) {
      var url = getBase() + withQuery(path, query);
      var headers = {
        'Content-Type': 'text/csv;charset=UTF-8',
        'Accept': 'application/json'
      };
      var token = getToken();
      if (token) { headers['Authorization'] = 'Bearer ' + token; }

      return window.fetch(url, { method: 'POST', headers: headers, body: csvText }).then(function (resp) {
        return resp.text().then(function (text) {
          var payload = null;
          try { payload = JSON.parse(text); } catch (e) { payload = null; }
          if (payload === null) {
            if (resp.status === 401) { handleUnauthorized(false); }
            if (resp.status === 403 && AJ.UI && AJ.UI.toast) { AJ.UI.toast('无权限访问该功能', 'error'); }
            throw new ApiError(resp.status, text || '导入失败', resp.status, url);
          }
          var code = typeof payload.code === 'number' ? payload.code : (resp.status >= 400 ? resp.status : 0);
          if (code === 0) { return payload.data; }
          if (code === 401) { handleUnauthorized(false); }
          if (code === 403 && AJ.UI && AJ.UI.toast) { AJ.UI.toast('无权限访问该功能', 'error'); }
          throw new ApiError(code, payload.message || '导入失败', resp.status, url);
        });
      }, function () {
        throw new ApiError(0, '无法连接后端服务，导入失败（' + getBase() + '）', 0, url);
      });
    }
  };

  AJ.Api = api;

  /* ------------------------------------------------------------------
   * 10. 下载与文件名工具
   * ---------------------------------------------------------------- */
  /** 从 Content-Disposition 解析文件名（兼容 filename*=UTF-8'' 形式） */
  function extractFilename(disposition) {
    if (!disposition) { return ''; }
    var star = /filename\*=UTF-8''([^;]+)/i.exec(disposition);
    if (star) {
      try { return decodeURIComponent(star[1].trim().replace(/^"|"$/g, '')); } catch (e) { return star[1]; }
    }
    var plain = /filename="?([^";]+)"?/i.exec(disposition);
    return plain ? plain[1].trim() : '';
  }

  /** 触发浏览器下载 */
  function saveBlob(blob, filename) {
    var url = window.URL.createObjectURL(blob);
    var a = document.createElement('a');
    a.href = url;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    window.setTimeout(function () { window.URL.revokeObjectURL(url); }, 1500);
  }

  /* ------------------------------------------------------------------
   * 11. 通用小工具（多个页面共用）
   * ---------------------------------------------------------------- */
  var util = AJ.util;

  /** HTML 转义，所有插入 DOM 的动态文本都必须经过它 */
  util.escape = function (value) {
    if (value === null || value === undefined) { return ''; }
    return String(value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  };

  /** 空值占位显示 */
  util.dash = function (value) {
    if (value === null || value === undefined || value === '') { return '—'; }
    return util.escape(value);
  };

  /** 数字格式化：空值为 '—'，支持保留小数位 */
  util.num = function (value, digits) {
    if (value === null || value === undefined || value === '') { return '—'; }
    var n = Number(value);
    if (isNaN(n)) { return '—'; }
    return digits ? n.toFixed(digits) : String(n);
  };

  /** 百分比格式化：入参可能是 0.85 或 85，统一按“数值本身即百分数”处理 */
  util.percent = function (value, digits) {
    if (value === null || value === undefined || value === '') { return '—'; }
    var n = Number(value);
    if (isNaN(n)) { return '—'; }
    return n.toFixed(digits === undefined ? 1 : digits) + '%';
  };

  /** 截断长文本 */
  util.truncate = function (value, len) {
    if (value === null || value === undefined) { return ''; }
    var s = String(value);
    return s.length > len ? s.slice(0, len) + '…' : s;
  };

  /** 今日日期 yyyy-MM-dd */
  util.today = function () {
    var d = new Date();
    var m = d.getMonth() + 1;
    var day = d.getDate();
    return d.getFullYear() + '-' + (m < 10 ? '0' : '') + m + '-' + (day < 10 ? '0' : '') + day;
  };

  /** 当前时间 yyyy-MM-dd HH:mm（AI 记录、听评课时间默认值用） */
  util.now = function () {
    var d = new Date();
    function p(n) { return (n < 10 ? '0' : '') + n; }
    return d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate()) +
      ' ' + p(d.getHours()) + ':' + p(d.getMinutes());
  };

  /** 数字安全取值（图表用） */
  util.toNumber = function (value, fallback) {
    var n = Number(value);
    return isNaN(n) ? (fallback === undefined ? 0 : fallback) : n;
  };

  /** 取对象里的第一个非空字段值（后端字段名可能略有差异时的兼容读取） */
  util.pick = function (obj, names, fallback) {
    if (!obj) { return fallback; }
    for (var i = 0; i < names.length; i++) {
      var v = obj[names[i]];
      if (v !== undefined && v !== null && v !== '') { return v; }
    }
    return fallback;
  };

  /** 中文标签 → 颜色语义（用于状态徽章） */
  util.statusTone = function (text) {
    var s = String(text || '');
    if (/正常|启用|已完成|已督导|开课|通过|优秀|在读|在用/.test(s)) { return 'green'; }
    if (/停用|已停|未通过|异常|删除|冲突|不合格/.test(s)) { return 'red'; }
    if (/待|审核|草稿|未督导|暂停|缺/.test(s)) { return 'orange'; }
    if (/已关联|新|信息/.test(s)) { return 'blue'; }
    return 'gray';
  };

  /** 条件合并对象（构造查询参数用） */
  util.assign = function () {
    var out = {};
    for (var i = 0; i < arguments.length; i++) {
      var src = arguments[i];
      if (!src) { continue; }
      for (var k in src) {
        if (Object.prototype.hasOwnProperty.call(src, k)) { out[k] = src[k]; }
      }
    }
    return out;
  };

})(window, document);
