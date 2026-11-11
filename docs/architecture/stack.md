# Stack

**For the agent:** read before writing code. This file is the AI Unified Process record of the technical layers,
independent of how the team documents its architecture. The Layers table says which layer skill builds and tests
each technical layer: `/use-case-implement` runs the Skill column bottom-up, `/use-case-test` runs the Test skill
column from the integration layer up, and `/use-case-coverage` searches for the markers in the last table.

**Last updated:** 2026-11-11

## Layers

| Layer       | Technology                                                    | Skill              | Test skill              | Location |
|-------------|---------------------------------------------------------------|--------------------|-------------------------|----------|
| Migration   | Flyway(C-007)                                                 | `flyway-schema`    | –                       | `backend/src/main/resources/db/migration` |
| Persistence | jOOQ(C-007)                                                   | `jooq-persistence` | `jooq-persistence-test` | 各モジュールの `persistence` パッケージ |
| Client      | Spring `RestClient`、耐障害性(リトライ・サーキットブレーカー) | `spring-client`    | `spring-client-test`    | 各モジュールの `client` パッケージ |
| Service     | Spring Boot 4.1 系(C-006)                                     | `spring-service`   | `spring-service-test`   | 各モジュールの `application` パッケージ(ドメイン型は `domain`) |
| API         | Spring MVC の REST API                                        | `spring-rest`      | `spring-rest-test`      | 各モジュールの `web` パッケージ |
| UI          | Vite + React + TypeScript + TanStack Router(C-008)            | –                  | –                       | `frontend/` |
| E2E         | –                                                             | –                  | –                       | –        |

**Integration layer:**

| Use case offered through | Tested in | Test skill         |
|--------------------------|-----------|--------------------|
| UI                       | API       | `spring-rest-test` |
| API                      | API       | `spring-rest-test` |
| Service                  | Service   | `spring-service-test` |

A layer the project does not have is marked `–` in every column (a UI layer in a REST service). The Integration
layer table keeps a row only when the project has that layer — a REST service drops the UI row, a Vaadin Flow
project without a service layer drops the Service row. Its Test skill column names
what the Layers table names for the layer in Tested in (`–` while the skill is not installed). A use
case is tested in the first row its `**Layers:**` line names, or in the first row when it has no such line. That layer's tests run in process against the real database
and exercise every layer below it, so `/use-case-test` runs its test skill and the ones above it and writes no tests
of their own for the layers below unless a run names them (`/use-case-test UC-001 persistence`).

- **UI**: React の SPA は REST API を通してサービスに届く(クライアント側の UI)。そのため、UI から使うユースケースも API の層で作り、API の層でテストする。React を作る層スキルは aiup に無いので Skill は `–` で、`/use-case-implement` は UI を作らない(`docs/guides/aiup-workflow.md` の未決事項)。
- **E2E**: 今は持たない。REST API のテストが一番外側のテストになる。UI を作り始めるときに `aiup-playwright` を入れて、この表を更新する。
- **Service**: 振替依頼データのバッチ(UC-033・UC-034、Spring Batch、C-010)のように、スケジューラが始めるユースケースはサービスの層でテストする。

## External systems

| System           | Contract | Location |
|------------------|----------|----------|
| 信用格付サービス | –        | `com.example.syndicateloan.syndicate.client.creditrating` |
| 金利指標サービス | –        | `com.example.syndicateloan.loan.client.rateindex` |

The systems our system calls, by the name the use cases write on their `**Secondary Actors:**` line: the Client
layer builds an adapter for each one from its contract (OpenAPI or AsyncAPI; `–` while there is none). A secondary
actor without a row is a person or a role. Without a Client layer the section is dropped.

- どちらも接続先はモックとし、遅延とエラーを再現できるようにする(C-011)。リトライ・サーキットブレーカーの技術検証に使う(`docs/brief/07-external-integration.md`)。契約(OpenAPI)はまだ無い。
- ID プロバイダ(UC-006 の副アクター)は、この表に入れない。認証はフロントエンドとバックエンドの間の認証の層で、標準の OpenID Connect で受ける(Issue #7、#28)。

## Module layout

- **バックエンド**: `backend/` の一つの Maven プロジェクト(単一モジュール)。ルートのパッケージは `com.example.syndicateloan`(仮。`tech-stack.md` の未決事項)。
- **アプリケーションのモジュール**: ルートの直下のパッケージを Spring Modulith のモジュールとする。一覧と依存の規則は [設計方針](design-principles.md) の「想定するパッケージ」と「ワークフローの独立」、ADR-002・ADR-003 による。
  - `organization`(利用組織・利用者・ロール・職位)と `workflow`(承認ワークフロー)は、業務から独立したモジュール。
  - 業務のモジュールは `party`・`syndicate`・`facility`・`loan`・`fee`・`transaction`。共通の値(`Money` など)は `shared`。
- **モジュールの中の層**: 各モジュールの下に、次のパッケージを置く。
  - `domain`: 不変のドメイン型と、リポジトリ・外部システムのポート(インターフェース)
  - `application`: `@Service` と `@Transactional`
  - `persistence`: jOOQ のアダプタ(`@Repository`)
  - `client`: 外部システムのアダプタ
  - `web`: REST のコントローラと DTO
- **spring-service のレイアウト**: 単一モジュールのため、`spring-service` の flat single module として扱う。ただし、ポートは `domain` のインターフェースとし、`persistence` のアダプタがそれを実装する([設計方針](design-principles.md) 第2節)。
- **jOOQ の生成物**: データベースのスキーマごとに、そのスキーマを持つモジュールの `persistence.jooq.generated` に生成する(`workflow` のスキーマは `workflow` モジュール、ADR-002)。手で編集しない。
- **モジュールの公開 API**: Spring Modulith の既定どおり、モジュールの直下のパッケージにある型が公開 API になり、`domain` などのサブパッケージは内部になる。他のモジュールから使わせる型(`workflow` の申請の API と公開イベントなど)は、モジュールの直下に置く。`shared` はオープンなモジュールで、どのモジュールからも中を使える。
- **テスト**: `backend/src/test/java` に、本体と同じパッケージで置く。構造テスト(Spring Modulith と ArchUnit)は `com.example.syndicateloan.architecture` に置く(`docs/guides/aiup-workflow.md` の 2-2)。規則が効くことを確かめるフィクスチャは、本体のモジュール検出に混ざらないよう、別のルートパッケージ(`com.example.archfixture`、`com.example.modfixture`)に置く。
- **フロントエンド**: `frontend/` の Node プロジェクト。

## API contract

- **方式**: code-first。springdoc が、コードから OpenAPI の文書を生成する(`/v3/api-docs`)。生成した文書は出力で、入力にしない。フロントエンドはこの文書から TypeScript の型を作る(`tech-stack.md`)。
- **パス**: REST API はすべて `/api` の下に置く。
- **エラー**: RFC 9457 の Problem Details で返す。
- **DTO**: Java の record とし、`XxxRequest`・`XxxResponse` と名付ける。

## Commands

| Purpose            | Command |
|--------------------|---------|
| Build              | `cd backend && ./mvnw verify`(JDK 25 が要る。`JAVA_HOME` を JDK 25 に向ける) |
| Unit tests         | `cd backend && ./mvnw test` |
| Integration tests  | `cd backend && ./mvnw test`(データベースのテストは Testcontainers で PostgreSQL を起動する) |
| Structure tests    | `cd backend && ./mvnw test -Dtest='Architecture*,Modularity*'` |
| Frontend tests     | `cd frontend && npm test` |
| End-to-end tests   | – |

## Markers

Each layer skill keeps its own rows here: on every run it checks that the rows for its layer match its marker
reference and corrects them. Do not edit the rows by hand; rerun the layer skill instead.

| Layer | Skill | Kind | Marker to grep for |
|-------|-------|------|--------------------|
| All   | –     | Code | `// UC-XXX BR-YYY:` directly above the method, query condition, or validator that enforces the rule |
