# Product.berlin

A Kotlin/JS + React website showing up to ten Berlin startups ranked by weekly Google News headline mentions. A scheduled Cloudflare Worker collects RSS, publishes a snapshot to D1, and serves the website and Kotlin/JS API. “Why” reveals up to five recent news links per company. Local development starts with an explicitly labeled demo.

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

Open http://localhost:8787. The real path is React → Ktor HTTP → Worker → local D1. The “Why” buttons reveal news returned with the ranking. Data persists in `.wrangler/state/` and is separate from remote databases. The initial seeded edition is fictional and visibly labeled as a demo. A successful scheduled collection replaces it with a dated live edition.

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

cloudflare/             SQL migrations, startup catalogue and mock fixture
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
| `GET /api/rankings/weekly` | Latest complete published snapshot, up to ten startups and their news; empty before seeding/collection |
| `GET /api/health` | Checks D1 connectivity and returns the deployed Git SHA |
| `POST /api/subscriptions` | Newsletter sign-up: same-origin JSON `{"email": …}` only. `202` sends a Brevo double opt-in email, `400` for an invalid address or body, `403` cross-origin, `405`/`413`/`415` for other misuse, `503` when Brevo is unavailable or not configured |
| `HEAD` on either ranking route | Same status/headers with no body |
| Unknown `/api/*` | JSON 404, never the SPA shell |
| Other methods on known API routes | JSON 405 |

Database failures return 503; the UI shows its retry state. The response includes news eagerly because there are only ten startups and a small feed per company. The only public write endpoint is the newsletter sign-up; there is no seed endpoint.

### Newsletter sign-ups

The email pill below the ranking calls `POST /api/subscriptions`. The Worker validates the address with the shared domain rule and asks [Brevo's double opt-in API](https://developers.brevo.com/reference/createdoicontact) to send template `BREVO_DOI_TEMPLATE_ID` for list `BREVO_LIST_ID` (both in `wrangler.json`). Contacts join the list only after clicking the confirmation link, which must use `{{ params.DOIurl }}` in the template. The link returns them to `/?subscribed=1` on the same site, where a dismissible neutral banner at the top of the page confirms the email is verified, and the marker is removed from the address bar. Repeated or already-known addresses get the same reply as new ones. Every sign-up outcome is shown beneath the pill.

`BREVO_API_KEY` is a Worker **secret**, never a committed var. Deployments read the GitHub secret `BREVO_API` and upload it with the same Worker version as the code (`wrangler deploy --secrets-file`, via a temporary private file). For local sign-ups, copy `.dev.vars.example` to the git-ignored `.dev.vars`. Without a key, the endpoint answers `503` and the form asks visitors to try again. Integration tests run with an invalid list ID, so they can never reach Brevo. The endpoint accepts only same-origin `application/json` bodies up to 1 KB, and the form has a hidden spam-trap field. `/privacy` describes the data the site and the email process. It is served with `X-Robots-Tag: noindex` (from `webApp/src/jsMain/resources/_headers`, applied by Cloudflare static assets), so search engines can crawl it but do not list it; do not block it in `robots.txt`, or crawlers could never see the noindex.

### Company logos

Ranked companies carry a `logoUrl` for the [Brandfetch Logo API](https://docs.brandfetch.com/logo-api/overview). The Worker builds it at response time from the company's `domain` in the bundled `cloudflare/startups.json` (not from D1), so catalogue domain fixes apply to existing snapshots on the next deployment. Only the up to ten companies in the response get a URL; nothing is requested for other catalogue companies. Brandfetch requires browsers to hotlink logos, so the Worker never fetches, caches or proxies them. URLs use `fallback/404`: when Brandfetch has no icon, or the image fails, the UI keeps its letter mark. The `BRANDFETCH_CLIENT_ID` Worker variable is a public client ID, committed in `wrangler.json`; set a GitHub environment variable of the same name to override it per environment. Without one, the API omits logos. Brandfetch rejects headless-browser user agents, so headless screenshots show letter marks unless they use a regular Chrome user agent.

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
4. Set environment variables `D1_DATABASE_ID` (that environment's database UUID) and `DEPLOY_URL` (the full HTTPS Worker/custom-domain URL). Optionally set `BRANDFETCH_CLIENT_ID` to override the committed public logo client ID. Worker names are `productberlin-staging` and `productberlin-production`.
5. Commit and push the scaffold (including `.github/workflows/`, `package-lock.json`, both new Kotlin modules, and migrations). In GitHub, open **Actions → Deploy Cloudflare → Run workflow**, select `staging`, and check **seed_mock_data** for the initial populated demo. Repeat for production when ready. Leave the checkbox off for ordinary updates; the standalone **Seed mock data** workflow is also available.

Set these variables in **Settings → Environments → staging/production**, rather than relying on one repository-wide URL. Repository variables are defaults for environments without an override. For workers.dev, the first hostname label must match the deployed Worker: `productberlin-staging` or `productberlin-production`; the following account subdomain stays the same and may itself contain `staging`. Preflight rejects a workers.dev URL for the wrong environment before migrations or uploads. Custom domains are still checked against the deployed Git SHA by the smoke test.

Workflows:

| Workflow | Trigger | Work |
| --- | --- | --- |
| Checks | Pull requests; reused by deploy | Kotlin lint/tests, both production builds, isolated local D1 integration tests, build artifact upload |
| Deploy Cloudflare | Push to `main` → production; manual staging/production | Checks → validate downloaded build and configuration → local deployment dry run → D1 migrations → upload Worker/assets → optional manual seed → HTTP/version/asset smoke checks |
| Seed mock data | Manual environment choice | Apply migrations, then seed that environment; no website deployment |

Deployment and seeding share an environment concurrency group so they cannot mutate the same environment concurrently. No Cloudflare credentials are needed by pull request checks. An empty first deployment passes smoke checks unless seeding was requested, in which case the checks require a populated ranking (a newer live edition can have fewer than ten). Each build artifact is named with its Git SHA; deployment uploads that tested build without recompiling. The workflow summary links the site and records the commit. D1 databases must already exist; migrations create/update their tables. The Worker itself is created by its first deployment. Configure GitHub environments and secrets **before** enabling production pushes.

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

See [the test coverage inventory](TESTING.md) for per-module scenarios, counts, report locations and measurement limitations.

```sh
npm run format            # ktlint across all Kotlin and Gradle Kotlin files
npm run check             # lint, Kotlin tests, production builds, D1/API integration tests
./gradlew check           # Gradle module checks and project-wide ktlint
./gradlew allTests        # Tests on every configured target in every module
./gradlew allTests --rerun-tasks  # Force fresh execution instead of reusing test results
npm run test:integration  # Run against already-built artifacts
npm run test:deployment   # Deployment URL/environment validation regression tests
npm run test:dev          # Port conflict, debugger collision, stop/restart regression checks
```

In the IDE, select the shared **All tests** Gradle run configuration. It runs `allTests --rerun-tasks` so unchanged tests execute again and emit fresh test events. For an existing Gradle configuration, enter that same task and option in its Run field, without `./gradlew`. A normal cached run may show `UP-TO-DATE` and “Test events were not received”; this does not mean the project has no tests. Modules without test sources legitimately show `NO-SOURCE` or `SKIPPED`. Browser tests require Chrome/Chromium (set `CHROME_BIN` if automatic discovery fails). HTML reports are under each tested module's `build/reports/tests/` directory. The standalone Node.js integration tests remain part of `npm run check`.

Integration tests use Wrangler/workerd with a temporary local D1 database, not mocked SQL responses. They cover empty data, migrations, exact fixture decoding, repeated seeds, DB-backed changes, hidden drafts/incomplete snapshots, health/version, routing, static assets, and database failures. Tests remove their temporary state on completion and never touch the normal local database or remote D1.

Kotlin versions are pinned in `gradle/libs.versions.toml`, the Gradle version/checksum in the wrapper, and Kotlin/JS packages in `kotlin-js-store/yarn.lock`. Cloudflare tooling is pinned in `package.json` and `package-lock.json`. After changing Kotlin/JS dependencies, use `./gradlew kotlinUpgradeYarnLock` as needed. ktlint is the Kotlin/Gradle linter; it does not format SQL or JavaScript tooling.

The production frontend still has a webpack bundle-size advisory (~1.19 MiB minified). This scaffold keeps the existing UI and Kotlin stack; bundle optimization is separate work.

## Weekly Google News collection

The deployed Worker has a Cron Trigger, `0 6 * * MON`: Monday at **06:00 UTC**, in both staging and production. Deploy through the existing **Deploy Cloudflare** workflow; migration `0002_weekly_collection.sql`, the catalogue, RSS parser and cron configuration ship with it. No Google API key or new GitHub secret is needed. The first live snapshot appears after the first successful scheduled run; deployment itself does not fetch news. Seed mock data only if you want a demo while waiting. Cron configuration can take time to propagate.

The collector:

1. Searches Google News RSS in **English and German**, in seven daily slices per language (14 discovery requests). The query combines `(Berlin OR Berliner)` with `startup`, `startups`, `start-up`, `start-ups`, `funding`, `Finanzierung`, `Finanzierungsrunde` and `Gründer`. Both editions use Germany (`gl=DE`, `ceid=DE:en` / `DE:de`). The ranking covers the most recent complete Monday–Sunday UTC week. Publication dates outside that window are rejected. Articles from video platforms (YouTube) and from publishers whose site uses a country-code domain outside the EU, Canada, the US, the UK and Australia are dropped while parsing, so they neither count nor appear; generic domains (`.com`, `.org`, `.news`, …) and generically used codes (`.io`, `.ai`, `.co`, `.me`, `.tv`, `.fm`, `.gg`) are kept (`PublisherPolicy.kt`). The publisher site comes from the RSS item's `<source url>`.
2. Matches case-insensitive whole-word company names/aliases from **`cloudflare/startups.json`** in headlines. Ambiguous company names also require one of their catalogue context keywords in the headline. An article counts once per company, regardless of repeated name occurrences. Duplicate IDs, links and same-publisher/headline pairs collapse. Publisher names and follow-up searches do not inflate counts.
3. Keeps up to ten companies with positive counts, descending by count, breaking ties by stable company ID. Fewer matches means a shorter list. Movement compares to the latest published live snapshot from an earlier week (never another policy edition of the same week); first-time entries show NEW.
4. Searches each winning company's name in both languages over the same period, adding catalogue `contextKeywords` as an OR group when the name is ambiguous (for example, mika + accounting/Buchhaltung/fintech). Combines those results with discovery articles, deduplicates, filters to matching headlines, shows the same headline only once even when publisher names are spelled differently, and keeps the five newest links (or fewer when unavailable). Headlines, publishers, links and ISO timestamps come from RSS; there are no fabricated excerpts or thumbnails.
5. Publishes the snapshot, entries and news in one **D1 transaction**. A per-window-and-policy 15-minute lease prevents overlapping work; successful windows under the same policy are idempotent, failed/expired attempts can retry. Failures or no catalogue matches retain the previous dated edition and record failure in `collection_runs`. Observe scheduled invocation errors and this table in Cloudflare; there is no separate alert integration or in-run retry loop.

The catalogue is editorial data, not automatic company discovery. It contains 229 Berlin startups, scaleups and a few large Berlin-founded companies; [catalogue notes and source references](cloudflare/CATALOGUE.md) describe the expanded coverage and Berlin connection criteria. Add verified Berlin companies with a stable slug, name, short description, category and optional distinctive aliases. Add `contextKeywords` for ambiguous names to constrain follow-up searches; a headline must contain a keyword as well as the company name/alias to qualify, both for discovery counts and displayed news. This conservative rule prevents person-name collisions but can miss terse company headlines. Avoid broad aliases and retired/non-Berlin companies. Changes require a normal deployment. [Automatic catalogue refresh](cloudflare/CATALOGUE.md#refreshing-the-catalogue-on-each-weekly-run) is possible with a supported feed, but is not enabled without API/export access. The fixed search plan makes at most 34 RSS requests per run (14 discovery + two for each of ten winners), independently of catalogue size. If ranking/search methodology changes again, increment `CollectionWindow.POLICY_VERSION` to permit a fresh same-week snapshot. Google News RSS can omit or cap results; daily slicing reduces that effect, but this is a **catalogue-limited news signal**, not an exhaustive census or a company-quality score. Article deduplication is heuristic and cannot detect all syndicated copies. The public RSS endpoint is an upstream dependency, not a guaranteed structured company API.

### Try a real collection locally

Start the full app (`./gradlew runLocal`) to build and apply migrations, then trigger its **local-only** scheduled endpoint:

```sh
curl 'http://localhost:8787/cdn-cgi/local/scheduled?cron=0%206%20*%20*%20MON'
```

This makes public Google News requests and writes **local D1 only**. Refresh the page after completion. Every event, on any weekday, resolves to the Monday–Sunday UTC week that ended at the most recent Monday 00:00 UTC, so an edition stays unchanged until the next Monday: once a week has succeeded, later events that week skip all upstream work. To collect a different week, provide a `time` parameter (Unix milliseconds) within the following week. The `bilingual-v2` policy uses new lease/snapshot IDs, so triggering it can publish an expanded edition for a week already collected by the earlier English-only policy, while preserving the old snapshot. To retry failed collection, invoke the same event again. The endpoint is provided by Wrangler for development; the production API remains read-only.

Integration tests exercise the actual bundled Kotlin scheduled handler in Miniflare/workerd with isolated D1 and deterministic RSS fixtures. They cover top-ten ordering, five stories, deduplication, status/idempotency, failed feed retention, expired leases, SQL rollback and retry. They make no real Google News calls. Domain tests cover identity boundaries/ties; browser tests cover safe links and disclosure focus. RSS here is an input source; the app exposes JSON, not a public RSS output endpoint.

References: [Cloudflare Cron Triggers](https://developers.cloudflare.com/workers/configuration/cron-triggers/), [D1 transactions](https://developers.cloudflare.com/d1/worker-api/d1-database/), [Cloudflare static assets](https://developers.cloudflare.com/workers/static-assets/binding/), [D1 migrations](https://developers.cloudflare.com/d1/reference/migrations/), [Worker versions](https://developers.cloudflare.com/workers/versions-and-deployments/), [Ktor client testing](https://ktor.io/docs/client-testing.html).
