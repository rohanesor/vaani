(() => {
  const byId = (id) => document.getElementById(id);
  const set = (id, value) => { const node = byId(id); if (node) node.textContent = value; };

  fetch('release.json', { cache: 'no-store' })
    .then((response) => { if (!response.ok) throw new Error('No release metadata'); return response.json(); })
    .then((release) => {
      set('release-version', `iTantra ${release.version}`);
      set('release-min-android', release.minAndroid || 'Android 7.0+');
      set('release-size', release.size || '—');
      set('release-date', release.date || '—');
      set('release-sha', release.sha256 || '—');
      set('release-title', `iTantra ${release.version}`);
      set('release-summary', release.commit ? `Built from ${release.commit.slice(0, 12)}.` : 'Signed Android release.');
      const url = release.downloadUrl || `downloads/${release.version}/Vokie-${release.version}.apk`;
      byId('release-download').href = url;
      byId('download-button').href = url;
      byId('release-data').hidden = false;
      set('availability', 'Signed APK available for download. Verify the checksum before installation.');
    })
    .catch(() => set('availability', 'No production release is published yet.'));

  byId('copy-sha')?.addEventListener('click', async () => {
    const value = byId('release-sha')?.textContent || '';
    if (!value || value === '—') return;
    try { await navigator.clipboard.writeText(value); set('copy-sha', 'COPIED'); setTimeout(() => set('copy-sha', 'COPY SHA-256'), 1800); }
    catch (_) { set('copy-sha', 'COPY FAILED'); }
  });
})();
