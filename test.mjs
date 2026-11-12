// Proves de la lògica de regles (la mateixa que Motor.java).  npm test
import { createRequire } from 'node:module';
import assert from 'node:assert/strict';
const require = createRequire(import.meta.url);
const L = require('./www/app.js');
let n = 0;
const prova = (nom, f) => { f(); n++; console.log('✓', nom); };
const dl10 = new Date(2026, 10, 9, 10, 0);   // dilluns 10:00 (9/11/2026)
const dg23 = new Date(2026, 10, 15, 23, 30); // diumenge 23:30

prova('sense condicions coincideix amb tot', () => assert.ok(L.coincideix({}, 'a', 'x', 'y', dl10)));
prova('regla inactiva no coincideix', () => assert.ok(!L.coincideix({ activa: false }, 'a', '', '', dl10)));
prova('filtre per app', () => {
  assert.ok(L.coincideix({ apps: ['com.whatsapp'] }, 'com.whatsapp', '', '', dl10));
  assert.ok(!L.coincideix({ apps: ['com.whatsapp'] }, 'org.telegram', '', '', dl10));
});
prova('paraules sense accents ni majúscules', () => {
  assert.ok(L.coincideix({ paraules: ['promocio'] }, 'a', 'PROMOCIÓ!', '', dl10));
  assert.ok(L.coincideix({ paraules: 'maria, joan' }, 'a', 'Joan', 'hola', dl10));
  assert.ok(!L.coincideix({ paraules: ['maria'] }, 'a', 'Pere', 'hola', dl10));
});
prova('paraules excloses', () => assert.ok(!L.coincideix({ exclou: ['publicitat'] }, 'a', '', 'Publicitat', dl10)));
prova('horari normal', () => {
  assert.ok(L.coincideix({ de: '09:00', a: '17:00' }, 'a', '', '', dl10));
  assert.ok(!L.coincideix({ de: '11:00', a: '17:00' }, 'a', '', '', dl10));
});
prova('horari que passa per mitjanit', () => {
  assert.ok(L.coincideix({ de: '22:00', a: '07:00' }, 'a', '', '', dg23));
  assert.ok(!L.coincideix({ de: '22:00', a: '07:00' }, 'a', '', '', dl10));
});
prova('dies de la setmana (1 = dilluns)', () => {
  assert.ok(L.coincideix({ dies: ['1'] }, 'a', '', '', dl10));
  assert.ok(!L.coincideix({ dies: ['1', '2'] }, 'a', '', '', dg23));
  assert.ok(L.coincideix({ dies: ['7'] }, 'a', '', '', dg23));
});
prova('avalua s\'atura a ignorar', () => {
  const r = [{ nom: 'a', accio: 'alerta' }, { nom: 'b', accio: 'ignora' }, { nom: 'c', accio: 'recorda' }];
  assert.deepEqual(L.avalua(r, 'p', '', '', dl10).map((x) => x.nom), ['a', 'b']);
});
prova('pròxima franja de posposar', () => {
  assert.equal(L.finsProperaFranja('13:00, 20:00', dl10), 3 * 3600000);
  assert.equal(L.finsProperaFranja('09:00', dl10), 23 * 3600000);
  assert.equal(L.finsProperaFranja('hola', dl10), -1);
});
prova('resum de regla', () => assert.match(L.resumRegla({ accio: 'refreda', segons: 90, apps: ['com.whatsapp'] }, { 'com.whatsapp': 'WhatsApp' }), /WhatsApp · 90 s/));
prova('plantilles amb acció vàlida', () => L.PLANTILLES.forEach((p) => assert.ok(L.ACCIONS[p.accio])));
const H = [
  { t: dl10.getTime(), paquet: 'com.whatsapp', app: 'WhatsApp', titol: 'Maria', text: 'Hola', regles: 'Calma, Maria' },
  { t: dl10.getTime() + 60000, paquet: 'com.whatsapp', app: 'WhatsApp', titol: 'Joan', text: 'Ei', accio: 'silenciada', regles: 'Calma' },
  { t: dl10.getTime() + 120000, paquet: 'com.instagram.android', app: 'Instagram', titol: 'Instagram', text: 'Like' },
  { t: dl10.getTime() - 10 * 86400000, paquet: 'com.vell', app: 'Vella', titol: '', text: '' },
];
const araH = dl10.getTime() + 3600000;
prova('estadística: compta per app, filtrades, i només 7 dies', () => {
  const e = L.estadistica(H, 7, araH);
  assert.deepEqual(e.map((x) => [x.paquet, x.n, x.filtrades]), [['com.whatsapp', 2, 1], ['com.instagram.android', 1, 0]]);
});
prova("prova d'una regla amb l'historial (fins i tot si està inactiva)", () => {
  assert.equal(L.provaRegla({ apps: ['com.whatsapp'], paraules: ['maria'], activa: false }, H).length, 1);
  assert.equal(L.provaRegla({ de: '09:00', a: '09:30' }, H).length, 0); // les de les 10:00 no hi entren
});
prova('vegades aplicada (nom exacte, 7 dies)', () => {
  assert.equal(L.vegadesAplicada({ nom: 'Calma' }, H, 7, araH), 2);
  assert.equal(L.vegadesAplicada({ nom: 'Mar' }, H, 7, araH), 0);
});
prova('còpia de seguretat: accepta la nostra, una llista sola, i rebutja brossa', () => {
  const c = L.llegeixCopia(JSON.stringify({ regles: [{ accio: 'ignora', nom: 'x' }, { accio: 'inventada' }], ajustos: { control: true } }));
  assert.equal(c.regles.length, 1); assert.equal(c.ajustos.control, true);
  assert.equal(L.llegeixCopia('[{"accio":"silencia"}]').regles[0].nom, 'Silenciar');
  assert.throws(() => L.llegeixCopia('hola'), /no és una còpia/);
  assert.throws(() => L.llegeixCopia('{"regles":[{"accio":"res"}]}'), /Cap regla vàlida/);
});
console.log(`\n${n} proves correctes`);
