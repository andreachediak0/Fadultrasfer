package com.fadu.fadultransf;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.DocumentsContract;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.documentfile.provider.DocumentFile;

public class MainActivity extends Activity {
    private static final int PICK_FOLDER = 41;
    private static final String PREFS = "fadultransf";
    private static final String FOLDER = "folder_uri";
    private final int purple = 0xFF7C3AED;
    private final int pale = 0xFFF4EEFF;
    private TextView folderText, statusText, addressText, pinText;
    private Button startButton;
    private final Handler handler = new Handler();
    private final Runnable updater = new Runnable() {
        @Override public void run() { refresh(); handler.postDelayed(this, 1000); }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(0xFF6D28D9);
        getWindow().setNavigationBarColor(0xFF6D28D9);
        buildUi();
        refresh();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(32), dp(24), dp(24));
        root.setBackgroundColor(0xFFFFFFFF);
        TextView brand = new TextView(this);
        brand.setText("fadultransf"); brand.setTextColor(purple); brand.setTextSize(30); brand.setGravity(Gravity.CENTER);
        brand.setTypeface(null, 1);
        root.addView(brand, lp(-1, -2));
        TextView subtitle = new TextView(this);
        subtitle.setText("Compartí archivos por tu red local"); subtitle.setTextColor(0xFF625B71); subtitle.setTextSize(15); subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle, lp(-1, dp(40)));

        folderText = cardText(root, "1. Elegí una carpeta para compartir");
        Button choose = button("Elegir carpeta"); root.addView(choose, lp(-1, dp(52)));
        choose.setOnClickListener(v -> { Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION); startActivityForResult(i, PICK_FOLDER); });

        statusText = cardText(root, "Estado: detenido");
        startButton = button("Iniciar servidor"); root.addView(startButton, lp(-1, dp(54)));
        startButton.setOnClickListener(v -> toggleServer());
        addressText = cardText(root, "Dirección: —");
        pinText = cardText(root, "PIN: —");
        TextView how = new TextView(this);
        how.setText("Conectá ambos equipos a la misma Wi-Fi. En el otro equipo, abrí la dirección en el navegador e ingresá el PIN. Desde ahí podés descargar y subir archivos a la carpeta elegida.");
        how.setTextSize(14); how.setTextColor(0xFF49454F); how.setPadding(dp(16), dp(14), dp(16), dp(14)); how.setBackgroundColor(pale); root.addView(how, lp(-1, -2));
        TextView safety = new TextView(this);
        safety.setText("El acceso funciona solo mientras el servidor está iniciado. Usalo en una red de confianza y detenelo al terminar.");
        safety.setTextColor(0xFF6F667A); safety.setTextSize(12); safety.setPadding(dp(4), dp(14), dp(4), 0); root.addView(safety, lp(-1, -2));
        setContentView(root);
    }

    private TextView cardText(LinearLayout root, String text) {
        TextView tv = new TextView(this); tv.setText(text); tv.setTextColor(0xFF302A3D); tv.setTextSize(15); tv.setPadding(dp(16), dp(14), dp(16), dp(14)); tv.setBackgroundColor(pale);
        LinearLayout.LayoutParams p = lp(-1, -2); p.topMargin = dp(16); p.bottomMargin = dp(8); root.addView(tv, p); return tv;
    }
    private Button button(String label) { Button b = new Button(this); b.setText(label); b.setTextColor(0xFFFFFFFF); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(purple)); return b; }
    private LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }

    private void toggleServer() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (WebServerService.isRunning) {
            stopService(new Intent(this, WebServerService.class));
            refresh();
            return;
        }
        String saved = p.getString(FOLDER, null);
        if (saved == null) { Toast.makeText(this, "Primero elegí una carpeta", Toast.LENGTH_SHORT).show(); return; }
        Intent i = new Intent(this, WebServerService.class); i.setAction(WebServerService.ACTION_START);
        if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
        handler.postDelayed(this.updater, 350);
    }

    private void refresh() {
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        String saved = p.getString(FOLDER, null);
        if (saved == null) folderText.setText("1. Carpeta: todavía no elegida");
        else {
            DocumentFile d = DocumentFile.fromTreeUri(this, Uri.parse(saved));
            folderText.setText("1. Carpeta: " + (d != null && d.getName() != null ? d.getName() : "seleccionada"));
        }
        boolean running = WebServerService.isRunning;
        statusText.setText(running ? "Estado: servidor activo" : "Estado: detenido");
        startButton.setText(running ? "Detener servidor" : "Iniciar servidor");
        addressText.setText(running ? "Dirección: http://" + WebServerService.ipAddress + ":8080" : "Dirección: —");
        pinText.setText(running ? "PIN de acceso: " + WebServerService.accessPin : "PIN: —");
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_FOLDER && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try { getContentResolver().takePersistableUriPermission(uri, flags); } catch (Exception ignored) {}
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(FOLDER, uri.toString()).apply(); refresh();
        }
    }

    @Override protected void onResume() { super.onResume(); handler.post(updater); }
    @Override protected void onPause() { super.onPause(); handler.removeCallbacks(updater); }
}
