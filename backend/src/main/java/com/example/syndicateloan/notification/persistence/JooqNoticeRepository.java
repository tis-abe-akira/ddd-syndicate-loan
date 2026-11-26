package com.example.syndicateloan.notification.persistence;

import static com.example.syndicateloan.notification.persistence.jooq.generated.Tables.NOTICE;

import com.example.syndicateloan.notification.domain.NewNotice;
import com.example.syndicateloan.notification.domain.NoticeRepository;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** {@link NoticeRepository} の jOOQ のアダプタ。 */
@Repository
@Transactional
public class JooqNoticeRepository implements NoticeRepository {

    private final DSLContext ctx;

    public JooqNoticeRepository(DSLContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void insert(NewNotice notice) {
        ctx.insertInto(NOTICE)
                .set(NOTICE.RECIPIENT_ID, notice.recipientId())
                .set(NOTICE.KIND, notice.kind().name())
                .set(NOTICE.SUBJECT_REF, notice.subjectRef())
                .set(NOTICE.MESSAGE, notice.message())
                .set(NOTICE.CREATED_AT, notice.createdAt())
                .execute();
    }
}
