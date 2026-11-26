import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ApiError } from '../api/client'
import { approvalProposal } from '../test/approval'
import { FormationApproval } from './FormationApproval'

function renderDraft(props: Partial<Parameters<typeof FormationApproval>[0]> = {}) {
  const handlers = { onSubmit: vi.fn(), onCancel: vi.fn() }
  render(<FormationApproval proposal={approvalProposal()} submitting={false} error={null} submitted={undefined} {...handlers} {...props} />)
  return handlers
}

describe('FormationApproval', () => {
  it('shows the proposed route with the users the rule put in, and an empty step to fill (UC-024 A4)', () => {
    renderDraft()
    expect(screen.getByLabelText('件名')).toHaveValue('東都電機工業 研究開発センターの建設 ファシリティ組成')
    expect(screen.getByLabelText('承認 1 段目(課長以上)')).toHaveValue('1104')
    expect(screen.getByLabelText('承認 2 段目(部長以上)')).toHaveValue('')
    expect(within(screen.getByLabelText('承認 2 段目(部長以上)')).getAllByRole('option').map((o) => o.textContent)).toEqual([
      '選んでください',
      '伊藤 美咲(部長)',
      '山本 直樹(部長)',
    ])
  })

  it('submits the chosen route, without the removed report (UC-024 BR-003)', async () => {
    const { onSubmit } = renderDraft()
    await userEvent.selectOptions(screen.getByLabelText('承認 2 段目(部長以上)'), '山本 直樹(部長)')
    await userEvent.type(screen.getByLabelText('本文'), '組成の判断をお願いします。')
    const report = screen.getByRole('row', { name: /報告先/ })
    await userEvent.click(within(report).getByRole('button', { name: '外す' }))
    await userEvent.click(screen.getByRole('button', { name: '稟議を申請する' }))
    expect(onSubmit).toHaveBeenCalledWith({
      facilityVersion: 2,
      routeRuleId: 1502,
      routeRuleVersion: 0,
      title: '東都電機工業 研究開発センターの建設 ファシリティ組成',
      body: '組成の判断をお願いします。',
      steps: [
        { kind: 'CONSULTATION', order: null, userId: 1108 },
        { kind: 'MAIN', order: 1, userId: 1104 },
        { kind: 'MAIN', order: 2, userId: 1107 },
      ],
    })
  })

  it('offers no way to remove a main-track step (UC-024 BR-003)', () => {
    renderDraft()
    expect(within(screen.getByRole('row', { name: /承認 1 段目/ })).queryByRole('button', { name: '外す' })).not.toBeInTheDocument()
  })

  it('shows each field to correct next to its step (UC-024 A5)', () => {
    renderDraft({
      error: new ApiError(400, '稟議に直すべき項目があります。', '/problems/invalid-input', [
        { field: 'steps[2]', message: '利用者を選んでください。' },
      ]),
    })
    expect(screen.getByLabelText('承認 2 段目(部長以上)')).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByText('利用者を選んでください。')).toBeInTheDocument()
  })

  it('shows why the request cannot be drafted (A10, UC-024 A1 to A3, A6)', () => {
    renderDraft({ error: new ApiError(409, '融資案件が取下げか謝絶になったため、組成の稟議を起案できません。', '/problems/business-rule') })
    expect(screen.getByRole('alert')).toHaveTextContent('取下げか謝絶')
  })

  it('shows that the request was submitted and the deal is now forming (step 8)', () => {
    renderDraft({ submitted: { requestId: 7, status: 'IN_CONSULTATION', steps: [] } })
    expect(screen.getByRole('status')).toHaveTextContent('稟議 #7 は合議中です。')
    expect(screen.getByRole('status')).toHaveTextContent('融資案件は組成中になり')
  })

  it('lets the drafter stop without submitting (UC-024 A7)', async () => {
    const { onCancel, onSubmit } = renderDraft()
    await userEvent.click(screen.getByRole('button', { name: '起案をやめる' }))
    expect(onCancel).toHaveBeenCalled()
    expect(onSubmit).not.toHaveBeenCalled()
  })
})
