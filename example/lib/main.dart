// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

// ignore_for_file: public_member_api_docs

import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:webview_flutter/webview_flutter.dart';

void main() => runApp(MaterialApp(home: WebViewExample()));

const String kNavigationExamplePage = '''
<!DOCTYPE html><html>
<head><title>Navigation Delegate Example</title></head>
<body>
<p>
The navigation delegate is set to block navigation to the youtube website.
</p>
<ul>
<ul><a href="https://www.youtube.com/">https://www.youtube.com/</a></ul>
<ul><a href="https://www.google.com/">https://www.google.com/</a></ul>
</ul>
</body>
</html>
''';

class WebViewExample extends StatefulWidget {
  @override
  _WebViewExampleState createState() => _WebViewExampleState();
}

class _WebViewExampleState extends State<WebViewExample> {
  final Completer<WebViewController> _controller =
      Completer<WebViewController>();
  WebViewController _webViewController;
  @override
  void initState() {
    super.initState();
    if (Platform.isAndroid) WebView.platform = SurfaceAndroidWebView();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Flutter WebView example'),
        // This drop down menu demonstrates that Flutter widgets can be shown over the web view.
        actions: <Widget>[
          NavigationControls(_controller.future),
          SampleMenu(_controller.future),
        ],
      ),
      // We're using a Builder here so we have a context that is below the Scaffold
      // to allow calling Scaffold.of(context) so we can show a snackbar.
      body: Builder(builder: (BuildContext context) {
        return WebView(
//          initialUrl: 'https://m.baidu.com',
//          initialUrl: 'http://192.168.10.66:8082/#/pages/shared_hotel/index?token=Bearer%20eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsImp0aSI6IjI5OGVmMTU3NTZiNGZjYzIwN2Y0ZDQxZTgyNDVlZDUyNzEwMjIyZjdkYjhlMmZhMDU3YmJkODZkMGJlYTA5NDE1NzQ4YTBiZTJiNDBiYjQxIn0.eyJhdWQiOiIzIiwianRpIjoiMjk4ZWYxNTc1NmI0ZmNjMjA3ZjRkNDFlODI0NWVkNTI3MTAyMjJmN2RiOGUyZmEwNTdiYmQ4NmQwYmVhMDk0MTU3NDhhMGJlMmI0MGJiNDEiLCJpYXQiOjE2MTU2MTY5NTYsIm5iZiI6MTYxNTYxNjk1NiwiZXhwIjoxNjQ3MTUyOTU2LCJzdWIiOiI3Iiwic2NvcGVzIjpbXSwiaXNzIjoidXNlciIsInN0b3JlX2lkIjo3fQ.HlD7yyQSY1J8uZfVQ_hvdi3UINYVPTgPSGK52Cc58Z4KbIB-ZQ73s6C05lt2uMPbkT--P0ACRjTdSYYxkCgIUrqJvWoPPXnibu3O_C60pss9iCo0BQoEOGSnzfs8xdsYrR0YWkO1Kxu8kp0PqrmOAWSu2RIOotyQmaf8lQV-G8msP9QFPHXL9Ro7tyekoP-b8nCk7hU5M9ikYMJFjfUJj85GyrI4aBlMkXBjZwaQUqXI3V0IOr_lLqJqAJb6YR-iAjfXpnyLoyeGmDNiU3yRti6HS4judAggLPIL3rzUbYfcsjRozEEo1WWa9n80-Iy3r16Cmv-Qcmo7FDGVSNodt4lkaZ5LJO2_g3Ix7TkctqaVyiJSr6ilmp48x8PeIQrUDwffhT4EmiGFRPMyoLAN7GOFZtZ5xwvrtSYE-mtJzJEYMZlHtjle98D-20-nd9SBBRdj9z8LOoIONsilk0pGcqn14mXInb0RtVhebr33N2Ug2Imo-6hyo1eW-rXaPYxJExwqOr_Fu4Qk3Tx4zDbIcwW-H6W1sTyPqFep69n3ZOGrGvDiM_nWD_jf_RNs6ATOBGsV1WTY0Zg-ZMcL32ANGpIKiCSz0AJGwlbNeNM0nz0QDhj3xr4oAJCv5l3CNRZUhDSMTj7ppFK8ws3gwAskSH3NNDEkBltzyx3F3fG_5w0',
          initialUrl: 'https://canteen-h5-dev.shall-buy.top/#/pages/shared_hotel/index?token=Bearer%20eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsImp0aSI6IjRlM2YzN2IxNDQwZGI1NTE3ZWNiNGMyMzc2NjU4NmI4ZjRlYjE0N2EyODJhY2FhMjUwMGI5NDA1MGY0NjViMGUzODJjM2ZhYjMwMTU0Y2E3In0.eyJhdWQiOiIzIiwianRpIjoiNGUzZjM3YjE0NDBkYjU1MTdlY2I0YzIzNzY2NTg2YjhmNGViMTQ3YTI4MmFjYWEyNTAwYjk0MDUwZjQ2NWIwZTM4MmMzZmFiMzAxNTRjYTciLCJpYXQiOjE2MTUzNDIwNjAsIm5iZiI6MTYxNTM0MjA2MCwiZXhwIjoxNjQ2ODc4MDYwLCJzdWIiOiIyIiwic2NvcGVzIjpbXSwiaXNzIjoidXNlciIsInN0b3JlX2lkIjoyfQ.gAkJzYEEU_KfOMNJ7QADbeSccAQrCvXyK3dCmMgwOe1qbCPb_iazZIwaR15dq6NprM9g7Ns1qLdajn5R8PGrys8Obu5Cey8t2qzCX2Zz6K3mTFyy1Bpye0iYsqLLdhnCQOrZgy_5_pA43HPeu1uPb8f9G-nBcKoMNPHHQVRiF2IKqE9j8GduIZbTI6qUJHO2cKuemis2VIZE_LxadMExZKs-QW2_n4xXMonrt9LYTGnHsqcjNjno3aw-FhugL00PewgVFllesZzTSXj2Q_IjfWYO5F4jGx1X7HO3Q2FtnHvBurz_H6IgQo7NZiesGd4zPcHH62URd8wTHwZKzBdpyGH6bEmKZoa-rREo72jMcuKarQxxYwukFtDVr3BSp_5fJRJMV8Jp6NtFqPemQjpI67uOpdVZUE_KMrG3yu-DUsRUnL44Yky95MFHDdTgZoIWxcwrGMhROE6EjZgCdssMZ5WaU9jRH8w7UbJawA0rXBMJ-AzUVVfpjczuN-M28IqLrOjcta7OM2Dsp0nhakKyOG3gUEbWMdB8P5R-S5OWqePcs_Zmyndlq67vFJsApTebKWccm02Mh_Vif1JCWwgYPynmvhRgBrsOHNnp6JejnnWYlugG2rFjKnPjgd2ah_a9o70u08kRLeEWEBsHPS2Ar1Bz2CQkl6U8I7i881Crsx0',
//          initialUrl: 'https://merchant-h5-dev.shall-buy.top/o2o/commodity?token=Bearer%20eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsImp0aSI6IjBiNmY0MTFhMmNhYzE2NTQzMDNlMTI2ZmJhNTI1ZWEyNmRhNGY0Mjc4Y2EwOGM1MjQ0MzUzOGRmODU5ODZjODA5ZWZkNmNkNTI2OGEzOGUxIn0.eyJhdWQiOiIzIiwianRpIjoiMGI2ZjQxMWEyY2FjMTY1NDMwM2UxMjZmYmE1MjVlYTI2ZGE0ZjQyNzhjYTA4YzUyNDQzNTM4ZGY4NTk4NmM4MDllZmQ2Y2Q1MjY4YTM4ZTEiLCJpYXQiOjE2MTQ3NTU5NzMsIm5iZiI6MTYxNDc1NTk3MywiZXhwIjoxNjQ2MjkxOTczLCJzdWIiOiIxMTkiLCJzY29wZXMiOltdLCJpc3MiOiJ1c2VyIiwic3RvcmVfaWQiOjIyMn0.YW9yvtE-wZjnhsqUXnqAlUWbF9gADDK8PZxes6lP0NOoPImAYbENo24uCGF9EYwsuuWCpa_TTqBJxaU9VheQvSbAlr259M3se_vASfSObaPVXnysWqBsMsyGw0XUaW4ZHRQrgdd5v5yhjKER7pz6oPCraXHNPdefo9SI36FWbJbj3caNBw3Dh3XVbqw7h97nNv57sod7G_8hRH54XnAC9cPkJmi8XaDemobjidKzYMVdm_bEevl9Augf_73PFwGsgMUHuuiNHgGxomfqxQzOYgziTJ7cwuswufaD2dObK7kkphRVoKCypL4rFnq3SWHr-VkVDaXfXOgz0-b1b7dW1VS8xpvfv3M8diggsM1GKaG7yqMs8rENUhT4Lms18DF3fdPYEnqe_ENtEd9lScFOu6hTaWqcyDAcZgok5wBxN8TPLfi34RLskBgl0hOmpE-76VM_7gNW6c3IVK3fYyKwijOapbzv54p_rdoTh2y066l83uNjsw5lI4Sax5EWV2Fy6-yCprqxz8MUwqxez1Sf3p5Zhz1rEkO6j_zmGO4CpV8skRzNT8NDtoaaFGXYUMsMLjLFQNiTEJ0yT3YDxp9E2E7q6HTtoPAviQMHImaHoZuUFeFMgx3FemV2Dd6fqOzbjJVTJJRziLFehWlGnqlVwK5JqEK1WAvvP63AUsqGyok',
          javascriptMode: JavascriptMode.unrestricted,
          onWebViewCreated: (WebViewController webViewController)  {
            _controller.complete(webViewController);
            _webViewController = webViewController;
          },
          // TODO(iskakaushik): Remove this when collection literals makes it to stable.
          // ignore: prefer_collection_literals
          javascriptChannels: <JavascriptChannel>[
            _toasterJavascriptChannel(context),
          ].toSet(),
          navigationDelegate: (NavigationRequest request) {
            if (request.url.startsWith('https://www.youtube.com/')) {
              print('blocking navigation to $request}');
              return NavigationDecision.prevent;
            }
            print('allowing navigation to $request');
            return NavigationDecision.navigate;
          },
          onPageStarted: (String url) {
            print('Page started loading: $url');
          },
          onPageFinished: (String url) {
            print('Page finished loading: $url');
          },
          callFlutter: (Map map){
            if(map['callFlutter'] == 'flu_goBack'){
              return {'status':'gobackSuccess'};
            }else if(map['callFlutter'] == 'flu_swipeChanged'){
              print(map['state']);
              return {'status':'gobackSuccess'};
            }else if(map['callFlutter'] == 'flu_goPay'){
                _webViewController.callOrigin({'ori_Method':'ori_payResult','code':'0','result':'success','ordersn':map['ordersn']});
            }
            return {'status':'failed'};
          },
          gestureNavigationEnabled: true,
        );
      }),
      floatingActionButton: favoriteButton(),
    );
  }

  JavascriptChannel _toasterJavascriptChannel(BuildContext context) {
    return JavascriptChannel(
        name: 'Toaster',
        onMessageReceived: (JavascriptMessage message) {
          // ignore: deprecated_member_use
          Scaffold.of(context).showSnackBar(
            SnackBar(content: Text(message.message)),
          );
        });
  }

  Widget favoriteButton() {
    return FutureBuilder<WebViewController>(
        future: _controller.future,
        builder: (BuildContext context,
            AsyncSnapshot<WebViewController> controller) {
          if (controller.hasData) {
            return FloatingActionButton(
              onPressed: () async {
                final String url = await controller.data.currentUrl();
                // ignore: deprecated_member_use
                Scaffold.of(context).showSnackBar(
                  SnackBar(content: Text('Favorited $url')),
                );
              },
              child: const Icon(Icons.favorite),
            );
          }
          return Container();
        });
  }
}

enum MenuOptions {
  showUserAgent,
  listCookies,
  clearCookies,
  addToCache,
  listCache,
  clearCache,
  navigationDelegate,
}

class SampleMenu extends StatelessWidget {
  SampleMenu(this.controller);

  final Future<WebViewController> controller;
  final CookieManager cookieManager = CookieManager();

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<WebViewController>(
      future: controller,
      builder:
          (BuildContext context, AsyncSnapshot<WebViewController> controller) {
        return PopupMenuButton<MenuOptions>(
          onSelected: (MenuOptions value) {
            switch (value) {
              case MenuOptions.showUserAgent:
                _onShowUserAgent(controller.data, context);
                break;
              case MenuOptions.listCookies:
                _onListCookies(controller.data, context);
                break;
              case MenuOptions.clearCookies:
                _onClearCookies(context);
                break;
              case MenuOptions.addToCache:
                _onAddToCache(controller.data, context);
                break;
              case MenuOptions.listCache:
                _onListCache(controller.data, context);
                break;
              case MenuOptions.clearCache:
                _onClearCache(controller.data, context);
                break;
              case MenuOptions.navigationDelegate:
                _onNavigationDelegateExample(controller.data, context);
                break;
            }
          },
          itemBuilder: (BuildContext context) => <PopupMenuItem<MenuOptions>>[
            PopupMenuItem<MenuOptions>(
              value: MenuOptions.showUserAgent,
              child: const Text('Show user agent'),
              enabled: controller.hasData,
            ),
            const PopupMenuItem<MenuOptions>(
              value: MenuOptions.listCookies,
              child: Text('List cookies'),
            ),
            const PopupMenuItem<MenuOptions>(
              value: MenuOptions.clearCookies,
              child: Text('Clear cookies'),
            ),
            const PopupMenuItem<MenuOptions>(
              value: MenuOptions.addToCache,
              child: Text('Add to cache'),
            ),
            const PopupMenuItem<MenuOptions>(
              value: MenuOptions.listCache,
              child: Text('List cache'),
            ),
            const PopupMenuItem<MenuOptions>(
              value: MenuOptions.clearCache,
              child: Text('Clear cache'),
            ),
            const PopupMenuItem<MenuOptions>(
              value: MenuOptions.navigationDelegate,
              child: Text('Navigation Delegate example'),
            ),
          ],
        );
      },
    );
  }

  void _onShowUserAgent(
      WebViewController controller, BuildContext context) async {
    // Send a message with the user agent string to the Toaster JavaScript channel we registered
    // with the WebView.
    await controller.evaluateJavascript(
        'Toaster.postMessage("User Agent: " + navigator.userAgent);');
  }

  void _onListCookies(
      WebViewController controller, BuildContext context) async {
    final String cookies =
        await controller.evaluateJavascript('document.cookie');
    // ignore: deprecated_member_use
    Scaffold.of(context).showSnackBar(SnackBar(
      content: Column(
        mainAxisAlignment: MainAxisAlignment.end,
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          const Text('Cookies:'),
          _getCookieList(cookies),
        ],
      ),
    ));
  }

  void _onAddToCache(WebViewController controller, BuildContext context) async {
    await controller.evaluateJavascript(
        'caches.open("test_caches_entry"); localStorage["test_localStorage"] = "dummy_entry";');
    // ignore: deprecated_member_use
    Scaffold.of(context).showSnackBar(const SnackBar(
      content: Text('Added a test entry to cache.'),
    ));
  }

  void _onListCache(WebViewController controller, BuildContext context) async {
    await controller.evaluateJavascript('caches.keys()'
        '.then((cacheKeys) => JSON.stringify({"cacheKeys" : cacheKeys, "localStorage" : localStorage}))'
        '.then((caches) => Toaster.postMessage(caches))');
  }

  void _onClearCache(WebViewController controller, BuildContext context) async {
    await controller.clearCache();
    // ignore: deprecated_member_use
    Scaffold.of(context).showSnackBar(const SnackBar(
      content: Text("Cache cleared."),
    ));
  }

  void _onClearCookies(BuildContext context) async {
    final bool hadCookies = await cookieManager.clearCookies();
    String message = 'There were cookies. Now, they are gone!';
    if (!hadCookies) {
      message = 'There are no cookies.';
    }
    // ignore: deprecated_member_use
    Scaffold.of(context).showSnackBar(SnackBar(
      content: Text(message),
    ));
  }

  void _onNavigationDelegateExample(
      WebViewController controller, BuildContext context) async {
    final String contentBase64 =
        base64Encode(const Utf8Encoder().convert(kNavigationExamplePage));
    await controller.loadUrl('data:text/html;base64,$contentBase64');
  }

  Widget _getCookieList(String cookies) {
    if (cookies == null || cookies == '""') {
      return Container();
    }
    final List<String> cookieList = cookies.split(';');
    final Iterable<Text> cookieWidgets =
        cookieList.map((String cookie) => Text(cookie));
    return Column(
      mainAxisAlignment: MainAxisAlignment.end,
      mainAxisSize: MainAxisSize.min,
      children: cookieWidgets.toList(),
    );
  }
}

class NavigationControls extends StatelessWidget {
  const NavigationControls(this._webViewControllerFuture)
      : assert(_webViewControllerFuture != null);

  final Future<WebViewController> _webViewControllerFuture;

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<WebViewController>(
      future: _webViewControllerFuture,
      builder:
          (BuildContext context, AsyncSnapshot<WebViewController> snapshot) {
        final bool webViewReady =
            snapshot.connectionState == ConnectionState.done;
        final WebViewController controller = snapshot.data;
        return Row(
          children: <Widget>[
            IconButton(
              icon: const Icon(Icons.arrow_back_ios),
              onPressed: !webViewReady
                  ? null
                  : () async {
                      if (await controller.canGoBack()) {
                        await controller.goBack();
                      } else {
                        // ignore: deprecated_member_use
                        Scaffold.of(context).showSnackBar(
                          const SnackBar(content: Text("No back history item")),
                        );
                        return;
                      }
                    },
            ),
            IconButton(
              icon: const Icon(Icons.arrow_forward_ios),
              onPressed: !webViewReady
                  ? null
                  : () async {
                      if (await controller.canGoForward()) {
                        await controller.goForward();
                      } else {
                        // ignore: deprecated_member_use
                        Scaffold.of(context).showSnackBar(
                          const SnackBar(
                              content: Text("No forward history item")),
                        );
                        return;
                      }
                    },
            ),
            IconButton(
              icon: const Icon(Icons.replay),
              onPressed: !webViewReady
                  ? null
                  : () async{
                      controller.reload();
//                      String a = await controller.getTitle();
//                      String res = await controller.callOrigin({'ori_Method':'ori_valueeee'});
//                      print(res);
                    },
            ),
          ],
        );
      },
    );
  }
}
