package cat.xoriguer.notificacions;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.json.JSONObject;

/**
 * Recordatoris i respostes automàtiques que han de passar d'aquí a uns minuts.
 *
 * Es desen a SharedPreferences ("temps") i es programen amb AlarmManager: si Android tanca l'app per estalviar
 * bateria, l'alarma la torna a obrir i la feina es fa igualment (abans, amb un Handler, es perdia).
 * Clau de cada temporitzador: "<clau de la notificació>#rec" o "#resp".
 */
final class Temporitzadors {
    static final String ACCIO = "cat.xoriguer.notificacions.TEMPS";
    private static final String PREF = "temps";

    private Temporitzadors() {}

    static boolean exactes(Context c) {
        if (Build.VERSION.SDK_INT < 31) return true;
        AlarmManager am = c.getSystemService(AlarmManager.class);
        return am != null && am.canScheduleExactAlarms();
    }

    private static PendingIntent pi(Context c, String k, int flags) {
        Intent i = new Intent(c, Receptor.class).setAction(ACCIO).putExtra("k", k);
        return PendingIntent.getBroadcast(c, k.hashCode(), i, PendingIntent.FLAG_IMMUTABLE | flags);
    }

    /** Desa i programa. `t` ha de portar clau, tipus i quan (ms). */
    static void programa(Context c, JSONObject t) {
        String k = t.optString("clau") + "#" + t.optString("tipus");
        JSONObject tots = Magatzem.objecte(c, PREF);
        try { tots.put(k, t); } catch (Exception ignored) {}
        Magatzem.desaObjecte(c, PREF, tots);
        alarma(c, k, t.optLong("quan"));
    }

    static void alarma(Context c, String k, long quan) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) return;
        PendingIntent p = pi(c, k, PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            if (exactes(c)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quan, p);
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quan, p); // pot arribar uns minuts tard
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, quan, p);
        }
    }

    static JSONObject llegeix(Context c, String k) {
        return Magatzem.objecte(c, PREF).optJSONObject(k);
    }

    static void treu(Context c, String k) {
        JSONObject tots = Magatzem.objecte(c, PREF);
        if (tots.remove(k) != null) Magatzem.desaObjecte(c, PREF, tots);
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent p = pi(c, k, PendingIntent.FLAG_NO_CREATE);
        if (am != null && p != null) { am.cancel(p); p.cancel(); }
    }

    /** La notificació ja no hi és: fora els seus temporitzadors. */
    static void cancela(Context c, String clau) {
        treu(c, clau + "#rec");
        treu(c, clau + "#resp");
    }

    static int pendents(Context c) { return Magatzem.objecte(c, PREF).length(); }

    /** Torna a programar els que han quedat desats (p. ex. després d'actualitzar l'app); fora els caducats d'un dia. */
    static void reprograma(Context c) {
        JSONObject tots = Magatzem.objecte(c, PREF);
        long ara = System.currentTimeMillis();
        List<String> vells = new ArrayList<>();
        for (Iterator<String> it = tots.keys(); it.hasNext(); ) {
            String k = it.next();
            JSONObject t = tots.optJSONObject(k);
            if (t == null || t.optLong("quan") < ara - 86_400_000L) { vells.add(k); continue; }
            alarma(c, k, Math.max(t.optLong("quan"), ara + 5_000L));
        }
        for (String k : vells) treu(c, k);
    }
}
