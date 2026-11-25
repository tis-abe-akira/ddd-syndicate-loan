import type { FacilityResponse } from '../api/client'
import { formatDate, formatYen } from '../lib/format'

/** UC-015 step 6: the facility registered under formation and its share pie. */
export function FormedFacility({ facility, onRevise }: { facility: FacilityResponse; onRevise: () => void }) {
  return (
    <div className="dark-card stack">
      <span className="caption-uppercase">組成中として登録しました</span>
      <p className="display-sm numeric" style={{ margin: 0 }}>
        {formatYen(facility.commitmentAmount)}
      </p>
      <p style={{ margin: 0 }}>
        引き出せる期間 {formatDate(facility.startDate)} 〜 {formatDate(facility.endDate)}
      </p>
      <div className="table-scroll">
        <table className="table">
          <thead>
            <tr>
              <th>参加金融機関</th>
              <th className="num">持分</th>
              <th className="num">比率</th>
            </tr>
          </thead>
          <tbody>
            {facility.shares.map((share) => (
              <tr key={share.organizationId}>
                <td>{share.organizationName}</td>
                <td className="num">{formatYen(share.amount)}</td>
                <td className="num">{((share.amount / facility.commitmentAmount) * 100).toFixed(2)}%</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p style={{ margin: 0 }}>組成の稟議の起案は、承認ワークフロー(UC-024)ができてから行えます。</p>
      <div className="row">
        <button type="button" className="button button-on-dark" onClick={onRevise}>
          組成を直す
        </button>
      </div>
    </div>
  )
}
