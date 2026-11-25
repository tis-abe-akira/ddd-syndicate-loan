import type { FacilityResponse, FormationView } from '../api/client'

/** UC-015 step 2 for a deal with two accepted investors and no facility yet. */
export function formationView(overrides: Partial<FormationView> = {}): FormationView {
  return {
    dealId: 1201,
    currency: 'JPY',
    leadOrganizationId: 1001,
    leadOrganizationName: 'さくら銀行',
    blockedReason: null,
    investors: [
      { organizationId: 1002, organizationName: 'みなと信託銀行', commitmentAmount: 1_200_000_000, shareAmount: 1_200_000_000 },
      { organizationId: 1003, organizationName: 'あおば生命保険', commitmentAmount: 800_000_000, shareAmount: 800_000_000 },
    ],
    totalAcceptedAmount: 2_000_000_000,
    leadCommitmentAmount: 0,
    facility: null,
    participationFingerprint: 'fp-1',
    ...overrides,
  }
}

export function formedFacility(): FacilityResponse {
  return {
    id: 1301,
    dealId: 1201,
    status: 'DRAFT',
    commitmentAmount: 2_000_000_000,
    currency: 'JPY',
    startDate: '2027-04-01',
    endDate: '2028-03-31',
    shares: [
      { organizationId: 1002, organizationName: 'みなと信託銀行', amount: 1_500_000_000 },
      { organizationId: 1001, organizationName: 'さくら銀行', amount: 500_000_000 },
    ],
    version: 0,
  }
}
