package cat.xoriguer.notificacions;

import android.content.Context;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Textos dels avisos propis (pausa, desfer, secret, alarma, rajola, canals) en català, castellà, francès i anglès.
 * L'idioma és el que tria l'app (ajust "llengua", que desa app.js); si no n'hi ha, el del sistema.
 * La clau és el text en català; `{0}` es substitueix pel paràmetre.
 */
final class Textos {
    private Textos() {}

    private static final Map<String, String[]> T = new HashMap<>();
    private static void d(String ca, String es, String fr, String en) { T.put(ca, new String[]{es, fr, en}); }
    static {
        d("Notificacions recuperades i amagades", "Notificaciones recuperadas y ocultas", "Notifications restaurées et masquées", "Restored and hidden notifications");
        d("Desfer (notificació descartada)", "Deshacer (notificación descartada)", "Annuler (notification balayée)", "Undo (dismissed notification)");
        d("Alarmes", "Alarmas", "Alarmes", "Alarms");
        d("Atura", "Detener", "Arrêter", "Stop");
        d("Nova notificació de {0}", "Nueva notificación de {0}", "Nouvelle notification de {0}", "New notification from {0}");
        d("Contingut amagat · toca per obrir", "Contenido oculto · toca para abrir", "Contenu masqué · touchez pour ouvrir", "Hidden content · tap to open");
        d("Descartada: {0}", "Descartada: {0}", "Balayée : {0}", "Dismissed: {0}");
        d("{0} · toca per recuperar-la", "{0} · toca para recuperarla", "{0} · touchez pour la restaurer", "{0} · tap to restore it");
        d("Desfer", "Deshacer", "Annuler", "Undo");
        d("Regles en pausa", "Reglas en pausa", "Règles en pause", "Rules paused");
        d("Fins {0} les notificacions arriben sense filtrar.", "Hasta {0} las notificaciones llegan sin filtrar.", "Jusqu’à {0}, les notifications arrivent sans filtre.", "Until {0}, notifications arrive unfiltered.");
        d("Reprèn", "Reanudar", "Reprendre", "Resume");
        d("Pausa les regles", "Pausar las reglas", "Mettre en pause", "Pause rules");
        d("fins {0}", "hasta {0}", "jusqu’à {0}", "until {0}");
        d("1 hora", "1 hora", "1 heure", "1 hour");
        d("demà a les {0}", "mañana a las {0}", "demain à {0}", "tomorrow at {0}");
        d("les {0}", "las {0}", "{0}", "{0}");
    }

    /** "ca", "es", "fr" o "en". */
    static String llengua(Context c) {
        String l = Magatzem.ajustos(c).optString("llengua", "");
        if (l.isEmpty()) {
            String s = Locale.getDefault().getLanguage();
            l = s.equals("ca") ? "ca" : (s.equals("es") || s.equals("gl") || s.equals("eu")) ? "es" : s.equals("fr") ? "fr" : "en";
        }
        return l;
    }

    static String t(Context c, String ca, Object... p) {
        String l = llengua(c), s = ca;
        String[] x = T.get(ca);
        if (x != null && !l.equals("ca")) s = x[l.equals("es") ? 0 : l.equals("fr") ? 1 : 2];
        for (int i = 0; i < p.length; i++) s = s.replace("{" + i + "}", String.valueOf(p[i]));
        return s;
    }
}
