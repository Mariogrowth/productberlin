# Berlin company catalogue

`startups.json` is a maintained list of Berlin startups and scaleups, not an exhaustive census. Names, aliases and context keywords control identity matching; the runtime never scrapes these verification pages. A Berlin operating/registered company or a documented Berlin origin qualifies, including internationally headquartered scaleups. This is broader than strictly newly founded companies headquartered only in Berlin.

## Expansion reviewed on 2026-09-27

The initial twelve entries were extended with the following companies. These first-party references establish a Berlin connection. Review relocation, closure, acquisition and ambiguous name matches when maintaining this list.

| Company | Berlin connection source |
| --- | --- |
| Noxtua | [Company reference](https://www.noxtua.com/legal/imprint) |
| Choco | [Company reference](https://choco.com/us/imprint) |
| Langdock | [Company reference](https://langdock.com/imprint) |
| KoRo | [Company reference](https://www.korodrogerie.de/impressum) |
| Nox | [Company reference](https://noxmobility.com/imprint) |
| wefox | [Company reference](https://www.wefox.com/press/wefox-secures-110m-with-55m-credit-facility-from-jp-morgan-and-barclays) |
| Raisin | [Company reference](https://www.raisin.com/es-es/aviso-legal/) |
| GetYourGuide | [Company reference](https://www.getyourguide.com/c/legal/) |
| Contentful | [Company reference](https://www.contentful.com/legal/) |
| Omio | [Company reference](https://www.omio.com/legal) |
| Forto | [Company reference](https://forto.com/en/imprint/) |
| Babbel | [Company reference](https://www.babbel.com/legal/imprint) |

## Further coverage

[Startup Map Berlin](https://startup-map.berlin/intro), linked by [Berlin's startup unit](https://www.berlin.de/sen/wirtschaft/startups/artikel.1405088.php), is a much larger source for editorial research. Its Berlin-Brandenburg coverage is wider than this project's Berlin scope. Do not treat every company on it as eligible or assume public browsing grants bulk data/API rights. [Dealroom API access](https://dealroom.co/products/dealroom-api/) is a separate integration, with access and licensing to resolve before automated imports.

Add stable IDs and distinctive aliases. For ambiguous names, `contextKeywords` must contain at least one relevant word/phrase in a headline, and also constrain the follow-up RSS searches. Include German vocabulary where useful. A real result from either language can count; the same article returned by both searches counts once. Translated or syndicated articles with different titles/IDs can still count separately.

## Refreshing the catalogue on each weekly run

This is possible with a supported machine-readable feed, but **is not enabled**. No API credentials or export URL are available yet; the Worker continues using the deployed `startups.json`.

Checked on 2026-09-27: [Dealroom's documented public endpoints](https://dealroom.co/for-agents/) expose curated market-map samples (roughly twelve companies per segment), with `capped`, `returned` and `total_companies` metadata. Their public `/api/marketmaps?q=Berlin&limit=10` endpoint returned no matching map. These endpoints are not a complete export of Startup Map Berlin. [Full API/bulk-feed access](https://dealroom.co/products/api/) is available separately; API access uses OAuth2 client credentials.

Once a feed is available, implement the refresh after acquiring the weekly lease and before RSS discovery:

1. Fetch the agreed feed with bounded timeouts, pagination and size limits. Store credentials as Worker secrets. Do not scrape undocumented internal map endpoints.
2. Validate the records and agreed Berlin eligibility, map stable provider IDs, and merge maintained name aliases/context overrides. Do not silently discard ambiguous-name protections. The current parser allows at most 500 companies, so a larger source needs an explicit bounded import policy.
3. Persist the validated catalogue, source/version and refresh timestamp in D1 with a new migration. Reject empty or incomplete imports rather than replacing good data.
4. On provider failure, use the last successfully validated catalogue (bundled catalogue on first run), record the refresh failure and continue RSS collection. Keep the ranking's existing failure/atomic-publication guarantees.
5. Test pagination, malformed/capped responses, stable IDs, overrides, provider outages and cache fallback with fixtures before enabling the provider.

Refreshing on each actual collection attempt is sufficient; already-completed duplicate cron events should still skip all upstream work. This design does not require an additional scheduler, but the provider's access terms and request limits must support weekly retrieval.
