# Product.berlin

A Kotlin/JS + React prototype of a weekly Berlin startup ranking, based on the supplied desktop and mobile references. Includes ten demo companies, ranking movement, and expandable “Why” panels with fictional news. Everything is local: no scraping, analytics, external fonts, or API calls.

## Run

Requirements: JDK 21. The Gradle wrapper downloads Gradle, Node.js, and npm dependencies on the first run. You do not need to install npm separately.

```sh
./gradlew :webApp:jsBrowserDevelopmentRun --continuous
```

Open the localhost URL printed by webpack (normally http://localhost:8080). Kotlin edits rebuild automatically. The IDE's `webApp` run configuration runs the same Gradle task. If npm is available, `npm start`, `npm run build`, and `npm test` are convenience aliases; do not run `npm install` in `webApp`.

```sh
# Optimized static website
./gradlew :webApp:jsBrowserDistribution

# Domain + data tests in Node, without a browser or network API
./gradlew :app-domain:jsNodeTest :app-data:jsNodeTest
```

The deployable site is in `webApp/build/dist/js/productionExecutable/`. Serve that directory with any static HTTP server.

## Lint and formatting

ktlint-gradle is the Kotlin linter for every module and all Gradle Kotlin scripts. `.editorconfig` uses ktlint's official style, four-space indentation, and explicit imports. Generated build files and `node_modules` are excluded.

```sh
./gradlew ktlintFormat  # Format the entire project
./gradlew ktlintCheck   # Check the entire project without changing files
```

The root `:ktlintFormat` and `:ktlintCheck` tasks also aggregate all modules. Lint is wired into `check`; the root `:check` runs lint across the project. `npm run format` and `npm run lint` are equivalent shortcuts. Formatting covers `.kt` and `.kts`, not CSS, HTML, JSON, or TOML.

## Dependency versions

Latest stable direct dependencies verified on September 13, 2026:

| Dependency | Version |
| --- | --- |
| Gradle wrapper | 9.7.1 |
| Kotlin | 2.4.20 |
| Kotlin React / React DOM wrappers | 2026.9.1-19.2.8 |
| React / React DOM runtime | 19.3.0 |
| Ktor | 3.5.2 |
| Koin | 4.2.2 |
| kotlinx.coroutines | 1.11.0 |
| kotlinx.serialization | 1.11.0 |
| ktlint-gradle | 14.2.0 |
| ktlint engine | 1.8.0 |

Versions are pinned in `gradle/libs.versions.toml`; Gradle's distribution URL and official checksum are pinned in the wrapper properties. The Kotlin plugin manages JavaScript tooling dependencies, recorded in `kotlin-js-store/yarn.lock`. Root Yarn resolutions pin React and React DOM to 19.3.0 across all modules; the latest published Kotlin wrapper types still target 19.2.8. The app uses APIs shared by these versions, verified by the production build and browser checks. Prereleases are excluded from upgrades.

Release sources: [Gradle](https://gradle.org/releases/), [Maven Central](https://repo.maven.apache.org/maven2/), [ktlint-gradle](https://github.com/JLLeitschuh/ktlint-gradle/releases), and [React](https://react.dev/blog/2026/09/09/react-19-3).

## Architecture

`webApp` is the umbrella module and composition root. The data and presentation modules depend only on the domain module within the application; neither depends on the other.

```text
webApp ──► app-domain
   ├─────► app-data ──────────► app-domain
   └─────► app-presentation ──► app-domain
```

- `webApp`: application/build configuration, HTML shell, `Main.kt`, and Koin wiring in `di/AppModule.kt`. It selects the mock transport and assembles the layers.
- `app-domain`: framework-independent entities, repository contracts, use cases, and application states. No Ktor, Koin, serialization, or React dependencies. Published ordering is preserved and duplicate identities are rejected.
- `app-data`: repository implementations, serializable DTOs with explicit domain mapping, Ktor mock transport, and deterministic fixtures.
- `app-presentation`: Kotlin React components, lifecycle-scoped loading hooks, responsive CSS, and local image assets. Components consume domain use cases, states, and entities. Company marks are typographic placeholders, not official logos.

Each repository, use case, state, entity, and DTO has its own file:

```text
app-domain/src/commonMain/kotlin/net/productberlin/domain/
  entity/NewsArticle.kt
  entity/Startup.kt
  entity/WeeklyRanking.kt
  repository/StartupRepository.kt
  state/RankingState.kt
  usecase/GetWeeklyRanking.kt

app-data/src/commonMain/kotlin/net/productberlin/data/
  dto/NewsDto.kt
  dto/RankingDto.kt
  dto/StartupDto.kt
  mock/MockHttpClient.kt
  mock/MockRanking.kt
  repository/KtorStartupRepository.kt

app-presentation/src/jsMain/
  kotlin/net/productberlin/presentation/
    App.kt
    StartupRow.kt
    WeeklyRankingHook.kt
  resources/
    styles.css
    arrow.svg
    favicon.svg
```

`webApp` packages presentation resources through its Gradle resource source set. Both development and production output include those assets alongside the HTML shell. Presentation's loading hook maps the use-case result into domain `RankingState` and cancels work when its React effect is disposed.

The only HTTP engine is Ktor `MockEngine`. It responds in memory to `GET /api/rankings/weekly`; unconfigured paths fail immediately. The `.invalid` host is deliberately reserved. Installing a real network engine requires an explicit implementation change. The demo's company selection, positions, movement, explanations, and stories are illustrative and unverified.

## Where real collection will go

Collection should run in a separate server-side worker service, never in the browser. Start with these responsibilities when real data is requested:

1. Source adapters collect permitted public company updates and news with provenance (canonical URL, publisher, publication time, fetched time).
2. Normalization resolves company identity; deduplication merges syndicated articles by canonical URL/content identity.
3. Aggregation stores source evidence and produces a versioned weekly ranking using an explicit scoring policy, comparing it to the previous published week.
4. An API exposes the snapshot through `/api/rankings/weekly`. Add real article URLs and provenance to the DTO/domain contract at that point.
5. Replace the mock client at the composition root and configure the repository's API base URL. Domain use cases and UI remain independent of collection machinery.

No worker scheduler, real scrape adapter, scoring algorithm, or backend has been implemented in this design scaffold. The mock fixture preserves an authored order rather than suggesting a fabricated ranking formula.

## Reference decisions

Centered wordmark, a narrow rounded desktop card, ten compact ranked rows, colored square marks, movement indicators, outlined “Why” controls, and a desktop margin note. On mobile the card border/shadow and margin note disappear. Disclosure buttons expose expanded state and support keyboard interaction; focus indicators and a skip link are included. No external image requests are needed.

Ktor mock transport follows the [official MockEngine documentation](https://ktor.io/docs/client-testing.html). React uses [JetBrains Kotlin wrappers](https://github.com/JetBrains/kotlin-wrappers).

## Validation

- Project-wide `ktlintFormat`, `:check` (including every module’s lint), and the production Gradle build passed.
- Four Kotlin/JS tests passed (published order/empty state, duplicate identities, JSON mapping, and unexpected mock routes).
- Headless Chrome checked 320, 390, 768, and 1400 px layouts, keyboard/pointer disclosure, and all ten rows. No horizontal overflow, JavaScript errors, or external requests were observed.
- Production bundle: approximately 1119 KiB minified, 287 KiB gzip. Webpack reports a bundle-size advisory; splitting or reducing runtime dependencies is future optimization work.
