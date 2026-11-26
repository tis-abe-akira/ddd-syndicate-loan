/**
 * お知らせ。workflow の公開イベントを受けて、利用者の画面に届くお知らせを記録する(UC-024 step 9、UC-031)。
 * 依存してよいのは organization と workflow だけ。
 */
@ApplicationModule(displayName = "notification")
package com.example.syndicateloan.notification;

import org.springframework.modulith.ApplicationModule;
