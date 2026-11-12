package cat.xoriguer.notificacions;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Botons de les notificacions pròpies (aturar l'alarma, desfer, reprendre) i alarmes dels temporitzadors. */
public class Receptor extends BroadcastReceiver {
    static final String ATURA_ALARMA = "cat.xoriguer.notificacions.ATURA_ALARMA";
    static final String DESFER = "cat.xoriguer.notificacions.DESFER";
    static final String REPREN = "cat.xoriguer.notificacions.REPREN";

    @Override
    public void onReceive(Context c, Intent i) {
        String a = i.getAction();
        if (ATURA_ALARMA.equals(a)) {
            Alertes.aturaAlarma(c);
        } else if (DESFER.equals(a)) {
            ServeiNotificacions.restaura(c, i.getIntExtra("id", 0));
        } else if (REPREN.equals(a)) {
            Pausa.posa(c, 0);
        } else if (Temporitzadors.ACCIO.equals(a)) {
            ServeiNotificacions.dispara(c, i.getStringExtra("k"));
        } else if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) {
            Temporitzadors.reprograma(c); // en actualitzar l'app, Android esborra les alarmes
        }
    }
}
