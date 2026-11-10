# AGENTS.md

AI エージェント(Claude Code など)向けの、このリポジトリでの作業ルールです。

## プロジェクト概要

シンジケートローン管理システム。DDD とオブジェクト指向設計、テスト技法を適用した Java 開発を、Claude Code で自動化する取り組み。

- 要件: [docs/requirements/](docs/requirements/README.md)
- 技術スタック・設計方針: [docs/arch/](docs/arch/README.md)(run-buddy と基本的に同じ)

## ドキュメントの書き方

**文書は OKF フォーマットの Markdown(`.md`)で書くことを推奨する。**

- 先頭に YAML フロントマターを置く。

  ```yaml
  ---
  type: <文書の種類を表す短い語。例: requirements / architecture / adr / guide / index>
  title: <人が読む題名>
  description: <1〜2文の要約。検索・取得の手がかりになる内容にする>
  tags: [<英語の安定したタグ>, ...]
  ---
  ```

- フォルダの目次ページ(`README.md`)には、`okf_version: "0.2"` と `type: index` を付ける。
- 本文は日本語。図は Mermaid で書く(差分が見やすく、ツールで扱いやすい)。
- 未確定・矛盾の見つかった事項は、本文に断定で書かず、文書末尾の「要確認」「未決事項」に集める。
- 設計判断は `docs/adr/NNNN-kebab-case-title.md` に ADR として残す。Accepted になった ADR の本文は書き換えず、新しい ADR で Superseded にする。
- 文書を追加・改名したら、そのフォルダの `README.md` の目次も更新する。

> 補足: OKF の仕様は、参照元 run-buddy の `openwiki/` とその生成スキルのフロントマター規約(`type` / `title` / `description` / `tags`、目次の `okf_version`)から読み取っている。仕様が確認できたら、この節を更新する。

## 開発方針(要点)

詳細は [docs/arch/design-principles.md](docs/arch/design-principles.md)。

- ドメイン型は不変で、自分で不変条件を守る。永続化アノテーションを付けない。
- リポジトリはポート(インターフェース)とし、`default` メソッドを置かない。
- 業務ルールは、境界値のテストを先に書く。
- jOOQ の生成物は手で編集しない。マージ済みの Flyway migration は書き換えない。
- テストを削除・スキップ・弱体化して通さない。
