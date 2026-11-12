package cat.xoriguer.notificacions;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;

/** So, vibració, alarma i notificacions pròpies (còpies, secret, desfer). */
public final class Alertes {
    static final String CANAL_COPIES = "copies";
    static final String CANAL_DESFER = "desfer";
    static final String CANAL_ALARMA = "alarma";
    static final int ID_ALARMA = 7001;

    private static MediaPlayer alarma;
    private static final Handler h = new Handler(Looper.getMainLooper());

    private Alertes() {}

    static void canals(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel copies = new NotificationChannel(CANAL_COPIES, "Notificacions recuperades i amagades", NotificationManager.IMPORTANCE_DEFAULT);
        copies.setSound(null, null); // el so el fa l'app segons les regles
        copies.enableVibration(false);
        nm.createNotificationChannel(copies);
        NotificationChannel desfer = new NotificationChannel(CANAL_DESFER, "Desfer (notificació descartada)", NotificationManager.IMPORTANCE_LOW);
        nm.createNotificationChannel(desfer);
        NotificationChannel al = new NotificationChannel(CANAL_ALARMA, "Alarmes", NotificationManager.IMPORTANCE_HIGH);
        al.setSound(null, null);
        al.enableVibration(false);
        al.setBypassDnd(true);
        nm.createNotificationChannel(al);
    }

    static final AudioAttributes ATRIBUTS_NOTIF = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();

    /**
     * Fa sonar i/o vibrar.
     * @param so      null/"defecte" = so per defecte (o `uriCanal` si n'hi ha), "cap" = mut, o una URI
     * @param patro   null = vibració per defecte (o `patroCanal`), buit = sense vibració
     */
    static void alerta(Context c, String so, long[] patro, Uri uriCanal, long[] patroCanal, boolean canalVibra) {
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        int mode = am != null ? am.getRingerMode() : AudioManager.RINGER_MODE_NORMAL;
        if (mode == AudioManager.RINGER_MODE_NORMAL && !"cap".equals(so)) {
            Uri uri = null;
            if (so != null && !so.isEmpty() && !"defecte".equals(so)) uri = Uri.parse(so);
            else if (uriCanal != null) uri = uriCanal;
            else if (so != null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            if (uri != null) {
                try {
                    Ringtone r = RingtoneManager.getRingtone(c, uri);
                    if (r != null) { r.setAudioAttributes(ATRIBUTS_NOTIF); r.play(); }
                } catch (Exception ignored) {}
            }
        }
        if (mode == AudioManager.RINGER_MODE_SILENT) return;
        long[] p = patro;
        if (p == null) p = patroCanal != null ? patroCanal : (canalVibra ? new long[]{0, 250, 120, 250} : null);
        if (p != null && p.length > 1) vibra(c, p, -1);
    }

    static void vibra(Context c, long[] p, int repeteix) {
        Vibrator v = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
        if (v == null || !v.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(p, repeteix), ATRIBUTS_NOTIF);
        else v.vibrate(p, repeteix);
    }

    // ---------------- Alarma ----------------
    static synchronized void alarma(Context c, String titol, String text, String so) {
        aturaAlarma(c);
        try {
            Uri uri = so != null && !so.isEmpty() && !"defecte".equals(so) ? Uri.parse(so) : RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            alarma = new MediaPlayer();
            alarma.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            alarma.setDataSource(c, uri);
            alarma.setLooping(true);
            alarma.prepare();
            alarma.start();
        } catch (Exception e) { alarma = null; }
        vibra(c, new long[]{0, 800, 400}, 1);
        Intent atura = new Intent(c, Receptor.class).setAction(Receptor.ATURA_ALARMA);
        PendingIntent pi = PendingIntent.getBroadcast(c, 1, atura, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = builder(c, CANAL_ALARMA)
                .setSmallIcon(R.drawable.ic_stat_campana)
                .setContentTitle("⏰ " + titol)
                .setContentText(text)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_ALARM)
                .setDeleteIntent(pi)
                .setContentIntent(pi)
                .addAction(new Notification.Action.Builder(null, "Atura", pi).build());
        nm(c).notify(ID_ALARMA, b.build());
        h.postDelayed(() -> aturaAlarma(c), 3 * 60_000L); // com a molt 3 minuts
    }

    static synchronized void aturaAlarma(Context c) {
        h.removeCallbacksAndMessages(null);
        if (alarma != null) { try { alarma.stop(); alarma.release(); } catch (Exception ignored) {} alarma = null; }
        Vibrator v = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null) v.cancel();
        nm(c).cancel(ID_ALARMA);
    }

    static boolean sonaAlarma() { return alarma != null; }

    // ---------------- Còpies pròpies ----------------
    static NotificationManager nm(Context c) { return (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE); }

    @SuppressWarnings("deprecation")
    static Notification.Builder builder(Context c, String canal) {
        return Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, canal) : new Notification.Builder(c);
    }

    static String nomApp(Context c, String paquet) {
        try {
            PackageManager pm = c.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(paquet, 0);
            return String.valueOf(pm.getApplicationLabel(ai));
        } catch (Exception e) { return paquet; }
    }

    static Bitmap iconaApp(Context c, String paquet) {
        try {
            Drawable d = c.getPackageManager().getApplicationIcon(paquet);
            if (d instanceof BitmapDrawable) return ((BitmapDrawable) d).getBitmap();
            Bitmap b = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(b);
            d.setBounds(0, 0, 96, 96);
            d.draw(cv);
            return b;
        } catch (Exception e) { return null; }
    }

    static PendingIntent obreApp(Context c, String paquet) {
        Intent i = c.getPackageManager().getLaunchIntentForPackage(paquet);
        if (i == null) return null;
        return PendingIntent.getActivity(c, paquet.hashCode(), i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Torna a mostrar una notificació (restaurar / desfer / secret). */
    static void copia(Context c, int id, String paquet, String titol, String text, PendingIntent obrir, boolean amagada) {
        String app = nomApp(c, paquet);
        Notification.Builder b = builder(c, CANAL_COPIES)
                .setSmallIcon(R.drawable.ic_stat_campana)
                .setAutoCancel(true)
                .setSubText(app)
                .setContentIntent(obrir != null ? obrir : obreApp(c, paquet));
        Bitmap ic = iconaApp(c, paquet);
        if (ic != null) b.setLargeIcon(ic);
        if (amagada) {
            b.setContentTitle("Nova notificació de " + app).setContentText("Contingut amagat · toca per obrir");
        } else {
            b.setContentTitle(titol == null || titol.isEmpty() ? app : titol).setContentText(text)
             .setStyle(new Notification.BigTextStyle().bigText(text));
        }
        nm(c).notify(id, b.build());
    }

    static void desfer(Context c, int idCopia, String paquet, String titol, String text, int segons) {
        Intent i = new Intent(c, Receptor.class).setAction(Receptor.DESFER).putExtra("id", idCopia);
        PendingIntent pi = PendingIntent.getBroadcast(c, idCopia, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = builder(c, CANAL_DESFER)
                .setSmallIcon(R.drawable.ic_stat_campana)
                .setContentTitle("Descartada: " + nomApp(c, paquet))
                .setContentText((titol == null ? "" : titol) + " · toca per recuperar-la")
                .setContentIntent(pi)
                .setAutoCancel(true)
                .addAction(new Notification.Action.Builder(null, "Desfer", pi).build());
        if (Build.VERSION.SDK_INT >= 26) b.setTimeoutAfter(segons * 1000L);
        nm(c).notify(idCopia + 1, b.build());
    }
}
