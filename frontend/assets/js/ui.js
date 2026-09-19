/* ============================================================================
 * ui.js —— 通用 UI 组件层
 *
 * 提供：
 *   toast()             顶部轻提示（success/error/warning/info）
 *   statCard()          统计卡片
 *   progressBar()       线性进度条
 *   progressRing()      环形进度（Canvas 绘制，用于覆盖率等）
 *   emptyState()        空状态块
 *   errorBox()          错误提示块（含“重试”按钮）
 *   loadingBlock()      加载态块
 *   filterBar()         筛选栏
 *   table()             数据表格（含分页器、加载态、空状态、行内操作）
 *   modal()             表单弹窗（新建/编辑）
 *   confirm()           危险操作二次确认弹窗
 *   drawer()            右侧详情抽屉
 *   note()              口径说明块
 *   csvFilePicker()     触发选择本地 CSV 文件并读取文本
 *
 * 全部为原生 DOM/Canvas 实现，无任何外部依赖。
 * ========================================================================== */
(function (window, document) {
  'use strict';

  var AJ = window.AJ = window.AJ || {};
  var util = AJ.util;

  /* ==================================================================
   * 1. Toast 轻提示
   * ================================================================ */
  function toastHost() {
    var host = document.getElementById('toastHost');
    if (!host) {
      host = document.createElement('div');
      host.id = 'toastHost';
      host.className = 'toast-host';
      document.body.appendChild(host);
    }
    return host;
  }

  var TOAST_ICONS = { success: '✓', error: '✕', warning: '!', info: 'i' };

  /**
   * 显示一条提示
   * @param {string} message 文本
   * @param {string} type    success | error | warning | info
   * @param {number} duration 毫秒，默认 3000
   */
  function toast(message, type, duration) {
    type = type || 'info';
    var host = toastHost();
    var el = document.createElement('div');
    el.className = 'toast toast-' + type;

    var icon = document.createElement('span');
    icon.className = 'toast-icon';
    icon.textContent = TOAST_ICONS[type] || 'i';

    var body = document.createElement('span');
    body.className = 'toast-body';
    body.textContent = message === undefined || message === null ? '' : String(message);

    el.appendChild(icon);
    el.appendChild(body);
    host.appendChild(el);

    var timer = window.setTimeout(remove, duration === undefined ? 3000 : duration);
    el.addEventListener('click', remove);

    function remove() {
      window.clearTimeout(timer);
      if (!el.parentNode) { return; }
      el.classList.add('leaving');
      window.setTimeout(function () {
        if (el.parentNode) { el.parentNode.removeChild(el); }
      }, 200);
    }
    return remove;
  }

  /* ==================================================================
   * 2. 统计卡片
   * ================================================================ */
  /**
   * @param {object} opt {label, value, unit, sub, tone, icon}
   *   tone: 默认（蓝）/ success / warning / danger
   */
  function statCard(opt) {
    opt = opt || {};
    var el = document.createElement('div');
    el.className = 'stat-card';

    var tone = opt.tone ? ' ' + opt.tone : '';
    var icon = opt.icon || (opt.label ? String(opt.label).charAt(0) : '·');

    el.innerHTML =
      '<div class="stat-icon' + tone + '">' + util.escape(icon) + '</div>' +
      '<div class="stat-meta">' +
        '<div class="stat-label" title="' + util.escape(opt.label || '') + '">' + util.escape(opt.label || '') + '</div>' +
        '<div class="stat-value">' + util.escape(opt.value === undefined || opt.value === null ? '—' : opt.value) +
          (opt.unit ? '<span class="unit">' + util.escape(opt.unit) + '</span>' : '') +
        '</div>' +
        (opt.sub ? '<div class="stat-sub">' + util.escape(opt.sub) + '</div>' : '') +
      '</div>';
    return el;
  }

  /**
   * 批量渲染统计卡片网格
   * @param {Array} cards [{label,value,unit,sub,tone,icon}]
   * @param {number} cols 每行列数（响应式，默认自动）
   */
  function statGrid(cards, cols) {
    var grid = document.createElement('div');
    grid.className = 'grid grid-' + (cols || 4);
    (cards || []).forEach(function (c) { grid.appendChild(statCard(c)); });
    return grid;
  }

  /* ==================================================================
   * 3. 进度条
   * ================================================================ */
  /**
   * @param {number} percent 0—100
   * @param {string} tone    默认 / success / warning / danger（不传则按数值自动配色）
   */
  function progressBar(percent, tone) {
    var wrap = document.createElement('div');
    wrap.className = 'progress';
    var value = Math.max(0, Math.min(100, util.toNumber(percent, 0)));
    var bar = document.createElement('div');
    bar.className = 'progress-bar' + (tone ? ' ' + tone : (value >= 80 ? ' success' : value >= 50 ? '' : ' warning'));
    bar.style.width = value + '%';
    wrap.appendChild(bar);
    return wrap;
  }

  /** 一行“标签 + 进度条 + 数值” */
  function progressRow(label, value, total, percentText) {
    var row = document.createElement('div');
    row.className = 'progress-row';
    var pct = total ? (util.toNumber(value) / util.toNumber(total) * 100) : 0;
    var labelEl = document.createElement('div');
    labelEl.className = 'progress-label';
    labelEl.title = label;
    labelEl.textContent = label;
    var barWrap = document.createElement('div');
    barWrap.appendChild(progressBar(pct));
    var valEl = document.createElement('div');
    valEl.className = 'progress-val';
    valEl.textContent = percentText !== undefined ? percentText : (pct.toFixed(1) + '%');

    row.appendChild(labelEl);
    row.appendChild(barWrap);
    row.appendChild(valEl);
    return row;
  }

  /* ==================================================================
   * 4. 环形进度（Canvas 手写，不用图表库）
   * ================================================================ */
  /**
   * @param {number} percent 0—100
   * @param {object} opt {size, thickness, color, label, sub}
   */
  function progressRing(percent, opt) {
    opt = opt || {};
    var size = opt.size || 148;
    var thickness = opt.thickness || 14;
    var pct = Math.max(0, Math.min(100, util.toNumber(percent, 0)));
    var color = opt.color || (pct >= 80 ? '#16a34a' : pct >= 50 ? '#2563eb' : '#f59e0b');

    var box = document.createElement('div');
    box.className = 'chart-box';
    box.style.maxWidth = size + 'px';

    var canvas = document.createElement('canvas');
    var dpr = window.devicePixelRatio || 1;
    canvas.width = size * dpr;
    canvas.height = size * dpr;
    canvas.style.height = size + 'px';
    box.appendChild(canvas);

    var ctx = canvas.getContext('2d');
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, size, size);

    var cx = size / 2;
    var cy = size / 2;
    var r = (size - thickness) / 2;

    // 底环
    ctx.beginPath();
    ctx.arc(cx, cy, r, 0, Math.PI * 2);
    ctx.strokeStyle = '#e5e7eb';
    ctx.lineWidth = thickness;
    ctx.stroke();

    // 进度环（从 12 点方向顺时针）
    if (pct > 0) {
      ctx.beginPath();
      ctx.arc(cx, cy, r, -Math.PI / 2, -Math.PI / 2 + Math.PI * 2 * (pct / 100));
      ctx.strokeStyle = color;
      ctx.lineWidth = thickness;
      ctx.lineCap = 'round';
      ctx.stroke();
    }

    // 中心文字
    ctx.fillStyle = '#1f2937';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.font = '700 ' + Math.round(size * 0.2) + 'px -apple-system, "Microsoft YaHei", sans-serif';
    ctx.fillText(pct.toFixed(1) + '%', cx, cy - (opt.label ? size * 0.06 : 0));

    if (opt.label) {
      ctx.fillStyle = '#6b7280';
      ctx.font = '500 ' + Math.round(size * 0.085) + 'px -apple-system, "Microsoft YaHei", sans-serif';
      ctx.fillText(opt.label, cx, cy + size * 0.14);
    }
    return { el: box, redraw: function () {}, destroy: function () {} };
  }

  /* ==================================================================
   * 5. 空状态 / 加载态 / 错误块
   * ================================================================ */
  function emptyState(title, desc, actionHtml) {
    return '<div class="empty">' +
      '<div class="empty-icon">▤</div>' +
      '<div class="empty-title">' + util.escape(title || '暂无数据') + '</div>' +
      (desc ? '<div class="empty-desc">' + util.escape(desc) + '</div>' : '') +
      (actionHtml || '') +
      '</div>';
  }

  function emptyStateEl(title, desc) {
    var el = document.createElement('div');
    el.innerHTML = emptyState(title, desc);
    return el.firstChild;
  }

  function loadingBlock(text) {
    return '<div class="loading"><span class="spinner"></span><span>' +
      util.escape(text || '加载中…') + '</span></div>';
  }

  /**
   * 错误提示块
   * @param {string} message 文本
   * @param {function} onRetry 可选重试回调，传入则显示重试按钮
   */
  function errorBox(message, onRetry) {
    var el = document.createElement('div');
    el.className = 'error-box';
    var span = document.createElement('span');
    span.textContent = message || '加载失败';
    el.appendChild(span);

    if (typeof onRetry === 'function') {
      var actions = document.createElement('span');
      actions.className = 'error-actions';
      var btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'btn btn-sm';
      btn.textContent = '重试';
      btn.addEventListener('click', onRetry);
      actions.appendChild(btn);
      el.appendChild(actions);
    }
    return el;
  }

  /* ==================================================================
   * 6. 口径说明块
   * ================================================================ */
  /** @param {string} html 允许传 HTML（内部使用，调用方保证内容可信） */
  function note(html) {
    var el = document.createElement('div');
    el.className = 'note-box';
    el.innerHTML = '<span class="note-icon">!</span><div>' + html + '</div>';
    return el;
  }

  /* ==================================================================
   * 7. 卡片容器
   * ================================================================ */
  /**
   * @param {object} opt {title, extraHtml, bodyClass, tight}
   * @returns {{el: HTMLElement, body: HTMLElement, head: HTMLElement}}
   */
  function card(opt) {
    opt = opt || {};
    var el = document.createElement('div');
    el.className = 'card';

    var head = null;
    if (opt.title || opt.extraHtml) {
      head = document.createElement('div');
      head.className = 'card-head';
      head.innerHTML = '<div class="card-title">' + util.escape(opt.title || '') + '</div>' +
        (opt.extraHtml ? '<div class="card-extra">' + opt.extraHtml + '</div>' : '');
      el.appendChild(head);
    }

    var body = document.createElement('div');
    body.className = 'card-body' + (opt.tight ? ' tight' : '');
    el.appendChild(body);

    return { el: el, body: body, head: head };
  }

  /* ==================================================================
   * 8. 筛选栏
   * ================================================================ */
  /**
   * @param {Array} fields [{name,label,type,options,value,placeholder,width}]
   *   type: text | select | number | date
   *   options: [{value,label}]
   * @param {object} handlers {onSearch, onReset}
   * @returns {{el:HTMLElement, getValues:function, setValues:function}}
   */
  function filterBar(fields, handlers) {
    handlers = handlers || {};
    var el = document.createElement('div');
    el.className = 'filter-bar';
    var refs = {};

    (fields || []).forEach(function (f) {
      var wrap = document.createElement('div');
      wrap.className = 'filter-field';

      var label = document.createElement('label');
      label.textContent = f.label || f.name;
      wrap.appendChild(label);

      var input;
      if (f.type === 'select') {
        input = document.createElement('select');
        input.className = 'select';
        var opt0 = document.createElement('option');
        opt0.value = '';
        opt0.textContent = f.placeholder || '全部';
        input.appendChild(opt0);
        (f.options || []).forEach(function (o) {
          var op = document.createElement('option');
          op.value = o.value;
          op.textContent = o.label;
          input.appendChild(op);
        });
      } else {
        input = document.createElement('input');
        input.className = 'input';
        input.type = f.type === 'number' ? 'number' : (f.type === 'date' ? 'date' : 'text');
        input.placeholder = f.placeholder || ('请输入' + (f.label || ''));
      }
      if (f.value !== undefined && f.value !== null) { input.value = f.value; }
      if (f.width) { input.style.width = f.width; }

      // 回车即查询
      input.addEventListener('keydown', function (e) {
        if (e.key === 'Enter') { trigger(); }
      });
      if (f.type === 'select' || f.type === 'date') {
        input.addEventListener('change', trigger);
      }

      refs[f.name] = input;
      wrap.appendChild(input);
      el.appendChild(wrap);
    });

    var actions = document.createElement('div');
    actions.className = 'filter-actions';

    var searchBtn = document.createElement('button');
    searchBtn.type = 'button';
    searchBtn.className = 'btn btn-primary';
    searchBtn.textContent = '查询';
    searchBtn.addEventListener('click', trigger);

    var resetBtn = document.createElement('button');
    resetBtn.type = 'button';
    resetBtn.className = 'btn';
    resetBtn.textContent = '重置';
    resetBtn.addEventListener('click', function () {
      for (var k in refs) {
        if (Object.prototype.hasOwnProperty.call(refs, k)) { refs[k].value = ''; }
      }
      if (typeof handlers.onReset === 'function') { handlers.onReset(getValues()); }
      trigger();
    });

    actions.appendChild(searchBtn);
    actions.appendChild(resetBtn);
    el.appendChild(actions);

    function trigger() {
      if (typeof handlers.onSearch === 'function') { handlers.onSearch(getValues()); }
    }

    function getValues() {
      var out = {};
      for (var k in refs) {
        if (Object.prototype.hasOwnProperty.call(refs, k)) {
          var v = refs[k].value;
          out[k] = v === '' ? '' : v;
        }
      }
      return out;
    }

    /** 以代码方式回填筛选值（例如从 URL 参数恢复） */
    function setValues(values) {
      if (!values) { return; }
      for (var k in values) {
        if (Object.prototype.hasOwnProperty.call(refs, k) && values[k] !== undefined) {
          refs[k].value = values[k];
        }
      }
    }

    return { el: el, getValues: getValues, setValues: setValues, search: trigger };
  }

  /* ==================================================================
   * 9. 数据表格（核心组件）
   * ================================================================ */
  /**
   * @param {object} opt
   *   columns: [{
   *     key, title, width, align, className,
   *     render: function(value, row) -> string（默认已做 HTML 转义的内容，需原样输出请用 raw:true）
   *     raw: boolean  render 返回值是否已是安全 HTML
   *   }]
   *   loader: function(query) -> Promise<{records,total,page,size}>
   *   query: 初始查询参数
   *   pageSize: 默认 10
   *   rowKey: 行唯一键，默认 'id'
   *   emptyTitle / emptyDesc
   *   onRowClick: function(row)（可选）
   *   toolbarHtml: 表格上方工具区 HTML（可选）
   * @returns {{el:HTMLElement, reload:function, setQuery:function, getQuery:function, getRecords:function}}
   */
  function table(opt) {
    opt = opt || {};
    var columns = opt.columns || [];
    var state = {
      page: util.toNumber(util.pick(opt.query || {}, ['page'], 1), 1),
      size: util.toNumber(util.pick(opt.query || {}, ['size'], opt.pageSize || 10), 10),
      query: util.assign({}, opt.query || {}),
      records: [],
      total: 0,
      loading: false,
      error: ''
    };
    delete state.query.page;
    delete state.query.size;

    var el = document.createElement('div');
    var toolbarEl = null;
    if (opt.toolbarHtml) {
      toolbarEl = document.createElement('div');
      toolbarEl.className = 'card-body';
      toolbarEl.style.paddingBottom = '0';
      toolbarEl.innerHTML = opt.toolbarHtml;
      el.appendChild(toolbarEl);
    }
    var box = document.createElement('div');
    el.appendChild(box);

    /* ---------------- 渲染 ---------------- */
    function renderBody() {
      box.innerHTML = '';

      if (state.loading) {
        box.innerHTML = loadingBlock('加载中…');
        return;
      }
      if (state.error) {
        box.appendChild(errorBox(state.error, function () { reload(); }));
        return;
      }
      if (!state.records.length) {
        box.innerHTML = emptyState(opt.emptyTitle || '暂无数据', opt.emptyDesc || '可通过上方条件筛选，或点击“新建”添加');
        return;
      }

      var wrap = document.createElement('div');
      wrap.className = 'table-wrap';
      var t = document.createElement('table');
      t.className = 'table';

      // 表头
      var thead = document.createElement('thead');
      var trh = document.createElement('tr');
      columns.forEach(function (c) {
        var th = document.createElement('th');
        if (c.align === 'right') { th.className = 'num'; }
        if (c.align === 'center') { th.className = 'center'; }
        if (c.width) { th.style.width = c.width; }
        th.textContent = c.title;
        trh.appendChild(th);
      });
      thead.appendChild(trh);
      t.appendChild(thead);

      // 表体
      var tbody = document.createElement('tbody');
      state.records.forEach(function (row, idx) {
        var tr = document.createElement('tr');
        columns.forEach(function (c) {
          var td = document.createElement('td');
          if (c.align === 'right') { td.className = 'num'; }
          if (c.align === 'center') { td.className = 'center'; }
          if (c.className) { td.className += ' ' + c.className; }

          var value = c.key ? row[c.key] : undefined;
          if (typeof c.render === 'function') {
            var out = c.render(value, row, idx);
            if (c.raw) { td.innerHTML = out === undefined || out === null ? '' : out; }
            else { td.innerHTML = util.escape(out === undefined || out === null ? '—' : out); }
          } else {
            td.innerHTML = util.dash(value);
          }
          tr.appendChild(td);
        });
        if (typeof opt.onRowClick === 'function') {
          tr.style.cursor = 'pointer';
          tr.addEventListener('click', function (e) {
            // 点到行内按钮时不触发行点击
            if (e.target && e.target.closest && e.target.closest('button, a, input, select')) { return; }
            opt.onRowClick(row);
          });
        }
        tbody.appendChild(tr);
      });
      t.appendChild(tbody);
      wrap.appendChild(t);
      box.appendChild(wrap);

      // 分页器
      box.appendChild(buildPager());
    }

    /* ---------------- 分页器 ---------------- */
    function buildPager() {
      var pager = document.createElement('div');
      pager.className = 'pager';

      var totalPages = Math.max(1, Math.ceil(state.total / state.size));
      var totalSpan = document.createElement('span');
      totalSpan.className = 'pager-total';
      totalSpan.textContent = '共 ' + state.total + ' 条，第 ' + state.page + '/' + totalPages + ' 页';
      pager.appendChild(totalSpan);

      // 每页条数
      var sizeWrap = document.createElement('span');
      sizeWrap.className = 'pager-size';
      var sizeSel = document.createElement('select');
      sizeSel.className = 'select';
      [10, 20, 50, 100].forEach(function (n) {
        var o = document.createElement('option');
        o.value = String(n);
        o.textContent = n + ' 条/页';
        if (n === state.size) { o.selected = true; }
        sizeSel.appendChild(o);
      });
      sizeSel.addEventListener('change', function () {
        state.size = util.toNumber(sizeSel.value, 10);
        state.page = 1;
        reload();
      });
      sizeWrap.appendChild(sizeSel);
      pager.appendChild(sizeWrap);

      // 翻页按钮
      var btns = document.createElement('div');
      btns.className = 'pager-btns';

      btns.appendChild(pagerBtn('上一页', state.page <= 1, function () { goto(state.page - 1); }));

      pageList(state.page, totalPages).forEach(function (p) {
        if (p === '...') {
          var span = document.createElement('span');
          span.className = 'pager-btn';
          span.style.cursor = 'default';
          span.textContent = '…';
          btns.appendChild(span);
        } else {
          var b = pagerBtn(String(p), false, function () { goto(p); });
          if (p === state.page) { b.classList.add('active'); }
          btns.appendChild(b);
        }
      });

      btns.appendChild(pagerBtn('下一页', state.page >= totalPages, function () { goto(state.page + 1); }));
      pager.appendChild(btns);
      return pager;
    }

    function pagerBtn(text, disabled, onClick) {
      var b = document.createElement('button');
      b.type = 'button';
      b.className = 'pager-btn';
      b.textContent = text;
      b.disabled = !!disabled;
      if (!disabled && typeof onClick === 'function') { b.addEventListener('click', onClick); }
      return b;
    }

    /** 生成页码序列，超过 7 页时用省略号收拢 */
    function pageList(current, total) {
      var out = [];
      if (total <= 7) {
        for (var i = 1; i <= total; i++) { out.push(i); }
        return out;
      }
      out.push(1);
      var start = Math.max(2, current - 1);
      var end = Math.min(total - 1, current + 1);
      if (start > 2) { out.push('...'); }
      for (var j = start; j <= end; j++) { out.push(j); }
      if (end < total - 1) { out.push('...'); }
      out.push(total);
      return out;
    }

    function goto(page) {
      var totalPages = Math.max(1, Math.ceil(state.total / state.size));
      var p = Math.min(Math.max(1, page), totalPages);
      if (p === state.page) { return; }
      state.page = p;
      reload();
    }

    /* ---------------- 数据加载 ---------------- */
    function reload() {
      if (typeof opt.loader !== 'function') {
        state.records = [];
        state.total = 0;
        renderBody();
        return window.Promise.resolve();
      }
      state.loading = true;
      state.error = '';
      renderBody();

      var params = util.assign({}, state.query, { page: state.page, size: state.size });
      // 丢弃空值参数，避免后端把空串当成有效筛选条件
      var clean = {};
      for (var k in params) {
        if (Object.prototype.hasOwnProperty.call(params, k)) {
          var v = params[k];
          if (v !== '' && v !== null && v !== undefined) { clean[k] = v; }
        }
      }

      return window.Promise.resolve()
        .then(function () { return opt.loader(clean); })
        .then(function (res) {
          state.loading = false;
          if (!res) {
            state.records = [];
            state.total = 0;
          } else if (Object.prototype.toString.call(res) === '[object Array]') {
            // 兼容后端直接返回数组的情况
            state.records = res;
            state.total = res.length;
          } else {
            state.records = res.records || res.list || res.rows || [];
            state.total = util.toNumber(res.total, state.records.length);
            if (res.page) { state.page = util.toNumber(res.page, state.page); }
            if (res.size) { state.size = util.toNumber(res.size, state.size); }
          }
          renderBody();
          return res;
        }, function (err) {
          state.loading = false;
          state.records = [];
          state.total = 0;
          state.error = (err && err.message) ? err.message : '加载失败';
          renderBody();
          return null;
        });
    }

    /**
     * 更新查询条件（并回到第一页）
     * @param {object} patch 需要合并的查询参数，值为 '' 表示清除该条件
     */
    function setQuery(patch) {
      if (patch) {
        for (var k in patch) {
          if (!Object.prototype.hasOwnProperty.call(patch, k)) { continue; }
          if (patch[k] === '' || patch[k] === null || patch[k] === undefined) {
            delete state.query[k];
          } else {
            state.query[k] = patch[k];
          }
        }
      }
      state.page = 1;
      return reload();
    }

    function resetQuery(patch) {
      state.query = util.assign({}, patch || {});
      state.page = 1;
      return reload();
    }

    renderBody();

    return {
      el: el,
      reload: reload,
      setQuery: setQuery,
      resetQuery: resetQuery,
      getQuery: function () { return util.assign({}, state.query); },
      getRecords: function () { return state.records; },
      getState: function () { return state; }
    };
  }

  /* ==================================================================
   * 10. 弹窗（表单 / 确认）
   * ================================================================ */
  var openModalCount = 0;

  function lockScroll(lock) {
    document.body.style.overflow = lock ? 'hidden' : '';
  }

  /**
   * 通用弹窗
   * @param {object} opt {title, bodyHtml或bodyNode, width:'wide'|'narrow', footer:[{text,cls,onClick,close}], onClose}
   * @returns {{close:function, el:HTMLElement, body:HTMLElement, foot:HTMLElement}}
   */
  function modal(opt) {
    opt = opt || {};
    openModalCount++;
    lockScroll(true);

    var mask = document.createElement('div');
    mask.className = 'modal-mask';

    var box = document.createElement('div');
    box.className = 'modal' + (opt.width ? ' ' + opt.width : '');

    // 头部
    var head = document.createElement('div');
    head.className = 'modal-head';
    var title = document.createElement('div');
    title.className = 'modal-title';
    title.textContent = opt.title || '';
    var closeBtn = document.createElement('button');
    closeBtn.type = 'button';
    closeBtn.className = 'modal-close';
    closeBtn.setAttribute('aria-label', '关闭');
    closeBtn.textContent = '×';
    closeBtn.addEventListener('click', function () { close(); });
    head.appendChild(title);
    head.appendChild(closeBtn);

    // 主体
    var body = document.createElement('div');
    body.className = 'modal-body';
    if (opt.bodyNode) { body.appendChild(opt.bodyNode); }
    else if (opt.bodyHtml) { body.innerHTML = opt.bodyHtml; }

    // 底部
    var foot = document.createElement('div');
    foot.className = 'modal-foot';

    box.appendChild(head);
    box.appendChild(body);
    box.appendChild(foot);
    mask.appendChild(box);
    document.body.appendChild(mask);

    // 点击遮罩关闭
    mask.addEventListener('mousedown', function (e) {
      if (e.target === mask) { close(); }
    });
    // ESC 关闭
    function onKey(e) {
      if (e.key === 'Escape') { close(); }
    }
    document.addEventListener('keydown', onKey);

    var closed = false;
    function close() {
      if (closed) { return; }
      closed = true;
      document.removeEventListener('keydown', onKey);
      if (mask.parentNode) { mask.parentNode.removeChild(mask); }
      openModalCount = Math.max(0, openModalCount - 1);
      if (openModalCount === 0) { lockScroll(false); }
      if (typeof opt.onClose === 'function') { opt.onClose(); }
    }

    var handle = { close: close, el: box, body: body, foot: foot, mask: mask };

    // 底部按钮
    (opt.footer || []).forEach(function (f) {
      var b = document.createElement('button');
      b.type = 'button';
      b.className = 'btn' + (f.cls ? ' ' + f.cls : '');
      b.textContent = f.text;
      b.addEventListener('click', function () {
        if (typeof f.onClick === 'function') {
          // onClick 返回 false 可阻止自动关闭
          var r = f.onClick(handle);
          if (r === false) { return; }
        }
        if (f.close !== false) { close(); }
      });
      foot.appendChild(b);
      f.node = b;
    });

    return handle;
  }

  /**
   * 表单弹窗：自动收集 input/select/textarea 的值（按 name 属性）
   * @param {object} opt
   *   title, fieldsHtml, width, values(回填), submitText,
   *   onSubmit: function(values, handle) -> Promise|false（返回 Promise 时按钮自动进入 loading）
   *   onValidate: function(values) -> string|null（返回错误文案则阻止提交）
   */
  function formModal(opt) {
    opt = opt || {};
    var bodyWrap = document.createElement('div');
    bodyWrap.innerHTML = opt.fieldsHtml || '';

    // 回填初始值
    if (opt.values) {
      var controls = bodyWrap.querySelectorAll('[name]');
      for (var i = 0; i < controls.length; i++) {
        var el = controls[i];
        var name = el.getAttribute('name');
        var v = opt.values[name];
        if (v === undefined || v === null) { continue; }
        if (el.type === 'checkbox') {
          el.checked = !!v;
        } else {
          el.value = v;
        }
      }
    }

    var handle = modal({
      title: opt.title,
      width: opt.width,
      bodyNode: bodyWrap,
      footer: [
        { text: opt.cancelText || '取消', cls: '', close: true },
        {
          text: opt.submitText || '保存',
          cls: 'btn-primary',
          close: false,
          onClick: function (h) { return submit(h); }
        }
      ]
    });

    var submitBtn = handle.foot.querySelector('.btn-primary');

    /** 读取表单值：checkbox 读 checked，其余读 value */
    function collect() {
      var out = {};
      var controls = bodyWrap.querySelectorAll('[name]');
      for (var i = 0; i < controls.length; i++) {
        var el = controls[i];
        var name = el.getAttribute('name');
        if (el.type === 'checkbox') { out[name] = el.checked; }
        else if (el.type === 'number') {
          out[name] = el.value === '' ? null : Number(el.value);
        } else { out[name] = el.value; }
      }
      return out;
    }

    function submit(h) {
      var values = collect();

      // 清除旧错误
      var oldErrs = bodyWrap.querySelectorAll('.form-error');
      for (var i = 0; i < oldErrs.length; i++) {
        oldErrs[i].parentNode.removeChild(oldErrs[i]);
      }
      var invalid = bodyWrap.querySelectorAll('.invalid');
      for (var j = 0; j < invalid.length; j++) { invalid[j].classList.remove('invalid'); }

      if (typeof opt.onValidate === 'function') {
        var msg = opt.onValidate(values);
        if (msg) {
          toast(msg, 'warning');
          return false;
        }
      }

      if (typeof opt.onSubmit !== 'function') { return true; }

      var ret;
      try {
        ret = opt.onSubmit(values, h);
      } catch (e) {
        toast((e && e.message) || '提交失败', 'error');
        return false;
      }

      // 返回 Promise：接管提交过程，成功才关闭
      if (ret && typeof ret.then === 'function') {
        if (submitBtn) { submitBtn.disabled = true; submitBtn.textContent = '提交中…'; }
        ret.then(function (ok) {
          if (submitBtn) { submitBtn.disabled = false; submitBtn.textContent = opt.submitText || '保存'; }
          if (ok !== false) { h.close(); }
        }, function () {
          if (submitBtn) { submitBtn.disabled = false; submitBtn.textContent = opt.submitText || '保存'; }
        });
        return false;
      }
      return ret === false ? false : true;
    }

    return handle;
  }

  /**
   * 危险操作确认框
   * @param {object} opt {title, message, warn, confirmText, onConfirm:function|Promise}
   */
  function confirm(opt) {
    opt = opt || {};
    var body = document.createElement('div');
    body.innerHTML = '<div class="confirm-text">' + util.escape(opt.message || '确定执行该操作吗？') + '</div>' +
      (opt.warn ? '<div class="confirm-warn">' + util.escape(opt.warn) + '</div>' : '');

    var handle = modal({
      title: opt.title || '请确认',
      width: 'narrow',
      bodyNode: body,
      footer: [
        { text: '取消', close: true },
        {
          text: opt.confirmText || '确定',
          cls: opt.danger === false ? 'btn-primary' : 'btn-danger',
          close: false,
          onClick: function (h) {
            var r = typeof opt.onConfirm === 'function' ? opt.onConfirm() : null;
            if (r && typeof r.then === 'function') {
              var btn = h.foot.querySelector('.btn-danger') || h.foot.querySelector('.btn-primary');
              if (btn) { btn.disabled = true; btn.textContent = '处理中…'; }
              r.then(function () { h.close(); }, function () {
                if (btn) { btn.disabled = false; btn.textContent = opt.confirmText || '确定'; }
              });
              return false;
            }
            return true;
          }
        }
      ]
    });
    return handle;
  }

  /* ==================================================================
   * 11. 右侧详情抽屉
   * ================================================================ */
  /**
   * @param {object} opt {title, bodyNode或bodyHtml, footerHtml, onClose}
   */
  function drawer(opt) {
    opt = opt || {};
    var mask = document.createElement('div');
    mask.className = 'drawer-mask';

    var panel = document.createElement('div');
    panel.className = 'drawer';

    var head = document.createElement('div');
    head.className = 'drawer-head';
    var title = document.createElement('div');
    title.className = 'drawer-title';
    title.textContent = opt.title || '详情';
    var closeBtn = document.createElement('button');
    closeBtn.type = 'button';
    closeBtn.className = 'modal-close';
    closeBtn.setAttribute('aria-label', '关闭');
    closeBtn.textContent = '×';
    closeBtn.addEventListener('click', function () { close(); });
    head.appendChild(title);
    head.appendChild(closeBtn);

    var body = document.createElement('div');
    body.className = 'drawer-body';
    if (opt.bodyNode) { body.appendChild(opt.bodyNode); }
    else if (opt.bodyHtml) { body.innerHTML = opt.bodyHtml; }

    panel.appendChild(head);
    panel.appendChild(body);

    if (opt.footerHtml) {
      var foot = document.createElement('div');
      foot.className = 'drawer-foot';
      foot.innerHTML = opt.footerHtml;
      panel.appendChild(foot);
    }

    document.body.appendChild(mask);
    document.body.appendChild(panel);

    function onKey(e) {
      if (e.key === 'Escape') { close(); }
    }
    document.addEventListener('keydown', onKey);
    mask.addEventListener('click', function () { close(); });

    var closed = false;
    function close() {
      if (closed) { return; }
      closed = true;
      document.removeEventListener('keydown', onKey);
      if (mask.parentNode) { mask.parentNode.removeChild(mask); }
      if (panel.parentNode) { panel.parentNode.removeChild(panel); }
      if (typeof opt.onClose === 'function') { opt.onClose(); }
    }

    return { close: close, body: body, el: panel };
  }

  /* ==================================================================
   * 12. 详情描述列表构建工具
   * ================================================================ */
  /**
   * @param {Array} items [{label, value, raw}]
   * @returns {HTMLElement}
   */
  function descList(items) {
    var dl = document.createElement('dl');
    dl.className = 'desc-list';
    (items || []).forEach(function (it) {
      var dt = document.createElement('dt');
      dt.textContent = it.label;
      var dd = document.createElement('dd');
      if (it.raw) { dd.innerHTML = it.value === undefined || it.value === null ? '—' : it.value; }
      else { dd.innerHTML = util.dash(it.value); }
      dl.appendChild(dt);
      dl.appendChild(dd);
    });
    return dl;
  }

  /**
   * 生成一个带标题的详情分区。
   * 标题同时写入 data-title 属性，便于后续用 fillSection 局部替换内容而不丢标题。
   */
  function descSection(titleText, contentNode) {
    var sec = document.createElement('div');
    sec.className = 'desc-section';
    sec.setAttribute('data-title', titleText || '');
    var h = document.createElement('h4');
    h.textContent = titleText;
    sec.appendChild(h);
    if (contentNode) { sec.appendChild(contentNode); }
    return sec;
  }

  /**
   * 替换详情分区里的内容（保留标题行）。
   * 会先清掉 loading / error-box / 旧内容，再插入新节点。
   */
  function fillSection(section, node) {
    var h = section.querySelector('h4');
    while (section.firstChild) { section.removeChild(section.firstChild); }
    if (h) { section.appendChild(h); } else {
      var title = section.getAttribute('data-title') || '';
      if (title) {
        var nh = document.createElement('h4');
        nh.textContent = title;
        section.appendChild(nh);
      }
    }
    if (node) { section.appendChild(node); }
    return section;
  }

  /** 一段可换行的纯文本块（课程简介等） */
  function descText(text) {
    var el = document.createElement('div');
    el.className = 'desc-text';
    el.textContent = text === null || text === undefined || text === '' ? '（暂无内容）' : String(text);
    return el;
  }

  /**
   * 迷你表格（详情里展示子列表）
   * @param {Array} columns [{title,key,align,render,raw}]
   * @param {Array} rows 数据
   * @param {string} emptyText 空数据文案
   */
  function miniTable(columns, rows, emptyText) {
    var wrap = document.createElement('div');
    if (!rows || !rows.length) {
      wrap.innerHTML = emptyState(emptyText || '暂无数据');
      return wrap;
    }
    var t = document.createElement('table');
    t.className = 'mini-table';
    var thead = document.createElement('thead');
    var trh = document.createElement('tr');
    (columns || []).forEach(function (c) {
      var th = document.createElement('th');
      th.textContent = c.title;
      if (c.align === 'right') { th.style.textAlign = 'right'; }
      trh.appendChild(th);
    });
    thead.appendChild(trh);
    t.appendChild(thead);

    var tbody = document.createElement('tbody');
    rows.forEach(function (row) {
      var tr = document.createElement('tr');
      (columns || []).forEach(function (c) {
        var td = document.createElement('td');
        var v = c.key ? row[c.key] : undefined;
        if (typeof c.render === 'function') {
          var out = c.render(v, row);
          if (c.raw) { td.innerHTML = out === undefined || out === null ? '' : out; }
          else { td.innerHTML = util.escape(out === undefined || out === null ? '—' : out); }
        } else {
          td.innerHTML = util.dash(v);
        }
        if (c.align === 'right') { td.style.textAlign = 'right'; }
        tr.appendChild(td);
      });
      tbody.appendChild(tr);
    });
    t.appendChild(tbody);
    wrap.appendChild(t);
    return wrap;
  }

  /* ==================================================================
   * 12.1 远程下拉选择器（表单里选择课程 / 教师 / 教学班用）
   * ================================================================ */
  /**
   * 生成一个可输入过滤的原生 <select>，选项由接口懒加载。
   * 用原生 select 保证零依赖、键盘可达；通过“输入过滤”输入框缩小候选集。
   *
   * @param {object} cfg
   *   name      字段名（提交时用）
   *   label     显示标签
   *   loader    function(keyword) -> Promise<[{value,label}]>
   *   value     当前值
   *   valueLabel 当前值的显示文案（编辑时后端只给了 id 时用）
   *   required  是否必填
   *   placeholder 空选项文案
   * @returns {HTMLElement}
   */
  function remoteSelect(cfg) {
    cfg = cfg || {};
    var wrap = document.createElement('div');
    wrap.className = 'form-item';

    var filterId = 'rs-' + cfg.name + '-' + Math.floor(Math.random() * 100000);
    wrap.innerHTML =
      '<label class="form-label" for="' + filterId + '">' + util.escape(cfg.label || cfg.name) +
      (cfg.required ? '<span class="req">*</span>' : '') + '</label>' +
      '<input class="input" id="' + filterId + '" placeholder="输入关键词过滤选项（可留空）" style="margin-bottom:6px">' +
      '<select class="select" name="' + util.escape(cfg.name) + '"></select>';

    var filterInput = wrap.querySelector('input');
    var select = wrap.querySelector('select');
    var loaded = false;

    function fill(options, currentValue) {
      var keep = currentValue !== undefined ? String(currentValue) : select.value;
      var html = '<option value="">' + util.escape(cfg.placeholder || '请选择') + '</option>';
      var matched = false;
      (options || []).forEach(function (o) {
        if (String(o.value) === keep) { matched = true; }
        html += '<option value="' + util.escape(o.value) + '"' +
          (String(o.value) === keep ? ' selected' : '') + '>' + util.escape(o.label) + '</option>';
      });
      // 当前值不在候选集里（例如编辑态后端只返回了 id）时补一个占位选项
      if (keep && !matched) {
        html += '<option value="' + util.escape(keep) + '" selected>' +
          util.escape(cfg.valueLabel ? (cfg.valueLabel + '（当前值）') : ('ID: ' + keep)) + '</option>';
      }
      select.innerHTML = html;
    }

    function load(keyword, currentValue) {
      if (typeof cfg.loader !== 'function') { return window.Promise.resolve(); }
      select.disabled = true;
      return window.Promise.resolve()
        .then(function () { return cfg.loader(keyword || ''); })
        .then(function (options) {
          select.disabled = false;
          fill(options, currentValue);
          loaded = true;
        }, function (err) {
          select.disabled = false;
          select.innerHTML = '<option value="">选项加载失败</option>';
          toast((err && err.message) || '下拉选项加载失败', 'error');
        });
    }

    // 首次加载 + 输入过滤（防抖 300ms）
    var timer = null;
    filterInput.addEventListener('input', function () {
      window.clearTimeout(timer);
      timer = window.setTimeout(function () { load(filterInput.value); }, 300);
    });

    // 初始值
    fill([], cfg.value);
    load('', cfg.value);

    wrap._select = select;
    wrap._reload = load;
    wrap._setValue = function (v) { fill(currentOptions, v); };
    var currentOptions = [];
    wrap._setOptions = function (opts) { currentOptions = opts || []; fill(currentOptions); };

    return wrap;
  }

  /* ==================================================================
   * 13. CSV 文件选择与读取
   * ================================================================ */
  /**
   * 弹出文件选择框，读取 CSV 文本
   * @param {function} onLoaded function(text, file)
   * @param {string} accept 默认 .csv
   */
  function csvFilePicker(onLoaded, accept) {
    var input = document.createElement('input');
    input.type = 'file';
    input.accept = accept || '.csv,text/csv';
    input.style.display = 'none';
    document.body.appendChild(input);

    input.addEventListener('change', function () {
      var file = input.files && input.files[0];
      if (!file) {
        if (input.parentNode) { input.parentNode.removeChild(input); }
        return;
      }
      var reader = new window.FileReader();
      reader.onload = function () {
        var text = String(reader.result || '');
        // 去掉 UTF-8 BOM，避免首个表头字段名带不可见字符
        if (text.charCodeAt(0) === 0xFEFF) { text = text.slice(1); }
        onLoaded(text, file);
        if (input.parentNode) { input.parentNode.removeChild(input); }
      };
      reader.onerror = function () {
        toast('文件读取失败，请重试', 'error');
        if (input.parentNode) { input.parentNode.removeChild(input); }
      };
      reader.readAsText(file, 'UTF-8');
    });

    input.click();
  }

  /** 把数组渲染成 CSV 文本（供“导入模板下载”使用） */
  function buildCsv(headers, rows) {
    function cell(v) {
      var s = v === null || v === undefined ? '' : String(v);
      return /[",\n]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s;
    }
    var lines = [];
    if (headers) { lines.push(headers.map(cell).join(',')); }
    (rows || []).forEach(function (r) { lines.push(r.map(cell).join(',')); });
    return lines.join('\r\n');
  }

  /* ==================================================================
   * 14. 导出
   * ================================================================ */
  AJ.UI = {
    toast: toast,
    statCard: statCard,
    statGrid: statGrid,
    progressBar: progressBar,
    progressRow: progressRow,
    progressRing: progressRing,
    emptyState: emptyState,
    emptyStateEl: emptyStateEl,
    loadingBlock: loadingBlock,
    errorBox: errorBox,
    note: note,
    card: card,
    filterBar: filterBar,
    table: table,
    modal: modal,
    formModal: formModal,
    confirm: confirm,
    drawer: drawer,
    descList: descList,
    descSection: descSection,
    fillSection: fillSection,
    descText: descText,
    miniTable: miniTable,
    remoteSelect: remoteSelect,
    csvFilePicker: csvFilePicker,
    buildCsv: buildCsv
  };

})(window, document);
