---
okf_version: "0.2"
type: index
title: 要件定義 目次
description: シンジケートローン管理システムの要件定義文書の一覧と読む順序。
tags: [requirements, index]
---

# 要件定義 目次

シンジケートローン管理システムの要件定義文書です。
元ネタは [tis-abe-akira/ddd-ai](https://github.com/tis-abe-akira/ddd-ai) の業務要件・機能仕様で、記述の重複・表記ゆれを整理して再構成しています。

## 読む順序

1. [業務概要と用語](01-business-overview.md) - シンジケートローンの仕組み、登場人物、主要概念
2. [機能要件](02-functional-requirements.md) - 機能ごとの要件(FR-xx)と業務プロセス
3. [業務ルール](03-business-rules.md) - 検証・計算・状態遷移・手数料のルール(BR-xx)
4. [非機能要件・スコープ](04-non-functional-and-scope.md) - 品質要件、対象外、将来拡張、未確定事項

## 文書の扱い

- 要件ID(FR-xx / BR-xx / NFR-xx)は変更しても番号を使い回さない。廃止は文書内で「廃止」と明記する。
- 元ネタに矛盾・曖昧さがあった箇所は、各文書の「要確認」に集約している。確定したら本文へ反映して削除する。
- 技術スタックと設計方針は [../arch/README.md](../arch/README.md) を参照。
