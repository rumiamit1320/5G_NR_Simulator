// V64 compatibility entry point. The functional HMI controller lives in the additive fixed module.
// This wrapper also normalizes non-JSON backend responses so the HMI does not fail with
// "Unexpected token ... in JSON" when Vercel/proxy returns an HTML/text error page.
(() => {
  const nativeFetch = window.fetch.bind(window);
  window.fetch = async (input, init) => {
    const response = await nativeFetch(input, init);
    const url = typeof input === 'string' ? input : input?.url || '';
    if (url.includes('/api/')) {
      const clone = response.clone();
      const text = await clone.text();
      try {
        JSON.parse(text);
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

  const s = document.createElement('script');
  s.src = '/radio-v64-fixed.js';
  s.defer = false;
  document.head.appendChild(s);
})();
