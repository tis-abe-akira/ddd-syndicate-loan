# 技術スタックと設計方針

* [技術スタック](tech-stack.md) - 採用する言語・FW・DB・テスト・CI と、run-buddy・ddd-ai との差分
* [スタックファイル](stack.md) - aiup の `/stack` が管理する、層ごとの技術と層スキル、モジュール構成、API の方式、コマンド
* [設計方針](design-principles.md) - ドメインモデル、ポート&アダプタ、永続化、テストの方針と、本システムへの当てはめ

# アーキテクチャの決定(ADR)

`decisions/` は aiup の `/architecture-decision` が管理する(aiup 形式)。

* [ADR-001: 複数回のドローダウンを許し、Loan に Share Pie を持たせる](decisions/ADR-001-multiple-drawdowns-and-loan-share-pie.md) - ddd-ai の「2度目のドローダウン禁止」と「引き出し時の配分で返済を按分」をやめ、Evans DDD 第8章どおり Facility と Loan が独立した Share Pie を持つモデルにする
* [ADR-002: 承認ワークフローを、業務から独立した workflow モジュールにする](decisions/ADR-002-independent-workflow-module.md) - 将来ワークフロー SaaS として切り出すため、業務に依存せず、公開 API とイベントで業務とつなぐ
* [ADR-003: 利用組織・利用者・ロール・職位を、共通の organization モジュールに置く](decisions/ADR-003-shared-organization-module.md) - 組織・職位の正を1つにし、workflow と業務の両方が使う。SaaS には workflow と一緒に持ち出す

# 関連

* [プロジェクト概要資料(brief)](../brief/index.md) - aiup スキルの入力にする業務概要・要件の元資料
