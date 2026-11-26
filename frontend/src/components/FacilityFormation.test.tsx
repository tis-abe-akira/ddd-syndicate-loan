import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ApiError } from '../api/client'
import { formationView, formedFacility } from '../test/formation'
import { FacilityFormation } from './FacilityFormation'

function renderForm(props: Partial<Parameters<typeof FacilityFormation>[0]> = {}) {
  const handlers = { onSubmit: vi.fn(), onReload: vi.fn(), onRevise: vi.fn(), onDraft: vi.fn() }
  render(
    <FacilityFormation view={formationView()} submitting={false} error={null} formed={undefined} {...handlers} {...props} />,
  )
  return handlers
}

describe('FacilityFormation', () => {
  it('shows the accepted investors, their commitments and the total (step 2)', () => {
    renderForm()
    expect(screen.getByLabelText('みなと信託銀行')).toHaveValue('1200000000')
    expect(screen.getByLabelText('あおば生命保険')).toHaveValue('800000000')
    expect(screen.getByText('1,200,000,000 円')).toBeInTheDocument()
    expect(screen.getByText('2,000,000,000 円', { selector: 'td' })).toBeInTheDocument()
  })

  it('shows the commitment the typed shares make (BR-002)', async () => {
    renderForm()
    const share = screen.getByLabelText('あおば生命保険')
    await userEvent.clear(share)
    await userEvent.type(share, '300000000')
    await userEvent.type(screen.getByLabelText('リード銀行のコミットメント額(円)'), '{selectall}100000000')
    expect(screen.getByTestId('commitment-total')).toHaveTextContent('1,600,000,000 円')
  })

  it('sends the decided shares and period when the formation is confirmed (step 4)', async () => {
    const { onSubmit } = renderForm()
    await userEvent.type(screen.getByLabelText('引き出せる期間の初日'), '2027-04-01')
    await userEvent.type(screen.getByLabelText('引き出せる期間の末日'), '2028-03-31')
    await userEvent.click(screen.getByRole('button', { name: '組成を確定する' }))
    expect(onSubmit).toHaveBeenCalledWith({
      participationFingerprint: 'fp-1',
      facilityVersion: null,
      investorShares: [
        { organizationId: 1002, amount: 1_200_000_000 },
        { organizationId: 1003, amount: 800_000_000 },
      ],
      leadCommitmentAmount: 0,
      startDate: '2027-04-01',
      endDate: '2028-03-31',
    })
  })

  it('does not send a share that is not a number', async () => {
    const { onSubmit } = renderForm()
    await userEvent.type(screen.getByLabelText('みなと信託銀行'), '{selectall}12億')
    await userEvent.click(screen.getByRole('button', { name: '組成を確定する' }))
    expect(onSubmit).not.toHaveBeenCalled()
    expect(screen.getByText('数値で入力してください。')).toBeInTheDocument()
  })

  it('shows each field the backend asks to correct next to it (A5)', () => {
    renderForm({
      error: new ApiError(400, '入力に直すべき項目があります。', '/problems/invalid-input', [
        { field: 'investorShares[1002]', message: '持分は応諾したコミットメント額以下にしてください。' },
        { field: 'startDate', message: '引き出せる期間の初日は今日以降にしてください。' },
      ]),
    })
    expect(screen.getByLabelText('みなと信託銀行')).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByText('持分は応諾したコミットメント額以下にしてください。')).toBeInTheDocument()
    expect(screen.getByLabelText('引き出せる期間の初日')).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByText('引き出せる期間の初日は今日以降にしてください。')).toBeInTheDocument()
  })

  it('offers to start again from step 2 when another operation changed the answers (A6)', async () => {
    const { onReload } = renderForm({
      error: new ApiError(409, '参加の回答が変わりました。組成をやり直してください。', '/problems/concurrent-change'),
    })
    expect(screen.getByRole('alert')).toHaveTextContent('参加の回答が変わりました。')
    await userEvent.click(screen.getByRole('button', { name: '最新の内容を読み直す' }))
    expect(onReload).toHaveBeenCalled()
  })

  it('shows why a deal without accepted investors cannot be formed, with no form (A1)', () => {
    renderForm({ view: formationView({ investors: [], blockedReason: 'NO_ACCEPTED_INVESTOR' }) })
    expect(screen.getByRole('status')).toHaveTextContent('参加を応諾した出資者がいないため、組成できません。')
    expect(screen.queryByRole('button', { name: '組成を確定する' })).not.toBeInTheDocument()
  })

  it('shows a fixed facility as it stands (A2)', () => {
    renderForm({
      view: formationView({
        blockedReason: 'FACILITY_LOCKED',
        facility: { id: 1, status: 'FIXED', underApproval: false, commitmentAmount: 2_000_000_000, startDate: '2027-04-01', endDate: '2028-03-31', version: 5 },
      }),
    })
    expect(screen.getByRole('status')).toHaveTextContent('ファシリティは確定しています。')
    expect(screen.getByRole('status')).toHaveTextContent('2,000,000,000 円')
  })

  it('shows the registered share pie and lets the user revise it (step 6)', async () => {
    const { onRevise } = renderForm({ formed: formedFacility() })
    expect(screen.getByText('組成中として登録しました')).toBeInTheDocument()
    expect(screen.getByRole('row', { name: /みなと信託銀行/ })).toHaveTextContent('1,500,000,000 円75.00%')
    await userEvent.click(screen.getByRole('button', { name: '組成を直す' }))
    expect(onRevise).toHaveBeenCalled()
  })

  it('offers to draft the formation request for a facility under formation (step 7)', async () => {
    const { onDraft } = renderForm({
      view: formationView({
        facility: { id: 1301, status: 'DRAFT', underApproval: false, commitmentAmount: 2_000_000_000, startDate: '2027-04-01', endDate: '2028-03-31', version: 0 },
      }),
    })
    await userEvent.click(screen.getByRole('button', { name: '組成の稟議を起案する' }))
    expect(onDraft).toHaveBeenCalled()
  })

  it('offers to draft the formation request right after the facility is registered (step 7)', async () => {
    const { onDraft } = renderForm({ formed: formedFacility() })
    await userEvent.click(screen.getByRole('button', { name: '組成の稟議を起案する' }))
    expect(onDraft).toHaveBeenCalled()
  })
})
