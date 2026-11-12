package cat.xoriguer.notificacions;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Botons de les notificacions pròpies: aturar l'alarma i desfer un descart. */
public class Receptor extends BroadcastReceiver {
    static final String ATURA_ALARMA = "cat.xoriguer.notificacions.ATURA_ALARMA";
    static final String DESFER = "cat.xoriguer.notificacions.DESFER";

    @Override
    public void onReceive(Context c, Intent i) {
        if (ATURA_ALARMA.equals(i.getAction())) {
            Alertes.aturaAlarma(c);
        } else if (DESFER.equals(i.getAction())) {
            ServeiNotificacions.restaura(c, i.getIntExtra("id", 0));
        }
    }
}
