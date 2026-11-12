package cat.xoriguer.notificacions;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * El cor de l'app: Android li passa totes les notificacions i aquí s'apliquen les regles.
 *
 * "Control del so" (ajust `control`): demana al sistema que no faci sonar ni vibrar cap notificació
 * (HINT_HOST_DISABLE_NOTIFICATION_EFFECTS) i el so el fa aquesta app, segons el canal de cada app i les regles.
 * Això és el que permet el refredament i les alertes pròpies. Si el servei s'atura, el sistema torna
 * a sonar sol (el "hint" desapareix): mai et quedes sense avisos. Les trucades no es toquen.
 */
public class ServeiNotificacions extends NotificationListenerService {
    static ServeiNotificacions actiu;

    /** Notificacions recents a memòria (per restaurar-les amb la seva acció original). */
    static final class Desat {
        int id; String clau, paquet, titol, text; PendingIntent obrir; Notification n; boolean ongoing;
    }

    private static final Map<Integer, Desat> perId = new LinkedHashMap<Integer, Desat>(64, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Integer, Desat> e) { return size() > 300; }
    };
    private static final Map<String, Integer> idPerClau = new HashMap<>();
    private static int seguent = (int) ((System.currentTimeMillis() / 1000) % 100000) * 2 + 10000;

    private final Map<String, String> darreraFirma = new HashMap<>();
    private final Map<String, Long> ultimaAlerta = new HashMap<>();
    private final Map<String, Long> posposades = new HashMap<>();
    private final Map<String, Runnable> temporitzadors = new HashMap<>();
    private String clauAlarma;
    private final Handler h = new Handler(Looper.getMainLooper());

    @Override
    public void onListenerConnected() {
        actiu = this;
        Alertes.canals(this);
        aplicaControl();
    }

    @Override
    public void onListenerDisconnected() {
        if (actiu == this) actiu = null;
    }

    @Override
    public void onDestroy() {
        if (actiu == this) actiu = null;
        super.onDestroy();
    }

    void aplicaControl() {
        boolean control = Magatzem.ajustos(this).optBoolean("control", false);
        try { requestListenerHints(control ? HINT_HOST_DISABLE_NOTIFICATION_EFFECTS : 0); } catch (Exception ignored) {}
    }

    static void actualitza() {
        if (actiu != null) actiu.aplicaControl();
    }

    static boolean connectat() { return actiu != null; }

    // ------------------------------------------------------------------
    static String cadena(Bundle e, String k) {
        if (e == null) return "";
        CharSequence cs = e.getCharSequence(k);
        return cs == null ? "" : cs.toString();
    }

    /** Què faria el sistema amb aquesta notificació (so, vibració), segons el seu canal. */
    static final class Efecte { boolean alerta; Uri so; long[] patro; boolean vibra; }

    @SuppressWarnings("deprecation")
    Efecte efecte(StatusBarNotification sbn, RankingMap rm) {
        Efecte ef = new Efecte();
        Notification n = sbn.getNotification();
        boolean resum = (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0;
        if (Build.VERSION.SDK_INT >= 26) {
            int gab = n.getGroupAlertBehavior();
            if (n.getGroup() != null && ((resum && gab == Notification.GROUP_ALERT_CHILDREN) || (!resum && gab == Notification.GROUP_ALERT_SUMMARY))) return ef;
            Ranking r = new Ranking();
            if (rm != null && rm.getRanking(sbn.getKey(), r)) {
                if (r.getImportance() < NotificationManager.IMPORTANCE_DEFAULT || !r.matchesInterruptionFilter()) return ef;
                NotificationChannel ch = r.getChannel();
                if (ch != null) {
                    ef.so = ch.getSound();
                    ef.vibra = ch.shouldVibrate();
                    ef.patro = ch.getVibrationPattern();
                }
                ef.alerta = ef.so != null || ef.vibra;
            }
        } else {
            ef.so = n.sound != null ? n.sound : ((n.defaults & Notification.DEFAULT_SOUND) != 0 ? android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION) : null);
            ef.vibra = n.vibrate != null || (n.defaults & Notification.DEFAULT_VIBRATE) != 0;
            ef.patro = n.vibrate;
            ef.alerta = ef.so != null || ef.vibra;
        }
        return ef;
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) { onNotificationPosted(sbn, getCurrentRanking()); }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn, RankingMap rm) {
        try { processa(sbn, rm); } catch (Exception e) { android.util.Log.e("XoriguerNotif", "processa", e); }
    }

    private void processa(StatusBarNotification sbn, RankingMap rm) {
        if (getPackageName().equals(sbn.getPackageName())) return;
        Notification n = sbn.getNotification();
        String clau = sbn.getKey();
        String paquet = sbn.getPackageName();
        Bundle ex = n.extras;
        String titol = cadena(ex, Notification.EXTRA_TITLE);
        String text = cadena(ex, Notification.EXTRA_BIG_TEXT);
        if (text.isEmpty()) text = cadena(ex, Notification.EXTRA_TEXT);
        boolean ongoing = sbn.isOngoing() || (n.flags & Notification.FLAG_FOREGROUND_SERVICE) != 0;
        boolean resum = (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0;
        long ara = System.currentTimeMillis();
        JSONObject aj = Magatzem.ajustos(this);
        boolean control = aj.optBoolean("control", false);

        String firma = titol + "\u0001" + text;
        boolean repetida = firma.equals(darreraFirma.put(clau, firma));
        boolean nomesUnCop = (n.flags & Notification.FLAG_ONLY_ALERT_ONCE) != 0 && idPerClau.containsKey(clau);
        boolean actualitzacio = repetida || nomesUnCop;
        Long fins = posposades.get(clau);
        boolean tornaDePosposar = fins != null && ara >= fins - 60_000L;
        if (tornaDePosposar) posposades.remove(clau);
        Efecte ef = efecte(sbn, rm);

        // Notificacions fixes (música, GPS...) i resums de grup: no s'hi apliquen regles.
        if (ongoing || resum) {
            if (control && !actualitzacio && ef.alerta && !ongoing) Alertes.alerta(this, ef.so != null ? ef.so.toString() : "cap", null, ef.so, ef.patro, ef.vibra);
            return;
        }

        // Desa a memòria (restaurar / desfer)
        Integer idv = idPerClau.get(clau);
        int id = idv != null ? idv : (seguent += 2);
        idPerClau.put(clau, id);
        Desat d = new Desat();
        d.id = id; d.clau = clau; d.paquet = paquet; d.titol = titol; d.text = text; d.obrir = n.contentIntent; d.n = n;
        synchronized (perId) { perId.put(id, d); }

        if (actualitzacio) return; // la mateixa notificació actualitzada: ni regles ni so

        Calendar cal = Calendar.getInstance();
        JSONArray regles = Magatzem.regles(this);
        boolean silenci = false, personalitzada = false;
        String so = null; long[] patro = null;
        StringBuilder aplicades = new StringBuilder();
        String accioFinal = null;

        for (int i = 0; i < regles.length(); i++) {
            JSONObject r = regles.optJSONObject(i);
            if (r == null || !Motor.coincideix(r, paquet, titol, text, cal)) continue;
            String accio = r.optString("accio", "");
            if (aplicades.length() > 0) aplicades.append(", ");
            aplicades.append(r.optString("nom", accio));
            switch (accio) {
                case "ignora":
                    cancelNotification(clau);
                    historial(id, paquet, titol, text, "ignorada", aplicades.toString());
                    return;
                case "secret":
                    cancelNotification(clau);
                    Alertes.copia(this, id, paquet, titol, text, n.contentIntent, true);
                    // la còpia és muda: amb control del so, sona com hauria sonat l'original
                    if (control && ef.alerta) Alertes.alerta(this, ef.so != null ? ef.so.toString() : "cap", null, ef.so, ef.patro, ef.vibra);
                    historial(id, paquet, titol, text, "amagada", aplicades.toString());
                    return;
                case "posposa":
                    if (!tornaDePosposar && Build.VERSION.SDK_INT >= 26) {
                        long ms = Motor.finsProperaFranja(r.optString("franges", ""), cal);
                        if (ms > 0) {
                            snoozeNotification(clau, ms);
                            posposades.put(clau, ara + ms);
                            String hora = new SimpleDateFormat("HH:mm", Locale.ROOT).format(ara + ms);
                            historial(id, paquet, titol, text, "posposada fins " + hora, aplicades.toString());
                            return;
                        }
                    }
                    break;
                case "refreda": {
                    String k = paquet + "|" + titol;
                    Long ult = ultimaAlerta.get(k);
                    if (ult != null && ara - ult < r.optInt("segons", 60) * 1000L) { silenci = true; accioFinal = "silenciada (refredament)"; }
                    ultimaAlerta.put(k, ara);
                    break;
                }
                case "alerta":
                    personalitzada = true;
                    so = r.optString("so", "defecte");
                    patro = Motor.patro(r.optString("vibracio", "defecte"));
                    break;
                case "alarma":
                    Alertes.alarma(this, titol.isEmpty() ? Alertes.nomApp(this, paquet) : titol, text, r.optString("so", ""));
                    clauAlarma = clau;
                    silenci = true;
                    accioFinal = "alarma";
                    break;
                case "recorda":
                    programaRecordatori(clau, Math.max(1, r.optInt("minuts", 5)), r.optInt("vegades", 6), ef);
                    break;
                case "respon":
                    programaResposta(clau, Math.max(1, r.optInt("minuts", 10)), r.optString("text", ""), id);
                    break;
                default:
                    break;
            }
        }

        // Refredament general (totes les apps)
        int rg = aj.optInt("refredaGlobal", 0);
        if (rg > 0 && !silenci) {
            String k = "*" + paquet + "|" + titol;
            Long ult = ultimaAlerta.get(k);
            if (ult != null && ara - ult < rg * 1000L) { silenci = true; if (accioFinal == null) accioFinal = "silenciada (refredament)"; }
            ultimaAlerta.put(k, ara);
        }

        if (control) {
            if (!silenci && (ef.alerta || personalitzada)) {
                Alertes.alerta(this, personalitzada ? so : (ef.so != null ? ef.so.toString() : "cap"), patro, ef.so, ef.patro, ef.vibra);
            }
        } else if (personalitzada && !silenci) {
            Alertes.alerta(this, so, patro, null, null, false); // s'hi suma al so de l'app
        }
        historial(id, paquet, titol, text, accioFinal, aplicades.toString());
    }

    private void historial(int id, String paquet, String titol, String text, String accio, String regles) {
        if (!Magatzem.ajustos(this).optBoolean("historial", true)) return;
        try {
            JSONObject o = new JSONObject();
            o.put("id", String.valueOf(id));
            o.put("t", System.currentTimeMillis());
            o.put("paquet", paquet);
            o.put("app", Alertes.nomApp(this, paquet));
            o.put("titol", titol);
            o.put("text", text.length() > 1000 ? text.substring(0, 1000) : text);
            if (accio != null) o.put("accio", accio);
            if (regles != null && !regles.isEmpty()) o.put("regles", regles);
            Magatzem.afegeix(this, o);
        } catch (Exception ignored) {}
    }

    // ---------------- Recordatoris i respostes automàtiques ----------------
    private boolean activa(String clau) {
        try {
            StatusBarNotification[] act = getActiveNotifications();
            if (act != null) for (StatusBarNotification s : act) if (clau.equals(s.getKey())) return true;
        } catch (Exception ignored) {}
        return false;
    }

    private void programa(String clau, String tipus, Runnable r, long ms) {
        Runnable vell = temporitzadors.put(clau + "#" + tipus, r);
        if (vell != null) h.removeCallbacks(vell);
        h.postDelayed(r, ms);
    }

    private void programaRecordatori(String clau, int minuts, int vegades, Efecte ef) {
        final int[] queden = {Math.max(1, Math.min(vegades, 30))};
        Runnable r = new Runnable() {
            @Override public void run() {
                if (!activa(clau)) { temporitzadors.remove(clau + "#rec"); return; }
                Alertes.alerta(ServeiNotificacions.this, ef.so != null ? ef.so.toString() : "defecte", null, ef.so, ef.patro, true);
                if (--queden[0] > 0) h.postDelayed(this, minuts * 60_000L); else temporitzadors.remove(clau + "#rec");
            }
        };
        programa(clau, "rec", r, minuts * 60_000L);
    }

    private void programaResposta(String clau, int minuts, String text, int id) {
        if (text.isEmpty()) return;
        programa(clau, "resp", () -> {
            temporitzadors.remove(clau + "#resp");
            if (!activa(clau)) return;
            Desat d;
            synchronized (perId) { d = perId.get(id); }
            if (d != null && respon(d.n, text)) Magatzem.marca(this, String.valueOf(id), "resposta", text);
        }, minuts * 60_000L);
    }

    /** Respon amb l'acció "Respon" de la notificació (WhatsApp, Telegram, SMS...). */
    boolean respon(Notification n, String text) {
        Notification.Action triada = null;
        if (n.actions != null) for (Notification.Action a : n.actions) {
            if (a.getRemoteInputs() != null && a.getRemoteInputs().length > 0) {
                triada = a;
                if (Build.VERSION.SDK_INT >= 28 && a.getSemanticAction() == Notification.Action.SEMANTIC_ACTION_REPLY) break;
            }
        }
        if (triada == null) {
            for (Notification.Action a : new Notification.WearableExtender(n).getActions())
                if (a.getRemoteInputs() != null && a.getRemoteInputs().length > 0) { triada = a; break; }
        }
        if (triada == null) return false;
        try {
            Intent i = new Intent();
            Bundle b = new Bundle();
            for (RemoteInput ri : triada.getRemoteInputs()) b.putCharSequence(ri.getResultKey(), text);
            RemoteInput.addResultsToIntent(triada.getRemoteInputs(), i, b);
            triada.actionIntent.send(this, 0, i);
            return true;
        } catch (Exception e) { return false; }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn, RankingMap rm, int reason) {
        String clau = sbn.getKey();
        for (String t : new String[]{"#rec", "#resp"}) {
            Runnable r = temporitzadors.remove(clau + t);
            if (r != null) h.removeCallbacks(r);
        }
        if (clau.equals(clauAlarma)) { Alertes.aturaAlarma(this); clauAlarma = null; }
        if (getPackageName().equals(sbn.getPackageName()) || sbn.isOngoing()) return;
        Integer id = idPerClau.get(clau);
        if (id == null) return;
        if (reason == REASON_CLICK) Magatzem.marca(this, String.valueOf(id), "vista", true);
        if (reason == REASON_CANCEL && !posposades.containsKey(clau)) {
            JSONObject aj = Magatzem.ajustos(this);
            if (aj.optBoolean("desfer", true)) {
                Desat d;
                synchronized (perId) { d = perId.get(id); }
                if (d != null) Alertes.desfer(this, id, d.paquet, d.titol, d.text, aj.optInt("segonsDesfer", 10));
            }
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn, RankingMap rm) { onNotificationRemoved(sbn, rm, 0); }

    /** Torna a posar una notificació (botó Desfer o Restaura de l'historial). */
    static boolean restaura(Context c, int id) {
        Desat d;
        synchronized (perId) { d = perId.get(id); }
        Alertes.nm(c).cancel(id + 1);
        if (d != null) {
            Alertes.copia(c, id, d.paquet, d.titol, d.text, d.obrir, false);
            return true;
        }
        JSONArray h = Magatzem.historial(c);
        for (int i = h.length() - 1; i >= 0; i--) {
            JSONObject o = h.optJSONObject(i);
            if (o != null && String.valueOf(id).equals(o.optString("id"))) {
                Alertes.copia(c, id, o.optString("paquet"), o.optString("titol"), o.optString("text"), null, false);
                return true;
            }
        }
        return false;
    }
}
