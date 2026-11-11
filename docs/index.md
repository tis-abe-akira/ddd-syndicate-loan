---
okf_version: "0.2"
---

# AI Unified Process の成果物

aiup スキルが作り、aiup の形式で管理する。仕様の正はここ。まだ無いものは、[実行順序](guides/aiup-workflow.md) に沿って作る。

* [requirements.md](requirements.md) - 要件カタログ。`/requirements` が作る FR / NFR / C
* [glossary.md](glossary.md) - 用語集。`/requirements` が作る用語と、使わない同義語
* [entity_model.md](entity_model.md) - エンティティモデル。`/entity-model` が作る ER 図と属性表
* [use_cases.puml](use_cases.puml) - ユースケース図。`/use-case-diagram` が作るアクターと UC-XXX
* `use_cases/` - ユースケース仕様。`/use-case-spec UC-XXX` が1件ずつ作る(作成済み: UC-001〜UC-002)

# 未決事項

* [GitHub Issue の `[要確認]`](https://github.com/tis-abe-akira/ddd-syndicate-loan/issues?q=is%3Aissue+is%3Aopen+%5B%E8%A6%81%E7%A2%BA%E8%AA%8D%5D) - 仕様の未決事項。aiup の `open_questions.md` は使わない

# プロジェクト概要資料(brief)

* [brief 目次](brief/index.md) - aiup スキルの入力にする業務概要・要件の元資料。中身がカタログ・ユースケース・エンティティモデルに取り込まれたら、そちらを正とする

# アーキテクチャ

* [アーキテクチャ文書 目次](architecture/index.md) - 技術スタック(run-buddy を基本踏襲)、設計方針、ADR

# ガイド

* [ガイド 目次](guides/index.md) - aiup スキルの実行順序など、開発の進め方
