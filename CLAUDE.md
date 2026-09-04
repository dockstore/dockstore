# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Dockstore is the web service (backend) component of dockstore.org — a registry for sharing tools and
workflows described in CWL, WDL, Nextflow, or Galaxy, packaged in Docker. It's a leading implementor of
GA4GH's Tool Registry Service (TRS) API. The related Angular UI lives in a separate repo
(`dockstore/dockstore-ui2`), as does the CLI (`dockstore/cli`) — this repo only contains the backend. User-facing
documentation lives in a separate repo, `dockstore/dockstore-documentation`, and the project has a discussion
forum at https://discuss.dockstore.org/.

## Dependency conventions

The webservice is built on Dropwizard — favor Dropwizard's own recommended libraries/patterns for things it has
an opinion on (configuration, health checks, metrics, bundles, etc.) over ad hoc alternatives. Beyond that,
prefer, in order: (1) built-in Java 17/21 features, (2) a third-party library already pulled in via Maven
elsewhere in the project, (3) a new third-party dependency — only reach for a new one when neither of the above
covers the need.

## Branching

The repo follows Hubflow (gitflow) conventions: `develop` is the main integration branch (this repo's default
branch for PRs), with work done on `feature/*` branches (e.g. `feature/http5_aws`) branched from and merged back
into `develop`, `hotfix/*` branches for urgent fixes, and `release/*` branches cut for releases.

## Build

This is a multi-module Maven project (Java 21). Use the wrapper if Maven isn't installed locally.

```
./mvnw clean install                              # build all modules
mvn clean install -Punit-tests                    # build + run only unit tests (fast, no confidential data needed)
mvn clean install -Pintegration-tests             # requires the confidential test data bundle (CI / team members only)
mvn clean install -Dtest=SomeClassName test        # run a single test class
mvn clean install -Dtest=SomeClassName#someMethod test  # run a single test method
```

Modules (in `pom.xml`, build order matters): `bom-internal`, `dockstore-common`, `dockstore-language-plugin-parent`,
`dockstore-webservice`, `swagger-java-client`, `openapi-java-client`, `dockstore-integration-testing`, `reports`.

`swagger-java-client` and `openapi-java-client` are largely generated (via swagger-codegen-maven-plugin) from
`dockstore-webservice/src/main/resources/swagger.yaml` / the OpenAPI spec — don't hand-edit generated sources under
`generated/`; regenerate by pointing the plugin's `inputSpec` at the updated spec instead. More generally, any
`generated/` directory anywhere in the repo (e.g. `dockstore-common/generated/`, `dockstore-webservice/generated/`)
is build output, not source — this includes the `pom.xml` files under `generated/src/main/resources/`, which are
produced from each submodule's own root `pom.xml`. To change a dependency/version that flows into a generated
`pom.xml`, edit it in `bom-internal` first (the shared bill-of-materials), then in the specific submodule's root
`pom.xml` if the change only applies there. When validating a dependency/version change, always build the whole
project from the root (e.g. `./mvnw clean install -DskipTests`) rather than building individual modules with
`-pl`/`-am` — a partial-reactor build regenerates `generated/` `pom.xml` files (and `THIRD-PARTY-LICENSES.txt`)
incorrectly, since they're derived from the full module set.

### Test categories (JUnit 5 `@Tag`)

Tests are grouped by tag, defined in `dockstore-common` (e.g. `ConfidentialTest`, `NonConfidentialTest`,
`ToolTest`, `WorkflowTest`, `RegressionTest`, `BenchmarkTest`, `LanguageParsingTest`, `LocalStackTest`,
`HoverflyTest`). Maven profiles select which tags run (see `pom.xml` `<profiles>`):
`non-confidential-tests`, `integration-tests`, `tool-integration-tests`, `workflow-integration-tests`,
`regression-integration-tests`, `language-parsing-tests`, `localstack-tests`, `hoverfly-tests`.

- `ConfidentialTest` — needs the encrypted confidential bundle (GitHub/Quay/Bitbucket credentials); ask the team for access.
- Most integration tests (`*IT.java`, under `dockstore-integration-testing`) extend `BaseIT`, which spins up
  a Dropwizard test app against a local Postgres instance — Postgres must be running and configured per
  `.circleci/config.yml` even for local integration-test runs (not needed for plain unit tests).
- Hoverfly-based tests simulate HTTPS responses and need Hoverfly's (older v0.10.3) certificate imported locally —
  see README for the exact steps.

### Code style

`checkstyle.xml` (with `checkstyle-suppressions.xml`) is enforced during the build via maven-checkstyle-plugin;
`codestyle.xml` is the matching IntelliJ code-style profile to import.

### Database migrations

Schema changes are Liquibase changelogs under `dockstore-webservice/src/main/resources/migrations*.xml`, chained
from the root `migrations.xml` (one file per released version, plus `migrations.test.*.xml` fixtures used only by
tests). `propose_migration.sh` and `scripts/check_migrations.sh` help scaffold/validate new migration files —
add a new versioned migration file rather than editing a released one.

### Running locally

Needs a separately-configured `dockstore.yml` (Dropwizard config, see the template under
`dockstore-integration-testing/src/test/resources/dockstore.yml`) stored outside the repo (e.g. `~/.dockstore/`),
plus a local Postgres instance. Then:
```
java -jar dockstore-webservice/target/dockstore-webservice-*.jar server ~/.dockstore/dockstore.yml
```
`docker-compose.yml` is an alternative way to bring up dependencies. Swagger UI is then browsable at
`http://localhost:8080/static/swagger-ui/index.html`.

### CI

CircleCI (`.circleci/config.yml`) builds and shards the test suite (including determining which non-confidential
tests changed via `scripts/generate-test-lists.sh`). Add `[skipTests]` to a commit message to skip the test shards
for changes that don't affect code (deploys still run on every tag, so this isn't the same as `[skip ci]`).

### Versioning

Components generally follow semantic versioning; the version is split into `revision`/`changelist` properties in
`pom.xml` (e.g. `1.21` + `.0-SNAPSHOT`). Pre-release identifiers (`alpha`, `rc`, etc., passed via `-Dchangelist=...`
as in the build example above) are used for releases to the staging environment.

## Architecture

### Module responsibilities

- **dockstore-common** (`io.dockstore.common`) — shared model/enum/utility code and the JUnit test-category tag
  classes (`ConfidentialTest`, `ToolTest`, etc.), used by both the webservice and the language-plugin infra.
- **dockstore-language-plugin-parent** — the SPI/interfaces that out-of-tree language plugins implement
  (`ServicePrototypePlugin`, etc.), consumed via `LanguagePluginHandler`.
- **dockstore-webservice** — the actual Dropwizard application; see below.
- **swagger-java-client / openapi-java-client** — generated HTTP clients for the webservice's own API (Swagger 2.0
  and OpenAPI 3.0, respectively), consumed by the separate CLI project. The project is deprecating Swagger in
  favor of OpenAPI: where a REST resource is exposed through both clients, prefer `openapi-java-client`.
- **dockstore-integration-testing** — all `*IT.java` integration tests (these live in their own module so they can
  run against a fully-assembled webservice + Postgres, separate from the webservice module's plain unit tests).
- **reports** — JaCoCo coverage aggregation (`coverage` profile).

### Webservice package layout (`io.dockstore.webservice`)

- **resources/** — JAX-RS resource (controller) classes, one per API surface — `WorkflowResource`,
  `DockerRepoResource` (tools), `OrganizationResource`, `HostedWorkflowResource`/`HostedToolResource`,
  `UserResource`, `TokenResource`, `CollectionResource`, `EventResource`, `MetadataResource`, etc. Common
  cross-cutting behavior (auth checks, aliasing, starring) is factored into interfaces like
  `AuthenticatedResourceInterface`, `AliasableResourceInterface`, `StarrableResourceInterface`, rather than a
  base class, since resources need to extend different entity-specific abstract classes
  (`AbstractWorkflowResource`, `AbstractHostedEntryResource`).
- **core/** — JPA/Hibernate entities (Workflow, Tool/DockerRepo, Organization, Collection, User, Token, etc.), with
  subpackages for source-specific metadata (`gitlab`/`dockerhub`/`webhook`) and secondary concerns
  (`metrics`, `tooltester`, `languageparsing`, `dag`, `database` — DB-level projections/views).
- **jdbi/** — DAO layer (one DAO per entity, JDBI-based) sitting between resources and core entities. For
  performance, prefer pushing work down the stack: do it in Postgres (e.g. a named/native query) if possible,
  otherwise in a JPA query, and only fall back to filtering/transforming in Java code when neither covers it.
  Many DAOs/entities already have `@NamedQuery`/`@NamedNativeQuery` examples to follow.
- **languages/** — per-workflow-language handling: `LanguageHandlerInterface`/`AbstractLanguageHandler` implemented
  by `CWLHandler`, `WDLHandler`, `NextflowHandler`, `JupyterHandler`, with `LanguageHandlerFactory` dispatching
  by descriptor type, and `LanguagePluginHandler` bridging to plugins built against
  `dockstore-language-plugin-parent`.
- **permissions/** — sharing/collaborator permissions abstraction (`PermissionsInterface`), with a Broad Institute
  SAM-backed implementation (`SamPermissionsImpl`) selected in production and in-memory/no-op implementations
  for tests, chosen via `PermissionsFactory`.
- **helpers/** — the largest package: source-control clients (GitHub/GitLab/Bitbucket), cloud/registry
  integrations, DOI helpers, notification/webhook plumbing, and other cross-resource utilities.
- **filters/** — servlet/JAX-RS filters (e.g. request logging, CORS-type concerns).
- **doi/** — DOI (Digital Object Identifier) minting integration for published entries.
- **api/** — thin API-shape classes distinct from `core` persistence entities.

Generated OpenAPI/Swagger server stubs (`io.swagger.api`/`io.swagger.model`, `io.openapi.api`/`io.openapi.model`)
are checked into `dockstore-webservice/src/main/java` but are generated from the spec — treat them like the
generated client modules.

### Key cross-cutting patterns

- Entries (tools, workflows, services, notebooks) share a lot of behavior — look for the shared interfaces in
  `resources/` and shared entity base classes in `core/` before adding new per-type logic; most new entry-type
  behavior is a matter of extending those, not writing new parallel code paths.
- Source control integration (GitHub/GitLab/Bitbucket refresh, webhooks) flows through `helpers/` clients into
  `AbstractWorkflowResource`/`HostedWorkflowResource` and the `core/webhook` and `core/gitlab` packages.
- Language parsing/validation is dispatched through `LanguageHandlerFactory` — when adding support for a new
  descriptor-language feature, check whether it needs to change in every `*Handler` implementation or just one.
