// V64 additive velocity-control adapter.
// Keeps radio-v64-fixed.js and the V62/V63 backend contracts unchanged.
(() => {
  'use strict';
  const velocity = document.getElementById('velocity');
  const velocityOut = document.getElementById('velocityOut');
  const legendVelocity = document.getElementById('legendVelocity');
  const statusText = document.getElementById('statusText');
  const runButton = document.getElementById('run');
  const liveToggle = document.getElementById('liveToggle');
  if (!velocity) return;

  let rerunTimer = null;
  let userLive = !!liveToggle?.textContent.includes('ON');
  let firstAutoRunObserved = false;

  // radio-v64-fixed.js currently owns the legacy 1.5 s scheduling loop.
  // Prevent that timer from continuing unless the operator explicitly enables Live.
  // Other timers, including the clock and velocity debounce, are untouched.
  const nativeSetTimeout = window.setTimeout.bind(window);
  window.setTimeout = (fn, delay, ...args) => {
    const source = typeof fn === 'function' ? Function.prototype.toString.call(fn) : '';
    if (delay === 1500 && /api\(['\"]V63/.test(source) && !userLive) return -1;
    return nativeSetTimeout(fn, delay, ...args);
  };

  if (liveToggle) {
    liveToggle.addEventListener('click', () => {
      userLive = liveToggle.textContent.includes('ON');
    });
  }

  function clearInitialAutoRun() {
    if (firstAutoRunObserved || userLive) return;
    firstAutoRunObserved = true;
    // The legacy controller may have completed one request before this adapter
    // loaded. Return the HMI to its intended idle/READY state and leave all
    // V61/V62/V63/V64 engine logic untouched.
    if (runButton && document.getElementById('lastRun')?.textContent !== '--') {
      document.getElementById('reset')?.click();
    }
  }

  function updateDisplay() {
    const value = Number(velocity.value) || 0;
    if (velocityOut) velocityOut.textContent = `${value} km/h`;
    if (legendVelocity) legendVelocity.textContent = `${value} km/h`;

    const details = document.getElementById('selectedDetails');
    if (details) {
      const rows = Array.from(details.querySelectorAll('div'));
      const row = rows.find(x => x.firstElementChild?.textContent === 'Velocity');
      if (row && row.lastElementChild) row.lastElementChild.textContent = `${value.toFixed(1)} km/h`;
    }

    if (runButton && userLive) {
      clearTimeout(rerunTimer);
      rerunTimer = nativeSetTimeout(() => {
        if (statusText) statusText.textContent = `Applying UE velocity: ${value} km/h`;
        runButton.click();
      }, 250);
    }
  }

  velocity.addEventListener('input', updateDisplay);
  velocity.addEventListener('change', updateDisplay);
  updateDisplay();

  // Detect completion of the legacy first request without touching its controller.
  const watch = nativeSetTimeout(function pollInitialRun() {
    if (userLive) return;
    const last = document.getElementById('lastRun');
    if (last && last.textContent !== '--') clearInitialAutoRun();
    else nativeSetTimeout(pollInitialRun, 100);
  }, 100);
})();