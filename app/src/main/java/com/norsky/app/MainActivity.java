package com.norsky.app;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.DownloadListener;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import java.io.File;

public class MainActivity extends Activity {

    private static final String URL = "http://192.168.1.133:8080/master";

    private WebView web;
    private WifiManager.WifiLock wifiLock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.setWebViewClient(new WebViewClient());

        web.setDownloadListener(new DownloadListener() {
            @Override public void onDownloadStart(String url, String ua, String cd, String mime, long len) {
                downloadAndInstall(url);
            }
        });

        setContentView(web);
        web.loadUrl(URL);

        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wm != null) {
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "norsky:wifilock");
            wifiLock.setReferenceCounted(false);
            wifiLock.acquire();
        }
    }

    private void downloadAndInstall(String url) {
        try {
            Toast.makeText(this, "Downloading update\u2026", Toast.LENGTH_SHORT).show();
            final File dest = new File(getExternalFilesDir(null), "update.apk");
            if (dest.exists()) dest.delete();
            DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
            req.setTitle("App update");
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationUri(Uri.fromFile(dest));
            req.setMimeType("application/vnd.android.package-archive");
            final DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            final long id = dm.enqueue(req);
            BroadcastReceiver rec = new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent i) {
                    if (i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) == id) {
                        try { unregisterReceiver(this); } catch (Exception ignored) {}
                        installApk(dest);
                    }
                }
            };
            IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(rec, filter, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(rec, filter);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Update download failed", Toast.LENGTH_LONG).show();
        }
    }

    private void installApk(File f) {
        try {
            Uri uri;
            if (Build.VERSION.SDK_INT >= 24) {
                uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
            } else {
                uri = Uri.fromFile(f);
            }
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Install failed", Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onResume() { super.onResume(); hideSystemUi(); }
    @Override public void onWindowFocusChanged(boolean hasFocus) { super.onWindowFocusChanged(hasFocus); if (hasFocus) hideSystemUi(); }
    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
              | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
              | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }
    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) { if (web != null) web.reload(); return true; }
        return super.onKeyDown(keyCode, event);
    }
    @Override protected void onDestroy() {
        if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
