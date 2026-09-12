// V64 additive live-performance presentation layer.
// Existing V20/V21/V28/V63 backend contracts and engines remain unchanged.
// This file fixes presentation-only lifecycle/metric issues; it does not replace any simulator path.
(() => {
  'use strict';
  const MAX = 60;
  const history = [];
  const $ = id => document.getElementById(id);
  const N = (v, d = 0) => Number.isFinite(Number(v)) ? Number(v) : d;
  const finite = v => Number.isFinite(Number(v));
  const clamp = (v, lo, hi) => Math.max(lo, Math.min(hi, v));
  const pct = v => clamp(N(v) * 100, 0, 100);
  const percentValue = v => {
    const n = N(v, NaN);
    if (!Number.isFinite(n)) return NaN;
    // BLER/CRC values in the V63 API are fractions; utilizationPercent is already 0..100.
    return n <= 1 ? n * 100 : n;
  };
  const avg = (us, key) => us.reduce((a, u) => a + N(typeof key === 'function' ? key(u) : u[key]), 0) / Math.max(1, us.length);

  function utilization(data, us) {
    const m = data.metrics || {};
    const direct = m.utilizationPercent ?? m.prbUtilizationPercent ?? m.prbUtilization;
    if (finite(direct)) return clamp(percentValue(direct), 0, 100);

    // Fallback for older payloads: allocated PRBs are cumulative over slots, so include slots
    // in the denominator. Keep the final display bounded to the physical 0..100% range.
    const cfg = data.config || {};
    const slots = Math.max(1, N(cfg.slots, 1));
    const cells = Math.max(1, N(cfg.cells, 1));
    const prbs = Math.max(1, N(cfg.prbs, 1));
    const allocated = us.reduce((a, u) => a + N(u.totalAllocatedPrbs ?? u.allocatedPrbs), 0);
    return clamp(allocated / (prbs * cells * slots) * 100, 0, 100);
  }

  function sample(data) {
    if (!data || !Array.isArray(data.ueStates)) return;
    const us = data.ueStates;
    const m = data.metrics || {};
    history.push({
      t: new Date(),
      throughput: N(m.totalThroughputMbps),
      sinr: avg(us, u => u.meanSinrDb ?? u.sinrDb),
      bler: percentValue(avg(us, u => u.meanBler ?? u.bler)),
      prb: utilization(data, us),
      crc: percentValue(m.phyCrcPassRate)
    });
    while (history.length > MAX) history.shift();
  }

  function syncSystemMetrics(data) {
    const m = data?.metrics || {};
    const us = Array.isArray(data?.ueStates) ? data.ueStates : [];
    const prb = utilization(data || {}, us);
    const throughput = N(m.totalThroughputMbps, NaN);
    const bler = percentValue(avg(us, u => u.meanBler ?? u.bler));
    const crc = percentValue(m.phyCrcPassRate);
    if (finite(throughput) && $('systemThr')) $('systemThr').textContent = throughput.toFixed(1) + ' Mbps';
    if (finite(prb) && $('systemPrb')) $('systemPrb').textContent = prb.toFixed(1) + '%';
    if (finite(bler) && $('systemBler')) $('systemBler').textContent = bler.toFixed(2) + '%';
    if (finite(crc) && $('systemCrc')) $('systemCrc').textContent = crc.toFixed(1) + '%';
  }

  // The existing HMI uses DIV containers for the four chart IDs. Create a canvas inside
  // each container without changing the surrounding DOM or chart API.
  function canvasFor(id) {
    const host = $(id);
    if (!host) return null;
    if (host instanceof HTMLCanvasElement) return host;
    let c = host.querySelector('canvas[data-v64-performance-canvas]');
    if (!c) {
      host.innerHTML = '';
      c = document.createElement('canvas');
      c.setAttribute('data-v64-performance-canvas', '1');
      c.style.width = '100%';
      c.style.height = '150px';
      c.style.display = 'block';
      host.appendChild(c);
    }
    return c;
  }

  function resizeCanvas(c) {
    if (!c || !(c instanceof HTMLCanvasElement)) return null;
    const d = window.devicePixelRatio || 1;
    const r = c.getBoundingClientRect();
    const w = Math.max(180, r.width || c.parentElement?.clientWidth || 180);
    const h = Math.max(110, r.height || c.parentElement?.clientHeight || 150);
    const width = Math.round(w * d), height = Math.round(h * d);
    if (c.width !== width || c.height !== height) {
      c.width = width;
      c.height = height;
    }
    const x = c.getContext('2d');
    if (!x) return null;
    x.setTransform(d, 0, 0, d, 0, 0);
    return { x, w, h };
  }

  function draw(id, key, label, unit, min, max) {
    const c = canvasFor(id);
    if (!c) return;
    const q = resizeCanvas(c);
    if (!q) return;
    const { x, w, h } = q;
    x.clearRect(0, 0, w, h);
    x.fillStyle = '#04101b';
    x.fillRect(0, 0, w, h);
    const pad = { l: 42, r: 12, t: 18, b: 25 };
    const pw = Math.max(1, w - pad.l - pad.r), ph = Math.max(1, h - pad.t - pad.b);
    const vals = history.map(z => N(z[key], NaN));
    let lo = min, hi = max;
    if (min === null || max === null) {
      const f = vals.filter(Number.isFinite);
      const a = f.length ? Math.min(...f) : 0;
      const b = f.length ? Math.max(...f) : 1;
      const span = Math.max(0.5, b - a);
      lo = Math.max(0, a - span * 0.15);
      hi = b + span * 0.15;
      if (key === 'throughput') lo = 0;
    }
    if (!Number.isFinite(lo)) lo = 0;
    if (!Number.isFinite(hi) || hi <= lo) hi = lo + 1;

    x.strokeStyle = '#15344d';
    x.lineWidth = 1;
    for (let i = 0; i <= 4; i++) {
      const yy = pad.t + ph * i / 4;
      x.beginPath(); x.moveTo(pad.l, yy); x.lineTo(w - pad.r, yy); x.stroke();
      x.fillStyle = '#6f879b'; x.font = '9px system-ui'; x.textAlign = 'right';
      x.fillText((hi - (hi - lo) * i / 4).toFixed(key === 'throughput' ? 0 : 1), pad.l - 6, yy + 3);
    }
    x.fillStyle = '#7f9ab1'; x.font = '9px system-ui'; x.textAlign = 'left'; x.fillText(label, pad.l, pad.t - 6);
    x.textAlign = 'right'; x.fillText(unit, w - pad.r, pad.t - 6);

    const points = vals.map((v, i) => ({ v, i })).filter(p => Number.isFinite(p.v));
    if (points.length > 1) {
      x.beginPath();
      points.forEach((p, j) => {
        const xx = pad.l + pw * p.i / Math.max(1, vals.length - 1);
        const yy = pad.t + ph * (1 - clamp((p.v - lo) / (hi - lo), 0, 1));
        j ? x.lineTo(xx, yy) : x.moveTo(xx, yy);
      });
      x.strokeStyle = '#16a5ff'; x.lineWidth = 2; x.stroke();
      const last = points[points.length - 1];
      const xx = pad.l + pw * last.i / Math.max(1, vals.length - 1);
      const yy = pad.t + ph * (1 - clamp((last.v - lo) / (hi - lo), 0, 1));
      x.fillStyle = '#dff5ff'; x.beginPath(); x.arc(xx, yy, 3, 0, Math.PI * 2); x.fill();
    } else {
      x.fillStyle = '#587187'; x.font = '10px system-ui'; x.textAlign = 'center';
      x.fillText('Waiting for live samples…', pad.l + pw / 2, pad.t + ph / 2);
    }
    x.fillStyle = '#587187'; x.font = '8px system-ui'; x.textAlign = 'left';
    x.fillText(history.length ? '-' + Math.max(0, history.length - 1) + ' samples' : '', pad.l, h - 7);
    x.textAlign = 'right'; x.fillText('now', w - pad.r, h - 7);
  }

  function renameChartTitles() {
    // The renderer below is a time-series view, not a per-UE bar chart. Rename only the
    // existing headings so the visualization cannot claim a different aggregation.
    const names = [
      ['chartThroughput', 'Throughput history'],
      ['chartSinr', 'Average SINR history'],
      ['chartBler', 'TB BLER history'],
      ['chartPrb', 'PRB utilization history']
    ];
    names.forEach(([id, text]) => {
      const host = $(id);
      const title = host?.closest('.chartCard')?.querySelector('.title h2');
      if (title) title.textContent = text;
    });
  }

  function render() {
    draw('chartThroughput', 'throughput', 'THROUGHPUT', 'Mbps', 0, null);
    draw('chartSinr', 'sinr', 'AVERAGE SINR', 'dB', null, null);
    draw('chartBler', 'bler', 'TB BLER', '%', 0, 100);
    draw('chartPrb', 'prb', 'PRB UTILIZATION', '%', 0, 100);
    renameChartTitles();
  }

  function installProvenance() {
    if (document.getElementById('v64ExecutionContext')) return;
    const panel = $('panel-performance');
    if (!panel) return;
    const box = document.createElement('div');
    box.id = 'v64ExecutionContext';
    box.className = 'v64-execution-context';
    box.innerHTML = '<b>Execution provenance</b><span>UI: V64 · Radio lab API: /api/lab · Performance source: V63 integrated radio/PHY result · PHY probe: V20/V21</span><span>V74–V84 research modules remain additive library components; this legacy/reference HMI does not silently claim to execute them.</span><span>Metric definitions: TB BLER = mean UE BLER; PHY CRC pass rate = TB/PHY CRC result fraction; PRB utilization = occupied PRB-time / configured PRB-time, bounded to 0–100%.</span>';
    const first = panel.querySelector('.panelGrid, .layout, .card');
    if (first) first.parentElement.insertBefore(box, first);
    else panel.insertBefore(box, panel.firstChild);
  }

  function installStyles() {
    if (document.getElementById('v64PerformanceFixStyle')) return;
    const style = document.createElement('style');
    style.id = 'v64PerformanceFixStyle';
    style.textContent = `
      .v64-execution-context{margin:0 0 10px;padding:10px 12px;border:1px solid #15415f;border-left:3px solid #16a9ff;border-radius:6px;background:#061827;color:#9fb5c8;font-size:10px;line-height:1.55;display:grid;gap:3px}
      .v64-execution-context b{color:#e6f4ff;font-size:11px}
      .v64-execution-context span{display:block}
      #chartThroughput,#chartSinr,#chartBler,#chartPrb{width:100%;height:150px;display:block;background:#04101b;border-radius:4px}
    `;
    document.head.appendChild(style);
  }

  function reset() { history.length = 0; render(); }
  function ingest(data) { sample(data); syncSystemMetrics(data); render(); }
  window.__v64Performance = { ingest, reset, render, history };

  function hook() {
    const d = window.__v64LastData;
    if (d && window.__v64PerformanceLast !== d) {
      window.__v64PerformanceLast = d;
      ingest(d);
    }
    installProvenance();
    render();
  }

  function boot() {
    installStyles();
    installProvenance();
    render();
    if (!window.__v64PerformanceTimer) window.__v64PerformanceTimer = setInterval(hook, 250);
  }

  // DOM-ready + delayed retries make the renderer resilient to the HMI's dynamic tab lifecycle.
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot, { once: true });
  else boot();
  [100, 400, 1000, 2000].forEach(ms => setTimeout(boot, ms));
  window.addEventListener('resize', render);
  window.addEventListener('pageshow', render);
  const resetButton = $('reset');
  if (resetButton) resetButton.addEventListener('click', () => setTimeout(reset, 0));
})();