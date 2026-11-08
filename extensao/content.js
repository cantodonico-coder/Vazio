// Shorts Auto Next — passa para o próximo Short sozinho
(() => {
  const DEF = { enabled: true, delay: 0, maxSecs: 0, stopVideos: 0, stopMinutes: 0 };
  let cfg = { ...DEF };
  let count = 0;
  let sessionStart = Date.now();
  let lastUrl = location.href;
  let lastTime = 0;
  let advancing = false;
  let badge;

  chrome.storage.sync.get(DEF, (v) => { cfg = v; render(); });
  chrome.storage.onChanged.addListener((ch) => {
    for (const k in ch) cfg[k] = ch[k].newValue;
    if (ch.enabled && ch.enabled.newValue) { count = 0; sessionStart = Date.now(); }
    render();
  });
  chrome.runtime.onMessage.addListener((m, _s, reply) => {
    if (m === 'status') reply({ count, minutes: Math.floor((Date.now() - sessionStart) / 60000) });
    if (m === 'reset') { count = 0; sessionStart = Date.now(); render(); }
  });

  const onShorts = () => location.pathname.startsWith('/shorts');

  function activeVideo() {
    return (
      document.querySelector('ytd-reel-video-renderer[is-active] video') ||
      document.querySelector('#shorts-player video') ||
      document.querySelector('ytm-shorts-player video') ||
      [...document.querySelectorAll('video')].find((v) => !v.paused)
    );
  }

  function goNext() {
    const btn = document.querySelector('#navigation-button-down button, #navigation-button-down ytd-button-renderer button');
    if (btn) { btn.click(); return; }
    // fallback: tecla seta para baixo
    const ev = { key: 'ArrowDown', code: 'ArrowDown', keyCode: 40, which: 40, bubbles: true };
    document.dispatchEvent(new KeyboardEvent('keydown', ev));
    document.dispatchEvent(new KeyboardEvent('keyup', ev));
  }

  function stop(reason) {
    chrome.storage.sync.set({ enabled: false });
    toast(reason);
  }

  function advance() {
    if (advancing) return;
    advancing = true;
    setTimeout(() => {
      advancing = false;
      if (!cfg.enabled || !onShorts()) return;
      count++;
      if (cfg.stopVideos > 0 && count >= cfg.stopVideos) {
        stop(`Parado: ${count} Shorts assistidos`);
        return;
      }
      goNext();
      render();
    }, Math.max(0, cfg.delay) * 1000);
  }

  function tick() {
    if (location.href !== lastUrl) { lastUrl = location.href; lastTime = 0; advancing = false; }
    if (!cfg.enabled || !onShorts()) return;

    if (cfg.stopMinutes > 0 && Date.now() - sessionStart >= cfg.stopMinutes * 60000) {
      const v = activeVideo(); if (v) v.pause();
      stop(`Parado: ${cfg.stopMinutes} min de Shorts`);
      return;
    }

    const v = activeVideo();
    if (!v || !v.duration || isNaN(v.duration)) return;
    const t = v.currentTime;

    // Short longo: pula após X segundos
    if (cfg.maxSecs > 0 && t >= cfg.maxSecs) { lastTime = t; advance(); return; }

    // Fim do vídeo: perto do final OU voltou ao início (o YouTube repete em loop)
    const nearEnd = t >= v.duration - 0.35;
    const looped = lastTime > v.duration - 1.5 && t < 1;
    lastTime = t;
    if (nearEnd || looped) {
      if (cfg.delay > 0) v.pause();
      advance();
    }
  }

  // ---- botão flutuante (liga/desliga rápido) ----
  function render() {
    if (!document.body) return;
    if (!badge) {
      badge = document.createElement('div');
      badge.id = 'san-badge';
      badge.title = 'Shorts Auto Next — clique para ligar/desligar (Alt+Shift+S)';
      Object.assign(badge.style, {
        position: 'fixed', bottom: '20px', left: '20px', zIndex: 99999,
        padding: '8px 12px', borderRadius: '20px', font: '600 13px system-ui, sans-serif',
        color: '#fff', cursor: 'pointer', userSelect: 'none', boxShadow: '0 2px 8px #0006'
      });
      badge.onclick = () => chrome.storage.sync.set({ enabled: !cfg.enabled });
      document.body.appendChild(badge);
    }
    badge.style.display = onShorts() ? 'block' : 'none';
    badge.style.background = cfg.enabled ? '#e62117' : '#555';
    let txt = cfg.enabled ? `⏭ Auto ON · ${count}` : '⏸ Auto OFF';
    if (cfg.enabled && cfg.stopVideos > 0) txt += `/${cfg.stopVideos}`;
    badge.textContent = txt;
  }

  function toast(msg) {
    const d = document.createElement('div');
    d.textContent = msg;
    Object.assign(d.style, {
      position: 'fixed', top: '80px', left: '50%', transform: 'translateX(-50%)', zIndex: 99999,
      background: '#222', color: '#fff', padding: '12px 18px', borderRadius: '10px',
      font: '600 15px system-ui, sans-serif', boxShadow: '0 4px 14px #0008'
    });
    document.body.appendChild(d);
    setTimeout(() => d.remove(), 4000);
  }

  setInterval(tick, 200);
  setInterval(render, 1000);
})();
