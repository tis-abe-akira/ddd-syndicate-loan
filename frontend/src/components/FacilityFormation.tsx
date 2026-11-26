import { useState, type FormEvent } from 'react'
import { ApiError, type FacilityResponse, type FormationView, type FormFacilityRequest } from '../api/client'
import {
  commitmentTotal,
  fieldErrors,
  initialForm,
  investorField,
  toRequest,
  type FieldErrors,
  type FormationForm,
} from '../lib/formationForm'
import { formatYen } from '../lib/format'
import { BlockedNotice } from './BlockedNotice'
import { FormedFacility } from './FormedFacility'

/**
 * UC-015 steps 2 to 6: the accepted investors, the shares the lead bank decides, and the
 * drawdown period. Every business rule is the backend's; this form only shows its answer.
 */
export function FacilityFormation({
  view,
  submitting,
  error,
  formed,
  onSubmit,
  onReload,
  onRevise,
  onDraft,
}: {
  view: FormationView
  submitting: boolean
  error: unknown
  formed: FacilityResponse | undefined
  onSubmit: (request: FormFacilityRequest) => void
  onReload: () => void
  onRevise: () => void
  onDraft: () => void
}) {
  const [form, setForm] = useState<FormationForm>(() => initialForm(view))
  const [localErrors, setLocalErrors] = useState<FieldErrors>({})

  if (formed) return <FormedFacility facility={formed} onRevise={onRevise} onDraft={onDraft} />
  if (view.blockedReason) return <BlockedNotice reason={view.blockedReason} facility={view.facility} />

  const apiError = error instanceof ApiError ? error : undefined
  const errors: FieldErrors = { ...(apiError?.type === '/problems/invalid-input' ? fieldErrors(apiError.errors) : {}), ...localErrors }
  // A6, A9 and the other rejections after step 4: nothing was registered; start again from step 2.
  const conflict = apiError?.status === 409
  const total = commitmentTotal(form)

  function submit(event: FormEvent) {
    event.preventDefault()
    const result = toRequest(view, form)
    if ('errors' in result) {
      setLocalErrors(result.errors)
      return
    }
    setLocalErrors({})
    onSubmit(result.request)
  }

  const setShare = (organizationId: number, value: string) =>
    setForm((f) => ({ ...f, investorShares: { ...f.investorShares, [organizationId]: value } }))

  return (
    <form className="feature-card stack" onSubmit={submit} noValidate>
      {view.facility && (
        <div className="row">
          <p className="muted" style={{ margin: 0 }}>
            組成中のファシリティがあります。今の持分と引き出せる期間から直すか、このまま組成の稟議にかけられます。
          </p>
          <span className="spacer" />
          <button type="button" className="button button-secondary" onClick={onDraft}>
            組成の稟議を起案する
          </button>
        </div>
      )}
      {conflict && (
        <div className="form-error stack" role="alert">
          <span>{apiError.detail}</span>
          <div className="row">
            <button type="button" className="button button-secondary" onClick={onReload}>
              最新の内容を読み直す
            </button>
          </div>
        </div>
      )}
      {apiError && !conflict && apiError.type !== '/problems/invalid-input' && (
        <p className="form-error" role="alert" style={{ margin: 0 }}>
          {apiError.detail}
        </p>
      )}
      {error !== null && error !== undefined && !apiError && (
        <p className="form-error" role="alert" style={{ margin: 0 }}>
          {error instanceof Error ? error.message : String(error)}
        </p>
      )}

      <h2 className="title-lg" style={{ margin: 0 }}>
        出資者の持分
      </h2>
      <p className="muted" style={{ margin: 0 }}>
        応諾したコミットメント額を目安に、その額以下で決めます。0 にした出資者はシェアパイに入りません。
      </p>
      {errors.investorShares && <p className="field-error">{errors.investorShares}</p>}
      <div className="table-scroll">
        <table className="table">
          <thead>
            <tr>
              <th>出資者</th>
              <th className="num">応諾したコミットメント額</th>
              <th>持分(円)</th>
            </tr>
          </thead>
          <tbody>
            {view.investors.map((investor) => {
              const field = investorField(investor.organizationId)
              const id = `share-${investor.organizationId}`
              return (
                <tr key={investor.organizationId}>
                  <td>
                    <label htmlFor={id}>{investor.organizationName}</label>
                  </td>
                  <td className="num">{formatYen(investor.commitmentAmount)}</td>
                  <td>
                    <input
                      id={id}
                      className="text-input numeric"
                      inputMode="numeric"
                      value={form.investorShares[investor.organizationId] ?? ''}
                      aria-invalid={errors[field] ? true : undefined}
                      aria-describedby={errors[field] ? `${id}-error` : undefined}
                      onChange={(event) => setShare(investor.organizationId, event.target.value)}
                    />
                    {errors[field] && (
                      <span id={`${id}-error`} className="field-error">
                        {errors[field]}
                      </span>
                    )}
                  </td>
                </tr>
              )
            })}
          </tbody>
          <tfoot>
            <tr>
              <td>応諾額の合計</td>
              <td className="num">{formatYen(view.totalAcceptedAmount)}</td>
              <td />
            </tr>
          </tfoot>
        </table>
      </div>

      <div className="field-row">
        <Field label="リード銀行のコミットメント額(円)" error={errors.leadCommitmentAmount} id="lead">
          <input
            id="lead"
            className="text-input numeric"
            inputMode="numeric"
            value={form.leadCommitmentAmount}
            aria-invalid={errors.leadCommitmentAmount ? true : undefined}
            onChange={(event) => setForm((f) => ({ ...f, leadCommitmentAmount: event.target.value }))}
          />
        </Field>
        <Field label="引き出せる期間の初日" error={errors.startDate} id="start">
          <input
            id="start"
            type="date"
            className="text-input"
            value={form.startDate}
            aria-invalid={errors.startDate ? true : undefined}
            onChange={(event) => setForm((f) => ({ ...f, startDate: event.target.value }))}
          />
        </Field>
        <Field label="引き出せる期間の末日" error={errors.endDate} id="end">
          <input
            id="end"
            type="date"
            className="text-input"
            value={form.endDate}
            aria-invalid={errors.endDate ? true : undefined}
            onChange={(event) => setForm((f) => ({ ...f, endDate: event.target.value }))}
          />
        </Field>
      </div>

      <div className="row">
        <span className="muted">コミットメント額(持分の合計)</span>
        <strong className="title-lg numeric" data-testid="commitment-total">
          {total === null ? '—' : formatYen(total)}
        </strong>
        <span className="spacer" />
        <button type="submit" className="button button-primary" disabled={submitting}>
          {submitting ? '確定しています…' : '組成を確定する'}
        </button>
      </div>
    </form>
  )
}

function Field({ label, error, id, children }: { label: string; error?: string; id: string; children: React.ReactNode }) {
  return (
    <div className="field">
      <label className="field-label" htmlFor={id}>
        {label}
      </label>
      {children}
      {error && <span className="field-error">{error}</span>}
    </div>
  )
}
