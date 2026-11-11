# 技術スタックと設計方針

* [技術スタック](tech-stack.md) - 採用する言語・FW・DB・テスト・CI と、run-buddy・ddd-ai との差分
* [設計方針](design-principles.md) - ドメインモデル、ポート&アダプタ、永続化、テストの方針と、本システムへの当てはめ

# アーキテクチャの決定(ADR)

`decisions/` は aiup の `/architecture-decision` が管理する(aiup 形式)。

* [ADR-001: 複数回のドローダウンを許し、Loan に Share Pie を持たせる](decisions/ADR-001-multiple-drawdowns-and-loan-share-pie.md) - ddd-ai の「2度目のドローダウン禁止」と「引き出し時の配分で返済を按分」をやめ、Evans DDD 第8章どおり Facility と Loan が独立した Share Pie を持つモデルにする

# 関連

* [プロジェクト概要資料(brief)](../brief/index.md) - aiup スキルの入力にする業務概要・要件の元資料
