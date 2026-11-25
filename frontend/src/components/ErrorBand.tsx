import { ApiError } from '../api/client'

/** Why a page could not load, in the actor's terms. */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    switch (error.status) {
      case 401:
        return '操作する利用者が分かりません。画面上部で利用者を選んでください。'
      case 403:
        return 'この操作には、業務ユーザーのロールが必要です。'
      case 404:
        return '融資案件が見つかりません。自社がリード銀行でない融資案件は表示できません。'
    }
  }
  return error instanceof Error ? error.message : String(error)
}

export function ErrorBand({ error }: { error: unknown }) {
  const known = error instanceof ApiError && [401, 403, 404].includes(error.status)
  return (
    <section className="band">
      <div className="container stack">
        <h1 className="display-md">表示できません</h1>
        <p className="form-error" style={{ margin: 0 }}>
          {errorMessage(error)}
        </p>
        {!known && (
          <p className="muted" style={{ margin: 0 }}>
            バックエンド(http://localhost:18080)が起動しているか確認してください。
          </p>
        )}
      </div>
    </section>
  )
}
