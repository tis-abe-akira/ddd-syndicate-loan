const yen = new Intl.NumberFormat('ja-JP')

/** An amount in whole yen (C-013), e.g. "1,200,000,000 円". */
export function formatYen(amount: number): string {
  return `${yen.format(amount)} 円`
}

/** An ISO date (YYYY-MM-DD) as "2027年4月1日". */
export function formatDate(isoDate: string): string {
  const [year, month, day] = isoDate.split('-').map(Number)
  return `${year}年${month}月${day}日`
}
