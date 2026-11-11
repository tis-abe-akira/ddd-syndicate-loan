---
type: architecture
title: 設計方針
description: run-buddy の設計方針(不変なドメイン型、ポート&アダプタ、jOOQ、契約テスト、ArchUnit)を、シンジケートローンのドメインに当てはめた方針と未決事項。
tags: [architecture, ddd, ports-and-adapters, testing, jooq, archunit]
---

# 設計方針

run-buddy の `backend/CLAUDE.md` と ADR-0004 / ADR-0007 の方針を、本システムに当てはめたもの。

## 1. ドメインモデル

- ドメイン型は不変な `record` か `final` クラスとし、**自分で不変条件を検査する**。setter、引数なしコンストラクタ、永続化用アノテーションを持たせない。
- 値の概念は値オブジェクトにする。例: `Money`(通貨付き金額)、`Percentage`(持分比率)。丸めと合計の一致(NFR-01)はここで守る。
- 業務ルール([BR-xx](../requirements/03-business-rules.md))は、検証・計算・状態遷移をドメイン層に置き、単体でテストできるようにする。

### 想定するパッケージ(package by feature)

| パッケージ | 内容 |
|---|---|
| `party` | Company / Borrower / Investor |
| `syndicate` | Syndicate |
| `facility` | Facility / SharePie / FacilityInvestment |
| `loan` | Drawdown / Loan / Payment / PaymentDetail / AmountPie / PaymentDistribution |
| `fee` | FeePayment / FeeDistribution |
| `transaction` | 取引の共通部分と照会 |
| `shared` | Money / Percentage / 共通例外 |
| `persistence` | リポジトリのアダプタ(jOOQ) |
| `web` | REST のコントローラと例外ハンドラ |

依存の向きは `web → ドメイン ← persistence`。ドメインは `web` と `persistence` に依存しない。パッケージ間の依存規則は ArchUnit で検査する。

## 2. ポート&アダプタ

- リポジトリはドメイン側のインターフェース(ポート)とし、実装(アダプタ)は `persistence` に置く。
- **ポートに `default` メソッドを置かない**。`@Transactional` のプロキシの外で実行され、実際にバグになった(run-buddy ADR-0007)。ArchUnit のルールで検査する。
- 外部システムとの連携が必要になった場合も、ポートで抽象化し、最初はダミー実装で一通りの流れを確認する(ADR-0004)。

## 3. 永続化

- jOOQ のコード生成物(`persistence/jooq/generated`)は**手で編集しない**。migration を変更したら再生成し、CI で差分を検出する。
- DB アクセスはすべてトランザクション内で行う。アダプタに `@Transactional`(読み取りは `readOnly = true`)を付ける。
- Flyway のマージ済み migration は書き換えず、新しい migration を足す。
- 複数の更新を伴う業務操作(ファシリティ組成、ドローダウン、返済)は、アプリケーション層で単一トランザクションにする。(BR-31)
- 楽観的排他制御は `version` 列で行う。(BR-30)

## 4. テスト

- 業務ルールは、外部依存のない単体テストで、境界値を含めて先に書く。返済スケジュール(BR-10, BR-11)は、既知の数値例で合計が元本と一致することを検査する。
- リポジトリの振る舞いは契約テスト(インターフェースにテストを書き、各アダプタのテストがそれを実装する)で一度だけ定める。新しいメソッドは契約テストから書く。
- DB を使うテストは Testcontainers(PostgreSQL)で行い、他のテストのデータに依存しない。
- Web 層は `@WebMvcTest` + MockMvc。
- ArchUnit でパッケージ依存・ポートの規則を検査し、規則自体の有効性もミューテーションチェックで確認する。

## 5. 未決事項

- 集約の境界(例: Facility と FacilityInvestment を同一集約にするか、Loan と PaymentDetail をどう束ねるか)。Share Pie は値オブジェクトとし、Facility と Loan がそれぞれ持つことは [ADR-0001](../adr/0001-multiple-drawdowns-and-loan-share-pie.md) で決定済み。
- Transaction の継承階層(ddd-ai は JPA の継承)を、ドメインでは sealed interface と record で表すか。
- ドメインイベント(ddd-ai の Spring Events による連携)を導入するか、アプリケーション層の明示的な呼び出しにするか。
- 状態遷移と複数集約にまたがる更新(ファシリティ組成時の Borrower / Investor の RESTRICTED 化)の置き場所。

これらは決まった時点で ADR に記録する。
