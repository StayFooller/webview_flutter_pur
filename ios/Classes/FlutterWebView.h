// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#import <Flutter/Flutter.h>
#import <WebKit/WebKit.h>
#import <dsbridge/dsbridge.h>

NS_ASSUME_NONNULL_BEGIN
@class FLTWKNavigationDelegate;
@interface FLTWebViewController : NSObject <FlutterPlatformView, WKUIDelegate>
{
    FLTWKNavigationDelegate *jsApi;
}
- (instancetype)initWithFrame:(CGRect)frame
               viewIdentifier:(int64_t)viewId
                    arguments:(id _Nullable)args
              binaryMessenger:(NSObject<FlutterBinaryMessenger>*)messenger;

- (UIView*)view;
@property (nonatomic, strong) UIProgressView *progressView;
@end

@interface FLTWebViewFactory : NSObject <FlutterPlatformViewFactory>
- (instancetype)initWithMessenger:(NSObject<FlutterBinaryMessenger>*)messenger;
@end

/**
 * The WkWebView used for the plugin.
 *
 * This class overrides some methods in `WKWebView` to serve the needs for the plugin.
 */
@interface FLTWKWebView : DWKWebView
@end

NS_ASSUME_NONNULL_END
