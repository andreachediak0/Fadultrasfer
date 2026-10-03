package com.fadu.fadultransf;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.text.format.Formatter;
import androidx.documentfile.provider.DocumentFile;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Random;

public class WebServerService extends Service {
    public static final String ACTION_START = "com.fadu.fadultransf.START";
    public static volatile boolean isRunning = false;
    public static volatile String ipAddress = "";
    public static volatile String accessPin = "";
    private volatile boolean running;
    private ServerSocket serverSocket;
    private Thread worker;
    private static final int PORT = 8080;
    private static final String CHANNEL = "fadultransf_server";

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_START.equals(intent.getAction()) && !running) startServer();
        return START_NOT_STICKY;
    }

    private void startServer() {
        try {
            createChannel(); startForeground(1, notification("Servidor FadulTransf activo"));
            accessPin = String.format(Locale.US, "%06d", new Random().nextInt(1000000));
            WifiManager wm = (WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
            int ip = wm == null ? 0 : wm.getConnectionInfo().getIpAddress();
            ipAddress = Formatter.formatIpAddress(ip);
            serverSocket = new ServerSocket(PORT);
            running = true; isRunning = true;
            worker = new Thread(() -> {
                while (running) {
                    try { Socket socket = serverSocket.accept(); new Thread(() -> handle(socket)).start(); }
                    catch (Exception e) { if (running) e.printStackTrace(); }
                }
            }, "FadulTransfServer"); worker.start();
        } catch (Exception e) { stopSelf(); }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "FadulTransf", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
        }
    }
    private Notification notification(String text) {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        return b.setContentTitle("FadulTransf").setContentText(text).setSmallIcon(android.R.drawable.stat_sys_upload_done).setOngoing(true).build();
    }

    private void handle(Socket socket) {
        try (Socket s = socket) {
            s.setSoTimeout(30000);
            InputStream in = s.getInputStream(); OutputStream out = new BufferedOutputStream(s.getOutputStream());
            String request = readLine(in); if (request == null || request.isEmpty()) return;
            String[] parts = request.split(" "); if (parts.length < 2) return;
            String method = parts[0], target = parts[1];
            java.util.Map<String,String> headers = new java.util.HashMap<>();
            String line; while ((line = readLine(in)) != null && !line.isEmpty()) { int c = line.indexOf(':'); if (c > 0) headers.put(line.substring(0,c).trim().toLowerCase(Locale.US), line.substring(c+1).trim()); }
            if ("GET".equals(method) && ("/".equals(target) || target.startsWith("/index"))) { send(out, 200, "text/html; charset=utf-8", html()); return; }
            if (!accessPin.equals(headers.get("x-fadultransf-pin"))) { send(out, 401, "application/json", "{\"error\":\"PIN incorrecto\"}"); return; }
            if ("GET".equals(method) && "/api/list".equals(target)) { send(out, 200, "application/json; charset=utf-8", listFiles()); return; }
            if ("GET".equals(method) && target.startsWith("/download?id=")) { download(out, target.substring("/download?id=".length())); return; }
            if ("PUT".equals(method) && target.startsWith("/upload/")) { upload(in, out, target.substring("/upload/".length()), headers); return; }
            send(out, 404, "text/plain; charset=utf-8", "No encontrado");
        } catch (Exception ignored) {}
    }

    private String listFiles() throws Exception {
        DocumentFile dir = folder(); StringBuilder b = new StringBuilder("{\"files\":["); boolean first = true;
        if (dir != null && dir.isDirectory()) for (DocumentFile f : dir.listFiles()) {
            if (!f.isFile()) continue; if (!first) b.append(','); first = false;
            b.append("{\"id\":\"").append(json(f.getName())).append("\",\"size\":").append(f.length()).append('}');
        }
        return b.append("]}").toString();
    }
    private void download(OutputStream out, String rawId) throws Exception {
        String id = URLDecoder.decode(rawId, "UTF-8"); DocumentFile file = find(id);
        if (file == null || !file.isFile()) { send(out, 404, "text/plain", "Archivo no encontrado"); return; }
        String name = file.getName() == null ? "archivo" : file.getName();
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Length: " + file.length() + "\r\nContent-Disposition: attachment; filename*=UTF-8''" + URLEncoder.encode(name,"UTF-8").replace("+","%20") + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        try (InputStream stream = getContentResolver().openInputStream(file.getUri())) { byte[] buf = new byte[16384]; int n; while (stream != null && (n = stream.read(buf)) != -1) out.write(buf,0,n); }
        out.flush();
    }
    private void upload(InputStream in, OutputStream out, String rawName, java.util.Map<String,String> headers) throws Exception {
        String name = URLDecoder.decode(rawName, "UTF-8").replace("/", "_").replace("\\", "_").trim();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) { send(out,400,"application/json","{\"error\":\"Nombre inválido\"}"); return; }
        int length; try { length = Integer.parseInt(headers.getOrDefault("content-length", "-1")); } catch (Exception e) { length = -1; }
        if (length < 0 || length > 2_000_000_000) { send(out, 411, "application/json", "{\"error\":\"Falta Content-Length\"}"); return; }
        DocumentFile dir = folder(); if (dir == null || !dir.canWrite()) { send(out,500,"application/json","{\"error\":\"Carpeta sin permiso de escritura\"}"); return; }
        DocumentFile old = findByName(name); if (old != null) old.delete();
        DocumentFile dest = dir.createFile("application/octet-stream", name); if (dest == null) { send(out,500,"application/json","{\"error\":\"No se pudo crear el archivo\"}"); return; }
        try (OutputStream fileOut = getContentResolver().openOutputStream(dest.getUri(), "w")) {
            byte[] buffer = new byte[16384]; int remaining = length;
            while (remaining > 0) { int n = in.read(buffer,0,Math.min(buffer.length,remaining)); if (n < 0) throw new java.io.EOFException(); fileOut.write(buffer,0,n); remaining -= n; }
        }
        send(out, 201, "application/json; charset=utf-8", "{\"ok\":true}");
    }
    private DocumentFile folder() { String u = getSharedPreferences("fadultransf", MODE_PRIVATE).getString("folder_uri", null); return u == null ? null : DocumentFile.fromTreeUri(this, Uri.parse(u)); }
    private DocumentFile find(String name) throws Exception { DocumentFile d=folder(); if(d!=null) for(DocumentFile f:d.listFiles()) if(name.equals(f.getName()) || name.equals(android.provider.DocumentsContract.getDocumentId(f.getUri()))) return f; return null; }
    private DocumentFile findByName(String name) throws Exception { DocumentFile d=folder(); if(d!=null) for(DocumentFile f:d.listFiles()) if(name.equals(f.getName())) return f; return null; }
    private String json(String s) { if(s==null)return ""; return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r",""); }
    private void send(OutputStream out, int code, String type, String body) throws Exception { byte[] data=body.getBytes(StandardCharsets.UTF_8); String status=code==200?"OK":code==201?"Created":code==401?"Unauthorized":code==404?"Not Found":code==411?"Length Required":code==400?"Bad Request":"Error"; out.write(("HTTP/1.1 "+code+" "+status+"\r\nContent-Type: "+type+"\r\nContent-Length: "+data.length+"\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8)); out.write(data); out.flush(); }
    private String readLine(InputStream in) throws Exception { ByteArrayOutputStream b=new ByteArrayOutputStream(); int c; while((c=in.read())!=-1){if(c=='\n')break;if(c!='\r')b.write(c);if(b.size()>8192)throw new Exception("line too long");} return c==-1&&b.size()==0?null:b.toString("UTF-8"); }

    private String html() { return "" +
"<!doctype html><html lang='es'><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><title>FadulTransf</title><style>body{font:16px system-ui;background:#f6f1ff;color:#282238;margin:0}.wrap{max-width:720px;margin:32px auto;padding:16px}.box{background:white;border-radius:20px;padding:22px;box-shadow:0 4px 20px #351b5818}h1{color:#7c3aed}button{background:#7c3aed;color:#fff;border:0;border-radius:12px;padding:12px 18px;font-weight:600;cursor:pointer}input{padding:12px;border:1px solid #d8c8f5;border-radius:10px;margin:5px 0;width:95%}.row{display:flex;justify-content:space-between;align-items:center;border-bottom:1px solid #eee;padding:12px 0}.muted{color:#6d6478;font-size:14px}#files{margin-top:16px}</style><div class='wrap'><div class='box'><h1>fadultransf</h1><p>Intercambio de archivos en tu red local</p><label>PIN mostrado en el teléfono</label><input id='pin' inputmode='numeric' maxlength='6' placeholder='Ingresá el PIN'><button onclick='load()'>Conectar</button><hr><h3>Subir archivos al teléfono</h3><input id='pick' type='file' multiple><button onclick='upload()'>Subir seleccionados</button><p id='msg' class='muted'></p><h3>Archivos disponibles</h3><div id='files' class='muted'>Ingresá el PIN para ver los archivos.</div></div></div><script>function hdr(){return {'x-fadultransf-pin':document.getElementById('pin').value}}async function load(){let r=await fetch('/api/list',{headers:hdr()});if(!r.ok){document.getElementById('files').textContent='PIN incorrecto o servidor no disponible';return}let d=await r.json(),el=document.getElementById('files');el.innerHTML='';if(!d.files.length){el.textContent='La carpeta está vacía.';return}d.files.forEach(f=>{let row=document.createElement('div');row.className='row';let n=document.createElement('span');n.textContent=f.id+' ('+fmt(f.size)+')';let a=document.createElement('a');a.textContent='Descargar';a.href='/download?id='+encodeURIComponent(f.id);a.onclick=async e=>{e.preventDefault();let x=await fetch(a.href,{headers:hdr()});if(!x.ok){alert('No se pudo descargar');return}let blob=await x.blob(),u=URL.createObjectURL(blob),z=document.createElement('a');z.href=u;z.download=f.id;z.click();URL.revokeObjectURL(u)};row.append(n,a);el.append(row)})}function fmt(n){return n<1024?n+' B':n<1048576?(n/1024).toFixed(1)+' KB':(n/1048576).toFixed(1)+' MB'}async function upload(){let files=[...document.getElementById('pick').files],m=document.getElementById('msg');if(!files.length){m.textContent='Elegí al menos un archivo.';return}for(let f of files){m.textContent='Subiendo '+f.name+'…';let r=await fetch('/upload/'+encodeURIComponent(f.name),{method:'PUT',headers:hdr(),body:f});if(!r.ok){m.textContent='Error al subir '+f.name;return}}m.textContent='Subida completada.';document.getElementById('pick').value='';load()}</script></html>"; }

    @Override public void onDestroy() { running=false; isRunning=false; ipAddress=""; accessPin=""; try { if(serverSocket!=null)serverSocket.close(); } catch(Exception ignored){} stopForeground(true); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
