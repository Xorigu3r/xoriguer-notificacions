package cat.xoriguer.notificacions;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Lògica pura de les regles (la mateixa que `coincideix` d'app.js, que té proves a test.mjs).
 *
 * Regla: {id, nom, activa, apps:[paquet], paraules:[text], exclou:[text], de:"HH:mm", a:"HH:mm", dies:[1..7 (dl=1)],
 *         accio: ignora|refreda|alerta|respon|recorda|posposa|alarma|secret,
 *         segons, so, vibracio, text, minuts, franges:"09:00,13:00,18:00"}
 */
public final class Motor {
    private Motor() {}

    public static String normalitza(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}+", "");
    }

    static List<String> llista(JSONArray a) {
        List<String> out = new ArrayList<>();
        if (a == null) return out;
        for (int i = 0; i < a.length(); i++) {
            String s = a.optString(i, "").trim();
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    static int minuts(String hhmm) {
        if (hhmm == null || !hhmm.matches("\\d{1,2}:\\d{2}")) return -1;
        String[] p = hhmm.split(":");
        return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
    }

    /** Dia de la setmana 1 = dilluns ... 7 = diumenge. */
    static int dia(Calendar c) {
        int d = c.get(Calendar.DAY_OF_WEEK); // diumenge = 1
        return d == Calendar.SUNDAY ? 7 : d - 1;
    }

    public static boolean dinsHorari(JSONObject r, Calendar ara) {
        List<String> dies = llista(r.optJSONArray("dies"));
        if (!dies.isEmpty() && !dies.contains(String.valueOf(dia(ara)))) return false;
        int de = minuts(r.optString("de", "")), a = minuts(r.optString("a", ""));
        if (de < 0 || a < 0 || de == a) return true;
        int m = ara.get(Calendar.HOUR_OF_DAY) * 60 + ara.get(Calendar.MINUTE);
        return de < a ? (m >= de && m < a) : (m >= de || m < a); // franja que passa per mitjanit
    }

    public static boolean coincideix(JSONObject r, String paquet, String titol, String text, Calendar ara) {
        if (!r.optBoolean("activa", true)) return false;
        List<String> apps = llista(r.optJSONArray("apps"));
        if (!apps.isEmpty() && !apps.contains(paquet)) return false;
        String tot = normalitza(titol) + "\n" + normalitza(text);
        List<String> paraules = llista(r.optJSONArray("paraules"));
        if (!paraules.isEmpty()) {
            boolean alguna = false;
            for (String p : paraules) if (tot.contains(normalitza(p))) { alguna = true; break; }
            if (!alguna) return false;
        }
        for (String p : llista(r.optJSONArray("exclou"))) if (tot.contains(normalitza(p))) return false;
        return dinsHorari(r, ara);
    }

    /** Pròxima franja de lliurament (posposar), en ms des d'ara. -1 si no n'hi ha cap de vàlida. */
    public static long finsProperaFranja(String franges, Calendar ara) {
        if (franges == null) return -1;
        int m = ara.get(Calendar.HOUR_OF_DAY) * 60 + ara.get(Calendar.MINUTE);
        int millor = Integer.MAX_VALUE;
        for (String f : franges.split("[,;\\s]+")) {
            int x = minuts(f.trim());
            if (x < 0) continue;
            int dif = x - m;
            if (dif <= 0) dif += 24 * 60;
            millor = Math.min(millor, dif);
        }
        if (millor == Integer.MAX_VALUE) return -1;
        long ms = millor * 60_000L - ara.get(Calendar.SECOND) * 1000L;
        return Math.max(ms, 30_000L);
    }

    public static long[] patro(String nom) {
        if (nom == null) return null;
        switch (nom) {
            case "curta": return new long[]{0, 120};
            case "llarga": return new long[]{0, 900};
            case "doble": return new long[]{0, 180, 120, 180};
            case "triple": return new long[]{0, 150, 100, 150, 100, 150};
            case "batec": return new long[]{0, 90, 90, 90, 500, 90, 90, 90};
            case "sos": return new long[]{0, 120, 80, 120, 80, 120, 250, 400, 120, 400, 120, 400, 250, 120, 80, 120, 80, 120};
            case "cap": return new long[0];
            default: return null; // "defecte": el del canal de l'app
        }
    }
}
