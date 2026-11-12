package cat.xoriguer.notificacions;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.service.quicksettings.TileService;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * Pausa de totes les regles durant un temps (des de l'app o del botó de la persiana).
 * Mentre dura, les notificacions arriben com si l'app no hi fos (però continuen a l'historial)
 * i hi ha un avís fix amb el botó «Reprèn». Quan s'acaba el temps, l'avís desapareix sol.
 */
final class Pausa {
    static final int ID_AVIS = 7002;

    private Pausa() {}

    static String hora(long t) {
        Calendar a = Calendar.getInstance(), b = Calendar.getInstance();
        b.setTimeInMillis(t);
        String h = new SimpleDateFormat("HH:mm", Locale.ROOT).format(t);
        return a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR) ? h : "demà a les " + h;
    }

    /** fins = moment (ms) en què s'acaba la pausa; 0 = reprèn ara. */
    static void posa(Context c, long fins) {
        long ara = System.currentTimeMillis();
        if (fins <= ara) fins = 0;
        Magatzem.desaPausa(c, fins);
        Alertes.canals(c);
        if (fins == 0) {
            Alertes.nm(c).cancel(ID_AVIS);
        } else {
            Intent i = new Intent(c, Receptor.class).setAction(Receptor.REPREN);
            PendingIntent pi = PendingIntent.getBroadcast(c, 3, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Intent obre = c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());
            Notification.Builder b = Alertes.builder(c, Alertes.CANAL_DESFER)
                    .setSmallIcon(R.drawable.ic_stat_campana)
                    .setContentTitle("Regles en pausa")
                    .setContentText("Fins " + hora(fins) + " les notificacions arriben sense filtrar.")
                    .setOngoing(true)
                    .setTimeoutAfter(fins - ara)
                    .addAction(new Notification.Action.Builder(null, "Reprèn", pi).build());
            if (obre != null) b.setContentIntent(PendingIntent.getActivity(c, 4, obre, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            Alertes.nm(c).notify(ID_AVIS, b.build());
        }
        try { TileService.requestListeningState(c, new ComponentName(c, Rajola.class)); } catch (Exception ignored) {}
    }
}
