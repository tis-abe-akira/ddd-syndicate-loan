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
