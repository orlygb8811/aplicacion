package com.gradink.studio;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.graphics.Color;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import android.widget.Toast;
import java.io.OutputStream;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQUEST_IMAGE = 4201;
    private WebView webView;
    private ValueCallback<Uri[]> awaitingSelection;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            getWindow().setStatusBarColor(Color.rgb(12, 17, 27));
            getWindow().setNavigationBarColor(Color.rgb(12, 17, 27));
            webView = new WebView(this);
            WebSettings s = webView.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setAllowFileAccess(true);
            s.setAllowContentAccess(true);
            s.setDefaultTextEncodingName("UTF-8");
            webView.setWebViewClient(new WebViewClient());
            webView.setWebChromeClient(new WebChromeClient() {
                @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                    if (awaitingSelection != null) awaitingSelection.onReceiveValue(null);
                    awaitingSelection = callback;
                    try {
                        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType("image/*");
                        startActivityForResult(Intent.createChooser(intent, "Seleccionar diseño"), REQUEST_IMAGE);
                        return true;
                    } catch (Exception ex) {
                        awaitingSelection = null;
                        callback.onReceiveValue(null);
                        Toast.makeText(MainActivity.this, "No se pudo abrir la galería", Toast.LENGTH_LONG).show();
                        return true;
                    }
                }
            });
            webView.addJavascriptInterface(new ImageStorage(), "GradInkNative");
            setContentView(webView);
            webView.loadUrl("file:///android_asset/index.html");
        } catch (Throwable ex) {
            TextView error = new TextView(this);
            error.setText("Error al iniciar GradInk Studio:\n"+ex.toString());
            error.setTextSize(16);
            error.setPadding(28, 32, 28, 32);
            setContentView(error);
        }
    }

    @Override protected void onActivityResult(int req, int resultCode, Intent data) {
        super.onActivityResult(req, resultCode, data);
        if (req == REQUEST_IMAGE && awaitingSelection != null) {
            Uri[] selected = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                selected = new Uri[] { data.getData() };
            }
            awaitingSelection.onReceiveValue(selected);
            awaitingSelection = null;
        }
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    private class ImageStorage {
        @JavascriptInterface public String saveImage(String dataUrl, String fileName) {
            Uri created = null;
            try {
                if (dataUrl == null || !dataUrl.startsWith("data:image/png;base64,")) {
                    return "Formato de imagen no válido";
                }
                String name = fileName == null ? "GradInk_mapa.png" : fileName.replaceAll("[^a-zA-Z0-9_.-]", "_");
                if (!name.toLowerCase(Locale.ROOT).endsWith(".png")) name += ".png";
                byte[] bytes = Base64.decode(dataUrl.substring(dataUrl.indexOf(',')+1), Base64.DEFAULT);
                ContentResolver resolver = getContentResolver();
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES+"/GradInk Studio");
                    values.put(MediaStore.Images.Media.IS_PENDING, 1);
                }
                created = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (created == null) return "No fue posible crear el archivo";
                try (OutputStream out = resolver.openOutputStream(created)) {
                    if (out == null) throw new IllegalStateException("No se pudo abrir el destino");
                    out.write(bytes);
                }
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    ContentValues done = new ContentValues();
                    done.put(MediaStore.Images.Media.IS_PENDING, 0);
                    resolver.update(created, done, null, null);
                }
                return "OK";
            } catch (Exception ex) {
                if (created != null) try { getContentResolver().delete(created, null, null); } catch (Exception ignored) {}
                return "Error al guardar PNG: "+ex.getMessage();
            }
        }
    }
}
