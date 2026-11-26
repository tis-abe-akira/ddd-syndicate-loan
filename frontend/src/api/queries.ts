import { queryOptions } from '@tanstack/react-query'
import { api } from './client'

export const actingUsersQuery = () =>
  queryOptions({ queryKey: ['acting-users'], queryFn: api.actingUsers, staleTime: Infinity })

/** UC-015 step 2. The acting user is part of the key: another user sees another answer (NFR-007). */
export const facilityFormationQuery = (dealId: number, userId: number | undefined) =>
  queryOptions({
    queryKey: ['facility-formation', dealId, userId],
    queryFn: () => api.facilityFormation(dealId),
    retry: false,
  })

/** UC-015 step 7 (UC-024 step 4): the proposed route of the formation request. Fetched when drafting starts. */
export const formationApprovalProposalQuery = (dealId: number, userId: number | undefined) =>
  queryOptions({
    queryKey: ['formation-approval-proposal', dealId, userId],
    queryFn: () => api.formationApprovalProposal(dealId),
    retry: false,
    staleTime: 0,
  })
