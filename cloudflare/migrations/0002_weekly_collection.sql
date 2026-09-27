-- Existing demo snapshots still require ten entries. Live snapshots record their actual positive-mention count.
ALTER TABLE ranking_snapshots ADD COLUMN expected_count INTEGER NOT NULL DEFAULT 10 CHECK (expected_count BETWEEN 1 AND 10);
ALTER TABLE ranking_snapshots ADD COLUMN search_query TEXT;
ALTER TABLE ranking_snapshots ADD COLUMN article_count INTEGER;
ALTER TABLE ranking_entries ADD COLUMN mention_count INTEGER CHECK (mention_count IS NULL OR mention_count > 0);
ALTER TABLE news_articles ADD COLUMN summary TEXT;

-- One lease per UTC week prevents overlapping scheduled deliveries from publishing competing snapshots.
CREATE TABLE collection_runs (
    week_start TEXT PRIMARY KEY,
    lease_token TEXT NOT NULL,
    lease_until TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('running', 'succeeded', 'failed')),
    started_at TEXT NOT NULL,
    finished_at TEXT,
    error TEXT
);
