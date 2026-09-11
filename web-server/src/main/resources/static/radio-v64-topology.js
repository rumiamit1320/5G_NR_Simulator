// V64 additive Network Topology presentation layer.
// Keeps the existing V63 data path and radio-v64-fixed.js intact.
(() => {
  'use strict';

  const $ = id => document.getElementById(id);
  const N = (v, d = 0) => Number.isFinite(Number(v)) ? Number(v) : d;
  const P = v => (100 * N(v)).toFixed(1) + ' %';
  const E = v => String(v ?? '').replace(/[&<>\"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','\"':'&quot;'}[c]));

  function selectedIndex() {
    const select = $('selectedUe');
    if (select && select.value !== '') return Math.max(0, Number(select.value) || 0);
    const selected = document.querySelector('#ueRows .selected-row');
    return selected ? Number(selected.dataset.i) || 0 : 0;
  }

  function renderTopologyState() {
    const body = $('topRows');
    const data = window.__v64LastData;
    if (!body || !data) return false;

    const us = Array.isArray(data.ueStates) ? data.ueStates : [];
    if (!us.length) {
      body.innerHTML = '<tr><td colspan="7">No UE state available. Run a simulation first.</td></tr>';
      return true;
    }

    const cells = Math.max(1, N(data.config?.cells, 1));
    const selected = Math.min(selectedIndex(), us.length - 1);

    body.innerHTML = us.map((u, i) => {
      const cell = ((N(u.ueId, i + 1) - 1) % cells) + 1;
      const sinr = N(u.meanSinrDb ?? u.sinrDb);
      const cqi = N(u.meanCqi ?? u.cqi);
      const mcs = N(u.meanMcs ?? u.mcs);
      const prbs = N(u.totalAllocatedPrbs ?? u.allocatedPrbs);
      const throughput = N(u.throughputMbps);
      return `<tr class="${i === selected ? 'selected-row' : ''}" data-topology-i="${i}">
        <td>UE ${E(u.ueId ?? i + 1)}</td>
        <td>gNB ${cell}</td>
        <td>${sinr.toFixed(1)} dB</td>
        <td>${cqi.toFixed(1)}</td>
        <td>${mcs.toFixed(1)}</td>
        <td>${prbs}</td>
        <td>${throughput.toFixed(1)} Mbps</td>
      </tr>`;
    }).join('');

    body.querySelectorAll('tr[data-topology-i]').forEach(row => {
      row.onclick = () => {
        const index = Number(row.dataset.topologyI) || 0;
        const select = $('selectedUe');
        if (select && index < select.options.length) {
          select.value = String(index);
          select.dispatchEvent(new Event('change', {bubbles: true}));
        } else {
          renderTopologyState();
        }
      };
    });
    return true;
  }

  function installTopologyState() {
    let attempts = 0;
    const timer = setInterval(() => {
      const body = $('topRows');
      if (!body) {
        if (++attempts > 100) clearInterval(timer);
        return;
      }
      renderTopologyState();
      if (window.__v64LastData) clearInterval(timer);
    }, 100);

    const select = $('selectedUe');
    if (select) {
      select.addEventListener('change', renderTopologyState);
    }

    // The selected UE can also be changed by clicking the topology canvas.
    const observeSelection = new MutationObserver(() => renderTopologyState());
    const selectedDetails = $('selectedDetails');
    if (selectedDetails) observeSelection.observe(selectedDetails, {childList: true, subtree: true});

    // V64 data is published by the existing fetch adapter after each V63 run.
    setInterval(renderTopologyState, 250);
  }

  function installStyles() {
    const style = document.createElement('style');
    style.textContent = `
      #topRows tr { cursor: pointer; }
      #topRows tr:hover { background: #0b2a44; }
      #topRows .selected-row { background: #0b3553; box-shadow: inset 3px 0 #16a9ff; }
      #topRows .selected-row td:first-child { color: #6effbc; font-weight: 800; }
    `;
    document.head.appendChild(style);
  }

  installStyles();
  installTopologyState();
})();
