package com.norsky.app;

import android.app.Activity;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Thin full-screen WebView shell. All the real app (UI, money logic, everything)
 * is the HTML served by the Pub Kiosk server, so feature updates are done by
 * editing the HTML on the server - this app almost never needs rebuilding.
 */
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
        s.setDomStorageEnabled(true);      // localStorage - the offline queue depends on this
        s.setDatabaseEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.setWebViewClient(new WebViewClient());   // keep navigation inside the WebView
        setContentView(web);
        web.loadUrl(URL);

        // Hold the Wi-Fi radio at full power so Android can't drop it into power-save
        // and get the driver stuck - this targets the tablets losing Wi-Fi.
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wm != null) {
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "norsky:wifilock");
            wifiLock.setReferenceCounted(false);
            wifiLock.acquire();
        }
    }

    @Override protected void onResume() { super.onResume(); hideSystemUi(); }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {         // Back reloads instead of exiting
            if (web != null) web.reload();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override protected void onDestroy() {
        if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
