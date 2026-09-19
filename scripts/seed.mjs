import { readFile } from 'node:fs/promises';

export const snapshotId = 'demo-2026-09-07-v1';
const sqlValue = (value) => value == null ? 'NULL' : typeof value === 'number' ? String(value) : `'${value.replaceAll("'", "''")}'`;

/** Deterministic and resumable: publish only after every row exists; never rewrite a published seed. */
export async function mockSeedSql() {
  const fixture = JSON.parse(await readFile(new URL('../cloudflare/fixtures/ranking.json', import.meta.url), 'utf8'));
  if (fixture.startups.length !== 10 || new Set(fixture.startups.map(s => s.id)).size !== 10) {
    throw new Error('Mock ranking must contain exactly ten unique startups');
  }
  const draft = `EXISTS (SELECT 1 FROM ranking_snapshots WHERE id = ${sqlValue(snapshotId)} AND status = 'draft')`;
  const statements = [
    `INSERT INTO ranking_snapshots (id, week_start, week_label, status, is_mock, created_at)
     VALUES (${sqlValue(snapshotId)}, '2026-09-07', ${sqlValue(fixture.weekLabel)}, 'draft', 1, '2026-09-13T00:00:00Z')
     ON CONFLICT(id) DO NOTHING;`,
  ];
  fixture.startups.forEach((startup, index) => {
    statements.push(`INSERT INTO startups (id, name, description, category)
      SELECT ${[startup.id, startup.name, startup.description, startup.category].map(sqlValue).join(', ')}
      WHERE ${draft} ON CONFLICT(id) DO NOTHING;`);
    statements.push(`INSERT INTO ranking_entries (snapshot_id, startup_id, position, movement, reason)
      SELECT ${[snapshotId, startup.id, index + 1, startup.movement, startup.reason].map(sqlValue).join(', ')}
      WHERE ${draft} ON CONFLICT(snapshot_id, startup_id) DO NOTHING;`);
    for (const story of startup.news) {
      statements.push(`INSERT INTO news_articles (id, snapshot_id, startup_id, headline, source, url, published_at)
        SELECT ${[story.id, snapshotId, startup.id, story.headline, story.source, null, story.publishedAt].map(sqlValue).join(', ')}
        WHERE ${draft} ON CONFLICT(id) DO NOTHING;`);
    }
  });
  statements.push(`UPDATE ranking_snapshots SET status = 'published'
    WHERE id = ${sqlValue(snapshotId)} AND status = 'draft'
      AND (SELECT COUNT(*) FROM ranking_entries WHERE snapshot_id = ${sqlValue(snapshotId)}) = 10;`);
  return statements.join('\n') + '\n';
}
