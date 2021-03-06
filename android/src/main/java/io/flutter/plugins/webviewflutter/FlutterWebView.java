// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.webviewflutter;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.blankj.utilcode.util.KeyboardUtils;
import com.blankj.utilcode.util.ToastUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.platform.PlatformView;
import wendu.dsbridge.OnReturnValue;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static io.flutter.plugins.webviewflutter.InputAwareWebView.progressBar;

public class FlutterWebView implements PlatformView, MethodCallHandler {
  private final String TAG = "FlutterWebView:";
  private static final String JS_CHANNEL_NAMES_FIELD = "javascriptChannelNames";
  private final InputAwareWebView webView;
  private final MethodChannel methodChannel;
  private final FlutterWebViewClient flutterWebViewClient;
  private final Handler platformThreadHandler;
  private Context context;

  private ValueCallback<Uri> uploadMessage;
  private ValueCallback<Uri[]> uploadMessageAboveL;
  private WebChromeClient.FileChooserParams fileChooserParams;
//  private Uri imageUri;
  private final static int FILE_CHOOSER_RESULT_CODE = 10000;
  public static final int RESULT_OK = -1;
  private Uri fileUri;
  private Uri videoUri;
  private String[] PERMISSIONS = new String[]{
          Manifest.permission.CAMERA,
          Manifest.permission.READ_EXTERNAL_STORAGE,
          Manifest.permission.WRITE_EXTERNAL_STORAGE};
  private final static int REQUEST_PERMISSIONS_CODE=1008;

  //?????????????????????(x1, y1)???????????????????????????(x2, y2)
  private float x1 = 0;
  private float x2 = 0;
  private float y1 = 0;
  private float y2 = 0;

  // Verifies that a url opened by `Window.open` has a secure url.
  private class FlutterWebChromeClient extends WebChromeClient {
    @Override
    public boolean onCreateWindow(final WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
      final WebViewClient webViewClient = new WebViewClient() {
        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @Override
        public boolean shouldOverrideUrlLoading(@NonNull WebView view, @NonNull WebResourceRequest request) {
          final String url = request.getUrl().toString();
          if (!flutterWebViewClient.shouldOverrideUrlLoading(FlutterWebView.this.webView, request)) {
            webView.loadUrl(url);
          }
          return true;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
          if (!flutterWebViewClient.shouldOverrideUrlLoading(
                  FlutterWebView.this.webView, url)) {
            webView.loadUrl(url);
          }
          return true;
        }
      };

      final WebView newWebView = new WebView(view.getContext());
      newWebView.setWebViewClient(webViewClient);

      final WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
      transport.setWebView(newWebView);
      resultMsg.sendToTarget();

      return true;
    }

    @Override
    public void onProgressChanged(WebView view, int newProgress) {
      super.onProgressChanged(view, newProgress);
      Log.i(TAG, "onProgressChanged: "+newProgress);
      if (newProgress == 100) { //??????????????????????????????
        progressBar.setVisibility(GONE);
      } else {
        if (progressBar.getVisibility() == GONE)
          progressBar.setVisibility(VISIBLE);
        progressBar.setProgress(newProgress);
      }
    }

    // For Android < 3.0
    public void openFileChooser(ValueCallback<Uri> valueCallback) {
      Log.v(TAG, "openFileChooser Android < 3.0");
      uploadMessage = valueCallback;
      Intent i = new Intent(Intent.ACTION_GET_CONTENT);
      i.addCategory(Intent.CATEGORY_OPENABLE);
      i.setType("image/*");
      WebViewFlutterPlugin.activity.startActivityForResult(Intent.createChooser(i, "File Chooser"), FILE_CHOOSER_RESULT_CODE);
    }

    // For Android  >= 3.0
    public void openFileChooser(ValueCallback valueCallback, String acceptType) {
      Log.v(TAG, "openFileChooser Android  >= 3.0");
      uploadMessage = valueCallback;
      Intent i = new Intent(Intent.ACTION_GET_CONTENT);
      i.addCategory(Intent.CATEGORY_OPENABLE);
      i.setType("image/*");
      WebViewFlutterPlugin.activity.startActivityForResult(Intent.createChooser(i, "File Chooser"), FILE_CHOOSER_RESULT_CODE);
    }

    //For Android  >= 4.1
    public void openFileChooser(ValueCallback<Uri> valueCallback, String acceptType, String capture) {
      Log.v(TAG, "openFileChooser Android  >= 4.1");
      uploadMessage = valueCallback;
      Intent i = new Intent(Intent.ACTION_GET_CONTENT);
      i.addCategory(Intent.CATEGORY_OPENABLE);
      i.setType("image/*");
      WebViewFlutterPlugin.activity.startActivityForResult(Intent.createChooser(i, "File Chooser"), FILE_CHOOSER_RESULT_CODE);
    }

    // For Android >= 5.0
    @Override
    public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
      Log.v(TAG, "openFileChooser Android >= 5.0");
      uploadMessageAboveL = filePathCallback;
      FlutterWebView.this.fileChooserParams=fileChooserParams;
      requestPermission(fileChooserParams);
      return true;
    }
  }


  /**
   * ????????????
   */
  private void requestPermission(WebChromeClient.FileChooserParams fileChooserParams) {
    if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.M){
      if (!checkPermission()){
        //???????????????????????? ??????????????????????????????????????????
        WebViewFlutterPlugin.activity.requestPermissions(PERMISSIONS,REQUEST_PERMISSIONS_CODE);
      }else {
        //??????????????????????????????
        choosePicture(fileChooserParams);
      }
    }else {
      choosePicture(fileChooserParams);
    }
  }

  /**
   * ??????????????????????????????
   */
  private boolean checkPermission(){
    for (String permission:PERMISSIONS){
        if (ContextCompat.checkSelfPermission(WebViewFlutterPlugin.activity,
                permission)!= PackageManager.PERMISSION_GRANTED){
          return false;
        }
    }
    return true;
  }

  @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
  @SuppressWarnings("unchecked")
  FlutterWebView(
      final Context context,
      BinaryMessenger messenger,
      int id,
      Map<String, Object> params,
      View containerView) {

    this.context = context;

//    KeyboardUtils.fixAndroidBug5497(WebViewFlutterPlugin.activity);//??????Android??????????????????
    DisplayListenerProxy displayListenerProxy = new DisplayListenerProxy();
    DisplayManager displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
    displayListenerProxy.onPreWebViewInitialization(displayManager);
    webView = new InputAwareWebView(context, containerView);
    displayListenerProxy.onPostWebViewInitialization(displayManager);

    platformThreadHandler = new Handler(context.getMainLooper());
    // Allow local storage.
    webView.getSettings().setDomStorageEnabled(true);
    webView.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);

    // Multi windows is set with FlutterWebChromeClient by default to handle internal bug: b/159892679.
    webView.getSettings().setSupportMultipleWindows(true);//????????????
    webView.getSettings().setGeolocationEnabled(true);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      //??????http???https?????????????????????: http://blog.csdn.net/luofen521/article/details/51783914
      webView.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
    }
    //??????????????????????????????  ??????http or https
    webView.getSettings().setBlockNetworkImage(false);
    //??????????????????????????????
    webView.getSettings().setLoadsImagesAutomatically(true);
    webView.setWebChromeClient(new FlutterWebChromeClient());

    methodChannel = new MethodChannel(messenger, "plugins.flutter.io/webview_" + id);
    methodChannel.setMethodCallHandler(this);
    webView.addJavascriptObject(new FlutterWebViewClient(methodChannel),null);

    /**
     * ?????????????????????
     */
    webView.setOnTouchListener(new View.OnTouchListener() {
      @Override
      public boolean onTouch(View v, MotionEvent event) {
        //??????????????????
        if(event.getAction() == MotionEvent.ACTION_DOWN) {
          //????????????????????????
          x1 = event.getX();
          y1 = event.getY();
        }
        if(event.getAction() == MotionEvent.ACTION_UP) {
          //????????????????????????
          x2 = event.getX();
          y2 = event.getY();
          if(y1 - y2 > 50) {
            Log.i(TAG, "onTouch: ?????????");
          } else if(y2 - y1 > 50) {
            Log.i(TAG, "onTouch: ?????????");
          } else if(x1 - x2 > 50) {
            Log.i(TAG, "onTouch: ?????????");
          } else if(x2 - x1 > 50) {
            Log.i(TAG, "onTouch: ?????????");
          }else{
            KeyboardUtils.hideSoftInput(WebViewFlutterPlugin.activity);
          }
        }
        return false;
      }
    });

    flutterWebViewClient = new FlutterWebViewClient(methodChannel);
    Map<String, Object> settings = (Map<String, Object>) params.get("settings");
    if (settings != null) applySettings(settings);

    if (params.containsKey(JS_CHANNEL_NAMES_FIELD)) {
      List<String> names = (List<String>) params.get(JS_CHANNEL_NAMES_FIELD);
      if (names != null) registerJavaScriptChannelNames(names);
    }

    Integer autoMediaPlaybackPolicy = (Integer) params.get("autoMediaPlaybackPolicy");
    if (autoMediaPlaybackPolicy != null) updateAutoMediaPlaybackPolicy(autoMediaPlaybackPolicy);
    if (params.containsKey("userAgent")) {
      String userAgent = (String) params.get("userAgent");
      updateUserAgent(userAgent);
    }
    if (params.containsKey("initialUrl")) {
      String url = (String) params.get("initialUrl");
      webView.loadUrl(url);
    }
  }

  @Override
  public View getView() {
    return webView;
  }

  // @Override
  // This is overriding a method that hasn't rolled into stable Flutter yet. Including the
  // annotation would cause compile time failures in versions of Flutter too old to include the new
  // method. However leaving it raw like this means that the method will be ignored in old versions
  // of Flutter but used as an override anyway wherever it's actually defined.
  // TODO(mklim): Add the @Override annotation once flutter/engine#9727 rolls to stable.
  public void onInputConnectionUnlocked() {
    webView.unlockInputConnection();
  }

  // @Override
  // This is overriding a method that hasn't rolled into stable Flutter yet. Including the
  // annotation would cause compile time failures in versions of Flutter too old to include the new
  // method. However leaving it raw like this means that the method will be ignored in old versions
  // of Flutter but used as an override anyway wherever it's actually defined.
  // TODO(mklim): Add the @Override annotation once flutter/engine#9727 rolls to stable.
  public void onInputConnectionLocked() {
    webView.lockInputConnection();
  }

  // @Override
  // This is overriding a method that hasn't rolled into stable Flutter yet. Including the
  // annotation would cause compile time failures in versions of Flutter too old to include the new
  // method. However leaving it raw like this means that the method will be ignored in old versions
  // of Flutter but used as an override anyway wherever it's actually defined.
  // TODO(mklim): Add the @Override annotation once stable passes v1.10.9.
  public void onFlutterViewAttached(View flutterView) {
    webView.setContainerView(flutterView);
  }

  // @Override
  // This is overriding a method that hasn't rolled into stable Flutter yet. Including the
  // annotation would cause compile time failures in versions of Flutter too old to include the new
  // method. However leaving it raw like this means that the method will be ignored in old versions
  // of Flutter but used as an override anyway wherever it's actually defined.
  // TODO(mklim): Add the @Override annotation once stable passes v1.10.9.
  public void onFlutterViewDetached() {
    webView.setContainerView(null);
  }

  @Override
  public void onMethodCall(MethodCall methodCall, final Result result) {
    if (methodCall.method.contains("ori_")){
      if (methodCall.method.equals("ori_payResult")){
        Map<String,Object> map = (Map<String, Object>) methodCall.arguments;
        String code = (String) map.get("code");
        String ordersn = (String) map.get("ordersn");
//        List<String> list = new ArrayList<>();
//        list.add(code);
//        list.add(ordersn);
        webView.callHandler(methodCall.method, new Object[]{code,ordersn},new OnReturnValue<Integer>(){
          @Override
          public void onValue(Integer retValue) {
            result.success("ori_payResult????????????????????????success");
          }
        });
      }else {
        result.success("backkey????????????????????????valueeeee");
      }
      return;
    }
    switch (methodCall.method) {
      case "loadUrl":
        loadUrl(methodCall, result);
        break;
      case "updateSettings":
        updateSettings(methodCall, result);
        break;
      case "canGoBack":
        canGoBack(result);
        break;
      case "canGoForward":
        canGoForward(result);
        break;
      case "goBack":
        goBack(result);
        break;
      case "goForward":
        goForward(result);
        break;
      case "reload":
        reload(result);
        break;
      case "currentUrl":
        currentUrl(result);
        break;
      case "evaluateJavascript":
        evaluateJavaScript(methodCall, result);
        break;
      case "addJavascriptChannels":
        addJavaScriptChannels(methodCall, result);
        break;
      case "removeJavascriptChannels":
        removeJavaScriptChannels(methodCall, result);
        break;
      case "clearCache":
        clearCache(result);
        break;
      case "getTitle":
        getTitle(result);
        break;
      case "scrollTo":
        scrollTo(methodCall, result);
        break;
      case "scrollBy":
        scrollBy(methodCall, result);
        break;
      case "getScrollX":
        getScrollX(result);
        break;
      case "getScrollY":
        getScrollY(result);
        break;
      default:
        result.notImplemented();
    }
  }

  @SuppressWarnings("unchecked")
  private void loadUrl(MethodCall methodCall, Result result) {
    Map<String, Object> request = (Map<String, Object>) methodCall.arguments;
    String url = (String) request.get("url");
    Map<String, String> headers = (Map<String, String>) request.get("headers");
    if (headers == null) {
      headers = Collections.emptyMap();
    }
    webView.loadUrl(url, headers);
    result.success(null);
  }

  private void canGoBack(Result result) {
    result.success(webView.canGoBack());
  }

  private void canGoForward(Result result) {
    result.success(webView.canGoForward());
  }

  private void goBack(Result result) {
    if (webView.canGoBack()) {
      webView.goBack();
    }
    result.success(null);
  }

  private void goForward(Result result) {
    if (webView.canGoForward()) {
      webView.goForward();
    }
    result.success(null);
  }

  private void reload(Result result) {
    webView.reload();
    result.success(null);
  }

  private void currentUrl(Result result) {
    result.success(webView.getUrl());
  }

  @SuppressWarnings("unchecked")
  private void updateSettings(MethodCall methodCall, Result result) {
    applySettings((Map<String, Object>) methodCall.arguments);
    result.success(null);
  }

  @TargetApi(Build.VERSION_CODES.KITKAT)
  private void evaluateJavaScript(MethodCall methodCall, final Result result) {
    String jsString = (String) methodCall.arguments;
    if (jsString == null) {
      throw new UnsupportedOperationException("JavaScript string cannot be null");
    }
    webView.evaluateJavascript(
        jsString,
        new android.webkit.ValueCallback<String>() {
          @Override
          public void onReceiveValue(String value) {
            result.success(value);
          }
        });
  }

  @SuppressWarnings("unchecked")
  private void addJavaScriptChannels(MethodCall methodCall, Result result) {
    List<String> channelNames = (List<String>) methodCall.arguments;
    registerJavaScriptChannelNames(channelNames);
    result.success(null);
  }

  @SuppressWarnings("unchecked")
  private void removeJavaScriptChannels(MethodCall methodCall, Result result) {
    List<String> channelNames = (List<String>) methodCall.arguments;
    for (String channelName : channelNames) {
      webView.removeJavascriptInterface(channelName);
    }
    result.success(null);
  }

  private void clearCache(Result result) {
    webView.clearCache(true);
    WebStorage.getInstance().deleteAllData();
    result.success(null);
  }

  private void getTitle(Result result) {
    result.success(webView.getTitle());
  }

  private void scrollTo(MethodCall methodCall, Result result) {
    Map<String, Object> request = methodCall.arguments();
    int x = (int) request.get("x");
    int y = (int) request.get("y");

    webView.scrollTo(x, y);

    result.success(null);
  }

  private void scrollBy(MethodCall methodCall, Result result) {
    Map<String, Object> request = methodCall.arguments();
    int x = (int) request.get("x");
    int y = (int) request.get("y");

    webView.scrollBy(x, y);
    result.success(null);
  }

  private void getScrollX(Result result) {
    result.success(webView.getScrollX());
  }

  private void getScrollY(Result result) {
    result.success(webView.getScrollY());
  }

  private void applySettings(Map<String, Object> settings) {
    for (String key : settings.keySet()) {
      switch (key) {
        case "jsMode":
          Integer mode = (Integer) settings.get(key);
          if (mode != null) updateJsMode(mode);
          break;
        case "hasNavigationDelegate":
          final boolean hasNavigationDelegate = (boolean) settings.get(key);

          final WebViewClient webViewClient =
              flutterWebViewClient.createWebViewClient(hasNavigationDelegate);

          webView.setWebViewClient(webViewClient);
          break;
        case "debuggingEnabled":
          final boolean debuggingEnabled = (boolean) settings.get(key);

          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            webView.setWebContentsDebuggingEnabled(debuggingEnabled);
          }
          break;
        case "gestureNavigationEnabled":
          break;
        case "userAgent":
          updateUserAgent((String) settings.get(key));
          break;
        default:
          throw new IllegalArgumentException("Unknown WebView setting: " + key);
      }
    }
  }

  private void updateJsMode(int mode) {
    switch (mode) {
      case 0: // disabled
        webView.getSettings().setJavaScriptEnabled(false);
        break;
      case 1: // unrestricted
        webView.getSettings().setJavaScriptEnabled(true);
        break;
      default:
        throw new IllegalArgumentException("Trying to set unknown JavaScript mode: " + mode);
    }
  }

  private void updateAutoMediaPlaybackPolicy(int mode) {
    // This is the index of the AutoMediaPlaybackPolicy enum, index 1 is always_allow, for all
    // other values we require a user gesture.
    boolean requireUserGesture = mode != 1;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      webView.getSettings().setMediaPlaybackRequiresUserGesture(requireUserGesture);
    }
  }

  private void registerJavaScriptChannelNames(List<String> channelNames) {
    for (String channelName : channelNames) {
      webView.addJavascriptInterface(
          new JavaScriptChannel(methodChannel, channelName, platformThreadHandler), channelName);
    }
  }

  private void updateUserAgent(String userAgent) {
    webView.getSettings().setUserAgentString(userAgent);
  }

  @Override
  public void dispose() {
    methodChannel.setMethodCallHandler(null);
    webView.dispose();
    webView.destroy();
  }

  private void choosePicture(WebChromeClient.FileChooserParams fileChooserParams) {
    final String[] acceptTypes = getSafeAcceptedTypes(fileChooserParams);
    List<Intent> intentList = new ArrayList<Intent>();
    fileUri = null;
    videoUri = null;
    if (acceptsImages(acceptTypes)) {
      Intent takePhotoIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
      fileUri = getOutputFilename(MediaStore.ACTION_IMAGE_CAPTURE);
      takePhotoIntent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);
      intentList.add(takePhotoIntent);
    }
    if (acceptsVideo(acceptTypes)) {
      Intent takeVideoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
      videoUri = getOutputFilename(MediaStore.ACTION_VIDEO_CAPTURE);
      takeVideoIntent.putExtra(MediaStore.EXTRA_OUTPUT, videoUri);
      intentList.add(takeVideoIntent);
    }
    Intent contentSelectionIntent;
    if (Build.VERSION.SDK_INT >= 21) {
      final boolean allowMultiple = fileChooserParams.getMode() == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE;
      contentSelectionIntent = fileChooserParams.createIntent();
      contentSelectionIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, allowMultiple);
    } else {
      contentSelectionIntent = new Intent(Intent.ACTION_GET_CONTENT);
      contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE);
      contentSelectionIntent.setType("*/*");
    }
    Intent[] intentArray = intentList.toArray(new Intent[intentList.size()]);

    Intent chooserIntent = Intent.createChooser(contentSelectionIntent, "???????????????");
    chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray);
//    Intent chooserIntent = new Intent(Intent.ACTION_CHOOSER);
//    chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent);
//    chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray);
    WebViewFlutterPlugin.activity.startActivityForResult(chooserIntent, FILE_CHOOSER_RESULT_CODE);
  }

  private Uri[] getSelectedFiles(Intent data) {
    // we have one files selected
    if (data.getData() != null) {
      String dataString = data.getDataString();
      if (dataString != null) {
        return new Uri[]{Uri.parse(dataString)};
      }
    }
    // we have multiple files selected
    if (data.getClipData() != null) {
      final int numSelectedFiles = data.getClipData().getItemCount();
      Uri[] result = new Uri[numSelectedFiles];
      for (int i = 0; i < numSelectedFiles; i++) {
        result[i] = data.getClipData().getItemAt(i).getUri();
      }
      return result;
    }
    return null;
  }

  public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults){
    if (requestCode==REQUEST_PERMISSIONS_CODE){//??????????????????
      boolean isAllGranted = true;
      for (int i=0;i<grantResults.length;i++) {
        if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
          if (grantResults[i]==PackageManager.PERMISSION_DENIED){
            ToastUtils.showShort("????????????????????????/????????????????????????????????????????????????????????????????????????");
          }
          isAllGranted = false;
          break;
        }
      }
      // ???????????????????????????
      if (isAllGranted&&fileChooserParams!=null){
        choosePicture(fileChooserParams);
      }else {
        uploadMessageAboveL.onReceiveValue(null);
        uploadMessageAboveL=null;
      }
      return false;
    }
    return false;
  }

  public boolean activityResult(int requestCode, int resultCode, Intent data) {
    boolean handled = false;
//    String dataString = data.getDataString();
//    ClipData clipData = data.getClipData();
    if (Build.VERSION.SDK_INT >= 21) {
      if (requestCode == FILE_CHOOSER_RESULT_CODE) {
        Uri[] results = null;
        if (resultCode == Activity.RESULT_OK) {
          if (fileUri != null &&data == null) {
            results = new Uri[]{fileUri};
          } else if (videoUri != null &&data == null) {
            results = new Uri[]{videoUri};
          }else if (data != null) {
            results = getSelectedFiles(data);
          }
        }
        if (uploadMessageAboveL != null) {
          uploadMessageAboveL.onReceiveValue(results);
          uploadMessageAboveL = null;
        }
        handled = true;
      }
    } else {
      if (requestCode == FILE_CHOOSER_RESULT_CODE) {
        Uri result = null;
        if (resultCode == RESULT_OK && data != null) {
          result = data.getData();
        }
        if (uploadMessage != null) {
          uploadMessage.onReceiveValue(result);
          uploadMessage = null;
        }
      }
      handled = true;
    }
    return handled;
  }

  private String[] getSafeAcceptedTypes(WebChromeClient.FileChooserParams params) {

    // the getAcceptTypes() is available only in api 21+
    // for lower level, we ignore it
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      return params.getAcceptTypes();
    }

    final String[] EMPTY = {};
    return EMPTY;
  }

  /**
   * ??????????????? ????????????????????????????????????
   */
  private Boolean acceptsImages(String[] types) {
    return isArrayEmpty(types) || arrayContainsString(types, "image");
  }

  private Boolean acceptsVideo(String[] types) {
    return isArrayEmpty(types) || arrayContainsString(types, "video");
  }
  private Uri getOutputFilename(String intentType) {
    String prefix = "";
    String suffix = "";

    if (intentType == MediaStore.ACTION_IMAGE_CAPTURE) {
      prefix = "image-";
      suffix = ".jpg";
    } else if (intentType == MediaStore.ACTION_VIDEO_CAPTURE) {
      prefix = "video-";
      suffix = ".mp4";
    }

    String packageName = context.getPackageName();
    File capturedFile = null;
    try {
      capturedFile = createCapturedFile(prefix, suffix);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return Uri.fromFile(capturedFile);
  }
  private Boolean isArrayEmpty(String[] arr) {
    // when our array returned from getAcceptTypes() has no values set from the
    // webview
    // i.e. <input type="file" />, without any "accept" attr
    // will be an array with one empty string element, afaik
    return arr.length == 0 || (arr.length == 1 && arr[0].length() == 0);
  }
  private Boolean arrayContainsString(String[] array, String pattern) {
    for (String content : array) {
      if (content.contains(pattern)) {
        return true;
      }
    }
    return false;
  }
  private File createCapturedFile(String prefix, String suffix) throws IOException {
    File file;
    String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
    String imageFileName = prefix + "_" + timeStamp;
    if (Build.VERSION.SDK_INT >= 30){
      File imageStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "lifeStore");
      if (!imageStorageDir.exists()) {
        imageStorageDir.mkdirs();
      }
      file = new File(imageStorageDir + File.separator+imageFileName+suffix);
    }else {
      file = File.createTempFile(imageFileName, suffix, context.getExternalFilesDir(null));
    }
    return file;
  }
}
