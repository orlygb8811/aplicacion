package com.inventariodeagujas.app;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.webkit.*;
import android.view.WindowManager;
import android.content.res.Configuration;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
public class MainActivity extends Activity {
 private static final String HOST="appassets.androidplatform.net";
 private static final int PICK=1001,SAVE=1002;
 private WebView view;
 private ValueCallback<Uri[]> chooser;
 private String backup;
 @Override public void onCreate(Bundle b){
 super.onCreate(b);
 view=new WebView(this);setContentView(view);
 WebSettings settings=view.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);
 settings.setAllowFileAccess(false);settings.setAllowContentAccess(true);settings.setDefaultTextEncodingName("UTF-8");
 settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
 view.addJavascriptInterface(new Bridge(),"InventoryBridge");
 view.setWebViewClient(new WebViewClient(){
  @Override public WebResourceResponse shouldInterceptRequest(WebView w,WebResourceRequest request){
   Uri u=request.getUrl();
   if("https".equals(u.getScheme())&&HOST.equals(u.getHost())&&u.getPath().startsWith("/assets/")){
    String file=u.getPath().substring(8);
    if(file.contains("..")||file.contains("/")||file.isEmpty())return null;
    try{
     InputStream stream=getAssets().open(file);
     String mime=file.endsWith(".html")?"text/html":file.endsWith(".webmanifest")?"application/manifest+json":file.endsWith(".png")?"image/png":file.endsWith(".js")?"application/javascript":"text/plain";
     return new WebResourceResponse(mime,"UTF-8",stream);
    }catch(Exception ignored){return null;}
   }return null;
  }
  @Override public boolean shouldOverrideUrlLoading(WebView w,WebResourceRequest request){
   Uri u=request.getUrl();
   if(HOST.equals(u.getHost()))return false;
   if("https".equals(u.getScheme())||"http".equals(u.getScheme())){open(u.toString());return true;}
   return false;
  }
 });
 view.setWebChromeClient(new WebChromeClient(){
  @Override public boolean onShowFileChooser(WebView w,ValueCallback<Uri[]> cb,FileChooserParams params){
   if(chooser!=null)chooser.onReceiveValue(null);
   chooser=cb;
   Intent intent=new Intent(Intent.ACTION_GET_CONTENT);
   String[] a=params.getAcceptTypes();String type="*/*";
   if(a!=null)for(String x:a){if(x!=null&&!x.trim().isEmpty()){type=x;break;}}
   intent.setType(type);intent.addCategory(Intent.CATEGORY_OPENABLE);
   startActivityForResult(Intent.createChooser(intent,"Seleccionar archivo"),PICK);
   return true;
  }
 });
 view.loadUrl("https://"+HOST+"/assets/index.html");
 }
 private void open(String url){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}catch(Exception ignored){}}
 public class Bridge {
  @JavascriptInterface public void openExternal(String url){if(url!=null&&(url.startsWith("https://")||url.startsWith("http://")))runOnUiThread(()->open(url));}
  @JavascriptInterface public void saveJson(String filename,String content){
   if(content==null||content.length()>8000000)return;
   backup=content;
   runOnUiThread(()->{
    Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.setType("application/json");intent.putExtra(Intent.EXTRA_TITLE,filename==null?"inventario-de-agujas-backup.json":filename.replaceAll("[^A-Za-z0-9_.-]","_"));
    startActivityForResult(intent,SAVE);
   });
  }
 }
 @Override protected void onActivityResult(int req,int result,Intent data){
  super.onActivityResult(req,result,data);
  if(req==PICK){
   if(chooser!=null){chooser.onReceiveValue(result==RESULT_OK&&data!=null&&data.getData()!=null?new Uri[]{data.getData()}:null);chooser=null;}
  }else if(req==SAVE){
   if(result==RESULT_OK&&data!=null&&data.getData()!=null&&backup!=null)
    try(OutputStream out=getContentResolver().openOutputStream(data.getData())){out.write(backup.getBytes(StandardCharsets.UTF_8));}catch(Exception ignored){}
   backup=null;
  }
 }
 @Override public void onBackPressed(){if(view!=null&&view.canGoBack())view.goBack();else super.onBackPressed();}
 @Override protected void onDestroy(){if(view!=null)view.destroy();super.onDestroy();}
}