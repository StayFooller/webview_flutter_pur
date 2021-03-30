// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.


#import <WebKit/WebKit.h>
#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN
@class FlutterMethodChannel;
@interface FLTWKNavigationDelegate : NSObject <WKNavigationDelegate>

- (instancetype)initWithChannel:(FlutterMethodChannel*)channel;

/**
 * Whether to delegate navigation decisions over the method channel.
 */
@property(nonatomic, assign) BOOL hasDartNavigationDelegate;

@end

NS_ASSUME_NONNULL_END
