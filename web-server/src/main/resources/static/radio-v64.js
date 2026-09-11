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

  const s = document.createElement('script');
  s.src = '/radio-v64-fixed.js';
  s.defer = false;
  document.head.appendChild(s);
})();
