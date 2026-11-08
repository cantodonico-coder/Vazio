const DEF = { enabled: true, delay: 0, maxSecs: 0, stopVideos: 0, stopMinutes: 0 };
const fields = ['delay', 'maxSecs', 'stopVideos', 'stopMinutes'];
const btn = document.getElementById('toggle');

function paint(on) {
  btn.textContent = on ? 'LIGADO — clique p/ desligar' : 'DESLIGADO — clique p/ ligar';
  btn.style.background = on ? '#e62117' : '#555';
}

chrome.storage.sync.get(DEF, (v) => {
  paint(v.enabled);
  fields.forEach((f) => {
    const el = document.getElementById(f);
    el.value = v[f];
    el.onchange = () => chrome.storage.sync.set({ [f]: Math.max(0, parseFloat(el.value) || 0) });
  });
  btn.onclick = () => chrome.storage.sync.get(DEF, (c) => { chrome.storage.sync.set({ enabled: !c.enabled }); paint(!c.enabled); });
});

function withTab(fn) {
  chrome.tabs.query({ active: true, currentWindow: true }, (t) => t[0] && fn(t[0].id));
}

withTab((id) => chrome.tabs.sendMessage(id, 'status', (r) => {
  if (chrome.runtime.lastError || !r) { document.getElementById('info').textContent = 'Abra youtube.com/shorts'; return; }
  document.getElementById('info').textContent = `${r.count} vídeos · ${r.minutes} min`;
}));

document.getElementById('reset').onclick = () => withTab((id) => {
  chrome.tabs.sendMessage(id, 'reset', () => void chrome.runtime.lastError);
  document.getElementById('info').textContent = '0 vídeos · 0 min';
});
