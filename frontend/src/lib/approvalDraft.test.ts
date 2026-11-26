import { approvalProposal } from '../test/approval'
import { initialDraft, stepLabel, toApprovalRequest } from './approvalDraft'

describe('approvalDraft', () => {
  it('starts from the proposed title and route', () => {
    const form = initialDraft(approvalProposal())
    expect(form.title).toBe('東都電機工業 研究開発センターの建設 ファシリティ組成')
    expect(form.steps.map((s) => s.userId)).toEqual([1108, 1104, null, 1102])
  })

  it('sends only the kept steps with the versions it was shown (NFR-003)', () => {
    const proposal = approvalProposal()
    const form = initialDraft(proposal)
    form.steps[2]!.userId = 1107
    form.steps[3]!.removed = true
    expect(toApprovalRequest(proposal, form)).toEqual({
      facilityVersion: 2,
      routeRuleId: 1502,
      routeRuleVersion: 0,
      title: '東都電機工業 研究開発センターの建設 ファシリティ組成',
      body: null,
      steps: [
        { kind: 'CONSULTATION', order: null, userId: 1108 },
        { kind: 'MAIN', order: 1, userId: 1104 },
        { kind: 'MAIN', order: 2, userId: 1107 },
      ],
    })
  })

  it('names each step the way the drafter reads it', () => {
    expect(approvalProposal().steps.map(stepLabel)).toEqual(['合議先', '承認 1 段目(課長以上)', '承認 2 段目(部長以上)', '報告先'])
  })
})
