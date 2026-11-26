package com.example.syndicateloan.notification.domain;

/** お知らせのポート。 */
public interface NoticeRepository {

    void insert(NewNotice notice);
}
