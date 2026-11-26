import type { components } from './schema.gen'
import { actingUserId } from '../lib/actingUser'

type Schemas = components['schemas']

export type FormationView = Schemas['FormationView']
export type Investor = Schemas['Investor']
export type FacilitySummary = Schemas['FacilitySummary']
export type FormFacilityRequest = Schemas['FormFacilityRequest']
export type FacilityResponse = Schemas['FacilityResponse']
export type ActiveUser = Schemas['ActiveUser']
export type BlockedReason = NonNullable<FormationView['blockedReason']>
export type FormationApprovalProposal = Schemas['FormationApprovalProposalResponse']
export type ProposedApprovalStep = Schemas['ProposedStepResponse']
export type FormationApprovalRequest = Schemas['FormationApprovalRequest']
export type FormationApprovalResponse = Schemas['FormationApprovalResponse']

/** A field the backend asks to correct (UC-015 A5). */
export type FieldError = { field: string; message: string }

/**
 * An error response from the backend, carrying its RFC 9457 problem detail.
 * type is the problem type (e.g. /problems/invalid-input), errors the fields to correct.
 */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly detail: string | undefined,
    readonly type: string | undefined,
    readonly errors: FieldError[] = [],
  ) {
    super(detail ?? `request failed with status ${status}`)
  }
}

/** The header the development backend reads the acting user from (until login, UC-006). */
export const ACTING_USER_HEADER = 'X-Acting-User'

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const userId = actingUserId()
  const response = await fetch(path, {
    ...init,
    headers: {
      Accept: 'application/json',
      ...(init?.body ? { 'Content-Type': 'application/json' } : {}),
      ...(userId === undefined ? {} : { [ACTING_USER_HEADER]: String(userId) }),
    },
  })
  if (!response.ok) {
    const problem = (await response.json().catch(() => undefined)) as
      | { detail?: string; type?: string; errors?: FieldError[] }
      | undefined
    throw new ApiError(response.status, problem?.detail, problem?.type, problem?.errors ?? [])
  }
  return (await response.json()) as T
}

export const api = {
  actingUsers: () => request<ActiveUser[]>('/api/dev/acting-users'),
  facilityFormation: (dealId: number) => request<FormationView>(`/api/deals/${dealId}/facility-formation`),
  formFacility: (dealId: number, body: FormFacilityRequest) =>
    request<FacilityResponse>(`/api/deals/${dealId}/facility`, { method: 'PUT', body: JSON.stringify(body) }),
  formationApprovalProposal: (dealId: number) =>
    request<FormationApprovalProposal>(`/api/deals/${dealId}/facility/approval-proposal`),
  submitFormationApproval: (dealId: number, body: FormationApprovalRequest) =>
    request<FormationApprovalResponse>(`/api/deals/${dealId}/facility/approval-request`, {
      method: 'POST',
      body: JSON.stringify(body),
    }),
}
