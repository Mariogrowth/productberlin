-- Each collected week's trusted articles that name a catalogue company, kept so an edition can score four weeks of
-- press momentum without searching earlier weeks again. New tables only, so older Workers are unaffected.
CREATE TABLE collected_weeks (
    week_start TEXT PRIMARY KEY,
    reviewed INTEGER NOT NULL CHECK (reviewed >= 0),
    recorded_at TEXT NOT NULL
);
CREATE TABLE collected_articles (
    week_start TEXT NOT NULL REFERENCES collected_weeks (week_start),
    id TEXT NOT NULL,
    headline TEXT NOT NULL,
    translated_headline TEXT,
    source TEXT NOT NULL,
    url TEXT,
    published_at TEXT NOT NULL,
    PRIMARY KEY (week_start, id)
);
