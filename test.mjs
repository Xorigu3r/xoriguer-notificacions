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
console.log(`\n${n} proves correctes`);
