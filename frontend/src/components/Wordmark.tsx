/**
 * This system's own mark: a share pie, a ring split into the participants' shares (ADR-001).
 * (DESIGN.md's spike-mark is Anthropic's logo and is deliberately not used.)
 */
export function Wordmark() {
  return (
    <>
      <svg width="22" height="22" viewBox="0 0 22 22" aria-hidden="true">
        <circle cx="11" cy="11" r="8" fill="none" stroke="currentColor" strokeWidth="2" />
        <path d="M11 3a8 8 0 0 1 7.6 10.5L11 11z" fill="var(--color-primary)" />
      </svg>
      <span className="wordmark-text">シンジケートローン</span>
    </>
  )
}
