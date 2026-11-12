package cat.xoriguer.notificacions;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.activity.result.ActivityResult;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;

import org.json.JSONArray;
import org.json.JSONObject;

/** Pont entre la web (www/app.js) i el servei natiu. */
@CapacitorPlugin(name = "Notificacions")
public class NotificacionsPlugin extends Plugin {

    private boolean teAcces() {
        return NotificationManagerCompat.getEnabledListenerPackages(getContext()).contains(getContext().getPackageName());
    }

    @PluginMethod
    public void estat(PluginCall call) {
        JSObject r = new JSObject();
        r.put("acces", teAcces());
        r.put("connectat", ServeiNotificacions.connectat());
        boolean permis = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(getContext(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        r.put("permisNotificacions", permis);
        r.put("alarma", Alertes.sonaAlarma());
        r.put("android", Build.VERSION.SDK_INT);
        r.put("pausaFins", Magatzem.enPausa(getContext()) ? Magatzem.pausaFins(getContext()) : 0);
        r.put("alarmesExactes", Temporitzadors.exactes(getContext()));
        r.put("pendents", Temporitzadors.pendents(getContext()));
        call.resolve(r);
    }

    /** minuts > 0: pausa les regles aquests minuts; 0: reprèn. */
    @PluginMethod
    public void pausa(PluginCall call) {
        int m = call.getInt("minuts", 0);
        long fins = call.getLong("fins", 0L);
        if (fins <= 0 && m > 0) fins = System.currentTimeMillis() + m * 60_000L;
        Pausa.posa(getContext(), fins);
        JSObject r = new JSObject();
        r.put("pausaFins", Magatzem.enPausa(getContext()) ? Magatzem.pausaFins(getContext()) : 0);
        call.resolve(r);
    }

    @PluginMethod
    public void obreAlarmes(PluginCall call) {
        Intent i = Build.VERSION.SDK_INT >= 31
                ? new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getContext().getPackageName()))
                : new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getContext().getPackageName()));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { getContext().startActivity(i); } catch (Exception e) { call.reject("No es pot obrir"); return; }
        call.resolve();
    }

    /** Obre una adreça al navegador (l'app no té Internet: ho fa el navegador). Només https. */
    @PluginMethod
    public void obreWeb(PluginCall call) {
        String url = call.getString("url", "");
        if (url == null || !url.startsWith("https://")) { call.reject("Adreça no vàlida"); return; }
        try { getContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
        catch (Exception e) { call.reject("No hi ha cap navegador"); return; }
        call.resolve();
    }

    // ---------------- Còpia de seguretat (fitxer triat per l'usuari, sense Internet) ----------------
    private String perExportar;

    @PluginMethod
    public void exporta(PluginCall call) {
        perExportar = call.getString("text", "");
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json").putExtra(Intent.EXTRA_TITLE, call.getString("nom", "regles.json"));
        startActivityForResult(call, i, "exportat");
    }

    @ActivityCallback
    private void exportat(PluginCall call, ActivityResult result) {
        JSObject r = new JSObject();
        Uri uri = result.getData() != null ? result.getData().getData() : null;
        if (uri == null) { r.put("ok", false); call.resolve(r); return; }
        try (java.io.OutputStream out = getContext().getContentResolver().openOutputStream(uri, "wt")) {
            out.write(perExportar.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            r.put("ok", true);
        } catch (Exception e) { call.reject("No s'ha pogut desar: " + e.getMessage()); return; }
        call.resolve(r);
    }

    @PluginMethod
    public void importa(PluginCall call) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
        startActivityForResult(call, i, "importat");
    }

    @ActivityCallback
    private void importat(PluginCall call, ActivityResult result) {
        JSObject r = new JSObject();
        Uri uri = result.getData() != null ? result.getData().getData() : null;
        if (uri == null) { call.resolve(r); return; }
        try (java.io.InputStream in = getContext().getContentResolver().openInputStream(uri)) {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n, total = 0;
            while ((n = in.read(buf)) > 0) { b.write(buf, 0, n); total += n; if (total > 2_000_000) throw new Exception("fitxer massa gran"); }
            r.put("text", new String(b.toByteArray(), java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) { call.reject("No s'ha pogut llegir: " + e.getMessage()); return; }
        call.resolve(r);
    }

    @PluginMethod
    public void obreAcces(PluginCall call) {
        Intent i = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        if (Build.VERSION.SDK_INT >= 30) {
            Intent det = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS);
            det.putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    new ComponentName(getContext(), ServeiNotificacions.class).flattenToString());
            if (det.resolveActivity(getContext().getPackageManager()) != null) i = det;
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(i);
        call.resolve();
    }

    @PluginMethod
    public void demanaPermis(PluginCall call) {
        if (Build.VERSION.SDK_INT >= 33 && getActivity() != null)
            ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.POST_NOTIFICATIONS}, 41);
        call.resolve();
    }

    @PluginMethod
    public void obreBateria(PluginCall call) {
        Intent i = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { getContext().startActivity(i); } catch (Exception e) {
            getContext().startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getContext().getPackageName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        call.resolve();
    }

    @PluginMethod
    public void llegeix(PluginCall call) {
        try {
            JSObject r = new JSObject();
            r.put("regles", new JSArray(Magatzem.regles(getContext()).toString()));
            r.put("ajustos", new JSObject(Magatzem.ajustos(getContext()).toString()));
            call.resolve(r);
        } catch (Exception e) { call.reject(e.getMessage()); }
    }

    @PluginMethod
    public void desa(PluginCall call) {
        try {
            JSONArray regles = call.getData().has("regles") ? new JSONArray(call.getData().getJSONArray("regles").toString()) : null;
            JSONObject ajustos = call.getData().has("ajustos") ? new JSONObject(call.getData().getJSONObject("ajustos").toString()) : null;
            Magatzem.desa(getContext(), regles, ajustos);
            ServeiNotificacions.actualitza();
            call.resolve();
        } catch (Exception e) { call.reject(e.getMessage()); }
    }

    @PluginMethod
    public void historial(PluginCall call) {
        try {
            JSObject r = new JSObject();
            r.put("items", new JSArray(Magatzem.historial(getContext()).toString()));
            call.resolve(r);
        } catch (Exception e) { call.reject(e.getMessage()); }
    }

    @PluginMethod
    public void esborraHistorial(PluginCall call) {
        Magatzem.esborraHistorial(getContext());
        call.resolve();
    }

    @PluginMethod
    public void restaura(PluginCall call) {
        boolean ok = ServeiNotificacions.restaura(getContext(), Integer.parseInt(call.getString("id", "0")));
        JSObject r = new JSObject();
        r.put("ok", ok);
        call.resolve(r);
    }

    /** Apps amb icona al llançador (per triar-les a les regles). */
    @PluginMethod
    public void apps(PluginCall call) {
        PackageManager pm = getContext().getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> l = pm.queryIntentActivities(i, 0);
        TreeMap<String, String> perNom = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (ResolveInfo ri : l) {
            String p = ri.activityInfo.packageName;
            if (p.equals(getContext().getPackageName())) continue;
            perNom.put(String.valueOf(ri.loadLabel(pm)) + "\u0000" + p, p);
        }
        JSArray out = new JSArray();
        List<String> vistos = new ArrayList<>();
        for (String k : perNom.keySet()) {
            String p = perNom.get(k);
            if (vistos.contains(p)) continue;
            vistos.add(p);
            JSObject o = new JSObject();
            o.put("paquet", p);
            o.put("nom", k.substring(0, k.indexOf('\u0000')));
            out.put(o);
        }
        JSObject r = new JSObject();
        r.put("apps", out);
        call.resolve(r);
    }

    @PluginMethod
    public void prova(PluginCall call) {
        String accio = call.getString("accio", "alerta");
        if ("alarma".equals(accio)) Alertes.alarma(getContext(), "Prova d'alarma", "Toca Atura per parar-la", call.getString("so", ""));
        else Alertes.alerta(getContext(), call.getString("so", "defecte"), Motor.patro(call.getString("vibracio", "defecte")), null, null, true);
        call.resolve();
    }

    @PluginMethod
    public void aturaAlarma(PluginCall call) {
        Alertes.aturaAlarma(getContext());
        call.resolve();
    }

    @PluginMethod
    public void triaSo(PluginCall call) {
        Intent i = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, "alarma".equals(call.getString("tipus")) ? RingtoneManager.TYPE_ALARM : RingtoneManager.TYPE_NOTIFICATION);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Tria el so");
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
        i.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
        String actual = call.getString("actual", "");
        if (actual != null && actual.startsWith("content:")) i.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(actual));
        startActivityForResult(call, i, "soTriat");
    }

    @ActivityCallback
    private void soTriat(PluginCall call, ActivityResult result) {
        JSObject r = new JSObject();
        Intent data = result.getData();
        Uri uri = data != null ? data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI) : null;
        if (uri != null) {
            r.put("uri", uri.toString());
            try { r.put("nom", RingtoneManager.getRingtone(getContext(), uri).getTitle(getContext())); } catch (Exception e) { r.put("nom", "So triat"); }
        }
        call.resolve(r);
    }
}
