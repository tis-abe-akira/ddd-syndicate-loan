import type { ActiveUser } from '../api/client'

/**
 * Development only: who the backend treats as the acting user (X-Acting-User).
 * Replaced by real login in UC-006.
 */
export function ActingUserPicker({
  users,
  value,
  onChange,
}: {
  users: ActiveUser[]
  value: number | undefined
  onChange: (userId: number | undefined) => void
}) {
  return (
    <label className="acting-user">
      <span className="field-label">操作する利用者(開発用)</span>
      <select
        className="text-input"
        value={value ?? ''}
        onChange={(event) => onChange(event.target.value === '' ? undefined : Number(event.target.value))}
      >
        <option value="">選んでください</option>
        {users.map((user) => (
          <option key={user.userId} value={user.userId}>
            {user.organizationName} / {user.name}
          </option>
        ))}
      </select>
    </label>
  )
}
