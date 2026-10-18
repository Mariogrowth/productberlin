-- Items from startup publishers' feeds and the Berlin startup stories found in them, for the weekly news section.
-- Every item read is kept (with or without stories) so it is never classified twice. New tables only.
CREATE TABLE feed_items (
    key TEXT PRIMARY KEY,
    feed TEXT NOT NULL,
    source TEXT NOT NULL,
    title TEXT NOT NULL,
    translated_title TEXT,
    link TEXT NOT NULL,
    published_at TEXT NOT NULL,
    classified_at TEXT NOT NULL,
    classifier TEXT NOT NULL CHECK (classifier IN ('model', 'rules'))
);
CREATE INDEX feed_items_published ON feed_items (published_at);
CREATE TABLE feed_stories (
    item_key TEXT NOT NULL REFERENCES feed_items (key),
    position INTEGER NOT NULL CHECK (position BETWEEN 0 AND 4),
    company_id TEXT,
    company_name TEXT NOT NULL,
    category TEXT NOT NULL CHECK (category IN ('Funding', 'Launches', 'Growth', 'People', 'Trouble', 'Other')),
    headline TEXT NOT NULL,
    PRIMARY KEY (item_key, position)
);
