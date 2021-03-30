package io.flutter.plugins.webviewflutter;

import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.JavascriptInterface;

import androidx.fragment.app.FragmentActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import io.flutter.plugin.common.MethodChannel;
import wendu.dsbridge.CompletionHandler;

/**
 * Create by wanjin on 3/4/21.
 */
public class JsApi {
//    private final MethodChannel methodChannel;
//
//    JsApi(MethodChannel methodChannel) {
//        this.methodChannel = methodChannel;
//    }

    @JavascriptInterface
    public String callFlutter(final Object jsonStr) {
//        new Handler(Looper.getMainLooper()).post(new Runnable() {
//            @Override
//            public void run() {
//                Map<String, Object> map = new HashMap<>();
//                try {
//                    JSONObject jsonObject = new JSONObject(jsonStr.toString());
//                    Iterator keys = jsonObject.keys();
//                    while (keys.hasNext()) {
//                        String key = (String) keys.next();//获取key
//                        String value = jsonObject.getString(key);//获取value值
//                        if (key.equals("callFlutter")) {
//                            map.put(key, value);
//                            Log.i("TAG", "成功调用");
//                            break;
//                        }
//                    }
//                } catch (JSONException e) {
//                    e.printStackTrace();
//                }
//            }
//        });
        return "错误调用";
    }

    @JavascriptInterface
    public void callFlutter(String jsonStr, CompletionHandler<String> handler) {
        try {
            JSONObject jsonObject = new JSONObject(jsonStr);
//      for (int i : jsonObject.)
            String dic = jsonObject.getString("callFlutter");
//            methodChannel.invokeMethod("callFlutter", dic);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @JavascriptInterface
    public String testSyn(Object msg) {
        return msg + "［syn call］";
    }

    @JavascriptInterface
    public void goPay(Object msg, CompletionHandler<String> handler) {
        handler.complete(msg + " [ asyn call]");
    }

    @JavascriptInterface
    public void callFlutter(Object msg, CompletionHandler<String> handler) {
//        methodChannel.invokeMethod("callFlutter",);
    }

    @JavascriptInterface
    public String testNoArgSyn(Object arg) throws JSONException {
        return "testNoArgSyn called [ syn call]";
    }

    @JavascriptInterface
    public void testNoArgAsyn(Object arg, CompletionHandler<String> handler) {
        handler.complete("testNoArgAsyn   called [ asyn call]");
    }


    //@JavascriptInterface
    //without @JavascriptInterface annotation can't be called
    public String testNever(Object arg) throws JSONException {
        JSONObject jsonObject = (JSONObject) arg;
        return jsonObject.getString("msg") + "[ never call]";
    }

    @JavascriptInterface
    public void callProgress(Object args, final CompletionHandler<Integer> handler) {

        new CountDownTimer(11000, 1000) {
            int i = 10;

            @Override
            public void onTick(long millisUntilFinished) {
                //setProgressData can be called many times util complete be called.
                handler.setProgressData((i--));

            }

            @Override
            public void onFinish() {
                //complete the js invocation with data; handler will be invalid when complete is called
                handler.complete(0);

            }
        }.start();
    }
}
