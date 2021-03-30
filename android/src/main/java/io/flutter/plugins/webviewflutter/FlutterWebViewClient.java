// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.webviewflutter;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.webkit.WebResourceErrorCompat;
import androidx.webkit.WebViewClientCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.flutter.plugin.common.MethodChannel;
import wendu.dsbridge.CompletionHandler;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

// We need to use WebViewClientCompat to get
// shouldOverrideUrlLoading(WebView view, WebResourceRequest request)
// invoked by the webview on older Android devices, without it pages that use iframes will
// be broken when a navigationDelegate is set on Android version earlier than N.
class FlutterWebViewClient {
  private static final String TAG = "FlutterWebViewClient";
  private final MethodChannel methodChannel;
  private boolean hasNavigationDelegate;

  FlutterWebViewClient(MethodChannel methodChannel) {
    this.methodChannel = methodChannel;
  }

  private static String errorCodeToString(int errorCode) {
    switch (errorCode) {
      case WebViewClient.ERROR_AUTHENTICATION:
        return "authentication";
      case WebViewClient.ERROR_BAD_URL:
        return "badUrl";
      case WebViewClient.ERROR_CONNECT:
        return "connect";
      case WebViewClient.ERROR_FAILED_SSL_HANDSHAKE:
        return "failedSslHandshake";
      case WebViewClient.ERROR_FILE:
        return "file";
      case WebViewClient.ERROR_FILE_NOT_FOUND:
        return "fileNotFound";
      case WebViewClient.ERROR_HOST_LOOKUP:
        return "hostLookup";
      case WebViewClient.ERROR_IO:
        return "io";
      case WebViewClient.ERROR_PROXY_AUTHENTICATION:
        return "proxyAuthentication";
      case WebViewClient.ERROR_REDIRECT_LOOP:
        return "redirectLoop";
      case WebViewClient.ERROR_TIMEOUT:
        return "timeout";
      case WebViewClient.ERROR_TOO_MANY_REQUESTS:
        return "tooManyRequests";
      case WebViewClient.ERROR_UNKNOWN:
        return "unknown";
      case WebViewClient.ERROR_UNSAFE_RESOURCE:
        return "unsafeResource";
      case WebViewClient.ERROR_UNSUPPORTED_AUTH_SCHEME:
        return "unsupportedAuthScheme";
      case WebViewClient.ERROR_UNSUPPORTED_SCHEME:
        return "unsupportedScheme";
    }

    final String message =
        String.format(Locale.getDefault(), "Could not find a string for errorCode: %d", errorCode);
    throw new IllegalArgumentException(message);
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
    if (!hasNavigationDelegate) {
      return false;
    }
    notifyOnNavigationRequest(
        request.getUrl().toString(), request.getRequestHeaders(), view, request.isForMainFrame());
    // We must make a synchronous decision here whether to allow the navigation or not,
    // if the Dart code has set a navigation delegate we want that delegate to decide whether
    // to navigate or not, and as we cannot get a response from the Dart delegate synchronously we
    // return true here to block the navigation, if the Dart delegate decides to allow the
    // navigation the plugin will later make an addition loadUrl call for this url.
    //
    // Since we cannot call loadUrl for a subframe, we currently only allow the delegate to stop
    // navigations that target the main frame, if the request is not for the main frame
    // we just return false to allow the navigation.
    //
    // For more details see: https://github.com/flutter/flutter/issues/25329#issuecomment-464863209
    return request.isForMainFrame();
  }

  boolean shouldOverrideUrlLoading(WebView view, String url) {
    if (!hasNavigationDelegate) {
      return false;
    }
    // This version of shouldOverrideUrlLoading is only invoked by the webview on devices with
    // webview versions  earlier than 67(it is also invoked when hasNavigationDelegate is false).
    // On these devices we cannot tell whether the navigation is targeted to the main frame or not.
    // We proceed assuming that the navigation is targeted to the main frame. If the page had any
    // frames they will be loaded in the main frame instead.
    Log.w(
        TAG,
        "Using a navigationDelegate with an old webview implementation, pages with frames or iframes will not work");
    notifyOnNavigationRequest(url, null, view, true);
    return true;
  }

  private void onPageStarted(WebView view, String url) {
    Map<String, Object> args = new HashMap<>();
    args.put("url", url);
    methodChannel.invokeMethod("onPageStarted", args);
  }

  private void onPageFinished(WebView view, String url) {
    Map<String, Object> args = new HashMap<>();
    args.put("url", url);
    methodChannel.invokeMethod("onPageFinished", args);
  }

  private void onWebResourceError(
      final int errorCode, final String description, final String failingUrl) {
    final Map<String, Object> args = new HashMap<>();
    args.put("errorCode", errorCode);
    args.put("description", description);
    args.put("errorType", FlutterWebViewClient.errorCodeToString(errorCode));
    args.put("failingUrl", failingUrl);
    methodChannel.invokeMethod("onWebResourceError", args);
  }

  private void notifyOnNavigationRequest(
      String url, Map<String, String> headers, WebView webview, boolean isMainFrame) {
    HashMap<String, Object> args = new HashMap<>();
    args.put("url", url);
    args.put("isForMainFrame", isMainFrame);
    if (isMainFrame) {
      methodChannel.invokeMethod(
          "navigationRequest", args, new OnNavigationRequestResult(url, headers, webview));
    } else {
      methodChannel.invokeMethod("navigationRequest", args);
    }
  }

  // This method attempts to avoid using WebViewClientCompat due to bug
  // https://bugs.chromium.org/p/chromium/issues/detail?id=925887. Also, see
  // https://github.com/flutter/flutter/issues/29446.
  WebViewClient createWebViewClient(boolean hasNavigationDelegate) {
    this.hasNavigationDelegate = hasNavigationDelegate;

    if (!hasNavigationDelegate || android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      return internalCreateWebViewClient();
    }

    return internalCreateWebViewClientCompat();
  }

  private WebViewClient internalCreateWebViewClient() {
    return new WebViewClient() {
      @TargetApi(Build.VERSION_CODES.N)
      @Override
      public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        return FlutterWebViewClient.this.shouldOverrideUrlLoading(view, request);
      }

      @Override
      public void onPageStarted(WebView view, String url, Bitmap favicon) {
        FlutterWebViewClient.this.onPageStarted(view, url);
      }

      @Override
      public void onPageFinished(WebView view, String url) {
        FlutterWebViewClient.this.onPageFinished(view, url);
      }

      @TargetApi(Build.VERSION_CODES.M)
      @Override
      public void onReceivedError(
          WebView view, WebResourceRequest request, WebResourceError error) {
        FlutterWebViewClient.this.onWebResourceError(
            error.getErrorCode(), error.getDescription().toString(), request.getUrl().toString());
      }

      @Override
      public void onReceivedError(
          WebView view, int errorCode, String description, String failingUrl) {
        FlutterWebViewClient.this.onWebResourceError(errorCode, description, failingUrl);
      }

      @Override
      public void onUnhandledKeyEvent(WebView view, KeyEvent event) {
        // Deliberately empty. Occasionally the webview will mark events as having failed to be
        // handled even though they were handled. We don't want to propagate those as they're not
        // truly lost.
      }
    };
  }

  private WebViewClientCompat internalCreateWebViewClientCompat() {
    return new WebViewClientCompat() {
      @Override
      public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        return FlutterWebViewClient.this.shouldOverrideUrlLoading(view, request);
      }

      @Override
      public boolean shouldOverrideUrlLoading(WebView view, String url) {
        return FlutterWebViewClient.this.shouldOverrideUrlLoading(view, url);
      }

      @Override
      public void onPageStarted(WebView view, String url, Bitmap favicon) {
        FlutterWebViewClient.this.onPageStarted(view, url);
      }

      @Override
      public void onPageFinished(WebView view, String url) {
        FlutterWebViewClient.this.onPageFinished(view, url);
      }

      // This method is only called when the WebViewFeature.RECEIVE_WEB_RESOURCE_ERROR feature is
      // enabled. The deprecated method is called when a device doesn't support this.
      @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
      @SuppressLint("RequiresFeature")
      @Override
      public void onReceivedError(
          WebView view, WebResourceRequest request, WebResourceErrorCompat error) {
        FlutterWebViewClient.this.onWebResourceError(
            error.getErrorCode(), error.getDescription().toString(), request.getUrl().toString());
      }

      @Override
      public void onReceivedError(
          WebView view, int errorCode, String description, String failingUrl) {
        FlutterWebViewClient.this.onWebResourceError(errorCode, description, failingUrl);
      }

      @Override
      public void onUnhandledKeyEvent(WebView view, KeyEvent event) {
        // Deliberately empty. Occasionally the webview will mark events as having failed to be
        // handled even though they were handled. We don't want to propagate those as they're not
        // truly lost.
      }
    };
  }

  private static class OnNavigationRequestResult implements MethodChannel.Result {
    private final String url;
    private final Map<String, String> headers;
    private final WebView webView;

    private OnNavigationRequestResult(String url, Map<String, String> headers, WebView webView) {
      this.url = url;
      this.headers = headers;
      this.webView = webView;
    }

    @Override
    public void success(Object shouldLoad) {
      Boolean typedShouldLoad = (Boolean) shouldLoad;
      if (typedShouldLoad) {
        loadUrl();
      }
    }

    @Override
    public void error(String errorCode, String s1, Object o) {
      throw new IllegalStateException("navigationRequest calls must succeed");
    }

    @Override
    public void notImplemented() {
      throw new IllegalStateException(
          "navigationRequest must be implemented by the webview method channel");
    }

    private void loadUrl() {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        webView.loadUrl(url, headers);
      } else {
        webView.loadUrl(url);
      }
    }
  }

  /**
   * H5调用Flutter
   * syncCall
   * @param jsonStr
   * @return
   */
  boolean called = true;
  @JavascriptInterface
  public String callFlutter(final Object jsonStr) {
    new Handler(Looper.getMainLooper()).post(new Runnable() {
      @Override
      public void run() {
        Map<String, Object> map = jsonToMap(jsonStr.toString());
        try {
          JSONObject jsonObject = new JSONObject(jsonStr.toString());
          Iterator keys = jsonObject.keys();
          while (keys.hasNext()) {
            String key = (String) keys.next();//获取key
            String value = jsonObject.getString(key);//获取value值
            if (key.equals("callFlutter")) {
              methodChannel.invokeMethod(value, map);
              break;
            } else {
              called = false;
            }
          }
        } catch (JSONException e) {
          e.printStackTrace();
        }
      }
    });
    return called?"success":"failed";
  }

  /**
   * asyncCall
   * @param jsonStr
   * @param handler
   */
  @JavascriptInterface
  public void callFlutter(final String jsonStr, final CompletionHandler<String> handler) {
    new Handler(Looper.getMainLooper()).post(new Runnable() {
      @Override
      public void run() {
        try {
          Map<String, Object> map = jsonToMap(jsonStr.toString());
          JSONObject jsonObject = new JSONObject(jsonStr.toString());
          Iterator keys = jsonObject.keys();
          while (keys.hasNext()){
            String key = (String) keys.next();//获取key
            String value = jsonObject.getString(key);//获取value值
            if (key.equals("callFlutter")){
              methodChannel.invokeMethod(value, map, new MethodChannel.Result() {
                @Override
                public void success(@Nullable Object result) {
                  JSONObject jsonObj=new JSONObject(jsonToMap(result.toString()));
                  handler.complete(jsonObj.toString());
                }

                @Override
                public void error(String errorCode, @Nullable String errorMessage, @Nullable Object errorDetails) {
                  Log.e(TAG, "error="+errorCode);
                }

                @Override
                public void notImplemented() {
                  Log.e(TAG, "notImplemented");
                }
              });
              break;
            }else{
              called = false;
            }
          }
        } catch (JSONException e) {
          e.printStackTrace();
        }
      }
    });
    if (!called){
      Map<String, Object> map = new HashMap<>();
      map.put("code","-1");
      map.put("result","please contain json key with 'callFlutter'!");
      JSONObject jsonObj=new JSONObject(map);
      handler.complete(jsonObj.toString());
    }
  }
  //Object转Map
//  public static Map<String, Object> getObjectToMap(Object obj) throws IllegalAccessException {
//    Map<String, Object> map = new HashMap<String, Object>();
//    Class<?> cla = obj.getClass();
//    Field[] fields = cla.getDeclaredFields();
//    for (Field field : fields) {
//      field.setAccessible(true);
//      String keyName = field.getName();
//      Object value = field.get(obj);
//      if (value == null)
//        value = "";
//      map.put(keyName, value);
//    }
//    return map;
//  }
  /**
   * @param content json字符串
   * @return  如果转换失败返回null,
   */
  Map<String, Object> jsonToMap(String content) {
    content = content.trim();
    Map<String, Object> result = new HashMap<>();
    try {
      if (content.charAt(0) == '[') {
        JSONArray jsonArray = new JSONArray(content);
        for (int i = 0; i < jsonArray.length(); i++) {
          Object value = jsonArray.get(i);
          if (value instanceof JSONArray || value instanceof JSONObject) {
            result.put(i + "", jsonToMap(value.toString().trim()));
          } else {
            result.put(i + "", jsonArray.getString(i));
          }
        }
      } else if (content.charAt(0) == '{'){
        JSONObject jsonObject = new JSONObject(content);
        Iterator<String> iterator = jsonObject.keys();
        while (iterator.hasNext()) {
          String key = iterator.next();
          Object value = jsonObject.get(key);
          if (value instanceof JSONArray || value instanceof JSONObject) {
            result.put(key, jsonToMap(value.toString().trim()));
          } else {
            result.put(key, value.toString().trim());
          }
        }
      }else {
        Log.e("异常", "json2Map: 字符串格式错误");
      }
    } catch (JSONException e) {
      Log.e("异常", "json2Map: ", e);
      result = null;
    }
    return result;
  }
}