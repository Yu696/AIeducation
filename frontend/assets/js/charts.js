/* ============================================================================
 * charts.js —— 纯 Canvas 手写图表库
 *
 * 不依赖任何图表库（无 ECharts / Chart.js / CDN），全部用 Canvas 2D 绘制：
 *   barChart()    纵向柱状图（教师授课分布等）
 *   hBarChart()   横向条形图（按院系覆盖率等，适合中文长标签）
 *   donutChart()  环形图 / 饼图（督导覆盖率、占比结构）
 *   lineChart()   折线图（分数分布、按学期趋势）
 *
 * 设计要点：
 *   1. 每个函数返回一个 DOM 元素（含 <canvas>），内部用 ResizeObserver 自适应重绘
 *   2. 按 devicePixelRatio 放大画布，保证高分屏不糊
 *   3. 数据为空时显示“暂无数据”，不抛异常
 *   4. 支持 destroy()：页面切换时释放监听，避免内存泄漏
 * ========================================================================== */
(function (window, document) {
  'use strict';

  var AJ = window.AJ = window.AJ || {};
  var util = AJ.util;

  /** 统一配色（与全局设计规范一致） */
  var PALETTE = ['#2563eb', '#16a34a', '#f59e0b', '#dc2626', '#8b5cf6',
    '#0891b2', '#ea580c', '#65a30d', '#db2777', '#475569'];
  var AXIS = '#9ca3af';
  var GRID = '#eef1f5';
  var TEXT = '#4b5563';

  var FONT = '-apple-system, "Microsoft YaHei", "PingFang SC", sans-serif';

  function color(i) { return PALETTE[i % PALETTE.length]; }

  /* ------------------------------------------------------------------
   * 通用：创建画布容器并处理自适应
   * @param {number} height 逻辑高度（CSS 像素）
   * @param {function} drawFn function(ctx, w, h) 绘制回调
   * @returns {{el:HTMLElement, destroy:function, redraw:function}}
   * ---------------------------------------------------------------- */
  function createCanvas(height, drawFn) {
    var box = document.createElement('div');
    box.className = 'chart-box';

    var canvas = document.createElement('canvas');
    canvas.style.width = '100%';
    canvas.style.height = height + 'px';
    box.appendChild(canvas);

    var ctx = canvas.getContext('2d');
    var lastWidth = 0;

    function redraw() {
      var w = box.clientWidth || box.parentNode && box.parentNode.clientWidth || 600;
      if (w <= 0) { w = 600; }
      var h = height;
      var dpr = window.devicePixelRatio || 1;

      canvas.width = Math.round(w * dpr);
      canvas.height = Math.round(h * dpr);
      canvas.style.height = h + 'px';

      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      ctx.clearRect(0, 0, w, h);
      ctx.font = '12px ' + FONT;
      lastWidth = w;

      try {
        drawFn(ctx, w, h);
      } catch (e) {
        // 绘制异常不应导致整页崩溃
        ctx.clearRect(0, 0, w, h);
        ctx.fillStyle = '#9ca3af';
        ctx.textAlign = 'center';
        ctx.fillText('图表绘制失败', w / 2, h / 2);
      }
    }

    var observer = null;
    if (typeof window.ResizeObserver === 'function') {
      observer = new window.ResizeObserver(function () {
        // 宽度变化才重绘，避免高度抖动导致死循环
        var w = box.clientWidth;
        if (w > 0 && Math.abs(w - lastWidth) > 1) { redraw(); }
      });
      observer.observe(box);
    } else {
      window.addEventListener('resize', redraw);
    }

    // 首次绘制：等一帧，确保容器已进入文档流并拿到宽度
    window.requestAnimationFrame(redraw);

    return {
      el: box,
      redraw: redraw,
      destroy: function () {
        if (observer) { observer.disconnect(); }
        else { window.removeEventListener('resize', redraw); }
      }
    };
  }

  /** 空数据占位 */
  function emptyChart(height, text) {
    var box = document.createElement('div');
    box.className = 'chart-box chart-empty';
    box.style.height = height + 'px';
    box.textContent = text || '暂无数据';
    return { el: box, destroy: function () {}, redraw: function () {} };
  }

  /* ------------------------------------------------------------------
   * 通用：数据归一化
   *   支持 [{label,value}]、[['标签', 3]]、{标签: 3} 三种输入
   * ---------------------------------------------------------------- */
  function normalize(data) {
    var out = [];
    if (!data) { return out; }
    if (Object.prototype.toString.call(data) === '[object Array]') {
      data.forEach(function (d) {
        if (d === null || d === undefined) { return; }
        if (Object.prototype.toString.call(d) === '[object Array]') {
          out.push({ label: String(d[0]), value: util.toNumber(d[1], 0) });
        } else if (typeof d === 'object') {
          out.push({
            label: String(util.pick(d, ['label', 'name', 'key', 'departmentName', 'deptName', 'teacherName', 'courseName', 'semester', 'range', 'type'], '')),
            value: util.toNumber(util.pick(d, ['value', 'count', 'total', 'num', 'amount', 'rate', 'percent'], 0), 0)
          });
        } else {
          out.push({ label: String(d), value: 0 });
        }
      });
    } else if (typeof data === 'object') {
      for (var k in data) {
        if (Object.prototype.hasOwnProperty.call(data, k)) {
          out.push({ label: k, value: util.toNumber(data[k], 0) });
        }
      }
    }
    return out;
  }

  /** 计算“好看”的 Y 轴最大刻度 */
  function niceMax(max) {
    if (max <= 0) { return 1; }
    var exp = Math.floor(Math.log(max) / Math.LN10);
    var base = Math.pow(10, exp);
    var n = max / base;
    var step;
    if (n <= 1) { step = 1; }
    else if (n <= 2) { step = 2; }
    else if (n <= 2.5) { step = 2.5; }
    else if (n <= 5) { step = 5; }
    else { step = 10; }
    return step * base;
  }

  /** 绘制文字标签，过长时自动旋转 -35°（中文长标签常用处理） */
  function drawXLabel(ctx, text, x, y, maxWidth, color) {
    ctx.fillStyle = color || TEXT;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'top';
    var metrics = ctx.measureText(text);
    if (metrics.width <= maxWidth) {
      ctx.fillText(text, x, y);
      return;
    }
    ctx.save();
    ctx.translate(x, y);
    ctx.rotate(-35 * Math.PI / 180);
    ctx.textAlign = 'right';
    ctx.textBaseline = 'middle';
    ctx.fillText(text, 0, 0);
    ctx.restore();
  }

  /* ==================================================================
   * 1. 纵向柱状图
   * ================================================================ */
  /**
   * @param {Array} data [{label,value}]
   * @param {object} opt {height, color, valueUnit, colorByIndex:boolean}
   */
  function barChart(data, opt) {
    opt = opt || {};
    var height = opt.height || 260;
    var rows = normalize(data);
    if (!rows.length) { return emptyChart(height, opt.emptyText); }

    return createCanvas(height, function (ctx, w, h) {
      var padTop = 22;
      var padBottom = rows.length > 6 || maxLabelLen(rows) > 4 ? 52 : 30;
      var padLeft = 46;
      var padRight = 12;
      var plotW = Math.max(10, w - padLeft - padRight);
      var plotH = Math.max(10, h - padTop - padBottom);

      var max = niceMax(Math.max.apply(null, rows.map(function (r) { return r.value; })));
      var ticks = 5;

      // 网格线与 Y 轴刻度
      ctx.strokeStyle = GRID;
      ctx.lineWidth = 1;
      ctx.fillStyle = TEXT;
      ctx.textAlign = 'right';
      ctx.textBaseline = 'middle';
      for (var i = 0; i <= ticks; i++) {
        var y = padTop + plotH - (plotH * i / ticks);
        ctx.beginPath();
        ctx.moveTo(padLeft, y);
        ctx.lineTo(padLeft + plotW, y);
        ctx.stroke();
        var v = max * i / ticks;
        ctx.fillText(formatTick(v), padLeft - 7, y);
      }

      // 柱子
      var slot = plotW / rows.length;
      var barW = Math.min(46, slot * 0.62);
      var radius = Math.min(4, barW / 2);

      rows.forEach(function (r, idx) {
        var cx = padLeft + slot * idx + slot / 2;
        var barH = max > 0 ? (r.value / max) * plotH : 0;
        var x = cx - barW / 2;
        var y = padTop + plotH - barH;

        ctx.fillStyle = opt.color || color(idx);
        if (barH > 0.5) {
          roundRectTop(ctx, x, y, barW, barH, radius);
          ctx.fill();
        }

        // 数值标注
        if (barH > 0 || r.value === 0) {
          ctx.fillStyle = '#374151';
          ctx.textAlign = 'center';
          ctx.textBaseline = 'bottom';
          ctx.fillText(formatTick(r.value), cx, Math.max(padTop - 2, y - 4));
        }

        // X 轴标签
        drawXLabel(ctx, r.label, cx, padTop + plotH + 7, Math.max(34, slot - 4));
      });

      // X 轴基线
      ctx.strokeStyle = AXIS;
      ctx.beginPath();
      ctx.moveTo(padLeft, padTop + plotH);
      ctx.lineTo(padLeft + plotW, padTop + plotH);
      ctx.stroke();

      // 单位说明
      if (opt.valueUnit) {
        ctx.fillStyle = '#9ca3af';
        ctx.textAlign = 'left';
        ctx.textBaseline = 'top';
        ctx.fillText('单位：' + opt.valueUnit, padLeft, 4);
      }
    });
  }

  function maxLabelLen(rows) {
    var m = 0;
    rows.forEach(function (r) { m = Math.max(m, r.label.length); });
    return m;
  }

  /** 顶部圆角矩形路径 */
  function roundRectTop(ctx, x, y, w, h, r) {
    var rr = Math.min(r, w / 2, h);
    ctx.beginPath();
    ctx.moveTo(x, y + h);
    ctx.lineTo(x, y + rr);
    ctx.quadraticCurveTo(x, y, x + rr, y);
    ctx.lineTo(x + w - rr, y);
    ctx.quadraticCurveTo(x + w, y, x + w, y + rr);
    ctx.lineTo(x + w, y + h);
    ctx.closePath();
  }

  /** 刻度数字格式化：整数不带小数，小数保留 1 位 */
  function formatTick(v) {
    var n = Number(v);
    if (isNaN(n)) { return '0'; }
    if (Math.abs(n - Math.round(n)) < 0.001) { return String(Math.round(n)); }
    return n.toFixed(1);
  }

  /* ==================================================================
   * 2. 横向条形图（中文长标签更友好）
   * ================================================================ */
  /**
   * @param {Array} data [{label,value}]
   * @param {object} opt {height, color, max, showValue, valueUnit, labelWidth}
   */
  function hBarChart(data, opt) {
    opt = opt || {};
    var rows = normalize(data);
    var height = opt.height || Math.max(140, rows.length * 32 + 30);
    if (!rows.length) { return emptyChart(height, opt.emptyText); }

    return createCanvas(height, function (ctx, w, h) {
      var labelW = opt.labelWidth || Math.min(140, Math.max(70, longestLabel(ctx, rows) + 12));
      var padRight = 54;
      var padTop = 8;
      var plotW = Math.max(10, w - labelW - padRight);
      var rowH = (h - padTop * 2) / rows.length;
      var barH = Math.min(18, rowH * 0.58);

      var max = opt.max !== undefined ? opt.max : Math.max.apply(null, rows.map(function (r) { return r.value; }));
      if (max <= 0) { max = 1; }

      rows.forEach(function (r, idx) {
        var cy = padTop + rowH * idx + rowH / 2;
        var barW = (r.value / max) * plotW;

        // 标签（右对齐，超长截断）
        ctx.fillStyle = TEXT;
        ctx.textAlign = 'right';
        ctx.textBaseline = 'middle';
        ctx.fillText(clipText(ctx, r.label, labelW - 10), labelW - 8, cy);

        // 底槽
        ctx.fillStyle = '#f1f5f9';
        roundRectFull(ctx, labelW, cy - barH / 2, plotW, barH, barH / 2);
        ctx.fill();

        // 条
        if (barW > 0.5) {
          ctx.fillStyle = opt.color || color(idx);
          roundRectFull(ctx, labelW, cy - barH / 2, Math.max(barH, barW), barH, barH / 2);
          ctx.fill();
        }

        // 数值
        ctx.fillStyle = '#374151';
        ctx.textAlign = 'left';
        ctx.textBaseline = 'middle';
        var txt = (opt.showValue === false) ? '' : (formatTick(r.value) + (opt.valueUnit || ''));
        ctx.fillText(txt, labelW + Math.min(plotW, Math.max(barH, barW)) + 7, cy);
      });
    });
  }

  function roundRectFull(ctx, x, y, w, h, r) {
    var rr = Math.min(r, w / 2, h / 2);
    ctx.beginPath();
    ctx.moveTo(x + rr, y);
    ctx.lineTo(x + w - rr, y);
    ctx.quadraticCurveTo(x + w, y, x + w, y + rr);
    ctx.lineTo(x + w, y + h - rr);
    ctx.quadraticCurveTo(x + w, y + h, x + w - rr, y + h);
    ctx.lineTo(x + rr, y + h);
    ctx.quadraticCurveTo(x, y + h, x, y + h - rr);
    ctx.lineTo(x, y + rr);
    ctx.quadraticCurveTo(x, y, x + rr, y);
    ctx.closePath();
  }

  /** 测量最长标签像素宽度（取样前 12 条，避免数据量大时耗时） */
  function longestLabel(ctx, rows) {
    var max = 0;
    var limit = Math.min(rows.length, 12);
    for (var i = 0; i < limit; i++) {
      max = Math.max(max, ctx.measureText(rows[i].label).width);
    }
    return max;
  }

  /** 按像素宽度截断文本并加省略号 */
  function clipText(ctx, text, maxWidth) {
    if (ctx.measureText(text).width <= maxWidth) { return text; }
    var t = text;
    while (t.length > 1 && ctx.measureText(t + '…').width > maxWidth) {
      t = t.slice(0, -1);
    }
    return t + '…';
  }

  /* ==================================================================
   * 3. 环形图 / 饼图
   * ================================================================ */
  /**
   * @param {Array} data [{label,value}]
   * @param {object} opt {height, donut:boolean, centerLabel, centerValue, unit, legend:boolean}
   */
  function donutChart(data, opt) {
    opt = opt || {};
    var height = opt.height || 240;
    var rows = normalize(data).filter(function (r) { return r.value > 0; });
    if (!rows.length) { return emptyChart(height, opt.emptyText); }

    var holder = document.createElement('div');

    var chart = createCanvas(height, function (ctx, w, h) {
      var total = rows.reduce(function (s, r) { return s + r.value; }, 0);
      if (total <= 0) { total = 1; }

      var cx = w / 2;
      var cy = h / 2;
      var radius = Math.min(w, h) / 2 - 10;
      var inner = opt.donut === false ? 0 : radius * 0.6;

      var start = -Math.PI / 2;
      rows.forEach(function (r, idx) {
        var angle = (r.value / total) * Math.PI * 2;
        ctx.beginPath();
        ctx.moveTo(cx, cy);
        ctx.arc(cx, cy, radius, start, start + angle);
        ctx.closePath();
        ctx.fillStyle = color(idx);
        ctx.fill();
        ctx.strokeStyle = '#fff';
        ctx.lineWidth = 2;
        ctx.stroke();
        start += angle;
      });

      // 挖空成环
      if (inner > 0) {
        ctx.beginPath();
        ctx.arc(cx, cy, inner, 0, Math.PI * 2);
        ctx.fillStyle = '#fff';
        ctx.fill();
      }

      // 中心文字
      var centerValue = opt.centerValue !== undefined ? opt.centerValue : null;
      var centerLabel = opt.centerLabel;
      if (centerValue !== null || centerLabel) {
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        if (centerValue !== null) {
          ctx.fillStyle = '#1f2937';
          ctx.font = '700 ' + Math.round(radius * 0.34) + 'px ' + FONT;
          ctx.fillText(String(centerValue), cx, cy - (centerLabel ? radius * 0.12 : 0));
        }
        if (centerLabel) {
          ctx.fillStyle = '#6b7280';
          ctx.font = '500 ' + Math.round(radius * 0.15) + 'px ' + FONT;
          ctx.fillText(centerLabel, cx, cy + radius * 0.26);
        }
      }
    });
    holder.appendChild(chart.el);

    // 图例
    if (opt.legend !== false) {
      var total2 = rows.reduce(function (s, r) { return s + r.value; }, 0) || 1;
      var legend = document.createElement('div');
      legend.className = 'chart-legend';
      rows.forEach(function (r, idx) {
        var item = document.createElement('span');
        item.className = 'lg-item';
        item.innerHTML = '<span class="lg-dot" style="background:' + color(idx) + '"></span>' +
          util.escape(r.label) + ' ' + formatTick(r.value) +
          '（' + (r.value / total2 * 100).toFixed(1) + '%）';
        legend.appendChild(item);
      });
      holder.appendChild(legend);
    }

    return {
      el: holder,
      redraw: chart.redraw,
      destroy: chart.destroy
    };
  }

  /* ==================================================================
   * 4. 折线图（含面积填充与数据点）
   * ================================================================ */
  /**
   * @param {Array} data [{label,value}]
   * @param {object} opt {height, color, area:boolean, yUnit, yMin, yMax}
   */
  function lineChart(data, opt) {
    opt = opt || {};
    var height = opt.height || 260;
    var rows = normalize(data);
    if (!rows.length) { return emptyChart(height, opt.emptyText); }

    return createCanvas(height, function (ctx, w, h) {
      var padTop = 22;
      var padBottom = rows.length > 7 ? 46 : 30;
      var padLeft = 46;
      var padRight = 14;
      var plotW = Math.max(10, w - padLeft - padRight);
      var plotH = Math.max(10, h - padTop - padBottom);

      var values = rows.map(function (r) { return r.value; });
      var dataMax = Math.max.apply(null, values);
      var dataMin = Math.min.apply(null, values);
      // 允许从 0 开始（分数分布更适合从 0 起），默认自适应留出上下边距
      var yMax = opt.yMax !== undefined ? opt.yMax : niceMax(dataMax);
      var yMin = opt.yMin !== undefined ? opt.yMin : 0;
      if (yMax <= yMin) { yMax = yMin + 1; }

      var ticks = 5;
      ctx.strokeStyle = GRID;
      ctx.lineWidth = 1;
      ctx.fillStyle = TEXT;
      ctx.textAlign = 'right';
      ctx.textBaseline = 'middle';
      for (var i = 0; i <= ticks; i++) {
        var y = padTop + plotH - (plotH * i / ticks);
        ctx.beginPath();
        ctx.moveTo(padLeft, y);
        ctx.lineTo(padLeft + plotW, y);
        ctx.stroke();
        ctx.fillText(formatTick(yMin + (yMax - yMin) * i / ticks), padLeft - 7, y);
      }

      // 坐标换算
      function px(idx) {
        if (rows.length === 1) { return padLeft + plotW / 2; }
        return padLeft + (plotW * idx / (rows.length - 1));
      }
      function py(v) {
        var ratio = (v - yMin) / (yMax - yMin);
        return padTop + plotH - Math.max(0, Math.min(1, ratio)) * plotH;
      }

      var lineColor = opt.color || color(0);

      // 面积填充
      if (opt.area !== false) {
        var grad = ctx.createLinearGradient(0, padTop, 0, padTop + plotH);
        grad.addColorStop(0, hexToRgba(lineColor, 0.25));
        grad.addColorStop(1, hexToRgba(lineColor, 0.02));
        ctx.beginPath();
        ctx.moveTo(px(0), padTop + plotH);
        rows.forEach(function (r, idx) { ctx.lineTo(px(idx), py(r.value)); });
        ctx.lineTo(px(rows.length - 1), padTop + plotH);
        ctx.closePath();
        ctx.fillStyle = grad;
        ctx.fill();
      }

      // 折线
      ctx.beginPath();
      rows.forEach(function (r, idx) {
        if (idx === 0) { ctx.moveTo(px(idx), py(r.value)); }
        else { ctx.lineTo(px(idx), py(r.value)); }
      });
      ctx.strokeStyle = lineColor;
      ctx.lineWidth = 2;
      ctx.lineJoin = 'round';
      ctx.stroke();

      // 数据点 + 数值
      rows.forEach(function (r, idx) {
        var x = px(idx);
        var y = py(r.value);
        ctx.beginPath();
        ctx.arc(x, y, 3.5, 0, Math.PI * 2);
        ctx.fillStyle = '#fff';
        ctx.fill();
        ctx.strokeStyle = lineColor;
        ctx.lineWidth = 2;
        ctx.stroke();

        // 点数不多时标数值，避免拥挤
        if (rows.length <= 14) {
          ctx.fillStyle = '#374151';
          ctx.textAlign = 'center';
          ctx.textBaseline = 'bottom';
          ctx.fillText(formatTick(r.value), x, y - 7);
        }

        // X 轴标签
        drawXLabel(ctx, r.label, x, padTop + plotH + 7, Math.max(40, plotW / rows.length - 4));
      });

      ctx.strokeStyle = AXIS;
      ctx.beginPath();
      ctx.moveTo(padLeft, padTop + plotH);
      ctx.lineTo(padLeft + plotW, padTop + plotH);
      ctx.stroke();

      if (opt.yUnit) {
        ctx.fillStyle = '#9ca3af';
        ctx.textAlign = 'left';
        ctx.textBaseline = 'top';
        ctx.fillText('单位：' + opt.yUnit, padLeft, 4);
      }
      // dataMin 参与计算，避免“常量序列”被误判
      void dataMin;
    });
  }

  /** #rrggbb → rgba(r,g,b,a) */
  function hexToRgba(hex, alpha) {
    var h = String(hex).replace('#', '');
    if (h.length === 3) {
      h = h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2);
    }
    var n = parseInt(h, 16);
    if (isNaN(n)) { return 'rgba(37,99,235,' + alpha + ')'; }
    var r = (n >> 16) & 255;
    var g = (n >> 8) & 255;
    var b = n & 255;
    return 'rgba(' + r + ',' + g + ',' + b + ',' + alpha + ')';
  }

  /* ==================================================================
   * 5. 导出
   * ================================================================ */
  AJ.Charts = {
    barChart: barChart,
    hBarChart: hBarChart,
    donutChart: donutChart,
    lineChart: lineChart,
    palette: PALETTE,
    /** 供页面统一处理“渲染图表 + 记录 destroy”的辅助方法 */
    mount: function (container, chart) {
      if (!container || !chart) { return chart; }
      container.innerHTML = '';
      container.appendChild(chart.el);
      return chart;
    }
  };

})(window, document);
