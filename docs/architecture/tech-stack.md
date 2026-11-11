---
type: architecture
title: 技術スタック
description: 本プロジェクトで採用する技術スタック(Java 25 / Spring Boot 4 / PostgreSQL / jOOQ / Flyway / React 等)。run-buddy を基本踏襲し、差分と未決事項を明示する。
tags: [architecture, tech-stack, spring-boot, java, postgresql, jooq, react]
---

# 技術スタック

基本は [run-buddy](https://github.com/tis-abe-akira/run-buddy) と同じ。バージョンは run-buddy の調査時点(2026-11)のもので、採用時に再確認する。

## 構成

| 領域 | 採用技術 | 備考(run-buddy の ADR) |
|---|---|---|
| リポジトリ構成 | モノレポ(`backend/` `frontend/` `docs/`) | ADR-0002。ルートに全体ビルドツールは置かない |
| 言語 | Java 25 | ADR-0003 |
| フレームワーク | Spring Boot 4.1 系(`spring-boot-starter-webmvc`) | ADR-0003。3 系とテスト関連のパッケージが異なる点に注意 |
| ビルド | Maven(Maven Wrapper) | `spring-boot-starter-parent` を継承 |
| パッケージ構成 | `com.example.<app>` 配下を機能ごとに分割(package by feature) | ADR-0003 |
| DB | PostgreSQL 17 | ローカルは docker compose で起動 |
| 永続化 | jOOQ(型安全な SQL DSL + コード生成) | ADR-0007。JPA は採用しない |
| マイグレーション | Flyway(`src/main/resources/db/migration`) | マージ済みの migration は書き換えない |
| API 仕様 | springdoc-openapi | OpenAPI を frontend の型生成に使う |
| frontend | Vite + React + TypeScript + TanStack Router + TanStack Query(SPA) | ADR-0005。SSR は使わない |
| backend テスト | JUnit 5、MockMvcTester(aiup `spring-rest-test`)、Testcontainers(PostgreSQL)、ArchUnit | |
| frontend テスト | Vitest + Testing Library | |
| 認証 | ID プロバイダを差し替えられる構成。候補: 簡易な自作、Keycloak | 本プロジェクト独自(#7, #8) |
| 耐障害性 | リトライ、サーキットブレーカー、タイムアウト。候補: Resilience4j、Spring Framework の標準機能 | 本プロジェクト独自。技術検証を兼ねる(#8) |
| バッチ | Spring Batch(振替依頼データの作成) | 本プロジェクト独自。技術検証を兼ねる(#8) |
| 外部サービスのモック | 信用格付・金利指標。遅延・エラーを再現できること。候補: WireMock、テスト用のアダプタ | 本プロジェクト独自(#8) |
| CI | GitHub Actions(backend: `./mvnw test`、jOOQ 生成物の差分チェック、ArchUnit のミューテーションチェック / frontend: `npm run build` + `npm test`) | |

## 元ネタ(ddd-ai)との差分

[ddd-ai](https://github.com/tis-abe-akira/ddd-ai) は Spring Boot 3.2 / Java 17 / H2 / JPA で実装されていた。本プロジェクトでは次のとおり変える。

| 項目 | ddd-ai | 本プロジェクト |
|---|---|---|
| Java / Spring Boot | 17 / 3.2.1 | 25 / 4.1 系 |
| DB | H2(インメモリ) | PostgreSQL |
| 永続化 | Spring Data JPA(エンティティ=ドメインモデル) | jOOQ(ドメインは永続化に依存しない) |
| 状態管理 | Spring State Machine | 未決(下記) |
| 構成 | 3 層(Controller / Service / Repository / Entity) | ポート&アダプタ([設計方針](design-principles.md)) |

## 本プロジェクトでは使わない(run-buddy 固有)

Garmin 連携、FIT ファイル、Leaflet の地図、Spring AI による AI 相談、OpenWiki による Wiki 生成は run-buddy 固有の要件であり、取り込まない。必要になった時点で別途決める。

## 未決事項

- **行レベルセキュリティ(RLS)**: run-buddy は複数ユーザーのデータ分離に使っている。本システムで複数ユーザーを想定するか([要件](../brief/04-non-functional-and-scope.md))次第。採用しない場合は、`user_id` と `RlsTransactionManager` を持ち込まない。
- **状態遷移の実装**: Spring State Machine を使うか、ドメインオブジェクト自身が遷移を守る(sealed interface / enum + 遷移メソッド)か。run-buddy の方針(不変なドメイン型が自分で不変条件を守る)に合わせるなら後者が自然。ddd-ai の docs/state-machine の分析も参考にする。
- **耐障害性のライブラリ**: Resilience4j か、Spring Framework の標準機能か。Spring Boot 4.1 系での対応を確認してから決める。
- **認証の層**: BFF やゲートウェイとするか、バックエンド内の部品とするか。Keycloak を使う場合の起動方法(docker compose など)。
- **frontend の範囲**: 要件上は API が中心。UI をどこまで作るか。
- **アプリの Java パッケージ名とアーティファクト名**(`com.example.syndicateloan` を仮置き)。
