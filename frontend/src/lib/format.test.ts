import { formatDate, formatYen } from './format'

describe('formatYen', () => {
  it('groups whole yen by thousands', () => {
    expect(formatYen(1_200_000_000)).toBe('1,200,000,000 円')
    expect(formatYen(0)).toBe('0 円')
  })
})

describe('formatDate', () => {
  it('writes an ISO date the Japanese way', () => {
    expect(formatDate('2027-04-01')).toBe('2027年4月1日')
  })
})
