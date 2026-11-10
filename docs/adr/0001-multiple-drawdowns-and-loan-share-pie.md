---
type: adr
title: "ADR-0001: 複数回のドローダウンを許し、Loan に Share Pie を持たせる"
description: ddd-ai の「2度目のドローダウン禁止」と「引き出し時の配分で返済を按分」をやめ、Evans DDD 第8章どおり Facility と Loan が独立した Share Pie を持つモデルにする。
tags: [adr, share-pie, drawdown, loan, domain-model]
status: stable
sources:
  - id: evans-ch8
    title: "Eric Evans, Domain-Driven Design, Chapter 8: Breakthrough"
    resource: https://imapai.github.io/Domain-Driven-Design-zh/ch8.html
  - id: ddd-ai
    title: tis-abe-akira/ddd-ai(元ネタの実装と仕様)
    resource: https://github.com/tis-abe-akira/ddd-ai
---

# ADR-0001: 複数回のドローダウンを許し、Loan に Share Pie を持たせる

- ステータス: Accepted
- 日付: 2026-11-10

## コンテキスト

要件の元ネタ ddd-ai[^ddd-ai] は、次のように実装していた。

- 最初のドローダウンで Facility を FIXED にし、2度目のドローダウンを禁止する。
- ドローダウンごとに Loan を作り、返済はそのドローダウン時の配分(Amount Pie)で按分する。

一方、Evans『Domain-Driven Design』第8章[^evans-ch8]は、次のことを示している。

- 同じ Facility から複数回引き出し、Loan が増えていく。
- 引き出しのたびに、貸し手は Facility の持分どおりに拠出するとは限らない(辞退・肩代わり)。そのため **Loan の持分は Facility の持分から独立して変わる**。
- 元本・利息は Loan の持分で、手数料は Facility の持分で配分する。
- 両者を結びつけたモデルでは、補正(Loan Adjustment)が増え、端数の不整合が消えなかった。持分を Share Pie に一般化して解消した。

## 決定

1. **同一 Facility に対し、利用可能額の範囲で複数回のドローダウンを許す。** Facility の FIXED は「持分とコミットメント額の変更禁止」を意味し、引き出しは止めない。
2. **Share Pie を、投資家ごとの持分を金額で持つ値オブジェクトとする。** Facility と Loan がそれぞれ持つ。
   - Loan は Facility ごとに1つ。引き出しの配分を Loan の Share Pie に足し、元本の返済の配分を引く。
   - 元本・利息は Loan の Share Pie で、投資家に配る手数料は Facility の Share Pie で按分する。
   - 端数は最大剰余方式で配り、配分の合計を常に支払額と一致させる。

### Share Pie の形(イメージ)

```java
// 不変の値オブジェクト。持分を金額で持ち、比率は導出する。
public record SharePie(Map<InvestorId, Money> shares) {
    Money total();
    Percentage ratioOf(InvestorId investor);
    SharePie prorate(Money amount);  // 自分の比率で按分(最大剰余方式)。結果の合計 == amount
    SharePie plus(SharePie other);   // 引き出しで Loan の持分を増やす
    SharePie minus(SharePie other);  // 元本返済で減らす。負になれば業務エラー
}

// 使い方
SharePie drawdownPie = explicit.orElseGet(() -> facility.sharePie().prorate(amount));
loan = loan.drawdown(drawdownPie);                         // loanPie.plus(drawdownPie)
SharePie principalDist = loan.sharePie().prorate(principal);
loan = loan.repay(principalDist);                          // loanPie.minus(principalDist)
SharePie interestDist = loan.sharePie().prorate(interest); // Loan は変わらない
SharePie feeDist = facility.sharePie().prorate(fee);       // Loan は変わらない
```

数値例とテストケースは [Share Pie の数値例とテストケース](../requirements/05-share-pie-examples.md) に置く。実装では、その TC-xx をドメイン層の単体テストにする。

## 検討した選択肢

| 選択肢 | 判断 |
|---|---|
| ddd-ai のまま(2度目のドローダウン禁止、引き出し時の配分で返済) | 書籍の業務と合わない。複数回の引き出しを表せない |
| Loan の持分を比率で持ち、引き出しのたびに比率を再計算 | 再計算のたびに端数が出て、元本の追跡がずれる。書籍が避けた問題 |
| **Loan の持分を金額の Share Pie で持つ** | 採用。引き出し・返済が足し算・引き算になり、合計が残高と常に一致する |

## 結果(トレードオフ)

- 良い点: 辞退・肩代わりを含む引き出しを、補正なしで表せる。配分の合計が常に一致し、テストで検証しやすい。
- 良い点: 投資家の現在投資額は、Loan の Share Pie から導出でき、別の集計値を持たなくてよい。
- 注意点: 返済スケジュールを引き出しごとに持つか Loan 全体で持つか、利用可能額を回転型で計算するか、などが新たに決める事項になる(要件文書の「要確認」)。
- 注意点: ddd-ai の実装・テストは、この点で参考にできない。

[^ddd-ai]: ddd-ai
[^evans-ch8]: evans-ch8
