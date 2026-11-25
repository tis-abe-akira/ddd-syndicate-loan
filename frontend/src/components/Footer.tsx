import { Wordmark } from './Wordmark'

export function Footer() {
  return (
    <footer className="footer">
      <div className="container stack">
        <span className="wordmark">
          <Wordmark />
        </span>
        <span>開発版です。ログインの代わりに、画面上部で操作する利用者を選びます。</span>
      </div>
    </footer>
  )
}
