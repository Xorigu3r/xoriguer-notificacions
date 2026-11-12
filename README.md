# Xoriguer Notificacions

Gestor de notificacions per a Android: mostra les que vols quan les vols i filtra la resta.

- **Ignorar**: elimina les notificacions que no vols veure, sense amagar tota l'app.
- **Refredament**: si et van escrivint seguit, només sona el primer missatge.
- **Alerta pròpia**: so o vibració especial per a una persona o una frase.
- **Respondre**: respon sol si no has vist el missatge al cap d'un temps.
- **Recorda-m'ho**: torna a sonar fins que la vegis.
- **Posposar**: te les dona en lots a les hores que triïs.
- **Alarma**: sona com una alarma (per exemple, la càmera de casa).
- **Secret**: amaga el contingut; només diu de quina app és.
- **Desfer** i **historial**: recupera una notificació descartada sense voler.

**Privadesa:** l'app no té permís d'Internet. Les regles i l'historial no surten mai del mòbil.

## Instal·lar
Baixa `XoriguerNotificacions.apk` de les [Releases](https://github.com/Xorigu3r/xoriguer-notificacions/releases),
instal·la-la i activa **Accés a les notificacions** des de la pantalla d'Inici de l'app.

La [versió web](https://xorigu3r.github.io/xoriguer-notificacions/) només serveix per veure l'app i preparar regles:
una web no pot llegir les notificacions de les altres apps.

## Construir
`npm install` i `npm run apk` (Capacitor 8, JDK 21 i SDK d'Android a `~/android-tools`). Proves: `npm test`.

Família Xoriguer · https://xorigu3r.github.io/
