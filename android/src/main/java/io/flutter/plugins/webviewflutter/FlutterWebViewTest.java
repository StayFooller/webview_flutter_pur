// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.webviewflutter;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.util.Log;
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

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.platform.PlatformView;

public class FlutterWebViewTest implements PlatformView, MethodCallHandler {
  private final String TAG = "FlutterWebView:";
  private static final String JS_CHANNEL_NAMES_FIELD = "javascriptChannelNames";
  private final InputAwareWebView webView;
  private final MethodChannel methodChannel;
  private final FlutterWebViewClient flutterWebViewClient;
  private final Handler platformThreadHandler;

//  private Activity activity;
//  private Context context;
  private ValueCallback<Uri> uploadMessage;
  private ValueCallback<Uri[]> uploadMessageAboveL;
  private Uri imageUri;
  private final static int FILE_CHOOSER_RESULT_CODE = 10000;
  public static final int RESULT_OK = -1;
  private String[] PERMISSIONS = new String[]{
          "android.permission.CAMERA",
          "android.permission.READ_EXTERNAL_STORAGE",
          "android.permission.WRITE_EXTERNAL_STORAGE",};
  private Context context;

  // Verifies that a url opened by `Window.open` has a secure url.
  private class FlutterWebChromeClient extends WebChromeClient {
    @Override
    public boolean onCreateWindow(
        final WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
      final WebViewClient webViewClient =
          new WebViewClient() {
            @TargetApi(Build.VERSION_CODES.LOLLIPOP)
            @Override
            public boolean shouldOverrideUrlLoading(
                @NonNull WebView view, @NonNull WebResourceRequest request) {
              final String url = request.getUrl().toString();
              if (!flutterWebViewClient.shouldOverrideUrlLoading(
                  FlutterWebViewTest.this.webView, request)) {
                webView.loadUrl(url);
              }
              return true;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
              if (!flutterWebViewClient.shouldOverrideUrlLoading(
                  FlutterWebViewTest.this.webView, url)) {
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

    // For Android < 3.0
    public void openFileChooser(ValueCallback<Uri> valueCallback) {
      Log.v(TAG, "openFileChooser Android < 3.0");
      uploadMessage = valueCallback;
      choosePicture();
    }

    // For Android  >= 3.0
    public void openFileChooser(ValueCallback valueCallback, String acceptType) {
      Log.v(TAG, "openFileChooser Android  >= 3.0");
      uploadMessage = valueCallback;
      choosePicture();
    }

    //For Android  >= 4.1
    public void openFileChooser(ValueCallback<Uri> valueCallback, String acceptType, String capture) {
      Log.v(TAG, "openFileChooser Android  >= 4.1");
      uploadMessage = valueCallback;
      choosePicture();
    }

    // For Android >= 5.0
    @Override
    public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
      Log.v(TAG, "openFileChooser Android >= 5.0");
      uploadMessageAboveL = filePathCallback;
//      openImageChooserActivity();
      //添加储存权限
//      verifyStoragePermissions(WebViewFlutterPlugin.activity);
      //去寻找是否已经有了相机的权限
//        if (ContextCompat.checkSelfPermission(WebViewFlutterPlugin.activity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
//            //否则去请求相机权限
//            ActivityCompat.requestPermissions(WebViewFlutterPlugin.activity, new String[]{Manifest.permission.CAMERA}, 100);
//        }
//      choosePicture();
      choosePicture1(fileChooserParams);
      return true;
    }
  }

  private Uri fileUri;
  private Uri videoUri;

  private String[] getSafeAcceptedTypes(WebChromeClient.FileChooserParams params) {

    // the getAcceptTypes() is available only in api 21+
    // for lower level, we ignore it
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      return params.getAcceptTypes();
    }

    final String[] EMPTY = {};
    return EMPTY;
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
//    return FileProvider.getUriForFile(context, packageName + ".fileprovider", capturedFile);
    return Uri.fromFile(capturedFile);
  }
  private File createCapturedFile(String prefix, String suffix) throws IOException {
    String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
    String imageFileName = prefix + "_" + timeStamp;
    File storageDir = context.getExternalFilesDir(null);
    return File.createTempFile(imageFileName, suffix, storageDir);
  }
  private void choosePicture1(WebChromeClient.FileChooserParams fileChooserParams) {
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

    Intent chooserIntent = new Intent(Intent.ACTION_CHOOSER);
    chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent);
    chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray);
    WebViewFlutterPlugin.activity.startActivityForResult(chooserIntent, FILE_CHOOSER_RESULT_CODE);
  }

  /**
   * 判断是否开启存储权限
   *
   * @param activity
   */
  private void verifyStoragePermissions(Activity activity) {
    try {
      //检测是否有写的权限
      boolean permission = ActivityCompat.checkSelfPermission(activity,
              PERMISSIONS[0]) != PackageManager.PERMISSION_GRANTED;
      boolean permission1 = ActivityCompat.checkSelfPermission(activity,
              PERMISSIONS[1]) != PackageManager.PERMISSION_GRANTED;
      boolean permission2 = ActivityCompat.checkSelfPermission(activity,
              PERMISSIONS[2]) != PackageManager.PERMISSION_GRANTED;
      if (permission ||permission1||permission2) {
        // 没有权限，去申请权限，会弹出对话框
        ActivityCompat.requestPermissions(activity, PERMISSIONS, 1);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void choosePicture() {
    File imageStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "MyApp");
    if (!imageStorageDir.exists()) {
      imageStorageDir.mkdirs();
    }
    File file = new File(imageStorageDir + File.separator + "IMG_" + String.valueOf(System.currentTimeMillis()) + ".jpg");
    imageUri = Uri.fromFile(file);
    final List<Intent> cameraIntents = new ArrayList<Intent>();
    final Intent captureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
    final PackageManager packageManager = WebViewFlutterPlugin.activity.getPackageManager();
    final List<ResolveInfo> listCam = packageManager.queryIntentActivities(captureIntent, 0);
    for (ResolveInfo res : listCam) {
      final String packageName = res.activityInfo.packageName;
      final Intent i = new Intent(captureIntent);
      i.setComponent(new ComponentName(res.activityInfo.packageName, res.activityInfo.name));
      i.setPackage(packageName);
      i.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
      cameraIntents.add(i);
    }
    Intent i = new Intent(Intent.ACTION_GET_CONTENT);
    i.addCategory(Intent.CATEGORY_OPENABLE);
    i.setType("image/*");
    i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
    Intent chooserIntent = Intent.createChooser(i, "Image Chooser");
    chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, cameraIntents.toArray(new Parcelable[]{}));
//    Intent chooserIntent = new Intent(Intent.ACTION_CHOOSER);
//    chooserIntent.putExtra(Intent.EXTRA_INTENT, i);
//    chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, cameraIntents.toArray(new Parcelable[]{}));
    if (WebViewFlutterPlugin.activity != null){
      WebViewFlutterPlugin.activity.startActivityForResult(chooserIntent, FILE_CHOOSER_RESULT_CODE);
    } else {
      Log.v(TAG, "activity is null");
    }
  }

  @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
  @SuppressWarnings("unchecked")
  FlutterWebViewTest(
      final Context context,
      BinaryMessenger messenger,
      int id,
      Map<String, Object> params,
      View containerView) {

    this.context = context;

    DisplayListenerProxy displayListenerProxy = new DisplayListenerProxy();
    DisplayManager displayManager =
        (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
    displayListenerProxy.onPreWebViewInitialization(displayManager);
    webView = new InputAwareWebView(context, containerView);
    displayListenerProxy.onPostWebViewInitialization(displayManager);

    platformThreadHandler = new Handler(context.getMainLooper());
    // Allow local storage.
    webView.getSettings().setDomStorageEnabled(true);
    webView.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);

    // Multi windows is set with FlutterWebChromeClient by default to handle internal bug: b/159892679.
    webView.getSettings().setSupportMultipleWindows(true);//支持定位
    webView.getSettings().setGeolocationEnabled(true);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      //允许http及https混合请求，参阅: http://blog.csdn.net/luofen521/article/details/51783914
      webView.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
    }
    //是否阻塞加载网络图片  协议http or https
    webView.getSettings().setBlockNetworkImage(false);
    //是否自动加载网络图片
    webView.getSettings().setLoadsImagesAutomatically(true);
    webView.setWebChromeClient(new FlutterWebChromeClient());

    methodChannel = new MethodChannel(messenger, "plugins.flutter.io/webview_" + id);
    methodChannel.setMethodCallHandler(this);
    webView.addJavascriptObject(new FlutterWebViewClient(methodChannel),null);

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
  public void onMethodCall(MethodCall methodCall, Result result) {
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
        new ValueCallback<String>() {
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
  public boolean activityResult(int requestCode, int resultCode, Intent data) {
    boolean handled = false;
    if (Build.VERSION.SDK_INT >= 21) {
      if (requestCode == FILE_CHOOSER_RESULT_CODE) {
        Uri[] results = null;
        if (resultCode == Activity.RESULT_OK) {
          if (fileUri != null &&data == null) {
            results = new Uri[]{fileUri};
          } else if (videoUri != null &&data == null) {
            results = new Uri[]{videoUri};
          } else if (data != null) {
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
//    boolean handled = false;
//    if (requestCode == FILE_CHOOSER_RESULT_CODE) {
//      if (null == uploadMessage && null == uploadMessageAboveL)
//        return false;
//      Uri result = data == null || resultCode != RESULT_OK ? null : data.getData();
//      if (uploadMessageAboveL != null) {
//        onActivityResultAboveL(requestCode, resultCode, data);
//      } else if (uploadMessage != null) {
//        Log.e("result", result + "");
//        if (result == null) {
//          uploadMessage.onReceiveValue(imageUri);
//          uploadMessage = null;
//          Log.e("imageUri", imageUri + "");
//        } else {
//          uploadMessage.onReceiveValue(result);
//          uploadMessage = null;
//        }
//        handled = true;
//      }

//      if (null == uploadMessage && null == uploadMessageAboveL) {
//        return false;
//      }
//      Uri result = data == null || resultCode != RESULT_OK ? null : data.getData();
//      if (uploadMessageAboveL != null) {
//        onActivityResultAboveL(requestCode, resultCode, data);
//      } else if (uploadMessage != null && result != null) {
//        uploadMessage.onReceiveValue(result);
//        uploadMessage = null;
//      }
//    }
//    return handled;
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  private void onActivityResultAboveL(int requestCode, int resultCode, Intent data) {
    if (requestCode != FILE_CHOOSER_RESULT_CODE || uploadMessageAboveL == null) {
      return;
    }
    Uri[] results = null;
    if (resultCode == Activity.RESULT_OK) {
      if (data == null) {
        results = new Uri[]{imageUri};
      } else {
        String dataString = data.getDataString();
        ClipData clipData = data.getClipData();
        if (clipData != null) {
          results = new Uri[clipData.getItemCount()];
          for (int i = 0; i < clipData.getItemCount(); i++) {
            ClipData.Item item = clipData.getItemAt(i);
            results[i] = item.getUri();
          }
        }
        if (dataString != null)
          results = new Uri[]{Uri.parse(dataString)};
      }
    }
    if (results != null) {
      uploadMessageAboveL.onReceiveValue(results);
      uploadMessageAboveL = null;
    } else {
      results = new Uri[]{imageUri};
      uploadMessageAboveL.onReceiveValue(results);
      uploadMessageAboveL = null;
    }
    clearUploadMessage();
//    if (requestCode != FILE_CHOOSER_RESULT_CODE || uploadMessageAboveL == null) {
//      return;
//    }
//    Uri[] results = null;
//    if (resultCode == Activity.RESULT_OK) {
//      if (data != null) {
//        String dataString = data.getDataString();
//        ClipData clipData = data.getClipData();
//        if (clipData != null) {
//          results = new Uri[clipData.getItemCount()];
//          for (int i = 0; i < clipData.getItemCount(); i++) {
//            ClipData.Item item = clipData.getItemAt(i);
//            results[i] = item.getUri();
//          }
//        }
//        if (dataString != null)
//        {
//          results = new Uri[]{Uri.parse(dataString)};
//        }
//      }
//    }
//    uploadMessageAboveL.onReceiveValue(results);
//    uploadMessageAboveL = null;
  }

  /**
   * webview没有选择文件也要传null，防止下次无法执行
   */
  private void clearUploadMessage() {
    if (uploadMessageAboveL != null) {
      uploadMessageAboveL.onReceiveValue(null);
      uploadMessageAboveL = null;
    }
    if (uploadMessage != null) {
      uploadMessage.onReceiveValue(null);
      uploadMessage = null;
    }
  }
}
