#!/usr/bin/env bash
# jOOQ のクラスを、Flyway の migration から生成し直す。
# 使い捨ての PostgreSQL を Docker で起動し、migration を当ててから、モジュールごとのスキーマを生成する。
# 生成物(各モジュールの persistence/jooq/generated)はコミットする。手で編集しない。
# migration を変えたら、このスクリプトを実行して、出たコンパイルエラーを直す。
set -euo pipefail
cd "$(dirname "$0")/.."

container="syndicate-loan-jooq-codegen-$$"
password="codegen"
docker run -d --rm --name "$container" -e POSTGRES_PASSWORD="$password" -e POSTGRES_DB=codegen \
  -p 127.0.0.1::5432 postgres:17-alpine >/dev/null
trap 'docker stop "$container" >/dev/null' EXIT

port="$(docker port "$container" 5432/tcp | head -1 | sed 's/.*://')"
for _ in $(seq 1 60); do
  if docker exec "$container" pg_isready -h 127.0.0.1 -U postgres -d codegen >/dev/null 2>&1; then break; fi
  sleep 1
done

url="jdbc:postgresql://127.0.0.1:${port}/codegen"
./mvnw -B -q -Pjooq-codegen \
  -Djooq.codegen.url="$url" -Djooq.codegen.user=postgres -Djooq.codegen.password="$password" \
  flyway:migrate \
  jooq-codegen:generate@organization jooq-codegen:generate@syndicate jooq-codegen:generate@facility
echo "jOOQ classes generated."
