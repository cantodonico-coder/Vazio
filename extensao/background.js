chrome.commands.onCommand.addListener((cmd) => {
  if (cmd !== 'toggle') return;
  chrome.storage.sync.get({ enabled: true }, (v) => chrome.storage.sync.set({ enabled: !v.enabled }));
});
