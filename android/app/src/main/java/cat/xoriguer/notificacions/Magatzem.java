package cat.xoriguer.notificacions;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

import org.json.JSONArray;
import org.json.JSONObject;

/** Regles, ajustos i historial. Tot es queda al mòbil (l'app no té permís d'Internet). */
public final class Magatzem {
    private static final String PREFS = "xoriguer_notificacions";
    private static final int MAX_HISTORIAL = 1000;
    private static JSONArray historial; // a memòria; es desa al fitxer

    private Magatzem() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static JSONArray regles(Context c) {
        try { return new JSONArray(prefs(c).getString("regles", "[]")); } catch (Exception e) { return new JSONArray(); }
    }

    public static JSONObject ajustos(Context c) {
        JSONObject a;
        try { a = new JSONObject(prefs(c).getString("ajustos", "{}")); } catch (Exception e) { a = new JSONObject(); }
        try {
            if (!a.has("control")) a.put("control", false);   // control del so (necessari per refredament i alertes pròpies)
            if (!a.has("desfer")) a.put("desfer", true);
            if (!a.has("segonsDesfer")) a.put("segonsDesfer", 10);
            if (!a.has("historial")) a.put("historial", true);
            if (!a.has("diesHistorial")) a.put("diesHistorial", 14);
            if (!a.has("refredaGlobal")) a.put("refredaGlobal", 0); // segons; 0 = desactivat
        } catch (Exception ignored) {}
        return a;
    }

    public static void desa(Context c, JSONArray regles, JSONObject ajustos) {
        SharedPreferences.Editor e = prefs(c).edit();
        if (regles != null) e.putString("regles", regles.toString());
        if (ajustos != null) e.putString("ajustos", ajustos.toString());
        e.apply();
    }

    /** Objecte JSON desat amb nom (temporitzadors, posposades...). */
    static JSONObject objecte(Context c, String k) {
        try { return new JSONObject(prefs(c).getString(k, "{}")); } catch (Exception e) { return new JSONObject(); }
    }

    static void desaObjecte(Context c, String k, JSONObject o) {
        prefs(c).edit().putString(k, o.toString()).apply();
    }

    /** Fins quan estan en pausa les regles (ms; 0 = no). */
    static long pausaFins(Context c) { return prefs(c).getLong("pausaFins", 0); }

    static boolean enPausa(Context c) { return pausaFins(c) > System.currentTimeMillis(); }

    static void desaPausa(Context c, long fins) { prefs(c).edit().putLong("pausaFins", fins).apply(); }

    static File fitxer(Context c) { return new File(c.getFilesDir(), "historial.json"); }

    public static synchronized JSONArray historial(Context c) {
        if (historial == null) {
            try {
                File f = fitxer(c);
                if (f.exists()) {
                    byte[] b = new byte[(int) f.length()];
                    try (FileInputStream in = new FileInputStream(f)) { int n = 0; while (n < b.length) { int r = in.read(b, n, b.length - n); if (r < 0) break; n += r; } }
                    historial = new JSONArray(new String(b, StandardCharsets.UTF_8));
                }
            } catch (Exception ignored) {}
            if (historial == null) historial = new JSONArray();
        }
        return historial;
    }

    public static synchronized void afegeix(Context c, JSONObject item) {
        JSONArray h = historial(c);
        h.put(item);
        long limit = System.currentTimeMillis() - ajustos(c).optInt("diesHistorial", 14) * 86_400_000L;
        int treu = Math.max(0, h.length() - MAX_HISTORIAL);
        while (treu < h.length() && h.optJSONObject(treu) != null && h.optJSONObject(treu).optLong("t") < limit) treu++;
        if (treu > 0) {
            JSONArray n = new JSONArray();
            for (int i = treu; i < h.length(); i++) n.put(h.opt(i));
            historial = n;
        }
        guardaHistorial(c);
    }

    public static synchronized void marca(Context c, String id, String camp, Object valor) {
        JSONArray h = historial(c);
        for (int i = h.length() - 1; i >= 0 && i >= h.length() - 200; i--) {
            JSONObject o = h.optJSONObject(i);
            if (o != null && id.equals(o.optString("id"))) {
                try { o.put(camp, valor); } catch (Exception ignored) {}
                guardaHistorial(c);
                return;
            }
        }
    }

    public static synchronized void esborraHistorial(Context c) {
        historial = new JSONArray();
        guardaHistorial(c);
    }

    static void guardaHistorial(Context c) {
        try (FileOutputStream out = new FileOutputStream(fitxer(c))) {
            out.write(historial.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
    }
}
