// Keep the page useful when the feed is unavailable or JavaScript is disabled.
fetch('releases.json')
  .then(response => {
    if (!response.ok) throw new Error('Release feed unavailable');
    return response.json();
  })
  .then(({ latest }) => {
    if (typeof latest !== 'string' || !/^\d+\.\d+\.\d+(?:-[\da-zA-Z.-]+)?$/.test(latest)) return;
    document.querySelectorAll('[data-release-link]').forEach(link => {
      link.href = `https://github.com/erfanoabdi/apklayer/releases/tag/v${latest}`;
    });
    document.querySelector('[data-release-label]').textContent = `v${latest} is here — see what’s new`;
    document.querySelector('[data-download-version]').textContent = `v${latest}`;
  })
  .catch(() => {});
