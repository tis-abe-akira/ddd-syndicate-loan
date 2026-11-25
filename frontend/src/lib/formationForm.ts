import type { FieldError, FormationView, FormFacilityRequest } from '../api/client'

/**
 * What the lead bank's user types in UC-015 step 3. Amounts stay text until they are sent,
 * so a half-typed number is never rounded or lost.
 */
export type FormationForm = {
  investorShares: Record<number, string>
  leadCommitmentAmount: string
  startDate: string
  endDate: string
}

/** Field name -> message, keyed as the backend names fields (investorShares[1002], startDate, ...). */
export type FieldErrors = Record<string, string>

/** Step 3 starts from the current facility (A3) or, without one, from the accepted commitments. */
export function initialForm(view: FormationView): FormationForm {
  return {
    investorShares: Object.fromEntries(view.investors.map((i) => [i.organizationId, String(i.shareAmount)])),
    leadCommitmentAmount: String(view.leadCommitmentAmount),
    startDate: view.facility?.startDate ?? '',
    endDate: view.facility?.endDate ?? '',
  }
}

const NUMBER = /^-?\d+(\.\d+)?$/

/** A typed amount: null when empty, a number when it reads as one, undefined when it does not. */
function parseAmount(text: string): number | null | undefined {
  const trimmed = text.replaceAll(',', '').trim()
  if (trimmed === '') return null
  return NUMBER.test(trimmed) ? Number(trimmed) : undefined
}

export const investorField = (organizationId: number) => `investorShares[${organizationId}]`

/**
 * Builds the request of step 4. Text that is not a number is caught here, before it is sent;
 * every business rule (A5) stays with the backend, which reports the fields to correct.
 */
export function toRequest(
  view: FormationView,
  form: FormationForm,
): { request: FormFacilityRequest } | { errors: FieldErrors } {
  const errors: FieldErrors = {}
  const investorShares = view.investors.map((investor) => {
    const amount = parseAmount(form.investorShares[investor.organizationId] ?? '')
    if (amount === undefined) errors[investorField(investor.organizationId)] = '数値で入力してください。'
    return { organizationId: investor.organizationId, amount: amount ?? null }
  })
  const lead = parseAmount(form.leadCommitmentAmount)
  if (lead === undefined) errors.leadCommitmentAmount = '数値で入力してください。'
  if (Object.keys(errors).length > 0) return { errors }
  return {
    request: {
      participationFingerprint: view.participationFingerprint,
      facilityVersion: view.facility?.version ?? null,
      investorShares,
      leadCommitmentAmount: lead ?? null,
      startDate: form.startDate === '' ? null : form.startDate,
      endDate: form.endDate === '' ? null : form.endDate,
    },
  }
}

export function fieldErrors(errors: FieldError[]): FieldErrors {
  return Object.fromEntries(errors.map((e) => [e.field, e.message]))
}

/** The commitment the typed shares would make (BR-002); null while some amount does not read. */
export function commitmentTotal(form: FormationForm): number | null {
  const amounts = [...Object.values(form.investorShares), form.leadCommitmentAmount].map(parseAmount)
  if (amounts.some((a) => a === undefined)) return null
  return amounts.reduce<number>((sum, a) => sum + (a ?? 0), 0)
}
