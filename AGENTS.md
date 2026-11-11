# AGENTS.md

AI エージェント(Claude Code など)向けの、このリポジトリでの作業ルールです。

## プロジェクト概要

シンジケートローン管理システム。DDD とオブジェクト指向設計、テスト技法を適用した Java 開発を、Claude Code で自動化する取り組み。

- 開発プロセス: AI Unified Process(aiup)に乗る。スキルは `.claude/skills/` にコピー済み。実行順序は [docs/guides/aiup-workflow.md](docs/guides/aiup-workflow.md)。
- 要件: 正は `docs/requirements.md`(`/requirements` で作る)。[docs/brief/](docs/brief/index.md) はその元資料。
- 技術スタック・設計方針・ADR: [docs/architecture/](docs/architecture/index.md)(run-buddy と基本的に同じ)

## aiup の流儀に従う

aiup の成果物は、aiup の形式・置き場所・ID に従う。このリポジトリ独自の流儀とぶつかったら、aiup を優先する。**例外は未決事項の管理だけで、GitHub Issue を使う(下の「仕様の未決事項は GitHub Issue で管理する」)。**

| 成果物 | 置き場所 | 作る・直すスキル |
|---|---|---|
| 要件カタログ(FR-001 / NFR-001 / C-001)・用語集 | `docs/requirements.md`、`docs/glossary.md` | `/requirements` |
| エンティティモデル | `docs/entity_model.md` | `/entity-model` |
| ユースケース図・仕様(UC-001) | `docs/use_cases.puml`、`docs/use_cases/` | `/use-case-diagram`、`/use-case-spec` |
| 業務プロセス(BP-001) | `docs/processes/` | `/business-process` |
| テストケース(TC-001、業務を通したもの) | `docs/test_cases/` | `/test-case` |
| ADR(ADR-001) | `docs/architecture/decisions/` | `/architecture-decision` |
| スタックファイル | `docs/architecture/stack.md` | `/stack` |

- 上の成果物は、各スキルを通して作り、直す。手で直したら `/spec-review` を通す。
- 上の成果物には OKF のフロントマターを付けず、そのフォルダに `index.md` も置かない(aiup の形式とスクリプトを優先)。上の階層の `index.md`(`docs/index.md`、`docs/architecture/index.md`)からリンクする。
- 仕様の穴は推測で埋めず、GitHub Issue に記録する(次節)。
- `TC-` は aiup のテストケース専用。単体テストの数値例は `EX-` を使う。

## 仕様の未決事項は GitHub Issue で管理する

仕様の未決事項(仕様の穴、矛盾、決めるべき選択肢)の正は、**GitHub Issue** とする。aiup の `docs/open_questions.md`(OQ-XXX)と `/open-questions` は使わない。

- **起票**: タイトルの先頭に `[要確認]` を付ける。本文には「背景」「決めたいこと」(選択肢があれば並べる)「参照」(関係する文書と ID、親や関連の Issue 番号)を書く。
- **aiup のスキルとの関係**: スキルが「`docs/open_questions.md` に記録する」「`/open-questions` を実行する」と指示した場合は、その質問を代わりに Issue に起票し、報告に Issue 番号を書く。スキルが「blocking な質問」で作業を止める場面では、開いている `[要確認]` の Issue を同じ扱いにする。
- **決まったら**: 決定を Issue にコメントし、仕様(`docs/requirements.md`、ユースケース、エンティティモデル、ADR など)に反映する。反映を人が確認したらクローズする。
- **作業の管理**: 未決事項以外のタスクにも Issue を使ってよい。未決事項と見分けられるよう、タイトルの `[要確認]` で区別する。
- **トレードオフ**: `/next` と `/spec-review` は `docs/open_questions.md` だけを見るため、Issue の未決事項を数えない。進める前に、開いている `[要確認]` の Issue を確認する。

## ドキュメントの書き方

**aiup の成果物以外の文書は、OKF(Open Knowledge Format)v0.2 の Markdown(`.md`)で書くことを推奨する。**
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
- 設計判断は `/architecture-decision` で `docs/architecture/decisions/ADR-XXX-*.md` に残す。Accepted になった ADR の本文は書き換えず、新しい ADR で Superseded にする。
- 文書を追加・改名したら、そのフォルダの `index.md` も更新する。

## 開発方針(要点)

詳細は [docs/architecture/design-principles.md](docs/architecture/design-principles.md)。

- ドメイン型は不変で、自分で不変条件を守る。永続化アノテーションを付けない。
- リポジトリはポート(インターフェース)とし、`default` メソッドを置かない。
- テストの正はユースケース仕様。`/use-case-implement` → `/use-case-test` → `/use-case-coverage` の順に進め、業務ルールは許可と拒否の両側の境界値をテストする。
- jOOQ の生成物は手で編集しない。マージ済みの Flyway migration は書き換えない。
- テストを削除・スキップ・弱体化して通さない。仕様と食い違うテストは、失敗のまま残して報告する。
