# Product.berlin engineering guidelines

These instructions apply to the repository. Preserve the agreed architecture and verification workflow when changing code. Keep changes scoped to the user's request and update these instructions when an agreed architectural decision changes.

## Architecture

- Use Kotlin/JS and React for the website, Ktor for client HTTP, kotlinx.serialization for wire data, and Koin for application composition. Keep versions in the existing version catalogue and lockfiles.
- `webApp` is the umbrella module: entry point, HTML shell, configuration, and dependency injection. Business logic and reusable UI belong in the modules below.
- `app-domain` owns entities, repository interfaces, use cases, and states. Keep it independent of React, Ktor, serialization DTOs, Cloudflare, and concrete data implementations.
- `app-data` implements domain repositories, owns HTTP/data sources, and maps `api-contract` DTOs to domain entities. It depends on `app-domain` and `api-contract`, never presentation or `webApp`.
- `app-presentation` owns React screens and the design system, consuming domain use cases/states. Do not access databases or make HTTP calls directly from UI components. See [its scoped instructions](app-presentation/AGENTS.md) when changing UI.
- `api-contract` contains serializable transport DTOs shared by client and Worker. Keep it independent of domain, UI, and concrete repositories.
- `api-worker` owns Cloudflare handlers and D1 access, depending on `app-domain` and `api-contract`. Browser code must not depend on it.
- Keep repositories, use cases, states, entities, DTOs, and per-type mappers in separate files. Keep component variants and presentation models beside their owning components, also in separate files.
- Preserve existing public use-case/state contracts during internal refactors unless the task includes changing those contracts.

For local operation, module responsibilities, API behavior, or deployment details, consult the relevant section of [README.md](README.md). Do not turn a focused change into a stack migration or dependency upgrade.

## Tests and validation

- Add or update tests for behavioral changes and bug fixes at the owning layer. Assert observable behavior, including relevant errors and edge cases. For pure moves/renames, preserve existing tests and verify imports/builds instead of adding tests that merely mirror the implementation.
- Domain/data tests live in their module's `commonTest`; HTTP tests use Ktor MockEngine. React tests live in `app-presentation/src/jsTest`, run in Chrome Headless, and load the shipped CSS. Cloudflare integration tests use local Wrangler/D1, not remote services.
- `npm test` / `./gradlew allTests` covers every configured Gradle test target in every module. Keep this aggregate command so future modules' tests are included.
- `./gradlew allTests --rerun-tasks` forces fresh execution when the IDE needs new test events. The shared **All tests** configuration uses it. `UP-TO-DATE` means results were reused; `NO-SOURCE` may legitimately mean a module has no tests.
- Use JDK 21, Node.js 24 for repository npm tooling, and Chrome/Chromium for browser tests. Use the checked-in Gradle wrapper. Set `CHROME_BIN` only if browser discovery needs it.

| Change | Verification |
| --- | --- |
| Kotlin or Gradle Kotlin | `./gradlew ktlintFormat`, then `./gradlew ktlintCheck` and affected tests |
| Presentation behavior/styles | `./gradlew :app-presentation:jsBrowserTest :webApp:jsBrowserDistribution`; visually inspect affected UI at desktop/mobile sizes |
| Module/package refactor | `./gradlew ktlintCheck allTests` and affected production builds |
| Runtime, build, or CI changes ready for handoff | `npm run check` (lint, all Gradle tests, both builds, D1/API and launcher integration tests) |
| Documentation only | Verify referenced paths/commands; no unrelated test runs required |

- Use `npm ci` when installing JavaScript tooling. Do not edit generated build outputs, vendor directories, or lockfiles by hand.
- Fix check failures rather than disabling tests, widening lint exclusions, or adding `continue-on-error`. Report a blocked check and its cause explicitly.
- Report the commands and outcomes actually verified. Distinguish compile/test success, browser inspection, GitHub CI results, and remote deployment evidence.

## Lint, CI, and delivery

- ktlint-gradle is the Kotlin/Gradle linter. Root `ktlintFormat` and `ktlintCheck` cover all modules. Keep CI linting in check mode; formatting belongs in the working tree.
- `.github/workflows/ci.yml` runs `npm run check` for pull requests and as a reusable deployment prerequisite. Keep checks credential-free and runnable locally.
- Deploy the Worker and website artifacts produced by the checks job for the same commit SHA. Preserve the checks dependency, environment isolation, and deployment/seed concurrency.
- Keep credentials in GitHub environment secrets and local ignored environment files. Use `BUILD_SHA` for deployed version identity and complete HTTPS `DEPLOY_URL` values for smoke checks.
- Keep D1 migrations numbered and backward-compatible with the currently deployed Worker. Add a migration rather than editing an already-applied one. Migrations and seeding are separate operations; ordinary push deployments must not seed.
- The scheduled Worker collects Google News RSS weekly and matches headlines against `cloudflare/startups.json`. Keep ranking deterministic, deduplicate articles, and publish D1 snapshots atomically. Every scheduled event resolves to the most recent complete Monday–Sunday UTC week, so off-schedule or retried events cannot replace a published edition mid-week. Preserve the previous ranking on failed or empty collections. Mock seeds and `/design-system` stay visibly labeled; live rankings must not be labeled as demo data. Keep tests independent of upstream news services.
- Company logos are hotlinked from Brandfetch by the browser. The Worker builds logo URLs only for companies in the ranking response; it never fetches, caches or proxies logos, and UI falls back to letter marks.
- Use the existing local launcher; stop temporary servers you start. Do not kill another IDE/server process to free a port.

## Code Review Rules

- Flag dependency-direction violations, data/network access in UI, or business logic added to the umbrella module; place the behavior in its owning layer instead.
- Flag basic design-system components importing layouts/showcase, or reusable layouts importing showcase/screen implementations.
- Flag behavioral changes without relevant verification, discarded accessibility semantics, or tests that silently stop running in CI.
- Flag deployment paths that bypass checks, mix environment data, upload a different commit's artifacts, or seed on ordinary deploys.
- Leave formatting enforcement to ktlint. Explain substantive findings with concrete behavior and file references.
