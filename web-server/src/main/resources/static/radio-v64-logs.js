// V64 additive simulation-log presentation layer.
// Keeps radio-v64-fixed.js logging/storage and all simulator APIs unchanged.
(() => {
  'use strict';

  const esc = value => String(value ?? '').replace(/[&<>\"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','\"':'&quot;'}[c]));
  const $ = id => document.getElementById(id);

  function configSnapshot() {
    const ids = [['slots','Slots'],['ue','UEs'],['cells','Cells'],['prbs','PRBs'],['scs','SCS'],['velocity','Velocity']];
    return ids.map(([id,label]) => {
      const e = $(id);
      if (!e) return null;
      return `${label}: ${e.value}${id==='scs'?' kHz':id==='velocity'?' km/h':''}`;
    }).filter(Boolean);
  }

  function eventDetail(message) {
    const m = String(message || '');
    const d = window.__v64LastData;
    const lines = [];
    if (/Simulation completed/i.test(m) && d) {
      lines.push(`V63 execution completed with ${d.config?.slots ?? '—'} slots, ${d.config?.ueCount ?? '—'} UEs, ${d.config?.cells ?? '—'} cells and ${d.config?.prbs ?? '—'} PRBs.`);
      lines.push(`Aggregate throughput: ${Number(d.metrics?.totalThroughputMbps ?? 0).toFixed(2)} Mbps.`);
      lines.push(`Mean system SINR: ${((d.ueStates||[]).reduce((a,u)=>a+Number(u.meanSinrDb||0),0)/Math.max(1,(d.ueStates||[]).length)).toFixed(2)} dB.`);
      lines.push(`CRC pass rate: ${(Number(d.metrics?.phyCrcPassRate ?? 0)*100).toFixed(2)}%.`);
    } else if (/PHY pipeline/i.test(m)) {
      lines.push('Executed the existing V20 PUSCH and V21 SRS/channel-probe checks.');
      lines.push(`Controls: SNR ${$('phySnr')?.value ?? '—'} dB · MCS ${$('phyMcs')?.value ?? '—'} · Layers ${$('phyLayers')?.value ?? '—'} · PRBs ${$('phyPrb')?.value ?? '—'}.`);
    } else if (/MIMO/i.test(m)) {
      const s = window.__v64MimoState;
      lines.push('Executed the existing V21 SRS and V28 beam-sweep adapters.');
      if (s) lines.push(`Configuration: ${s.tx} TX · ${s.rx} RX · ${s.rankAdaptive ? 'adaptive rank' : `rank ${s.rank}`} · ${s.beamSweep ? `${s.beams}-beam sweep` : 'single beam'}.`);
    } else if (/Scheduler/i.test(m)) {
      lines.push(`V22 proportional-fair scheduler experiment: ${$('expUe')?.value ?? '—'} UEs · ${$('expPrb')?.value ?? '—'} PRBs · base CQI ${$('expCqi')?.value ?? '—'} · base SINR ${$('expSinr')?.value ?? '—'} dB.`);
    } else if (/sweep/i.test(m)) {
      lines.push(`Five-point V63 SNR sweep completed for ${$('sweepUe')?.value ?? '—'} UEs and ${$('sweepPrb')?.value ?? '—'} PRBs per point.`);
    } else if (/reset/i.test(m)) {
      lines.push('HMI state was cleared; the next Start/Live cycle will create a new V63 result set.');
    } else if (/Exported/i.test(m)) {
      lines.push('The current configuration, latest metrics, UE states and client log history were serialized to JSON.');
    } else if (/Opened/i.test(m)) {
      lines.push(`Current HMI configuration: ${configSnapshot().join(' · ')}.`);
    } else if (/velocity/i.test(m)) {
      lines.push(`UE velocity control is ${$('velocity')?.value ?? '—'} km/h.`);
    } else if (/ERROR/i.test(m)) {
      lines.push('The client recorded an error while executing the requested HMI operation.');
    } else {
      lines.push('Client-side HMI event. Existing simulator/backend logic is unchanged.');
      const cfg = configSnapshot();
      if (cfg.length) lines.push(`Configuration at inspection time: ${cfg.join(' · ')}.`);
    }
    return lines.join('\n');
  }

  function copyText() {
    const rows = Array.from(document.querySelectorAll('#logBody tr[data-v64-log]'));
    const text = rows.length ? rows.map(r => {
      const cells = r.querySelectorAll('td');
      return `[${cells[0]?.textContent || ''}] [${cells[1]?.textContent || ''}] ${cells[2]?.textContent || ''}\n  ${cells[3]?.textContent || ''}`;
    }).join('\n\n') : 'No simulation log events.';
    const done = ok => {
      const b = $('copyLogs');
      if (!b) return;
      const old = b.textContent;
      b.textContent = ok ? '✓ Copied' : 'Copy failed';
      setTimeout(() => b.textContent = old, 1400);
    };
    if (navigator.clipboard?.writeText) navigator.clipboard.writeText(text).then(() => done(true)).catch(() => fallbackCopy(text, done));
    else fallbackCopy(text, done);
  }

  function fallbackCopy(text, done) {
    const ta = document.createElement('textarea');
    ta.value = text; ta.style.position='fixed'; ta.style.opacity='0';
    document.body.appendChild(ta); ta.select();
    let ok = false; try { ok = document.execCommand('copy'); } catch (_) {}
    ta.remove(); done(ok);
  }

  function enhance() {
    const body = $('logBody');
    const table = body?.closest('table');
    if (!body || !table) return false;
    const head = table.querySelector('thead tr');
    if (head && !head.querySelector('[data-v64-detail-head]')) {
      const th = document.createElement('th');
      th.dataset.v64DetailHead = '1'; th.textContent = 'Details'; head.appendChild(th);
    }
    body.querySelectorAll('tr').forEach(row => {
      if (row.dataset.v64Log === '1') return;
      if (row.children.length < 3) return;
      row.dataset.v64Log = '1';
      const message = row.children[2].textContent;
      const detail = document.createElement('td');
      detail.className = 'v64-log-detail';
      detail.innerHTML = `<details><summary>View details</summary><div>${esc(eventDetail(message)).replace(/\n/g,'<br>')}</div></details>`;
      row.appendChild(detail);
    });
    return true;
  }

  function install() {
    const panel = $('panel-logs');
    if (!panel || panel.dataset.v64LogsReady === '1') return !!panel;
    panel.dataset.v64LogsReady = '1';
    const hero = panel.querySelector('.heroLine');
    const clear = $('clearLogs');
    if (hero && clear && !$('copyLogs')) {
      const copy = document.createElement('button');
      copy.id='copyLogs'; copy.className='btn'; copy.textContent='⧉ Copy Log';
      copy.title='Copy the complete simulation log with event details';
      copy.onclick=copyText;
      hero.insertBefore(copy, clear);
    }
    const style=document.createElement('style');
    style.textContent=`
      #panel-logs .heroLine{gap:8px;flex-wrap:wrap}
      #panel-logs .heroLine button{min-width:108px}
      #panel-logs .logTable{table-layout:auto}
      #panel-logs .logTable th:nth-child(1){width:92px}
      #panel-logs .logTable th:nth-child(2){width:70px}
      #panel-logs .logTable th:nth-child(3){min-width:280px}
      #panel-logs .v64-log-detail{min-width:330px;white-space:normal!important;text-align:left!important;color:#9bb0c3;vertical-align:top}
      #panel-logs .v64-log-detail details{border:1px solid #123957;background:#041522;border-radius:5px;padding:5px 7px}
      #panel-logs .v64-log-detail summary{cursor:pointer;color:#16a9ff;font-weight:700;font-size:10px}
      #panel-logs .v64-log-detail details div{margin-top:6px;line-height:1.5;font-size:10px;color:#a9bdd0}
      #panel-logs .logTable tr[data-v64-log] td{vertical-align:top}
      @media(max-width:900px){#panel-logs .v64-log-detail{min-width:220px}}
    `;
    document.head.appendChild(style);
    const observer=new MutationObserver(enhance);
    observer.observe(body,{childList:true,subtree:true});
    enhance();
    return true;
  }

  let attempts=0;
  const timer=setInterval(()=>{if(install()||++attempts>100)clearInterval(timer)},100);
})();
