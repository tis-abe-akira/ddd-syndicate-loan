import type { BlockedReason, FacilitySummary } from '../api/client'
import { formatDate, formatYen } from '../lib/format'

/** UC-015 A1, A2 and the deals that cannot be formed: why, and the facility as it stands. */
export function blockedMessage(reason: BlockedReason, facility: FacilitySummary | null): string {
  switch (reason) {
    case 'NO_ACCEPTED_INVESTOR':
      return '参加を応諾した出資者がいないため、組成できません。'
    case 'FACILITY_LOCKED':
      return facility?.status === 'FIXED'
        ? 'ファシリティは確定しています。持分、コミットメント額、引き出せる期間は変えられません。'
        : 'ファシリティは組成の稟議にかかっています。稟議の間は、持分と引き出せる期間を変えられません。'
    case 'DEAL_NOT_PREPARING':
      return '融資案件が組成準備中でないため、組成できません。'
    case 'DEAL_CLOSED':
      return '融資案件が取下げか謝絶になったため、組成できません。'
  }
}

export function BlockedNotice({ reason, facility }: { reason: BlockedReason; facility: FacilitySummary | null }) {
  return (
    <div className="outline-card stack" role="status">
      <p className="title-md" style={{ margin: 0 }}>
        {blockedMessage(reason, facility)}
      </p>
      {facility && (
        <p className="muted" style={{ margin: 0 }}>
          今のファシリティ: {facility.status === 'FIXED' ? '確定済み' : '組成中'} ・ コミットメント額{' '}
          <span className="numeric">{formatYen(facility.commitmentAmount)}</span> ・ 引き出せる期間{' '}
          {formatDate(facility.startDate)} 〜 {formatDate(facility.endDate)}
        </p>
      )}
    </div>
  )
}
