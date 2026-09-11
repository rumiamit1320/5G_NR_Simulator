// V64 additive velocity-control adapter.
// Keeps radio-v64-fixed.js and the V62/V63 backend contracts unchanged.
(() => {
  'use strict';
  const velocity = document.getElementById('velocity');
  const velocityOut = document.getElementById('velocityOut');
  const legendVelocity = document.getElementById('legendVelocity');
  const statusText = document.getElementById('statusText');
  const runButton = document.getElementById('run');
  if (!velocity) return;

  let rerunTimer = null;

  function updateDisplay() {
    const value = Number(velocity.value) || 0;
    if (velocityOut) velocityOut.textContent = `${value} km/h`;
    if (legendVelocity) legendVelocity.textContent = `${value} km/h`;

    // Make the selected-UE panel reflect the newly selected velocity immediately.
    const details = document.getElementById('selectedDetails');
    if (details) {
      const rows = Array.from(details.querySelectorAll('div'));
      const row = rows.find(x => x.firstElementChild?.textContent === 'Velocity');
      if (row && row.lastElementChild) row.lastElementChild.textContent = `${value.toFixed(1)} km/h`;
    }

    // With Live enabled, apply the new velocity to the next V63 execution instead
    // of waiting for the old 1.5 s polling cycle. The existing Run handler remains
    // the sole owner of the simulation/API call.
    if (runButton && document.getElementById('liveToggle')?.textContent.includes('ON')) {
      clearTimeout(rerunTimer);
      rerunTimer = setTimeout(() => {
        if (statusText) statusText.textContent = `Applying UE velocity: ${value} km/h`;
        runButton.click();
      }, 250);
    }
  }

  velocity.addEventListener('input', updateDisplay);
  velocity.addEventListener('change', updateDisplay);
  updateDisplay();
})();
