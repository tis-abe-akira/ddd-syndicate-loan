package com.example.syndicateloan.notification.domain;

import java.time.LocalDateTime;

/**
 * 届けるお知らせ。
 *
 * @param recipientId 受け取る利用者
 * @param kind        お知らせの種類
 * @param subjectRef  関係する稟議への参照
 * @param message     本文
 * @param createdAt   届いた日時
 */
public record NewNotice(long recipientId, NoticeKind kind, String subjectRef, String message, LocalDateTime createdAt) {
}
