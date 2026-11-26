package com.example.syndicateloan.notification.application;

import com.example.syndicateloan.notification.Notices;
import com.example.syndicateloan.notification.domain.NewNotice;
import com.example.syndicateloan.notification.domain.NoticeKind;
import com.example.syndicateloan.notification.domain.NoticeRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link Notices} の実装。呼び出し側のトランザクションで記録する。 */
@Service
@Transactional
public class NoticesService implements Notices {

    private final NoticeRepository notices;
    private final Clock clock;

    public NoticesService(NoticeRepository notices, Clock clock) {
        this.notices = notices;
        this.clock = clock;
    }

    @Override
    public void send(long recipientId, String subjectRef, String message) {
        notices.insert(new NewNotice(recipientId, NoticeKind.OTHER, subjectRef, message, LocalDateTime.now(clock)));
    }
}
