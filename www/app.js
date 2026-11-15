/* Xoriguer Notificacions — interfície (web/PWA i APK).
 * A dalt: lògica pura (exportada per a test.mjs; és la mateixa que Motor.java).
 * A sota: navegador. A l'APK parla amb el connector natiu "Notificacions"; a la web, localStorage. */
'use strict';

const ACCIONS = {
  ignora:  { ic: '🚫', nom: 'Ignorar',     desc: "L'elimina en arribar, sense amagar tota l'app." },
  refreda: { ic: '🧊', nom: 'Refredament', desc: 'Si et van escrivint seguit, només sona el primer missatge.', control: true },
  silencia: { ic: '🔕', nom: 'Silenciar', desc: 'Arriba igual, però sense so ni vibració.', control: true },
  alerta:  { ic: '🔔', nom: 'Alerta pròpia', desc: 'So o vibració especial per a una persona o una frase.' },
  respon:  { ic: '💬', nom: 'Respondre',   desc: "Respon sol si no l'has vista en un temps." },
  recorda: { ic: '⏰', nom: "Recorda-m'ho", desc: "Torna a sonar fins que la vegis." },
  posposa: { ic: '📦', nom: 'Posposar',    desc: 'Les guarda i te les dona en lots a les hores que triïs.' },
  alarma:  { ic: '🚨', nom: 'Alarma',      desc: 'Sona com una alarma (p. ex. càmera de seguretat).' },
  secret:  { ic: '🙈', nom: 'Secret',      desc: 'Amaga el contingut; només diu de quina app és.' },
};
/** Traducció (i18n.js). A node (proves) no hi és: queda en català. */
const T_ = (s, v) => (typeof I18N !== 'undefined' ? I18N.t(s, v)
  : (v ? s.replace(/\{(\w+)\}/g, (m, k) => (k in v ? v[k] : m)) : s));
const ATURA = new Set(['ignora', 'secret', 'posposa']);
const DIES = ['Dl', 'Dt', 'Dc', 'Dj', 'Dv', 'Ds', 'Dg'];
const VIBRACIONS = { defecte: 'La de l\'app', cap: 'Sense vibració', curta: 'Curta', llarga: 'Llarga', doble: 'Doble', triple: 'Triple', batec: 'Batec', sos: 'SOS' };

function normalitza(s) {
  return String(s || '').toLowerCase().normalize('NFD').replace(/\p{M}+/gu, '');
}
function llista(a) {
  if (typeof a === 'string') a = a.split(',');
  return (a || []).map((s) => String(s).trim()).filter(Boolean);
}
function minuts(hhmm) {
  const m = /^(\d{1,2}):(\d{2})$/.exec(hhmm || '');
  return m ? +m[1] * 60 + +m[2] : -1;
}
/** 1 = dilluns ... 7 = diumenge */
function diaSetmana(d) { return d.getDay() === 0 ? 7 : d.getDay(); }

function dinsHorari(r, ara) {
  const dies = llista(r.dies);
  if (dies.length && !dies.includes(String(diaSetmana(ara)))) return false;
  const de = minuts(r.de), a = minuts(r.a);
  if (de < 0 || a < 0 || de === a) return true;
  const m = ara.getHours() * 60 + ara.getMinutes();
  return de < a ? m >= de && m < a : m >= de || m < a;
}

function coincideix(r, paquet, titol, text, ara = new Date()) {
  if (r.activa === false) return false;
  const apps = llista(r.apps);
  if (apps.length && !apps.includes(paquet)) return false;
  const tot = normalitza(titol) + '\n' + normalitza(text);
  const paraules = llista(r.paraules);
  if (paraules.length && !paraules.some((p) => tot.includes(normalitza(p)))) return false;
  if (llista(r.exclou).some((p) => tot.includes(normalitza(p)))) return false;
  return dinsHorari(r, ara);
}

/** Què passaria amb una notificació: llista de regles aplicades fins a la primera que atura. */
function avalua(regles, paquet, titol, text, ara = new Date()) {
  const out = [];
  for (const r of regles) {
    if (!coincideix(r, paquet, titol, text, ara)) continue;
    out.push(r);
    if (ATURA.has(r.accio)) break;
  }
  return out;
}

function finsProperaFranja(franges, ara = new Date()) {
  const m = ara.getHours() * 60 + ara.getMinutes();
  let millor = Infinity;
  for (const f of String(franges || '').split(/[,;\s]+/)) {
    const x = minuts(f.trim());
    if (x < 0) continue;
    let dif = x - m;
    if (dif <= 0) dif += 1440;
    millor = Math.min(millor, dif);
  }
  if (millor === Infinity) return -1;
  return Math.max(millor * 60000 - ara.getSeconds() * 1000, 30000);
}

function resumRegla(r, nomsApps = {}) {
  const parts = [];
  const apps = llista(r.apps).map((p) => nomsApps[p] || p.split('.').pop());
  parts.push(apps.length ? apps.join(', ') : T_('Totes les apps'));
  const p = llista(r.paraules);
  if (p.length) parts.push('“' + p.join('”, “') + '”');
  if (r.de && r.a) parts.push(`${r.de}–${r.a}`);
  const d = llista(r.dies);
  if (d.length && d.length < 7) parts.push(d.map((x) => T_(DIES[x - 1])).join(' '));
  const extra = {
    refreda: () => T_('{n} s de calma', { n: r.segons || 60 }),
    respon: () => T_('als {n} min: “{t}”', { n: r.minuts || 10, t: r.text || '' }),
    recorda: () => T_('cada {n} min', { n: r.minuts || 5 }),
    posposa: () => T_('a les {h}', { h: r.franges || '?' }),
    alerta: () => T_(VIBRACIONS[r.vibracio || 'defecte']) + (r.soNom ? ' · ' + r.soNom : ''),
  }[r.accio];
  if (extra) parts.push(extra());
  return parts.join(' · ');
}

/** Quines apps envien més notificacions els últims `dies` dies: [{paquet, app, n, filtrades}], de més a menys. */
function estadistica(historial, dies = 7, ara = Date.now()) {
  const des = ara - dies * 86400000;
  const per = new Map();
  for (const h of historial || []) {
    if (!h || h.t < des) continue;
    const e = per.get(h.paquet) || { paquet: h.paquet, app: h.app || h.paquet, n: 0, filtrades: 0 };
    e.n++; if (h.accio) e.filtrades++;
    per.set(h.paquet, e);
  }
  return [...per.values()].sort((a, b) => b.n - a.n || a.app.localeCompare(b.app));
}

/** Notificacions de l'historial que aquesta regla hauria atrapat (amb l'hora en què van arribar). */
function provaRegla(r, historial) {
  return (historial || []).filter((h) => h && coincideix({ ...r, activa: true }, h.paquet, h.titol, h.text, new Date(h.t)));
}

/** Quantes vegades s'ha aplicat una regla (segons l'historial) els últims `dies` dies. */
function vegadesAplicada(r, historial, dies = 7, ara = Date.now()) {
  const des = ara - dies * 86400000;
  return (historial || []).filter((h) => h && h.t >= des && String(h.regles || '').split(', ').includes(r.nom)).length;
}

/** Llegeix una còpia de seguretat. Retorna {regles, ajustos} o llança un error amb el motiu. */
function llegeixCopia(text) {
  let o;
  try { o = JSON.parse(text); } catch { throw new Error(T_('El fitxer no és una còpia de les regles')); }
  if (Array.isArray(o)) o = { regles: o };
  if (!o || !Array.isArray(o.regles)) throw new Error(T_('El fitxer no és una còpia de les regles'));
  const regles = o.regles.filter((r) => r && typeof r === 'object' && ACCIONS[r.accio])
    .map((r) => ({ ...r, nom: String(r.nom || T_(ACCIONS[r.accio].nom)).slice(0, 60) }));
  if (o.regles.length && !regles.length) throw new Error(T_('Cap regla vàlida al fitxer'));
  const ajustos = o.ajustos && typeof o.ajustos === 'object' && !Array.isArray(o.ajustos) ? o.ajustos : null;
  return { regles, ajustos };
}

const PLANTILLES = [
  { accio: 'refreda', nom: 'Calma als xats', segons: 120, apps: ['com.whatsapp', 'org.telegram.messenger'] },
  { accio: 'alerta', nom: 'Persona important', paraules: ['Nom de la persona'], vibracio: 'batec', so: 'defecte' },
  { accio: 'ignora', nom: 'Fora publicitat', paraules: ['oferta', 'descompte', 'promoció'] },
  { accio: 'respon', nom: 'Estic conduint', minuts: 10, text: 'Ara no puc respondre, et dic alguna cosa més tard.', apps: ['com.whatsapp'] },
  { accio: 'recorda', nom: "Recorda'm els SMS", minuts: 5, vegades: 6, apps: ['com.google.android.apps.messaging'] },
  { accio: 'posposa', nom: 'Xarxes en lots', franges: '09:00, 13:00, 20:00', apps: ['com.instagram.android', 'com.facebook.katana'] },
  { accio: 'alarma', nom: 'Càmera de casa', paraules: ['moviment', 'persona detectada'] },
  { accio: 'secret', nom: 'Banc en secret', paraules: ['banc', 'targeta'] },
  { accio: 'silencia', nom: 'Nit tranquil·la', de: '23:00', a: '07:00' },
];

if (typeof module !== 'undefined') {
  module.exports = { normalitza, coincideix, dinsHorari, avalua, finsProperaFranja, resumRegla, llista, minuts, ACCIONS, PLANTILLES,
    estadistica, provaRegla, vegadesAplicada, llegeixCopia };
}

/* ===================================================================== */
if (typeof document !== 'undefined') (() => {
  const $ = (s, el = document) => el.querySelector(s);
  const $$ = (s, el = document) => [...el.querySelectorAll(s)];
  const natiu = !!(window.Capacitor && window.Capacitor.isNativePlatform && window.Capacitor.isNativePlatform());
  const N = natiu ? window.Capacitor.registerPlugin('Notificacions') : null;
  const VERSIO = '1.3.1';
  // Idioma: el desat (ajust «idioma», còpia a localStorage per traduir abans de pintar); buit = el del sistema
  let idiomaDesat = '';
  try { idiomaDesat = JSON.parse(localStorage.getItem('idioma')) || ''; } catch { /* res */ }
  I18N.posa(idiomaDesat);
  document.documentElement.lang = I18N.llengua;
  I18N.tradueix(document.body);
  const LOC = () => ({ ca: 'ca', es: 'es', fr: 'fr', en: 'en-GB' }[I18N.llengua] || 'ca');
  const WEB_DESCARREGUES = 'https://xoriguer.dev/#utils';
  const WEB_VERSIONS = 'https://xoriguer.dev/#utils';

  let regles = [];
  let ajustos = {};
  let historial = [];
  let apps = [];          // [{paquet, nom}]
  const nomsApps = {};
  let filtreHist = '';
  let filtreApp = '';     // paquet (des de l'estadística)

  const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  function toast(t) {
    const el = $('#toast'); el.textContent = t; el.classList.add('vist');
    clearTimeout(toast.t); toast.t = setTimeout(() => el.classList.remove('vist'), 2400);
  }
  const ls = {
    get(k, d) { try { return JSON.parse(localStorage.getItem(k)) ?? d; } catch { return d; } },
    set(k, v) { try { localStorage.setItem(k, JSON.stringify(v)); } catch { /* sense emmagatzematge */ } },
  };
  const AJ_DEF = { idioma: '', control: false, desfer: true, segonsDesfer: 10, historial: true, diesHistorial: 14, refredaGlobal: 0 };

  async function carrega() {
    if (natiu) {
      const r = await N.llegeix();
      regles = r.regles || []; ajustos = { ...AJ_DEF, ...(r.ajustos || {}) };
      try { apps = (await N.apps()).apps || []; } catch { apps = []; }
    } else {
      regles = ls.get('regles', []); ajustos = { ...AJ_DEF, ...ls.get('ajustos', {}) };
      apps = [
        ['com.whatsapp', 'WhatsApp'], ['org.telegram.messenger', 'Telegram'], ['com.google.android.gm', 'Gmail'],
        ['com.google.android.apps.messaging', 'Missatges'], ['com.instagram.android', 'Instagram'], ['com.facebook.katana', 'Facebook'],
        ['com.twitter.android', 'X'], ['com.android.chrome', 'Chrome'],
      ].map(([paquet, nom]) => ({ paquet, nom }));
    }
    apps.forEach((a) => { nomsApps[a.paquet] = a.nom; });
  }
  async function desa() {
    if (natiu) await N.desa({ regles, ajustos });
    else { ls.set('regles', regles); ls.set('ajustos', ajustos); }
  }
  async function carregaHistorial() {
    if (natiu) historial = ((await N.historial()).items || []).reverse();
    else historial = demoHistorial();
    historial.forEach((h) => { if (h.app) nomsApps[h.paquet] = nomsApps[h.paquet] || h.app; });
  }
  function demoHistorial() {
    const ara = Date.now();
    const mostres = [
      ['com.whatsapp', 'WhatsApp', 'Maria', T_('Arribo en 10 minuts')],
      ['com.whatsapp', 'WhatsApp', 'Maria', T_('On ets?')],
      ['com.instagram.android', 'Instagram', 'Instagram', T_('A en Pere li agrada la teva foto')],
      ['com.google.android.gm', 'Gmail', T_('Botiga'), T_('Oferta: 30% de descompte avui')],
      ['org.telegram.messenger', 'Telegram', T_('Grup Família'), T_('Diumenge dinem a casa la iaia')],
    ];
    return mostres.map(([paquet, app, titol, text], i) => {
      const ap = avalua(regles, paquet, titol, text);
      const at = ap.find((r) => ATURA.has(r.accio));
      return { id: 'demo' + i, t: ara - i * 7 * 60000, paquet, app, titol, text, regles: ap.map((r) => r.nom).join(', '),
        accio: at ? { ignora: 'ignorada', secret: 'amagada', posposa: 'posposada' }[at.accio] : undefined };
    });
  }

  // ---------------- Navegació ----------------
  function mostra(v) {
    $$('.vista').forEach((s) => s.classList.toggle('activa', s.id === 'v-' + v));
    $$('.pestanyes button').forEach((b) => b.classList.toggle('activa', b.dataset.v === v));
    if (v === 'historial') carregaHistorial().then(pintaHistorial);
    if (v === 'regles') carregaHistorial().then(pintaRegles);
    if (v === 'inici') refrescaInici();
    window.scrollTo(0, 0);
  }
  $$('.pestanyes button').forEach((b) => b.addEventListener('click', () => mostra(b.dataset.v)));
  // Guia: [data-guia="id"] obre la pestanya i l'apartat
  document.addEventListener('click', (ev) => {
    const b = ev.target.closest('[data-guia]'); if (!b) return;
    mostra('guia');
    const d = document.getElementById(b.dataset.guia);
    if (d) { d.open = true; setTimeout(() => d.scrollIntoView({ block: 'start' }), 0); }
  });
  if (!natiu) $('#g-instal').open = true; // a la web, el primer que cal és instal·lar-la

  // ---------------- Inici ----------------
  async function refrescaEstat() {
    if (!natiu) {
      $('#avis-web').hidden = false;
      $$('#estat button[data-fes]').forEach((b) => { b.disabled = true; });
      return;
    }
    const e = await N.estat();
    const marca = (id, ok) => { $(id + ' .punt').classList.toggle('ok', ok); $(id).classList.toggle('fet', ok); };
    marca('#p-acces', e.acces);
    marca('#p-permis', e.permisNotificacions);
    $('#p-alarmes').hidden = e.android < 31;
    marca('#p-alarmes', !!e.alarmesExactes);
    pintaPausa(e.pausaFins || 0);
  }

  // ---------------- Pausa ----------------
  let pausaWeb = 0;
  function horaCurta(t) {
    const d = new Date(t), h = d.toLocaleTimeString(LOC(), { hour: '2-digit', minute: '2-digit' });
    return d.toDateString() === new Date().toDateString() ? T_('les {h}', { h }) : T_('demà a les {h}', { h });
  }
  function pintaPausa(fins) {
    const on = fins > Date.now();
    $('#pausa-on').hidden = !on; $('#pausa-off').hidden = on;
    $('#pausa').classList.toggle('avis', on);
    if (on) $('#pausa-fins').textContent = horaCurta(fins);
  }
  $('#pausa').addEventListener('click', async (ev) => {
    const b = ev.target.closest('button[data-pausa]'); if (!b) return;
    let fins = 0;
    if (b.dataset.pausa === 'dema') { const d = new Date(); d.setDate(d.getDate() + 1); d.setHours(8, 0, 0, 0); fins = d.getTime(); }
    else if (+b.dataset.pausa > 0) fins = Date.now() + +b.dataset.pausa * 60000;
    if (natiu) fins = (await N.pausa({ fins })).pausaFins || 0; else pausaWeb = fins;
    pintaPausa(fins);
    toast(fins ? T_('Regles en pausa fins {h}', { h: horaCurta(fins) }) : T_('Regles actives de nou'));
  });

  async function refrescaInici() {
    await refrescaEstat();
    if (!natiu) pintaPausa(pausaWeb);
    await carregaHistorial();
    const avui = new Date(); avui.setHours(0, 0, 0, 0);
    const d = historial.filter((h) => h.t >= avui.getTime());
    $('#n-avui').textContent = d.length;
    $('#n-filtrades').textContent = d.filter((h) => h.accio).length;
    $('#n-regles').textContent = regles.filter((r) => r.activa !== false).length;
    pintaEstadistica();
  }
  $('#estat').addEventListener('click', async (ev) => {
    const b = ev.target.closest('button[data-fes]');
    if (!b || !natiu) return;
    await N[b.dataset.fes]();
  });

  // ---------------- Estadística ----------------
  function pintaEstadistica() {
    const e = estadistica(historial).slice(0, 6);
    const max = e.length ? e[0].n : 1;
    $('#sense-estadistica').hidden = e.length > 0;
    $('#estadistica').innerHTML = e.map((x) => `
      <li data-p="${esc(x.paquet)}">
        <button class="barra" data-veure="1"><span class="nom">${esc(nomsApps[x.paquet] || x.app)}</span>
          <span class="pista"><span style="width:${Math.max(4, Math.round(x.n / max * 100))}%"></span></span>
          <span class="n">${x.n}${x.filtrades ? ` <small>${T_('({n} filtrades)', { n: x.filtrades })}</small>` : ''}</span></button>
        <button class="xip" data-regla="1">${T_('+ Regla')}</button>
      </li>`).join('');
  }
  $('#estadistica').addEventListener('click', (ev) => {
    const li = ev.target.closest('li'); if (!li) return;
    const p = li.dataset.p;
    if (ev.target.closest('[data-regla]')) {
      obreEditor({ accio: 'posposa', activa: true, nom: nomsApps[p] || p, apps: [p], franges: '09:00, 13:00, 20:00' }, true);
    } else if (ev.target.closest('[data-veure]')) {
      filtreApp = p; mostra('historial');
    }
  });
  document.addEventListener('visibilitychange', () => { if (!document.hidden) refrescaEstat(); });

  function pintaPlantilles() {
    $('#plantilles').innerHTML = PLANTILLES.map((p, i) =>
      `<button class="plantilla" data-i="${i}"><span class="ic">${ACCIONS[p.accio].ic}</span><span><b>${esc(T_(p.nom))}</b><small>${esc(T_(ACCIONS[p.accio].desc))}</small></span></button>`).join('');
  }
  $('#plantilles').addEventListener('click', (ev) => {
    const b = ev.target.closest('.plantilla'); if (!b) return;
    const p = structuredClone(PLANTILLES[+b.dataset.i]);
    p.apps = (p.apps || []).filter((x) => !natiu || nomsApps[x]); // només les que tens instal·lades
    p.nom = T_(p.nom); if (p.text) p.text = T_(p.text); if (p.paraules) p.paraules = p.paraules.map((x) => T_(x));
    obreEditor({ ...p, activa: true }, true);
  });

  // ---------------- Regles ----------------
  function pintaRegles() {
    const ul = $('#llista-regles');
    $('#sense-regles').hidden = regles.length > 0;
    ul.innerHTML = regles.map((r, i) => {
      const v = vegadesAplicada(r, historial);
      return `
      <li class="${r.activa === false ? 'inactiva' : ''}" data-i="${i}">
        <span class="ic">${ACCIONS[r.accio]?.ic || '•'}</span>
        <div class="cos"><b>${esc(r.nom)}</b><small>${esc(T_(ACCIONS[r.accio]?.nom || ''))} · ${esc(resumRegla(r, nomsApps))}</small>
          ${v ? `<span class="etiqueta">${T_(v === 1 ? '{n} cop en 7 dies' : '{n} cops en 7 dies', { n: v })}</span>` : ''}</div>
        <div class="ordre"><button data-m="-1" aria-label="${T_('Puja')}">▲</button><button data-m="1" aria-label="${T_('Baixa')}">▼</button></div>
      </li>`;
    }).join('');
  }
  $('#llista-regles').addEventListener('click', async (ev) => {
    const li = ev.target.closest('li'); if (!li) return;
    const i = +li.dataset.i;
    const m = ev.target.closest('button[data-m]');
    if (m) {
      const j = i + +m.dataset.m;
      if (j < 0 || j >= regles.length) return;
      [regles[i], regles[j]] = [regles[j], regles[i]];
      await desa(); pintaRegles(); return;
    }
    obreEditor(regles[i], false, i);
  });
  $('#nova-regla').addEventListener('click', () => obreEditor({ accio: 'ignora', activa: true, nom: '' }, true));

  // ---------------- Editor ----------------
  const ed = $('#editor');
  let edRegla = null, edIndex = -1, edApps = [];

  function obreEditor(r, nova, index = -1) {
    edRegla = structuredClone(r); edIndex = nova ? -1 : index; edApps = llista(r.apps);
    $('#ed-titol').textContent = nova ? T_('Nova regla') : T_('Edita la regla');
    $('#ed-esborra').hidden = nova;
    $('#r-nom').value = r.nom || '';
    $('#r-paraules').value = llista(r.paraules).join(', ');
    $('#r-exclou').value = llista(r.exclou).join(', ');
    $('#r-de').value = r.de || ''; $('#r-a').value = r.a || '';
    $('#r-activa').checked = r.activa !== false;
    $('#r-dies').innerHTML = DIES.map((d, i) => `<button type="button" class="xip ${llista(r.dies).includes(String(i + 1)) ? 'activa' : ''}" data-d="${i + 1}">${T_(d)}</button>`).join('');
    $('#r-accions').innerHTML = Object.entries(ACCIONS).map(([k, a]) =>
      `<button type="button" class="accio ${k === r.accio ? 'activa' : ''}" data-a="${k}"><span class="ic">${a.ic}</span>${T_(a.nom)}</button>`).join('');
    pintaParams(); pintaApps();
    $('#r-apps-cerca').value = ''; $('#r-apps-llista').innerHTML = '';
    $('#r-prova-res').innerHTML = '';
    $('#ed-duplica').hidden = nova;
    ed.showModal();
  }
  function pintaParams() {
    const r = edRegla, a = ACCIONS[r.accio];
    const v = (k, d) => esc(r[k] ?? d);
    let h = `<p class="ajuda">${a.ic} ${esc(T_(a.desc))}</p>`;
    if (a.control && !ajustos.control) h += `<p class="ajuda">⚠️ ${T_('Cal activar <b>Control del so</b> a Ajustos perquè funcioni.')}</p>`;
    if (r.accio === 'refreda') h += `<label class="camp">${T_('Segons de calma després de cada missatge')} <input type="number" id="p-segons" min="5" max="3600" value="${v('segons', 120)}"></label>`;
    if (r.accio === 'alerta' || r.accio === 'alarma') {
      h += `<label class="camp">${T_('So')} <div class="dues"><input id="p-soNom" readonly value="${r.soNom ? v('soNom', '') : esc(T_(r.accio === 'alarma' ? 'Alarma per defecte' : "El de l'app"))}"><button type="button" id="p-tria-so">${T_('Tria…')}</button></div></label>`;
      if (r.accio === 'alerta') {
        h += `<label class="camp">${T_('Vibració')} <select id="p-vibracio">${Object.entries(VIBRACIONS).map(([k, n]) => `<option value="${k}" ${k === (r.vibracio || 'defecte') ? 'selected' : ''}>${esc(T_(n))}</option>`).join('')}</select></label>`;
        if (!ajustos.control) h += `<p class="ajuda">${T_("Sense <b>Control del so</b>, aquesta alerta sonarà a més del so normal de l'app.")}</p>`;
      }
    }
    if (r.accio === 'respon') {
      h += `<label class="camp">${T_('Al cap de (minuts)')} <input type="number" id="p-minuts" min="1" max="240" value="${v('minuts', 10)}"></label>`;
      h += `<label class="camp">${T_('Resposta')} <input id="p-text" maxlength="300" value="${v('text', '')}" placeholder="${esc(T_('Ara no puc, et dic alguna cosa després.'))}"></label>`;
      h += `<p class="ajuda">${T_('Només funciona amb apps que tenen el botó <b>Respon</b> a la notificació (WhatsApp, Telegram, Missatges...).')}</p>`;
    }
    if (r.accio === 'recorda') {
      h += `<div class="dues"><label class="camp">${T_('Cada (minuts)')} <input type="number" id="p-minuts" min="1" max="120" value="${v('minuts', 5)}"></label>`;
      h += `<label class="camp">${T_('Com a molt (vegades)')} <input type="number" id="p-vegades" min="1" max="30" value="${v('vegades', 6)}"></label></div>`;
    }
    if (r.accio === 'posposa') h += `<label class="camp">${T_('Hores de lliurament')} <input id="p-franges" value="${v('franges', '09:00, 13:00, 20:00')}" placeholder="09:00, 13:00, 20:00"></label>`;
    $('#r-params').innerHTML = h;
    const tria = $('#p-tria-so');
    if (tria) tria.addEventListener('click', async () => {
      if (!natiu) return toast(T_("Triar so només funciona a l'APK"));
      const s = await N.triaSo({ tipus: r.accio === 'alarma' ? 'alarma' : 'notificacio', actual: r.so || '' });
      if (s.uri) { r.so = s.uri; r.soNom = s.nom; $('#p-soNom').value = s.nom; }
    });
  }
  function llegeixParams() {
    const r = edRegla;
    const n = (id) => { const el = $(id); return el ? +el.value : undefined; };
    const t = (id) => { const el = $(id); return el ? el.value.trim() : undefined; };
    if (r.accio === 'refreda') r.segons = n('#p-segons') || 120;
    if (r.accio === 'alerta') r.vibracio = t('#p-vibracio');
    if (r.accio === 'respon') { r.minuts = n('#p-minuts') || 10; r.text = t('#p-text'); }
    if (r.accio === 'recorda') { r.minuts = n('#p-minuts') || 5; r.vegades = n('#p-vegades') || 6; }
    if (r.accio === 'posposa') r.franges = t('#p-franges');
  }
  function pintaApps() {
    $('#r-apps-triades').innerHTML = edApps.map((p) => `<button type="button" class="xip activa" data-p="${esc(p)}">${esc(nomsApps[p] || p)} ✕</button>`).join('')
      || `<small>${T_('Totes les apps')}</small>`;
  }
  $('#r-accions').addEventListener('click', (ev) => {
    const b = ev.target.closest('.accio'); if (!b) return;
    llegeixParams();
    edRegla.accio = b.dataset.a;
    $$('.accio', ed).forEach((x) => x.classList.toggle('activa', x === b));
    pintaParams();
  });
  $('#r-dies').addEventListener('click', (ev) => { const b = ev.target.closest('.xip'); if (b) b.classList.toggle('activa'); });
  $('#r-apps-triades').addEventListener('click', (ev) => {
    const b = ev.target.closest('.xip'); if (!b) return;
    edApps = edApps.filter((p) => p !== b.dataset.p); pintaApps();
  });
  $('#r-apps-cerca').addEventListener('input', (ev) => {
    const q = normalitza(ev.target.value);
    const ll = q ? apps.filter((a) => !edApps.includes(a.paquet) && (normalitza(a.nom).includes(q) || a.paquet.includes(q))).slice(0, 30) : [];
    $('#r-apps-llista').innerHTML = ll.map((a) => `<li data-p="${esc(a.paquet)}">${esc(a.nom)}<small>${esc(a.paquet)}</small></li>`).join('');
  });
  $('#r-apps-llista').addEventListener('click', (ev) => {
    const li = ev.target.closest('li'); if (!li) return;
    edApps.push(li.dataset.p); pintaApps();
    $('#r-apps-cerca').value = ''; $('#r-apps-llista').innerHTML = '';
  });
  $('#ed-tanca').addEventListener('click', () => ed.close());
  $('#ed-esborra').addEventListener('click', async () => {
    if (edIndex < 0 || !confirm(T_('Vols esborrar la regla «{n}»?', { n: edRegla.nom }))) return;
    regles.splice(edIndex, 1); await desa(); pintaRegles(); ed.close(); toast(T_('Regla esborrada'));
  });
  $('#ed-prova').addEventListener('click', async () => {
    llegeixParams();
    if (!natiu) return toast(T_("Les proves de so només funcionen a l'APK"));
    await N.prova({ accio: edRegla.accio, so: edRegla.so || 'defecte', vibracio: edRegla.vibracio || 'defecte' });
    if (edRegla.accio === 'alarma') setTimeout(() => N.aturaAlarma(), 5000);
  });
  /** Passa el que hi ha al formulari a edRegla (sense desar). */
  function llegeixForm() {
    llegeixParams();
    const r = edRegla;
    r.nom = $('#r-nom').value.trim() || T_(ACCIONS[r.accio].nom);
    r.apps = [...edApps];
    r.paraules = llista($('#r-paraules').value);
    r.exclou = llista($('#r-exclou').value);
    r.de = $('#r-de').value; r.a = $('#r-a').value;
    r.dies = $$('#r-dies .xip.activa').map((b) => b.dataset.d);
    r.activa = $('#r-activa').checked;
    return r;
  }
  $('#ed-prova-hist').addEventListener('click', async () => {
    const r = llegeixForm();
    if (!historial.length) await carregaHistorial();
    const t = provaRegla(r, historial);
    const dies = historial.length ? Math.max(1, Math.ceil((Date.now() - Math.min(...historial.map((h) => h.t))) / 86400000)) : 0;
    $('#r-prova-res').innerHTML = !historial.length
      ? `<p class="ajuda">${T_("L'historial és buit: quan arribin notificacions, aquí veuràs quines hauria atrapat.")}</p>`
      : `<p class="ajuda">${T_(dies === 1 ? "De l'últim dia, n'hauria atrapat <b>{n}</b> de {total}." : "Dels últims {d} dies, n'hauria atrapat <b>{n}</b> de {total}.", { d: dies, n: t.length, total: historial.length })}</p>`
        + (t.length ? '<ul class="mini">' + t.slice(0, 6).map((h) => `<li><b>${esc(h.titol || h.app)}</b> <small>${esc(h.app)} · ${hora(h.t)} · ${esc(String(h.text || '').slice(0, 80))}</small></li>`).join('') + '</ul>' : '');
  });
  $('#ed-duplica').addEventListener('click', () => {
    const r = structuredClone(llegeixForm());
    delete r.id; r.nom = (r.nom + T_(' (còpia)')).slice(0, 60);
    ed.close();
    obreEditor(r, true);
  });
  $('#f-regla').addEventListener('submit', async (ev) => {
    ev.preventDefault();
    const r = llegeixForm();
    if (r.accio === 'posposa' && finsProperaFranja(r.franges) < 0) return toast(T_('Posa almenys una hora vàlida (HH:MM)'));
    if (r.accio === 'respon' && !r.text) return toast(T_('Escriu la resposta'));
    if (!r.id) r.id = 'r' + Date.now().toString(36);
    if (edIndex >= 0) regles[edIndex] = r; else regles.push(r);
    await desa(); pintaRegles(); ed.close();
    toast(T_('Regla desada'));
    if (!$('#v-regles').classList.contains('activa')) mostra('regles');
  });

  // ---------------- Historial ----------------
  function hora(t) {
    const d = new Date(t), avui = new Date();
    const h = d.toLocaleTimeString(LOC(), { hour: '2-digit', minute: '2-digit' });
    return d.toDateString() === avui.toDateString() ? h : d.toLocaleDateString(LOC(), { day: 'numeric', month: 'short' }) + ' ' + h;
  }
  /** L'acció de l'historial la desa Android en català: es tradueix en pintar-la. */
  function accioHist(a) {
    const m = /^posposada fins (.+)$/.exec(a);
    return m ? T_('posposada fins {h}', { h: m[1] }) : T_(a);
  }
  function pintaHistorial() {
    const q = normalitza($('#cerca').value);
    const avui = new Date(); avui.setHours(0, 0, 0, 0);
    const fa = $('#filtre-app');
    fa.hidden = !filtreApp;
    if (filtreApp) fa.textContent = (nomsApps[filtreApp] || filtreApp) + ' ✕';
    const ll = historial.filter((h) => (!q || normalitza(`${h.app} ${h.titol} ${h.text}`).includes(q))
      && (!filtreApp || h.paquet === filtreApp)
      && (filtreHist !== 'filtrades' || h.accio) && (filtreHist !== 'avui' || h.t >= avui.getTime())).slice(0, 300);
    $('#sense-historial').hidden = ll.length > 0;
    $('#llista-historial').innerHTML = ll.map((h) => `
      <li data-id="${esc(h.id)}">
        <div class="cos"><b>${esc(h.titol || h.app)}</b><small>${esc(h.app)} · ${esc(h.text)}</small>
          ${h.accio ? `<span class="etiqueta">${esc(accioHist(h.accio))}</span> ` : ''}${h.resposta ? `<span class="etiqueta">${T_('resposta enviada')}</span>` : ''}</div>
        <div style="text-align:right"><div class="hora">${hora(h.t)}</div><button class="discret" data-r="1">${T_('Restaura')}</button></div>
      </li>`).join('');
  }
  $('#cerca').addEventListener('input', pintaHistorial);
  $('#filtre-historial').addEventListener('click', (ev) => {
    const b = ev.target.closest('.xip'); if (!b) return;
    if (b.id === 'filtre-app') { filtreApp = ''; pintaHistorial(); return; }
    filtreHist = b.dataset.f;
    $$('#filtre-historial .xip[data-f]').forEach((x) => x.classList.toggle('activa', x === b));
    pintaHistorial();
  });
  $('#llista-historial').addEventListener('click', async (ev) => {
    if (!ev.target.closest('button[data-r]')) return;
    const id = ev.target.closest('li').dataset.id;
    if (!natiu) return toast(T_("Restaurar només funciona a l'APK"));
    const r = await N.restaura({ id });
    toast(r.ok ? T_('Tornada a posar a les notificacions') : T_("No s'ha trobat"));
  });
  $('#esborra-historial').addEventListener('click', async () => {
    if (!confirm(T_("Vols esborrar tot l'historial? No es pot desfer."))) return;
    if (natiu) await N.esborraHistorial();
    historial = []; pintaHistorial(); toast(T_('Historial esborrat'));
  });

  // ---------------- Ajustos ----------------
  function pintaAjustos() {
    for (const k of Object.keys(AJ_DEF)) {
      const el = $('#a-' + k); if (!el) continue;
      if (el.type === 'checkbox') el.checked = !!ajustos[k]; else el.value = ajustos[k] ?? '';
    }
    $('#versio').textContent = 'v' + VERSIO + (natiu ? '' : T_(' (web)'));
    $$('.versio').forEach((el) => { el.textContent = 'v' + VERSIO; });
  }

  // ---------------- Còpia de seguretat ----------------
  $('#exporta').addEventListener('click', async () => {
    const d = new Date();
    const nom = `xoriguer-notificacions-${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}.json`;
    const text = JSON.stringify({ app: 'xoriguer-notificacions', versio: VERSIO, data: d.toISOString(), regles, ajustos }, null, 2);
    if (natiu) {
      try { const r = await N.exporta({ text, nom }); if (r.ok) toast(T_('Còpia desada')); } catch (e) { toast(e.message || T_("No s'ha pogut desar")); }
    } else {
      const a = document.createElement('a');
      a.href = URL.createObjectURL(new Blob([text], { type: 'application/json' })); a.download = nom;
      document.body.appendChild(a); a.click(); a.remove();
      setTimeout(() => URL.revokeObjectURL(a.href), 5000);
    }
  });
  async function recupera(text) {
    let c;
    try { c = llegeixCopia(text); } catch (e) { return toast(e.message); }
    const substitueix = !regles.length || confirm(T_("El fitxer té {n} regles.\n\nD'acord = SUBSTITUEIX les teves {m} regles.\nCancel·la = AFEGEIX-les a les que tens.", { n: c.regles.length, m: regles.length }));
    if (substitueix) regles = c.regles;
    else {
      const ids = new Set(regles.map((r) => r.id));
      regles = regles.concat(c.regles.map((r) => (r.id && ids.has(r.id) ? { ...r, id: 'r' + Date.now().toString(36) + Math.random().toString(36).slice(2, 5) } : r)));
    }
    if (c.ajustos && substitueix) ajustos = { ...AJ_DEF, ...c.ajustos };
    await desa(); pintaRegles(); pintaAjustos();
    toast(c.regles.length === 1 ? T_('Recuperada 1 regla') : T_('Recuperades {n} regles', { n: c.regles.length }));
  }
  $('#importa').addEventListener('click', async () => {
    if (!natiu) return $('#importa-fitxer').click();
    try { const r = await N.importa(); if (r.text) await recupera(r.text); } catch (e) { toast(e.message || T_("No s'ha pogut llegir")); }
  });
  $('#importa-fitxer').addEventListener('change', async (ev) => {
    const f = ev.target.files && ev.target.files[0]; ev.target.value = '';
    if (f) await recupera(await f.text());
  });

  // ---------------- Versió nova ----------------
  async function obreWeb(url) {
    if (natiu) { try { await N.obreWeb({ url }); } catch { toast(T_("No s'ha pogut obrir el navegador")); } }
    else window.open(url, '_blank', 'noopener');
  }
  // app d'Android: s'actualitza sense reinstal·lar (xoriguer-actualitza.js)
  $('#versio-nova').addEventListener('click', () => (window.XActualitza?.natiu ? window.XActualitza.comprova(true) : obreWeb(WEB_VERSIONS)));
  window.XActualitza?.inicia({ app: 'notificacions', versio: VERSIO });
  $('#g-baixa').addEventListener('click', () => obreWeb(WEB_DESCARREGUES));
  $('#v-ajustos').addEventListener('change', async (ev) => {
    const el = ev.target; if (!el.id || !el.id.startsWith('a-')) return;
    const k = el.id.slice(2);
    if (k === 'idioma') {
      ajustos.idioma = el.value; ajustos.llengua = I18N.posa(el.value);
      await desa(); ls.set('idioma', el.value); location.reload(); return;
    }
    ajustos[k] = el.type === 'checkbox' ? el.checked : Math.max(+el.min || 0, Math.min(+el.max || 1e9, +el.value || 0));
    await desa();
    toast(k === 'control' ? (el.checked ? T_("Ara el so el fa l'app") : T_('El so torna a ser del sistema')) : T_('Desat'));
  });

  // ---------------- Inici de l'app ----------------
  (async () => {
    await carrega();
    // l'idioma desat a l'app mana; Android el llegeix d'«llengua»
    if ((ajustos.idioma || '') !== idiomaDesat) {
      ls.set('idioma', ajustos.idioma || '');
      if (ls.get('idioma', null) === (ajustos.idioma || '')) { location.reload(); return; }
    }
    if (ajustos.llengua !== I18N.llengua) { ajustos.llengua = I18N.llengua; await desa(); }
    pintaPlantilles(); pintaRegles(); pintaAjustos();
    await refrescaInici();
  })();
  if (!natiu && 'serviceWorker' in navigator && location.protocol.startsWith('http')) {
    navigator.serviceWorker.register('sw.js').catch(() => {});
  }
})();
