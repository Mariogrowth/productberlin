# Product.berlin

A Kotlin/JS + React website showing ten Berlin startups and expandable fictional news. Cloudflare Workers serves the website and a Kotlin/JS API; D1 stores the demo ranking. There are no upstream scraping or RSS requests yet.

## Working with coding agents

[AGENTS.md](AGENTS.md) records the repository's architecture, testing, lint, CI, and delivery rules. [app-presentation/AGENTS.md](app-presentation/AGENTS.md) adds the component/layout boundaries and UI-specific conventions. Keep these files versioned and update them alongside agreed changes; use them as the shared source of guidance rather than relying on one chat's history.

Codex discovers repository instruction files automatically; see the [official AGENTS.md guide](https://learn.chatgpt.com/docs/agent-configuration/agents-md). For another agent, configure its instruction entry point to read these files rather than duplicating the rules. A fresh session can verify discovery by asking it to summarize the applicable architecture and validation commands.

Instructions guide implementation; CI checks provide executable validation. `npm run check` runs ktlint, all configured Gradle tests, both production builds, local D1/API tests, and launcher tests. Architecture/package boundaries additionally require review; they are not currently covered by a dedicated architecture checker. To prevent merging failed checks, configure a GitHub ruleset/branch protection requiring the checks job on `main` and restrict bypasses. That repository setting is managed separately from these files; adding instructions or a workflow does not enable it automatically.

## Run locally

Requirements: JDK 21. The saved IDE **webApp** run configuration now launches the complete app (both website and API):

```sh
./gradlew runLocal
```

Gradle supplies Node.js and builds the frontend and Worker. The launcher installs pinned Cloudflare tooling, applies local D1 migrations, seeds missing demo data, and starts Wrangler. Published data survives repeated starts. No Cloudflare account or credentials are needed. Run only one full-app/Worker session at a time. The launcher checks port 8787 before dependency installation or database changes and reports an actionable error if occupied; it never kills an existing server. Stopping the launcher terminates its Wrangler/runtime process group. The debugger uses an automatically assigned port, avoiding conflicts on 9229.

If Node.js 24 and npm are already installed, `npm start` performs the same build/setup/start sequence. You do not need to run database setup separately.

Open http://localhost:8787. The real path is React → Ktor HTTP → Worker → local D1. The “Why” buttons reveal news returned with the ranking. Data persists in `.wrangler/state/` and is separate from remote databases. Startup order, movement, explanations, and news are fictional and visibly labeled as a demo.

For the full-app run, restart `webApp` / `runLocal` after Kotlin edits, or run `npm run build` in another terminal; Wrangler reloads generated files. For fast frontend iteration, leave `npm run dev` running on port 8787 and run `npm run dev:web` in another terminal. Webpack serves the UI on port 8080 and proxies `/api` to Wrangler. `dev:web` alone does not start the API: a 504 on `/api/rankings/weekly` means Wrangler is missing or unreachable. Use the full-app run configuration by default.

```sh
npm run db:migrate:local
npm run db:seed:local  # Safe to rerun; a published demo snapshot is not overwritten
npm run dev           # Serve an already-built app
```

## Modules and dependency boundaries

```text
webApp (Main, HTML shell, Koin composition, build configuration)
  ├── app-presentation ──► app-domain
  └── app-data ──────────► app-domain + api-contract

api-worker ─────────────► app-domain + api-contract
  ├── API handler
  ├── D1 repository
  └── small JavaScript Cloudflare entry adapter

cloudflare/             SQL migrations and mock fixture
scripts/                local/remote DB tooling, deployment configuration, tests
.github/workflows/      checks, deployment, manual seeding
```

- `app-domain`: framework-independent entities, repository interfaces, use cases, and application states. Published order is preserved and duplicate startup IDs are rejected.
- `app-data`: Ktor browser HTTP client, repository implementation, and DTO-to-domain mapping. MockEngine and its fixture exist only in test sources.
- `app-presentation`: React UI, loading/error/retry states, responsive styles, and local assets. It consumes domain types and use cases.
- `api-contract`: serializable request/response boundary shared by browser and Worker. One DTO per file; no domain or UI dependencies.
- `api-worker`: Kotlin/JS API and D1 implementation of the domain repository. Cloudflare's native Fetch/D1 interfaces are wrapped at the boundary; there is no JVM server. `entry.mjs` adapts Cloudflare's module handler to the Kotlin export.
- `webApp`: the umbrella application and Koin composition root. It supplies the browser's own origin to the Ktor repository, so localhost, staging, and production need no frontend URL rebuilds.

Repositories, use cases, states, entities, DTOs, and per-type mappers stay in separate files. Domain and presentation remain independent of Cloudflare and Ktor.

## API and data

| Route | Behavior |
| --- | --- |
| `GET /api/rankings/weekly` | Latest complete published snapshot, ten startups and their news; an empty list before seeding |
| `GET /api/health` | Checks D1 connectivity and returns the deployed Git SHA |
| `HEAD` on either route | Same status/headers with no body |
| Unknown `/api/*` | JSON 404, never the SPA shell |
| Other methods on known API routes | JSON 405 |

Database failures return 503; the UI shows its retry state. The response includes news eagerly because there are only ten startups and a small feed per company. There is no public write or seed endpoint.

`cloudflare/migrations/0001_initial.sql` creates:

- `startups`: company identity and descriptions.
- `ranking_snapshots`: week, publication status, creation time, and mock flag.
- `ranking_entries`: ten unique positions per snapshot, movement, and ranking reasons.
- `news_articles`: stories attached to a startup within a snapshot; a nullable URL is reserved for real sources.

The API selects one snapshot and its entries/news in a single SQL query. Drafts and incomplete published snapshots are excluded. An empty database is a valid state, not a hard-coded fallback.

`cloudflare/fixtures/ranking.json` is the seed source. The seed script inserts the fixed `demo-2026-09-07-v1` snapshot as a draft and publishes it only after all entries have been written. Interrupted seeds can be rerun; existing published data is left intact. Change the seed's snapshot identity when intentionally introducing a new demo edition. Seeds are separate from migrations. Normal push deployments never seed; a manual deployment can seed when **seed_mock_data** is explicitly checked.

## Cloudflare setup and GitHub deployment

The committed `wrangler.json` is for local development, with a placeholder database ID. The remote helper generates an ignored configuration with absolute build paths and the chosen environment's real D1 ID. Remote operations always pass `--remote`; local operations pass `--local`.

One-time setup (these remote resources have not been created by this scaffold):

1. Create a Cloudflare account and enable Workers. Create two D1 databases named `productberlin-staging` and `productberlin-production` using the dashboard or `npx wrangler d1 create <name>`.
2. Create GitHub environments named `staging` and `production`. Configure their allowed deployment branches and any desired approval rules.
3. In each environment set secrets `CLOUDFLARE_API_TOKEN` and `CLOUDFLARE_ACCOUNT_ID`. The token needs Workers Scripts Edit and D1 Edit on the target account.
4. Set environment variables `D1_DATABASE_ID` (that environment's database UUID) and `DEPLOY_URL` (the full HTTPS Worker/custom-domain URL). Worker names are `productberlin-staging` and `productberlin-production`.
5. Commit and push the scaffold (including `.github/workflows/`, `package-lock.json`, both new Kotlin modules, and migrations). In GitHub, open **Actions → Deploy Cloudflare → Run workflow**, select `staging`, and check **seed_mock_data** for the initial populated demo. Repeat for production when ready. Leave the checkbox off for ordinary updates; the standalone **Seed mock data** workflow is also available.

Workflows:

| Workflow | Trigger | Work |
| --- | --- | --- |
| Checks | Pull requests; reused by deploy | Kotlin lint/tests, both production builds, isolated local D1 integration tests, build artifact upload |
| Deploy Cloudflare | Push to `main` → production; manual staging/production | Checks → validate downloaded build and configuration → local deployment dry run → D1 migrations → upload Worker/assets → optional manual seed → HTTP/version/asset smoke checks |
| Seed mock data | Manual environment choice | Apply migrations, then seed that environment; no website deployment |

Deployment and seeding share an environment concurrency group so they cannot mutate the same environment concurrently. No Cloudflare credentials are needed by pull request checks. An empty first deployment passes smoke checks unless seeding was requested, in which case the checks require ten startups. Each build artifact is named with its Git SHA; deployment uploads that tested build without recompiling. The workflow summary links the site and records the commit. D1 databases must already exist; migrations create/update their tables. The Worker itself is created by its first deployment. Configure GitHub environments and secrets **before** enabling production pushes.

For manual remote commands after `npm run build`, set `CLOUDFLARE_API_TOKEN`, `CLOUDFLARE_ACCOUNT_ID`, `D1_DATABASE_ID`, and the full commit SHA in `BUILD_SHA` in your environment, then:

```sh
npm run cf -- config staging  # Validate/write generated configuration
npm run cf -- deploy-check staging  # Bundle locally; no cloud changes
npm run cf -- migrate staging
npm run cf -- deploy staging
npm run cf -- seed staging    # Explicit one-time demo seeding
```

Application versions use the Git SHA in `/api/health` and the Worker deployment message. Wrangler also records a Worker version. Schema versions are numbered SQL migrations; data versions are snapshot IDs/weeks. The deployment workflow uses the pinned Wrangler installed by `npm ci` to upload the Worker and the entire static assets directory in one deployment, following [Cloudflare’s static assets model](https://developers.cloudflare.com/workers/static-assets/). Keep migrations backward-compatible with the currently running Worker: migrations run before deployment, and rolling back a Worker does not roll back D1. Do not rename or edit an already-applied migration; add the next numbered file.

## Validation and tooling

```sh
npm run format            # ktlint across all Kotlin and Gradle Kotlin files
npm run check             # lint, Kotlin tests, production builds, D1/API integration tests
./gradlew check           # Gradle module checks and project-wide ktlint
./gradlew allTests        # Tests on every configured target in every module
./gradlew allTests --rerun-tasks  # Force fresh execution instead of reusing test results
npm run test:integration  # Run against already-built artifacts
npm run test:dev          # Port conflict, debugger collision, stop/restart regression checks
```

In the IDE, select the shared **All tests** Gradle run configuration. It runs `allTests --rerun-tasks` so unchanged tests execute again and emit fresh test events. For an existing Gradle configuration, enter that same task and option in its Run field, without `./gradlew`. A normal cached run may show `UP-TO-DATE` and “Test events were not received”; this does not mean the project has no tests. Modules without test sources legitimately show `NO-SOURCE` or `SKIPPED`. Browser tests require Chrome/Chromium (set `CHROME_BIN` if automatic discovery fails). HTML reports are under each tested module's `build/reports/tests/` directory. The standalone Node.js integration tests remain part of `npm run check`.

Integration tests use Wrangler/workerd with a temporary local D1 database, not mocked SQL responses. They cover empty data, migrations, exact fixture decoding, repeated seeds, DB-backed changes, hidden drafts/incomplete snapshots, health/version, routing, static assets, and database failures. Tests remove their temporary state on completion and never touch the normal local database or remote D1.

Kotlin versions are pinned in `gradle/libs.versions.toml`, the Gradle version/checksum in the wrapper, and Kotlin/JS packages in `kotlin-js-store/yarn.lock`. Cloudflare tooling is pinned in `package.json` and `package-lock.json`. After changing Kotlin/JS dependencies, use `./gradlew kotlinUpgradeYarnLock` as needed. ktlint is the Kotlin/Gradle linter; it does not format SQL or JavaScript tooling.

The production frontend still has a webpack bundle-size advisory (~1.19 MiB minified). This scaffold keeps the existing UI and Kotlin stack; bundle optimization is separate work.

## Next: weekly collection

The current mock seed is an explicit setup task. No Cron Trigger or live source adapter is enabled yet. Add a server-side scheduled collector when sources and the ranking policy are chosen:

1. Fetch API/RSS sources, normalize timestamps/URLs, resolve company identity, and deduplicate news.
2. Write a new draft snapshot and its ten ranked entries/news. Publish only when the collection is complete; retain the previous published snapshot on failure.
3. Add the Worker `scheduled` adapter and a weekly cron configuration, with retries and collection status logging. Both browser and collector use the same D1 schema; the frontend continues using the existing read API.

This scaffold uses human-readable demo publication dates; normalize them to ISO timestamps (and format them in the UI) before adding real feeds. RSS currently means the visible list of sample news, not an XML feed endpoint.

References: [Cloudflare static assets](https://developers.cloudflare.com/workers/static-assets/binding/), [D1 migrations](https://developers.cloudflare.com/d1/reference/migrations/), [Worker versions](https://developers.cloudflare.com/workers/versions-and-deployments/), [Ktor client testing](https://ktor.io/docs/client-testing.html).
