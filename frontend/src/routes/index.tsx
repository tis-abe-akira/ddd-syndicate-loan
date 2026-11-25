import { createFileRoute, useNavigate } from '@tanstack/react-router'
import { useState, type FormEvent } from 'react'

export const Route = createFileRoute('/')({ component: Home })

/** Until the deal list exists, a deal is opened by its number. */
function Home() {
  const navigate = useNavigate()
  const [dealId, setDealId] = useState('')
  const valid = /^\d+$/.test(dealId.trim())

  function open(event: FormEvent) {
    event.preventDefault()
    if (valid) void navigate({ to: '/deals/$dealId/facility', params: { dealId: Number(dealId.trim()) } })
  }

  return (
    <>
      <section className="band">
        <div className="container stack">
          <h1 className="display-lg">ファシリティの組成</h1>
          <p className="muted" style={{ margin: 0 }}>
            参加を応諾した出資者とリード銀行の持分を合わせて、ファシリティを組成します(UC-015)。
          </p>
        </div>
      </section>
      <section className="band">
        <div className="container">
          <form className="feature-card stack" onSubmit={open}>
            <div className="field">
              <label className="field-label" htmlFor="deal-id">
                融資案件の番号
              </label>
              <input
                id="deal-id"
                className="text-input numeric"
                inputMode="numeric"
                value={dealId}
                onChange={(event) => setDealId(event.target.value)}
              />
            </div>
            <div className="row">
              <button type="submit" className="button button-primary" disabled={!valid}>
                組成を始める
              </button>
            </div>
          </form>
        </div>
      </section>
    </>
  )
}
