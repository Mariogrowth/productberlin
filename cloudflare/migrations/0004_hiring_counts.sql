-- Weekly open-role counts from each catalogue company's public job board, recorded to test hiring as a ranking signal.
-- New tables only, unused by older Workers, so this is safe to apply before deploying the Worker that writes them.
CREATE TABLE hiring_runs (
    week_start TEXT PRIMARY KEY,
    recorded_at TEXT NOT NULL,
    boards INTEGER NOT NULL CHECK (boards >= 0),
    failed INTEGER NOT NULL CHECK (failed >= 0)
);
CREATE TABLE hiring_counts (
    week_start TEXT NOT NULL REFERENCES hiring_runs (week_start),
    startup_id TEXT NOT NULL,
    provider TEXT NOT NULL,
    total_jobs INTEGER NOT NULL CHECK (total_jobs >= 0),
    berlin_jobs INTEGER NOT NULL CHECK (berlin_jobs >= 0 AND berlin_jobs <= total_jobs),
    PRIMARY KEY (week_start, startup_id)
);
