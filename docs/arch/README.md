---
okf_version: "0.2"
type: index
title: アーキテクチャ文書 目次
description: 技術スタックと設計方針の文書の一覧。run-buddy の構成を基本的に踏襲する。
tags: [architecture, index]
---

# アーキテクチャ文書 目次

本プロジェクトは [tis-abe-akira/run-buddy](https://github.com/tis-abe-akira/run-buddy) と基本的に同じ技術スタックを使う。

- [技術スタック](tech-stack.md) - 採用する言語・FW・DB・テスト・CI と、run-buddy からの差分
- [設計方針](design-principles.md) - ドメインモデル、ポート&アダプタ、永続化、テストの方針と、本システムへの当てはめ

## 方針

- 設計判断は ADR(`docs/adr/NNNN-kebab-case-title.md`)として残す。run-buddy の ADR-0001 と同じ運用とする。
- run-buddy の ADR は参照元であり、本プロジェクトに採用するかどうかは、各文書の「未決事項」で明示する。
- 要件は [../requirements/README.md](../requirements/README.md) を参照。
