import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { createFileRoute, notFound } from '@tanstack/react-router'
import { useState } from 'react'
import { ApiError, api, type FormationApprovalRequest, type FormFacilityRequest } from '../api/client'
import { facilityFormationQuery, formationApprovalProposalQuery } from '../api/queries'
import { ErrorBand } from '../components/ErrorBand'
import { FacilityFormation } from '../components/FacilityFormation'
import { FormationApproval } from '../components/FormationApproval'
import { useActingUserId } from '../lib/actingUser'

export const Route = createFileRoute('/deals/$dealId/facility')({
  params: {
    parse: ({ dealId }) => {
      if (!/^\d+$/.test(dealId)) throw notFound()
      return { dealId: Number(dealId) }
    },
    stringify: ({ dealId }) => ({ dealId: String(dealId) }),
  },
  component: FacilityFormationPage,
})

function FacilityFormationPage() {
  const { dealId } = Route.useParams()
  const userId = useActingUserId()
  const queryClient = useQueryClient()
  const formation = useQuery({ ...facilityFormationQuery(dealId, userId), enabled: userId !== undefined })
  const form = useMutation({
    mutationFn: (request: FormFacilityRequest) => api.formFacility(dealId, request),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['facility-formation', dealId] }),
  })
  // UC-015 step 7, 8: drafting the formation request for the facility under formation.
  const [drafting, setDrafting] = useState(false)
  const proposal = useQuery({ ...formationApprovalProposalQuery(dealId, userId), enabled: drafting && userId !== undefined })
  const approval = useMutation({
    mutationFn: (request: FormationApprovalRequest) => api.submitFormationApproval(dealId, request),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['facility-formation', dealId] }),
  })
  const stopDrafting = () => {
    setDrafting(false)
    approval.reset()
  }

  if (userId === undefined) {
    return (
      <section className="band">
        <div className="container stack">
          <h1 className="display-md">利用者を選んでください</h1>
          <p className="muted" style={{ margin: 0 }}>
            画面上部で、操作する利用者(リード銀行の業務ユーザー)を選ぶと、組成を始められます。
          </p>
        </div>
      </section>
    )
  }
  if (formation.isError) return <ErrorBand error={formation.error} />
  if (!formation.data) return <section className="band"><div className="container muted">読み込んでいます…</div></section>

  const view = formation.data
  return (
    <>
      <section className="band">
        <div className="container stack">
          <h1 className="display-md">ファシリティの組成</h1>
          <p className="muted" style={{ margin: 0 }}>
            融資案件 <span className="numeric">#{view.dealId}</span> ・ リード銀行 {view.leadOrganizationName} ・ 通貨{' '}
            {view.currency}
          </p>
        </div>
      </section>
      <section className="band">
        <div className="container">
          {drafting ? (
            proposal.isError ? (
              <div className="outline-card stack">
                <p className="form-error" role="alert" style={{ margin: 0 }}>
                  {proposal.error instanceof ApiError ? proposal.error.detail : String(proposal.error)}
                </p>
                <div className="row">
                  <button type="button" className="button button-secondary" onClick={stopDrafting}>
                    戻る
                  </button>
                </div>
              </div>
            ) : proposal.data ? (
              <FormationApproval
                key={`${proposal.data.facilityVersion}-${proposal.data.routeRuleVersion}`}
                proposal={proposal.data}
                submitting={approval.isPending}
                error={approval.error}
                submitted={approval.data}
                onSubmit={(request) => approval.mutate(request)}
                onCancel={stopDrafting}
              />
            ) : (
              <p className="muted">承認経路の案を読み込んでいます…</p>
            )
          ) : (
          <FacilityFormation
            // A fresh view (after a reload or a revision) starts the form again from step 2.
            key={`${view.participationFingerprint}-${view.facility?.version ?? 'new'}-${form.isSuccess}`}
            view={view}
            submitting={form.isPending}
            error={form.error}
            formed={form.data}
            onSubmit={(request) => form.mutate(request)}
            onReload={() => {
              form.reset()
              void formation.refetch()
            }}
            onRevise={() => form.reset()}
            onDraft={() => {
              form.reset()
              approval.reset()
              setDrafting(true)
            }}
          />
          )}
        </div>
      </section>
    </>
  )
}
