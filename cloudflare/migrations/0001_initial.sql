CREATE TABLE startups (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT NOT NULL,
    category TEXT NOT NULL
);

CREATE TABLE ranking_snapshots (
    id TEXT PRIMARY KEY,
    week_start TEXT NOT NULL,
    week_label TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'draft' CHECK (status IN ('draft', 'published')),
    is_mock INTEGER NOT NULL DEFAULT 0 CHECK (is_mock IN (0, 1)),
    created_at TEXT NOT NULL
);
CREATE INDEX snapshots_latest ON ranking_snapshots(status, week_start DESC, created_at DESC);

CREATE TABLE ranking_entries (
    snapshot_id TEXT NOT NULL REFERENCES ranking_snapshots(id),
    startup_id TEXT NOT NULL REFERENCES startups(id),
    position INTEGER NOT NULL CHECK (position BETWEEN 1 AND 10),
    movement INTEGER,
    reason TEXT NOT NULL,
    PRIMARY KEY (snapshot_id, startup_id),
    UNIQUE (snapshot_id, position)
);

CREATE TABLE news_articles (
    id TEXT PRIMARY KEY,
    snapshot_id TEXT NOT NULL,
    startup_id TEXT NOT NULL,
    headline TEXT NOT NULL,
    source TEXT NOT NULL,
    url TEXT,
    published_at TEXT NOT NULL,
    FOREIGN KEY (snapshot_id, startup_id) REFERENCES ranking_entries(snapshot_id, startup_id)
);
CREATE INDEX news_by_startup ON news_articles(snapshot_id, startup_id, published_at DESC);
