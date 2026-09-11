// V64 compatibility entry point. The functional HMI controller lives in the additive fixed module.
(() => {
  const s = document.createElement('script');
  s.src = '/radio-v64-fixed.js';
  s.defer = false;
  document.head.appendChild(s);
})();
