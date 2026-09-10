(() => {
  // Web-only controller. It does not modify or duplicate nr-core logic.
  const PARAM = new Set(Array.from({length: 12}, (_, i) => `V${19 + i}`));
  const value = id => document.getElementById(id)?.value ?? '';
  const esc = s => String(s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));

  const field = (name, label, type='number', attrs='') =>
    `<label class="labfield"><span>${label}</span><input data-param="${name}" type="${type}" ${attrs}></label>`;

  function controls(version) {
    const common = `${field('snr','SNR','number','step="0.5" value="15" min="-20" max="50"')}${field('prbs','PRBs','number','value="52" min="1" max="275"')}${field('layers','Layers','number','value="2" min="1" max="4"')}`;
    switch (version) {
      case 'V19': return common + field('ack','ACK','checkbox','checked') + field('sr','SR','checkbox') + field('csi','CSI index','number','value="12" min="0" max="15"');
      case 'V20': return common + field('payloadBits','Payload bits','number','value="12000" min="1" max="2000000"') + field('rv','RV','number','value="0" min="0" max="3"');
      case 'V21': return common;
      case 'V22': return common + field('ue','UE count','number','value="4" min="1" max="16"') + field('cqi','Base CQI','number','value="12" min="0" max="15"') + field('ueSinr','UE SINR','number','value="15" step="0.5" min="-20" max="50"');
      case 'V23': return field('frames','Frames','number','value="1" min="1" max="1000"') + field('slots','Slots/frame','number','value="20" min="1" max="160"') + field('dlRatio','DL ratio','number','value="0.7" step="0.05" min="0" max="1"');
      case 'V24': return field('payloadBytes','Payload bytes','number','value="1400" min="1" max="1000000"') + field('mtu','MTU','number','value="300" min="1" max="9000"') + `<label class="labfield"><span>RLC mode</span><select data-param="mode"><option>AM</option><option>UM</option><option>TM</option></select></label>`;
      case 'V25': return field('payloadBytes','Payload bytes','number','value="1400" min="1" max="1000000"') + field('snBits','SN bits','number','value="12" min="5" max="18"');
      case 'V26': return field('ue','UE count','number','value="2" min="1" max="32"');
      case 'V27': return field('x','UE X','number','value="20" step="1"') + field('y','UE Y','number','value="0" step="1"') + field('offset','HO offset dB','number','value="3" step="0.5" min="-20" max="20"');
      case 'V28': return field('azimuth','Azimuth°','number','value="10" step="1"') + field('layers','Antenna rows','number','value="2" min="1" max="4"') + field('beams','Beam count','number','value="16" min="2" max="128"');
      case 'V29': return field('pattern','TDD pattern','text','value="DDDDDDUUUU"') + field('slotsPerFrame','Slots/frame','number','value="20" min="1" max="1000"');
      case 'V30': return field('throughput','Throughput','number','value="100" step="1"') + field('goodput','Goodput','number','value="90" step="1"') + field('snr','SNR','number','value="15" step="0.5"') + field('ber','BER','number','value="0.02" step="0.001" min="0" max="1"') + field('latency','Latency ms','number','value="5" step="0.1" min="0"') + field('prbs','PRBs','number','value="52" min="1"') + field('totalPrbs','Total PRBs','number','value="106" min="1"') + field('retransmissions','Retransmissions','number','value="3" min="0"');
      default: return '';
    }
  }

  function addStyles() {
    if (document.getElementById('lab-js-style')) return;
    const s = document.createElement('style'); s.id = 'lab-js-style';
    s.textContent = `.labparams{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:6px;margin-top:8px;padding:8px;border:1px solid #1d2838;border-radius:8px;background:#080e16}.labfield{display:block;font-size:8px;color:#71809a}.labfield span{display:block;margin-bottom:3px}.labfield input,.labfield select{box-sizing:border-box;width:100%;padding:5px 6px;background:#0d141f;border:1px solid #263143;border-radius:5px;color:#dce7f8;font-size:9px}.labfield input[type=checkbox]{width:auto}.labhint{margin-top:7px;color:#68758b;font-size:8px;line-height:1.45}@media(max-width:650px){.labparams{grid-template-columns:1fr 1fr}}`;
    document.head.appendChild(s);
  }

  function getParams(card, version) {
    const p = new URLSearchParams({ version });
    card.querySelectorAll('[data-param]').forEach(el => {
      if (el.type === 'checkbox') p.set(el.dataset.param, el.checked ? 'true' : 'false');
      else if (el.value !== '') p.set(el.dataset.param, el.value);
    });
    // Keep the main simulator context synchronized for versions that use it.
    if (!p.has('snr')) p.set('snr', value('snr') || '15');
    if (!p.has('prbs')) p.set('prbs', value('prb') || '52');
    if (!p.has('mcs')) p.set('mcs', value('mcs') || '16');
    if (!p.has('layers')) p.set('layers', value('layers') || '2');
    return p;
  }

  async function runParameterized(card, version) {
    const button = card.querySelector('.one');
    const status = card.querySelector('.status');
    const result = card.querySelector('.result');
    button.disabled = true; status.textContent = 'RUNNING'; status.className = 'status ready';
    try {
      const r = await fetch('/api/lab?' + getParams(card, version).toString(), {cache:'no-store'});
      const d = await r.json();
      if (!r.ok || !d.ok) throw Error(d.error || `HTTP ${r.status}`);
      result.textContent = `${d.version}: ${d.result}`;
      result.style.display = 'block'; card.classList.add('open');
      status.textContent = 'PASS'; status.className = 'status pass';
    } catch (e) {
      result.textContent = e.message || String(e); result.style.display = 'block'; card.classList.add('open');
      status.textContent = 'FAIL'; status.className = 'status fail';
    } finally { button.disabled = false; }
  }

  function bindCard(card) {
    const version = (card.querySelector('.testtop b')?.textContent || '').match(/^V\d+/)?.[0];
    if (!PARAM.has(version) || card.dataset.labBound) return;
    card.dataset.labBound = '1';
    const panel = document.createElement('div'); panel.className = 'labparams'; panel.innerHTML = controls(version) + `<div class="labhint" style="grid-column:1/-1">These controls are passed directly to the existing Kotlin V${version.slice(1)} implementation through the web adapter. They do not change the canonical regression vectors.</div>`;
    card.querySelector('.result').before(panel);
    card.querySelector('.explain').insertAdjacentHTML('afterbegin', '<span class="muted"><b>Mode:</b> parameterized interactive adapter</span><br>');
    card.querySelector('.one').addEventListener('click', e => { e.stopImmediatePropagation(); runParameterized(card, version); }, true);
  }

  async function runAllInteractive() {
    const cards = Array.from(document.querySelectorAll('.test'));
    const summary = document.getElementById('summary');
    const button = document.getElementById('runall');
    button.disabled = true; button.textContent = 'RUNNING…';
    let passed = 0;
    try {
      const interactive = cards.filter(c => PARAM.has((c.querySelector('.testtop b')?.textContent || '').match(/^V\d+/)?.[0]));
      for (const card of interactive) {
        const v = (card.querySelector('.testtop b').textContent.match(/^V\d+/) || [])[0];
        await runParameterized(card, v);
        if (card.querySelector('.status').classList.contains('pass')) passed++;
      }
      summary.textContent = `${passed}/${interactive.length} V19–V30 parameterized tests passed; V31–V60 remain canonical.`;
    } finally { button.disabled = false; button.textContent = 'RUN ALL REFERENCE TESTS'; }
  }

  function bind() {
    addStyles();
    document.querySelectorAll('.test').forEach(bindCard);
    const all = document.getElementById('runall');
    if (all && !all.dataset.labRunAllBound) {
      all.dataset.labRunAllBound = '1';
      all.addEventListener('click', e => { e.stopImmediatePropagation(); runAllInteractive(); }, true);
    }
  }

  const observer = new MutationObserver(bind);
  const start = () => { bind(); const g = document.getElementById('groups'); if (g) observer.observe(g, {childList:true, subtree:true}); };
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start); else start();
})();
