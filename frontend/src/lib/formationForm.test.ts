import { formationView } from '../test/formation'
import { commitmentTotal, fieldErrors, initialForm, toRequest } from './formationForm'

describe('initialForm', () => {
  it('starts from the accepted commitments when there is no facility yet', () => {
    expect(initialForm(formationView())).toEqual({
      investorShares: { 1002: '1200000000', 1003: '800000000' },
      leadCommitmentAmount: '0',
      startDate: '',
      endDate: '',
    })
  })

  it('starts from the facility under formation (UC-015 A3)', () => {
    const view = formationView({
      investors: [{ organizationId: 1002, organizationName: 'みなと信託銀行', commitmentAmount: 1_200_000_000, shareAmount: 900_000_000 }],
      leadCommitmentAmount: 100_000_000,
      facility: {
        id: 1301,
        status: 'DRAFT',
        underApproval: false,
        commitmentAmount: 1_000_000_000,
        startDate: '2027-04-01',
        endDate: '2028-03-31',
        version: 2,
      },
    })
    expect(initialForm(view)).toEqual({
      investorShares: { 1002: '900000000' },
      leadCommitmentAmount: '100000000',
      startDate: '2027-04-01',
      endDate: '2028-03-31',
    })
  })
})

describe('toRequest', () => {
  const view = formationView()

  it('sends every accepted investor, the fingerprint and no version for a new facility', () => {
    const result = toRequest(view, {
      investorShares: { 1002: '1,000,000,000', 1003: '0' },
      leadCommitmentAmount: '200000000',
      startDate: '2027-04-01',
      endDate: '2028-03-31',
    })
    expect(result).toEqual({
      request: {
        participationFingerprint: 'fp-1',
        facilityVersion: null,
        investorShares: [
          { organizationId: 1002, amount: 1_000_000_000 },
          { organizationId: 1003, amount: 0 },
        ],
        leadCommitmentAmount: 200_000_000,
        startDate: '2027-04-01',
        endDate: '2028-03-31',
      },
    })
  })

  it('sends empty fields as null, so the backend reports them (UC-015 A5)', () => {
    const result = toRequest(view, { investorShares: { 1002: '', 1003: '1' }, leadCommitmentAmount: '', startDate: '', endDate: '' })
    expect(result).toMatchObject({
      request: { investorShares: [{ organizationId: 1002, amount: null }, { organizationId: 1003, amount: 1 }], leadCommitmentAmount: null, startDate: null, endDate: null },
    })
  })

  it('stops text that is not a number before it is sent', () => {
    expect(toRequest(view, { investorShares: { 1002: '1億', 1003: '1' }, leadCommitmentAmount: 'abc', startDate: '', endDate: '' })).toEqual({
      errors: { 'investorShares[1002]': '数値で入力してください。', leadCommitmentAmount: '数値で入力してください。' },
    })
  })

  it('sends the facility version it was shown (NFR-003)', () => {
    const withFacility = formationView({
      facility: { id: 1, status: 'DRAFT', underApproval: false, commitmentAmount: 1, startDate: '2027-04-01', endDate: '2028-03-31', version: 3 },
    })
    const result = toRequest(withFacility, initialForm(withFacility))
    expect(result).toMatchObject({ request: { facilityVersion: 3 } })
  })
})

describe('commitmentTotal', () => {
  it('adds the shares and the lead bank, treating an empty field as zero', () => {
    expect(commitmentTotal({ investorShares: { 1: '100', 2: '' }, leadCommitmentAmount: '50', startDate: '', endDate: '' })).toBe(150)
  })

  it('has no total while an amount does not read as a number', () => {
    expect(commitmentTotal({ investorShares: { 1: 'x' }, leadCommitmentAmount: '0', startDate: '', endDate: '' })).toBeNull()
  })
})

describe('fieldErrors', () => {
  it('keys the backend errors by field', () => {
    expect(fieldErrors([{ field: 'startDate', message: 'm' }])).toEqual({ startDate: 'm' })
  })
})
