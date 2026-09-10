(() => {
  const apiVersions = new Set(Array.from({length:12}, (_,i) => `V${19+i}`));
  const value = id => document.getElementById(id)?.value ?? '';
  const query = (version) => {
    const p = new URLSearchParams({
      version,
      snr: value('snr') || '15',
      prbs: value('prb') || '52',
      mcs: value('mcs') || '16',
      layers: value('layers') || '2',
      ue: value('ue') || '4'
    });
    switch (version) {
      case 'V19': p.set('ack','true'); p.set('sr','false'); p.set('csi','12'); break;
      case 'V20': p.set('payloadBits','12000'); p.set('rv','0'); break;
      case 'V21': break;
      case 'V22': p.set('cqi','12'); p.set('ueSinr', value('snr') || '15'); break;
      case 'V23': p.set('frames','1'); p.set('slots','20'); p.set('dlRatio','0.7'); break;
      case 'V24': p.set('payloadBytes','1400'); p.set('mtu','300'); p.set('mode','AM'); break;
      case 'V25': p.set('payloadBytes','1400'); p.set('snBits','12'); break;
      case 'V26': break;
      case 'V27': p.set('x','20'); p.set('y','0'); p.set('offset','3'); break;
      case 'V28': p.set('azimuth','10'); p.set('beams','16'); break;
      case 'V29': p.set('pattern','DDDDDDUUUU'); p.set('slotsPerFrame','20'); break;
      case 'V30': p.set('throughput','100'); p.set('goodput','90'); p.set('ber','0.02'); p.set('latency','5'); p.set('totalPrbs','106'); p.set('retransmissions','3'); break;
    }
    return p;
  };
  const run = async (card, version) => {
    const button = card.querySelector('.one');
    const status = card.querySelector('.status');
    const result = card.querySelector('.result');
    button.disabled = true;
    status.textContent = 'RUNNING'; status.className = 'status ready';
    try {
      const r = await fetch('/api/lab?' + query(version).toString(), {cache:'no-store'});
      const d = await r.json();
      if (!r.ok || !d.ok) throw Error(d.error || `HTTP ${r.status}`);
      result.textContent = `${d.version}: ${d.result}`;
      result.style.display = 'block'; card.classList.add('open');
      status.textContent = 'PASS'; status.className = 'status pass';
    } catch (e) {
      result.textContent = e.message || String(e); result.style.display = 'block'; card.classList.add('open');
      status.textContent = 'FAIL'; status.className = 'status fail';
    } finally { button.disabled = false; }
  };
  const cards = () => Array.from(document.querySelectorAll('.test'));
  const versionFor = card => (card.querySelector('.testtop b')?.textContent || '').match(/^V\d+/)?.[0];
  const bind = () => cards().forEach(card => {
    const v = versionFor(card);
    if (!apiVersions.has(v)) return;
    const b = card.querySelector('.one');
    if (!b || b.dataset.parameterizedBound) return;
    b.dataset.parameterizedBound = '1';
    b.addEventListener('click', e => { e.stopImmediatePropagation(); run(card, v); }, true);
    card.querySelector('.explain').insertAdjacentHTML('afterbegin','<span class="muted"><b>Mode:</b> parameterized live adapter · uses the selected radio context</span><br>');
  });
  const observe = new MutationObserver(bind);
  window.addEventListener('DOMContentLoaded', () => { bind(); observe.observe(document.getElementById('groups'), {childList:true,subtree:true}); });
})();
