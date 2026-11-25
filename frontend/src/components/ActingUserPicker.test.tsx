import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ActingUserPicker } from './ActingUserPicker'

const users = [
  { userId: 1101, name: '佐藤 花子', organizationId: 1001, organizationName: 'さくら銀行', roles: ['BUSINESS_USER'] },
  { userId: 1103, name: '高橋 健', organizationId: 1002, organizationName: 'みなと信託銀行', roles: ['BUSINESS_USER'] },
]

describe('ActingUserPicker', () => {
  it('lists the users with their company and reports the chosen one', async () => {
    const onChange = vi.fn()
    render(<ActingUserPicker users={users} value={undefined} onChange={onChange} />)
    await userEvent.selectOptions(screen.getByLabelText('操作する利用者(開発用)'), 'みなと信託銀行 / 高橋 健')
    expect(onChange).toHaveBeenCalledWith(1103)
  })
})
