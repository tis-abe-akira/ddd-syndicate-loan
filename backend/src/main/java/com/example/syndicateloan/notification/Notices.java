package com.example.syndicateloan.notification;

/** お知らせの公開 API。業務のモジュールが、業務の出来事を利用者に知らせるのに使う。 */
public interface Notices {

    /**
     * 利用者にお知らせを届ける。
     *
     * @param recipientId 受け取る利用者
     * @param subjectRef  関係する業務のものへの参照
     * @param message     本文
     */
    void send(long recipientId, String subjectRef, String message);
}
