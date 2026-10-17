// КлипМастер — редактор. Нативный движок: плагин VideoEngine (Media3 Transformer, Android).
const $ = (id) => document.getElementById(id);
const Cap = window.Capacitor;
const Engine = Cap && Cap.isNativePlatform && Cap.isNativePlatform() ? Cap.Plugins.VideoEngine : null;

const FILTERS = [
  ['none','Без фильтра',''],['vivid','Яркий','saturate(1.5) contrast(1.12)'],['warm','Тёплый','sepia(.25) saturate(1.15)'],
  ['cool','Холодный','hue-rotate(-12deg) saturate(.95) brightness(1.02)'],['bw','Ч/Б','grayscale(1) contrast(1.15)'],
  ['cinema','Кино','saturate(.8) contrast(1.22) sepia(.1)'],['vintage','Винтаж','sepia(.45) saturate(.7)'],
  ['faded','Пастель','saturate(.75) contrast(.82) brightness(1.06)'],['drama','Драма','contrast(1.35) saturate(.9)'],['sunset','Закат','sepia(.3) saturate(1.2) hue-rotate(-8deg)'],
  ['sepia','Сепия','sepia(.9)'],['mint','Мята','hue-rotate(10deg) saturate(1.1)'],['invert','Негатив','invert(1)']];
const TRANS = [['none','Нет','⏐'],['black','Затемнение','◐'],['white','Вспышка','☀'],['slide','Сдвиг ←','⇠'],['slideL','Сдвиг →','⇢'],['zoom','Наезд','⊕'],['crossfade','Наплыв','◑'],['wipe','Шторка ←','▤'],['wipeL','Шторка →','▥']];
const OVERLAP = ['crossfade','wipe','wipeL'];
const COLORS = ['#FFFFFF','#FFD84D','#FF5D6C','#5FF0D4','#8F6BFF','#111111'];
const fcss = (id) => (FILTERS.find(f => f[0] === id) || FILTERS[0])[2];
const state = {joint:-1, musicVol:80, origVol:100, musicStart:0, textSize:56, textStyle:'box', textColor:'#FFFFFF', clips: [], sel: -1, portrait: false, music: null, muteOriginal: false, text: '', textPos: 'bottom', tool: null };
let uid = 0;

setTimeout(() => { $('splash').classList.add('hide'); $('app').hidden = false; }, 700);

const fmt = (ms) => { if (!isFinite(ms)) return '--:--'; const s = Math.max(0, ms) / 1000; return Math.floor(s / 60) + ':' + String(Math.floor(s % 60)).padStart(2, '0'); };
const fmtT = (ms) => fmt(ms) + '.' + Math.floor((ms % 1000) / 100);
const len = (c) => (c.end - c.start) / (c.speed || 1);
function toast(t, ms = 2600) { const el = $('toast'); el.textContent = t; el.hidden = false; clearTimeout(toast.t); toast.t = setTimeout(() => el.hidden = true, ms); }

// ---------- Импорт ----------
async function importVideos() {
  if (Engine) {
    try {
      toast('Импорт…', 60000);
      const { items } = await Engine.pickVideos();
      $('toast').hidden = true;
      for (const it of items) addClip({ name: it.name, path: it.path, url: Cap.convertFileSrc(it.path), duration: it.durationMs, w: it.width, h: it.height, hasAudio: it.hasAudio });
      if (items.length && state.clips.length === items.length) autoFormat();
    } catch (e) { toast('Ошибка импорта: ' + (e.message || e)); }
  } else {
    const inp = document.createElement('input'); inp.type = 'file'; inp.accept = 'video/*'; inp.multiple = true;
    inp.onchange = () => { for (const f of inp.files) addClip({ name: f.name, url: URL.createObjectURL(f), duration: NaN }); };
    inp.click();
  }
}
function addClip(c) {
  c.id = ++uid; c.filter = 'none'; c.transitionIn = 'none'; c.speed = 1; c.start = 0; c.end = c.duration;
  state.clips.push(c);
  const v = document.createElement('video'); v.preload = 'auto'; v.muted = true; v.src = c.url;
  v.onloadedmetadata = () => {
    if (!isFinite(c.duration)) { c.duration = v.duration * 1000; c.end = c.duration; }
    if (!c.w) { c.w = v.videoWidth; c.h = v.videoHeight; if (state.clips.length === 1) autoFormat(); }
    v.currentTime = Math.min(0.5, v.duration / 2);
  };
  v.onseeked = () => { try { const cv = document.createElement('canvas'); cv.width = 160; cv.height = 90; cv.getContext('2d').drawImage(v, 0, 0, 160, 90); c.thumb = cv.toDataURL('image/jpeg', .7); render(); } catch (e) {} v.src = ''; };
  select(state.clips.length - 1);
}
function autoFormat() { const c = state.clips[0]; if (c && c.w) { state.portrait = c.h > c.w; applyFormat(); } }
function applyFormat() { $('formatBtn').textContent = state.portrait ? '9:16' : '16:9'; $('frame').classList.toggle('portrait', state.portrait); }

// ---------- Рендер ----------
function render() {
  const has = state.clips.length > 0;
  $('emptyStage').hidden = has; $('player').hidden = !has; $('transport').hidden = !has;
  $('exportBtn').disabled = !has;
  document.querySelectorAll('.toolbar button').forEach(b => b.disabled = !has);
  const n = state.clips.length;
  $('clipCount').textContent = n + ' ' + (n % 10 === 1 && n % 100 !== 11 ? 'клип' : (n % 10 >= 2 && n % 10 <= 4 && (n % 100 < 10 || n % 100 >= 20)) ? 'клипа' : 'клипов');
  const total = state.clips.reduce((s, c, i) => s + (len(c) || 0) - (i > 0 && OVERLAP.includes(c.transitionIn) ? Math.min(800, len(c) / 2, len(state.clips[i - 1]) / 2) : 0), 0);
  $('totalLabel').textContent = 'Итого ' + fmt(total);
  const box = $('clips'); box.innerHTML = '';
  state.clips.forEach((c, i) => {
    const el = document.createElement('button');
    el.className = 'clip' + (i === state.sel ? ' active' : '') + (c.start > 0 || c.end < c.duration ? ' trimmed' : '');
    el.style.width = Math.max(64, Math.min(220, (len(c) || 3000) / 1000 * 14)) + 'px';
    if (c.thumb) el.style.backgroundImage = `url(${c.thumb})`;
    el.innerHTML = `<span class="num">${i + 1}</span><span class="meta">${fmt(len(c))}</span>`;
    if (c.filter !== 'none') el.innerHTML += `<span class="fx">FX</span>`;
    if (c.speed !== 1) el.innerHTML += `<span class="spd">${c.speed}×</span>`;
    el.onclick = () => select(i);
    if (i > 0) {
      const t = TRANS.find(x => x[0] === c.transitionIn) || TRANS[0];
      const jb = document.createElement('button');
      jb.className = 'joint' + (c.transitionIn !== 'none' ? ' set' : '') + (state.joint === i && state.tool === 'trans' ? ' active' : '');
      jb.textContent = c.transitionIn !== 'none' ? t[2] : '+'; jb.title = 'Переход';
      jb.onclick = () => openJoint(i);
      box.appendChild(jb);
    }
    box.appendChild(el);
  });
  $('musicTrack').hidden = !state.music; if (state.music) $('musicName').textContent = state.music.name + (state.muteOriginal ? ' · без исходного звука' : '');
  $('textTrack').hidden = !state.text; $('textName').textContent = state.text;
  const tp = $('textPreview'); tp.hidden = !state.text; tp.textContent = state.text; tp.className = 'text-preview ' + state.textPos + ' ' + state.textStyle; tp.style.color = state.textColor; tp.style.fontSize = 'clamp(10px,' + (state.textSize / 14) + 'vw,' + (state.textSize / 2.2) + 'px)';
  $('removeMusic').hidden = !state.music; $('pickMusic').textContent = state.music ? 'Заменить трек' : 'Выбрать трек';
  syncTrim(); syncFx();
}

// ---------- Плеер ----------
const pv = $('preview');
let playAll = false;
function select(i) {
  state.sel = i; const c = state.clips[i];
  if (c) { if (pv.dataset.src !== c.url) { pv.src = c.url; pv.dataset.src = c.url; } pv.onloadedmetadata = () => pv.currentTime = c.start / 1000; pv.currentTime = c.start / 1000; }
  render();
}
function setPlayIcon(p) { $('playIco').innerHTML = p ? '<path d="M7 5h3.5v14H7zM13.5 5H17v14h-3.5z"/>' : '<path d="M7 5l12 7-12 7z"/>'; }
$('playBtn').onclick = () => {
  if (!pv.paused) { pv.pause(); return; }
  const c = state.clips[state.sel]; if (!c) return;
  if (pv.currentTime * 1000 >= c.end - 50) pv.currentTime = c.start / 1000;
  pv.play();
};
pv.onplay = () => setPlayIcon(true); pv.onpause = () => setPlayIcon(false);
pv.ontimeupdate = () => {
  const c = state.clips[state.sel]; if (!c) return;
  pv.playbackRate = c.speed || 1;
  const t = pv.currentTime * 1000;
  if (t < c.start - 100) pv.currentTime = c.start / 1000;
  if (t >= c.end) {
    if (!pv.paused && state.sel < state.clips.length - 1) { select(state.sel + 1); pv.play(); }
    else pv.pause();
  }
  $('timeLabel').textContent = fmt((t - c.start) / (c.speed || 1)) + ' / ' + fmt(len(c));
};

// ---------- Инструменты ----------
document.querySelectorAll('.toolbar button').forEach(b => b.onclick = () => {
  const t = b.dataset.tool;
  if (t === 'export') return startExport();
  setTool(state.tool === t ? null : t);
});
function setTool(t) {
  state.tool = t;
  document.querySelectorAll('.toolbar button').forEach(x => x.classList.toggle('on', x.dataset.tool === state.tool));
  $('panel').hidden = !state.tool;
  ['trim', 'filter', 'trans', 'music', 'text'].forEach(p => $('panel-' + p).hidden = p !== state.tool);
  render();
}

function syncTrim() {
  const c = state.clips[state.sel]; if (!c || !isFinite(c.duration)) return;
  for (const id of ['trimStart', 'trimEnd']) { $(id).max = Math.round(c.duration); }
  $('trimStart').value = c.start; $('trimEnd').value = c.end;
  $('trimStartLbl').textContent = fmtT(c.start); $('trimEndLbl').textContent = fmtT(c.end);
  $('speedSeg').querySelectorAll('button').forEach(b => b.classList.toggle('on', +b.dataset.s === c.speed));
  $('trimInfo').textContent = '№' + (state.sel + 1) + ' · ' + fmtT(len(c)) + ' из ' + fmtT(c.duration);
  $('moveLeft').disabled = state.sel <= 0; $('moveRight').disabled = state.sel >= state.clips.length - 1;
}
const MIN = 300;
$('trimStart').oninput = (e) => { const c = state.clips[state.sel]; c.start = Math.min(+e.target.value, c.end - MIN); pv.pause(); pv.currentTime = c.start / 1000; render(); };
$('trimEnd').oninput = (e) => { const c = state.clips[state.sel]; c.end = Math.max(+e.target.value, c.start + MIN); pv.pause(); pv.currentTime = c.end / 1000; render(); };
function move(d) { const i = state.sel, j = i + d; if (j < 0 || j >= state.clips.length) return; [state.clips[i], state.clips[j]] = [state.clips[j], state.clips[i]]; state.sel = j; render(); }
$('moveLeft').onclick = () => move(-1); $('moveRight').onclick = () => move(1);
$('deleteClip').onclick = () => { state.clips.splice(state.sel, 1); if (!state.clips.length) { state.sel = -1; pv.removeAttribute('src'); pv.dataset.src = ''; $('panel').hidden = true; state.tool = null; render(); return; } select(Math.min(state.sel, state.clips.length - 1)); };
$('splitBtn').onclick = () => {
  const c = state.clips[state.sel]; const t = Math.round(pv.currentTime * 1000);
  if (t <= c.start + MIN || t >= c.end - MIN) return toast('Поставьте плеер внутрь клипа, чтобы разрезать');
  const b = Object.assign({}, c, { id: ++uid, start: t }); c.end = t;
  state.clips.splice(state.sel + 1, 0, b); render(); toast('Клип разрезан на ' + fmtT(t - c.start));
};

$('pickMusic').onclick = async () => {
  if (!Engine) return toast('Музыка доступна в Android-приложении');
  try { const { items } = await Engine.pickAudio(); if (items.length) { state.music = items[0]; render(); toast('Трек добавлен'); } } catch (e) { toast('Ошибка: ' + (e.message || e)); }
};
$('removeMusic').onclick = () => { state.music = null; render(); };
$('muteOriginal').onchange = (e) => { state.muteOriginal = e.target.checked; render(); };
$('textInput').oninput = (e) => { state.text = e.target.value; render(); };
$('textPos').querySelectorAll('button').forEach(b => b.onclick = () => { state.textPos = b.dataset.pos; $('textPos').querySelectorAll('button').forEach(x => x.classList.toggle('on', x === b)); render(); });
$('formatBtn').onclick = () => { state.portrait = !state.portrait; applyFormat(); toast('Формат ' + (state.portrait ? '9:16 (720×1280)' : '16:9 (1280×720)')); };
$('importBig').onclick = importVideos; $('importSmall').onclick = importVideos;
$('exportBtn').onclick = startExport;

// ---------- Экспорт ----------
let progressSub = null;
function sheet(title, text, pct, done) {
  $('sheet').hidden = false; $('sheetTitle').textContent = title; $('sheetText').textContent = text;
  $('progressBar').style.width = pct + '%'; $('progressLbl').textContent = pct + '%';
  $('progressBar').parentElement.hidden = $('progressLbl').hidden = pct < 0;
  $('sheetCancel').hidden = done; $('sheetOk').hidden = !done;
}
$('sheetOk').onclick = () => { $('sheet').hidden = true; if (exportJustSucceeded) { exportJustSucceeded = false; maybeAskReview(); } };
let exportJustSucceeded = false;
$('sheetCancel').onclick = async () => { if (Engine) await Engine.cancelExport(); $('sheet').hidden = true; toast('Экспорт отменён'); };

async function startExport() {
  if (!state.clips.length) return;
  pv.pause();
  if (!Engine) { sheet('Экспорт недоступен', 'Сборка MP4 выполняется нативным движком и работает только в Android-приложении КлипМастер.', -1, true); return; }
  sheet('Экспорт видео', 'MP4 · H.264 · ' + (state.portrait ? '720×1280' : '1280×720'), 0, false);
  if (progressSub) progressSub.remove();
  progressSub = await Engine.addListener('progress', (p) => { $('progressBar').style.width = p.percent + '%'; $('progressLbl').textContent = p.percent + '%'; });
  try {
    const r = await Engine.export({
      clips: state.clips.map(c => ({ path: c.path, startMs: Math.round(c.start), filter: c.filter, speed: c.speed, transitionIn: c.transitionIn, endMs: Math.round(c.end), hasAudio: c.hasAudio !== false })),
      portrait: state.portrait,
      text: state.text, textPosition: state.textPos,
      musicPath: state.music ? state.music.path : null,
      muteOriginal: state.muteOriginal,
      musicVolume: state.musicVol / 100, originalVolume: state.origVol / 100, musicStartMs: Math.round(state.musicStart),
      textSize: state.textSize, textStyle: state.textStyle, textColor: state.textColor,
    });
    const mb = (r.sizeBytes / 1048576).toFixed(1);
    onExportSuccess();
    sheet('Готово!', `Сохранено: ${r.location} · ${fmt(r.durationMs)} · ${mb} МБ`, 100, true);
  } catch (e) {
    if ($('sheet').hidden) return;
    sheet('Не удалось экспортировать', e.message || String(e), -1, true);
  } finally { if (progressSub) { progressSub.remove(); progressSub = null; } }
}

// ---------- Фильтры и переходы ----------
function openJoint(i) { state.joint = i; setTool('trans'); }
function presetBtn(label, on, bg, icon, onclick) {
  const b = document.createElement('button'); b.className = 'preset' + (on ? ' on' : '');
  b.innerHTML = `<span class="sw"></span><span>${label}</span>`;
  const sw = b.firstChild; if (bg) { sw.style.backgroundImage = `url(${bg})`; } if (icon) sw.textContent = icon;
  b.onclick = onclick; return b;
}
function syncFx() {
  const c = state.clips[state.sel];
  pv.style.filter = c ? fcss(c.filter) : '';
  if (c && !$('panel-filter').hidden) {
    $('filterInfo').textContent = 'клип №' + (state.sel + 1);
    const box = $('filters'); box.innerHTML = '';
    FILTERS.forEach(f => { const b = presetBtn(f[1], c.filter === f[0], c.thumb, c.thumb ? '' : '🎨', () => { c.filter = f[0]; render(); }); b.firstChild.style.filter = f[2]; box.appendChild(b); });
  }
  if (!$('panel-trans').hidden) {
    if (state.joint < 1 || state.joint >= state.clips.length) state.joint = Math.max(1, state.sel);
    const n = state.joint, k = state.clips[n], box = $('transitions'); box.innerHTML = '';
    if (state.clips.length < 2 || !k) { $('transInfo').textContent = 'добавьте второй клип'; return; }
    $('transInfo').textContent = 'между №' + n + ' и №' + (n + 1);
    TRANS.forEach(t => box.appendChild(presetBtn(t[1], k.transitionIn === t[0], null, t[2], () => { k.transitionIn = t[0]; render(); })));
  }
}
$('filterAll').onclick = () => { const c = state.clips[state.sel]; if (!c) return; state.clips.forEach(x => x.filter = c.filter); render(); toast('Фильтр применён ко всем клипам'); };
$('transAll').onclick = () => { const k = state.clips[state.joint]; if (!k) return; state.clips.forEach((x, i) => { if (i > 0) x.transitionIn = k.transitionIn; }); render(); toast('Переход применён ко всем стыкам'); };
render();

// ---------- Скорость, дубли, звук, стиль текста ----------
$('speedSeg').querySelectorAll('button').forEach(b => b.onclick = () => { const c = state.clips[state.sel]; if (!c) return; c.speed = +b.dataset.s; render(); });
$('dupBtn').onclick = () => { const c = state.clips[state.sel]; if (!c) return; state.clips.splice(state.sel + 1, 0, Object.assign({}, c, { id: ++uid })); select(state.sel + 1); toast('Клип продублирован'); };
function bindRange(id, key, lbl, f) { $(id).oninput = (e) => { state[key] = +e.target.value; render(); }; }
bindRange('musicVol', 'musicVol'); bindRange('origVol', 'origVol'); bindRange('musicStart', 'musicStart');
function seg(id, key, num) { $(id).querySelectorAll('button').forEach(b => b.onclick = () => { state[key] = num ? +b.dataset.v : b.dataset.v; render(); }); }
seg('textSizeSeg', 'textSize', true); seg('textStyleSeg', 'textStyle', false);
COLORS.forEach(col => { const b = document.createElement('button'); b.style.background = col; b.dataset.v = col; b.onclick = () => { state.textColor = col; render(); }; $('textColors').appendChild(b); });
function syncExtras() {
  $('musicVol').value = state.musicVol; $('musicVolLbl').textContent = state.musicVol + '%';
  $('origVol').value = state.origVol; $('origVolLbl').textContent = state.origVol + '%';
  $('musicStart').max = state.music ? Math.max(0, (state.music.durationMs || 0) - 1000) : 0;
  $('musicStart').value = state.musicStart; $('musicStartLbl').textContent = fmt(state.musicStart);
  $('muteOriginal').checked = state.muteOriginal; $('textInput').value !== state.text && ($('textInput').value = state.text);
  $('textSizeSeg').querySelectorAll('button').forEach(b => b.classList.toggle('on', +b.dataset.v === state.textSize));
  $('textStyleSeg').querySelectorAll('button').forEach(b => b.classList.toggle('on', b.dataset.v === state.textStyle));
  $('textPos').querySelectorAll('button').forEach(b => b.classList.toggle('on', b.dataset.pos === state.textPos));
  $('textColors').querySelectorAll('button').forEach(b => b.classList.toggle('on', b.dataset.v === state.textColor));
  $('formatBtn').textContent = state.portrait ? '9:16' : '16:9'; $('frame').classList.toggle('portrait', state.portrait);
}

// ---------- Сохранение проекта ----------
const KEY = 'clipmaster.project.v1';
const SAVE_KEYS = ['portrait', 'music', 'muteOriginal', 'text', 'textPos', 'musicVol', 'origVol', 'musicStart', 'textSize', 'textStyle', 'textColor'];
let saveT = 0;
function saveProject() {
  if (!Engine) return; // в браузере blob-ссылки не переживают перезапуск
  clearTimeout(saveT);
  saveT = setTimeout(() => {
    const p = { clips: state.clips.map(c => { const o = Object.assign({}, c); delete o.url; return o; }) };
    SAVE_KEYS.forEach(k => p[k] = state[k]);
    try { localStorage.setItem(KEY, JSON.stringify(p)); } catch (e) {}
  }, 300);
}
function restoreProject() {
  if (!Engine) return;
  try {
    const p = JSON.parse(localStorage.getItem(KEY) || 'null'); if (!p || !p.clips) return;
    SAVE_KEYS.forEach(k => { if (k in p) state[k] = p[k]; });
    state.clips = p.clips.map(c => Object.assign(c, { url: Cap.convertFileSrc(c.path) }));
    uid = state.clips.reduce((m, c) => Math.max(m, c.id || 0), 0);
    if (state.clips.length) { select(0); toast('Проект восстановлен'); }
  } catch (e) {}
}
$('newBtn').onclick = () => {
  if (state.clips.length && !confirm('Начать новый проект? Текущий будет очищен.')) return;
  Object.assign(state, { clips: [], sel: -1, music: null, muteOriginal: false, text: '', textPos: 'bottom', musicVol: 80, origVol: 100, musicStart: 0, textSize: 56, textStyle: 'box', textColor: '#FFFFFF' });
  pv.removeAttribute('src'); pv.dataset.src = ''; localStorage.removeItem(KEY); setTool(null);
};
const _render = render;
render = function () { _render(); syncExtras(); saveProject(); };
restoreProject();
render();

// ---------- Оценки RuStore ----------
// Автозапрос: после 2-го успешного экспорта; если экран оценки не был показан или пользователь
// отказался — повтор не раньше чем через 10 экспортов. После показа экрана RuStore — больше не спрашиваем сами.
const Review = Engine ? Cap.Plugins.RuStoreReview : null;
const RKEY = 'clipmaster.review.v1';
const rstate = () => { try { return Object.assign({ exports: 0, lastAskAt: 0, shown: false }, JSON.parse(localStorage.getItem(RKEY) || '{}')); } catch (e) { return { exports: 0, lastAskAt: 0, shown: false }; } };
const rsave = (s) => { try { localStorage.setItem(RKEY, JSON.stringify(s)); } catch (e) {} };
function onExportSuccess() {
  const s = rstate(); s.exports++; rsave(s);
  exportJustSucceeded = true;
  if (Review && shouldAsk(s)) Review.requestReviewFlow().catch(() => {}); // подготовить заранее
}
function shouldAsk(s) {
  if (s.shown) return false;
  if (s.lastAskAt === 0) return s.exports >= 2;
  return s.exports - s.lastAskAt >= 10;
}
function maybeAskReview() {
  const s = rstate();
  if (!Review || !shouldAsk(s)) return;
  s.lastAskAt = s.exports; rsave(s);
  $('ask').hidden = false;
}
async function launchReview(fromMenu) {
  if (!Review) { toast('Оценка доступна в приложении из RuStore'); return; }
  let r = { shown: false };
  try { r = await Review.launchReviewFlow(); } catch (e) {}
  if (r.shown) { const s = rstate(); s.shown = true; rsave(s); return; }
  // SDK не смог показать экран (нет RuStore, лимит показов и т.п.) — открываем страницу приложения
  try { await Review.openStorePage(); } catch (e) { if (fromMenu) toast('Не удалось открыть RuStore'); }
}
$('askRate').onclick = () => { $('ask').hidden = true; launchReview(false); };
$('askLater').onclick = () => { $('ask').hidden = true; };
$('menuBtn').onclick = () => { $('menu').hidden = false; };
$('menuClose').onclick = () => { $('menu').hidden = true; };
$('menu').onclick = (e) => { if (e.target.id === 'menu') $('menu').hidden = true; };
$('rateBtn').onclick = () => { $('menu').hidden = true; launchReview(true); };
$('privacyBtn').onclick = async () => {
  const url = 'https://lueissa.github.io/klipmaster/privacy.html';
  if (Review) { try { await Review.openUrl({ url }); } catch (e) { toast('Не удалось открыть ссылку'); } } else window.open(url, '_blank');
};
