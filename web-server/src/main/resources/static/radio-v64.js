// V64 additive compatibility and presentation adapter.
// Existing V20/V21/V28/V63 backend contracts and radio-v64-fixed.js remain intact.
(() => {
  'use strict';

  const nativeFetch = window.fetch.bind(window);
  const esc = value => String(value ?? '').replace(/[&<>\"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','\"':'&quot;'}[c]));
  const field = (text, key, fallback = '—') => {
    const m = String(text || '').match(new RegExp(`${key}=([^,\\n\\r)]*)`));
    return m ? m[1].trim() : fallback;
  };
  const boolValue = value => String(value).trim().toLowerCase() === 'true';
  const passText = value => boolValue(value) ? 'PASS' : 'FAIL';
  const passClass = value => boolValue(value) ? 'phy-good' : 'phy-bad';

  window.fetch = async (input, init) => {
    let requestInput = input;
    try {
      const originalUrl = typeof input === 'string' ? input : input?.url || '';
      if (originalUrl.includes('/api/lab') && window.__v64MimoState) {
        const u = new URL(originalUrl, window.location.href);
        const version = u.searchParams.get('version');
        const m = window.__v64MimoState;
        if (version === 'V21' || version === 'V28') {
          const rank = m.rankAdaptive ? Math.min(m.tx, m.rx) : m.rank;
          u.searchParams.set('layers', String(Math.max(1, Math.min(4, rank))));
          u.searchParams.set('tx', String(m.tx));
          u.searchParams.set('rx', String(m.rx));
          if (version === 'V28') u.searchParams.set('beams', String(m.beamSweep ? m.beams : 1));
          requestInput = u.toString();
        }
      }
    } catch (_) {}

    const response = await nativeFetch(requestInput, init);
    const url = typeof requestInput === 'string' ? requestInput : requestInput?.url || '';
    if (!url.includes('/api/')) return response;
    const clone = response.clone();
    const text = await clone.text();
    try {
      const payload = JSON.parse(text);
      if (url.includes('/api/lab') && payload && Array.isArray(payload.ueStates)) {
        payload.ueStates = payload.ueStates.map(ue => ({
          ...ue,
          meanSinrDb: ue.meanSinrDb ?? ue.sinrDb,
          meanCqi: ue.meanCqi ?? ue.cqi,
          meanMcs: ue.meanMcs ?? ue.mcs,
          totalAllocatedPrbs: ue.totalAllocatedPrbs ?? ue.allocatedPrbs,
          meanBler: ue.meanBler ?? ue.bler
        }));
        window.__v64LastData = payload;
        return new Response(JSON.stringify(payload), {
          status: response.status,
          statusText: response.statusText,
          headers: {'Content-Type':'application/json'}
        });
      }
    } catch (_) {
      const message = text.replace(/\s+/g,' ').trim().slice(0,300) || 'empty response';
      return new Response(JSON.stringify({
        ok:false,
        error:`Backend returned non-JSON (${response.status} ${response.statusText}): ${message}`
      }), {
        status: response.status,
        statusText: response.statusText,
        headers: {'Content-Type':'application/json'}
      });
    }
    return response;
  };

  if (!document.getElementById('activeUes')) {
    const activeUes = document.createElement('span');
    activeUes.id = 'activeUes';
    activeUes.hidden = true;
    document.body.appendChild(activeUes);
  }

  function installBaseStyles() {
    const style = document.createElement('style');
    style.textContent = `
      .mimo-selectable{cursor:pointer!important;user-select:none;transition:.15s;display:inline-flex;align-items:center;gap:5px}
      .mimo-selectable:hover{border-color:#159cff!important;color:#e8f7ff!important;transform:translateY(-1px)}
      .mimo-selectable.active{border-color:#00df86!important;background:#063b2a!important;color:#6effbc!important;box-shadow:0 0 10px #00df8633}
      .mimo-control-hint{margin-top:9px;color:#7891a8;font-size:10px;line-height:1.4}
      .mimo-result-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}
      .mimo-result-card{background:#071c30;border:1px solid #15415f;border-radius:6px;padding:12px}
      .mimo-result-head{display:flex;justify-content:space-between;align-items:flex-start;gap:10px;margin-bottom:8px}
      .mimo-result-head strong{display:block;font-size:13px;color:#e8f4ff}
      .mimo-result-head small{display:block;color:#7891a8;font-size:10px;margin-top:3px}
      .mimo-status{font-size:10px;font-weight:800;white-space:nowrap}
      .mimo-good{color:#00e994}.mimo-bad{color:#ff5264}
      .mimo-metric{display:flex;justify-content:space-between;gap:12px;border-bottom:1px solid #0d2b45;padding:6px 0;font-size:11px}
      .mimo-metric span{color:#9bb0c3}.mimo-metric b{color:#e7f5ff}
      .mimo-note{margin-top:9px;padding:8px;border-left:2px solid #159cff;background:#041522;color:#7f9ab1;font-size:9px;line-height:1.4}
      @media(max-width:700px){.mimo-result-grid{grid-template-columns:1fr}}
    `;
    document.head.appendChild(style);
  }

  function installMimoControls() {
    const row = document.querySelector('#panel-mimo .pillRow');
    if (!row || row.dataset.mimoReady === '1') return !!row;
    row.dataset.mimoReady = '1';
    window.__v64MimoState = {tx:4,rx:4,rankAdaptive:true,rank:2,beamSweep:true,beams:16};
    const controls = [
      {key:'tx', values:[1,2,3,4]},
      {key:'rx', values:[1,2,3,4]},
      {key:'rank', values:['adaptive',1,2,3,4]},
      {key:'beamSweep', values:[true,false]}
    ];
    row.innerHTML = '';
    controls.forEach(spec => {
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'pill mimo-selectable';
      button.dataset.mimoKey = spec.key;
      button.onclick = () => {
        const m = window.__v64MimoState;
        if (spec.key === 'rank') {
          const current = m.rankAdaptive ? 'adaptive' : m.rank;
          const next = spec.values[(spec.values.indexOf(current)+1)%spec.values.length];
          m.rankAdaptive = next === 'adaptive';
          if (!m.rankAdaptive) m.rank = Number(next);
        } else {
          const current = m[spec.key];
          m[spec.key] = spec.values[(spec.values.indexOf(current)+1)%spec.values.length];
        }
        updateMimoControls();
        logMimoSelection();
      };
      row.appendChild(button);
    });
    const hint = document.createElement('div');
    hint.id = 'mimoControlHint';
    hint.className = 'mimo-control-hint';
    row.parentElement.appendChild(hint);
    updateMimoControls();
    return true;
  }

  function updateMimoControls() {
    const m = window.__v64MimoState;
    if (!m) return;
    document.querySelectorAll('[data-mimo-key]').forEach(button => {
      const key = button.dataset.mimoKey;
      button.textContent = key === 'tx' ? `${m.tx} TX antennas` :
        key === 'rx' ? `${m.rx} RX antennas` :
        key === 'rank' ? (m.rankAdaptive ? 'Rank adaptation' : `Rank ${m.rank}`) :
        (m.beamSweep ? `Beam sweep: ON (${m.beams})` : 'Beam sweep: OFF');
      button.classList.toggle('active', key === 'rank' ? m.rankAdaptive : key === 'beamSweep' ? m.beamSweep : true);
      button.title = 'Click to change this parameter';
    });
    const hint = document.getElementById('mimoControlHint');
    if (hint) hint.textContent = `Active: ${m.tx}×${m.rx} antenna array · ${m.rankAdaptive ? 'adaptive rank' : `fixed rank ${m.rank}`} · ${m.beamSweep ? `${m.beams}-beam sweep` : 'single-beam probe'}. Click a control to change it.`;
  }

  function logMimoSelection() {
    const m = window.__v64MimoState;
    if (typeof window.__v64MimoLog === 'function') window.__v64MimoLog(m);
  }

  function renderMimoResult(raw) {
    const target = document.getElementById('mimoResult');
    if (!target || !raw || raw === 'Running…') return false;
    if (!raw.includes('NrSrsV21Result') || !raw.includes('NrBeamV28Result')) return false;
    const parts = raw.split(/\n\s*Beam sweep:\s*/i);
    const srs = parts[0].replace(/^.*?NrSrsV21Result\(/s,'');
    const beam = (parts[1] || '').replace(/^NrBeamV28Result\(/s,'');
    const rsrp=field(srs,'rsrpDb'),sinr=field(srs,'sinrDb'),rank=field(srs,'rank'),port=field(srs,'preferredPort'),sp=field(srs,'pass'),sn=field(srs,'note');
    const beamId=field(beam,'beam'),gain=field(beam,'gainDb'),beamRank=field(beam,'rank'),scanned=field(beam,'beamsScanned'),bp=field(beam,'pass'),bn=field(beam,'note');
    const row=(label,value,cls='')=>`<div class="mimo-metric"><span>${esc(label)}</span><b class="${cls}">${esc(value)}</b></div>`;
    target.innerHTML=`<div class="mimo-result-grid"><section class="mimo-result-card"><div class="mimo-result-head"><div><strong>SRS / Channel Probe</strong><small>V21 sounding/channel abstraction</small></div><span class="mimo-status ${boolValue(sp)?'mimo-good':'mimo-bad'}">● ${passText(sp)}</span></div>${row('RSRP',rsrp==='—'?'—':`${rsrp} dB`)}${row('SINR',sinr==='—'?'—':`${sinr} dB`)}${row('Estimated Rank',rank)}${row('Preferred Port',port)}${row('SRS Check',passText(sp),boolValue(sp)?'mimo-good':'mimo-bad')}${sn!=='—'?`<div class="mimo-note">${esc(sn)}</div>`:''}</section><section class="mimo-result-card"><div class="mimo-result-head"><div><strong>Beam Sweep</strong><small>V28 codebook / array-gain reference</small></div><span class="mimo-status ${boolValue(bp)?'mimo-good':'mimo-bad'}">● ${passText(bp)}</span></div>${row('Selected Beam',beamId)}${row('Array Gain',gain==='—'?'—':`${gain} dB`)}${row('Selected Rank',beamRank)}${row('Beams Scanned',scanned)}${row('Beam Check',passText(bp),boolValue(bp)?'mimo-good':'mimo-bad')}${bn!=='—'?`<div class="mimo-note">${esc(bn)}</div>`:''}</section></div>`;
    return true;
  }

  function installMimoPresentation() {
    let attempts=0;
    const timer=setInterval(()=>{if(installMimoControls()||++attempts>100)clearInterval(timer)},100);
    const observerTimer=setInterval(()=>{const target=document.getElementById('mimoResult');if(target){clearInterval(observerTimer);const observer=new MutationObserver(()=>renderMimoResult(target.textContent||''));observer.observe(target,{childList:true,subtree:true,characterData:true})}},100);
  }

  function renderPhyResult(raw) {
    const target=document.getElementById('phyResult');
    if(!target||!raw||raw==='Executing…'||!raw.includes('NrPuschV20Result')||!raw.includes('NrSrsV21Result'))return false;
    const parts=raw.split(/\n\s*SRS\s*\/\s*channel probe:\s*/i),p=parts[0].replace(/^.*?NrPuschV20Result\(/s,''),s=(parts[1]||'').replace(/^NrSrsV21Result\(/s,'');
    const row=(label,value,cls='')=>`<div class="phy-metric"><span>${esc(label)}</span><b class="${cls}">${esc(value)}</b></div>`;
    const tb=field(p,'tbBits'),enc=field(p,'encodedBits'),sym=field(p,'symbols'),layers=field(p,'layers'),rv=field(p,'rv'),crc=field(p,'crcOk'),pp=field(p,'pass'),pn=field(p,'note');
    const rsrp=field(s,'rsrpDb'),sinr=field(s,'sinrDb'),rank=field(s,'rank'),port=field(s,'preferredPort'),sp=field(s,'pass'),sn=field(s,'note');
    target.innerHTML=`<div class="phy-result-grid"><section class="phy-result-card"><div class="phy-result-head"><div><strong>PUSCH / UL-SCH</strong><small>V20 uplink transport path</small></div><span class="phy-status ${passClass(pp)}">● ${passText(pp)}</span></div>${row('Transport Block',tb==='—'?'—':`${tb} bits`)}${row('Encoded Bits',enc==='—'?'—':`${enc} bits`)}${row('Modulation Symbols',sym)}${row('MIMO Layers',layers)}${row('Redundancy Version',rv)}${row('CRC',passText(crc),passClass(crc))}${row('PHY Check',passText(pp),passClass(pp))}${pn!=='—'?`<div class="phy-note">${esc(pn)}</div>`:''}</section><section class="phy-result-card"><div class="phy-result-head"><div><strong>SRS / Channel Probe</strong><small>V21 sounding/channel abstraction</small></div><span class="phy-status ${passClass(sp)}">● ${passText(sp)}</span></div>${row('RSRP',rsrp==='—'?'—':`${rsrp} dB`)}${row('SINR',sinr==='—'?'—':`${sinr} dB`)}${row('Estimated Rank',rank)}${row('Preferred Port',port)}${row('SRS Check',passText(sp),passClass(sp))}${sn!=='—'?`<div class="phy-note">${esc(sn)}</div>`:''}</section></div><div class="phy-implementation-note"><b>Implementation status:</b> Reference/educational PHY path. This presentation layer does not replace or alter the existing V20/V21 implementation.</div>`;
    return true;
  }

  function installPhyLayoutAndPresentation() {
    const style=document.createElement('style');
    style.textContent=`
      .phy-result-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}.phy-result-card{background:#071c30;border:1px solid #15415f;border-radius:6px;padding:12px}.phy-result-head{display:flex;justify-content:space-between;align-items:flex-start;gap:10px;margin-bottom:8px}.phy-result-head strong{display:block;font-size:13px;color:#e8f4ff}.phy-result-head small{display:block;color:#7891a8;font-size:10px;margin-top:3px}.phy-status{font-size:10px;font-weight:800;white-space:nowrap}.phy-good{color:#00e994}.phy-bad{color:#ff5264}.phy-metric{display:flex;justify-content:space-between;gap:12px;border-bottom:1px solid #0d2b45;padding:6px 0;font-size:11px}.phy-metric span{color:#9bb0c3}.phy-metric b{color:#e7f5ff}.phy-note{margin-top:9px;padding:8px;border-left:2px solid #159cff;background:#041522;color:#7f9ab1;font-size:9px;line-height:1.4}.phy-implementation-note{margin-top:10px;padding:9px 11px;border:1px solid #123554;border-radius:6px;background:#041522;color:#7891a8;font-size:10px;line-height:1.45}
      #panel-phy>.panelGrid{display:flex;flex-direction:column;gap:10px}#panel-phy .phy-experiment-card{order:1;padding:14px}#panel-phy .phy-chain-card{order:2;padding:14px}#panel-phy .phy-control-row{display:grid;grid-template-columns:repeat(4,minmax(130px,1fr)) minmax(190px,1.35fr);gap:10px;align-items:end;margin-top:12px}#panel-phy .phy-control-row .field{margin-top:0}#panel-phy .phy-control-row .actions{margin-top:0;display:block}#panel-phy .phy-control-row .actions .btn{width:100%;height:34px;padding:8px 12px}#panel-phy .phy-experiment-card>.output{margin-top:10px;min-height:0;padding:0;border:0;background:transparent;overflow:visible}#panel-phy .phy-chain-card .pipeline{grid-template-columns:repeat(6,minmax(0,1fr));gap:9px;margin-top:12px}#panel-phy .phy-chain-card .stage{min-height:78px;padding:11px}#panel-phy .phy-chain-card .stage::after{display:block}#panel-phy .phy-chain-card .stage:nth-child(6n)::after{display:none}@media(max-width:1100px){#panel-phy .phy-control-row{grid-template-columns:repeat(4,minmax(110px,1fr))}#panel-phy .phy-control-row .actions{grid-column:1/-1}#panel-phy .phy-chain-card .pipeline{grid-template-columns:repeat(3,minmax(0,1fr))}#panel-phy .phy-chain-card .stage:nth-child(3n)::after{display:none}}@media(max-width:700px){.phy-result-grid{grid-template-columns:1fr}#panel-phy .phy-control-row{grid-template-columns:1fr 1fr}#panel-phy .phy-control-row .actions{grid-column:1/-1}#panel-phy .phy-chain-card .pipeline{grid-template-columns:1fr 1fr}#panel-phy .phy-chain-card .stage::after{display:none}}
    `;
    document.head.appendChild(style);
    const apply=()=>{
      const panel=document.getElementById('panel-phy'),grid=panel?.querySelector('.panelGrid');
      if(!panel||!grid)return false;
      const chain=grid.querySelector('.pipeline')?.closest('.bigCard'),experiment=grid.querySelector('#phySnr')?.closest('.bigCard');
      if(!chain||!experiment)return false;
      chain.classList.add('phy-chain-card');experiment.classList.add('phy-experiment-card');
      if(grid.firstElementChild!==experiment)grid.insertBefore(experiment,grid.firstElementChild);
      if(!experiment.querySelector('.phy-control-row')){
        const row=document.createElement('div');row.className='phy-control-row';
        const title=experiment.querySelector('.title'),fields=Array.from(experiment.querySelectorAll(':scope > .field')),actions=experiment.querySelector(':scope > .actions');
        fields.forEach(el=>row.appendChild(el));if(actions)row.appendChild(actions);if(title)title.after(row);
      }
      return true;
    };
    let attempts=0;const timer=setInterval(()=>{if(apply()||++attempts>100)clearInterval(timer)},100);
    let phyAttempts=0;const pTimer=setInterval(()=>{const target=document.getElementById('phyResult');if(target&&renderPhyResult(target.textContent||'')){clearInterval(pTimer);const observer=new MutationObserver(()=>renderPhyResult(target.textContent||''));observer.observe(target,{childList:true,subtree:true,characterData:true})}if(++phyAttempts>100)clearInterval(pTimer)},100);
  }

  function loadDependentScripts() {
    const load = src => {
      const s = document.createElement('script');
      s.src = src;
      s.defer = false;
      s.async = false;
      document.body.appendChild(s);
    };
    load('/radio-v64-fixed.js');
    load('/radio-v64-plots.js');
    load('/radio-v64-mimo-plots.js');
    load('/radio-v64-scheduler.js');
    load('/radio-v64-topology.js');\nload('/radio-v64-settings.js');
    load('/radio-v64-performance.js');
    load('/radio-v64-velocity.js');
  }

  function init() {
    installBaseStyles();
    installMimoPresentation();
    installPhyLayoutAndPresentation();
    loadDependentScripts();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init, {once:true});
  } else {
    init();
  }
})();
