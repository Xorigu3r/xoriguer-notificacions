package cat.xoriguer.notificacions;

import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Botó de la persiana de Configuració ràpida: un toc posa les regles en pausa 1 hora; un altre les reprèn. */
public class Rajola extends TileService {
    static final long UNA_HORA = 3_600_000L;

    @Override
    public void onStartListening() { pinta(); }

    @Override
    public void onClick() {
        if (Magatzem.enPausa(this)) Pausa.posa(this, 0);
        else Pausa.posa(this, System.currentTimeMillis() + UNA_HORA);
        pinta();
    }

    private void pinta() {
        Tile t = getQsTile();
        if (t == null) return;
        boolean pausa = Magatzem.enPausa(this);
        t.setIcon(Icon.createWithResource(this, R.drawable.ic_stat_campana));
        t.setLabel(pausa ? "Regles en pausa" : "Pausa les regles");
        if (Build.VERSION.SDK_INT >= 29) t.setSubtitle(pausa ? "fins " + Pausa.hora(Magatzem.pausaFins(this)) : "1 hora");
        t.setState(pausa ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.updateTile();
    }
}
