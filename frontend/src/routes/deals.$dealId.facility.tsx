import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { createFileRoute, notFound } from '@tanstack/react-router'
import { api, type FormFacilityRequest } from '../api/client'
import { facilityFormationQuery } from '../api/queries'
import { ErrorBand } from '../components/ErrorBand'
import { FacilityFormation } from '../components/FacilityFormation'
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
          />
        </div>
      </section>
    </>
  )
}
