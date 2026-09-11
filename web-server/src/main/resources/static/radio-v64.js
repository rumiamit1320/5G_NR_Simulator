// V64 compatibility entry point. The functional HMI controller lives in the additive fixed module.
// This wrapper also normalizes non-JSON backend responses so the HMI does not fail with
// "Unexpected token ... in JSON" when Vercel/proxy returns an HTML/text error page.
//
// V64 compatibility is intentionally additive: the existing fixed controller and backend
// contracts remain unchanged. V63 UE fields are mirrored to the legacy V64 display names,
// and the optional activeUes DOM target is created when the current HMI does not contain it.
(() => {
  const nativeFetch = window.fetch.bind(window);
  window.fetch = async (input, init) => {
    const response = await nativeFetch(input, init);
    const url = typeof input === 'string' ? input : input?.url || '';
    if (url.includes('/api/')) {
      const clone = response.clone();
      const text = await clone.text();
      try {
        const payload = JSON.parse(text);

        // Additive V63 -> V64 display-schema compatibility.
        // Preserve every original V63 field and only add the names expected by the
        // existing V64 renderer.
        if (url.includes('/api/lab') && payload && Array.isArray(payload.ueStates)) {
          payload.ueStates = payload.ueStates.map((ue) => ({
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
        return new Response(JSON.stringify({ ok: false, error: `Backend returned non-JSON (${response.status} ${response.statusText}): ${message}` }), {
          status: response.status,
          statusText: response.statusText,
          headers: { 'Content-Type': 'application/json' }
        });
      }
    }
    return response;
  };

  // The current V64 HMI does not render an active-UE counter, while the retained
  // controller still updates it. Provide a harmless compatibility target instead
  // of changing the existing controller or layout.
  if (!document.getElementById('activeUes')) {
    const activeUes = document.createElement('span');
    activeUes.id = 'activeUes';
    activeUes.hidden = true;
    document.body.appendChild(activeUes);
  }

  // ---------------------------------------------------------------------------
  // Additive PHY result presentation layer.
  // V20/V21 continue to return their existing result objects/strings unchanged.
  // This observer only reformats the retained controller's textual result into
  // engineering-style PUSCH and SRS metric cards after the PHY check completes.
  // ---------------------------------------------------------------------------
  const esc = (value) => String(value ?? '').replace(/[&<>\"]/g, c => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '\"': '&quot;'
  }[c]));

  const num = (text, key, fallback = '—') => {
    const m = String(text || '').match(new RegExp(`${key}=([^,\\)]*)`));
    return m ? m[1].trim() : fallback;
  };

  function renderPhyResult(raw) {
    const target = document.getElementById('phyResult');
    if (!target || !raw || raw === 'Executing…' || !/NrPuschV20Result|NrSrsV21Result/.test(raw)) return;

    const pusch = raw.match(/PUSCH\\s*\/\\s*PHY:\\s*NrPuschV20Result\\((.*?)(?:\\)\\s*|$)/s)?.[1] || '';
    const srs = raw.match(/SRS\\s*\/\\s*channel probe:\\s*NrSrsV21Result\\((.*?)(?:\\)\\s*|$)/s)?.[1] || '';

    const tbBits = num(pusch, 'tbBits');
    const encodedBits = num(pusch, 'encodedBits');
    const symbols = num(pusch, 'symbols');
    const layers = num(pusch, 'layers');
    const rv = num(pusch, 'rv');
    const crcOk = num(pusch, 'crcOk');
    const puschPass = num(pusch, 'pass');
    const puschNote = num(pusch, 'note');

    const rsrp = num(srs, 'rsrpDb');
    const sinr = num(srs, 'sinrDb');
    const rank = num(srs, 'rank');
    const preferredPort = num(srs, 'preferredPort');
    const srsPass = num(srs, 'pass');
    const srsNote = num(srs, 'note');

    const passClass = v => String(v).toLowerCase() === 'true' ? 'phy-good' : 'phy-bad';
    const statusText = (v) => String(v).toLowerCase() === 'true' ? 'PASS' : 'FAIL';
    const row = (label, value, cls = '') => `<div class="phy-metric"><span>${esc(label)}</span><b class="${cls}">${esc(value)}</b></div>`;

    target.innerHTML = `
      <div class="phy-result-grid">
        <section class="phy-result-card">
          <div class="phy-result-head">
            <div><strong>PUSCH / UL-SCH</strong><small>V20 uplink transport path</small></div>
            <span class="phy-status ${passClass(puschPass)}">● ${statusText(puschPass)}</span>
          </div>
          ${row('Transport Block', tbBits === '—' ? '—' : `${tbBits} bits`)}
          ${row('Encoded Bits', encodedBits === '—' ? '—' : `${encodedBits} bits`)}
          ${row('Modulation Symbols', symbols)}
          ${row('MIMO Layers', layers)}
          ${row('Redundancy Version', rv)}
          ${row('CRC', statusText(crcOk), passClass(crcOk))}
          ${row('PHY Check', statusText(puschPass), passClass(puschPass))}
          ${puschNote !== '—' ? `<div class="phy-note">${esc(puschNote)}</div>` : ''}
        </section>
        <section class="phy-result-card">
          <div class="phy-result-head">
            <div><strong>SRS / Channel Probe</strong><small>V21 sounding/channel abstraction</small></div>
            <span class="phy-status ${passClass(srsPass)}">● ${statusText(srsPass)}</span>
          </div>
          ${row('RSRP', rsrp === '—' ? '—' : `${rsrp} dB`)}
          ${row('SINR', sinr === '—' ? '—' : `${sinr} dB`)}
          ${row('Estimated Rank', rank)}
          ${row('Preferred Port', preferredPort)}
          ${row('SRS Check', statusText(srsPass), passClass(srsPass))}
          ${srsNote !== '—' ? `<div class="phy-note">${esc(srsNote)}</div>` : ''}
        </section>
      </div>
      <div class="phy-implementation-note"><b>Implementation status:</b> Reference/educational PHY path. PUSCH/UL-SCH transport abstraction and SRS channel-probe interfaces are active; full 3GPP 38.211 waveform/resource mapping is not replaced by this presentation layer.</div>`;
  }

  function installPhyPresentation() {
    const target = document.getElementById('phyResult');
    if (!target || target.dataset.phyPresentationInstalled) return;
    target.dataset.phyPresentationInstalled = 'true';
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

    const observer = new MutationObserver(() => {
      const raw = target.textContent || '';
      if (!target.dataset.phyRendering && /NrPuschV20Result|NrSrsV21Result/.test(raw)) {
        target.dataset.phyRendering = 'true';
        observer.disconnect();
        renderPhyResult(raw);
        delete target.dataset.phyRendering;
        observer.observe(target, { childList: true, subtree: true, characterData: true });
      }
    });
    observer.observe(target, { childList: true, subtree: true, characterData: true });
  }

  installPhyPresentation();

  const s = document.createElement('script');
  s.src = '/radio-v64-fixed.js';
  s.defer = false;
  document.head.appendChild(s);
})();
