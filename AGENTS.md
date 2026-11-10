# AGENTS.md

AI エージェント(Claude Code など)向けの、このリポジトリでの作業ルールです。

## プロジェクト概要

シンジケートローン管理システム。DDD とオブジェクト指向設計、テスト技法を適用した Java 開発を、Claude Code で自動化する取り組み。

- 要件: [docs/requirements/](docs/requirements/index.md)
- 技術スタック・設計方針: [docs/arch/](docs/arch/index.md)(run-buddy と基本的に同じ)

## ドキュメントの書き方

**文書は OKF(Open Knowledge Format)v0.2 の Markdown(`.md`)で書くことを推奨する。**
仕様: [GoogleCloudPlatform/knowledge-catalog の okf/SPEC.md](https://github.com/GoogleCloudPlatform/knowledge-catalog/blob/main/okf/SPEC.md)

- `docs/` を OKF のバンドル(ルート)とする。`docs/index.md` だけが、フロントマターに `okf_version: "0.2"` を持てる。
- 概念を書く文書(`index.md` / `log.md` 以外のすべての `.md`)は、先頭に YAML フロントマターを置く。**必須は `type` のみ**。`title`・`description`(1文)・`tags` は推奨。

  ```yaml
  ---
  type: <文書の種類。例: requirements / architecture / adr / guide>
  title: <人が読む題名>
  description: <1文の要約>
  tags: [<タグ>, ...]
  ---
  ```

- `index.md` は各フォルダの目次。**`docs/index.md` のルート以外はフロントマターを置かない**。本文は `# 見出し` の下に `* [題名](相対パス) - 説明` を並べる。説明には、リンク先の `description` を使う。
- `index.md` と `log.md` は予約名。概念の文書名に使わない。`log.md` は日付見出し(`YYYY-MM-DD`)を新しい順に並べる。
- リンクは相対パスか、バンドルルート起点(`/` 始まり)で書く。出典を持つ文書は、フロントマターの `sources` に書く(v0.1 の本文 `# Citations` は廃止)。
- 本文は日本語。図は Mermaid で書く。
- 未確定・矛盾の見つかった事項は、本文に断定で書かず、文書末尾の「要確認」「未決事項」に集める。
- 設計判断は `docs/adr/NNNN-kebab-case-title.md` に ADR として残す。Accepted になった ADR の本文は書き換えず、新しい ADR で Superseded にする。
- 文書を追加・改名したら、そのフォルダの `index.md` も更新する。

## 開発方針(要点)

詳細は [docs/arch/design-principles.md](docs/arch/design-principles.md)。

- ドメイン型は不変で、自分で不変条件を守る。永続化アノテーションを付けない。
- リポジトリはポート(インターフェース)とし、`default` メソッドを置かない。
- 業務ルールは、境界値のテストを先に書く。
- jOOQ の生成物は手で編集しない。マージ済みの Flyway migration は書き換えない。
- テストを削除・スキップ・弱体化して通さない。
