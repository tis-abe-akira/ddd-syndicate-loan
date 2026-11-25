import { useQuery } from '@tanstack/react-query'
import { Link } from '@tanstack/react-router'
import { actingUsersQuery } from '../api/queries'
import { setActingUserId, useActingUserId } from '../lib/actingUser'
import { ActingUserPicker } from './ActingUserPicker'
import { Wordmark } from './Wordmark'

export function TopNav() {
  const users = useQuery(actingUsersQuery())
  const actingUserId = useActingUserId()
  return (
    <header className="top-nav">
      <div className="container">
        <Link to="/" className="wordmark" aria-label="シンジケートローン">
          <Wordmark />
        </Link>
        <span className="spacer" />
        {users.data && <ActingUserPicker users={users.data} value={actingUserId} onChange={setActingUserId} />}
      </div>
    </header>
  )
}
