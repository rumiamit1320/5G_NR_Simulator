// V64 compatibility entry point. The functional HMI controller lives in the additive fixed module.
// Existing backend/controller contracts remain unchanged.
(() => {
  const nativeFetch = window.fetch.bind(window);
  window.fetch = async (input, init) => {
    const response = await nativeFetch(input, init);
    const url = typeof input === 'string' ? input : input?.url || '';
    if (!url.includes('/api/')) return response;
    const clone = response.clone();
    const text = await clone.text();
    try {
      const payload = JSON.parse(text);
      // Additive V63 -> V64 display-schema compatibility.
      if (url.includes('/api/lab') && payload && Array.isArray(payload.ueStates)) {
        payload.ueStates = payload.ueStates.map(ue => ({
          ...ue,
          meanSinrDb: ue.meanSinrDb ?? ue.sinrDb,
          meanCqi: ue.meanCqi ?? ue.cqi,
          meanMcs: ue.meanMcs ?? ue.mcs,
          totalAllocatedPrbs: ue.totalAllocatedPrbs ?? ue.allocatedPrbs,
          meanBler: ue.meanBler ?? ue.bler
        }));
        return new Response(JSON.stringify(payload), {
          status: response.status,
          statusText: response.statusText,
          headers: { 'Content-Type': 'application/json' }
        });
      }
    } catch (_) {
      const message = text.replace(/\s+/g, ' ').trim().slice(0, 300) || 'empty response';
      return new Response(JSON.stringify({
        ok: false,
        error: `Backend returned non-JSON (${response.status} ${response.statusText}): ${message}`
      }), {
        status: response.status,
        statusText: response.statusText,
        headers: { 'Content-Type': 'application/json' }
      });
    }
    return response;
  };

  // Compatibility target retained by radio-v64-fixed.js.
  if (!document.getElementById('activeUes')) {
    const activeUes = document.createElement('span');
    activeUes.id = 'activeUes';
    activeUes.hidden = true;
    document.body.appendChild(activeUes);
  }

  // ---------------------------------------------------------------------------
  // Additive PHY presentation layer.
  // V20/V21 continue to execute exactly as before. The retained controller writes
  // their Kotlin data-class strings into #phyResult; this adapter parses those
  // strings without changing the underlying PHY implementation.
  // ---------------------------------------------------------------------------
  const esc = value => String(value ?? '').replace(/[&<>\"]/g, c => ({
    '&':'&amp;', '<':'&lt;', '>':'&gt;', '\"':'&quot;'
  }[c]));

  const field = (text, key, fallback = '—') => {
    const match = String(text || '').match(new RegExp(`(?:^|[,(\\s])${key}=([^,\\n\\r)]*)`));
    return match ? match[1].trim() : fallback;
  };

  const status = value => String(value).trim().toLowerCase() === 'true';
  const statusText = value => status(value) ? 'PASS' : 'FAIL';
  const statusClass = value => status(value) ? 'phy-good' : 'phy-bad';
  const row = (label, value, cls = '') =>
    `<div class="phy-metric"><span>${esc(label)}</span><b class="${cls}">${esc(value)}</b></div>`;

  function renderPhyResult(raw) {
    const target = document.getElementById('phyResult');
    if (!target || !raw || raw === 'Executing…') return false;
    if (!raw.includes('NrPuschV20Result') || !raw.includes('NrSrsV21Result')) return false;

    // Split at the known controller delimiter instead of relying on parentheses.
    // This is robust to the parentheses contained inside the V20 note text.
    const parts = raw.split(/\n\s*SRS\s*\/\s*channel probe:\s*/i);
    const puschText = parts[0].replace(/^.*?NrPuschV20Result\(/s, '');
    const srsText = (parts[1] || '').replace(/^NrSrsV21Result\(/s, '');

    const tbBits = field(puschText, 'tbBits');
    const encodedBits = field(puschText, 'encodedBits');
    const symbols = field(puschText, 'symbols');
    const layers = field(puschText, 'layers');
    const rv = field(puschText, 'rv');
    const crcOk = field(puschText, 'crcOk');
    const puschPass = field(puschText, 'pass');
    const puschNote = field(puschText, 'note');

    const rsrp = field(srsText, 'rsrpDb');
    const sinr = field(srsText, 'sinrDb');
    const rank = field(srsText, 'rank');
    const preferredPort = field(srsText, 'preferredPort');
    const srsPass = field(srsText, 'pass');
    const srsNote = field(srsText, 'note');

    target.innerHTML = `
      <div class="phy-result-grid">
        <section class="phy-result-card">
          <div class="phy-result-head">
            <div><strong>PUSCH / UL-SCH</strong><small>V20 uplink transport path</small></div>
            <span class="phy-status ${statusClass(puschPass)}">● ${statusText(puschPass)}</span>
          </div>
          ${row('Transport Block', tbBits === '—' ? '—' : `${tbBits} bits`)}
          ${row('Encoded Bits', encodedBits === '—' ? '—' : `${encodedBits} bits`)}
          ${row('Modulation Symbols', symbols)}
          ${row('MIMO Layers', layers)}
          ${row('Redundancy Version', rv)}
          ${row('CRC', statusText(crcOk), statusClass(crcOk))}
          ${row('PHY Check', statusText(puschPass), statusClass(puschPass))}
          ${puschNote !== '—' ? `<div class="phy-note">${esc(puschNote)}</div>` : ''}
        </section>
        <section class="phy-result-card">
          <div class="phy-result-head">
            <div><strong>SRS / Channel Probe</strong><small>V21 sounding/channel abstraction</small></div>
            <span class="phy-status ${statusClass(srsPass)}">● ${statusText(srsPass)}</span>
          </div>
          ${row('RSRP', rsrp === '—' ? '—' : `${rsrp} dB`)}
          ${row('SINR', sinr === '—' ? '—' : `${sinr} dB`)}
          ${row('Estimated Rank', rank)}
          ${row('Preferred Port', preferredPort)}
          ${row('SRS Check', statusText(srsPass), statusClass(srsPass))}
          ${srsNote !== '—' ? `<div class="phy-note">${esc(srsNote)}</div>` : ''}
        </section>
      </div>
      <div class="phy-implementation-note"><b>Implementation status:</b> Reference/educational PHY path. This presentation layer does not replace or alter the existing V20/V21 implementation.</div>`;
    return true;
  }

  // ---------------------------------------------------------------------------
  // Additive PHY layout adapter.
  // Only rearranges existing DOM nodes/CSS on the V64 PHY tab. No controller,
  // backend API, V20/V21, V61, V62 or V63 logic is changed.
  // ---------------------------------------------------------------------------
  function installPhyLayout() {
    const apply = () => {
      const panel = document.getElementById('panel-phy');
      const grid = panel?.querySelector('.panelGrid');
      if (!panel || !grid) return false;

      const chain = grid.querySelector('.pipeline')?.closest('.bigCard');
      const experiment = grid.querySelector('#phySnr')?.closest('.bigCard');
      if (!chain || !experiment) return false;

      chain.classList.add('phy-chain-card');
      experiment.classList.add('phy-experiment-card');

      // Put the PHY controls/results first and the reference chain underneath.
      if (grid.firstElementChild !== experiment) grid.insertBefore(experiment, grid.firstElementChild);

      // Compact the existing four controls plus the Run button into one row.
      if (!experiment.querySelector('.phy-control-row')) {
        const row = document.createElement('div');
        row.className = 'phy-control-row';
        const title = experiment.querySelector('.title');
        const fields = Array.from(experiment.querySelectorAll(':scope > .field'));
        const actions = experiment.querySelector(':scope > .actions');
        fields.forEach(el => row.appendChild(el));
        if (actions) row.appendChild(actions);
        if (title) title.after(row);
      }
      return true;
    };

    const style = document.createElement('style');
    style.textContent = `
      /* V64 PHY: maximize useful viewport area while preserving all existing nodes. */
      #panel-phy > .panelGrid{display:flex;flex-direction:column;gap:10px}
      #panel-phy .phy-experiment-card{order:1;padding:14px}
      #panel-phy .phy-chain-card{order:2;padding:14px}
      #panel-phy .phy-control-row{display:grid;grid-template-columns:repeat(4,minmax(130px,1fr)) minmax(190px,1.35fr);gap:10px;align-items:end;margin-top:12px}
      #panel-phy .phy-control-row .field{margin-top:0}
      #panel-phy .phy-control-row .actions{margin-top:0;display:block}
      #panel-phy .phy-control-row .actions .btn{width:100%;height:34px;padding:8px 12px}
      #panel-phy .phy-experiment-card > .output{margin-top:10px;min-height:0;padding:0;border:0;background:transparent;overflow:visible}
      #panel-phy .phy-result-grid{grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}
      #panel-phy .phy-result-card{padding:13px}
      #panel-phy .phy-metric{padding:7px 0}
      #panel-phy .phy-implementation-note{margin-top:10px}
      #panel-phy .phy-chain-card .pipeline{grid-template-columns:repeat(6,minmax(0,1fr));gap:9px;margin-top:12px}
      #panel-phy .phy-chain-card .stage{min-height:78px;padding:11px}
      #panel-phy .phy-chain-card .stage::after{display:block}
      #panel-phy .phy-chain-card .stage:nth-child(6n)::after{display:none}
      @media(max-width:1100px){
        #panel-phy .phy-control-row{grid-template-columns:repeat(4,minmax(110px,1fr))}
        #panel-phy .phy-control-row .actions{grid-column:1/-1}
        #panel-phy .phy-chain-card .pipeline{grid-template-columns:repeat(3,minmax(0,1fr))}
        #panel-phy .phy-chain-card .stage::after{display:block}
        #panel-phy .phy-chain-card .stage:nth-child(3n)::after{display:none}
      }
      @media(max-width:700px){
        #panel-phy .phy-control-row{grid-template-columns:1fr 1fr}
        #panel-phy .phy-control-row .actions{grid-column:1/-1}
        #panel-phy .phy-chain-card .pipeline{grid-template-columns:1fr 1fr}
        #panel-phy .phy-chain-card .stage::after{display:none}
        #panel-phy .phy-result-grid{grid-template-columns:1fr}
      }
    `;
    document.head.appendChild(style);

    if (apply()) return;
    let attempts = 0;
    const timer = setInterval(() => {
      if (apply() || ++attempts > 100) clearInterval(timer);
    }, 100);
  }

  function installPhyPresentation() {
    const style = document.createElement('style');
    style.textContent = `
      .phy-result-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}
      .phy-result-card{background:#071c30;border:1px solid #15415f;border-radius:6px;padding:12px}
      .phy-result-head{display:flex;justify-content:space-between;align-items:flex-start;gap:10px;margin-bottom:8px}
      .phy-result-head strong{display:block;font-size:13px;color:#e8f4ff}
      .phy-result-head small{display:block;color:#7891a8;font-size:10px;margin-top:3px}
      .phy-status{font-size:10px;font-weight:800;white-space:nowrap}
      .phy-good{color:#00e994}.phy-bad{color:#ff5264}
      .phy-metric{display:flex;justify-content:space-between;gap:12px;border-bottom:1px solid #0d2b45;padding:6px 0;font-size:11px}
      .phy-metric span{color:#9bb0c3}.phy-metric b{color:#e7f5ff}
      .phy-note{margin-top:9px;padding:8px;border-left:2px solid #159cff;background:#041522;color:#7f9ab1;font-size:9px;line-height:1.4}
      .phy-implementation-note{margin-top:10px;padding:9px 11px;border:1px solid #123554;border-radius:6px;background:#041522;color:#7891a8;font-size:10px;line-height:1.45}
      .phy-implementation-note b{color:#a9c3d8}
      @media(max-width:700px){.phy-result-grid{grid-template-columns:1fr}}
    `;
    document.head.appendChild(style);

    // Poll briefly because the retained controller and this additive adapter are
    // loaded independently. This avoids racing the controller's PHY result write.
    let attempts = 0;
    const timer = setInterval(() => {
      const target = document.getElementById('phyResult');
      if (target && renderPhyResult(target.textContent || '')) {
        clearInterval(timer);
        // Re-arm after each future PHY check so repeated checks are also formatted.
        const observer = new MutationObserver(() => renderPhyResult(target.textContent || ''));
        observer.observe(target, { childList:true, subtree:true, characterData:true });
      }
      if (++attempts > 100) clearInterval(timer);
    }, 100);
  }

  installPhyLayout();
  installPhyPresentation();

  const s = document.createElement('script');
  s.src = '/radio-v64-fixed.js';
  s.defer = false;
  document.head.appendChild(s);
})();
