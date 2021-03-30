//
//  MethodBridge.h
//  webview_flutter
//
//  Created by Purool on 2021/3/29.
//

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN
@class FlutterMethodCall,WKWebView;
typedef void (^FlutterResult)(id _Nullable result);
typedef void (^completionHandler)(id _Nullable value);

@interface MethodBridge : NSObject

+ (void)callOrigin:(FlutterMethodCall*)call result:(FlutterResult)result andWebView:(id)webView;
@end

NS_ASSUME_NONNULL_END
