// V64 additive Settings control.
// Keeps the existing simulator controls and runtime architecture intact.
(() => {
  'use strict';

  const init = () => {
    const button = document.querySelector('.settings');
    if (!button || button.dataset.v64SettingsBound === '1') return;
    button.dataset.v64SettingsBound = '1';
    button.setAttribute('role', 'button');
    button.setAttribute('tabindex', '0');
    button.setAttribute('title', 'Open simulator settings');
    button.style.cursor = 'pointer';

    const openSettings = () => {
      let panel = document.getElementById('v64SettingsPopover');
      if (!panel) {
        panel = document.createElement('div');
        panel.id = 'v64SettingsPopover';
        panel.style.cssText = 'position:fixed;top:68px;right:20px;z-index:1000;width:290px;padding:16px;border:1px solid #164464;border-radius:8px;background:linear-gradient(145deg,#071c30,#061424);color:#dcefff;box-shadow:0 14px 40px #0008;font-size:12px';
        panel.innerHTML = '<div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px"><strong style="font-size:14px">Simulator Settings</strong><button id="v64SettingsClose" style="border:1px solid #164464;background:#092036;color:#cfe7fa;border-radius:5px;padding:4px 8px;cursor:pointer">×</button></div>' +
          '<div style="color:#8fa8bd;line-height:1.55">V64 Radio Environment Lab</div>' +
          '<div style="margin-top:10px;padding:9px;border:1px solid #123554;border-radius:6px;background:#03111e">Backend: <b>V62 → V63 → V61 → V64</b><br>Mode: <b>Real-Time Closed Loop</b><br>API: <b>/api/lab</b></div>' +
          '<div style="margin-top:10px;color:#7890aa">These settings are informational. Existing simulation controls remain the source of truth.</div>';
        document.body.appendChild(panel);
        panel.querySelector('#v64SettingsClose').addEventListener('click', () => panel.remove());
      } else {
        panel.remove();
      }
    };

    button.addEventListener('click', openSettings);
    button.addEventListener('keydown', e => {
      if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); openSettings(); }
    });
  };

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
  else init();
  new MutationObserver(init).observe(document.documentElement, {childList:true, subtree:true});
})();
