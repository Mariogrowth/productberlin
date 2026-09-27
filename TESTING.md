# Test coverage

All six modules have automated behavioral tests. These are test counts and a coverage inventory, **not measured line/branch percentages**. Kotlin/JS generated code makes JVM coverage tools inappropriate; no percentage threshold is currently enforced. Source-mapped Kotlin/JS instrumentation remains separate work. Counts below list each Kotlin test once, even when it runs in both Node and Chrome.

| Module | Tests | Boundaries covered |
| --- | ---: | --- |
| `app-domain` | 9 | Published ordering, empty rankings, duplicate identities, propagated failures/cancellation, whole-word and Unicode matching, aliases/context, deduplication, top ten, deterministic ties |
| `app-data` | 8 | Ktor request URL/method, trailing slashes, JSON decoding/mapping, live metadata and news, future fields, empty rankings, 404/503, malformed responses, cancellation |
| `api-contract` | 4 | Legacy defaults, live round-trip including Unicode/news, required fields/types, forward compatibility |
| `api-worker` | 25 | HTTP query/locale encoding, HTTP errors, size limit, cancellation, malformed/unsafe RSS, catalogue validation, UTC/leap-year/DST windows, partial feed failures, enrichment limits, lease/publication failure |
| `app-presentation` | 23 | Loading/success/empty/error/retry states, cancellation on use-case replacement, live/mock attribution, design-system semantics/styles, safe news links, disclosure and focus |
| `webApp` | 4 | Koin graph and instance isolation, real Ktor-to-screen bootstrap with an intercepted response, API-free design-system route |

`app-domain`, `app-data`, and `api-contract` run in both Node and Chrome. Presentation and bootstrap tests run in Chrome, Worker unit tests in Node. This produces **94 Kotlin test executions**. There are also **19 local Worker/RSS integration tests** and **3 launcher regression tests**.

## Database publication and fetching

`scripts/test/collector.test.mjs` runs the production bundled scheduled handler in Miniflare/workerd against real, isolated local D1. Outbound RSS is intercepted with deterministic bilingual fixtures; there are no real Google News requests and no remote database access. Each collector scenario starts with a clean database.

The suite verifies:

- Discovery across daily/language queries does not multiply mention counts; enrichment cannot inflate rankings.
- Top-ten order, five newest links, sparse weeks, prior-week movement, same-week policy reruns.
- Previously published data survives empty/malformed feeds, discovery errors and winner-news errors.
- A failed write at **each of six publication stages**, including the final success audit, rolls back snapshots, identities, entries and news. Retrying can publish successfully.
- Duplicate and overlapping events have one owner; active leases skip, expired leases recover, replaced owners cannot publish or mark the new owner failed.
- Mock, draft and incomplete snapshots do not affect movement calculations.

The API integration suite additionally checks migrations, seeding/idempotency, DB-backed reads, draft/incomplete visibility, HTTP methods/errors, static assets and database outages. Unit tests cover HTTP limits and parsing separately so these failure paths remain fast and deterministic.

## Running and reading results

```sh
./gradlew allTests                 # Every module and configured target
./gradlew allTests --rerun-tasks   # Fresh test events for the IDE
npm run check                     # Lint, tests, both builds, D1/API/RSS and launcher checks
npm run test:integration          # Integration tests; requires current production builds
```

Use JDK 21, Node 24 and Chrome/Chromium. Module HTML reports are in `<module>/build/reports/tests/<target>/index.html`; JUnit XML is in `<module>/build/test-results/<target>/`. GitHub Checks uploads these as `test-reports-<commit SHA>` even when a check fails. Integration results appear in the `npm run check` log.

Passing these tests proves fixture-driven behavior against local runtime/database implementations. It does not prove Google News completeness, upstream availability, a remote deployment, or a numerical code-coverage target. New behavior and regressions should add tests at the owning boundary; do not count generated serialization accessors or trivial data-class properties as meaningful test scenarios.
