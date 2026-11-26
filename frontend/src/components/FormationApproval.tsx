import { useState, type FormEvent } from 'react'
import { ApiError, type FormationApprovalProposal, type FormationApprovalRequest, type FormationApprovalResponse } from '../api/client'
import { initialDraft, keptSteps, stepLabel, toApprovalRequest, type ApprovalDraftForm } from '../lib/approvalDraft'
import { fieldErrors } from '../lib/formationForm'

const STATUS: Record<string, string> = { IN_CONSULTATION: '合議中', IN_APPROVAL: '承認中' }

/**
 * UC-015 step 7, 8 with UC-024 steps 4 to 10: the drafter reviews the proposed route, picks a user for
 * every empty step, and submits. The request's content (amount, shares, period) is built by the backend.
 */
export function FormationApproval({
  proposal,
  submitting,
  error,
  submitted,
  onSubmit,
  onCancel,
}: {
  proposal: FormationApprovalProposal
  submitting: boolean
  error: unknown
  submitted: FormationApprovalResponse | undefined
  onSubmit: (request: FormationApprovalRequest) => void
  onCancel: () => void
}) {
  const [form, setForm] = useState<ApprovalDraftForm>(() => initialDraft(proposal))

  if (submitted) {
    return (
      <div className="dark-card stack" role="status">
        <span className="caption-uppercase">組成の稟議を申請しました</span>
        <p className="display-sm" style={{ margin: 0 }}>
          稟議 #{submitted.requestId} は{STATUS[submitted.status] ?? submitted.status}です。
        </p>
        <p style={{ margin: 0 }}>融資案件は組成中になり、稟議の間はファシリティを直せません。</p>
      </div>
    )
  }

  const apiError = error instanceof ApiError ? error : undefined
  const invalid = apiError?.type === '/problems/invalid-input'
  const errors = invalid ? fieldErrors(apiError.errors) : {}
  // The backend numbers the sent steps; map them back onto the rows still shown.
  const kept = keptSteps(form)
  const errorOf = (row: ApprovalDraftForm['steps'][number]) => errors[`steps[${kept.indexOf(row)}]`]

  function submit(event: FormEvent) {
    event.preventDefault()
    onSubmit(toApprovalRequest(proposal, form))
  }

  const update = (index: number, change: Partial<ApprovalDraftForm['steps'][number]>) =>
    setForm((f) => ({ ...f, steps: f.steps.map((s, i) => (i === index ? { ...s, ...change } : s)) }))

  return (
    <form className="outline-card stack" onSubmit={submit} noValidate>
      <h2 className="title-lg" style={{ margin: 0 }}>
        組成の稟議を起案する
      </h2>
      <p className="muted" style={{ margin: 0 }}>
        コミットメント額、引き出せる期間、持分は、組成中のファシリティのまま稟議にかかります。
      </p>
      {apiError && !invalid && (
        <p className="form-error" role="alert" style={{ margin: 0 }}>
          {apiError.detail}
        </p>
      )}
      {errors.steps && <p className="field-error">{errors.steps}</p>}

      <div className="field">
        <label className="field-label" htmlFor="approval-title">
          件名
        </label>
        <input
          id="approval-title"
          className="text-input"
          value={form.title}
          aria-invalid={errors.title ? true : undefined}
          onChange={(event) => setForm((f) => ({ ...f, title: event.target.value }))}
        />
        {errors.title && <span className="field-error">{errors.title}</span>}
      </div>
      <div className="field">
        <label className="field-label" htmlFor="approval-body">
          本文
        </label>
        <textarea
          id="approval-body"
          className="text-input approval-body"
          value={form.body}
          onChange={(event) => setForm((f) => ({ ...f, body: event.target.value }))}
        />
      </div>

      <div className="table-scroll">
        <table className="table">
          <thead>
            <tr>
              <th>段</th>
              <th>利用者</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {form.steps.map((row, index) =>
              row.removed ? null : (
                <tr key={index}>
                  <td>
                    <label htmlFor={`approval-step-${index}`}>{stepLabel(row.step)}</label>
                  </td>
                  <td>
                    <select
                      id={`approval-step-${index}`}
                      className="text-input"
                      value={row.userId ?? ''}
                      aria-invalid={errorOf(row) ? true : undefined}
                      onChange={(event) =>
                        update(index, { userId: event.target.value === '' ? null : Number(event.target.value) })
                      }
                    >
                      <option value="">選んでください</option>
                      {row.step.candidates.map((c) => (
                        <option key={c.userId} value={c.userId}>
                          {c.name}
                          {c.positionName ? `(${c.positionName})` : ''}
                        </option>
                      ))}
                    </select>
                    {errorOf(row) && <span className="field-error">{errorOf(row)}</span>}
                  </td>
                  <td>
                    {row.step.kind !== 'MAIN' && (
                      <button type="button" className="button button-text" onClick={() => update(index, { removed: true })}>
                        外す
                      </button>
                    )}
                  </td>
                </tr>
              ),
            )}
          </tbody>
        </table>
      </div>

      <div className="row">
        <button type="button" className="button button-secondary" onClick={onCancel}>
          起案をやめる
        </button>
        <span className="spacer" />
        <button type="submit" className="button button-primary" disabled={submitting}>
          {submitting ? '申請しています…' : '稟議を申請する'}
        </button>
      </div>
    </form>
  )
}
