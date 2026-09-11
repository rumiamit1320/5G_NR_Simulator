// V64 additive PDF report layer.
// Self-contained engineering PDF writer: no CDN, npm bundle, or network dependency.
(() => {
  'use strict';
  const $ = id => document.getElementById(id);
  const clean = v => String(v ?? '').replace(/\s+/g, ' ').trim();
  const val = id => { const e = $(id); return e ? clean(e.value ?? e.textContent) : '—'; };
  const num = (v, d = 2) => v == null || Number.isNaN(Number(v)) ? '—' : Number(v).toFixed(d);
  const pct = (v, d = 2) => v == null || Number.isNaN(Number(v)) ? '—' : (Number(v) * 100).toFixed(d) + '%';

  // Minimal PDF 1.4 writer. This deliberately avoids external libraries so the
  // report works offline and cannot fail because a CDN is blocked.
  const pdfEscape = s => String(s ?? '').replace(/\\/g, '\\\\').replace(/\(/g, '\\(').replace(/\)/g, '\\)').replace(/[\r\n]+/g, ' ');
  function makePdf(pages) {
    const objects = [];
    const add = body => { objects.push(body); return objects.length; };
    const font = add('<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>');
    const fontBold = add('<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>');
    const pageIds = [];
    const contentIds = [];
    pages.forEach(page => {
      const stream = page.join('\n') + '\n';
      const cid = add(`<< /Length ${stream.length} >>\nstream\n${stream}endstream`);
      contentIds.push(cid);
      pageIds.push(add('PLACEHOLDER'));
    });
    const pagesId = add('PLACEHOLDER');
    const catalogId = add(`<< /Type /Catalog /Pages ${pagesId} 0 R >>`);
    pageIds.forEach((pid, i) => {
      objects[pid - 1] = `<< /Type /Page /Parent ${pagesId} 0 R /MediaBox [0 0 595.28 841.89] /Resources << /Font << /F1 ${font} 0 R /F2 ${fontBold} 0 R >> >> /Contents ${contentIds[i]} 0 R >>`;
    });
    objects[pagesId - 1] = `<< /Type /Pages /Kids [${pageIds.map(id => id + ' 0 R').join(' ')}] /Count ${pageIds.length} >>`;
    let out = '%PDF-1.4\n%\xE2\xE3\xCF\xD3\n';
    const offsets = [0];
    objects.forEach((obj, i) => { offsets.push(out.length); out += `${i + 1} 0 obj\n${obj}\nendobj\n`; });
    const xref = out.length;
    out += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`;
    for (let i = 1; i <= objects.length; i++) out += String(offsets[i]).padStart(10, '0') + ' 00000 n \n';
    out += `trailer\n<< /Size ${objects.length + 1} /Root ${catalogId} 0 R /Info ${add('<< /Title (5G NR Simulator V64 Simulation Report) /Author (5G NR Simulator V64 HMI) /Creator (5G_NR_Simulator) >>')} 0 R >>\nstartxref\n${xref}\n%%EOF`;
    return new Blob([out], { type: 'application/pdf' });
  }

  function pageBuilder() {
    const pages = [];
    let cmds = [];
    let y = 805;
    const newPage = () => { if (cmds.length) pages.push(cmds); cmds = []; y = 805; header(); };
    const header = () => {
      cmds.push('0.04 0.10 0.16 rg 0 800 595 42 re f');
      text('5G NR SIMULATOR', 40, 824, 11, true, '1 1 1');
      text('V64 RADIO ENVIRONMENT LAB', 555, 824, 8, true, '0.85 0.9 0.94', true);
    };
    const footer = (id, pageNo) => {
      cmds.push('0.65 0.68 0.72 RG 40 34 515 0 l S');
      text(`Report ID: ${id}`, 40, 21, 7, false, '0.38 0.42 0.46');
      text(`Page ${pageNo}`, 555, 21, 7, false, '0.38 0.42 0.46', true);
    };
    const text = (s, x, yy, size = 9, bold = false, rgb = '0.12 0.15 0.18', right = false) => {
      cmds.push(`${rgb} rg BT /${bold ? 'F2' : 'F1'} ${size} Tf ${x} ${yy} Td ${right ? '1 0 0 1' : ''} (${pdfEscape(s)}) Tj ET`);
    };
    const wrapped = (s, x, width, size = 8.5, leading = 13) => {
      const words = clean(s).split(' '); let line = '';
      words.forEach(w => { const candidate = line ? line + ' ' + w : w; if (candidate.length > Math.max(1, Math.floor(width / (size * 0.48)))) { text(line, x, y, size); y -= leading; line = w; } else line = candidate; });
      if (line) { text(line, x, y, size); y -= leading; }
    };
    const heading = title => { if (y < 90) newPage(); text(title, 40, y, 13, true, '0.06 0.16 0.25'); y -= 7; cmds.push('0.22 0.45 0.63 RG 40 ' + y + ' 515 0 l S'); y -= 20; };
    const line = (label, value, x = 44, w = 170) => { text(label, x, y, 8, true, '0.35 0.4 0.45'); text(String(value ?? '—'), x + w, y, 8); y -= 15; };
    const start = () => { header(); return { newPage, heading, wrapped, text, line, footer, pages, get y() { return y; }, set y(v) { y = v; } }; };
    return start();
  }

  function buildReport() {
    const data = window.__v64LastData || {};
    const c = data.config || {};
    const m = data.metrics || {};
    const us = data.ueStates || [];
    const now = new Date();
    const id = `V64-${now.getFullYear()}${String(now.getMonth()+1).padStart(2,'0')}${String(now.getDate()).padStart(2,'0')}-${String(now.getHours()).padStart(2,'0')}${String(now.getMinutes()).padStart(2,'0')}${String(now.getSeconds()).padStart(2,'0')}`;
    const generated = now.toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'medium' });
    const b = pageBuilder();

    // Cover page.
    b.text('5G NR SIMULATOR', 58, 700, 14, true, '0.16 0.44 0.61');
    b.text('V64 RADIO ENVIRONMENT', 58, 655, 27, true, '0.04 0.10 0.16');
    b.text('SIMULATION REPORT', 58, 622, 24, true, '0.04 0.10 0.16');
    b.text('Engineering analysis and simulation results', 58, 592, 11, false, '0.35 0.4 0.45');
    b.text('DOCUMENT CONTROL', 58, 525, 9, true, '0.16 0.44 0.61');
    b.line('Report ID', id, 58, 115); b.line('Generated by', '5G NR Simulator V64 HMI', 58, 115); b.line('Generated on', generated, 58, 115); b.line('Application', '5G_NR_Simulator', 58, 115); b.line('Simulation mode', 'Real-Time Closed Loop', 58, 115);
    b.wrapped('Generated from the current V64 HMI state. Existing V61 PHY, V62 radio environment, V63 integration and V64 closed-loop computation engines are unchanged by this report layer.', 58, 475, 8, 12);
    b.newPage();

    b.heading('Executive Summary');
    b.wrapped('This report records the current V64 real-time closed-loop simulation state, including radio conditions, PHY performance, scheduler behavior and per-UE results. It is an engineering simulation artifact and is not a 3GPP conformance report.', 40, 515);
    b.heading('Key Performance Indicators');
    b.line('Total Throughput', m.totalThroughputMbps == null ? '—' : num(m.totalThroughputMbps, 3) + ' Mbps');
    b.line('System Fairness', num(m.systemFairness, 5));
    b.line('PHY CRC Pass Rate', pct(m.phyCrcPassRate, 3));
    b.line('PHY BER', m.phyBer == null ? '—' : Number(m.phyBer).toExponential(4));

    b.heading('Simulation Configuration');
    b.line('Cells', c.cells ?? val('cells')); b.line('UE count', c.ueCount ?? c.ue ?? val('ue')); b.line('PRBs / cell', c.prbs ?? val('prbs')); b.line('SCS', `${c.scs ?? val('scs')} kHz`); b.line('UE velocity', `${c.velocityKmh ?? c.velocity ?? val('velocity')} km/h`); b.line('Simulation slots', c.slots ?? val('slots')); b.line('Payload', `${c.payloadBits ?? '128'} bits`); b.line('TX / RX', `${c.tx ?? 4} / ${c.rx ?? 4}`); b.line('Layers', c.layers ?? 1);

    b.heading('Per-UE Radio / PHY Results');
    if (!us.length) b.wrapped('No UE result is currently available.', 40, 515);
    us.forEach(u => {
      if (b.y < 120) b.newPage(), b.heading('Per-UE Results — Continuation');
      b.text(`UE ${u.ueId}`, 44, b.y, 9, true, '0.16 0.44 0.61'); b.y -= 14;
      b.line('SINR / CQI / MCS', `${num(u.sinrDb ?? u.meanSinrDb,2)} dB / ${u.cqi ?? u.meanCqi ?? '—'} / ${u.mcs ?? u.meanMcs ?? '—'}`);
      b.line('Allocated PRBs', u.allocatedPrbs ?? u.totalAllocatedPrbs ?? '—'); b.line('Throughput', num(u.throughputMbps,3) + ' Mbps'); b.line('BLER', u.bler == null && u.meanBler == null ? '—' : num((u.bler ?? u.meanBler) * 100,3) + '%'); b.line('PHY CRC pass rate', pct(u.phyCrcPassRate,1)); b.line('PHY BER', u.phyBer == null ? '—' : Number(u.phyBer).toExponential(4)); b.y -= 5;
    });

    const blocks = [['PHY Pipeline Result','phyResult'],['MIMO / CSI Result','mimoResult'],['Scheduler Experiment Result','schedulerResult'],['Parameter Sweep Result','sweepResult']];
    blocks.forEach(([title, id2]) => { const e = $(id2); const t = clean(e?.textContent || e?.value || ''); if (t && t !== '—' && t !== 'Running…') { if (b.y < 150) b.newPage(); b.heading(title); b.wrapped(t, 40, 515, 7.5, 10); } });

    if (b.y < 150) b.newPage();
    b.heading('Simulation Log');
    const logs = Array.from(document.querySelectorAll('#logBody tr[data-v64-log]'));
    if (!logs.length) b.wrapped('No log events recorded.', 40, 515);
    logs.forEach(r => { if (b.y < 100) b.newPage(), b.heading('Simulation Log — Continuation'); const td = r.querySelectorAll('td'); b.wrapped(`[${clean(td[0]?.textContent)}] [${clean(td[1]?.textContent)}] ${clean(td[2]?.textContent)} ${clean(td[3]?.textContent)}`, 40, 515, 7.5, 10); });

    const history = window.__v64Performance?.history || [];
    if (b.y < 150) b.newPage();
    b.heading('Live Performance History');
    b.wrapped(`The HMI retained ${history.length} live performance samples. The numerical history below is the data source used by the live performance plots.`, 40, 515, 8, 11);
    history.slice(-100).forEach((s, i) => { if (b.y < 75) b.newPage(), b.heading('Performance History — Continuation'); b.text(`${i+1}. Throughput ${num(s.throughput,3)} Mbps | Avg SINR ${num(s.sinr,2)} dB | Avg BLER ${num(s.bler,3)}% | PRB ${num(s.prb,2)}%`, 44, b.y, 7.5); b.y -= 11; });

    if (b.y < 120) b.newPage();
    b.heading('Engineering Notes');
    b.wrapped('V64 closes the loop by feeding measured PHY outcomes back into proportional-fair scheduling history. BLER/ACK-NACK feedback is represented at the closed-loop scheduler layer; this implementation does not claim full HARQ retransmission and soft-combining behavior.', 40, 515, 8, 12);
    b.wrapped('Radio and PHY results are generated by the existing simulator engines. This report layer only reads the current HMI state and serializes it into a self-contained PDF document.', 40, 515, 8, 12);

    pagesWithFooters(b, id);
    const blob = makePdf(b.pages);
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a'); a.href = url; a.download = `5G_NR_V64_Report_${now.toISOString().replace(/[:.]/g,'-')}.pdf`; document.body.appendChild(a); a.click(); a.remove(); setTimeout(() => URL.revokeObjectURL(url), 5000);
  }

  function pagesWithFooters(b, id) {
    b.pages.forEach((p, i) => {
      const footer = [];
      footer.push('0.65 0.68 0.72 RG 40 34 515 0 l S');
      const e = s => String(s).replace(/\\/g,'\\\\').replace(/\(/g,'\\(').replace(/\)/g,'\\)').replace(/[\r\n]+/g,' ');
      footer.push(`0.38 0.42 0.46 rg BT /F1 7 Tf 40 21 Td (Report ID: ${e(id)}) Tj ET`);
      footer.push(`0.38 0.42 0.46 rg BT /F1 7 Tf 555 21 Td (${i+1} / ${b.pages.length}) Tj ET`);
      p.push(...footer);
    });
  }

  function install() {
    const actions = document.querySelector('.actions');
    if (!actions || $('v64ReportBtn')) return;
    const btn = document.createElement('button');
    btn.id = 'v64ReportBtn'; btn.type = 'button'; btn.textContent = '▣ Download Report'; btn.title = 'Generate a self-contained engineering PDF report'; btn.onclick = buildReport;
    actions.appendChild(btn);
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', install); else install();
})();
