# AI Unified Process skills (vendored copy)

- Source: https://github.com/AI-Unified-Process/marketplace
- Commit: 648476b422613a233e2aed6f540087f729a2ece5
- Copied: 2026-11-11
- License: Apache-2.0 (see LICENSE / NOTICE)

## Copied items
- aiup-core 3.17.0: skills/* -> .claude/skills/, agents/* -> .claude/agents/
- aiup-spring-boot 3.7.1: skills/spring-service, skills/spring-service-test, skills/spring-rest, skills/spring-rest-test -> .claude/skills/
- aiup-jooq 3.5.1: skills/jooq-persistence, skills/jooq-persistence-test -> .claude/skills/
- aiup-flyway 3.5.1: skills/flyway-schema -> .claude/skills/
- Not copied: the plugins' MCP server configs (optional per upstream: JavaDocs, jOOQ, context7) and evals/.

## Local modifications
- `project-override:architecture-docs`: a block inserted after the license comment of every SKILL.md / agent that reads
  `docs/architecture/stack.md`. It makes them read all files under `docs/architecture/`. Find them with
  `grep -rl project-override:architecture-docs .claude`. Reapply after updating the vendored copy.
