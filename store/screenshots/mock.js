// Мок-данные для скриншотов магазина (не входит в приложение).
(function () {
  const scenes = [
    ['#ff9a5a', '#5b2a86', 'sun'], ['#4fc3f7', '#0b3d63', 'sea'], ['#a8e063', '#1e5631', 'hills'],
    ['#ffd1e8', '#6a3093', 'city'], ['#f6d365', '#c0392b', 'sun'], ['#89f7fe', '#2b5876', 'hills']];
  function scene(i, w, h) {
    const [a, b, kind] = scenes[i % scenes.length];
    const c = document.createElement('canvas'); c.width = w; c.height = h; const g = c.getContext('2d');
    const gr = g.createLinearGradient(0, 0, 0, h); gr.addColorStop(0, a); gr.addColorStop(1, b); g.fillStyle = gr; g.fillRect(0, 0, w, h);
    g.fillStyle = 'rgba(255,255,230,.9)'; g.beginPath(); g.arc(w * .7, h * .38, h * .12, 0, 7); g.fill();
    g.fillStyle = 'rgba(0,0,0,.35)';
    if (kind === 'city') { for (let x = 0; x < w; x += w / 14) { const bh = h * (.25 + ((x * 7) % 5) / 14); g.fillRect(x, h - bh, w / 16, bh); } }
    else if (kind === 'sea') { g.fillRect(0, h * .62, w, h); g.fillStyle = 'rgba(255,255,255,.25)'; for (let y = h * .66; y < h; y += h / 18) g.fillRect(w * .1, y, w * .8, 2); }
    else { g.beginPath(); g.moveTo(0, h); for (let x = 0; x <= w; x += w / 8) g.lineTo(x, h * (.55 + .15 * Math.sin(x / w * 7 + i))); g.lineTo(w, h); g.fill();
      g.fillStyle = 'rgba(0,0,0,.25)'; g.beginPath(); g.moveTo(0, h); for (let x = 0; x <= w; x += w / 6) g.lineTo(x, h * (.7 + .1 * Math.cos(x / w * 5 + i))); g.lineTo(w, h); g.fill(); }
    return c.toDataURL('image/jpeg', .85);
  }
  const mode = (location.hash || '#editor').slice(1);
  document.getElementById('splash').classList.add('hide'); document.getElementById('app').hidden = false;
  if (mode === 'empty') { render(); return; }
  const durs = [8200, 5400, 11800, 6900, 4300];
  durs.forEach((d, i) => state.clips.push({ id: ++uid, name: 'clip' + i, url: '', path: '', duration: d, start: 0, end: d, speed: 1, filter: 'none', transitionIn: 'none', thumb: scene(i, 160, 90), hasAudio: true, w: 1920, h: 1080 }));
  const C = state.clips;
  C[0].end = 6100; C[1].transitionIn = 'crossfade'; C[2].transitionIn = 'wipe'; C[3].transitionIn = 'black'; C[4].transitionIn = 'zoom';
  C[1].filter = 'vivid'; C[2].filter = 'cinema'; C[3].speed = 2; C[4].speed = 0.5;
  state.music = { name: 'Летний вечер.mp3', durationMs: 185000, path: '' };
  state.musicVol = 70; state.origVol = 40; state.musicStart = 12000;
  state.text = 'Лето 2026'; state.textSize = 80; state.textStyle = 'box';
  let sel = 1;
  const tool = { editor: 'trim', filters: 'filter', transitions: 'trans', music: 'music', text: 'text', export: null, done: null }[mode];
  if (mode === 'filters') sel = 2;
  if (mode === 'transitions') state.joint = 2;
  if (mode === 'text') { state.textStyle = 'pill'; state.textColor = '#FFFFFF'; state.text = 'Наш лучший день'; state.textPos = 'center'; }
  if (mode === 'filters') C[2].filter = 'cinema';
  state.sel = sel;
  const pv = document.getElementById('preview');
  pv.removeAttribute('src'); pv.poster = scene(sel, 1280, 720);
  if (tool) setTool(tool); else render();
  document.getElementById('timeLabel').textContent = '0:02 / ' + fmt(len(C[sel]));
  if (mode === 'export') sheet('Экспорт видео', 'MP4 · H.264 · 1280×720', 64, false);
  if (mode === 'done') sheet('Готово!', 'Сохранено: Галерея → Movies/KlipMaster/KlipMaster_1760562000.mp4 · 0:31 · 18.4 МБ', 100, true);
  document.getElementById('toast').hidden = true;
})();
